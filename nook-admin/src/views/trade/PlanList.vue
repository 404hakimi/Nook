<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import {
  NButton,
  NCard,
  NDataTable,
  NInput,
  NPagination,
  NSelect,
  NSpace,
  NTag,
  useMessage,
  type DataTableColumns
} from 'naive-ui'
import { storeToRefs } from 'pinia'
import { useConfirm } from '@/composables/useConfirm'
import { useRegionStore } from '@/stores/region'
import { useIpTypeStore } from '@/stores/ipType'
import { deleteTradePlan, pageTradePlan, toggleTradePlanEnabled, type TradePlan } from '@/api/trade/plan'
import PlanEditDialog from './PlanEditDialog.vue'

const message = useMessage()
const { confirm } = useConfirm()
const regionStore = useRegionStore()
const ipTypeStore = useIpTypeStore()
const { map: regionMap } = storeToRefs(regionStore)
const { map: ipTypeMap } = storeToRefs(ipTypeStore)

const list = ref<TradePlan[]>([])
const total = ref(0)
const loading = ref(false)
const pageNo = ref(1)
const pageSize = ref(10)
const keyword = ref('')
const filterEnabled = ref<number | null>(null)

const editOpen = ref(false)
const editTarget = ref<TradePlan | null>(null)

const enabledOptions = [
  { label: '全部', value: null },
  { label: '上架', value: 1 },
  { label: '下架', value: 0 }
]

async function load() {
  loading.value = true
  try {
    const res = await pageTradePlan({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      keyword: keyword.value.trim() || undefined,
      enabled: filterEnabled.value ?? undefined
    })
    list.value = res.records
    total.value = res.total
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

function openCreate() {
  editTarget.value = null
  editOpen.value = true
}
function openEdit(p: TradePlan) {
  editTarget.value = p
  editOpen.value = true
}

async function onToggle(p: TradePlan) {
  const to = p.enabled !== 1
  const ok = await confirm({
    title: to ? '上架套餐' : '下架套餐',
    message: to ? `上架 "${p.name}"? 上架后用户可购买` : `下架 "${p.name}"? 老用户不受影响, 新购被禁`,
    type: to ? 'info' : 'warning',
    confirmText: to ? '上架' : '下架'
  })
  if (!ok) return
  try {
    await toggleTradePlanEnabled(p.id, to)
    message.success(to ? '已上架' : '已下架')
    load()
  } catch {
    /* */
  }
}

async function onDelete(p: TradePlan) {
  const ok = await confirm({
    title: '删除套餐',
    message: `删除 "${p.name}"? (有活跃订阅时会被拒)`,
    type: 'error',
    confirmText: '删除'
  })
  if (!ok) return
  try {
    await deleteTradePlan(p.id)
    message.success('已删除')
    load()
  } catch {
    /* */
  }
}

const columns = computed<DataTableColumns<TradePlan>>(() => [
  { title: '套餐码', key: 'code', width: 180 },
  { title: '名称', key: 'name' },
  { title: '区域', key: 'regionCode', width: 110, render: (p) => (p.regionCode ? regionMap.value[p.regionCode]?.displayName ?? p.regionCode : '—') },
  { title: 'IP 类型', key: 'ipTypeId', width: 90, render: (p) => (p.ipTypeId ? ipTypeMap.value[p.ipTypeId]?.name ?? '-' : '—') },
  { title: '流量', key: 'trafficGb', width: 80, render: (p) => `${p.trafficGb}GB` },
  { title: '周期', key: 'periodDays', width: 70, render: (p) => `${p.periodDays}天` },
  { title: '价格', key: 'price', width: 90, render: (p) => `¥${p.price}` },
  {
    title: '容量(剩/总)',
    key: 'capacity',
    width: 110,
    render: (p) => h('span', { class: (p.capacityAvailable ?? 0) > 0 ? '' : 'text-red-500' },
      `${p.capacityAvailable ?? 0} / ${p.capacityTotal ?? 0}`)
  },
  {
    title: '状态',
    key: 'enabled',
    width: 80,
    render: (p) => h(NTag, { size: 'small', type: p.enabled === 1 ? 'success' : 'default' },
      { default: () => (p.enabled === 1 ? '上架' : '下架') })
  },
  {
    title: '操作',
    key: 'op',
    width: 180,
    render: (p) => h(NSpace, { size: 4 }, {
      default: () => [
        h(NButton, { size: 'tiny', onClick: () => openEdit(p) }, { default: () => '编辑' }),
        h(NButton, { size: 'tiny', quaternary: true, type: p.enabled === 1 ? 'warning' : 'primary', onClick: () => onToggle(p) },
          { default: () => (p.enabled === 1 ? '下架' : '上架') }),
        h(NButton, { size: 'tiny', quaternary: true, type: 'error', onClick: () => onDelete(p) }, { default: () => '删除' })
      ]
    })
  }
])
</script>

<template>
  <div class="space-y-3">
    <NCard size="small" :bordered="false">
      <div class="flex items-center gap-2 flex-wrap">
        <NInput v-model:value="keyword" placeholder="搜套餐码 / 名称" clearable style="width: 220px" @keyup.enter="onSearch" />
        <NSelect v-model:value="filterEnabled" :options="enabledOptions" style="width: 120px" @update:value="onSearch" />
        <NButton type="primary" @click="onSearch">查询</NButton>
        <div class="flex-1"></div>
        <NButton type="primary" @click="openCreate">新建套餐</NButton>
      </div>
    </NCard>

    <NCard size="small" :bordered="false">
      <NDataTable
        :columns="columns"
        :data="list"
        :loading="loading"
        size="small"
        :bordered="false"
        :row-key="(p: TradePlan) => p.id"
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

    <PlanEditDialog v-model="editOpen" :plan="editTarget" @saved="load" />
  </div>
</template>
