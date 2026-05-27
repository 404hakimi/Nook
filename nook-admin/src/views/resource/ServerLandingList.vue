<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Globe2,
  PauseCircle,
  PlayCircle,
  Plus,
  RefreshCcw,
  Rocket,
  Search,
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

const regionOptions = computed(() => [
  { label: '全部', value: '' },
  ...regions.value.map((r) => ({ label: `${r.flagEmoji || ''} ${r.displayName}`, value: r.code }))
])

function regionRecord(code?: string): SystemRegion | null {
  if (!code) return null
  return regions.value.find((x) => x.code === code) ?? null
}

const query = reactive<Required<Pick<ServerLandingQuery, 'pageNo' | 'pageSize'>> & ServerLandingQuery>({
  pageNo: 1,
  pageSize: 12,
  keyword: '',
  lifecycleState: undefined,
  status: undefined,
  region: '',
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

// ===== lifecycle 流转 (admin 只暴露 2 项: 停用 / 启用; INSTALLING/READY 是装机内部态) =====
async function onSuspend(ip: ServerLanding) {
  if (ip.lifecycleState !== 'LIVE') return
  const ok = await confirm({
    title: '停用落地机',
    message: `停用落地机 ${ip.ipAddress}? 停用后系统不再将此落地机分配给新订阅, 当前正在使用的会员不受影响.`,
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

      <div v-else class="flex flex-col gap-2">
        <div
          v-for="ip in list"
          :key="ip.id"
          class="ip-row"
          :class="[`ip-row--lc-${ip.lifecycleState.toLowerCase()}`]"
          role="button"
          tabindex="0"
          title="点击查看详情"
          @click="openDetail(ip)"
          @keyup.enter="openDetail(ip)"
        >
          <NIcon :size="16" class="ip-row__icon"><Globe2 /></NIcon>
          <span class="ip-row__addr font-mono">{{ ip.ipAddress }}</span>
          <NTag
            v-if="ip.provisionMode === 2"
            size="tiny"
            round
            :bordered="false"
            class="ip-row__third"
          >第三方</NTag>

          <div class="ip-row__region">
            <RegionFlag :code="regionRecord(ip.region)?.countryCode" :fallback="regionRecord(ip.region)?.flagEmoji" :size="14" />
            <span class="ml-1 text-xs text-zinc-600 truncate">{{ regionRecord(ip.region)?.displayName || ip.region || '-' }}</span>
          </div>
          <span class="ip-row__type text-xs text-zinc-500 truncate">{{ ipTypeName(ip.ipTypeId) }}</span>

          <span v-if="ip.socks5Port" class="ip-row__socks font-mono text-xs text-zinc-500">
            :{{ ip.socks5Port }}<span v-if="ip.socks5Username"> / {{ ip.socks5Username }}</span>
          </span>
          <span v-else class="ip-row__socks text-xs text-zinc-400">未配置 SOCKS5</span>

          <span class="ip-row__bw text-xs text-zinc-500 font-mono">
            {{ ip.bandwidthLimitMbps ? `${ip.bandwidthLimitMbps} Mbps` : '不限' }}
            <span class="text-zinc-400 mx-1">·</span>
            {{ ip.monthlyTrafficGb ? `${ip.monthlyTrafficGb}G/月` : '不限' }}
          </span>

          <div class="ip-row__badges">
            <NTag size="small" :type="SERVER_LANDING_LIFECYCLE_TAG_TYPE[ip.lifecycleState] || 'default'">
              {{ SERVER_LANDING_LIFECYCLE_LABELS[ip.lifecycleState] || ip.lifecycleState }}
            </NTag>
            <NTag size="small" :type="statusTagType(ip.status)">
              {{ SERVER_LANDING_STATUS_LABELS[ip.status] || ip.status }}
            </NTag>
          </div>

          <div class="ip-row__actions" @click.stop>
            <NTooltip v-if="canTest(ip)" placement="top">
              <template #trigger>
                <NButton size="tiny" quaternary type="warning" circle @click="openTest(ip)">
                  <template #icon><NIcon><Zap /></NIcon></template>
                </NButton>
              </template>
              <div class="text-xs">测试 (拨号自检 SOCKS5)</div>
            </NTooltip>
            <NTooltip
              v-if="ip.provisionMode === 1 && (ip.lifecycleState === 'INSTALLING' || ip.lifecycleState === 'READY' || ip.lifecycleState === 'LIVE')"
              placement="top"
            >
              <template #trigger>
                <NButton
                  size="tiny"
                  quaternary
                  :type="ip.lifecycleState === 'LIVE' ? 'info' : 'primary'"
                  circle
                  @click="openDeploy(ip)"
                >
                  <template #icon><NIcon><Rocket /></NIcon></template>
                </NButton>
              </template>
              <div class="text-xs">{{ ip.lifecycleState === 'LIVE' ? '重装 dante' : '装机' }}</div>
            </NTooltip>
            <NTooltip v-if="ip.lifecycleState === 'LIVE'" placement="top">
              <template #trigger>
                <NButton size="tiny" quaternary type="warning" circle @click="onSuspend(ip)">
                  <template #icon><NIcon><PauseCircle /></NIcon></template>
                </NButton>
              </template>
              <div class="text-xs">停用 (停止分配; 现有占用不受影响)</div>
            </NTooltip>
            <NTooltip v-else-if="ip.lifecycleState === 'RETIRED'" placement="top">
              <template #trigger>
                <NButton size="tiny" quaternary type="success" circle @click="onActivate(ip)">
                  <template #icon><NIcon><PlayCircle /></NIcon></template>
                </NButton>
              </template>
              <div class="text-xs">启用 (恢复分配)</div>
            </NTooltip>
            <NTooltip placement="top">
              <template #trigger>
                <NButton size="tiny" quaternary type="error" circle @click="onDelete(ip)">
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

/* ===== 落地机紧凑列表项 (整行 click → 详情页) ===== */
.ip-row {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  background: var(--n-card-color, #fff);
  border: 1px solid var(--n-border-color, #efeff5);
  border-radius: 8px;
  padding: 8px 14px 8px 18px;
  cursor: pointer;
  transition: background 0.12s ease, border-color 0.12s ease, box-shadow 0.12s ease;
  overflow: hidden;
}
.ip-row::before {
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
.ip-row:hover {
  background: color-mix(in srgb, currentColor 4%, var(--n-card-color, #fff));
  border-color: color-mix(in srgb, currentColor 35%, var(--n-border-color, #efeff5));
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}
.ip-row:focus-visible {
  outline: 2px solid currentColor;
  outline-offset: 1px;
}

/* lifecycle 配色 (= 左侧色条颜色; 跟 NTag 颜色对齐) */
.ip-row--lc-installing { color: #2080f0; }
.ip-row--lc-ready      { color: #f0a020; }
.ip-row--lc-live       { color: #18a058; }
.ip-row--lc-retired    { color: #999; }

.ip-row__icon { color: currentColor; flex-shrink: 0; }
.ip-row__addr {
  font-size: 14px;
  font-weight: 600;
  color: var(--n-text-color-1, #222);
  flex-shrink: 0;
}
.ip-row__third { background: rgba(160, 160, 160, 0.18); color: #555; flex-shrink: 0; }
.ip-row__region {
  display: flex;
  align-items: center;
  min-width: 0;
  max-width: 12rem;
  flex-shrink: 1;
}
.ip-row__type { max-width: 8rem; flex-shrink: 1; }
.ip-row__socks { flex-shrink: 0; }
.ip-row__bw { flex-shrink: 0; }
.ip-row__badges {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: auto;
  flex-shrink: 0;
}
.ip-row__actions {
  display: flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
}
</style>
