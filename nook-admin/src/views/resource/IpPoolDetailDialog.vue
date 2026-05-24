<script setup lang="ts">
import { computed, h, ref, watch } from 'vue'
import {
  Activity,
  AlertCircle,
  CheckCircle2,
  Copy,
  FileText,
  Globe2,
  KeyRound,
  Pencil,
  Plug,
  RefreshCcw,
  Rocket,
  Server as ServerIcon,
  Trash2,
  Wifi,
  XCircle,
  Zap
} from 'lucide-vue-next'
import {
  NButton,
  NDescriptions,
  NDescriptionsItem,
  NEmpty,
  NIcon,
  NModal,
  NSpin,
  NTabs,
  NTabPane,
  NTag,
  NTooltip,
  useMessage
} from 'naive-ui'
import {
  IP_POOL_LIFECYCLE_LABELS,
  IP_POOL_LIFECYCLE_TAG_TYPE,
  IP_POOL_STATUS_LABELS,
  getIpPoolDetail,
  getSocks5Status,
  type ResourceIpPool,
  type Socks5ServiceStatus
} from '@/api/resource/ip-pool'
import { IP_TYPE_CODE_LABELS, listIpTypes, type ResourceIpType } from '@/api/resource/ip-type'
import { formatDateTime } from '@/utils/date'

/**
 * IP 池条目详情 — 6 tab workflow 视图.
 *
 * Tabs (按部署 workflow 排序):
 *   ① 概览       — 部署进度 + 端口可达 + 状态徽章
 *   ② SSH 信息   — 凭据 + 编辑入口 + 连通性
 *   ③ SOCKS5 服务 — dante 配置 + 服务状态 + 编辑/装机/拨测/日志
 *   ④ Agent      — token + 心跳 + 健康徽章 + 安装入口
 *   ⑤ 服务器信息  — host / kernel / OS / mem / disk / UFW (走 socks5-status)
 *   ⑥ 账面       — 带宽 / 流量 / 月费 / 到期
 *
 * 视图 / 编辑严格分离: 每 tab 顶部有自己的 actions area, 不再有统一 dropdown.
 * 底部 footer 危险区域 (退役 / 启用 / 删除) 跟视图分离.
 * socks5-status 响应缓存到 statusData, 概览 / SOCKS5 / 服务器 tabs 共享展示.
 */
interface Props {
  modelValue: boolean
  ipId?: string | null
}
const props = defineProps<Props>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'edit-core', ip: ResourceIpPool): void
  (e: 'edit-credential', ip: ResourceIpPool): void
  (e: 'edit-billing', ip: ResourceIpPool): void
  (e: 'edit-socks5', ip: ResourceIpPool): void
  (e: 'deploy', ip: ResourceIpPool): void
  (e: 'test', ip: ResourceIpPool): void
  (e: 'view-log', ip: ResourceIpPool): void
  (e: 'provision-agent', ip: ResourceIpPool): void
  (e: 'lifecycle-retire', ip: ResourceIpPool): void
  (e: 'lifecycle-restore', ip: ResourceIpPool): void
  (e: 'delete', ip: ResourceIpPool): void
  (e: 'refresh'): void
}>()

const message = useMessage()
const loading = ref(false)
const detail = ref<ResourceIpPool | null>(null)
const error = ref<string>('')
const ipTypes = ref<ResourceIpType[]>([])
const activeTab = ref<'overview' | 'ssh' | 'socks5' | 'agent' | 'server' | 'billing'>('overview')
let ipTypesLoaded = false

// ===== 远端状态 (socks5-status 一次加载, 多 tab 共用) =====
const statusLoading = ref(false)
const statusData = ref<Socks5ServiceStatus | null>(null)
const statusError = ref<string>('')

async function ensureIpTypes() {
  if (ipTypesLoaded) return
  try {
    ipTypes.value = await listIpTypes()
    ipTypesLoaded = true
  } catch { /* */ }
}

function ipTypeName(ipTypeId?: string): string {
  if (!ipTypeId) return '-'
  const t = ipTypes.value.find((x) => x.id === ipTypeId)
  if (!t) return ipTypeId
  return IP_TYPE_CODE_LABELS[t.code] || t.name || t.code
}

function statusTagType(status?: string) {
  switch (status) {
    case 'OCCUPIED': return 'warning'
    case 'AVAILABLE': return 'success'
    case 'COOLING': return 'info'
    case 'RESERVED': return 'default'
    default: return 'default'
  }
}

const socks5Endpoint = computed(() => {
  if (!detail.value?.ipAddress || !detail.value.socks5Port) return ''
  return `${detail.value.ipAddress}:${detail.value.socks5Port}`
})

/** SOCKS5 凭据齐才能拨测 */
const canTest = computed(() =>
  !!detail.value?.ipAddress && !!detail.value?.socks5Port
  && !!detail.value?.socks5Username && !!detail.value?.socks5Password
)
/** 自部署 + SSH 密码齐才能拉远端状态 / 日志 */
const canManage = computed(() => detail.value?.provisionMode === 1 && !!detail.value?.sshPassword)
const isSelfDeploy = computed(() => detail.value?.provisionMode === 1)
const isLive = computed(() => detail.value?.lifecycleState === 'LIVE')
const isInstalling = computed(() =>
  detail.value?.lifecycleState === 'INSTALLING' || detail.value?.lifecycleState === 'READY')
const isRetired = computed(() => detail.value?.lifecycleState === 'RETIRED')
const sshComplete = computed(() =>
  !!detail.value?.sshHost && !!detail.value?.sshUser && !!detail.value?.sshPassword)
const socks5Installed = computed(() =>
  !!detail.value?.installedAt && detail.value?.lifecycleState === 'LIVE')

// ===== 部署进度时间线 (4 step) =====
interface DeployStep {
  key: string
  label: string
  done: boolean
  hint?: string
}
const deploySteps = computed<DeployStep[]>(() => [
  {
    key: 'ssh',
    label: 'SSH 凭据',
    done: sshComplete.value,
    hint: sshComplete.value ? `${detail.value?.sshUser}@${detail.value?.sshHost}` : '在 SSH 信息 tab 配置'
  },
  {
    key: 'socks5',
    label: '部署 SOCKS5',
    done: socks5Installed.value,
    hint: socks5Installed.value
      ? `已装机 ${formatDateTime(detail.value?.installedAt)}`
      : '在 SOCKS5 tab 装机'
  },
  {
    key: 'agent',
    label: '安装 Agent',
    done: !!detail.value?.agentToken,
    hint: detail.value?.agentToken ? '已注册 token' : '在 Agent tab 安装'
  },
  {
    key: 'health',
    label: '心跳健康',
    done: isAgentOnline.value,
    hint: detail.value?.lastHealthAt
      ? `最近心跳 ${relativeTime(detail.value.lastHealthAt)}`
      : '装好 agent 后会自动上报'
  }
])

const isAgentOnline = computed(() => {
  if (!detail.value?.lastHealthAt) return false
  const last = new Date(detail.value.lastHealthAt).getTime()
  return Date.now() - last < 5 * 60 * 1000
})

/** 把 ISO datetime 转 "刚刚 / 5 分钟前 / 1 小时前 / 2 天前" */
function relativeTime(iso?: string): string {
  if (!iso) return '-'
  const t = new Date(iso).getTime()
  const diffMs = Date.now() - t
  if (diffMs < 0) return formatDateTime(iso)
  const sec = Math.floor(diffMs / 1000)
  if (sec < 30) return '刚刚'
  if (sec < 3600) return `${Math.floor(sec / 60)} 分钟前`
  if (sec < 86400) return `${Math.floor(sec / 3600)} 小时前`
  return `${Math.floor(sec / 86400)} 天前`
}

const agentHealthLabel = computed(() => {
  if (!detail.value?.agentToken) return { text: '未安装', type: 'default' as const }
  if (!detail.value?.lastHealthAt) return { text: '已装未上线', type: 'warning' as const }
  return isAgentOnline.value
    ? { text: '在线', type: 'success' as const }
    : { text: '离线', type: 'error' as const }
})

// ===== 远端探测 — 端口可达性 + dante listening =====
/** 用 statusData 推 SSH/SOCKS5 可达性. statusData 不存在 = 未探测 */
const sshReachable = computed<'unknown' | 'open' | 'closed'>(() => {
  if (statusError.value) return 'closed'
  if (!statusData.value) return 'unknown'
  // status 拿到了 hostInfo 就说明 SSH 通了 (探测脚本就是 SSH 跑出来的)
  return statusData.value.hostInfo ? 'open' : 'unknown'
})
const socks5Listening = computed<'unknown' | 'listening' | 'down'>(() => {
  if (!statusData.value) return 'unknown'
  if (statusData.value.active !== 'active') return 'down'
  if (statusData.value.listening && detail.value?.socks5Port
      && statusData.value.listening.includes(`:${detail.value.socks5Port}`)) {
    return 'listening'
  }
  return 'down'
})

async function copyToClipboard(value: string | undefined, label: string) {
  if (!value) {
    message.warning(`${label} 为空`)
    return
  }
  try {
    await navigator.clipboard.writeText(value)
    message.success(`已复制 ${label}`)
  } catch {
    message.warning('复制失败')
  }
}

async function loadDetail(id: string) {
  loading.value = true
  error.value = ''
  detail.value = null
  // 切 IP 时清掉远端状态缓存
  statusData.value = null
  statusError.value = ''
  try {
    detail.value = await getIpPoolDetail(id)
  } catch (e) {
    error.value = (e as Error).message || '加载失败'
  } finally {
    loading.value = false
  }
}

/** 拉远端 socks5-status (SSH + dante + hostInfo 一把抓; SOCKS5/服务器 tabs 共用) */
async function loadStatus() {
  if (!props.ipId) return
  if (!canManage.value) {
    statusError.value = '需要先配置 SSH 凭据才能探测'
    return
  }
  statusLoading.value = true
  statusError.value = ''
  try {
    statusData.value = await getSocks5Status(props.ipId)
  } catch (e) {
    statusError.value = (e as Error).message || '拉取状态失败'
  } finally {
    statusLoading.value = false
  }
}

/** 父组件刷新事件: 重拉 detail + 失效 status 缓存 */
async function refresh() {
  if (props.ipId) {
    await loadDetail(props.ipId)
    emit('refresh')
  }
}
defineExpose({ refresh })

watch(
  () => [props.modelValue, props.ipId],
  ([open, ipId]) => {
    if (!open) return
    activeTab.value = 'overview'
    void ensureIpTypes()
    if (typeof ipId === 'string' && ipId.length > 0) {
      void loadDetail(ipId)
    } else {
      detail.value = null
      error.value = 'ipId 缺失'
    }
  },
  { immediate: false }
)

/** 切到需要远端状态的 tab 时, 若未加载过则自动拉一次 (减少用户手动点击) */
watch(activeTab, (tab) => {
  if ((tab === 'server' || tab === 'socks5') && !statusData.value && !statusLoading.value
      && !statusError.value && canManage.value) {
    void loadStatus()
  }
})

function close() {
  emit('update:modelValue', false)
}

/** 屏蔽 agent_token / SOCKS5 密码 等敏感字段的展示 (保留前 6 后 4) */
function maskSecret(s?: string): string {
  if (!s) return '-'
  if (s.length <= 12) return s.slice(0, 2) + '****' + s.slice(-2)
  return s.slice(0, 6) + '****' + s.slice(-4)
}
</script>

<template>
  <NModal
    :show="modelValue"
    preset="card"
    title="IP 详情"
    style="max-width: 64rem; width: 92vw"
    :bordered="false"
    @update:show="(v: boolean) => emit('update:modelValue', v)"
  >
    <template #header>
      <div class="flex items-center gap-2">
        <NIcon :size="18" :depth="2"><Globe2 /></NIcon>
        <span>IP 详情</span>
        <span v-if="detail" class="font-mono text-sm text-zinc-500">{{ detail.ipAddress }}</span>
      </div>
    </template>
    <template v-if="detail" #header-extra>
      <NTag size="small" :type="IP_POOL_LIFECYCLE_TAG_TYPE[detail.lifecycleState] || 'default'">
        {{ IP_POOL_LIFECYCLE_LABELS[detail.lifecycleState] || detail.lifecycleState }}
      </NTag>
    </template>

    <NSpin :show="loading">
      <NEmpty v-if="!loading && !detail && error" :description="error" />

      <div v-else-if="detail" class="detail-body">
        <NTabs v-model:value="activeTab" type="line" size="small" animated>
          <!-- ============ Tab 1: 概览 ============ -->
          <NTabPane name="overview" tab="概览">
            <!-- 部署进度时间线 -->
            <div class="section">
              <div class="section__title">部署进度</div>
              <div class="deploy-steps">
                <div
                  v-for="(step, idx) in deploySteps"
                  :key="step.key"
                  class="deploy-step"
                  :class="{ 'deploy-step--done': step.done }"
                >
                  <div class="deploy-step__dot">
                    <NIcon v-if="step.done" :size="14"><CheckCircle2 /></NIcon>
                    <span v-else>{{ idx + 1 }}</span>
                  </div>
                  <div class="deploy-step__body">
                    <div class="deploy-step__label">{{ step.label }}</div>
                    <div class="deploy-step__hint">{{ step.hint }}</div>
                  </div>
                </div>
              </div>
            </div>

            <!-- 端口可达性 -->
            <div class="section">
              <div class="section__title-row">
                <span class="section__title">端口可达</span>
                <NButton
                  size="tiny"
                  quaternary
                  :loading="statusLoading"
                  :disabled="!canManage"
                  @click="loadStatus"
                >
                  <template #icon><NIcon><Plug /></NIcon></template>
                  {{ statusData ? '重新探测' : '探测' }}
                </NButton>
              </div>
              <div v-if="!canManage" class="section__tip text-warning">
                <NIcon :size="14"><AlertCircle /></NIcon>
                需要 SSH 凭据齐才能探测; 去 SSH 信息 tab 补全
              </div>
              <div v-else class="port-grid">
                <div class="port-card" :class="`port-card--${sshReachable}`">
                  <div class="port-card__icon"><NIcon :size="20"><KeyRound /></NIcon></div>
                  <div class="port-card__body">
                    <div class="port-card__label">SSH</div>
                    <div class="port-card__value">{{ detail.sshHost || '-' }}:{{ detail.sshPort ?? 22 }}</div>
                  </div>
                  <NTag size="small" :type="sshReachable === 'open' ? 'success' : (sshReachable === 'closed' ? 'error' : 'default')">
                    {{ sshReachable === 'open' ? '可达' : (sshReachable === 'closed' ? '不可达' : '未探测') }}
                  </NTag>
                </div>
                <div class="port-card" :class="`port-card--${socks5Listening}`">
                  <div class="port-card__icon"><NIcon :size="20"><Wifi /></NIcon></div>
                  <div class="port-card__body">
                    <div class="port-card__label">SOCKS5 (dante)</div>
                    <div class="port-card__value">{{ detail.ipAddress }}:{{ detail.socks5Port ?? '-' }}</div>
                  </div>
                  <NTag size="small" :type="socks5Listening === 'listening' ? 'success' : (socks5Listening === 'down' ? 'error' : 'default')">
                    {{ socks5Listening === 'listening' ? '监听中' : (socks5Listening === 'down' ? '未监听' : '未探测') }}
                  </NTag>
                </div>
              </div>
              <div v-if="statusError" class="section__tip text-error mt-2">
                <NIcon :size="14"><XCircle /></NIcon> {{ statusError }}
              </div>
            </div>

            <!-- 资源归属 + 状态 -->
            <div class="section">
              <div class="section__title">资源归属</div>
              <NDescriptions bordered size="small" label-placement="left" :column="2">
                <NDescriptionsItem label="IP 地址">
                  <div class="flex items-center gap-2">
                    <span class="font-mono">{{ detail.ipAddress }}</span>
                    <NButton quaternary size="tiny" circle @click="copyToClipboard(detail.ipAddress, 'IP 地址')">
                      <template #icon><NIcon><Copy /></NIcon></template>
                    </NButton>
                  </div>
                </NDescriptionsItem>
                <NDescriptionsItem label="部署模式">
                  <NTag size="small" :type="detail.provisionMode === 1 ? 'success' : 'warning'">
                    {{ detail.provisionMode === 1 ? '自部署' : '第三方' }}
                  </NTag>
                </NDescriptionsItem>
                <NDescriptionsItem label="区域">{{ detail.region || '-' }}</NDescriptionsItem>
                <NDescriptionsItem label="类型">{{ ipTypeName(detail.ipTypeId) }}</NDescriptionsItem>
                <NDescriptionsItem label="lifecycle">
                  <NTag size="small" :type="IP_POOL_LIFECYCLE_TAG_TYPE[detail.lifecycleState] || 'default'">
                    {{ IP_POOL_LIFECYCLE_LABELS[detail.lifecycleState] || detail.lifecycleState }}
                  </NTag>
                </NDescriptionsItem>
                <NDescriptionsItem label="占用状态">
                  <NTag size="small" :type="statusTagType(detail.status)">
                    {{ IP_POOL_STATUS_LABELS[detail.status] || detail.status }}
                  </NTag>
                </NDescriptionsItem>
                <NDescriptionsItem label="当前会员">
                  <span class="font-mono text-xs">{{ detail.occupiedByMemberId || '-' }}</span>
                </NDescriptionsItem>
                <NDescriptionsItem label="占用时间">{{ formatDateTime(detail.occupiedAt) }}</NDescriptionsItem>
                <NDescriptionsItem v-if="detail.coolingUntil" label="冷却到期">
                  {{ formatDateTime(detail.coolingUntil) }}
                </NDescriptionsItem>
                <NDescriptionsItem label="创建时间">{{ formatDateTime(detail.createdAt) }}</NDescriptionsItem>
                <NDescriptionsItem v-if="detail.remark" :span="2" label="备注">{{ detail.remark }}</NDescriptionsItem>
              </NDescriptions>
              <div class="section__actions">
                <NButton size="small" quaternary @click="emit('edit-core', detail)">
                  <template #icon><NIcon><Pencil /></NIcon></template>
                  编辑核心信息
                </NButton>
              </div>
            </div>
          </NTabPane>

          <!-- ============ Tab 2: SSH 信息 ============ -->
          <NTabPane name="ssh" tab="SSH 信息">
            <div class="tab-actions">
              <NButton size="small" type="primary" @click="emit('edit-credential', detail)">
                <template #icon><NIcon><Pencil /></NIcon></template>
                编辑 SSH 凭据
              </NButton>
              <NButton
                size="small"
                quaternary
                :loading="statusLoading"
                :disabled="!canManage"
                @click="loadStatus"
              >
                <template #icon><NIcon><Plug /></NIcon></template>
                测试连通性
              </NButton>
            </div>

            <div v-if="!detail.sshHost" class="empty-hint">
              <NIcon :size="20"><AlertCircle /></NIcon>
              <div>
                <div class="font-semibold">尚未配置 SSH 凭据</div>
                <div class="text-xs text-zinc-500 mt-1">
                  部署 SOCKS5 / 装 agent / 查日志都依赖 SSH; 点上面"编辑 SSH 凭据"开始
                </div>
              </div>
            </div>
            <NDescriptions v-else bordered size="small" label-placement="left" :column="1">
              <NDescriptionsItem label="SSH host">
                <div class="flex items-center gap-2">
                  <span class="font-mono text-xs">{{ detail.sshHost }}</span>
                  <NButton quaternary size="tiny" circle @click="copyToClipboard(detail.sshHost, 'SSH host')">
                    <template #icon><NIcon><Copy /></NIcon></template>
                  </NButton>
                </div>
              </NDescriptionsItem>
              <NDescriptionsItem label="SSH port">{{ detail.sshPort ?? 22 }}</NDescriptionsItem>
              <NDescriptionsItem label="SSH user">
                <span class="font-mono text-xs">{{ detail.sshUser || '-' }}</span>
              </NDescriptionsItem>
              <NDescriptionsItem label="SSH password">
                <NTag v-if="detail.sshPassword" size="small" type="success">已配置 (mask)</NTag>
                <NTag v-else size="small" type="warning">未配置</NTag>
              </NDescriptionsItem>
              <NDescriptionsItem label="连通性">
                <div class="flex items-center gap-2">
                  <NTag v-if="sshReachable === 'unknown'" size="small">未探测</NTag>
                  <NTag v-else-if="sshReachable === 'open'" size="small" type="success">
                    <template #icon><NIcon><CheckCircle2 /></NIcon></template>
                    可达
                  </NTag>
                  <NTag v-else size="small" type="error">
                    <template #icon><NIcon><XCircle /></NIcon></template>
                    不可达
                  </NTag>
                  <span v-if="statusError" class="text-xs text-error">{{ statusError }}</span>
                </div>
              </NDescriptionsItem>
            </NDescriptions>
          </NTabPane>

          <!-- ============ Tab 3: SOCKS5 服务 ============ -->
          <NTabPane name="socks5" tab="SOCKS5 服务">
            <div class="tab-actions">
              <NButton size="small" type="primary" @click="emit('edit-socks5', detail)">
                <template #icon><NIcon><Pencil /></NIcon></template>
                编辑 dante 配置
              </NButton>
              <NButton
                v-if="isSelfDeploy && (isInstalling || isLive)"
                size="small"
                :type="isLive ? 'default' : 'primary'"
                :quaternary="isLive"
                @click="emit('deploy', detail)"
              >
                <template #icon><NIcon><Rocket /></NIcon></template>
                {{ isLive ? '重装' : '装机' }}
              </NButton>
              <NButton
                v-if="canTest"
                size="small"
                quaternary
                type="warning"
                @click="emit('test', detail)"
              >
                <template #icon><NIcon><Zap /></NIcon></template>
                拨号测试
              </NButton>
              <NButton
                size="small"
                quaternary
                :disabled="!canManage"
                @click="emit('view-log', detail)"
              >
                <template #icon><NIcon><FileText /></NIcon></template>
                查看日志
              </NButton>
              <NButton
                size="small"
                quaternary
                :loading="statusLoading"
                :disabled="!canManage"
                @click="loadStatus"
              >
                <template #icon><NIcon><RefreshCcw /></NIcon></template>
                刷新状态
              </NButton>
            </div>

            <!-- dante 业务配置 -->
            <div class="section">
              <div class="section__title">dante 配置</div>
              <div v-if="!socks5Endpoint" class="empty-hint">
                <NIcon :size="20"><AlertCircle /></NIcon>
                <div>
                  <div class="font-semibold">尚未配置 SOCKS5</div>
                  <div class="text-xs text-zinc-500 mt-1">点 "编辑 dante 配置" 填端口/用户/密码</div>
                </div>
              </div>
              <NDescriptions v-else bordered size="small" label-placement="left" :column="2">
                <NDescriptionsItem label="端点">
                  <div class="flex items-center gap-2">
                    <span class="font-mono">{{ socks5Endpoint }}</span>
                    <NButton quaternary size="tiny" circle @click="copyToClipboard(socks5Endpoint, 'SOCKS5 端点')">
                      <template #icon><NIcon><Copy /></NIcon></template>
                    </NButton>
                  </div>
                </NDescriptionsItem>
                <NDescriptionsItem label="用户名">
                  <span class="font-mono text-xs">{{ detail.socks5Username || '-' }}</span>
                </NDescriptionsItem>
                <NDescriptionsItem label="密码">
                  <NTag v-if="detail.socks5Password" size="small" type="success">已配置 (mask)</NTag>
                  <NTag v-else size="small" type="warning">未配置</NTag>
                </NDescriptionsItem>
                <NDescriptionsItem label="日志级别">
                  <span class="font-mono text-xs">{{ detail.logLevel || '-' }}</span>
                </NDescriptionsItem>
                <NDescriptionsItem label="限速">
                  <NTag size="small" :type="detail.bandwidthLimitMbps ? 'warning' : 'default'">
                    {{ detail.bandwidthLimitMbps ? `${detail.bandwidthLimitMbps} Mbps` : '不限速' }}
                  </NTag>
                </NDescriptionsItem>
                <NDescriptionsItem label="装机目录">
                  <span class="font-mono text-xs">{{ detail.installDir || '-' }}</span>
                </NDescriptionsItem>
                <NDescriptionsItem :span="2" label="日志路径">
                  <span class="font-mono text-xs">{{ detail.logPath || '-' }}</span>
                </NDescriptionsItem>
                <NDescriptionsItem label="systemd 自启">
                  <NTag size="small" :type="detail.autostartEnabled ? 'success' : 'default'">
                    {{ detail.autostartEnabled ? '已启用' : '未启用' }}
                  </NTag>
                </NDescriptionsItem>
                <NDescriptionsItem label="UFW">
                  <NTag size="small" :type="detail.firewallEnabled ? 'success' : 'default'">
                    {{ detail.firewallEnabled ? '已配置' : '未配置' }}
                  </NTag>
                </NDescriptionsItem>
                <NDescriptionsItem v-if="detail.installedAt" label="装机时间" :span="2">
                  {{ formatDateTime(detail.installedAt) }}
                </NDescriptionsItem>
              </NDescriptions>
            </div>

            <!-- 远端 dante 实时状态 -->
            <div v-if="canManage" class="section">
              <div class="section__title">远端 dante 状态</div>
              <div v-if="!statusData && !statusLoading && !statusError" class="section__tip">
                还未拉取; 点上面 "刷新状态" 按钮
              </div>
              <div v-else-if="statusError" class="section__tip text-error">
                {{ statusError }}
              </div>
              <NDescriptions v-else-if="statusData" bordered size="small" label-placement="left" :column="2">
                <NDescriptionsItem label="systemd">
                  <NTag size="small" :type="statusData.active === 'active' ? 'success' : 'error'">
                    {{ statusData.active || 'unknown' }}
                  </NTag>
                </NDescriptionsItem>
                <NDescriptionsItem label="开机自启">
                  <span class="text-xs">{{ statusData.enabled || '-' }}</span>
                </NDescriptionsItem>
                <NDescriptionsItem label="版本">
                  <span class="font-mono text-xs">{{ statusData.version || '-' }}</span>
                </NDescriptionsItem>
                <NDescriptionsItem label="进程启动">
                  <span class="text-xs">{{ statusData.uptimeFrom || '-' }}</span>
                </NDescriptionsItem>
                <NDescriptionsItem :span="2" label="监听端口">
                  <pre class="status-pre">{{ statusData.listening || '-' }}</pre>
                </NDescriptionsItem>
                <NDescriptionsItem v-if="statusData.ufwStatus" :span="2" label="UFW">
                  <pre class="status-pre">{{ statusData.ufwStatus }}</pre>
                </NDescriptionsItem>
              </NDescriptions>
            </div>
          </NTabPane>

          <!-- ============ Tab 4: Agent ============ -->
          <NTabPane name="agent" tab="Agent">
            <div class="tab-actions">
              <NButton
                v-if="isSelfDeploy"
                size="small"
                type="primary"
                @click="emit('provision-agent', detail)"
              >
                <template #icon><NIcon><ServerIcon /></NIcon></template>
                {{ detail.agentToken ? '重装 agent' : '安装 landing agent' }}
              </NButton>
            </div>

            <div v-if="!isSelfDeploy" class="empty-hint">
              <NIcon :size="20"><AlertCircle /></NIcon>
              <div>第三方 SOCKS5 不需要 landing agent</div>
            </div>
            <NDescriptions v-else bordered size="small" label-placement="left" :column="1">
              <NDescriptionsItem label="安装状态">
                <NTag size="small" :type="detail.agentToken ? 'success' : 'default'">
                  {{ detail.agentToken ? '已安装' : '未安装' }}
                </NTag>
              </NDescriptionsItem>
              <NDescriptionsItem v-if="detail.agentToken" label="Agent token">
                <NTooltip>
                  <template #trigger>
                    <span class="font-mono text-xs">{{ maskSecret(detail.agentToken) }}</span>
                  </template>
                  <span class="text-xs">出于安全, token 仅 mask 展示</span>
                </NTooltip>
              </NDescriptionsItem>
              <NDescriptionsItem label="健康状态">
                <NTag size="small" :type="agentHealthLabel.type">
                  {{ agentHealthLabel.text }}
                </NTag>
              </NDescriptionsItem>
              <NDescriptionsItem v-if="detail.lastHealthAt" label="最近心跳">
                <span class="text-xs">
                  {{ relativeTime(detail.lastHealthAt) }}
                  <span class="text-zinc-400 ml-1">({{ formatDateTime(detail.lastHealthAt) }})</span>
                </span>
              </NDescriptionsItem>
              <NDescriptionsItem label="说明">
                <span class="text-xs text-zinc-500">
                  agent 装好后会向后台每 30s 心跳; 在线 = 5min 内有心跳
                </span>
              </NDescriptionsItem>
            </NDescriptions>
          </NTabPane>

          <!-- ============ Tab 5: 服务器信息 ============ -->
          <NTabPane name="server" tab="服务器">
            <div class="tab-actions">
              <NButton
                size="small"
                quaternary
                :loading="statusLoading"
                :disabled="!canManage"
                @click="loadStatus"
              >
                <template #icon><NIcon><RefreshCcw /></NIcon></template>
                {{ statusData ? '刷新' : '拉取主机信息' }}
              </NButton>
            </div>

            <div v-if="!canManage" class="empty-hint">
              <NIcon :size="20"><AlertCircle /></NIcon>
              <div>需要 SSH 凭据齐才能拉远端主机信息</div>
            </div>
            <div v-else-if="!statusData && !statusLoading && !statusError" class="section__tip">
              点上面 "拉取主机信息" 探测远端
            </div>
            <div v-else-if="statusError" class="section__tip text-error">{{ statusError }}</div>
            <NDescriptions
              v-else-if="statusData?.hostInfo"
              bordered
              size="small"
              label-placement="left"
              :column="2"
            >
              <NDescriptionsItem label="主机名">
                <span class="font-mono text-xs">{{ statusData.hostInfo.hostname || '-' }}</span>
              </NDescriptionsItem>
              <NDescriptionsItem label="时区">{{ statusData.hostInfo.timezone || '-' }}</NDescriptionsItem>
              <NDescriptionsItem :span="2" label="OS">
                <span class="text-xs">{{ statusData.hostInfo.osRelease || '-' }}</span>
              </NDescriptionsItem>
              <NDescriptionsItem :span="2" label="内核">
                <span class="font-mono text-xs">{{ statusData.hostInfo.kernel || '-' }}</span>
              </NDescriptionsItem>
              <NDescriptionsItem label="系统已运行">
                <span class="text-xs">{{ statusData.hostInfo.systemUptime || '-' }}</span>
              </NDescriptionsItem>
              <NDescriptionsItem label="Load avg">
                <span class="font-mono text-xs">{{ statusData.hostInfo.loadAvg || '-' }}</span>
              </NDescriptionsItem>
              <NDescriptionsItem :span="2" label="内存">
                <pre class="status-pre">{{ statusData.hostInfo.memory || '-' }}</pre>
              </NDescriptionsItem>
              <NDescriptionsItem :span="2" label="磁盘">
                <pre class="status-pre">{{ statusData.hostInfo.disk || '-' }}</pre>
              </NDescriptionsItem>
            </NDescriptions>
            <div v-else class="section__tip">远端未返回 hostInfo</div>
          </NTabPane>

          <!-- ============ Tab 6: 账面 ============ -->
          <NTabPane name="billing" tab="账面">
            <div class="tab-actions">
              <NButton size="small" type="primary" @click="emit('edit-billing', detail)">
                <template #icon><NIcon><Pencil /></NIcon></template>
                编辑账面
              </NButton>
            </div>

            <NDescriptions bordered size="small" label-placement="left" :column="2">
              <NDescriptionsItem label="采购带宽">
                <span class="font-mono">{{ detail.bandwidthMbps == null ? '∞' : `${detail.bandwidthMbps} Mbps` }}</span>
              </NDescriptionsItem>
              <NDescriptionsItem label="采购流量">
                <span class="font-mono">{{ detail.trafficQuotaGb == null ? '∞' : `${detail.trafficQuotaGb} GB` }}</span>
              </NDescriptionsItem>
              <NDescriptionsItem label="月度成本">
                <span class="font-mono">{{ detail.costMonthlyUsd != null ? `${detail.costMonthlyUsd} USD` : '-' }}</span>
              </NDescriptionsItem>
              <NDescriptionsItem label="账单日">
                <span class="font-mono">{{ detail.billingCycleDay != null ? `每月 ${detail.billingCycleDay} 号` : '-' }}</span>
              </NDescriptionsItem>
              <NDescriptionsItem :span="2" label="到期日">
                <span class="font-mono">{{ detail.expiresAt || '-' }}</span>
              </NDescriptionsItem>
            </NDescriptions>
          </NTabPane>
        </NTabs>

        <!-- ============ 底部危险区域 (跟视图分离) ============ -->
        <div class="danger-zone">
          <div class="danger-zone__title">
            <NIcon :size="14"><AlertCircle /></NIcon>
            <span>危险操作</span>
          </div>
          <div class="danger-zone__actions">
            <NButton
              v-if="isLive"
              size="small"
              quaternary
              type="warning"
              @click="emit('lifecycle-retire', detail)"
            >
              <template #icon><NIcon><Activity /></NIcon></template>
              退役 (停止分配)
            </NButton>
            <NButton
              v-else-if="isRetired"
              size="small"
              quaternary
              type="success"
              @click="emit('lifecycle-restore', detail)"
            >
              <template #icon><NIcon><Activity /></NIcon></template>
              重新启用
            </NButton>
            <NButton
              size="small"
              quaternary
              type="error"
              @click="emit('delete', detail)"
            >
              <template #icon><NIcon><Trash2 /></NIcon></template>
              删除 IP
            </NButton>
          </div>
        </div>
      </div>
    </NSpin>

    <template #footer>
      <div class="flex justify-end">
        <NButton size="small" @click="close">关闭</NButton>
      </div>
    </template>
  </NModal>
</template>

<style scoped>
.detail-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* ===== Tab 内部 actions 区域 (视图/编辑分离) ===== */
.tab-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 0 12px 0;
  margin-bottom: 12px;
  border-bottom: 1px dashed var(--n-divider-color, #efeff5);
  flex-wrap: wrap;
}

/* ===== section 通用 ===== */
.section {
  margin-bottom: 16px;
}
.section__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--n-text-color-2, #555);
  margin-bottom: 8px;
}
.section__title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.section__title-row .section__title { margin-bottom: 0; }
.section__tip {
  font-size: 12px;
  color: var(--n-text-color-3, #999);
  display: flex;
  align-items: center;
  gap: 4px;
}
.section__actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}

/* ===== 部署进度时间线 ===== */
.deploy-steps {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
}
.deploy-step {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid var(--n-border-color, #efeff5);
  border-radius: 6px;
  background: var(--n-card-color, #fff);
}
.deploy-step__dot {
  flex-shrink: 0;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--n-text-color-3, #d9d9d9);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
}
.deploy-step--done .deploy-step__dot {
  background: var(--n-success-color, #18a058);
}
.deploy-step--done {
  border-color: color-mix(in srgb, var(--n-success-color, #18a058) 40%, transparent);
}
.deploy-step__body { min-width: 0; flex: 1; }
.deploy-step__label {
  font-size: 13px;
  font-weight: 600;
  color: var(--n-text-color-1, #222);
}
.deploy-step__hint {
  font-size: 11px;
  color: var(--n-text-color-3, #999);
  margin-top: 2px;
  word-break: break-all;
}

/* ===== 端口卡片 ===== */
.port-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
}
.port-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border: 1px solid var(--n-border-color, #efeff5);
  border-radius: 6px;
  background: var(--n-card-color, #fff);
}
.port-card--open, .port-card--listening {
  border-color: color-mix(in srgb, var(--n-success-color, #18a058) 30%, transparent);
}
.port-card--closed, .port-card--down {
  border-color: color-mix(in srgb, var(--n-error-color, #d03050) 30%, transparent);
}
.port-card__icon {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  background: var(--n-action-color, #f5f5f5);
  color: var(--n-text-color-2, #666);
}
.port-card__body { flex: 1; min-width: 0; }
.port-card__label { font-size: 11px; color: var(--n-text-color-3, #999); }
.port-card__value { font-size: 13px; font-family: monospace; color: var(--n-text-color-1, #222); }

/* ===== 空态提示 ===== */
.empty-hint {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px;
  border: 1px dashed var(--n-border-color, #efeff5);
  border-radius: 6px;
  color: var(--n-text-color-2, #666);
  background: var(--n-action-color, #fafafa);
}

/* ===== 远端 status pre ===== */
.status-pre {
  font-family: monospace;
  font-size: 11px;
  white-space: pre-wrap;
  background: var(--n-action-color, #f5f5f5);
  padding: 6px 8px;
  border-radius: 4px;
  margin: 0;
  max-height: 200px;
  overflow: auto;
}

/* ===== 颜色辅助 ===== */
.text-error { color: var(--n-error-color, #d03050); }
.text-warning { color: var(--n-warning-color, #f0a020); }

/* ===== 底部危险区域 ===== */
.danger-zone {
  margin-top: 16px;
  padding: 12px 16px;
  border: 1px solid color-mix(in srgb, var(--n-error-color, #d03050) 30%, transparent);
  border-radius: 6px;
  background: color-mix(in srgb, var(--n-error-color, #d03050) 5%, transparent);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.danger-zone__title {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 600;
  color: var(--n-error-color, #d03050);
}
.danger-zone__actions {
  display: flex;
  align-items: center;
  gap: 6px;
}
</style>
