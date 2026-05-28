<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import {
  NButton,
  NCard,
  NDataTable,
  NPagination,
  NSelect,
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
import AdminCreateSubDialog from './AdminCreateSubDialog.vue'

const message = useMessage()
const { confirm } = useConfirm()

const list = ref<TradeSubscription[]>([])
const total = ref(0)
const loading = ref(false)
const pageNo = ref(1)
const pageSize = ref(10)
const filterStatus = ref<string | undefined>(undefined)
const createOpen = ref(false)

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
  { title: '会员', key: 'memberUserId', width: 130, render: (s) => (s.memberUserId ?? '').slice(0, 12) },
  { title: '客户端', key: 'xrayClientId', width: 130, render: (s) => (s.xrayClientId ?? '').slice(0, 12) },
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
    width: 80,
    render: (s) => (s.status === 'ACTIVE'
      ? h(NButton, { size: 'tiny', quaternary: true, type: 'error', onClick: () => onCancel(s) }, { default: () => '退订' })
      : null)
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
  </div>
</template>
