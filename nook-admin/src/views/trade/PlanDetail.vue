<script setup lang="ts">
import { computed, h, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  NButton,
  NCard,
  NDataTable,
  NEmpty,
  NIcon,
  NPagination,
  NProgress,
  NSelect,
  NSpace,
  NSpin,
  NTag,
  useMessage,
  type DataTableColumns
} from 'naive-ui'
import {
  ArrowLeft,
  Pencil,
  Plus,
  Power,
  Trash2,
  Users
} from 'lucide-vue-next'
import { storeToRefs } from 'pinia'
import { useConfirm } from '@/composables/useConfirm'
import { useRegionStore } from '@/stores/region'
import { useIpTypeStore } from '@/stores/ipType'
import { IP_TYPE_CODE_LABELS } from '@/api/system/ip-type'
import {
  deleteTradePlan,
  getTradePlan,
  toggleTradePlanEnabled,
  type TradePlan
} from '@/api/trade/plan'
import {
  SUB_STATUS_LABELS,
  SUB_STATUS_OPTIONS,
  SUB_STATUS_TAG_TYPE,
  cancelSubscription,
  pageTradeSubscription,
  type TradeSubscription
} from '@/api/trade/subscription'
import { formatDateTime } from '@/utils/date'
import { formatBytes, trafficPercent } from '@/utils/format'
import RegionFlag from '@/components/RegionFlag.vue'
import PlanEditDialog from './PlanEditDialog.vue'
import AdminCreateSubDialog from './AdminCreateSubDialog.vue'
import SubscriptionChangeLogDialog from './SubscriptionChangeLogDialog.vue'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const { confirm } = useConfirm()
const regionStore = useRegionStore()
const ipTypeStore = useIpTypeStore()
const { map: regionMap } = storeToRefs(regionStore)
const { map: ipTypeMap } = storeToRefs(ipTypeStore)

const planId = computed(() => route.params.id as string)

// ===== 套餐 =====
const plan = ref<TradePlan | null>(null)
const loading = ref(false)
const error = ref('')

const editOpen = ref(false)
const createSubOpen = ref(false)
const changeLogOpen = ref(false)
const changeLogSub = ref<TradeSubscription | null>(null)

function openChangeLog(s: TradeSubscription) {
  changeLogSub.value = s
  changeLogOpen.value = true
}

// ===== 订阅列表 =====
const subs = ref<TradeSubscription[]>([])
const subTotal = ref(0)
const subLoading = ref(false)
const subPageNo = ref(1)
const subPageSize = ref(10)
const filterStatus = ref<string | undefined>(undefined)

const regionInfo = computed(() => (plan.value?.regionCode ? regionMap.value[plan.value.regionCode] : undefined))
const regionLabel = computed(() => regionInfo.value?.displayName ?? plan.value?.regionCode ?? '—')
const ipTypeLabel = computed(() => {
  if (!plan.value?.ipTypeId) return '—'
  const t = ipTypeMap.value[plan.value.ipTypeId]
  if (!t) return plan.value.ipTypeId
  return IP_TYPE_CODE_LABELS[t.code] || t.name || t.code
})

const capacityPercent = computed(() => {
  const totalCap = plan.value?.capacityTotal ?? 0
  if (totalCap <= 0) return 0
  return Math.round(((plan.value?.capacityOccupied ?? 0) / totalCap) * 100)
})
const capacityStatus = computed<'success' | 'warning' | 'error' | 'default'>(() => {
  const totalCap = plan.value?.capacityTotal ?? 0
  if (totalCap <= 0) return 'default'
  if ((plan.value?.capacityAvailable ?? 0) <= 0) return 'error'
  if (capacityPercent.value >= 80) return 'warning'
  return 'success'
})

async function loadPlan() {
  loading.value = true
  error.value = ''
  try {
    plan.value = await getTradePlan(planId.value)
  } catch (e) {
    error.value = (e as Error).message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function loadSubs() {
  subLoading.value = true
  try {
    const res = await pageTradeSubscription({
      pageNo: subPageNo.value,
      pageSize: subPageSize.value,
      planId: planId.value,
      status: filterStatus.value
    })
    subs.value = res.records
    subTotal.value = res.total
  } catch {
    /* */
  } finally {
    subLoading.value = false
  }
}

onMounted(async () => {
  await Promise.all([regionStore.ensureLoaded(), ipTypeStore.ensureLoaded()])
  await Promise.all([loadPlan(), loadSubs()])
})
watch(planId, async () => {
  subPageNo.value = 1
  await Promise.all([loadPlan(), loadSubs()])
})

function onSubSearch() {
  subPageNo.value = 1
  loadSubs()
}

async function onToggle() {
  if (!plan.value) return
  const to = plan.value.enabled !== 1
  const ok = await confirm({
    title: to ? '上架套餐' : '下架套餐',
    message: to ? `上架 "${plan.value.name}"? 上架后用户可购买` : `下架 "${plan.value.name}"? 老用户不受影响, 新购被禁`,
    type: to ? 'info' : 'warning',
    confirmText: to ? '上架' : '下架'
  })
  if (!ok) return
  try {
    await toggleTradePlanEnabled(plan.value.id, to)
    message.success(to ? '已上架' : '已下架')
    loadPlan()
  } catch {
    /* */
  }
}

async function onDelete() {
  if (!plan.value) return
  const ok = await confirm({
    title: '删除套餐',
    message: `删除 "${plan.value.name}"? (有活跃订阅时会被拒)`,
    type: 'danger',
    confirmText: '删除'
  })
  if (!ok) return
  try {
    await deleteTradePlan(plan.value.id)
    message.success('已删除')
    router.push('/trade/plans')
  } catch {
    /* */
  }
}

async function onCancelSub(s: TradeSubscription) {
  const ok = await confirm({
    title: '退订',
    message: '退订该订阅? 将吊销 xray 客户端 + 释放落地机',
    type: 'warning',
    confirmText: '退订'
  })
  if (!ok) return
  try {
    await cancelSubscription(s.id)
    message.success('已退订')
    loadSubs()
  } catch {
    /* */
  }
}

function onSubCreated() {
  subPageNo.value = 1
  loadSubs()
  loadPlan()
}

function back() {
  router.push('/trade/plans')
}

/** 解析服务器详情页地址 (供 <a target="_blank"> 新标签打开). */
function serverHref(routeName: string, id: string) {
  return router.resolve({ name: routeName, params: { id } }).href
}

const columns = computed<DataTableColumns<TradeSubscription>>(() => [
  { title: '会员', key: 'member', render: (s) => s.memberEmail ?? (s.memberUserId ?? '').slice(0, 12) },
  {
    title: '线路机',
    key: 'frontline',
    width: 150,
    render: (s) => (s.frontlineServerId
      ? h('a', { href: serverHref('server-detail', s.frontlineServerId), target: '_blank', rel: 'noopener', class: 'srv-link' },
        s.frontlineIp ?? s.frontlineServerId.slice(0, 8))
      : h('span', { class: 'text-zinc-400' }, '—'))
  },
  {
    title: '落地机',
    key: 'landing',
    width: 150,
    render: (s) => (s.landingServerId
      ? h('a', { href: serverHref('resource-server-landing-detail', s.landingServerId), target: '_blank', rel: 'noopener', class: 'srv-link' },
        s.landingIp ?? s.landingServerId.slice(0, 8))
      : h('span', { class: 'text-zinc-400' }, '—'))
  },
  {
    title: '流量',
    key: 'traffic',
    width: 170,
    render: (s) => {
      const pct = trafficPercent(s.usedBytes, s.trafficGb)
      const status = pct >= 100 ? 'error' : pct >= 80 ? 'warning' : 'success'
      return h('div', null, [
        h('div', { class: 'text-xs', style: 'line-height:1.3' },
          `${formatBytes(s.usedBytes)} / ${s.trafficGb ?? '∞'} GB`),
        h(NProgress, { percentage: pct, height: 5, status, showIndicator: false, style: 'margin-top:3px' })
      ])
    }
  },
  { title: '开始', key: 'startedAt', width: 150, render: (s) => formatDateTime(s.startedAt) },
  { title: '到期', key: 'expiresAt', width: 150, render: (s) => formatDateTime(s.expiresAt) },
  {
    title: '状态',
    key: 'status',
    width: 90,
    render: (s) => h(NTag, { size: 'small', type: SUB_STATUS_TAG_TYPE[s.status] ?? 'default' },
      { default: () => SUB_STATUS_LABELS[s.status] ?? s.status })
  },
  {
    title: '操作',
    key: 'op',
    width: 120,
    render: (s) => h(NSpace, { size: 4, wrap: false }, {
      default: () => [
        h(NButton, { size: 'tiny', quaternary: true, onClick: () => openChangeLog(s) }, { default: () => '记录' }),
        s.status === 'ACTIVE'
          ? h(NButton, { size: 'tiny', quaternary: true, type: 'error', onClick: () => onCancelSub(s) }, { default: () => '退订' })
          : null
      ]
    })
  }
])
</script>

<template>
  <div class="detail-wrap space-y-3">
    <!-- ============ 头部 ============ -->
    <NCard size="small" :content-style="{ padding: '14px 16px' }" class="header-card">
      <div class="flex items-start gap-4">
        <NButton quaternary size="small" class="mt-1" @click="back">
          <template #icon><NIcon><ArrowLeft :size="16" /></NIcon></template>
        </NButton>

        <NSpin :show="loading" size="small" class="flex-1">
          <NEmpty v-if="!loading && error && !plan" :description="error" />

          <div v-else-if="plan" class="flex items-start gap-3 flex-wrap">
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
                <span class="text-xl font-semibold">{{ plan.name }}</span>
                <NTag size="small" :type="plan.enabled === 1 ? 'success' : 'default'">
                  {{ plan.enabled === 1 ? '上架' : '下架' }}
                </NTag>
                <NTag v-if="regionInfo" size="small" type="info" :bordered="false">{{ regionLabel }}</NTag>
                <NTag size="small" :bordered="false">{{ ipTypeLabel }}</NTag>
              </div>
              <div class="mt-1 text-xs text-zinc-500 font-mono">
                {{ plan.code }}
              </div>
              <!-- 规格 -->
              <div class="spec-line">
                <div class="spec-item"><span class="spec-k">流量</span><span class="spec-v">{{ plan.trafficGb }} GB</span></div>
                <div class="spec-item"><span class="spec-k">带宽</span><span class="spec-v">{{ plan.bandwidthMbps }} Mbps</span></div>
                <div class="spec-item"><span class="spec-k">周期</span><span class="spec-v">{{ plan.periodDays }} 天</span></div>
                <div class="spec-item"><span class="spec-k">售价</span><span class="spec-v spec-price">¥{{ plan.price }}</span></div>
              </div>
              <div v-if="plan.remark" class="mt-1 text-xs text-zinc-400">备注: {{ plan.remark }}</div>
            </div>

            <!-- 操作 -->
            <NSpace :size="6">
              <NButton
                size="small"
                type="primary"
                :disabled="plan.enabled !== 1"
                :title="plan.enabled === 1 ? '' : '套餐下架中, 无法下单'"
                @click="createSubOpen = true"
              >
                <template #icon><NIcon><Plus :size="14" /></NIcon></template>
                代客下单
              </NButton>
              <NButton size="small" @click="editOpen = true">
                <template #icon><NIcon><Pencil :size="14" /></NIcon></template>
                编辑
              </NButton>
              <NButton size="small" :type="plan.enabled === 1 ? 'warning' : 'primary'" quaternary @click="onToggle">
                <template #icon><NIcon><Power :size="14" /></NIcon></template>
                {{ plan.enabled === 1 ? '下架' : '上架' }}
              </NButton>
              <NButton size="small" type="error" quaternary @click="onDelete">
                <template #icon><NIcon><Trash2 :size="14" /></NIcon></template>
                删除
              </NButton>
            </NSpace>
          </div>
        </NSpin>
      </div>
    </NCard>

    <!-- ============ 容量 ============ -->
    <NCard v-if="plan" size="small" :content-style="{ padding: '14px 16px' }" title="落地机容量">
      <div class="cap-strip">
        <div class="cap-item">
          <span class="cap-num">{{ plan.capacityTotal ?? 0 }}</span>
          <span class="cap-label">匹配落地机</span>
        </div>
        <div class="stat-divider"></div>
        <div class="cap-item">
          <span class="cap-num" :class="(plan.capacityAvailable ?? 0) > 0 ? 'text-green-600' : 'text-red-500'">{{ plan.capacityAvailable ?? 0 }}</span>
          <span class="cap-label">剩余可售</span>
        </div>
        <div class="cap-item">
          <span class="cap-num text-amber-600">{{ plan.capacityOccupied ?? 0 }}</span>
          <span class="cap-label">已占用</span>
        </div>
        <div class="cap-progress">
          <NProgress :percentage="capacityPercent" :height="8" :status="capacityStatus" :show-indicator="false" />
        </div>
      </div>
      <div class="text-xs text-zinc-400 mt-2">容量按"同区域 + 同 IP 类型 + 带宽/流量达标"自动匹配落地机池统计</div>
    </NCard>

    <!-- ============ 订阅会员 ============ -->
    <NCard size="small" :content-style="{ padding: '14px 16px' }">
      <template #header>
        <div class="flex items-center gap-2">
          <NIcon class="text-zinc-400"><Users :size="16" /></NIcon>
          <span>订阅会员</span>
          <span class="text-xs text-zinc-400">共 {{ subTotal }} 条</span>
        </div>
      </template>
      <div class="flex items-center gap-2 mb-3">
        <NSelect
          v-model:value="filterStatus"
          :options="SUB_STATUS_OPTIONS"
          size="small"
          style="width: 120px"
          @update:value="onSubSearch"
        />
      </div>
      <NDataTable
        :columns="columns"
        :data="subs"
        :loading="subLoading"
        size="small"
        :bordered="false"
        :row-key="(s: TradeSubscription) => s.id"
      />
      <div class="flex justify-end mt-3">
        <NPagination
          v-model:page="subPageNo"
          v-model:page-size="subPageSize"
          :item-count="subTotal"
          :page-sizes="[10, 20, 50]"
          show-size-picker
          @update:page="loadSubs"
          @update:page-size="() => { subPageNo = 1; loadSubs() }"
        />
      </div>
    </NCard>

    <PlanEditDialog v-model="editOpen" :plan="plan" @saved="loadPlan" />
    <AdminCreateSubDialog v-model="createSubOpen" :preset-plan-id="planId" @created="onSubCreated" />
    <SubscriptionChangeLogDialog
      v-model="changeLogOpen"
      :subscription-id="changeLogSub?.id"
      :member-email="changeLogSub?.memberEmail"
    />
  </div>
</template>

<style scoped>
.detail-wrap {
  max-width: 1280px;
  margin: 0 auto;
}
.header-card {
  background: linear-gradient(to right, rgba(99, 102, 241, 0.03), rgba(127, 127, 127, 0));
}
.header-flag {
  filter: drop-shadow(0 1px 2px rgba(0, 0, 0, 0.1));
}

/* 头部规格行: 标签在上 / 值在下, 字号统一 */
.spec-line {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 28px;
  margin-top: 10px;
}
.spec-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.spec-k {
  font-size: 11px;
  color: var(--nook-fg-faint);
}
.spec-v {
  font-size: 15px;
  font-weight: 600;
  color: var(--nook-fg);
}
.spec-price {
  color: var(--nook-accent);
}

/* 容量条 */
.cap-strip {
  display: flex;
  align-items: center;
  gap: 28px;
}
.cap-item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
}
.cap-num {
  font-size: 20px;
  font-weight: 700;
  font-family: var(--nook-mono);
  line-height: 1.1;
}
.cap-label {
  font-size: 11px;
  color: var(--nook-fg-faint);
}
.stat-divider {
  width: 1px;
  height: 30px;
  background: rgba(127, 127, 127, 0.2);
}
.cap-progress {
  flex: 1;
  min-width: 120px;
}

/* 线路机 / 落地机 跳转链接 */
.srv-link {
  color: var(--nook-accent);
  font-family: var(--nook-mono);
  font-size: 12px;
  text-decoration: none;
}
.srv-link:hover {
  text-decoration: underline;
}
</style>
