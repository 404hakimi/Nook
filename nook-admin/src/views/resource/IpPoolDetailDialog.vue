<script setup lang="ts">
import { computed, h, ref, watch } from 'vue'
import {
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
 * IP 池条目详情 — 部署进度 step 卡当 nav.
 *
 * 4 个 step (= 装机 workflow): SSH 凭据 → 部署 SOCKS5 → 安装 Agent → 心跳健康.
 * 每个 step 卡都可点击切换 (done=绿, pending=灰但可点; 不卡步骤), 切换后下方展示该 step 的内容 + 编辑/操作按钮.
 * 账面信息折到底部小卡片. lifecycle 流转 (停用/启用) + 删除走列表卡片操作行, 不在详情.
 *
 * 数据源:
 *   - detail = getIpPoolDetail 主 + 5 子表合并
 *   - statusData = getSocks5Status (按需拉, 用于端口可达 / 远端 systemd / 主机 info)
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
  (e: 'refresh'): void
}>()

const message = useMessage()
const loading = ref(false)
const detail = ref<ResourceIpPool | null>(null)
const error = ref<string>('')
const ipTypes = ref<ResourceIpType[]>([])
let ipTypesLoaded = false

type StepKey = 'ssh' | 'socks5' | 'agent' | 'health'
const activeStep = ref<StepKey>('ssh')

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
  const last = new Date(detail.value.lastHealthAt).getTime()
  return Date.now() - last < 5 * 60 * 1000
})

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

// ===== 4 个 step (cards = nav) =====
interface Step {
  key: StepKey
  idx: number
  label: string
  done: boolean
  hint: string
}
const steps = computed<Step[]>(() => [
  {
    key: 'ssh',
    idx: 1,
    label: 'SSH 凭据',
    done: sshComplete.value,
    hint: sshComplete.value
      ? `${detail.value?.sshUser}@${detail.value?.sshHost}:${detail.value?.sshPort ?? 22}`
      : '未配置'
  },
  {
    key: 'socks5',
    idx: 2,
    label: '部署 SOCKS5',
    done: socks5Installed.value,
    hint: socks5Installed.value
      ? `:${detail.value?.socks5Port} / ${detail.value?.socks5Username || '-'}`
      : (detail.value?.socks5Port ? `已配 :${detail.value.socks5Port} 未装机` : '未配置')
  },
  {
    key: 'agent',
    idx: 3,
    label: '安装 Agent',
    done: !!detail.value?.agentToken,
    hint: detail.value?.agentToken ? '已注册 token' : '未安装'
  },
  {
    key: 'health',
    idx: 4,
    label: '心跳健康',
    done: isAgentOnline.value,
    hint: detail.value?.lastHealthAt
      ? (isAgentOnline.value ? `在线 · ${relativeTime(detail.value.lastHealthAt)}` : `离线 · ${relativeTime(detail.value.lastHealthAt)}`)
      : '未上报'
  }
])

// ===== 远端探测 =====
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

async function loadStatus() {
  if (!props.ipId) return
  if (!canManage.value) {
    statusError.value = '需要 SSH 凭据齐才能探测'
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
    activeStep.value = 'ssh'
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

/** 切到 SOCKS5 / 心跳 step 时自动拉一次远端状态 (减少手动点) */
watch(activeStep, (step) => {
  if ((step === 'socks5' || step === 'health') && !statusData.value && !statusLoading.value
      && !statusError.value && canManage.value) {
    void loadStatus()
  }
})

function close() {
  emit('update:modelValue', false)
}

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
        <!-- ============ Header 紧凑: IP + 资源归属 + 占用 ============ -->
        <div class="ip-header">
          <div class="ip-header__main">
            <span class="ip-header__addr font-mono">{{ detail.ipAddress }}</span>
            <NButton quaternary size="tiny" circle @click="copyToClipboard(detail.ipAddress, 'IP 地址')">
              <template #icon><NIcon><Copy /></NIcon></template>
            </NButton>
            <NTag size="small" :type="statusTagType(detail.status)">
              {{ IP_POOL_STATUS_LABELS[detail.status] || detail.status }}
            </NTag>
            <NTag size="small" :type="detail.provisionMode === 1 ? 'success' : 'warning'">
              {{ detail.provisionMode === 1 ? '自部署' : '第三方' }}
            </NTag>
            <NButton size="tiny" quaternary @click="emit('edit-core', detail)" title="编辑核心信息">
              <template #icon><NIcon><Pencil /></NIcon></template>
            </NButton>
          </div>
          <div class="ip-header__meta">
            <span>{{ detail.region || '-' }}</span>
            <span class="text-zinc-400 mx-1">·</span>
            <span>{{ ipTypeName(detail.ipTypeId) }}</span>
            <template v-if="detail.occupiedByMemberId">
              <span class="text-zinc-400 mx-2">|</span>
              <span>占用: <span class="font-mono">{{ detail.occupiedByMemberId }}</span></span>
              <span v-if="detail.occupiedAt" class="text-zinc-400 ml-1">({{ formatDateTime(detail.occupiedAt) }})</span>
            </template>
            <template v-if="detail.remark">
              <span class="text-zinc-400 mx-2">|</span>
              <span class="ip-header__remark">备注: {{ detail.remark }}</span>
            </template>
          </div>
        </div>

        <!-- ============ 部署进度 step cards (= 主导航) ============ -->
        <div class="section-title">
          <span>部署进度</span>
          <span class="section-title__hint">点击切换查看 / 编辑</span>
        </div>
        <div class="step-cards">
          <div
            v-for="step in steps"
            :key="step.key"
            class="step-card"
            :class="{
              'step-card--active': activeStep === step.key,
              'step-card--done': step.done,
              'step-card--pending': !step.done
            }"
            @click="activeStep = step.key"
          >
            <div class="step-card__dot">
              <NIcon v-if="step.done" :size="14"><CheckCircle2 /></NIcon>
              <span v-else>{{ step.idx }}</span>
            </div>
            <div class="step-card__body">
              <div class="step-card__label">{{ step.label }}</div>
              <div class="step-card__hint">{{ step.hint }}</div>
            </div>
          </div>
        </div>

        <!-- ============ 当前 step 内容 ============ -->
        <div class="step-content">
          <!-- ===== Step 1: SSH 凭据 ===== -->
          <template v-if="activeStep === 'ssh'">
            <div class="step-content__actions">
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
              <NIcon :size="18"><AlertCircle /></NIcon>
              <div>
                <div class="font-semibold">尚未配置 SSH 凭据</div>
                <div class="text-xs text-zinc-500 mt-1">装机 / 装 agent / 看日志都依赖 SSH</div>
              </div>
            </div>
            <NDescriptions v-else bordered size="small" label-placement="left" :column="2">
              <NDescriptionsItem label="host">
                <div class="flex items-center gap-1">
                  <span class="font-mono text-xs">{{ detail.sshHost }}</span>
                  <NButton quaternary size="tiny" circle @click="copyToClipboard(detail.sshHost, 'SSH host')">
                    <template #icon><NIcon><Copy /></NIcon></template>
                  </NButton>
                </div>
              </NDescriptionsItem>
              <NDescriptionsItem label="port">{{ detail.sshPort ?? 22 }}</NDescriptionsItem>
              <NDescriptionsItem label="user">
                <span class="font-mono text-xs">{{ detail.sshUser || '-' }}</span>
              </NDescriptionsItem>
              <NDescriptionsItem label="password">
                <NTag v-if="detail.sshPassword" size="small" type="success">已配置 (mask)</NTag>
                <NTag v-else size="small" type="warning">未配置</NTag>
              </NDescriptionsItem>
              <NDescriptionsItem :span="2" label="连通性">
                <NTag v-if="sshReachable === 'unknown'" size="small">未探测</NTag>
                <NTag v-else-if="sshReachable === 'open'" size="small" type="success">
                  <template #icon><NIcon><CheckCircle2 /></NIcon></template>
                  可达
                </NTag>
                <NTag v-else size="small" type="error">
                  <template #icon><NIcon><XCircle /></NIcon></template>
                  不可达 <span v-if="statusError" class="ml-1">({{ statusError }})</span>
                </NTag>
              </NDescriptionsItem>
            </NDescriptions>
          </template>

          <!-- ===== Step 2: 部署 SOCKS5 ===== -->
          <template v-else-if="activeStep === 'socks5'">
            <div class="step-content__actions">
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
            <NDescriptions v-else bordered size="small" label-placement="left" :column="2">
              <NDescriptionsItem label="端点">
                <div class="flex items-center gap-1">
                  <span class="font-mono">{{ socks5Endpoint }}</span>
                  <NButton quaternary size="tiny" circle @click="copyToClipboard(socks5Endpoint, 'SOCKS5 端点')">
                    <template #icon><NIcon><Copy /></NIcon></template>
                  </NButton>
                </div>
              </NDescriptionsItem>
              <NDescriptionsItem label="用户">
                <span class="font-mono text-xs">{{ detail.socks5Username || '-' }}</span>
              </NDescriptionsItem>
              <NDescriptionsItem label="密码">
                <NTag v-if="detail.socks5Password" size="small" type="success">已配置 (mask)</NTag>
                <NTag v-else size="small" type="warning">未配置</NTag>
              </NDescriptionsItem>
              <NDescriptionsItem label="限速">
                <NTag size="small" :type="detail.bandwidthLimitMbps ? 'warning' : 'default'">
                  {{ detail.bandwidthLimitMbps ? `${detail.bandwidthLimitMbps} Mbps` : '不限速' }}
                </NTag>
              </NDescriptionsItem>
              <NDescriptionsItem label="日志级别">
                <span class="font-mono text-xs">{{ detail.logLevel || '-' }}</span>
              </NDescriptionsItem>
              <NDescriptionsItem label="自启 / UFW">
                <NTag size="small" :type="detail.autostartEnabled ? 'success' : 'default'">
                  自启: {{ detail.autostartEnabled ? 'on' : 'off' }}
                </NTag>
                <NTag size="small" :type="detail.firewallEnabled ? 'success' : 'default'" class="ml-1">
                  UFW: {{ detail.firewallEnabled ? 'on' : 'off' }}
                </NTag>
              </NDescriptionsItem>
              <NDescriptionsItem :span="2" label="装机目录 / 日志路径">
                <div class="font-mono text-xs">{{ detail.installDir || '-' }}</div>
                <div class="font-mono text-xs text-zinc-500">{{ detail.logPath || '-' }}</div>
              </NDescriptionsItem>
              <NDescriptionsItem v-if="detail.installedAt" :span="2" label="装机时间">
                {{ formatDateTime(detail.installedAt) }}
              </NDescriptionsItem>
            </NDescriptions>

            <!-- 远端 systemd 实时状态 -->
            <div v-if="canManage && statusData" class="sub-section">
              <div class="sub-section__title">远端 dante 状态</div>
              <NDescriptions bordered size="small" label-placement="left" :column="2">
                <NDescriptionsItem label="systemd">
                  <NTag size="small" :type="statusData.active === 'active' ? 'success' : 'error'">
                    {{ statusData.active || 'unknown' }}
                  </NTag>
                  <NTag size="small" type="default" class="ml-1">
                    自启: {{ statusData.enabled || '-' }}
                  </NTag>
                </NDescriptionsItem>
                <NDescriptionsItem label="版本">
                  <span class="font-mono text-xs">{{ statusData.version || '-' }}</span>
                </NDescriptionsItem>
                <NDescriptionsItem label="端口可达">
                  <NTag
                    size="small"
                    :type="socks5Listening === 'listening' ? 'success' : 'error'"
                  >
                    {{ socks5Listening === 'listening' ? '监听中' : '未监听' }}
                  </NTag>
                </NDescriptionsItem>
                <NDescriptionsItem label="进程启动">
                  <span class="text-xs">{{ statusData.uptimeFrom || '-' }}</span>
                </NDescriptionsItem>
                <NDescriptionsItem v-if="statusData.listening" :span="2" label="监听">
                  <pre class="status-pre">{{ statusData.listening }}</pre>
                </NDescriptionsItem>
              </NDescriptions>
            </div>
            <div v-else-if="canManage && statusError" class="sub-section__hint text-error">
              <NIcon :size="14"><XCircle /></NIcon> {{ statusError }}
            </div>
          </template>

          <!-- ===== Step 3: 安装 Agent ===== -->
          <template v-else-if="activeStep === 'agent'">
            <div class="step-content__actions">
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
              <NIcon :size="18"><AlertCircle /></NIcon>
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
              <NDescriptionsItem :span="1" label="说明">
                <span class="text-xs text-zinc-500">
                  agent 装好后会向后台每 30s 心跳; 心跳详情见下一步 "心跳健康"
                </span>
              </NDescriptionsItem>
            </NDescriptions>
          </template>

          <!-- ===== Step 4: 心跳健康 + 服务器信息 ===== -->
          <template v-else-if="activeStep === 'health'">
            <div class="step-content__actions">
              <NButton
                size="small"
                quaternary
                :loading="statusLoading"
                :disabled="!canManage"
                @click="loadStatus"
              >
                <template #icon><NIcon><RefreshCcw /></NIcon></template>
                {{ statusData ? '刷新主机信息' : '拉取主机信息' }}
              </NButton>
            </div>

            <!-- 心跳块 -->
            <div class="sub-section">
              <div class="sub-section__title">心跳健康</div>
              <NDescriptions bordered size="small" label-placement="left" :column="2">
                <NDescriptionsItem label="健康状态">
                  <NTag
                    v-if="!detail.agentToken"
                    size="small"
                  >未安装 agent</NTag>
                  <NTag
                    v-else-if="!detail.lastHealthAt"
                    size="small"
                    type="warning"
                  >已装未上线</NTag>
                  <NTag
                    v-else-if="isAgentOnline"
                    size="small"
                    type="success"
                  >
                    <template #icon><NIcon><CheckCircle2 /></NIcon></template>
                    在线
                  </NTag>
                  <NTag v-else size="small" type="error">
                    <template #icon><NIcon><XCircle /></NIcon></template>
                    离线
                  </NTag>
                </NDescriptionsItem>
                <NDescriptionsItem label="最近心跳">
                  <span v-if="detail.lastHealthAt" class="text-xs">
                    {{ relativeTime(detail.lastHealthAt) }}
                    <span class="text-zinc-400 ml-1">({{ formatDateTime(detail.lastHealthAt) }})</span>
                  </span>
                  <span v-else class="text-xs text-zinc-400">-</span>
                </NDescriptionsItem>
              </NDescriptions>
            </div>

            <!-- 服务器主机信息块 -->
            <div class="sub-section">
              <div class="sub-section__title">服务器主机信息 (远端探测)</div>
              <div v-if="!canManage" class="sub-section__hint">
                <NIcon :size="14"><AlertCircle /></NIcon>
                需要 SSH 凭据齐才能拉远端
              </div>
              <div v-else-if="!statusData && !statusLoading && !statusError" class="sub-section__hint">
                未拉取; 点上面 "拉取主机信息"
              </div>
              <div v-else-if="statusError" class="sub-section__hint text-error">
                <NIcon :size="14"><XCircle /></NIcon> {{ statusError }}
              </div>
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
              <div v-else class="sub-section__hint">远端未返回 hostInfo</div>
            </div>
          </template>
        </div>

        <!-- ============ 账面信息 (折叠/紧凑展示) ============ -->
        <div class="billing-block">
          <div class="billing-block__head">
            <div class="billing-block__title">账面</div>
            <NButton size="tiny" quaternary @click="emit('edit-billing', detail)">
              <template #icon><NIcon><Pencil /></NIcon></template>
              编辑
            </NButton>
          </div>
          <div class="billing-block__body">
            <div class="billing-cell">
              <div class="billing-cell__label">带宽</div>
              <div class="billing-cell__value">{{ detail.bandwidthMbps == null ? '∞' : `${detail.bandwidthMbps} Mbps` }}</div>
            </div>
            <div class="billing-cell">
              <div class="billing-cell__label">流量</div>
              <div class="billing-cell__value">{{ detail.trafficQuotaGb == null ? '∞' : `${detail.trafficQuotaGb} GB` }}</div>
            </div>
            <div class="billing-cell">
              <div class="billing-cell__label">月费</div>
              <div class="billing-cell__value">{{ detail.costMonthlyUsd != null ? `${detail.costMonthlyUsd} USD` : '-' }}</div>
            </div>
            <div class="billing-cell">
              <div class="billing-cell__label">账单日</div>
              <div class="billing-cell__value">{{ detail.billingCycleDay != null ? `每月 ${detail.billingCycleDay}` : '-' }}</div>
            </div>
            <div class="billing-cell">
              <div class="billing-cell__label">到期</div>
              <div class="billing-cell__value">{{ detail.expiresAt || '-' }}</div>
            </div>
            <div class="billing-cell">
              <div class="billing-cell__label">创建</div>
              <div class="billing-cell__value text-xs">{{ formatDateTime(detail.createdAt) }}</div>
            </div>
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
  gap: 14px;
}

/* ===== 紧凑 Header ===== */
.ip-header {
  padding: 8px 12px;
  background: var(--n-action-color, #fafafa);
  border-radius: 6px;
  border: 1px solid var(--n-border-color, #efeff5);
}
.ip-header__main {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.ip-header__addr {
  font-size: 16px;
  font-weight: 600;
  color: var(--n-text-color-1, #222);
}
.ip-header__meta {
  margin-top: 4px;
  font-size: 12px;
  color: var(--n-text-color-2, #666);
  display: flex;
  align-items: center;
  flex-wrap: wrap;
}
.ip-header__remark {
  max-width: 280px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ===== Section 标题 ===== */
.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  font-weight: 600;
  color: var(--n-text-color-2, #555);
}
.section-title__hint {
  font-size: 11px;
  color: var(--n-text-color-3, #aaa);
  font-weight: normal;
}

/* ===== 部署进度 step cards (= 主导航) ===== */
.step-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
}
.step-card {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid var(--n-border-color, #efeff5);
  border-radius: 6px;
  background: var(--n-card-color, #fff);
  cursor: pointer;
  transition: border-color 0.15s ease, box-shadow 0.15s ease, transform 0.15s ease;
  min-width: 0;
}
.step-card:hover {
  transform: translateY(-1px);
  box-shadow: 0 2px 6px rgba(0,0,0,0.05);
}
.step-card__dot {
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
.step-card--done .step-card__dot {
  background: var(--n-success-color, #18a058);
}
.step-card--done {
  border-color: color-mix(in srgb, var(--n-success-color, #18a058) 30%, var(--n-border-color, #efeff5));
}
.step-card--pending {
  /* 灰色: 没操作就是灰, 但仍可点 (不卡步骤) */
  background: #fafafa;
}
.step-card--active {
  border-color: var(--n-primary-color, #2080f0);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--n-primary-color, #2080f0) 18%, transparent);
}
.step-card__body { min-width: 0; flex: 1; }
.step-card__label {
  font-size: 13px;
  font-weight: 600;
  color: var(--n-text-color-1, #222);
}
.step-card__hint {
  font-size: 11px;
  color: var(--n-text-color-3, #999);
  margin-top: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* ===== Step 内容区 ===== */
.step-content {
  padding: 14px;
  border: 1px solid var(--n-border-color, #efeff5);
  border-radius: 6px;
  background: var(--n-card-color, #fff);
  border-top: 2px solid var(--n-primary-color, #2080f0);
}
.step-content__actions {
  display: flex;
  align-items: center;
  gap: 6px;
  padding-bottom: 12px;
  margin-bottom: 12px;
  border-bottom: 1px dashed var(--n-divider-color, #efeff5);
  flex-wrap: wrap;
}
.sub-section {
  margin-top: 16px;
}
.sub-section__title {
  font-size: 12px;
  font-weight: 600;
  color: var(--n-text-color-2, #666);
  margin-bottom: 8px;
}
.sub-section__hint {
  font-size: 12px;
  color: var(--n-text-color-3, #999);
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 0;
}

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
  max-height: 180px;
  overflow: auto;
}

/* ===== 账面紧凑卡 ===== */
.billing-block {
  border: 1px solid var(--n-border-color, #efeff5);
  border-radius: 6px;
  padding: 10px 14px;
  background: var(--n-card-color, #fff);
}
.billing-block__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.billing-block__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--n-text-color-2, #555);
}
.billing-block__body {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 8px;
}
.billing-cell {
  padding: 6px 8px;
  border: 1px solid var(--n-border-color, #efeff5);
  border-radius: 4px;
  background: var(--n-action-color, #fafafa);
  min-width: 0;
}
.billing-cell__label {
  font-size: 11px;
  color: var(--n-text-color-3, #999);
  margin-bottom: 2px;
}
.billing-cell__value {
  font-size: 13px;
  font-family: monospace;
  color: var(--n-text-color-1, #222);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* ===== 颜色辅助 ===== */
.text-error { color: var(--n-error-color, #d03050); }
.text-warning { color: var(--n-warning-color, #f0a020); }


@media (max-width: 720px) {
  .step-cards { grid-template-columns: repeat(2, 1fr); }
  .billing-block__body { grid-template-columns: repeat(3, 1fr); }
}
</style>
