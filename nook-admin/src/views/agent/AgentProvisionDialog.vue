<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { ArrowUp, Rocket, RefreshCcw } from 'lucide-vue-next'
import {
  NAlert,
  NButton,
  NIcon,
  NModal,
  NRadio,
  NRadioGroup,
  NSelect,
  NSpin,
  NTabs,
  NTabPane,
  NTag,
  useDialog,
  useMessage
} from 'naive-ui'
import {
  agentInstallStream,
  getAgentInstallYamlTemplate,
  pageServers,
  type AgentInstallDTO
} from '@/api/resource/server'
import {
  AGENT_ONLINE_LABELS,
  AGENT_ONLINE_TAG_TYPE,
  listAgents,
  upgradeAgent,
  type AgentListItem
} from '@/api/agent/agent'

const props = defineProps<{
  modelValue: boolean
  initialServerId: string | null
  initialRole?: 'frontline' | 'landing'
}>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'dispatched'): void
}>()

const open = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const message = useMessage()
const dialog = useDialog()

// ---------- 服务器列表 + role 过滤 ----------
const loading = ref(false)
const agents = ref<AgentListItem[]>([])
const allServers = ref<Array<{ id: string; name: string; host: string; lifecycleState: string }>>([])
const selectedRole = ref<'frontline' | 'landing'>('frontline')
const selectedServerId = ref<string | null>(null)

const ROLE_OPTIONS = [
  { label: 'Frontline (xray)', value: 'frontline' },
  { label: 'Landing (socks5)', value: 'landing' }
]

const currentAgent = computed(() =>
  agents.value.find((a) => a.serverId === selectedServerId.value) || null
)

function serverRole(serverId: string): 'frontline' | 'landing' | 'unprovisioned' {
  const a = agents.value.find((x) => x.serverId === serverId)
  if (!a || !a.agentVersion) return 'unprovisioned'
  if (a.agentVersion.startsWith('landing-')) return 'landing'
  return 'frontline'
}

const serverOptions = computed(() =>
  allServers.value
    .filter((s) => {
      const r = serverRole(s.id)
      return r === 'unprovisioned' || r === selectedRole.value
    })
    .map((s) => {
      const a = agents.value.find((x) => x.serverId === s.id)
      const tag = a && a.agentVersion ? `${a.onlineState} ${a.agentVersion}` : '未装'
      return { label: `${s.name} (${s.host}) — ${tag}`, value: s.id }
    })
)

async function loadData() {
  loading.value = true
  try {
    const [servers, agentList] = await Promise.all([
      pageServers({ pageNo: 1, pageSize: 200 }),
      listAgents()
    ])
    agents.value = agentList
    allServers.value = servers.records.map((s) => ({
      id: s.id, name: s.name, host: s.host, lifecycleState: s.lifecycleState
    }))
  } catch { /* */ } finally {
    loading.value = false
  }
}

// ---------- 推荐 tab: 装过 → 升级, 没装 → 部署 ----------
type Flow = 'upgrade' | 'deploy'
const activeTab = ref<Flow>('upgrade')
const recommended = computed<Flow>(() => {
  const a = currentAgent.value
  if (!a || a.onlineState === 'NEVER') return 'deploy'
  return 'upgrade'
})
watch(currentAgent, () => { activeTab.value = recommended.value })

// ---------- 升级 ----------
const upgrading = ref(false)
const dispatchedTaskId = ref<string | null>(null)
const lastDispatchedFor = ref<string | null>(null)
const lastDispatchedVersion = ref<string | null>(null)
type UpgradeStatus = 'WAITING' | 'COMPLETED' | 'TIMEOUT'
const upgradeStatus = ref<UpgradeStatus>('WAITING')
const upgradeElapsed = ref(0)
let upgradePollTimer: ReturnType<typeof setInterval> | null = null

function stopPolling() {
  if (upgradePollTimer) { clearInterval(upgradePollTimer); upgradePollTimer = null }
}

async function doUpgrade() {
  const a = currentAgent.value
  if (!a) return
  if (a.onlineState === 'OFFLINE' || a.onlineState === 'NEVER') {
    message.error('agent 不在线, 升级会一直 PENDING')
    return
  }
  dialog.warning({
    title: '确认升级',
    content: `${a.serverName}: ${a.agentVersion ?? '?'} → backend 当前版本. 重启窗口 ~10-20 秒, 期间断 1 次心跳; 服务器上 xray/socks5 不动.`,
    positiveText: '升级', negativeText: '取消',
    onPositiveClick: async () => {
      upgrading.value = true
      try {
        const taskId = await upgradeAgent(a.serverId)
        dispatchedTaskId.value = taskId
        lastDispatchedFor.value = a.serverName
        lastDispatchedVersion.value = a.agentVersion ?? '?'
        upgradeStatus.value = 'WAITING'
        upgradeElapsed.value = 0
        message.success(`已派 task ${taskId.slice(0, 8)}…`)
        emit('dispatched')
        startUpgradePolling(a.serverId, a.agentVersion ?? '?')
      } catch { /* */ } finally { upgrading.value = false }
    }
  })
}

function startUpgradePolling(serverId: string, oldVersion: string) {
  stopPolling()
  upgradePollTimer = setInterval(async () => {
    upgradeElapsed.value += 5
    try {
      const list = await listAgents()
      agents.value = list
      const a = list.find((x) => x.serverId === serverId)
      if (a && a.agentVersion && a.agentVersion !== oldVersion && a.onlineState === 'ONLINE') {
        upgradeStatus.value = 'COMPLETED'
        stopPolling()
      }
    } catch { /* */ }
    if (upgradeElapsed.value >= 180 && upgradeStatus.value === 'WAITING') {
      upgradeStatus.value = 'TIMEOUT'
      stopPolling()
    }
  }, 5000)
}

// ---------- SSH 自动部署 (流式) ----------
const deploying = ref(false)
const deployLog = ref('')
const deployLogRef = ref<HTMLPreElement | null>(null)
let deployAbort: AbortController | null = null

// yaml 编辑器
const yamlDraft = ref('')
const yamlLoading = ref(false)
const yamlLineNumsRef = ref<HTMLElement | null>(null)
const yamlTaRef = ref<HTMLTextAreaElement | null>(null)
const yamlLineNumbers = computed(() => {
  const n = (yamlDraft.value.match(/\n/g)?.length ?? 0) + 1
  return Array.from({ length: n }, (_, i) => String(i + 1).padStart(3, ' ')).join('\n')
})
const yamlStats = computed(() => {
  const lines = (yamlDraft.value.match(/\n/g)?.length ?? 0) + 1
  const bytes = new TextEncoder().encode(yamlDraft.value).length
  return { lines, bytes }
})
function onYamlScroll(e: Event) {
  const ta = e.target as HTMLTextAreaElement
  if (yamlLineNumsRef.value) yamlLineNumsRef.value.scrollTop = ta.scrollTop
}

async function loadYamlTemplate() {
  yamlLoading.value = true
  try {
    yamlDraft.value = await getAgentInstallYamlTemplate(selectedRole.value)
  } catch { /* */ } finally {
    yamlLoading.value = false
  }
}

function appendLog(t: string) {
  deployLog.value += t
  nextTick(() => {
    if (deployLogRef.value) deployLogRef.value.scrollTop = deployLogRef.value.scrollHeight
  })
}

async function runDeploy() {
  const a = currentAgent.value
  if (!a) return
  if (!yamlDraft.value.includes('{{AGENT_TOKEN}}')) {
    message.error('yaml 必须含 {{AGENT_TOKEN}} 占位符 (api_token 行); 已被你删掉, 重新加载模板?')
    return
  }
  if (a.onlineState !== 'NEVER') {
    return new Promise<void>((resolve) => {
      dialog.error({
        title: '重新部署 = 重置 token + 覆盖 config.yml',
        content: '日常更新走"升级" tab. 确认继续?',
        positiveText: '继续', negativeText: '取消',
        onPositiveClick: async () => { await actuallyDeploy(); resolve() },
        onNegativeClick: () => resolve()
      })
    })
  }
  await actuallyDeploy()
}

async function actuallyDeploy() {
  const a = currentAgent.value
  if (!a) return
  deployLog.value = ''
  deploying.value = true
  deployAbort = new AbortController()
  try {
    const dto: AgentInstallDTO = {
      role: selectedRole.value,
      configYaml: yamlDraft.value
    }
    await agentInstallStream(a.serverId, dto, appendLog, deployAbort.signal)
    appendLog('\n[nook-admin] ✅ 装机流完成\n')
    emit('dispatched')
    await loadData()
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : String(e)
    appendLog(`\n[nook-admin] ❌ 失败: ${msg}\n`)
    message.error(msg)
  } finally {
    deploying.value = false
    deployAbort = null
  }
}

function cancelDeploy() {
  if (deployAbort) deployAbort.abort()
}

// ---------- 生命周期 ----------
watch(open, async (v) => {
  if (v) {
    loadData()
    selectedRole.value = props.initialRole ?? 'frontline'
    selectedServerId.value = props.initialServerId
    deployLog.value = ''
    dispatchedTaskId.value = null
    upgradeStatus.value = 'WAITING'
    upgradeElapsed.value = 0
    await loadYamlTemplate()
  } else {
    stopPolling()
    if (deployAbort) deployAbort.abort()
  }
})

watch(selectedRole, async () => {
  if (selectedServerId.value) {
    const r = serverRole(selectedServerId.value)
    if (r !== 'unprovisioned' && r !== selectedRole.value) {
      selectedServerId.value = null
    }
  }
  // role 切换重新拉对应模板 (frontline/landing yaml 不一样)
  if (open.value) await loadYamlTemplate()
})

onMounted(() => { if (props.modelValue) loadData() })
onUnmounted(() => { stopPolling(); if (deployAbort) deployAbort.abort() })
</script>

<template>
  <NModal
    :show="open"
    preset="card"
    title="部署 / 升级 Agent"
    style="max-width: 64rem"
    :bordered="false"
    :mask-closable="false"
    @update:show="(v: boolean) => (open = v)"
  >
    <NSpin :show="loading">
      <div class="space-y-3">
        <!-- 角色 + server (一行) -->
        <div class="flex flex-wrap items-center gap-3">
          <NRadioGroup v-model:value="selectedRole" size="small">
            <NRadio v-for="opt in ROLE_OPTIONS" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </NRadio>
          </NRadioGroup>
          <NSelect
            v-model:value="selectedServerId"
            :options="serverOptions"
            filterable
            size="small"
            class="flex-1 min-w-[20rem]"
            :placeholder="`选 ${selectedRole} server`"
          />
        </div>

        <div v-if="currentAgent" class="flex items-center gap-2 text-xs flex-wrap">
          <NTag size="small" :type="AGENT_ONLINE_TAG_TYPE[currentAgent.onlineState]">
            {{ AGENT_ONLINE_LABELS[currentAgent.onlineState] }}
          </NTag>
          <span class="font-mono text-zinc-500">{{ currentAgent.agentVersion || '未装' }}</span>
          <div class="flex-1"></div>
          <NTag size="small" type="info">推荐: {{ recommended === 'upgrade' ? '升级' : '部署' }}</NTag>
        </div>

        <NTabs v-if="currentAgent" v-model:value="activeTab" type="line" size="small">
          <!-- ============ 升级 ============ -->
          <NTabPane name="upgrade" tab="升级 binary">
            <div class="space-y-2 mt-2">
              <NAlert type="info" :show-icon="false" size="small">
                仅替换 binary; <b>agent_token / config.yml / systemd unit 全保留</b>.
                重启窗口 ~10-20 秒, 期间断 1 次心跳 + 1-2 次任务轮询; xray / socks5 不动.
              </NAlert>
              <div v-if="!dispatchedTaskId || lastDispatchedFor !== currentAgent.serverName">
                <NButton
                  type="primary"
                  size="small"
                  :loading="upgrading"
                  :disabled="currentAgent.onlineState === 'OFFLINE' || currentAgent.onlineState === 'NEVER'"
                  @click="doUpgrade"
                >
                  <template #icon><NIcon><ArrowUp /></NIcon></template>
                  一键升级
                </NButton>
                <span v-if="currentAgent.onlineState === 'OFFLINE' || currentAgent.onlineState === 'NEVER'"
                      class="ml-3 text-xs text-zinc-500">agent 不在线</span>
              </div>
              <div v-else class="text-xs">
                <NTag size="small" :type="upgradeStatus === 'COMPLETED' ? 'success' : upgradeStatus === 'TIMEOUT' ? 'error' : 'info'">
                  {{ upgradeStatus === 'WAITING' ? `⏳ ${upgradeElapsed}s` : upgradeStatus === 'COMPLETED' ? '✅ 完成' : '⚠️ 超时' }}
                </NTag>
                <span class="ml-2 text-zinc-500">
                  task <code>{{ dispatchedTaskId.slice(0, 8) }}…</code> · v{{ lastDispatchedVersion }}
                  <span v-if="upgradeStatus === 'COMPLETED'"> → v{{ currentAgent.agentVersion }}</span>
                </span>
              </div>
            </div>
          </NTabPane>

          <!-- ============ 部署 ============ -->
          <NTabPane name="deploy" tab="重新部署">
            <div class="space-y-2 mt-2">
              <NAlert type="warning" :show-icon="false" size="small">
                <b>重置 agent_token</b> (旧心跳立刻失效) + 覆盖 <code>/home/nook-agent/etc/config.yml</code> (下面的 yaml).
                日常更新用"升级"; 此 tab 用于新机 / 救灾 / 改 yaml 字段集.
              </NAlert>

              <!-- yaml 编辑器 -->
              <NSpin :show="yamlLoading">
                <div class="flex items-center gap-2 text-xs mb-1">
                  <span class="text-zinc-500">config.yml (装机时 SSH 写到远端)</span>
                  <div class="flex-1"></div>
                  <NButton size="tiny" quaternary :disabled="yamlLoading || deploying" @click="loadYamlTemplate">
                    <template #icon><NIcon><RefreshCcw /></NIcon></template>
                    重置为默认模板
                  </NButton>
                </div>
                <div class="yaml-editor-wrap">
                  <pre ref="yamlLineNumsRef" class="yaml-line-nums">{{ yamlLineNumbers }}</pre>
                  <textarea
                    ref="yamlTaRef"
                    v-model="yamlDraft"
                    class="yaml-editor-ta"
                    placeholder="yaml..."
                    spellcheck="false"
                    :disabled="deploying"
                    @scroll="onYamlScroll"
                  ></textarea>
                </div>
                <div class="text-right text-xs text-zinc-500 font-mono mt-1">
                  {{ yamlStats.lines }} 行 · {{ yamlStats.bytes }} 字节
                </div>
              </NSpin>

              <div class="flex gap-2 pt-1">
                <NButton type="primary" size="small" :loading="deploying" @click="runDeploy">
                  <template #icon><NIcon><Rocket /></NIcon></template>
                  SSH 自动装机
                </NButton>
                <NButton v-if="deploying" size="small" @click="cancelDeploy">中止</NButton>
              </div>
              <pre v-if="deployLog" ref="deployLogRef" class="deploy-log">{{ deployLog }}</pre>
            </div>
          </NTabPane>
        </NTabs>
        <div v-else class="text-center text-zinc-500 text-sm py-4">↑ 先选服务器</div>
      </div>
    </NSpin>

    <template #footer>
      <div class="flex justify-end">
        <NButton size="small" :disabled="deploying" @click="open = false">关闭</NButton>
      </div>
    </template>
  </NModal>
</template>

<style scoped>
.yaml-editor-wrap {
  display: flex;
  align-items: stretch;
  border: 1px solid rgba(127, 127, 127, 0.25);
  border-radius: 4px;
  overflow: hidden;
  background: #fafafc;
  height: 18rem;
}
.yaml-line-nums {
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  color: #94a3b8;
  background: rgba(127, 127, 127, 0.08);
  padding: 8px 6px 8px 8px;
  margin: 0;
  user-select: none;
  white-space: pre;
  text-align: right;
  border-right: 1px solid rgba(127, 127, 127, 0.25);
  line-height: 1.5;
  overflow: hidden;
  flex-shrink: 0;
}
.yaml-editor-ta {
  flex: 1;
  border: none;
  outline: none;
  resize: none;
  padding: 8px 10px;
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  line-height: 1.5;
  background: transparent;
  color: #1e293b;
  caret-color: #1e293b;
  overflow: auto;
  min-width: 0;
  tab-size: 2;
  white-space: pre;
  word-wrap: normal;
  overflow-wrap: normal;
}
html[data-theme='dark'] .yaml-editor-wrap {
  background: #1c1c20;
  border-color: rgba(255, 255, 255, 0.12);
}
html[data-theme='dark'] .yaml-line-nums {
  background: rgba(255, 255, 255, 0.04);
  color: #71717a;
  border-right-color: rgba(255, 255, 255, 0.12);
}
html[data-theme='dark'] .yaml-editor-ta {
  color: #e4e4e7;
  caret-color: #e4e4e7;
}
.deploy-log {
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  line-height: 1.5;
  background: #1c1c20;
  color: #e4e4e7;
  padding: 8px 10px;
  border-radius: 4px;
  height: 16rem;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
}
</style>
