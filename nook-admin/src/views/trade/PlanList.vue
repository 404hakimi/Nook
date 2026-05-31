<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  NButton,
  NEmpty,
  NIcon,
  NInput,
  NPagination,
  NProgress,
  NSelect,
  NSpin
} from 'naive-ui'
import {
  ChevronDown,
  Database,
  Gauge,
  Globe,
  Network,
  Package,
  Plus,
  RefreshCcw,
  RotateCcw,
  Search,
  Users,
  Zap
} from 'lucide-vue-next'
import { storeToRefs } from 'pinia'
import { useRegionStore } from '@/stores/region'
import { useIpTypeStore } from '@/stores/ipType'
import { IP_TYPE_CODE_LABELS } from '@/api/system/ip-type'
import { pageTradePlan, type TradePlan } from '@/api/trade/plan'
import { compactCount } from '@/utils/format'
import RegionFlag from '@/components/RegionFlag.vue'
import PlanEditDialog from './PlanEditDialog.vue'

const router = useRouter()
const regionStore = useRegionStore()
const ipTypeStore = useIpTypeStore()
const { map: regionMap, list: regionList } = storeToRefs(regionStore)
const { map: ipTypeMap } = storeToRefs(ipTypeStore)

const list = ref<TradePlan[]>([])
const total = ref(0)
const loading = ref(false)
const pageNo = ref(1)
const pageSize = ref(9)
const keyword = ref('')
const filterEnabled = ref<number | undefined>(undefined)

const createOpen = ref(false)

// ===== 左侧区域 (国家 → 城市): 默认全展开, 点国家展开+筛该国, 点城市筛单城 =====
interface RegionCountry {
  code: string
  name: string
  flag?: string
  cities: { code: string; label: string }[]
}

const selectedRegionCodes = ref<string[]>([])
const activeRegionKey = ref('all') // 'all' | country:XX | region:XX, 仅用于高亮
const collapsedCountries = ref<Record<string, boolean>>({}) // 默认空 = 全部展开

const regionGroups = computed<RegionCountry[]>(() => {
  const groups = new Map<string, RegionCountry>()
  for (const r of regionList.value) {
    if (!groups.has(r.countryCode)) {
      groups.set(r.countryCode, { code: r.countryCode, name: r.countryName, flag: r.flagEmoji, cities: [] })
    }
    groups.get(r.countryCode)!.cities.push({ code: r.code, label: r.city || r.displayName || r.code })
  }
  return [...groups.values()]
})

function applyRegion(key: string, codes: string[]) {
  activeRegionKey.value = key
  selectedRegionCodes.value = codes
  pageNo.value = 1
  load()
}
function selectAllRegions() {
  applyRegion('all', [])
}
function onCountryClick(c: RegionCountry) {
  collapsedCountries.value[c.code] = false // 点国家即展开
  applyRegion(`country:${c.code}`, c.cities.map((x) => x.code))
}
function onCityClick(code: string) {
  applyRegion(`region:${code}`, [code])
}
function toggleCountry(code: string) {
  collapsedCountries.value[code] = !collapsedCountries.value[code]
}

const enabledOptions = [
  { label: '全部状态', value: undefined as number | undefined },
  { label: '上架', value: 1 },
  { label: '下架', value: 0 }
]

async function load() {
  loading.value = true
  try {
    const page = await pageTradePlan({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      keyword: keyword.value.trim() || undefined,
      enabled: filterEnabled.value,
      regionCodes: selectedRegionCodes.value.length ? selectedRegionCodes.value : undefined
    })
    list.value = page.records
    total.value = page.total
  } catch {
    /* request 拦截器 toast */
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await Promise.all([regionStore.ensureLoaded(), ipTypeStore.ensureLoaded()])
  load()
})

function onSearch() {
  pageNo.value = 1
  load()
}
function doReset() {
  keyword.value = ''
  filterEnabled.value = undefined
  activeRegionKey.value = 'all'
  selectedRegionCodes.value = []
  pageNo.value = 1
  load()
}
function onPageChange(p: number) {
  pageNo.value = p
  load()
}
function onPageSizeChange(s: number) {
  pageSize.value = s
  pageNo.value = 1
  load()
}

// ===== 头部摘要 (当前页) =====
const stats = computed(() => {
  let on = 0
  let off = 0
  for (const p of list.value) {
    if (p.enabled === 1) on++
    else off++
  }
  return { on, off }
})

function ipTypeLabel(p: TradePlan): string {
  if (!p.ipTypeId) return '—'
  const t = ipTypeMap.value[p.ipTypeId]
  if (!t) return p.ipTypeId
  return IP_TYPE_CODE_LABELS[t.code] || t.name || t.code
}
function regionLabel(p: TradePlan): string {
  if (!p.regionCode) return '—'
  return regionMap.value[p.regionCode]?.displayName ?? p.regionCode
}

function capacityPercent(p: TradePlan): number {
  const totalCap = p.capacityTotal ?? 0
  if (totalCap <= 0) return 0
  return Math.round(((p.capacityOccupied ?? 0) / totalCap) * 100)
}
function capacityStatus(p: TradePlan): 'success' | 'warning' | 'error' | 'default' {
  const totalCap = p.capacityTotal ?? 0
  if (totalCap <= 0) return 'default'
  if ((p.capacityAvailable ?? 0) <= 0) return 'error'
  if (capacityPercent(p) >= 80) return 'warning'
  return 'success'
}

function openDetail(id: string) {
  router.push(`/trade/plans/${id}`)
}
function onCreated() {
  pageNo.value = 1
  load()
}
</script>

<template>
  <div class="plan-layout">
    <!-- ============ 左侧区域 (国家 → 城市) ============ -->
    <aside class="region-aside">
      <div class="region-head">
        <NIcon :size="14"><Globe /></NIcon>
        <span>区域</span>
      </div>
      <div class="region-list">
        <div class="r-row r-all" :class="{ 'r-active': activeRegionKey === 'all' }" @click="selectAllRegions">
          全部区域
        </div>
        <div v-for="c in regionGroups" :key="c.code" class="r-group">
          <div
            class="r-row r-country"
            :class="{ 'r-active': activeRegionKey === `country:${c.code}` }"
            @click="onCountryClick(c)"
          >
            <span
              class="r-chevron"
              :class="{ 'r-collapsed': collapsedCountries[c.code] }"
              @click.stop="toggleCountry(c.code)"
            >
              <NIcon :size="13"><ChevronDown /></NIcon>
            </span>
            <span class="r-flag">{{ c.flag || '🏳️' }}</span>
            <span class="r-name">{{ c.name }}</span>
            <span class="r-count">{{ c.cities.length }}</span>
          </div>
          <div v-show="!collapsedCountries[c.code]" class="r-cities">
            <div
              v-for="city in c.cities"
              :key="city.code"
              class="r-row r-city"
              :class="{ 'r-active': activeRegionKey === `region:${city.code}` }"
              @click="onCityClick(city.code)"
            >
              {{ city.label }}
            </div>
          </div>
        </div>
      </div>
    </aside>

    <div class="overview-wrap space-y-4">
    <!-- ============ 头部 ============ -->
    <div class="page-header">
      <div class="flex items-center gap-3 flex-wrap mb-3">
        <div>
          <div class="text-lg font-semibold">套餐</div>
          <div class="text-xs text-zinc-500 mt-0.5">点卡片进套餐详情, 查看订阅该套餐的会员 · 容量来自匹配落地机池</div>
        </div>
        <div class="flex-1"></div>
        <NButton type="primary" size="small" @click="createOpen = true">
          <template #icon><NIcon><Plus :size="14" /></NIcon></template>
          新建套餐
        </NButton>
        <NButton quaternary size="small" :loading="loading" @click="load">
          <template #icon><NIcon><RefreshCcw :size="14" /></NIcon></template>
          刷新
        </NButton>
      </div>

      <!-- 摘要条 (当前页) -->
      <div class="stats-strip">
        <div class="stat-item">
          <span class="stat-num">{{ total }}</span>
          <span class="stat-label">套餐总数</span>
        </div>
        <div class="stat-divider"></div>
        <div class="stat-item">
          <span class="stat-dot" style="background:#16a34a"></span>
          <span class="stat-num text-green-600">{{ stats.on }}</span>
          <span class="stat-label">上架</span>
        </div>
        <div class="stat-item">
          <span class="stat-dot" style="background:#9ca3af"></span>
          <span class="stat-num text-zinc-500">{{ stats.off }}</span>
          <span class="stat-label">下架</span>
        </div>
        <span class="text-xs text-zinc-400 ml-1">(当前页 {{ list.length }} 条)</span>
      </div>

      <!-- 筛选 -->
      <div class="filter-form">
        <div class="filter-item">
          <label class="filter-label">关键字</label>
          <NInput
            v-model:value="keyword"
            placeholder="套餐码 / 名称"
            clearable
            size="small"
            class="w-52"
            @keyup.enter="onSearch"
          />
        </div>
        <div class="filter-item">
          <label class="filter-label">状态</label>
          <NSelect v-model:value="filterEnabled" :options="enabledOptions" size="small" class="w-32" />
        </div>
        <div class="filter-actions">
          <NButton type="primary" size="small" @click="onSearch">
            <template #icon><NIcon><Search :size="14" /></NIcon></template>
            搜索
          </NButton>
          <NButton size="small" @click="doReset">
            <template #icon><NIcon><RotateCcw :size="14" /></NIcon></template>
            重置
          </NButton>
        </div>
      </div>
    </div>

    <!-- ============ 卡片网格 ============ -->
    <NSpin :show="loading">
      <NEmpty v-if="!loading && list.length === 0" description="无匹配套餐" />
      <div v-else class="plan-grid">
        <div
          v-for="p in list"
          :key="p.id"
          class="plan-card"
          @click="openDetail(p.id)"
        >
          <!-- 状态徽标 (右上角) -->
          <span class="pc-status" :class="p.enabled === 1 ? 'pc-status-on' : 'pc-status-off'">
            <span class="pc-status-dot"></span>{{ p.enabled === 1 ? '上架' : '下架' }}
          </span>

          <!-- 套餐名 独占一行 -->
          <div class="pc-name" :title="p.name">
            <RegionFlag
              v-if="p.regionCode && regionMap[p.regionCode]"
              :code="regionMap[p.regionCode].countryCode"
              :fallback="regionMap[p.regionCode].flagEmoji"
              squared
              :size="18"
              :title="regionLabel(p)"
            />
            <NIcon v-else class="pc-name-icon"><Package :size="16" /></NIcon>
            <span class="pc-name-text">{{ p.name }}</span>
          </div>
          <div class="pc-code truncate" :title="p.code">{{ p.code }}</div>

          <!-- 价格 -->
          <div class="pc-price">
            <span class="pc-price-cur">¥</span>{{ p.price }}
            <span class="pc-price-per">/ {{ p.periodDays }}天</span>
          </div>

          <div class="pc-divider"></div>

          <!-- 规格特性 (每项独占一行) -->
          <div class="pc-specs">
            <div class="pc-spec">
              <span class="pc-spec-k"><Globe :size="13" />区域</span>
              <span class="pc-spec-v">{{ regionLabel(p) }}</span>
            </div>
            <div class="pc-spec">
              <span class="pc-spec-k"><Network :size="13" />IP 类型</span>
              <span class="pc-spec-v">{{ ipTypeLabel(p) }}</span>
            </div>
            <div class="pc-spec">
              <span class="pc-spec-k"><Zap :size="13" />带宽</span>
              <span class="pc-spec-v">{{ p.bandwidthMbps }} Mbps</span>
            </div>
            <div class="pc-spec">
              <span class="pc-spec-k"><Database :size="13" />月流量</span>
              <span class="pc-spec-v">{{ p.trafficGb }} GB</span>
            </div>
          </div>

          <div class="pc-divider"></div>

          <!-- 底部: 订阅人数 + 落地机容量 -->
          <div class="pc-foot">
            <div class="pc-foot-row">
              <span class="pc-foot-k"><Users :size="13" />订阅会员</span>
              <span class="pc-foot-v">{{ compactCount(p.activeSubCount) }} 人</span>
            </div>
            <div class="pc-foot-row">
              <span class="pc-foot-k"><Gauge :size="13" />落地机容量</span>
              <span class="pc-foot-v" :class="{ 'pc-danger': (p.capacityAvailable ?? 0) <= 0 }">
                剩 {{ compactCount(p.capacityAvailable) }} / 总 {{ compactCount(p.capacityTotal) }}
              </span>
            </div>
            <NProgress
              :percentage="capacityPercent(p)"
              :height="5"
              :status="capacityStatus(p)"
              :show-indicator="false"
              class="mt-1.5"
            />
          </div>
        </div>
      </div>

      <div v-if="total > 0" class="pagination-bar">
        <NPagination
          :page="pageNo"
          :page-size="pageSize"
          :item-count="total"
          :page-sizes="[9, 18, 36]"
          show-size-picker
          size="small"
          @update:page="onPageChange"
          @update:page-size="onPageSizeChange"
        />
      </div>
    </NSpin>

      <PlanEditDialog v-model="createOpen" :plan="null" @saved="onCreated" />
    </div>
  </div>
</template>

<style scoped>
.plan-layout {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}
.overview-wrap {
  flex: 1;
  min-width: 0;
}

/* 左侧区域面板 */
.region-aside {
  width: 216px;
  flex-shrink: 0;
  align-self: stretch; /* 与右侧内容等高, 顶部对齐 */
  overflow-y: auto;
  scrollbar-gutter: stable; /* 滚动条出现不挤压内容, 避免展开时横向抖动 */
  padding: 8px;
  background: var(--card-color, #fff);
  border: 1px solid rgba(127, 127, 127, 0.12);
  border-radius: 10px;
}
html[data-theme='dark'] .region-aside {
  background: #1f1f23;
}
.region-head {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--nook-fg-muted);
  padding: 4px 8px 8px;
}
.region-head :deep(svg) {
  color: var(--nook-fg-faint);
}
.region-list {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

/* 行通用 */
.r-row {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 6px 8px;
  border-radius: 7px;
  cursor: pointer;
  font-size: 13px;
  line-height: 1.2;
  color: var(--nook-fg);
  user-select: none;
  transition: background 0.12s ease;
}
.r-row:hover {
  background: rgba(127, 127, 127, 0.08);
}
.r-active,
.r-active:hover {
  background: rgba(99, 102, 241, 0.12);
  color: var(--nook-accent);
  font-weight: 600;
}
.r-all {
  font-weight: 500;
}
.r-country {
  font-weight: 500;
}
.r-chevron {
  display: inline-flex;
  color: var(--nook-fg-faint);
  border-radius: 4px;
  transition: transform 0.15s ease;
}
.r-chevron:hover {
  background: rgba(127, 127, 127, 0.15);
}
.r-chevron.r-collapsed {
  transform: rotate(-90deg);
}
.r-flag {
  font-size: 14px;
  line-height: 1;
}
.r-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.r-count {
  font-size: 11px;
  color: var(--nook-fg-faint);
  background: rgba(127, 127, 127, 0.1);
  border-radius: 999px;
  padding: 0 6px;
  min-width: 18px;
  text-align: center;
}
.r-active .r-count {
  background: rgba(99, 102, 241, 0.18);
  color: var(--nook-accent);
}
.r-cities {
  display: flex;
  flex-direction: column;
  gap: 1px;
}
.r-city {
  padding-left: 30px;
  font-size: 12.5px;
  color: var(--nook-fg-muted);
}
.r-city.r-active {
  color: var(--nook-accent);
}
.page-header {
  background: linear-gradient(180deg, rgba(99, 102, 241, 0.03), transparent);
  padding: 14px 16px;
  border-radius: 6px;
  border: 1px solid rgba(127, 127, 127, 0.1);
}

/* 摘要条 */
.stats-strip {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 10px 14px;
  background: rgba(127, 127, 127, 0.05);
  border-radius: 6px;
  border: 1px solid rgba(127, 127, 127, 0.08);
}
.stat-item {
  display: flex;
  align-items: baseline;
  gap: 5px;
}
.stat-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.stat-num {
  font-size: 18px;
  font-weight: 600;
  font-family: 'JetBrains Mono', monospace;
}
.stat-label {
  font-size: 12px;
  color: #71717a;
}
.stat-divider {
  width: 1px;
  height: 18px;
  background: rgba(127, 127, 127, 0.2);
}

/* 筛选 */
.filter-form {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 10px 14px;
  margin-top: 14px;
  padding: 10px 12px;
  background: rgba(127, 127, 127, 0.03);
  border: 1px dashed rgba(127, 127, 127, 0.15);
  border-radius: 6px;
}
.filter-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.filter-label {
  font-size: 11px;
  color: #71717a;
  font-weight: 500;
}
.filter-actions {
  display: flex;
  gap: 6px;
  margin-left: auto;
}

/* 卡片网格: 竖向 (portrait) 卡片 */
/* 卡片网格: 固定宽 + 靠左排 (从左铺, 自适应换行) */
.plan-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, 290px);
  justify-content: start;
  gap: 16px;
}
.plan-card {
  position: relative;
  display: flex;
  flex-direction: column;
  padding: 18px 18px 16px;
  background: var(--card-color, #fff);
  border: 1px solid rgba(127, 127, 127, 0.15);
  border-radius: 12px;
  cursor: pointer;
  transition: transform 0.15s ease, box-shadow 0.15s ease, border-color 0.15s ease;
}
.plan-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 22px -8px rgba(0, 0, 0, 0.16);
  border-color: rgba(99, 102, 241, 0.45);
}
html[data-theme='dark'] .plan-card {
  background: #1f1f23;
}

/* 状态徽标 */
.pc-status {
  position: absolute;
  top: 12px;
  right: 12px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  font-size: 11px;
  font-weight: 500;
  line-height: 1.5;
  border-radius: 999px;
}
.pc-status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}
.pc-status-on {
  color: #16a34a;
  background: rgba(22, 163, 74, 0.1);
}
.pc-status-off {
  color: #71717a;
  background: rgba(161, 161, 170, 0.12);
}
html[data-theme='dark'] .pc-status-on {
  color: #4ade80;
  background: rgba(74, 222, 128, 0.12);
}

/* 套餐名 (独占一行) */
.pc-name {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  padding-right: 60px;
  min-width: 0;
}
.pc-name-icon {
  color: var(--nook-accent);
  margin-top: 1px;
}
.pc-name-text {
  font-size: 15.5px;
  font-weight: 600;
  line-height: 1.3;
  color: var(--nook-fg);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.pc-code {
  font-size: 11px;
  font-family: var(--nook-mono);
  color: var(--nook-fg-faint);
  margin-top: 3px;
}

/* 价格 */
.pc-price {
  display: flex;
  align-items: baseline;
  gap: 2px;
  margin-top: 12px;
  font-size: 28px;
  font-weight: 700;
  line-height: 1.1;
  color: var(--nook-fg);
}
.pc-price-cur {
  font-size: 15px;
  font-weight: 600;
  color: var(--nook-fg-muted);
}
.pc-price-per {
  font-size: 12px;
  font-weight: 400;
  color: var(--nook-fg-faint);
  margin-left: 4px;
}

.pc-divider {
  height: 1px;
  background: rgba(127, 127, 127, 0.12);
  margin: 12px 0;
}

/* 规格特性列表 */
.pc-specs {
  display: flex;
  flex-direction: column;
  gap: 11px;
}
.pc-spec {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.pc-spec-k {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--nook-fg-muted);
}
.pc-spec-k :deep(svg) {
  color: var(--nook-fg-faint);
}
.pc-spec-v {
  font-size: 14px;
  font-weight: 500;
  color: var(--nook-fg);
}

/* 底部 */
.pc-foot {
  margin-top: auto;
}
.pc-foot-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}
.pc-foot-k {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--nook-fg-muted);
}
.pc-foot-k :deep(svg) {
  color: var(--nook-fg-faint);
}
.pc-foot-v {
  font-size: 13px;
  font-weight: 600;
  color: var(--nook-fg);
  white-space: nowrap;
}
.pc-danger {
  color: #dc2626;
}
html[data-theme='dark'] .pc-danger {
  color: #f87171;
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
