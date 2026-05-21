<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ArrowUp, Rocket } from 'lucide-vue-next'
import {
  NAlert,
  NButton,
  NForm,
  NFormItem,
  NIcon,
  NInput,
  NInputNumber,
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
  listNetworkInterfaces,
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

// ---------- 推荐 tab ----------
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
    content: `${a.serverName}: ${a.agentVersion ?? '?'} → backend 当前版本. 重启窗口 ~10-20 秒, 期间断 1 次心跳; xray/socks5 不动.`,
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

// ---------- 部署表单 ----------
// 选 server 后 SSH 拉远端网卡; 失败/未选时只剩 auto
const nicOptions = ref<Array<{ label: string; value: string }>>([
  { label: 'auto (默认路由出口网卡)', value: 'auto' }
])
const nicLoading = ref(false)

async function loadNicOptions(serverId: string) {
  nicLoading.value = true
  try {
    const ifaces = await listNetworkInterfaces(serverId)
    nicOptions.value = [
      { label: 'auto (默认路由出口网卡)', value: 'auto' },
      ...ifaces.map((n) => ({ label: n, value: n }))
    ]
  } catch {
    nicOptions.value = [{ label: 'auto (默认路由出口网卡)', value: 'auto' }]
  } finally {
    nicLoading.value = false
  }
}

function defaultForm(): AgentInstallDTO {
  return {
    role: selectedRole.value,
    backendTimeoutSeconds: 30,
    heartbeatIntervalSeconds: 60,
    nicIntervalSeconds: 300,
    nicInterface: 'auto',
    pollerIntervalSeconds: 30
  }
}
const form = reactive<AgentInstallDTO>(defaultForm())

function resetForm() {
  Object.assign(form, defaultForm())
}

// ---------- SSH 部署 (流式) ----------
const deploying = ref(false)
const deployLog = ref('')
const deployLogRef = ref<HTMLPreElement | null>(null)
let deployAbort: AbortController | null = null

function appendLog(t: string) {
  deployLog.value += t
  nextTick(() => {
    if (deployLogRef.value) deployLogRef.value.scrollTop = deployLogRef.value.scrollHeight
  })
}

async function runDeploy() {
  const a = currentAgent.value
  if (!a) return
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
    const dto: AgentInstallDTO = { ...form, role: selectedRole.value }
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
watch(open, (v) => {
  if (v) {
    loadData()
    selectedRole.value = props.initialRole ?? 'frontline'
    selectedServerId.value = props.initialServerId
    deployLog.value = ''
    dispatchedTaskId.value = null
    upgradeStatus.value = 'WAITING'
    upgradeElapsed.value = 0
    resetForm()
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
  form.role = selectedRole.value
})

// 选 server 后 SSH 拉网卡填下拉
watch(selectedServerId, (id) => {
  if (id) {
    loadNicOptions(id)
  } else {
    nicOptions.value = [{ label: 'auto (默认路由出口网卡)', value: 'auto' }]
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
        <!-- 角色 + server -->
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
            <div class="space-y-3 mt-2">
              <NAlert type="warning" :show-icon="false" size="small">
                重置 agent_token + 覆盖 <code>/home/nook-agent/etc/config.yml</code>.
                字段全必填, 后端不持兜底默认.
              </NAlert>

              <NForm :model="form" label-placement="left" label-width="auto" size="small">
                <NFormItem label="backend 超时 (s)" path="backendTimeoutSeconds">
                  <NInputNumber v-model:value="form.backendTimeoutSeconds" :min="5" :max="600" class="w-40" />
                </NFormItem>
                <NFormItem label="心跳间隔 (s)" path="heartbeatIntervalSeconds">
                  <NInputNumber v-model:value="form.heartbeatIntervalSeconds" :min="10" :max="3600" class="w-40" />
                </NFormItem>
                <NFormItem label="NIC 上报间隔 (s)" path="nicIntervalSeconds">
                  <NInputNumber v-model:value="form.nicIntervalSeconds" :min="60" :max="3600" class="w-40" />
                </NFormItem>
                <NFormItem label="NIC 网卡" path="nicInterface">
                  <NSelect
                    v-model:value="form.nicInterface"
                    :options="nicOptions"
                    :loading="nicLoading"
                    tag
                    filterable
                    class="w-60"
                    placeholder="选 server 后自动拉取"
                  />
                </NFormItem>
                <NFormItem label="任务轮询间隔 (s)" path="pollerIntervalSeconds">
                  <NInputNumber v-model:value="form.pollerIntervalSeconds" :min="5" :max="600" class="w-40" />
                </NFormItem>
              </NForm>
              <div v-if="selectedRole === 'frontline'" class="text-xs text-zinc-500">
                ℹ️ frontline 必须先装 xray; xray 路径 / API 端口由 backend 从 xray_node 读取.
              </div>

              <div class="flex gap-2 pt-1">
                <NButton type="primary" size="small" :loading="deploying" @click="runDeploy">
                  <template #icon><NIcon><Rocket /></NIcon></template>
                  SSH 自动装机
                </NButton>
                <NButton size="small" :disabled="deploying" @click="resetForm">重置表单</NButton>
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
