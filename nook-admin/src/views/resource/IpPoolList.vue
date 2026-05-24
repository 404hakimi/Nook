<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import {
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Copy,
  FileText,
  Globe2,
  KeyRound,
  MoreHorizontal,
  Pencil,
  Plus,
  RefreshCcw,
  Rocket,
  Search,
  Server as ServerIcon,
  Trash2,
  Undo2,
  Users,
  Zap
} from 'lucide-vue-next'
import {
  NButton,
  NCard,
  NDropdown,
  NEmpty,
  NIcon,
  NInput,
  NPagination,
  NSelect,
  NSpin,
  NTag,
  NTooltip,
  useMessage
} from 'naive-ui'
import { useConfirm } from '@/composables/useConfirm'
import {
  IP_POOL_LIFECYCLE_LABELS,
  IP_POOL_LIFECYCLE_OPTIONS,
  IP_POOL_LIFECYCLE_TAG_TYPE,
  IP_POOL_STATUS_LABELS,
  IP_POOL_STATUS_OPTIONS,
  deleteIpPool,
  getIpPoolSummary,
  pageIpPool,
  releaseIpPool,
  transitionIpPoolLifecycle,
  type ResourceIpPool,
  type ResourceIpPoolQuery,
  type ResourceIpPoolSummary
} from '@/api/resource/ip-pool'
import { listEnabledRegions, type ResourceRegion } from '@/api/resource/region'
import { IP_TYPE_CODE_LABELS, listIpTypes, type ResourceIpType } from '@/api/resource/ip-type'
import IpPoolDeployDialog from './IpPoolDeployDialog.vue'
import IpPoolCreateDialog from './IpPoolCreateDialog.vue'
import IpPoolCreateChoiceDialog from './IpPoolCreateChoiceDialog.vue'
import IpPoolTestDialog from './IpPoolTestDialog.vue'
import IpPoolSyncCredsDialog from './IpPoolSyncCredsDialog.vue'
import IpPoolStatusDialog from './IpPoolStatusDialog.vue'
import IpPoolLogDialog from './IpPoolLogDialog.vue'
import IpPoolCoreEditDialog from './dialogs/IpPoolCoreEditDialog.vue'
import IpPoolCredentialEditDialog from './dialogs/IpPoolCredentialEditDialog.vue'
import IpPoolBillingEditDialog from './dialogs/IpPoolBillingEditDialog.vue'
import IpPoolSocks5EditDialog from './dialogs/IpPoolSocks5EditDialog.vue'
import AgentProvisionDialog from '@/views/agent/AgentProvisionDialog.vue'
import RegionFlag from '@/components/RegionFlag.vue'

const message = useMessage()
const { confirm } = useConfirm()

const ipTypes = ref<ResourceIpType[]>([])
const ipTypeOptions = computed(() => [
  { label: '全部类型', value: '' },
  ...ipTypes.value.map((t) => ({
    label: t.name + (IP_TYPE_CODE_LABELS[t.code] ? ` (${IP_TYPE_CODE_LABELS[t.code]})` : ''),
    value: t.id
  }))
])

const regions = ref<ResourceRegion[]>([])
const regionOptions = computed(() => [
  { label: '全部', value: '' },
  ...regions.value.map((r) => ({ label: `${r.flagEmoji || ''} ${r.displayName}`, value: r.code }))
])

function regionRecord(code?: string): ResourceRegion | null {
  if (!code) return null
  return regions.value.find((x) => x.code === code) ?? null
}

const query = reactive<Required<Pick<ResourceIpPoolQuery, 'pageNo' | 'pageSize'>> & ResourceIpPoolQuery>({
  pageNo: 1,
  pageSize: 12,
  keyword: '',
  lifecycleState: undefined,
  status: undefined,
  region: '',
  ipTypeId: ''
})
const list = ref<ResourceIpPool[]>([])
const total = ref(0)
const loading = ref(false)
const summary = ref<ResourceIpPoolSummary>({
  total: 0, installing: 0, ready: 0, live: 0, retired: 0,
  available: 0, occupied: 0, cooling: 0, reserved: 0
})

async function loadSummary() {
  try {
    summary.value = await getIpPoolSummary()
  } catch { /* silent */ }
}

/** 点 stats 卡片 = 按 lifecycle 或 status 过滤; 再点同一个清掉过滤 */
function applyStatsFilter(opts: { lifecycleState?: string; status?: string }) {
  if (opts.lifecycleState != null) {
    query.lifecycleState = query.lifecycleState === opts.lifecycleState ? undefined : (opts.lifecycleState as never)
  }
  if (opts.status != null) {
    query.status = query.status === opts.status ? undefined : (opts.status as never)
  }
  query.pageNo = 1
  void loadList()
}

async function loadIpTypes() {
  try {
    ipTypes.value = await listIpTypes()
  } catch (e) {
    console.error('[ip-pool] 加载 IP 类型失败:', e)
  }
}

async function loadRegions() {
  try {
    regions.value = await listEnabledRegions()
  } catch { /* */ }
}

async function loadList() {
  loading.value = true
  try {
    const res = await pageIpPool({
      pageNo: query.pageNo,
      pageSize: query.pageSize,
      keyword: query.keyword || undefined,
      lifecycleState: query.lifecycleState,
      status: query.status,
      region: query.region || undefined,
      ipTypeId: query.ipTypeId || undefined
    })
    const maxPage = res.total > 0 ? Math.ceil(res.total / query.pageSize) : 1
    if (query.pageNo > maxPage) {
      query.pageNo = maxPage
      loading.value = false
      await loadList()
      return
    }
    list.value = res.records
    total.value = res.total
  } catch {
    /* */
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.pageNo = 1
  query.keyword = ''
  query.lifecycleState = undefined
  query.status = undefined
  query.region = ''
  query.ipTypeId = ''
  loadList()
}

function onSearch() {
  query.pageNo = 1
  loadList()
}

function ipTypeName(typeId: string): string {
  const t = ipTypes.value.find((x) => x.id === typeId)
  if (!t) return typeId
  const label = IP_TYPE_CODE_LABELS[t.code] ?? t.code
  return `${t.name} · ${label}`
}

function statusTagType(status: string): 'success' | 'info' | 'warning' | 'default' {
  switch (status) {
    case 'AVAILABLE': return 'success'
    case 'RESERVED': return 'warning'
    case 'OCCUPIED': return 'info'
    case 'COOLING': return 'default'
    default: return 'default'
  }
}

// ===== lifecycle 流转 =====
const LIFECYCLE_DROPDOWN_OPTIONS = [
  { label: '装机中 INSTALLING', key: 'INSTALLING' },
  { label: '待上线 READY', key: 'READY' },
  { label: '运行中 LIVE', key: 'LIVE' },
  { label: '已退役 RETIRED', key: 'RETIRED' }
]

async function onLifecycleSelect(ip: ResourceIpPool, target: string) {
  if (ip.lifecycleState === target) return
  const targetLabel = IP_POOL_LIFECYCLE_LABELS[target] || target
  const ok = await confirm({
    title: '切换生命周期',
    message: `把 IP ${ip.ipAddress} 从 ${IP_POOL_LIFECYCLE_LABELS[ip.lifecycleState]} 切到 ${targetLabel}?`,
    confirmText: '切换'
  })
  if (!ok) return
  try {
    await transitionIpPoolLifecycle(ip.id, target)
    message.success(`已切换到 ${targetLabel}`)
    loadList()
    loadSummary()
  } catch { /* */ }
}

// ===== 分段编辑 (4 个独立 dialog) =====
const coreEditOpen = ref(false)
const credentialEditOpen = ref(false)
const billingEditOpen = ref(false)
const socks5EditOpen = ref(false)
const editingIp = ref<ResourceIpPool | null>(null)

function openCoreEdit(ip: ResourceIpPool) { editingIp.value = ip; coreEditOpen.value = true }
function openCredentialEdit(ip: ResourceIpPool) { editingIp.value = ip; credentialEditOpen.value = true }
function openBillingEdit(ip: ResourceIpPool) { editingIp.value = ip; billingEditOpen.value = true }
function openSocks5Edit(ip: ResourceIpPool) { editingIp.value = ip; socks5EditOpen.value = true }

const EDIT_DROPDOWN_OPTIONS = [
  { label: '核心信息 (区域 / 类型 / IP / 部署模式)', key: 'core' },
  { label: 'SSH 凭据', key: 'credential' },
  { label: '账面 (带宽 / 成本 / 到期)', key: 'billing' },
  { label: 'dante 配置 + 限速', key: 'socks5' }
]

function onEditSelect(ip: ResourceIpPool, key: string) {
  switch (key) {
    case 'core': openCoreEdit(ip); break
    case 'credential': openCredentialEdit(ip); break
    case 'billing': openBillingEdit(ip); break
    case 'socks5': openSocks5Edit(ip); break
  }
}

// ===== 装 landing agent (provisionMode=1 才有意义) =====
const provisionOpen = ref(false)
const provisionIpId = ref<string | null>(null)

function openProvision(ip: ResourceIpPool) {
  provisionIpId.value = ip.id
  provisionOpen.value = true
}

function onSaved() {
  loadList()
  loadSummary()
}

// ===== 删除 =====
async function onDelete(ip: ResourceIpPool) {
  const ok = await confirm({
    title: '删除 IP',
    message: `从池中删除 ${ip.ipAddress}? 此操作不可恢复.`,
    type: 'danger',
    confirmText: '删除'
  })
  if (!ok) return
  try {
    await deleteIpPool(ip.id)
    message.success('已删除')
    onSaved()
  } catch { /* */ }
}

// ===== 退订 (occupied → cooling) =====
async function onRelease(ip: ResourceIpPool) {
  const ok = await confirm({
    title: '退订 IP',
    message: `把 ${ip.ipAddress} 置为冷却中?`,
    type: 'warning',
    confirmText: '退订'
  })
  if (!ok) return
  try {
    await releaseIpPool(ip.id)
    message.success('已置冷却')
    onSaved()
  } catch { /* */ }
}

// ===== 新增 IP 入口 =====
const createChoiceOpen = ref(false)
const createOpen = ref(false)
const deployOpen = ref(false)
const deployIpId = ref<string | null>(null)

function openCreateChoice() { createChoiceOpen.value = true }
function openCreate() { createOpen.value = true }

function onCreatedAfterChoice(ipId: string) {
  void ipId
  onSaved()
}

function onInstallNow(ipId: string) {
  deployIpId.value = ipId
  deployOpen.value = true
}

function openDeploy(ip: ResourceIpPool) {
  deployIpId.value = ip.id
  deployOpen.value = true
}

function onDeployInstalled(ipId: string) {
  void ipId
  onSaved()
}

// ===== 凭据 / 拨测 =====
function canTest(ip: ResourceIpPool): boolean {
  return !!ip.ipAddress && !!ip.socks5Port && !!ip.socks5Username && !!ip.socks5Password
}

function canManage(ip: ResourceIpPool): boolean {
  // 详情/日志/切自启都依赖 IP 池条目里存储的 SSH 凭据
  return ip.provisionMode === 1 && !!ip.sshPassword
}

const testOpen = ref(false)
const testTarget = ref<ResourceIpPool | null>(null)
function openTest(ip: ResourceIpPool) { testTarget.value = ip; testOpen.value = true }

const syncCredsOpen = ref(false)
const syncCredsTarget = ref<ResourceIpPool | null>(null)
function openSyncCreds(ip: ResourceIpPool) { syncCredsTarget.value = ip; syncCredsOpen.value = true }
function onSynced() { loadList() }

const statusOpen = ref(false)
const statusTarget = ref<ResourceIpPool | null>(null)
function openStatus(ip: ResourceIpPool) { statusTarget.value = ip; statusOpen.value = true }

const logOpen = ref(false)
const logTarget = ref<ResourceIpPool | null>(null)
function openLog(ip: ResourceIpPool) { logTarget.value = ip; logOpen.value = true }

/**
 * 拼标准 socks5:// URL: socks5://[user[:pass]@]host:port
 * user/pass 走 encodeURIComponent 防特殊字符破坏 URL 结构.
 */
function buildSocks5Url(ip: ResourceIpPool): string | null {
  if (!ip.ipAddress || !ip.socks5Port) return null
  const user = ip.socks5Username?.trim() ?? ''
  const pass = ip.socks5Password ?? ''
  let auth = ''
  if (user) {
    auth = encodeURIComponent(user)
    if (pass) auth += ':' + encodeURIComponent(pass)
    auth += '@'
  }
  return `socks5://${auth}${ip.ipAddress}:${ip.socks5Port}`
}

async function copySocks5Url(ip: ResourceIpPool) {
  const url = buildSocks5Url(ip)
  if (!url) {
    message.warning('SOCKS5 信息不完整')
    return
  }
  try {
    await navigator.clipboard.writeText(url)
    const masked = ip.socks5Password ? url.replace(/:[^@]*@/, ':***@') : url
    message.success(`已复制 ${masked}`)
  } catch {
    message.error('复制失败')
  }
}

// ===== 卡片的"更多"下拉选项 =====
function moreOptionsFor(ip: ResourceIpPool) {
  const isInstalling = ip.lifecycleState === 'INSTALLING' || ip.lifecycleState === 'READY'
  const isLive = ip.lifecycleState === 'LIVE'
  return [
    ip.provisionMode === 1 && isInstalling
      ? { label: '装机 SOCKS5', key: 'deploy', icon: () => h(NIcon, null, { default: () => h(Rocket) }) }
      : null,
    ip.provisionMode === 1 && isLive
      ? { label: '重装 SOCKS5', key: 'deploy', icon: () => h(NIcon, null, { default: () => h(Rocket) }) }
      : null,
    canManage(ip) ? { label: '查看 dante 状态', key: 'status' } : null,
    canManage(ip) ? { label: '查看日志', key: 'log', icon: () => h(NIcon, null, { default: () => h(FileText) }) } : null,
    ip.provisionMode === 1 ? { label: '装 landing agent', key: 'provision' } : null,
    ip.provisionMode === 1 && canTest(ip)
      ? { label: '同步 SOCKS5 凭据', key: 'sync', icon: () => h(NIcon, null, { default: () => h(KeyRound) }) }
      : null,
    { type: 'divider', key: 'd1' },
    ...LIFECYCLE_DROPDOWN_OPTIONS.map((o) => ({ ...o, key: `lc:${o.key}` })),
    { type: 'divider', key: 'd2' },
    ip.status === 'OCCUPIED'
      ? { label: '退订到 cooling', key: 'release', icon: () => h(NIcon, null, { default: () => h(Undo2) }) }
      : null,
    { label: '删除 IP', key: 'delete', icon: () => h(NIcon, null, { default: () => h(Trash2) }) }
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  ].filter(Boolean) as any[]
}

function onMoreSelect(ip: ResourceIpPool, key: string) {
  switch (key) {
    case 'deploy': return openDeploy(ip)
    case 'status': return openStatus(ip)
    case 'log': return openLog(ip)
    case 'provision': return openProvision(ip)
    case 'sync': return openSyncCreds(ip)
    case 'release': return onRelease(ip)
    case 'delete': return onDelete(ip)
    default:
      if (key.startsWith('lc:')) return onLifecycleSelect(ip, key.slice(3))
  }
}

onMounted(async () => {
  await Promise.all([loadIpTypes(), loadRegions()])
  await Promise.all([loadList(), loadSummary()])
})
</script>

<template>
  <div class="space-y-4">
    <!-- 顶部统计卡片: 点击 = 切换过滤; 再点同一个清掉 -->
    <div class="grid grid-cols-2 sm:grid-cols-4 gap-4">
      <div
        class="stat-card stat-card--zinc"
        :class="{ 'stat-card--active': !query.lifecycleState && !query.status }"
        @click="() => { query.lifecycleState = undefined; query.status = undefined; query.pageNo = 1; loadList() }"
      >
        <div class="stat-card__accent" />
        <div class="stat-card__body">
          <div class="stat-card__label">总 IP</div>
          <div class="stat-card__value">{{ summary.total }}</div>
          <div class="stat-card__hint">全量, 含已退役</div>
        </div>
        <div class="stat-card__icon">
          <NIcon :size="28"><Globe2 /></NIcon>
        </div>
      </div>

      <div
        class="stat-card stat-card--green"
        :class="{ 'stat-card--active': query.lifecycleState === 'LIVE' }"
        @click="applyStatsFilter({ lifecycleState: 'LIVE' })"
      >
        <div class="stat-card__accent" />
        <div class="stat-card__body">
          <div class="stat-card__label">已部署 LIVE</div>
          <div class="stat-card__value">{{ summary.live }}</div>
          <div class="stat-card__hint">SOCKS5 已起 + 可对外服务</div>
        </div>
        <div class="stat-card__icon">
          <NIcon :size="28"><Rocket /></NIcon>
        </div>
      </div>

      <div
        class="stat-card stat-card--blue"
        :class="{ 'stat-card--active': query.status === 'AVAILABLE' }"
        @click="applyStatsFilter({ status: 'AVAILABLE' })"
      >
        <div class="stat-card__accent" />
        <div class="stat-card__body">
          <div class="stat-card__label">可分配</div>
          <div class="stat-card__value">{{ summary.available }}</div>
          <div class="stat-card__hint">allocator 可给新订阅</div>
        </div>
        <div class="stat-card__icon">
          <NIcon :size="28"><CheckCircle2 /></NIcon>
        </div>
      </div>

      <div
        class="stat-card stat-card--orange"
        :class="{ 'stat-card--active': query.status === 'OCCUPIED' }"
        @click="applyStatsFilter({ status: 'OCCUPIED' })"
      >
        <div class="stat-card__accent" />
        <div class="stat-card__body">
          <div class="stat-card__label">已占用</div>
          <div class="stat-card__value">{{ summary.occupied }}</div>
          <div class="stat-card__hint">已分配给会员订阅</div>
        </div>
        <div class="stat-card__icon">
          <NIcon :size="28"><Users /></NIcon>
        </div>
      </div>
    </div>

    <!-- 搜索栏 -->
    <NCard size="small">
      <div class="flex flex-wrap gap-3 items-end">
        <div>
          <div class="text-xs text-zinc-500 mb-1">关键词</div>
          <NInput
            v-model:value="query.keyword"
            size="small"
            placeholder="IP 地址"
            class="w-48"
            :input-props="{ style: 'font-family: monospace' }"
            @keyup.enter="onSearch"
          />
        </div>
        <div>
          <div class="text-xs text-zinc-500 mb-1">生命周期</div>
          <NSelect v-model:value="query.lifecycleState" :options="IP_POOL_LIFECYCLE_OPTIONS" size="small" class="w-28" />
        </div>
        <div>
          <div class="text-xs text-zinc-500 mb-1">占用状态</div>
          <NSelect v-model:value="query.status" :options="IP_POOL_STATUS_OPTIONS" size="small" class="w-28" />
        </div>
        <div>
          <div class="text-xs text-zinc-500 mb-1">区域</div>
          <NSelect v-model:value="query.region" :options="regionOptions" size="small" class="w-40" placeholder="选区域" />
        </div>
        <div>
          <div class="text-xs text-zinc-500 mb-1">类型</div>
          <NSelect v-model:value="query.ipTypeId" :options="ipTypeOptions" size="small" class="w-44" />
        </div>
        <NButton type="primary" size="small" @click="onSearch">
          <template #icon><NIcon><Search /></NIcon></template>
          搜索
        </NButton>
        <NButton quaternary size="small" @click="resetQuery">
          <template #icon><NIcon><RefreshCcw /></NIcon></template>
          重置
        </NButton>
        <div class="flex-1" />
        <NButton type="primary" size="small" @click="openCreateChoice" title="选择方式 (自部署 / 第三方)">
          <template #icon><NIcon><Plus /></NIcon></template>
          新增 IP
        </NButton>
      </div>
    </NCard>

    <!-- IP 卡片网格 -->
    <NSpin :show="loading">
      <NEmpty v-if="!loading && list.length === 0" description="暂无 IP" class="py-12">
        <template #extra>
          <NButton type="primary" size="small" @click="openCreateChoice">
            <template #icon><NIcon><Plus /></NIcon></template>
            新增第一个 IP
          </NButton>
        </template>
      </NEmpty>

      <div v-else class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
        <div
          v-for="ip in list"
          :key="ip.id"
          class="ip-card"
          :class="[`ip-card--lc-${ip.lifecycleState.toLowerCase()}`]"
        >
          <!-- header: IP + 部署模式 chip + 更多 -->
          <div class="ip-card__header">
            <div class="ip-card__title">
              <NIcon :size="18" class="ip-card__title-icon"><Globe2 /></NIcon>
              <span class="font-mono">{{ ip.ipAddress }}</span>
              <NTag
                v-if="ip.provisionMode === 2"
                size="tiny"
                round
                :bordered="false"
                class="ml-1"
                style="background: rgba(160,160,160,0.18); color: #555"
              >第三方</NTag>
            </div>
            <NDropdown
              trigger="click"
              :options="moreOptionsFor(ip)"
              @select="(k: string) => onMoreSelect(ip, k)"
            >
              <NButton size="small" quaternary circle title="更多操作">
                <template #icon><NIcon><MoreHorizontal /></NIcon></template>
              </NButton>
            </NDropdown>
          </div>

          <!-- region + type + 状态徽章 -->
          <div class="ip-card__meta">
            <div class="ip-card__meta-line">
              <RegionFlag :code="regionRecord(ip.region)?.countryCode" :fallback="regionRecord(ip.region)?.flagEmoji" :size="14" />
              <span class="ml-1 text-xs text-zinc-600">{{ regionRecord(ip.region)?.displayName || ip.region || '-' }}</span>
              <span class="text-xs text-zinc-400 mx-2">·</span>
              <span class="text-xs text-zinc-600">{{ ipTypeName(ip.ipTypeId) }}</span>
            </div>
            <div class="ip-card__badges">
              <NTag size="small" :type="IP_POOL_LIFECYCLE_TAG_TYPE[ip.lifecycleState] || 'default'">
                {{ IP_POOL_LIFECYCLE_LABELS[ip.lifecycleState] || ip.lifecycleState }}
              </NTag>
              <NTag size="small" :type="statusTagType(ip.status)">
                {{ IP_POOL_STATUS_LABELS[ip.status] || ip.status }}
              </NTag>
            </div>
          </div>

          <!-- SOCKS5 端口 + 用户 + 复制按钮; 带宽 / 流量 -->
          <div class="ip-card__body">
            <div v-if="ip.socks5Port" class="ip-card__row">
              <NIcon :size="14" class="ip-card__row-icon"><ServerIcon /></NIcon>
              <span class="font-mono text-xs">:{{ ip.socks5Port }}</span>
              <span v-if="ip.socks5Username" class="ml-1 font-mono text-xs text-zinc-500">/ {{ ip.socks5Username }}</span>
              <span
                v-if="!ip.socks5Password"
                class="ml-2 text-xs"
                style="color: var(--n-warning-color, #f0a020)"
              >(无密码)</span>
              <NTooltip placement="top">
                <template #trigger>
                  <NButton size="tiny" quaternary class="ml-1" @click="copySocks5Url(ip)">
                    <template #icon><NIcon><Copy /></NIcon></template>
                  </NButton>
                </template>
                <div class="text-xs">复制 socks5://user:pass@host:port</div>
              </NTooltip>
            </div>
            <div v-else class="ip-card__row">
              <NIcon :size="14" class="ip-card__row-icon"><ServerIcon /></NIcon>
              <span class="text-xs text-zinc-400">未配置 SOCKS5</span>
            </div>

            <div class="ip-card__row">
              <NIcon :size="14" class="ip-card__row-icon"><Zap /></NIcon>
              <span class="text-xs text-zinc-500 font-mono">
                {{ ip.bandwidthMbps == null ? '∞' : `${ip.bandwidthMbps} Mbps` }}
                <span class="text-zinc-400 mx-1">·</span>
                {{ ip.trafficQuotaGb == null ? '∞' : `${ip.trafficQuotaGb} GB` }}
              </span>
            </div>
          </div>

          <!-- footer 操作: 测试 / 编辑▾ / 装机 (按 lifecycle 显示) -->
          <div class="ip-card__footer">
            <NButton
              v-if="canTest(ip)"
              size="small"
              quaternary
              type="warning"
              title="拨号自检 SOCKS5"
              @click="openTest(ip)"
            >
              <template #icon><NIcon><Zap /></NIcon></template>
              测试
            </NButton>
            <NDropdown trigger="click" :options="EDIT_DROPDOWN_OPTIONS" @select="(k: string) => onEditSelect(ip, k)">
              <NButton size="small" quaternary title="编辑分段配置">
                <template #icon><NIcon><Pencil /></NIcon></template>
                编辑
              </NButton>
            </NDropdown>
            <NButton
              v-if="ip.provisionMode === 1 && (ip.lifecycleState === 'INSTALLING' || ip.lifecycleState === 'READY')"
              size="small"
              type="primary"
              @click="openDeploy(ip)"
            >
              <template #icon><NIcon><Rocket /></NIcon></template>
              装机
            </NButton>
            <NButton
              v-else-if="ip.provisionMode === 1 && ip.lifecycleState === 'LIVE'"
              size="small"
              quaternary
              type="info"
              @click="openDeploy(ip)"
              title="重装 dante"
            >
              <template #icon><NIcon><Rocket /></NIcon></template>
              重装
            </NButton>
          </div>
        </div>
      </div>
    </NSpin>

    <!-- 分页: 单独一行, 居中 -->
    <div class="flex justify-center pt-2" v-if="total > 0">
      <div class="flex items-center gap-3">
        <span class="text-xs text-zinc-500">共 {{ total }} 条</span>
        <NPagination
          :page="query.pageNo"
          :page-size="query.pageSize"
          :item-count="total"
          :page-sizes="[12, 24, 48]"
          show-size-picker
          @update:page="(p: number) => { query.pageNo = p; loadList() }"
          @update:page-size="(s: number) => { query.pageSize = s; query.pageNo = 1; loadList() }"
        >
          <template #prev>
            <NIcon><ChevronLeft /></NIcon>
          </template>
          <template #next>
            <NIcon><ChevronRight /></NIcon>
          </template>
        </NPagination>
      </div>
    </div>

    <!-- ===== Dialogs ===== -->
    <IpPoolCreateChoiceDialog v-model="createChoiceOpen" @choose-self-deploy="openCreate" />

    <IpPoolCreateDialog
      v-model="createOpen"
      :ip-types="ipTypes"
      :regions="regions"
      @created="onCreatedAfterChoice"
      @install-now="onInstallNow"
    />

    <IpPoolDeployDialog v-model="deployOpen" :ip-id="deployIpId" @installed="onDeployInstalled" />

    <IpPoolTestDialog v-model="testOpen" :ip="testTarget" />
    <IpPoolSyncCredsDialog v-model="syncCredsOpen" :ip="syncCredsTarget" @synced="onSynced" />
    <IpPoolStatusDialog v-model="statusOpen" :ip="statusTarget" @changed="loadList" />
    <IpPoolLogDialog v-model="logOpen" :ip="logTarget" />

    <!-- 分段编辑 dialog; v-if 让每次切换 IP 重建 dialog, watch immediate=true 立即 fetch 回填 -->
    <IpPoolCoreEditDialog v-if="editingIp" v-model="coreEditOpen" :ip-pool="editingIp" @saved="onSaved" />
    <IpPoolCredentialEditDialog v-if="editingIp" v-model="credentialEditOpen" :ip-id="editingIp.id" :ip-address="editingIp.ipAddress" @saved="onSaved" />
    <IpPoolBillingEditDialog v-if="editingIp" v-model="billingEditOpen" :ip-id="editingIp.id" @saved="onSaved" />
    <IpPoolSocks5EditDialog v-if="editingIp" v-model="socks5EditOpen" :ip-id="editingIp.id" @saved="onSaved" />

    <AgentProvisionDialog
      v-model="provisionOpen"
      :initial-server-id="provisionIpId"
      initial-role="landing"
      @dispatched="onSaved"
    />
  </div>
</template>

<style scoped>
/* ===== Stats 卡片 ===== */
.stat-card {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 20px;
  background: var(--n-card-color, #fff);
  border: 1px solid var(--n-border-color, #efeff5);
  border-radius: 8px;
  cursor: pointer;
  overflow: hidden;
  transition: transform 0.15s ease, box-shadow 0.15s ease, border-color 0.15s ease;
}
.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}
.stat-card--active {
  border-color: currentColor;
  box-shadow: 0 0 0 1px currentColor inset;
}
.stat-card__accent {
  position: absolute;
  top: 0;
  left: 0;
  width: 4px;
  height: 100%;
  background: currentColor;
}
.stat-card__body { flex: 1; min-width: 0; }
.stat-card__label {
  font-size: 13px;
  color: var(--n-text-color-3, #707070);
  margin-bottom: 4px;
}
.stat-card__value {
  font-size: 30px;
  font-weight: 600;
  line-height: 1.1;
  color: currentColor;
}
.stat-card__hint {
  font-size: 11px;
  color: var(--n-text-color-3, #909399);
  margin-top: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.stat-card__icon {
  flex-shrink: 0;
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  background: color-mix(in srgb, currentColor 12%, transparent);
  color: currentColor;
}

.stat-card--zinc   { color: #71717a; }
.stat-card--green  { color: #18a058; }
.stat-card--blue   { color: #2080f0; }
.stat-card--orange { color: #f0a020; }

/* ===== IP 卡片 ===== */
.ip-card {
  position: relative;
  display: flex;
  flex-direction: column;
  background: var(--n-card-color, #fff);
  border: 1px solid var(--n-border-color, #efeff5);
  border-radius: 10px;
  padding: 14px 16px 10px 16px;
  transition: transform 0.15s ease, box-shadow 0.15s ease, border-color 0.15s ease;
  overflow: hidden;
}
.ip-card::before {
  /* 左侧色条, 按 lifecycle 区分 */
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 3px;
  height: 100%;
  background: currentColor;
  opacity: 0.85;
}
.ip-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.08);
  border-color: color-mix(in srgb, currentColor 30%, var(--n-border-color, #efeff5));
}

/* lifecycle 配色 (= 左侧色条颜色; 跟 NTag 颜色对齐) */
.ip-card--lc-installing { color: #2080f0; }
.ip-card--lc-ready      { color: #f0a020; }
.ip-card--lc-live       { color: #18a058; }
.ip-card--lc-retired    { color: #999; }

.ip-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}
.ip-card__title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 16px;
  font-weight: 600;
  color: var(--n-text-color-1, #222);
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
}
.ip-card__title-icon { color: currentColor; flex-shrink: 0; }

.ip-card__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 10px;
}
.ip-card__meta-line {
  display: flex;
  align-items: center;
  min-width: 0;
}
.ip-card__badges {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.ip-card__body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 8px 0 4px 0;
  border-top: 1px dashed var(--n-divider-color, #efeff5);
}
.ip-card__row {
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 22px;
  color: var(--n-text-color-2, #555);
}
.ip-card__row-icon { color: var(--n-text-color-3, #999); flex-shrink: 0; }

.ip-card__footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--n-divider-color, #efeff5);
}
</style>
