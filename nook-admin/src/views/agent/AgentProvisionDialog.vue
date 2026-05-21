<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { ArrowUp, Rocket } from 'lucide-vue-next'
import {
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
import { agentInstallStream, pageServers } from '@/api/resource/server'
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
    content: `${a.serverName}: ${a.agentVersion ?? '?'} → backend 当前版本`,
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
const logRef = ref<HTMLPreElement | null>(null)
let deployAbort: AbortController | null = null

function appendLog(t: string) {
  deployLog.value += t
  // 自动滚到底
  requestAnimationFrame(() => {
    if (logRef.value) logRef.value.scrollTop = logRef.value.scrollHeight
  })
}

async function runDeploy() {
  const a = currentAgent.value
  if (!a) return
  if (a.onlineState !== 'NEVER') {
    return new Promise<void>((resolve) => {
      dialog.error({
        title: '重新部署会重置 token + 覆盖 config.yml',
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
    await agentInstallStream(a.serverId, selectedRole.value, appendLog, deployAbort.signal)
    appendLog('\n[nook-admin] ✅ 装机流完成\n')
    emit('dispatched')
    // 刷一下 agent 列表
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
watch(open, (v) => {
  if (v) {
    loadData()
    selectedRole.value = props.initialRole ?? 'frontline'
    selectedServerId.value = props.initialServerId
    deployLog.value = ''
    dispatchedTaskId.value = null
    upgradeStatus.value = 'WAITING'
    upgradeElapsed.value = 0
  } else {
    stopPolling()
    if (deployAbort) deployAbort.abort()
  }
})

watch(selectedRole, () => {
  if (selectedServerId.value) {
    const r = serverRole(selectedServerId.value)
    if (r !== 'unprovisioned' && r !== selectedRole.value) {
      selectedServerId.value = null
    }
  }
})

onMounted(() => { if (props.modelValue) loadData() })
onUnmounted(() => { stopPolling(); if (deployAbort) deployAbort.abort() })
</script>

<template>
  <NModal
    :show="open"
    preset="card"
    title="部署 / 升级 Agent"
    style="max-width: 56rem"
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

        <!-- 当前 agent 状态 (一行 tag) -->
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
                      class="ml-3 text-xs text-zinc-500">
                  agent 不在线
                </span>
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
              <div class="flex gap-2">
                <NButton type="primary" size="small" :loading="deploying" @click="runDeploy">
                  <template #icon><NIcon><Rocket /></NIcon></template>
                  SSH 自动装机
                </NButton>
                <NButton v-if="deploying" size="small" @click="cancelDeploy">中止</NButton>
              </div>
              <pre
                v-if="deployLog"
                ref="logRef"
                class="deploy-log"
              >{{ deployLog }}</pre>
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
.deploy-log {
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  line-height: 1.5;
  background: #1c1c20;
  color: #e4e4e7;
  padding: 8px 10px;
  border-radius: 4px;
  height: 22rem;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
}
</style>
