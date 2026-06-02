<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  CalendarClock,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Cpu,
  Database,
  Gauge,
  Globe2,
  PauseCircle,
  PlayCircle,
  Plus,
  RefreshCcw,
  Rocket,
  Search,
  ServerCog,
  Tag as TagIcon,
  Trash2,
  Users,
  Zap
} from 'lucide-vue-next'
import {
  NButton,
  NCard,
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
  SERVER_LANDING_LIFECYCLE_LABELS,
  SERVER_LANDING_LIFECYCLE_OPTIONS,
  SERVER_LANDING_LIFECYCLE_TAG_TYPE,
  SERVER_LANDING_STATUS_LABELS,
  SERVER_LANDING_STATUS_OPTIONS,
  deleteServerLanding,
  getServerLandingSummary,
  pageServerLanding,
  type ServerLanding,
  type ServerLandingQuery,
  type ServerLandingSummary
} from '@/api/resource/server-landing'
import { transitionServerLifecycle } from '@/api/resource/server'
import { AGENT_ONLINE_TAG_TYPE } from '@/api/agent/agent'
import type { SystemRegion } from '@/api/system/region'
import { IP_TYPE_CODE_LABELS, type SystemIpType } from '@/api/system/ip-type'
import { useRegionStore } from '@/stores/region'
import { useIpTypeStore } from '@/stores/ipType'
import { storeToRefs } from 'pinia'
import ServerLandingDeployDialog from './ServerLandingDeployDialog.vue'
import ServerCreateDialog from '@/views/server/dialogs/ServerCreateDialog.vue'
import ServerLandingCreateChoiceDialog from './ServerLandingCreateChoiceDialog.vue'
import ServerLandingTestDialog from './ServerLandingTestDialog.vue'
import RegionFlag from '@/components/RegionFlag.vue'
import RegionTreeFilter from '@/components/RegionTreeFilter.vue'

const router = useRouter()

const message = useMessage()
const { confirm } = useConfirm()

const regionStore = useRegionStore()
const ipTypeStore = useIpTypeStore()
const { list: regions } = storeToRefs(regionStore)
const { list: ipTypes } = storeToRefs(ipTypeStore)
const ipTypeOptions = computed(() => [
  { label: '全部类型', value: '' },
  ...ipTypes.value.map((t) => ({
    label: t.name + (IP_TYPE_CODE_LABELS[t.code] ? ` (${IP_TYPE_CODE_LABELS[t.code]})` : ''),
    value: t.id
  }))
])

function regionRecord(code?: string): SystemRegion | null {
  if (!code) return null
  return regions.value.find((x) => x.code === code) ?? null
}

// 左侧区域树筛选 (即时生效, 不走搜索按钮)
const regionFilter = ref<InstanceType<typeof RegionTreeFilter> | null>(null)
const regionCodes = ref<string[]>([])
function onRegionChange(codes: string[]) {
  regionCodes.value = codes
  query.pageNo = 1
  loadList()
}

const query = reactive<Required<Pick<ServerLandingQuery, 'pageNo' | 'pageSize'>> & ServerLandingQuery>({
  pageNo: 1,
  pageSize: 12,
  keyword: '',
  lifecycleState: undefined,
  status: undefined,
  ipTypeId: ''
})
const list = ref<ServerLanding[]>([])
const total = ref(0)
const loading = ref(false)
const summary = ref<ServerLandingSummary>({
  total: 0, installing: 0, ready: 0, live: 0, retired: 0,
  available: 0, occupied: 0, cooling: 0, reserved: 0
})

async function loadSummary() {
  try {
    summary.value = await getServerLandingSummary()
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


async function loadList() {
  loading.value = true
  try {
    const res = await pageServerLanding({
      pageNo: query.pageNo,
      pageSize: query.pageSize,
      keyword: query.keyword || undefined,
      lifecycleState: query.lifecycleState,
      status: query.status,
      regionCodes: regionCodes.value.length ? regionCodes.value : undefined,
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
  query.ipTypeId = ''
  regionCodes.value = []
  regionFilter.value?.reset()
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
    default: return 'default'
  }
}

type PillTone = 'success' | 'warning' | 'error' | 'default'
/** 在线状态短标签; 在线度配色复用线路机 AGENT_ONLINE_TAG_TYPE. */
const ONLINE_SHORT: Record<string, string> = {
  ONLINE: '在线', WARN: '延迟', TEMP_UNHEALTHY: '不健康', OFFLINE: '掉线', NEVER: '未上报'
}
/** 心跳延迟紧凑格式 (12s / 5m / 2h / 3d). */
function fmtElapsed(sec?: number): string {
  if (sec == null) return ''
  if (sec < 60) return `${sec}s`
  if (sec < 3600) return `${Math.floor(sec / 60)}m`
  if (sec < 86400) return `${Math.floor(sec / 3600)}h`
  return `${Math.floor(sec / 86400)}d`
}
/** agent 版本剥角色前缀 (landing-0.8.2 → 0.8.2). */
function agentVer(v?: string): string | null {
  if (!v) return null
  const m = /^(?:frontline|landing)-(.+)$/.exec(v)
  return m ? m[1] : v
}
/** Agent 安装: 上报过版本即视为已装 (已装绿 / 未装灰). */
function agentOf(ip: ServerLanding): { tone: PillTone; label: string } {
  const v = agentVer(ip.agentVersion)
  return v ? { tone: 'success', label: `Agent v${v}` } : { tone: 'default', label: 'Agent 未装' }
}
/** SOCKS5(dante) 安装: 第三方无装机概念; 自部署看 dante 版本 / 装机完成 (已装绿 / 未装灰). */
function socksOf(ip: ServerLanding): { tone: PillTone; label: string } {
  if (ip.provisionMode === 2) return { tone: 'default', label: '第三方' }
  if (ip.danteVersion) return { tone: 'success', label: `SOCKS5 ${ip.danteVersion}` }
  if (ip.installedAt) return { tone: 'success', label: 'SOCKS5 已装' }
  return { tone: 'default', label: 'SOCKS5 未部署' }
}

/** 距到期天数; null = 无到期日. */
function daysToExpiry(d?: string): number | null {
  if (!d) return null
  const t = new Date(`${d}T00:00:00`).getTime()
  return Number.isNaN(t) ? null : Math.floor((t - Date.now()) / 86400000)
}
/** 到期紧急度配色: 已过期红 / 30 天内橙 / 其余常态. */
function expiryTone(d?: string): 'error' | 'warning' | 'normal' {
  const n = daysToExpiry(d)
  if (n === null) return 'normal'
  if (n < 0) return 'error'
  if (n <= 30) return 'warning'
  return 'normal'
}
function expiryTitle(d?: string): string {
  const n = daysToExpiry(d)
  if (n === null) return ''
  if (n < 0) return `已过期 ${-n} 天, 请尽快续费`
  if (n === 0) return '今天到期'
  return `剩 ${n} 天到期`
}

// ===== lifecycle 流转 (admin 只暴露 2 项: 停用 / 启用; INSTALLING/READY 是装机内部态) =====
/** 占用中 (已分配 / 预占): 在用, 不可停用. */
function landingInUse(ip: ServerLanding): boolean {
  return ip.status === 'OCCUPIED' || ip.status === 'RESERVED'
}
async function onSuspend(ip: ServerLanding) {
  // 占用 / 预占中不可停用 (后端同样拦截; 这里提前挡掉)
  if (ip.lifecycleState !== 'LIVE' || ip.status === 'OCCUPIED' || ip.status === 'RESERVED') return
  const ok = await confirm({
    title: '停用落地机',
    message: `停用落地机 ${ip.ipAddress}? 停用后将从分配池移除, 不再分配给新订阅.`,
    type: 'warning',
    confirmText: '停用'
  })
  if (!ok) return
  try {
    await transitionServerLifecycle(ip.id, 'RETIRED')
    message.success('已停用')
    onSaved()
    void refreshDetail()
  } catch { /* */ }
}

async function onActivate(ip: ServerLanding) {
  if (ip.lifecycleState !== 'RETIRED') return
  const ok = await confirm({
    title: '启用落地机',
    message: `启用落地机 ${ip.ipAddress}? 启用后此落地机可重新被分配给新订阅.`,
    type: 'info',
    confirmText: '启用'
  })
  if (!ok) return
  try {
    await transitionServerLifecycle(ip.id, 'LIVE')
    message.success('已启用')
    onSaved()
    void refreshDetail()
  } catch { /* */ }
}

// 编辑分段 dialog (核心/SSH/账面/dante) + agent provision + log 全部归详情页;
// list 卡片只保留高频: 测试 / 装机·重装 / 详情 / 停用·启用 / 删除.

function onSaved() {
  loadList()
  loadSummary()
  void refreshDetail()
}

// ===== 删除 (入口在详情 dialog) =====
async function onDelete(ip: ServerLanding) {
  const ok = await confirm({
    title: '删除落地机',
    message: `从池中删除 ${ip.ipAddress}? 此操作不可恢复.`,
    type: 'danger',
    confirmText: '删除'
  })
  if (!ok) return
  try {
    await deleteServerLanding(ip.id)
    message.success('已删除')
    detailOpen.value = false  // 删除后关详情
    onSaved()
  } catch { /* */ }
}

// ===== 新增落地机 入口 =====
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

function openDeploy(ip: ServerLanding) {
  deployIpId.value = ip.id
  deployOpen.value = true
}

function onDeployInstalled(ipId: string) {
  void ipId
  onSaved()
}

// ===== 凭据 / 拨测 =====
function canTest(ip: ServerLanding): boolean {
  return !!ip.ipAddress && !!ip.socks5Port && !!ip.socks5Username && !!ip.socks5Password
}

function canManage(ip: ServerLanding): boolean {
  // 详情/日志/切自启都依赖落地机条目里存储的 SSH 凭据
  return ip.provisionMode === 1 && !!ip.sshPassword
}

const testOpen = ref(false)
const testTarget = ref<ServerLanding | null>(null)
function openTest(ip: ServerLanding) { testTarget.value = ip; testOpen.value = true }

// log dialog 入口归详情页 socks5 tab; socks5:// URL 复制由详情页处理

// ===== 详情 (路由页) =====
// detailOpen 仅用于 onDelete 关闭可能存在的详情 dialog; 当前详情走独立路由, 占位 ref
const detailOpen = ref(false)
function openDetail(ip: ServerLanding) {
  router.push({ name: 'resource-server-landing-detail', params: { id: ip.id } })
}

/** 详情页不在 list 里, lifecycle 切换不需要刷详情 (各自的页) */
async function refreshDetail() { /* no-op; 详情走独立路由 */ }

onMounted(async () => {
  await Promise.all([ipTypeStore.ensureLoaded(), regionStore.ensureLoaded()])
  await Promise.all([loadList(), loadSummary()])
})
</script>

<template>
  <div class="landing-layout">
    <RegionTreeFilter ref="regionFilter" @change="onRegionChange" />
    <div class="landing-main space-y-4">
    <!-- 顶部统计卡片: 点击 = 切换过滤; 再点同一个清掉 -->
    <div class="grid grid-cols-2 sm:grid-cols-4 gap-4">
      <div
        class="stat-card stat-card--zinc"
        :class="{ 'stat-card--active': !query.lifecycleState && !query.status }"
        @click="() => { query.lifecycleState = undefined; query.status = undefined; query.pageNo = 1; loadList() }"
      >
        <div class="stat-card__accent" />
        <div class="stat-card__body">
          <div class="stat-card__label">总落地机</div>
          <div class="stat-card__value">{{ summary.total }}</div>
          <div class="stat-card__hint">全量, 含已停用</div>
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
          <div class="stat-card__hint">可分配给新订阅</div>
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
          <NSelect v-model:value="query.lifecycleState" :options="SERVER_LANDING_LIFECYCLE_OPTIONS" size="small" class="w-28" />
        </div>
        <div>
          <div class="text-xs text-zinc-500 mb-1">占用状态</div>
          <NSelect v-model:value="query.status" :options="SERVER_LANDING_STATUS_OPTIONS" size="small" class="w-28" />
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
          新建落地机
        </NButton>
      </div>
    </NCard>

    <!-- 落地机卡片网格 -->
    <NSpin :show="loading">
      <NEmpty v-if="!loading && list.length === 0" description="暂无落地机" class="py-12">
        <template #extra>
          <NButton type="primary" size="small" @click="openCreateChoice">
            <template #icon><NIcon><Plus /></NIcon></template>
            新建第一台落地机
          </NButton>
        </template>
      </NEmpty>

      <div v-else class="flex flex-col gap-2.5">
        <div
          v-for="ip in list"
          :key="ip.id"
          class="lc-card"
          :class="[`lc-card--${ip.lifecycleState.toLowerCase()}`]"
          role="button"
          tabindex="0"
          title="点击查看详情"
          @click="openDetail(ip)"
          @keyup.enter="openDetail(ip)"
        >
          <div class="lc-content">
            <!-- 第一行: 区域 + IP 类型 + 状态徽标 -->
            <div class="lc-r1">
              <RegionFlag
                :code="regionRecord(ip.region)?.countryCode"
                :fallback="regionRecord(ip.region)?.flagEmoji"
                squared
                :size="18"
              />
              <span class="lc-country">{{ regionRecord(ip.region)?.displayName || ip.region || '未设区域' }}</span>
              <span class="lc-iptype">
                <NIcon :size="12"><TagIcon /></NIcon>
                {{ ipTypeName(ip.ipTypeId) }}
              </span>
              <NTag v-if="ip.provisionMode === 2" size="tiny" round :bordered="false" class="lc-third">第三方</NTag>
              <span class="flex-1" />
              <span
                v-if="ip.expiresAt"
                class="lc-expiry"
                :class="`lc-expiry--${expiryTone(ip.expiresAt)}`"
                :title="expiryTitle(ip.expiresAt)"
              >
                <NIcon :size="12"><CalendarClock /></NIcon>到期 {{ ip.expiresAt }}
              </span>
              <NTag size="small" :type="SERVER_LANDING_LIFECYCLE_TAG_TYPE[ip.lifecycleState] || 'default'">
                {{ SERVER_LANDING_LIFECYCLE_LABELS[ip.lifecycleState] || ip.lifecycleState }}
              </NTag>
              <NTag size="small" :type="statusTagType(ip.status)">
                {{ SERVER_LANDING_STATUS_LABELS[ip.status] || ip.status }}
              </NTag>
            </div>

            <!-- 第二行: IP / 探活 / 安装 / 带宽 / 流量 -->
            <div class="lc-r2">
              <span class="lc-field lc-ip">
                <NIcon :size="14"><Globe2 /></NIcon>
                <span class="font-mono">{{ ip.ipAddress }}</span>
              </span>
              <span
                class="lc-pill"
                :class="`lc-pill--${AGENT_ONLINE_TAG_TYPE[ip.onlineState || 'NEVER']}`"
                :title="ip.lastHeartbeatAt ? `上次心跳 ${ip.lastHeartbeatAt}` : '从未上报心跳'"
              >
                <span class="lc-dot"></span>{{ ONLINE_SHORT[ip.onlineState || 'NEVER'] }}<span v-if="ip.elapsedSec != null" class="lc-hb">{{ fmtElapsed(ip.elapsedSec) }}</span>
              </span>
              <span class="lc-pill" :class="`lc-pill--${agentOf(ip).tone}`" title="管理 agent 安装情况">
                <NIcon :size="11"><Cpu /></NIcon>{{ agentOf(ip).label }}
              </span>
              <span class="lc-pill" :class="`lc-pill--${socksOf(ip).tone}`" title="SOCKS5 (dante) 安装情况">
                <NIcon :size="11"><ServerCog /></NIcon>{{ socksOf(ip).label }}
              </span>
              <span class="lc-field">
                <NIcon :size="13"><Gauge /></NIcon>
                {{ ip.bandwidthLimitMbps ? `${ip.bandwidthLimitMbps} Mbps` : '带宽不限' }}
              </span>
              <span class="lc-field">
                <NIcon :size="13"><Database /></NIcon>
                {{ ip.monthlyTrafficGb ? `${ip.monthlyTrafficGb} GB/月` : '流量不限' }}
              </span>
            </div>
          </div>

          <div class="lc-actions" @click.stop>
            <NTooltip v-if="canTest(ip)" placement="top">
              <template #trigger>
                <NButton size="small" quaternary type="warning" circle @click="openTest(ip)">
                  <template #icon><NIcon><Zap /></NIcon></template>
                </NButton>
              </template>
              <div class="text-xs">测试 (拨号自检 SOCKS5)</div>
            </NTooltip>
            <!-- 装机: 仅自部署的 INSTALLING / READY (LIVE 不再提供重装) -->
            <NTooltip
              v-if="ip.provisionMode === 1 && (ip.lifecycleState === 'INSTALLING' || ip.lifecycleState === 'READY')"
              placement="top"
            >
              <template #trigger>
                <NButton size="small" quaternary type="primary" circle @click="openDeploy(ip)">
                  <template #icon><NIcon><Rocket /></NIcon></template>
                </NButton>
              </template>
              <div class="text-xs">装机 (部署 dante)</div>
            </NTooltip>
            <!-- 停用: LIVE 才有; 占用中禁用并提示原因 (后端同样拦截) -->
            <NTooltip v-if="ip.lifecycleState === 'LIVE'" placement="top">
              <template #trigger>
                <NButton
                  size="small"
                  quaternary
                  type="warning"
                  circle
                  :disabled="landingInUse(ip)"
                  @click="onSuspend(ip)"
                >
                  <template #icon><NIcon><PauseCircle /></NIcon></template>
                </NButton>
              </template>
              <div class="text-xs">
                {{ landingInUse(ip) ? '占用中不可停用 (请先释放占用它的订阅)' : '停用 (移出分配池, 不再分配给新订阅)' }}
              </div>
            </NTooltip>
            <NTooltip v-else-if="ip.lifecycleState === 'RETIRED'" placement="top">
              <template #trigger>
                <NButton size="small" quaternary type="success" circle @click="onActivate(ip)">
                  <template #icon><NIcon><PlayCircle /></NIcon></template>
                </NButton>
              </template>
              <div class="text-xs">启用 (恢复分配)</div>
            </NTooltip>
            <!-- 删除: 仅未占用 (OCCUPIED / RESERVED 在用, 禁止删除) -->
            <NTooltip v-if="ip.status !== 'OCCUPIED' && ip.status !== 'RESERVED'" placement="top">
              <template #trigger>
                <NButton size="small" quaternary type="error" circle @click="onDelete(ip)">
                  <template #icon><NIcon><Trash2 /></NIcon></template>
                </NButton>
              </template>
              <div class="text-xs">删除落地机 (不可恢复)</div>
            </NTooltip>
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
    <ServerLandingCreateChoiceDialog v-model="createChoiceOpen" @choose-self-deploy="openCreate" />

    <ServerCreateDialog
      v-model="createOpen"
      server-type="landing"
      @created="onCreatedAfterChoice"
    />

    <ServerLandingDeployDialog v-model="deployOpen" :server-id="deployIpId" @installed="onDeployInstalled" />

    <ServerLandingTestDialog v-model="testOpen" :ip="testTarget" />
    </div>
  </div>
</template>

<style scoped>
/* ===== 左树 + 右内容布局 ===== */
.landing-layout {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}
.landing-main {
  flex: 1;
  min-width: 0;
}

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

/* ===== 落地机卡片 (两行: 区域+类型 / IP+带宽; 整卡 click → 详情页) ===== */
.lc-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 14px;
  background: var(--n-card-color, #fff);
  border: 1px solid var(--n-border-color, #efeff5);
  border-radius: 10px;
  padding: 13px 16px 13px 20px;
  cursor: pointer;
  transition: background 0.12s ease, border-color 0.12s ease, box-shadow 0.12s ease, transform 0.12s ease;
  overflow: hidden;
}
.lc-card::before {
  /* 左侧色条, 按 lifecycle 区分 */
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 3px;
  height: 100%;
  background: currentColor;
  opacity: 0.9;
}
.lc-card:hover {
  background: color-mix(in srgb, currentColor 4%, var(--n-card-color, #fff));
  border-color: color-mix(in srgb, currentColor 35%, var(--n-border-color, #efeff5));
  box-shadow: 0 3px 12px rgba(0, 0, 0, 0.06);
  transform: translateY(-1px);
}
.lc-card:focus-visible {
  outline: 2px solid currentColor;
  outline-offset: 1px;
}

/* lifecycle 配色 (= 左侧色条颜色; 跟 NTag 颜色对齐) */
.lc-card--installing { color: #2080f0; }
.lc-card--ready      { color: #f0a020; }
.lc-card--live       { color: #18a058; }
.lc-card--retired    { color: #999; }

.lc-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 9px;
}

/* 第一行 */
.lc-r1 {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.lc-country {
  font-size: 14.5px;
  font-weight: 600;
  color: var(--n-text-color-1, #222);
  white-space: nowrap;
  flex-shrink: 0;
}
.lc-iptype {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--n-text-color-2, #666);
  background: rgba(127, 127, 127, 0.09);
  border-radius: 6px;
  padding: 2px 8px;
  max-width: 15rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.lc-iptype :deep(svg) { opacity: 0.65; flex-shrink: 0; }
.lc-third { background: rgba(160, 160, 160, 0.18); color: #555; flex-shrink: 0; }
.lc-expiry {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  white-space: nowrap;
  color: var(--n-text-color-3, #9ca3af);
  font-variant-numeric: tabular-nums;
}
.lc-expiry :deep(svg) { opacity: 0.7; }
.lc-expiry--warning { color: #d97706; }
.lc-expiry--warning :deep(svg) { opacity: 1; }
.lc-expiry--error { color: #dc2626; font-weight: 600; }
.lc-expiry--error :deep(svg) { opacity: 1; }
html[data-theme='dark'] .lc-expiry--warning { color: #fbbf24; }
html[data-theme='dark'] .lc-expiry--error { color: #f87171; }

/* 第二行 */
.lc-r2 {
  display: flex;
  align-items: center;
  gap: 18px;
  flex-wrap: wrap;
  row-gap: 4px;
}
.lc-field {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12.5px;
  color: var(--n-text-color-2, #555);
}
.lc-field :deep(svg) { color: #a1a1aa; flex-shrink: 0; }
.lc-ip {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--n-text-color-1, #222);
}
.lc-ip :deep(svg) { color: currentColor; }
.lc-muted { color: #a1a1aa; }

/* 探活 / 安装 状态 pill (参照线路机卡片) */
.lc-pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px 2px 7px;
  font-size: 11.5px;
  font-weight: 500;
  line-height: 1;
  border-radius: 10px;
  border: 1px solid transparent;
  white-space: nowrap;
}
.lc-pill :deep(svg) { flex-shrink: 0; }
.lc-dot { width: 6px; height: 6px; border-radius: 50%; background: currentColor; flex-shrink: 0; }
.lc-hb { margin-left: 4px; opacity: 0.75; font-variant-numeric: tabular-nums; }
.lc-pill--success { color: #16a34a; background: rgba(22, 163, 74, 0.1); border-color: rgba(22, 163, 74, 0.22); }
.lc-pill--warning { color: #ca8a04; background: rgba(202, 138, 4, 0.1); border-color: rgba(202, 138, 4, 0.22); }
.lc-pill--error   { color: #dc2626; background: rgba(220, 38, 38, 0.1); border-color: rgba(220, 38, 38, 0.22); }
.lc-pill--default { color: #71717a; background: rgba(161, 161, 170, 0.1); border-color: rgba(161, 161, 170, 0.22); }
html[data-theme='dark'] .lc-pill--success { color: #4ade80; background: rgba(74, 222, 128, 0.12); border-color: rgba(74, 222, 128, 0.25); }
html[data-theme='dark'] .lc-pill--warning { color: #facc15; background: rgba(250, 204, 21, 0.12); border-color: rgba(250, 204, 21, 0.25); }
html[data-theme='dark'] .lc-pill--error   { color: #f87171; background: rgba(248, 113, 113, 0.12); border-color: rgba(248, 113, 113, 0.25); }
html[data-theme='dark'] .lc-pill--default { color: #a1a1aa; background: rgba(161, 161, 170, 0.14); border-color: rgba(161, 161, 170, 0.22); }

.lc-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
}
</style>
