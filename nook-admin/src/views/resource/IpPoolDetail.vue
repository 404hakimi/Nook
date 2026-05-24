<script setup lang="ts">
import { computed, h, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Activity,
  AlertCircle,
  ArrowLeft,
  CheckCircle2,
  Copy,
  FileText,
  Gauge,
  Info,
  KeyRound,
  Pencil,
  Plug,
  RefreshCcw,
  Rocket,
  Server as ServerIcon,
  Wallet,
  XCircle,
  Zap
} from 'lucide-vue-next'
import {
  NButton,
  NCard,
  NDescriptions,
  NDescriptionsItem,
  NEmpty,
  NIcon,
  NSpace,
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
import { listEnabledRegions, type ResourceRegion } from '@/api/resource/region'
import { IP_TYPE_CODE_LABELS, listIpTypes, type ResourceIpType } from '@/api/resource/ip-type'
import { formatDateTime } from '@/utils/date'
import RegionFlag from '@/components/RegionFlag.vue'
import IpPoolDeployDialog from './IpPoolDeployDialog.vue'
import IpPoolTestDialog from './IpPoolTestDialog.vue'
import IpPoolLogDialog from './IpPoolLogDialog.vue'
import IpPoolCoreEditDialog from './dialogs/IpPoolCoreEditDialog.vue'
import IpPoolCredentialEditDialog from './dialogs/IpPoolCredentialEditDialog.vue'
import IpPoolBillingEditDialog from './dialogs/IpPoolBillingEditDialog.vue'
import IpPoolSocks5EditDialog from './dialogs/IpPoolSocks5EditDialog.vue'
import AgentProvisionDialog from '@/views/agent/AgentProvisionDialog.vue'

/**
 * IP 池条目详情 — 路由页 (镜像 ServerDetail 风格).
 *
 * 顶部 Header card: 国旗 + IP + lifecycle tag + 占用 tag + 部署模式 tag, 右侧 (心跳/占用方) 状态摘要.
 * Tabs: 概览 / SSH 凭据 / SOCKS5 服务 / Agent / 监控 / 账面.
 * 各 tab 自己头部 actions, 编辑 dialog 内嵌挂载, 不依赖父组件.
 *
 * 列表卡片 → /resource/ip-pool/:id 跳过来; 卡片留高频操作 (装机/测试/停用启用/删除).
 */
const route = useRoute()
const router = useRouter()
const message = useMessage()

const ipId = computed(() => route.params.id as string)
const activeTab = ref<string>(typeof route.query.tab === 'string' ? route.query.tab : 'overview')
watch(activeTab, (t) => router.replace({ query: { ...route.query, tab: t } }))

const detail = ref<ResourceIpPool | null>(null)
const error = ref<string>('')
const loading = ref(false)

const ipTypes = ref<ResourceIpType[]>([])
const regions = ref<ResourceRegion[]>([])

const statusLoading = ref(false)
const statusData = ref<Socks5ServiceStatus | null>(null)
const statusError = ref<string>('')

// ===== sub-dialog mount state =====
const deployOpen = ref(false)
const testOpen = ref(false)
const logOpen = ref(false)
const coreEditOpen = ref(false)
const credEditOpen = ref(false)
const billingEditOpen = ref(false)
const socks5EditOpen = ref(false)
const provisionOpen = ref(false)

// ===== computed (跟原 dialog 一致) =====
const regionMap = computed<Record<string, ResourceRegion>>(() => {
  const m: Record<string, ResourceRegion> = {}
  for (const r of regions.value) m[r.code] = r
  return m
})
const regionInfo = computed(() => detail.value?.region ? regionMap.value[detail.value.region] : undefined)

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
    default: return 'default'
  }
}

const socks5Endpoint = computed(() => {
  if (!detail.value?.ipAddress || !detail.value.socks5Port) return ''
  return `${detail.value.ipAddress}:${detail.value.socks5Port}`
})

const canTest = computed(() =>
  !!detail.value?.ipAddress && !!detail.value?.socks5Port
  && !!detail.value?.socks5Username && !!detail.value?.socks5Password)
const canManage = computed(() => detail.value?.provisionMode === 1 && !!detail.value?.sshPassword)
const isSelfDeploy = computed(() => detail.value?.provisionMode === 1)
const isLive = computed(() => detail.value?.lifecycleState === 'LIVE')
const isInstalling = computed(() =>
  detail.value?.lifecycleState === 'INSTALLING' || detail.value?.lifecycleState === 'READY')
const sshComplete = computed(() =>
  !!detail.value?.sshHost && !!detail.value?.sshUser && !!detail.value?.sshPassword)
const socks5Installed = computed(() =>
  !!detail.value?.installedAt && detail.value?.lifecycleState === 'LIVE')

const isAgentOnline = computed(() => {
  if (!detail.value?.lastHealthAt) return false
  return Date.now() - new Date(detail.value.lastHealthAt).getTime() < 5 * 60 * 1000
})

// ===== 部署进度 4 step (展示用, 不再做导航) =====
const deploySteps = computed(() => [
  { label: 'SSH 凭据', done: sshComplete.value },
  { label: '部署 SOCKS5', done: socks5Installed.value },
  { label: '安装 Agent', done: !!detail.value?.agentToken },
  { label: '心跳健康', done: isAgentOnline.value }
])

const sshReachable = computed<'unknown' | 'open' | 'closed'>(() => {
  if (statusError.value) return 'closed'
  if (!statusData.value) return 'unknown'
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

const agentHealthLabel = computed(() => {
  if (!detail.value?.agentToken) return { text: '未安装', type: 'default' as const }
  if (!detail.value?.lastHealthAt) return { text: '已装未上线', type: 'warning' as const }
  return isAgentOnline.value
    ? { text: '在线', type: 'success' as const }
    : { text: '离线', type: 'error' as const }
})

function relativeTime(iso?: string): string {
  if (!iso) return '-'
  const diffMs = Date.now() - new Date(iso).getTime()
  if (diffMs < 0) return formatDateTime(iso)
  const sec = Math.floor(diffMs / 1000)
  if (sec < 30) return '刚刚'
  if (sec < 3600) return `${Math.floor(sec / 60)} 分钟前`
  if (sec < 86400) return `${Math.floor(sec / 3600)} 小时前`
  return `${Math.floor(sec / 86400)} 天前`
}

function maskSecret(s?: string): string {
  if (!s) return '-'
  if (s.length <= 12) return s.slice(0, 2) + '****' + s.slice(-2)
  return s.slice(0, 6) + '****' + s.slice(-4)
}

function formatBytes(bytes?: number | null): string {
  if (bytes == null || bytes === 0) return '0 B'
  const gb = bytes / 1024 / 1024 / 1024
  if (gb >= 1) return `${gb.toFixed(2)} GB`
  const mb = bytes / 1024 / 1024
  if (mb >= 1) return `${mb.toFixed(1)} MB`
  return `${(bytes / 1024).toFixed(0)} KB`
}

const trafficUsagePercent = computed(() => {
  const limitGb = detail.value?.monthlyTrafficGb
  if (!limitGb || limitGb <= 0) return null
  const usedGb = (detail.value?.usedTrafficBytes ?? 0) / 1024 / 1024 / 1024
  return Math.min(100, Math.round((usedGb / limitGb) * 100))
})

// ===== load =====
async function loadDetail() {
  if (!ipId.value) return
  loading.value = true
  error.value = ''
  detail.value = null
  statusData.value = null
  statusError.value = ''
  try {
    detail.value = await getIpPoolDetail(ipId.value)
  } catch (e) {
    error.value = (e as Error).message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function loadStatus() {
  if (!ipId.value || !canManage.value) {
    if (!canManage.value) statusError.value = '需要 SSH 凭据齐才能探测'
    return
  }
  statusLoading.value = true
  statusError.value = ''
  try {
    statusData.value = await getSocks5Status(ipId.value)
  } catch (e) {
    statusError.value = (e as Error).message || '拉取状态失败'
  } finally {
    statusLoading.value = false
  }
}

async function copyToClipboard(value: string | undefined, label: string) {
  if (!value) { message.warning(`${label} 为空`); return }
  try {
    await navigator.clipboard.writeText(value)
    message.success(`已复制 ${label}`)
  } catch { message.warning('复制失败') }
}

onMounted(async () => {
  try { regions.value = await listEnabledRegions() } catch { /* */ }
  try { ipTypes.value = await listIpTypes() } catch { /* */ }
  await loadDetail()
})
watch(ipId, loadDetail)

watch(activeTab, (tab) => {
  if ((tab === 'socks5' || tab === 'monitor') && !statusData.value
      && !statusLoading.value && !statusError.value && canManage.value) {
    void loadStatus()
  }
})

function back() {
  router.push({ name: 'resource-ip-pool' })
}

function onSubDialogSaved() {
  void loadDetail()
}

function onDeployInstalled() {
  void loadDetail()
}

// 卡片需要的"上次心跳"指示色 (跟 server 详情同款圆点)
const HEARTBEAT_DOT: Record<string, string> = {
  online: '#16a34a',
  warn: '#ca8a04',
  offline: '#dc2626',
  never: '#9ca3af'
}
const heartbeatDotColor = computed(() => {
  if (!detail.value?.agentToken) return HEARTBEAT_DOT.never
  if (!detail.value?.lastHealthAt) return HEARTBEAT_DOT.warn
  return isAgentOnline.value ? HEARTBEAT_DOT.online : HEARTBEAT_DOT.offline
})
</script>

<template>
  <div class="detail-wrap space-y-3">
    <!-- ============ 顶部头条 ============ -->
    <NCard size="small" :content-style="{ padding: '14px 16px' }" class="header-card">
      <div class="flex items-start gap-4">
        <NButton quaternary size="small" @click="back" class="mt-1">
          <template #icon><NIcon><ArrowLeft :size="16" /></NIcon></template>
        </NButton>

        <NSpin :show="loading" size="small" class="flex-1">
          <NEmpty v-if="!loading && !detail && error" :description="error" />
          <div v-else-if="detail" class="flex items-start gap-3 flex-wrap">
            <!-- 左: 国旗 + IP + tags -->
            <div class="flex-1 min-w-0">
              <div class="flex items-center gap-2 flex-wrap">
                <RegionFlag
                  v-if="regionInfo"
                  :code="regionInfo.countryCode"
                  :fallback="regionInfo.flagEmoji"
                  :size="26"
                  :title="regionInfo.displayName"
                  class="header-flag"
                />
                <span class="text-xl font-semibold font-mono">{{ detail.ipAddress }}</span>
                <NButton quaternary size="tiny" circle @click="copyToClipboard(detail.ipAddress, 'IP 地址')">
                  <template #icon><NIcon><Copy /></NIcon></template>
                </NButton>
                <NTag
                  size="small"
                  :type="IP_POOL_LIFECYCLE_TAG_TYPE[detail.lifecycleState] || 'default'"
                >
                  {{ IP_POOL_LIFECYCLE_LABELS[detail.lifecycleState] || detail.lifecycleState }}
                </NTag>
                <NTag size="small" :type="statusTagType(detail.status)">
                  {{ IP_POOL_STATUS_LABELS[detail.status] || detail.status }}
                </NTag>
                <NTag size="small" :type="detail.provisionMode === 1 ? 'success' : 'warning'" :bordered="false">
                  {{ detail.provisionMode === 1 ? '自部署' : '第三方' }}
                </NTag>
                <NTag v-if="regionInfo" size="small" type="info" :bordered="false">
                  {{ regionInfo.displayName }}
                </NTag>
              </div>
              <div class="mt-1 text-xs text-zinc-500 font-mono">
                ipId: {{ detail.id }} · {{ ipTypeName(detail.ipTypeId) }}
                <span v-if="detail.remark"> · 备注: {{ detail.remark }}</span>
              </div>
            </div>

            <!-- 右: 心跳点 + 占用方 + 创建时间 -->
            <div class="text-right text-xs">
              <div class="flex items-center justify-end gap-1.5 mb-1">
                <span class="status-dot" :style="`background:${heartbeatDotColor}`"></span>
                <NTag size="small" :type="agentHealthLabel.type">
                  {{ agentHealthLabel.text }}
                </NTag>
                <span v-if="detail.lastHealthAt" class="text-zinc-400 font-mono ml-1">
                  {{ relativeTime(detail.lastHealthAt) }}
                </span>
              </div>
              <div v-if="detail.occupiedByMemberId" class="text-zinc-500">
                占用: <span class="font-mono">{{ detail.occupiedByMemberId }}</span>
              </div>
              <div class="text-zinc-400 mt-0.5">
                创建于 {{ formatDateTime(detail.createdAt) }}
              </div>
            </div>
          </div>
        </NSpin>
      </div>
    </NCard>

    <!-- ============ Tabs ============ -->
    <NCard v-if="detail" size="small" :content-style="{ padding: '0 16px' }">
      <NTabs v-model:value="activeTab" type="line" size="medium" pane-style="padding: 14px 0">
        <!-- ============ 概览 ============ -->
        <NTabPane name="overview">
          <template #tab>
            <NSpace :size="6" align="center">
              <NIcon><Info :size="14" /></NIcon>
              <span>概览</span>
            </NSpace>
          </template>

          <!-- 部署进度时间线 -->
          <div class="section-title">部署进度</div>
          <div class="deploy-steps">
            <div
              v-for="(step, idx) in deploySteps"
              :key="step.label"
              class="deploy-step"
              :class="{ 'deploy-step--done': step.done }"
            >
              <div class="deploy-step__dot">
                <NIcon v-if="step.done" :size="14"><CheckCircle2 /></NIcon>
                <span v-else>{{ idx + 1 }}</span>
              </div>
              <span>{{ step.label }}</span>
            </div>
          </div>

          <!-- 端口可达 -->
          <div class="section-title mt-4 flex items-center justify-between">
            <span>端口可达</span>
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
          <div v-if="!canManage" class="hint text-warning">
            <NIcon :size="14"><AlertCircle /></NIcon>
            需要 SSH 凭据齐才能探测; 去 SSH 凭据 tab 补全
          </div>
          <div v-else class="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div class="port-card" :class="`port-card--${sshReachable}`">
              <NIcon :size="20"><KeyRound /></NIcon>
              <div class="flex-1">
                <div class="text-xs text-zinc-500">SSH</div>
                <div class="font-mono text-sm">{{ detail.sshHost || '-' }}:{{ detail.sshPort ?? 22 }}</div>
              </div>
              <NTag
                size="small"
                :type="sshReachable === 'open' ? 'success' : (sshReachable === 'closed' ? 'error' : 'default')"
              >
                {{ sshReachable === 'open' ? '可达' : (sshReachable === 'closed' ? '不可达' : '未探测') }}
              </NTag>
            </div>
            <div class="port-card" :class="`port-card--${socks5Listening}`">
              <NIcon :size="20"><ServerIcon /></NIcon>
              <div class="flex-1">
                <div class="text-xs text-zinc-500">SOCKS5 (dante)</div>
                <div class="font-mono text-sm">{{ detail.ipAddress }}:{{ detail.socks5Port ?? '-' }}</div>
              </div>
              <NTag
                size="small"
                :type="socks5Listening === 'listening' ? 'success' : (socks5Listening === 'down' ? 'error' : 'default')"
              >
                {{ socks5Listening === 'listening' ? '监听中' : (socks5Listening === 'down' ? '未监听' : '未探测') }}
              </NTag>
            </div>
          </div>

          <!-- 资源归属 -->
          <div class="section-title mt-4 flex items-center justify-between">
            <span>资源归属</span>
            <NButton size="tiny" quaternary @click="coreEditOpen = true">
              <template #icon><NIcon><Pencil /></NIcon></template>
              编辑
            </NButton>
          </div>
          <div class="info-grid">
            <div class="info-row"><span class="k">IP 地址</span><code class="v">{{ detail.ipAddress }}</code></div>
            <div class="info-row"><span class="k">部署模式</span><span class="v">{{ detail.provisionMode === 1 ? '自部署' : '第三方' }}</span></div>
            <div class="info-row"><span class="k">区域</span><span class="v">{{ detail.region || '-' }}</span></div>
            <div class="info-row"><span class="k">类型</span><span class="v">{{ ipTypeName(detail.ipTypeId) }}</span></div>
            <div class="info-row"><span class="k">占用状态</span>
              <NTag size="tiny" :type="statusTagType(detail.status)">{{ IP_POOL_STATUS_LABELS[detail.status] || detail.status }}</NTag>
            </div>
            <div class="info-row"><span class="k">当前会员</span><code class="v">{{ detail.occupiedByMemberId || '-' }}</code></div>
            <div class="info-row"><span class="k">占用时间</span><span class="v">{{ formatDateTime(detail.occupiedAt) || '—' }}</span></div>
            <div v-if="detail.coolingUntil" class="info-row"><span class="k">冷却到期</span><span class="v">{{ formatDateTime(detail.coolingUntil) }}</span></div>
            <div v-if="detail.remark" class="info-row info-row--full"><span class="k">备注</span><span class="v">{{ detail.remark }}</span></div>
          </div>
        </NTabPane>

        <!-- ============ SSH 凭据 ============ -->
        <NTabPane name="ssh">
          <template #tab>
            <NSpace :size="6" align="center">
              <NIcon><KeyRound :size="14" /></NIcon>
              <span>SSH 凭据</span>
            </NSpace>
          </template>

          <div class="tab-actions">
            <NButton size="small" type="primary" @click="credEditOpen = true">
              <template #icon><NIcon><Pencil /></NIcon></template>
              编辑 SSH 凭据
            </NButton>
            <NButton size="small" quaternary :loading="statusLoading" :disabled="!canManage" @click="loadStatus">
              <template #icon><NIcon><Plug /></NIcon></template>
              测试连通性
            </NButton>
          </div>

          <div v-if="!detail.sshHost" class="empty-hint">
            <NIcon :size="18"><AlertCircle /></NIcon>
            <div>
              <div class="font-semibold">尚未配置 SSH 凭据</div>
              <div class="text-xs text-zinc-500 mt-1">装机 / 装 agent / 看日志都依赖 SSH</div>
            </div>
          </div>
          <div v-else class="info-grid">
            <div class="info-row"><span class="k">host</span><code class="v">{{ detail.sshHost }}</code>
              <NButton quaternary size="tiny" circle @click="copyToClipboard(detail.sshHost, 'SSH host')">
                <template #icon><NIcon><Copy /></NIcon></template>
              </NButton>
            </div>
            <div class="info-row"><span class="k">port</span><span class="v">{{ detail.sshPort ?? 22 }}</span></div>
            <div class="info-row"><span class="k">user</span><code class="v">{{ detail.sshUser || '-' }}</code></div>
            <div class="info-row"><span class="k">password</span>
              <NTag v-if="detail.sshPassword" size="tiny" type="success">已配置 (mask)</NTag>
              <NTag v-else size="tiny" type="warning">未配置</NTag>
            </div>
            <div class="info-row info-row--full"><span class="k">连通性</span>
              <NTag v-if="sshReachable === 'unknown'" size="tiny">未探测</NTag>
              <NTag v-else-if="sshReachable === 'open'" size="tiny" type="success">
                <template #icon><NIcon><CheckCircle2 /></NIcon></template> 可达
              </NTag>
              <NTag v-else size="tiny" type="error">
                <template #icon><NIcon><XCircle /></NIcon></template> 不可达
                <span v-if="statusError" class="ml-1">({{ statusError }})</span>
              </NTag>
            </div>
          </div>
        </NTabPane>

        <!-- ============ SOCKS5 服务 ============ -->
        <NTabPane name="socks5">
          <template #tab>
            <NSpace :size="6" align="center">
              <NIcon><ServerIcon :size="14" /></NIcon>
              <span>SOCKS5 服务</span>
            </NSpace>
          </template>

          <div class="tab-actions">
            <NButton size="small" type="primary" @click="socks5EditOpen = true">
              <template #icon><NIcon><Pencil /></NIcon></template>
              编辑 dante 配置
            </NButton>
            <NButton
              v-if="isSelfDeploy && (isInstalling || isLive)"
              size="small"
              :type="isLive ? 'default' : 'primary'"
              :quaternary="isLive"
              @click="deployOpen = true"
            >
              <template #icon><NIcon><Rocket /></NIcon></template>
              {{ isLive ? '重装' : '装机' }}
            </NButton>
            <NButton v-if="canTest" size="small" quaternary type="warning" @click="testOpen = true">
              <template #icon><NIcon><Zap /></NIcon></template>
              拨号测试
            </NButton>
            <NButton size="small" quaternary :disabled="!canManage" @click="logOpen = true">
              <template #icon><NIcon><FileText /></NIcon></template>
              查看日志
            </NButton>
            <NButton size="small" quaternary :loading="statusLoading" :disabled="!canManage" @click="loadStatus">
              <template #icon><NIcon><RefreshCcw /></NIcon></template>
              刷新远端
            </NButton>
          </div>

          <div v-if="!socks5Endpoint" class="empty-hint">
            <NIcon :size="18"><AlertCircle /></NIcon>
            <div>
              <div class="font-semibold">尚未配置 SOCKS5</div>
              <div class="text-xs text-zinc-500 mt-1">点 "编辑 dante 配置" 填端口/用户/密码</div>
            </div>
          </div>
          <div v-else>
            <div class="section-title">dante 配置</div>
            <div class="info-grid">
              <div class="info-row"><span class="k">端点</span><code class="v">{{ socks5Endpoint }}</code>
                <NButton quaternary size="tiny" circle @click="copyToClipboard(socks5Endpoint, 'SOCKS5 端点')">
                  <template #icon><NIcon><Copy /></NIcon></template>
                </NButton>
              </div>
              <div class="info-row"><span class="k">用户</span><code class="v">{{ detail.socks5Username || '-' }}</code></div>
              <div class="info-row"><span class="k">密码</span>
                <NTag v-if="detail.socks5Password" size="tiny" type="success">已配置 (mask)</NTag>
                <NTag v-else size="tiny" type="warning">未配置</NTag>
              </div>
              <div class="info-row"><span class="k">日志级别</span><code class="v">{{ detail.logLevel || '-' }}</code></div>
              <div class="info-row"><span class="k">实际限速</span><span class="v">{{ detail.bandwidthLimitMbps ? `${detail.bandwidthLimitMbps} Mbps` : '不限' }}</span></div>
              <div class="info-row"><span class="k">月流量上限</span><span class="v">{{ detail.monthlyTrafficGb ? `${detail.monthlyTrafficGb} GB` : '不限' }}</span></div>
              <div class="info-row"><span class="k">本期已用</span><span class="v">{{ formatBytes(detail.usedTrafficBytes) }}<NTag v-if="trafficUsagePercent != null" size="tiny" :type="trafficUsagePercent >= 90 ? 'error' : (trafficUsagePercent >= 70 ? 'warning' : 'success')" class="ml-1">{{ trafficUsagePercent }}%</NTag></span></div>
              <div class="info-row"><span class="k">systemd 自启</span>
                <NTag size="tiny" :type="detail.autostartEnabled ? 'success' : 'default'">
                  {{ detail.autostartEnabled ? '已启用' : '未启用' }}
                </NTag>
              </div>
              <div class="info-row info-row--full"><span class="k">安装目录</span><code class="v text-xs">{{ detail.installDir || '-' }}</code></div>
              <div class="info-row info-row--full"><span class="k">日志路径</span><code class="v text-xs">{{ detail.logPath || '-' }}</code></div>
              <div v-if="detail.installedAt" class="info-row info-row--full"><span class="k">装机时间</span><span class="v">{{ formatDateTime(detail.installedAt) }}</span></div>
            </div>

            <!-- 远端 systemd 状态 -->
            <template v-if="canManage">
              <div class="section-title mt-4">远端 dante 状态</div>
              <div v-if="!statusData && !statusLoading && !statusError" class="hint">
                还未拉取; 点 "刷新远端" 按钮
              </div>
              <div v-else-if="statusError" class="hint text-error">{{ statusError }}</div>
              <div v-else-if="statusData" class="info-grid">
                <div class="info-row"><span class="k">systemd</span>
                  <NTag size="tiny" :type="statusData.active === 'active' ? 'success' : 'error'">
                    {{ statusData.active || 'unknown' }}
                  </NTag>
                </div>
                <div class="info-row"><span class="k">开机自启</span><span class="v">{{ statusData.enabled || '-' }}</span></div>
                <div class="info-row"><span class="k">版本</span><code class="v">{{ statusData.version || '-' }}</code></div>
                <div class="info-row"><span class="k">进程启动</span><span class="v">{{ statusData.uptimeFrom || '-' }}</span></div>
                <div v-if="statusData.listening" class="info-row info-row--full"><span class="k">监听</span>
                  <pre class="status-pre">{{ statusData.listening }}</pre>
                </div>
              </div>
            </template>
          </div>
        </NTabPane>

        <!-- ============ Agent ============ -->
        <NTabPane name="agent">
          <template #tab>
            <NSpace :size="6" align="center">
              <NIcon><Activity :size="14" /></NIcon>
              <span>Agent</span>
            </NSpace>
          </template>

          <div class="tab-actions">
            <NButton v-if="isSelfDeploy" size="small" type="primary" @click="provisionOpen = true">
              <template #icon><NIcon><ServerIcon /></NIcon></template>
              {{ detail.agentToken ? '重装 agent' : '安装 landing agent' }}
            </NButton>
          </div>

          <div v-if="!isSelfDeploy" class="empty-hint">
            <NIcon :size="18"><AlertCircle /></NIcon>
            <div>第三方 SOCKS5 不需要 landing agent</div>
          </div>
          <div v-else class="info-grid">
            <div class="info-row"><span class="k">安装状态</span>
              <NTag size="tiny" :type="detail.agentToken ? 'success' : 'default'">
                {{ detail.agentToken ? '已安装' : '未安装' }}
              </NTag>
            </div>
            <div class="info-row"><span class="k">健康状态</span>
              <NTag size="tiny" :type="agentHealthLabel.type">{{ agentHealthLabel.text }}</NTag>
            </div>
            <div v-if="detail.agentToken" class="info-row info-row--full"><span class="k">Agent token</span>
              <NTooltip>
                <template #trigger>
                  <code class="v">{{ maskSecret(detail.agentToken) }}</code>
                </template>
                <span class="text-xs">出于安全, token 仅 mask 展示</span>
              </NTooltip>
            </div>
            <div v-if="detail.lastHealthAt" class="info-row info-row--full"><span class="k">最近心跳</span>
              <span class="v">{{ relativeTime(detail.lastHealthAt) }}
                <span class="text-zinc-400 ml-1">({{ formatDateTime(detail.lastHealthAt) }})</span>
              </span>
            </div>
            <div class="info-row info-row--full"><span class="k">说明</span>
              <span class="v text-xs text-zinc-500">agent 装好后每 30s 心跳; 在线 = 5min 内有心跳</span>
            </div>
          </div>
        </NTabPane>

        <!-- ============ 监控 (心跳 + 主机) ============ -->
        <NTabPane name="monitor">
          <template #tab>
            <NSpace :size="6" align="center">
              <NIcon><Gauge :size="14" /></NIcon>
              <span>监控</span>
            </NSpace>
          </template>

          <div class="tab-actions">
            <NButton size="small" quaternary :loading="statusLoading" :disabled="!canManage" @click="loadStatus">
              <template #icon><NIcon><RefreshCcw /></NIcon></template>
              {{ statusData ? '刷新' : '拉取' }}
            </NButton>
          </div>

          <div v-if="!canManage" class="empty-hint">
            <NIcon :size="18"><AlertCircle /></NIcon>
            <div>需要 SSH 凭据齐才能拉远端主机信息</div>
          </div>
          <div v-else-if="!statusData && !statusLoading && !statusError" class="hint">
            点 "拉取" 探测远端
          </div>
          <div v-else-if="statusError" class="hint text-error">{{ statusError }}</div>
          <div v-else-if="statusData?.hostInfo" class="info-grid">
            <div class="info-row"><span class="k">主机名</span><code class="v">{{ statusData.hostInfo.hostname || '-' }}</code></div>
            <div class="info-row"><span class="k">时区</span><span class="v">{{ statusData.hostInfo.timezone || '-' }}</span></div>
            <div class="info-row info-row--full"><span class="k">OS</span><span class="v text-xs">{{ statusData.hostInfo.osRelease || '-' }}</span></div>
            <div class="info-row info-row--full"><span class="k">内核</span><code class="v text-xs">{{ statusData.hostInfo.kernel || '-' }}</code></div>
            <div class="info-row"><span class="k">系统已运行</span><span class="v">{{ statusData.hostInfo.systemUptime || '-' }}</span></div>
            <div class="info-row"><span class="k">Load avg</span><code class="v">{{ statusData.hostInfo.loadAvg || '-' }}</code></div>
            <div class="info-row info-row--full"><span class="k">内存</span><pre class="status-pre">{{ statusData.hostInfo.memory || '-' }}</pre></div>
            <div class="info-row info-row--full"><span class="k">磁盘</span><pre class="status-pre">{{ statusData.hostInfo.disk || '-' }}</pre></div>
            <div v-if="statusData.ufwStatus" class="info-row info-row--full"><span class="k">UFW</span><pre class="status-pre">{{ statusData.ufwStatus }}</pre></div>
          </div>
          <div v-else class="hint">远端未返回 hostInfo</div>
        </NTabPane>

        <!-- ============ 容量与账面 ============ -->
        <NTabPane name="billing">
          <template #tab>
            <NSpace :size="6" align="center">
              <NIcon><Wallet :size="14" /></NIcon>
              <span>容量与账面</span>
            </NSpace>
          </template>

          <div class="tab-actions">
            <NButton size="small" type="primary" @click="billingEditOpen = true">
              <template #icon><NIcon><Pencil /></NIcon></template>
              编辑
            </NButton>
          </div>

          <div class="section-title">容量 — 实际控制</div>
          <div class="info-grid">
            <div class="info-row"><span class="k">实际限速</span><span class="v">{{ detail.bandwidthLimitMbps ? `${detail.bandwidthLimitMbps} Mbps` : '不限' }}</span></div>
            <div class="info-row"><span class="k">月流量上限</span><span class="v">{{ detail.monthlyTrafficGb ? `${detail.monthlyTrafficGb} GB` : '不限' }}</span></div>
            <div class="info-row"><span class="k">本期已用</span>
              <span class="v">{{ formatBytes(detail.usedTrafficBytes) }}
                <NTag v-if="trafficUsagePercent != null" size="tiny" :type="trafficUsagePercent >= 90 ? 'error' : (trafficUsagePercent >= 70 ? 'warning' : 'success')" class="ml-1">
                  {{ trafficUsagePercent }}%
                </NTag>
              </span>
            </div>
            <div class="info-row"><span class="k">流量状态</span>
              <NTag size="tiny" :type="detail.throttleState === 'THROTTLED' ? 'warning' : 'success'">
                {{ detail.throttleState === 'THROTTLED' ? '已触发限流' : '正常' }}
              </NTag>
            </div>
          </div>

          <div class="section-title mt-4">账面 — 财务记录</div>
          <div class="info-grid">
            <div class="info-row"><span class="k">月度成本</span><span class="v">{{ detail.costMonthlyUsd != null ? `${detail.costMonthlyUsd} USD` : '-' }}</span></div>
            <div class="info-row"><span class="k">账单日</span><span class="v">{{ detail.billingCycleDay != null ? `每月 ${detail.billingCycleDay} 号` : '-' }}</span></div>
            <div class="info-row info-row--full"><span class="k">到期日</span><span class="v">{{ detail.expiresAt || '-' }}</span></div>
          </div>
        </NTabPane>
      </NTabs>
    </NCard>

    <!-- ============ 内嵌 sub-dialogs ============ -->
    <IpPoolDeployDialog v-model="deployOpen" :ip-id="ipId" @installed="onDeployInstalled" />
    <IpPoolTestDialog v-model="testOpen" :ip="detail" />
    <IpPoolLogDialog v-model="logOpen" :ip="detail" />
    <IpPoolCoreEditDialog v-if="detail" v-model="coreEditOpen" :ip-pool="detail" @saved="onSubDialogSaved" />
    <IpPoolCredentialEditDialog
      v-if="detail"
      v-model="credEditOpen"
      :ip-id="detail.id"
      @saved="onSubDialogSaved"
    />
    <IpPoolBillingEditDialog v-if="detail" v-model="billingEditOpen" :ip-id="detail.id" @saved="onSubDialogSaved" />
    <IpPoolSocks5EditDialog v-if="detail" v-model="socks5EditOpen" :ip-id="detail.id" @saved="onSubDialogSaved" />
    <AgentProvisionDialog
      v-model="provisionOpen"
      :initial-server-id="ipId"
      initial-role="landing"
      @dispatched="onSubDialogSaved"
    />
  </div>
</template>

<style scoped>
.detail-wrap { max-width: 1280px; margin: 0 auto; }
.header-card {
  background: linear-gradient(to right, rgba(99, 102, 241, 0.03), rgba(127, 127, 127, 0));
}
.status-dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
}
.header-flag { filter: drop-shadow(0 1px 2px rgba(0, 0, 0, 0.1)); }

.section-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--n-text-color-2, #555);
  margin-bottom: 8px;
}

/* ===== info-row / k / v 走 main.scss 全局 tokens; 本地补 grid ===== */
.info-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 6px 24px;
}
.info-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  padding: 4px 0;
  border-bottom: 1px dashed transparent;
}
.info-row--full { grid-column: 1 / -1; }
.info-row .k {
  flex-shrink: 0;
  width: 100px;
  color: var(--n-text-color-3, #999);
  font-size: 12px;
}
.info-row .v {
  color: var(--n-text-color-1, #222);
  word-break: break-all;
  min-width: 0;
}
.info-row code.v { font-family: monospace; font-size: 12px; }

/* ===== Tab 内部 actions ===== */
.tab-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  padding-bottom: 12px;
  margin-bottom: 12px;
  border-bottom: 1px dashed var(--n-divider-color, #efeff5);
  flex-wrap: wrap;
}

/* ===== 部署进度时间线 ===== */
.deploy-steps {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
}
.deploy-step {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border: 1px solid var(--n-border-color, #efeff5);
  border-radius: 6px;
  background: #fafafa;
  font-size: 13px;
  color: var(--n-text-color-2, #555);
}
.deploy-step__dot {
  flex-shrink: 0;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--n-text-color-3, #d9d9d9);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
}
.deploy-step--done {
  background: rgba(24, 160, 88, 0.04);
  border-color: rgba(24, 160, 88, 0.3);
  color: var(--n-text-color-1, #222);
}
.deploy-step--done .deploy-step__dot { background: var(--n-success-color, #18a058); }

/* ===== 端口卡片 ===== */
.port-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border: 1px solid var(--n-border-color, #efeff5);
  border-radius: 6px;
  background: var(--n-card-color, #fff);
}
.port-card--open, .port-card--listening { border-color: rgba(24, 160, 88, 0.3); }
.port-card--closed, .port-card--down { border-color: rgba(208, 48, 80, 0.3); }

/* ===== 空态 ===== */
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
.hint {
  font-size: 12px;
  color: var(--n-text-color-3, #999);
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 0;
}

.status-pre {
  font-family: monospace;
  font-size: 11px;
  white-space: pre-wrap;
  background: var(--n-action-color, #f5f5f5);
  padding: 6px 8px;
  border-radius: 4px;
  margin: 0;
  max-height: 180px;
  overflow: auto;
}

.text-error { color: var(--n-error-color, #d03050); }
.text-warning { color: var(--n-warning-color, #f0a020); }

@media (max-width: 720px) {
  .info-grid { grid-template-columns: 1fr; }
  .deploy-steps { grid-template-columns: repeat(2, 1fr); }
}
</style>
