<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import {
  NCard,
  NDataTable,
  NIcon,
  NPagination,
  NSelect,
  NTag,
  type DataTableColumns
} from 'naive-ui'
import { ArrowRight, History } from 'lucide-vue-next'
import { formatDateTime } from '@/utils/date'
import {
  CHANGE_REASON_LABELS,
  CHANGE_REASON_OPTIONS,
  CHANGE_REASON_TAG_TYPE,
  CHANGE_TYPE_LABELS,
  CHANGE_TYPE_OPTIONS,
  CHANGE_TYPE_TAG_TYPE,
  pageSubscriptionChangeLog,
  type SubscriptionChangeLog
} from '@/api/trade/subscriptionChangeLog'

const list = ref<SubscriptionChangeLog[]>([])
const total = ref(0)
const loading = ref(false)
const pageNo = ref(1)
const pageSize = ref(10)
const filterType = ref<string | undefined>(undefined)
const filterReason = ref<string | undefined>(undefined)

async function load() {
  loading.value = true
  try {
    const res = await pageSubscriptionChangeLog({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      changeType: filterType.value,
      reason: filterReason.value
    })
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

/** 机器 IP 单元: 无值显示占位横杠. */
function ipCell(ip?: string) {
  return ip
    ? h('span', { class: 'font-mono text-xs' }, ip)
    : h('span', { class: 'text-zinc-300 dark:text-zinc-600' }, '—')
}

const columns = computed<DataTableColumns<SubscriptionChangeLog>>(() => [
  { title: '时间', key: 'createdAt', width: 165, render: (r) => formatDateTime(r.createdAt) },
  { title: '会员', key: 'member', width: 200, render: (r) => r.memberEmail ?? (r.memberUserId ?? '').slice(0, 12) },
  {
    title: '订阅',
    key: 'subscriptionId',
    width: 110,
    render: (r) => h('span', { class: 'font-mono text-xs', title: r.subscriptionId }, (r.subscriptionId ?? '').slice(0, 8))
  },
  {
    title: '类型',
    key: 'changeType',
    width: 90,
    render: (r) => h(NTag, { size: 'small', type: CHANGE_TYPE_TAG_TYPE[r.changeType] ?? 'default', bordered: false },
      { default: () => CHANGE_TYPE_LABELS[r.changeType] ?? r.changeType })
  },
  {
    title: '变更 (原 → 新)',
    key: 'change',
    minWidth: 220,
    render: (r) => h('div', { class: 'flex items-center gap-2' }, [
      ipCell(r.oldServerIp),
      h(NIcon, { size: 14, class: 'text-zinc-400' }, { default: () => h(ArrowRight) }),
      ipCell(r.newServerIp)
    ])
  },
  {
    title: '原因',
    key: 'reason',
    width: 100,
    render: (r) => h(NTag, { size: 'small', type: CHANGE_REASON_TAG_TYPE[r.reason] ?? 'default', bordered: false },
      { default: () => CHANGE_REASON_LABELS[r.reason] ?? r.reason })
  },
  {
    title: '操作者',
    key: 'operator',
    width: 110,
    render: (r) => (r.operator === 'system'
      ? h('span', { class: 'text-zinc-400' }, '系统')
      : h('span', { class: 'font-mono text-xs', title: r.operator }, (r.operator ?? '').slice(0, 8)))
  }
])
</script>

<template>
  <div class="space-y-3">
    <NCard size="small" :bordered="false">
      <div class="flex items-center gap-2">
        <NIcon class="text-zinc-400"><History :size="16" /></NIcon>
        <span class="font-medium">换机日志</span>
        <span class="text-xs text-zinc-400">共 {{ total }} 条</span>
        <div class="flex-1"></div>
        <NSelect
          v-model:value="filterType"
          :options="CHANGE_TYPE_OPTIONS"
          size="small"
          style="width: 120px"
          placeholder="类型"
          @update:value="onSearch"
        />
        <NSelect
          v-model:value="filterReason"
          :options="CHANGE_REASON_OPTIONS"
          size="small"
          style="width: 130px"
          placeholder="原因"
          @update:value="onSearch"
        />
      </div>
    </NCard>
    <NCard size="small" :bordered="false">
      <NDataTable
        :columns="columns"
        :data="list"
        :loading="loading"
        size="small"
        :bordered="false"
        :row-key="(r: SubscriptionChangeLog) => r.id"
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
  </div>
</template>
