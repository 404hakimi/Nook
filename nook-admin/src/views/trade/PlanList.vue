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
  NSpin,
  NTree,
  type TreeOption
} from 'naive-ui'
import {
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
import type { SystemRegion } from '@/api/system/region'
import { pageTradePlan, type TradePlan } from '@/api/trade/plan'
import { countActiveSubByPlan } from '@/api/trade/subscription'
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

/** planId → 活跃订阅数 (一次拉全量, 卡片合并展示). */
const subCountMap = ref<Record<string, number>>({})

const createOpen = ref(false)

// ===== 左侧区域树 (国家 → 城市) =====
const selectedRegionKeys = ref<string[]>(['all'])
const selectedRegionCodes = ref<string[]>([])

const regionTree = computed<TreeOption[]>(() => {
  const groups = new Map<string, { name: string; flag?: string; cities: SystemRegion[] }>()
  for (const r of regionList.value) {
    if (!groups.has(r.countryCode)) {
      groups.set(r.countryCode, { name: r.countryName, flag: r.flagEmoji, cities: [] })
    }
    groups.get(r.countryCode)!.cities.push(r)
  }
  const nodes: TreeOption[] = [{ key: 'all', label: '全部区域' }]
  for (const [cc, g] of groups) {
    nodes.push({
      key: `country:${cc}`,
      label: (g.flag ? g.flag + ' ' : '') + g.name,
      children: g.cities.map((c) => ({ key: `region:${c.code}`, label: c.city || c.displayName || c.code }))
    })
  }
  return nodes
})

/** 树选中: all=清空; country:xx=该国全部城市码; region:xx=单个城市码. */
function onRegionSelect(keys: Array<string | number>) {
  const arr = keys.map(String)
  selectedRegionKeys.value = arr
  const k = arr[0]
  if (!k || k === 'all') {
    selectedRegionCodes.value = []
  } else if (k.startsWith('country:')) {
    const cc = k.slice('country:'.length)
    selectedRegionCodes.value = regionList.value.filter((r) => r.countryCode === cc).map((r) => r.code)
  } else if (k.startsWith('region:')) {
    selectedRegionCodes.value = [k.slice('region:'.length)]
  }
  pageNo.value = 1
  load()
}

const enabledOptions = [
  { label: '全部状态', value: undefined as number | undefined },
  { label: '上架', value: 1 },
  { label: '下架', value: 0 }
]

async function load() {
  loading.value = true
  try {
    const [page, counts] = await Promise.all([
      pageTradePlan({
        pageNo: pageNo.value,
        pageSize: pageSize.value,
        keyword: keyword.value.trim() || undefined,
        enabled: filterEnabled.value,
        regionCodes: selectedRegionCodes.value.length ? selectedRegionCodes.value : undefined
      }),
      countActiveSubByPlan().catch(() => ({} as Record<string, number>))
    ])
    list.value = page.records
    total.value = page.total
    subCountMap.value = counts
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
  selectedRegionKeys.value = ['all']
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
    <!-- ============ 左侧区域树 (国家 → 城市) ============ -->
    <aside class="region-aside">
      <div class="region-aside-head">
        <NIcon :size="15"><Globe /></NIcon>
        <span>区域</span>
      </div>
      <NTree
        block-line
        :data="regionTree"
        :selected-keys="selectedRegionKeys"
        selectable
        @update:selected-keys="onRegionSelect"
      />
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
            <span class="pc-name-text truncate">{{ p.name }}</span>
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
              <span class="pc-spec-k"><Database :size="13" />月流量</span>
              <span class="pc-spec-v">{{ p.trafficGb }} GB</span>
            </div>
            <div class="pc-spec">
              <span class="pc-spec-k"><Zap :size="13" />带宽</span>
              <span class="pc-spec-v">{{ p.bandwidthMbps }} Mbps</span>
            </div>
          </div>

          <div class="pc-divider"></div>

          <!-- 底部: 订阅人数 + 落地机容量 -->
          <div class="pc-foot">
            <div class="pc-foot-row">
              <span class="pc-foot-k"><Users :size="13" />订阅会员</span>
              <span class="pc-foot-v">{{ subCountMap[p.id] ?? 0 }} 人</span>
            </div>
            <div class="pc-foot-row">
              <span class="pc-foot-k"><Gauge :size="13" />落地机容量</span>
              <span class="pc-foot-v" :class="{ 'pc-danger': (p.capacityAvailable ?? 0) <= 0 }">
                剩 {{ p.capacityAvailable ?? 0 }} / 总 {{ p.capacityTotal ?? 0 }}
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
  max-width: 1280px;
  margin: 0 auto;
}
.overview-wrap {
  flex: 1;
  min-width: 0;
}

/* 左侧区域树面板 */
.region-aside {
  width: 208px;
  flex-shrink: 0;
  position: sticky;
  top: 12px;
  max-height: calc(100vh - 88px);
  overflow: auto;
  padding: 10px 6px 12px;
  background: rgba(127, 127, 127, 0.03);
  border: 1px solid rgba(127, 127, 127, 0.1);
  border-radius: 8px;
}
.region-aside-head {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--nook-fg-muted);
  padding: 2px 6px 8px;
}
.region-aside-head :deep(svg) {
  color: var(--nook-fg-faint);
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
.plan-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(244px, 1fr));
  gap: 14px;
}
.plan-card {
  position: relative;
  display: flex;
  flex-direction: column;
  padding: 16px 16px 14px;
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
  align-items: center;
  gap: 7px;
  padding-right: 56px;
  min-width: 0;
}
.pc-name-icon {
  color: var(--nook-accent);
}
.pc-name-text {
  font-size: 15px;
  font-weight: 600;
  color: var(--nook-fg);
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
  font-size: 26px;
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
  gap: 9px;
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
  font-size: 13px;
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
