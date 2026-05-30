<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  NButton,
  NCard,
  NDataTable,
  NPagination,
  NProgress,
  NSelect,
  NSpace,
  NTag,
  useMessage,
  type DataTableColumns
} from 'naive-ui'
import { useConfirm } from '@/composables/useConfirm'
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
import AdminCreateSubDialog from './AdminCreateSubDialog.vue'
import SubscriptionChangeLogDialog from './SubscriptionChangeLogDialog.vue'

const message = useMessage()
const { confirm } = useConfirm()
const router = useRouter()

/** 解析服务器详情页地址 (供 <a target="_blank"> 新标签打开). */
function serverHref(routeName: string, id: string) {
  return router.resolve({ name: routeName, params: { id } }).href
}

const list = ref<TradeSubscription[]>([])
const total = ref(0)
const loading = ref(false)
const pageNo = ref(1)
const pageSize = ref(10)
const filterStatus = ref<string | undefined>(undefined)
const createOpen = ref(false)
const changeLogOpen = ref(false)
const changeLogSub = ref<TradeSubscription | null>(null)

function openChangeLog(s: TradeSubscription) {
  changeLogSub.value = s
  changeLogOpen.value = true
}

async function load() {
  loading.value = true
  try {
    const res = await pageTradeSubscription({ pageNo: pageNo.value, pageSize: pageSize.value, status: filterStatus.value })
    list.value = res.records
    total.value = res.total
  } catch {
    /* */
  } finally {
    loading.value = false
  }
}

onMounted(load)

function onSearch() {
  pageNo.value = 1
  load()
}

async function onCancel(s: TradeSubscription) {
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
    load()
  } catch {
    /* */
  }
}

const columns = computed<DataTableColumns<TradeSubscription>>(() => [
  { title: '套餐', key: 'planName', render: (s) => s.planName ?? s.planId },
  { title: '会员', key: 'member', width: 200, render: (s) => s.memberEmail ?? (s.memberUserId ?? '').slice(0, 12) },
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
  { title: '开始', key: 'startedAt', width: 160, render: (s) => formatDateTime(s.startedAt) },
  { title: '到期', key: 'expiresAt', width: 160, render: (s) => formatDateTime(s.expiresAt) },
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
          ? h(NButton, { size: 'tiny', quaternary: true, type: 'error', onClick: () => onCancel(s) }, { default: () => '退订' })
          : null
      ]
    })
  }
])
</script>

<template>
  <div class="space-y-3">
    <NCard size="small" :bordered="false">
      <div class="flex items-center gap-2">
        <NSelect v-model:value="filterStatus" :options="SUB_STATUS_OPTIONS" style="width: 120px" @update:value="onSearch" />
        <div class="flex-1"></div>
        <NButton type="primary" @click="createOpen = true">代客下单</NButton>
      </div>
    </NCard>
    <NCard size="small" :bordered="false">
      <NDataTable
        :columns="columns"
        :data="list"
        :loading="loading"
        size="small"
        :bordered="false"
        :row-key="(s: TradeSubscription) => s.id"
      />
      <div class="flex justify-end mt-3">
        <NPagination
          v-model:page="pageNo"
          v-model:page-size="pageSize"
          :item-count="total"
          :page-sizes="[10, 20, 50]"
          show-size-picker
          @update:page="load"
          @update:page-size="() => { pageNo = 1; load() }"
        />
      </div>
    </NCard>
    <AdminCreateSubDialog v-model="createOpen" @created="load" />
    <SubscriptionChangeLogDialog
      v-model="changeLogOpen"
      :subscription-id="changeLogSub?.id"
      :member-email="changeLogSub?.memberEmail"
    />
  </div>
</template>

<style scoped>
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
