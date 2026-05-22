<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ArrowUp, HelpCircle, Rocket } from 'lucide-vue-next'
import {
  NAlert,
  NButton,
  NDivider,
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
  NTooltip,
  useDialog,
  useMessage
} from 'naive-ui'
import {
  agentInstallStream,
  getAgentInstallMeta,
  listNetworkInterfaces,
  pageServers,
  type AgentInstallDTO,
  type AgentInstallMeta
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
    content: '是否确认升级?',
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

// ---------- 装机元信息 (后端常量, readonly 展示) ----------
const installMeta = ref<AgentInstallMeta | null>(null)

async function loadInstallMeta() {
  try {
    installMeta.value = await getAgentInstallMeta(selectedRole.value, selectedServerId.value)
    // backend 已知数据 (URL + SSH 默认) 覆盖到表单, admin 直接基于真实值改
    applyMetaToForm(form, installMeta.value)
  } catch {
    installMeta.value = null
  }
}

/** frontline 选了 server 但 xray_node 没记录 → 装机会失败, 前端拦; landing 永远 false. */
const xrayMissing = computed(() =>
  selectedRole.value === 'frontline'
  && !!selectedServerId.value
  && (!installMeta.value?.xrayBin || installMeta.value?.xrayApiPort == null)
)

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
    // 120s 覆盖跨境 binary 下载 (~60s 实测) + 余量; 常态心跳/poll 实际秒级返回, 不会真吃满
    backendTimeoutSeconds: 120,
    heartbeatIntervalSeconds: 60,
    nicIntervalSeconds: 300,
    nicInterface: 'auto',
    // 60s: admin 派任务后最差等 1min 才被 agent 拾取; 比 30s 省一半 backend 请求, 体感无差
    pollerIntervalSeconds: 60,
    // 路径 + URL 默认 (前端持有, 改了对本次装机生效); backendUrl 选 server 后从 meta 覆盖
    nookHome: '/home/nook-agent',
    binPath: '/home/nook-agent/nook-agent',
    configPath: '/home/nook-agent/config.yml',
    systemdUnitPath: '/etc/systemd/system/nook-agent.service',
    backendUrl: '',
    // SSH 默认 (跟 socks5 deploy 一致); 选了 server 后会被 install-meta 里 resource_server 存值覆盖
    sshTimeoutSeconds: 60,
    sshOpTimeoutSeconds: 60,
    sshUploadTimeoutSeconds: 180,
    installTimeoutSeconds: 600
  }
}
const form = reactive<AgentInstallDTO>(defaultForm())

/** 把 meta 里 backend 已知值 (URL + SSH 4 件套) 应用到给定 form 对象上 (in-place). */
function applyMetaToForm(target: AgentInstallDTO, m: AgentInstallMeta | null) {
  if (!m) return
  if (m.backendUrl) target.backendUrl = m.backendUrl
  if (m.sshTimeoutSeconds != null) target.sshTimeoutSeconds = m.sshTimeoutSeconds
  if (m.sshOpTimeoutSeconds != null) target.sshOpTimeoutSeconds = m.sshOpTimeoutSeconds
  if (m.sshUploadTimeoutSeconds != null) target.sshUploadTimeoutSeconds = m.sshUploadTimeoutSeconds
  if (m.installTimeoutSeconds != null) target.installTimeoutSeconds = m.installTimeoutSeconds
}

/** Reset: 先拉 meta 算出最终值, 一次性 assign — 避免先 default 后 prefill 的两帧闪烁. */
async function resetForm() {
  const fresh = defaultForm()
  try {
    const m = await getAgentInstallMeta(selectedRole.value, selectedServerId.value)
    installMeta.value = m
    applyMetaToForm(fresh, m)
  } catch {
    installMeta.value = null
  }
  Object.assign(form, fresh)
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

function runDeploy() {
  const a = currentAgent.value
  if (!a) return
  if (a.onlineState !== 'NEVER') {
    dialog.warning({
      title: '确认重新部署',
      content: '是否确认部署?',
      positiveText: '继续', negativeText: '取消',
      // 不 await, 让确认框立即关闭, 部署在后台跑
      onPositiveClick: () => { actuallyDeploy() }
    })
    return
  }
  actuallyDeploy()
}

async function actuallyDeploy() {
  const a = currentAgent.value
  if (!a) return
  deployLog.value = ''
  deploying.value = true
  deployAbort = new AbortController()
  try {
    // frontline 必带 xray bin/api_port (后端从 install-meta 已查过, 这里只是把前端拿到的值回塞)
    const dto: AgentInstallDTO = {
      ...form,
      role: selectedRole.value,
      xrayBin: selectedRole.value === 'frontline' ? installMeta.value?.xrayBin : undefined,
      xrayApiPort: selectedRole.value === 'frontline' ? installMeta.value?.xrayApiPort : undefined
    }
    await agentInstallStream(a.serverId, dto, appendLog, deployAbort.signal)
    appendLog('\n[nook-admin] ✅ 装机流完成\n')
    emit('dispatched')
    await loadData()
    // loadData 触发 watch(currentAgent) → 把 activeTab 重置成 recommended (新装的还没心跳, recommended='deploy');
    // 等 watch flush 完再切, 不然立即赋值会被覆盖回去
    await nextTick()
    activeTab.value = 'upgrade'
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
    // resetForm 内部已 await meta + 一次性 assign, 不再单独 loadInstallMeta (避免重复请求 + 闪烁)
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
  loadInstallMeta()
})

// 选 server 后 SSH 拉网卡 + 重拉 install-meta (frontline 时附带 xray 信息)
watch(selectedServerId, (id) => {
  if (id) {
    loadNicOptions(id)
  } else {
    nicOptions.value = [{ label: 'auto (默认路由出口网卡)', value: 'auto' }]
  }
  loadInstallMeta()
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
          <NTabPane name="upgrade">
            <template #tab>
              <span>升级二进制包</span>
              <NTooltip trigger="hover">
                <template #trigger>
                  <NIcon class="hint-icon"><HelpCircle :size="14" /></NIcon>
                </template>
                仅替换二进制包; agent_token / config.yml / systemd unit 全保留. 重启窗口 ~10-20 秒, 期间断 1 次心跳 + 1-2 次任务轮询; xray / socks5 不动.
              </NTooltip>
            </template>
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
          <NTabPane name="deploy">
            <template #tab>
              <span>重新部署</span>
              <NTooltip trigger="hover">
                <template #trigger>
                  <NIcon class="hint-icon"><HelpCircle :size="14" /></NIcon>
                </template>
                覆盖远端 config.yml + 重写 systemd unit + 重启 agent. 全字段 admin 自定 (前端默认 + 选 server 后 backend 已知值 prefill); 后端不兜底.
              </NTooltip>
            </template>
            <div class="space-y-3 mt-2">
              <NAlert v-if="xrayMissing" type="error" :show-icon="false" size="small">
                ⚠ 该 server 未装 xray (xray_node 表无记录), frontline 装机会失败. 请先到 xray 管理装上.
              </NAlert>

              <NForm :model="form" label-placement="left" label-width="auto" size="small">
                <NDivider title-placement="left" class="form-section">
                  <span class="text-xs text-zinc-500">Agent 参数</span>
                </NDivider>
                <NFormItem path="backendTimeoutSeconds">
                  <template #label>
                    backend 超时 (s)
                    <NTooltip trigger="hover">
                      <template #trigger>
                        <NIcon class="hint-icon"><HelpCircle :size="14" /></NIcon>
                      </template>
                      agent → backend HTTP 客户端超时. 跨境抖动 / natapp 隧道场景可调大到 60-120.
                    </NTooltip>
                  </template>
                  <NInputNumber v-model:value="form.backendTimeoutSeconds" :min="5" :max="600" class="w-40" />
                </NFormItem>
                <NFormItem path="heartbeatIntervalSeconds">
                  <template #label>
                    心跳间隔 (s)
                    <NTooltip trigger="hover">
                      <template #trigger>
                        <NIcon class="hint-icon"><HelpCircle :size="14" /></NIcon>
                      </template>
                      agent → backend 心跳频率. backend 60s 无心跳 → WARN, 180s → TEMP_UNHEALTHY, 300s → OFFLINE.
                    </NTooltip>
                  </template>
                  <NInputNumber v-model:value="form.heartbeatIntervalSeconds" :min="10" :max="3600" class="w-40" />
                </NFormItem>
                <NFormItem path="nicIntervalSeconds">
                  <template #label>
                    NIC 上报间隔 (s)
                    <NTooltip trigger="hover">
                      <template #trigger>
                        <NIcon class="hint-icon"><HelpCircle :size="14" /></NIcon>
                      </template>
                      vnstat 网卡流量上报频率. 越短数据越实时, 但 backend 请求量越大; 默认 5min.
                    </NTooltip>
                  </template>
                  <NInputNumber v-model:value="form.nicIntervalSeconds" :min="60" :max="3600" class="w-40" />
                </NFormItem>
                <NFormItem path="nicInterface">
                  <template #label>
                    NIC 网卡
                    <NTooltip trigger="hover">
                      <template #trigger>
                        <NIcon class="hint-icon"><HelpCircle :size="14" /></NIcon>
                      </template>
                      vnstat 采样的网卡名. auto = agent 自动用 /proc/net/route 默认路由出口网卡; 多网卡场景可选具体网卡名.
                    </NTooltip>
                  </template>
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
                <NFormItem path="pollerIntervalSeconds">
                  <template #label>
                    任务轮询间隔 (s)
                    <NTooltip trigger="hover">
                      <template #trigger>
                        <NIcon class="hint-icon"><HelpCircle :size="14" /></NIcon>
                      </template>
                      agent 轮询 backend 任务队列频率 (升级 / 改配置 / 清日志 等 task 走这条路). 越短任务越及时, backend 请求量越大.
                    </NTooltip>
                  </template>
                  <NInputNumber v-model:value="form.pollerIntervalSeconds" :min="5" :max="600" class="w-40" />
                </NFormItem>

                <NDivider title-placement="left" class="form-section">
                  <span class="text-xs text-zinc-500">路径 + URL (本次装机生效)</span>
                </NDivider>
                <NFormItem path="backendUrl">
                  <template #label>
                    Backend URL
                    <NTooltip trigger="hover">
                      <template #trigger>
                        <NIcon class="hint-icon"><HelpCircle :size="14" /></NIcon>
                      </template>
                      agent yaml backend.api_url + 装机脚本 curl binary 都用此值. 默认从 backend config 读, 可改 (e.g. 换 natapp 地址).
                    </NTooltip>
                  </template>
                  <NInput v-model:value="form.backendUrl" placeholder="https://your-backend.example.com" class="w-96" />
                </NFormItem>
                <NFormItem path="nookHome">
                  <template #label>
                    装机根目录
                    <NTooltip trigger="hover">
                      <template #trigger>
                        <NIcon class="hint-icon"><HelpCircle :size="14" /></NIcon>
                      </template>
                      agent 文件 (binary + config) 落地的目录; 改路径要确保 server 文件系统已存在或脚本能 mkdir.
                    </NTooltip>
                  </template>
                  <NInput v-model:value="form.nookHome" class="w-96" />
                </NFormItem>
                <NFormItem path="binPath">
                  <template #label>
                    Binary 路径
                    <NTooltip trigger="hover">
                      <template #trigger>
                        <NIcon class="hint-icon"><HelpCircle :size="14" /></NIcon>
                      </template>
                      agent binary 绝对路径; systemd ExecStart + yaml runtime.bin_path 都用此. 默认在 nookHome 下.
                    </NTooltip>
                  </template>
                  <NInput v-model:value="form.binPath" class="w-96" />
                </NFormItem>
                <NFormItem path="configPath">
                  <template #label>
                    Config 路径
                    <NTooltip trigger="hover">
                      <template #trigger>
                        <NIcon class="hint-icon"><HelpCircle :size="14" /></NIcon>
                      </template>
                      agent config.yml 绝对路径; systemd ExecStart 用 -c 指向此. 后续改配置也会写到此处.
                    </NTooltip>
                  </template>
                  <NInput v-model:value="form.configPath" class="w-96" />
                </NFormItem>
                <NFormItem path="systemdUnitPath">
                  <template #label>
                    systemd unit 路径
                    <NTooltip trigger="hover">
                      <template #trigger>
                        <NIcon class="hint-icon"><HelpCircle :size="14" /></NIcon>
                      </template>
                      systemd unit 文件位置; 默认 /etc/systemd/system/nook-agent.service. 改 unit 名也会影响 systemctl 命令.
                    </NTooltip>
                  </template>
                  <NInput v-model:value="form.systemdUnitPath" class="w-96" />
                </NFormItem>

                <NDivider title-placement="left" class="form-section">
                  <span class="text-xs text-zinc-500">SSH 参数 (本次装机生效, 不回写表)</span>
                </NDivider>
                <NFormItem path="sshTimeoutSeconds">
                  <template #label>
                    SSH 握手超时 (s)
                    <NTooltip trigger="hover">
                      <template #trigger>
                        <NIcon class="hint-icon"><HelpCircle :size="14" /></NIcon>
                      </template>
                      建 SSH 会话握手阶段超时 (跨境抖动可调大). 默认从 server 表读, 这里改只对本次装机生效.
                    </NTooltip>
                  </template>
                  <NInputNumber v-model:value="form.sshTimeoutSeconds" :min="5" :max="600" class="w-40" />
                </NFormItem>
                <NFormItem path="sshOpTimeoutSeconds">
                  <template #label>
                    SSH 单条命令超时 (s)
                    <NTooltip trigger="hover">
                      <template #trigger>
                        <NIcon class="hint-icon"><HelpCircle :size="14" /></NIcon>
                      </template>
                      装机脚本里单条命令 (apt / curl / systemctl) 最大耗时. 单步卡过这值会中断本次装机.
                    </NTooltip>
                  </template>
                  <NInputNumber v-model:value="form.sshOpTimeoutSeconds" :min="5" :max="300" class="w-40" />
                </NFormItem>
                <NFormItem path="sshUploadTimeoutSeconds">
                  <template #label>
                    SCP 上传超时 (s)
                    <NTooltip trigger="hover">
                      <template #trigger>
                        <NIcon class="hint-icon"><HelpCircle :size="14" /></NIcon>
                      </template>
                      SCP 单文件上传超时 (装机脚本 ~几 KB, 默认 180 余量足). 慢链路或大文件调大.
                    </NTooltip>
                  </template>
                  <NInputNumber v-model:value="form.sshUploadTimeoutSeconds" :min="5" :max="600" class="w-40" />
                </NFormItem>
                <NFormItem path="installTimeoutSeconds">
                  <template #label>
                    安装整体超时 (s)
                    <NTooltip trigger="hover">
                      <template #trigger>
                        <NIcon class="hint-icon"><HelpCircle :size="14" /></NIcon>
                      </template>
                      整段装机脚本最大耗时 (含 binary 下载 + xray 等启动). natapp 慢链路下 binary ~5MB 约 50s, 600s 默认够用.
                    </NTooltip>
                  </template>
                  <NInputNumber v-model:value="form.installTimeoutSeconds" :min="60" :max="3600" class="w-40" />
                </NFormItem>
              </NForm>
              <div class="flex gap-2 pt-1 items-center">
                <NButton
                  type="primary"
                  size="small"
                  :loading="deploying"
                  :disabled="xrayMissing"
                  @click="runDeploy"
                >
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
.hint-icon {
  margin-left: 4px;
  vertical-align: middle;
  color: #a1a1aa;
  cursor: help;
}
/* NDivider 默认上下 28px margin 太占地; 表单分组用紧凑值 */
.form-section {
  margin-top: 8px !important;
  margin-bottom: 4px !important;
}
.hint-icon:hover {
  color: #6366f1;
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
