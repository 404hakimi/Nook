<script setup lang="ts">
import { computed, h, onMounted, onUnmounted, reactive, ref } from 'vue'
import { RefreshCcw, Search, Eye, Ban } from 'lucide-vue-next'
import {
  NButton,
  NCard,
  NDataTable,
  NIcon,
  NInput,
  NProgress,
  NSelect,
  NTag,
  useMessage,
  type DataTableColumns
} from 'naive-ui'
import { useConfirm } from '@/composables/useConfirm'
import {
  OP_STATUS_META,
  cancelOpLog,
  pageOpLog,
  type OpLog,
  type OpLogPageQuery,
  type OpStatus,
  type OpType
} from '@/api/operation/op-log'
import { opProgressClient, type OpProgressEvent } from '@/api/operation/op-progress-ws'
import { useOpConfigStore } from '@/stores/opConfig'
import { formatDateTime } from '@/utils/date'
import OpLogDetailDialog from './OpLogDetailDialog.vue'

const message = useMessage()
const { confirm } = useConfirm()
const opConfigStore = useOpConfigStore()

const STATUS_OPTIONS: { label: string; value: OpStatus | undefined }[] = [
  { label: '全部', value: undefined },
  { label: '排队中', value: 'QUEUED' },
  { label: '执行中', value: 'RUNNING' },
  { label: '已完成', value: 'DONE' },
  { label: '失败', value: 'FAILED' },
  { label: '已取消', value: 'CANCELLED' },
  { label: '超时', value: 'TIMED_OUT' }
]

// 下拉数据来自 op_config 表 (admin 改名后刷新即生效, 不再前端硬编码)
const opTypeOptions = computed<{ label: string; value: OpType | undefined }[]>(() => [
  { label: '全部', value: undefined },
  ...opConfigStore.list.map((c) => ({
    label: c.name,
    value: c.opType as OpType
  }))
])

const query = reactive<Required<Pick<OpLogPageQuery, 'pageNo' | 'pageSize'>> & OpLogPageQuery>({
  pageNo: 1,
  pageSize: 20,
  status: undefined,
  serverId: '',
  opType: undefined
})
const list = ref<OpLog[]>([])
const total = ref(0)
const loading = ref(false)

// WebSocket 实时进度: 活跃行 (QUEUED/RUNNING) 自动订阅, 终态自动 unsubscribe;
// 终态事件还触发一次 silent loadList 兜底取回耗时 / 错误信息等持久化字段.
const unsubscribers = new Map<string, () => void>()
let finalRefreshTimer: number | undefined
const FINAL_REFRESH_DEBOUNCE_MS = 600

function applyEvent(ev: OpProgressEvent) {
  const row = list.value.find((r) => r.id === ev.opId)
  if (!row) return
  row.status = ev.status
  if (ev.currentStep != null) row.currentStep = ev.currentStep
  if (ev.progressPct != null) row.progressPct = ev.progressPct
  if (ev.message != null) row.lastMessage = ev.message
  if (ev.errorCode != null) row.errorCode = ev.errorCode
  if (ev.errorMsg != null) row.errorMsg = ev.errorMsg
  if (isTerminal(ev.status)) {
    // 终态: 取消订阅 + debounce 刷新一次拿 elapsedMs / startedAt / endedAt 等
    const un = unsubscribers.get(ev.opId)
    if (un) {
      un()
      unsubscribers.delete(ev.opId)
    }
    if (finalRefreshTimer) window.clearTimeout(finalRefreshTimer)
    finalRefreshTimer = window.setTimeout(() => loadList(/* silent */ true), FINAL_REFRESH_DEBOUNCE_MS)
  }
}

function isTerminal(s: OpStatus): boolean {
  return s === 'DONE' || s === 'FAILED' || s === 'CANCELLED' || s === 'TIMED_OUT'
}

/** 同步当前 list 与订阅: 活跃行没订阅就订阅, 终态行有订阅就清掉. */
function syncSubscriptions() {
  const activeIds = new Set(list.value.filter((r) => !isTerminal(r.status)).map((r) => r.id))
  // 新增订阅
  for (const id of activeIds) {
    if (!unsubscribers.has(id)) {
      unsubscribers.set(id, opProgressClient.subscribe(id, applyEvent))
    }
  }
  // 已不存在 / 终态的清掉
  for (const id of Array.from(unsubscribers.keys())) {
    if (!activeIds.has(id)) {
      unsubscribers.get(id)!()
      unsubscribers.delete(id)
    }
  }
}

async function loadList(silent = false) {
  if (!silent) loading.value = true
  try {
    const res = await pageOpLog({
      pageNo: query.pageNo,
      pageSize: query.pageSize,
      status: query.status,
      serverId: query.serverId || undefined,
      opType: query.opType
    })
    const maxPage = res.total > 0 ? Math.ceil(res.total / query.pageSize) : 1
    if (query.pageNo > maxPage) {
      query.pageNo = maxPage
      if (!silent) loading.value = false
      await loadList(silent)
      return
    }
    list.value = res.records
    total.value = res.total
    syncSubscriptions()
  } catch {
    /* request 拦截器已 toast */
  } finally {
    if (!silent) loading.value = false
  }
}

function resetQuery() {
  query.pageNo = 1
  query.status = undefined
  query.serverId = ''
  query.opType = undefined
  loadList()
}

function onSearch() {
  query.pageNo = 1
  loadList()
}

// ===== 详情 =====
const detailOpen = ref(false)
const detailOpId = ref<string | null>(null)

function openDetail(id: string) {
  detailOpId.value = id
  detailOpen.value = true
}

// ===== 取消 =====
const cancelingId = ref<string | null>(null)

async function onCancel(row: OpLog) {
  if (row.status !== 'QUEUED') {
    message.warning('只能取消处于"排队中"的任务')
    return
  }
  const ok = await confirm({
    title: '取消任务',
    message: `取消任务「${opConfigStore.getLabel(row.opType)}」?`,
    type: 'warning',
    confirmText: '取消任务',
    cancelText: '关闭'
  })
  if (!ok) return
  cancelingId.value = row.id
  try {
    const ok2 = await cancelOpLog(row.id)
    if (ok2) {
      message.success('已取消')
    } else {
      message.warning('该任务已开始执行或已结束, 无法取消')
    }
    loadList()
  } catch {
    /* */
  } finally {
    cancelingId.value = null
  }
}

// ===== 列定义 =====
const columns = computed<DataTableColumns<OpLog>>(() => [
  {
    title: 'opId',
    key: 'id',
    width: 110,
    render: (row) => h('span', { class: 'font-mono text-xs text-zinc-500' }, row.id.slice(0, 8))
  },
  {
    title: '操作',
    key: 'opType',
    width: 130,
    render: (row) =>
      h('div', { class: 'whitespace-nowrap' }, [
        h('span', { class: 'font-medium' }, opConfigStore.getLabel(row.opType))
      ])
  },
  {
    title: '服务器',
    key: 'serverId',
    render: (row) =>
      h(
        'span',
        // 后端 enrich 后 serverName 是别名, hover tooltip 看完整 id
        { class: 'text-xs', title: row.serverId },
        row.serverName || row.serverId.slice(0, 12)
      )
  },
  {
    title: '目标',
    key: 'targetId',
    render: (row) => {
      if (!row.targetId) return h('span', { class: 'text-zinc-400 text-xs' }, '-')
      return h(
        'span',
        { class: 'text-xs', title: row.targetId },
        row.targetName || row.targetId.slice(0, 12)
      )
    }
  },
  {
    title: '状态',
    key: 'status',
    width: 100,
    render: (row) => {
      const meta = OP_STATUS_META[row.status]
      return h(NTag, { size: 'small', type: meta?.tagType || 'default' }, { default: () => meta?.label || row.status })
    }
  },
  {
    title: '进度',
    key: 'progress',
    width: 130,
    render: (row) => {
      // RUNNING / QUEUED 都画进度条 (QUEUED = 0%); 终态用 tag 显示, 不重复占视觉
      if (row.status !== 'RUNNING' && row.status !== 'QUEUED') {
        return h('span', { class: 'text-zinc-400 text-xs' }, '-')
      }
      const pct = row.progressPct ?? 0
      return h(NProgress, {
        type: 'line',
        percentage: pct,
        showIndicator: true,
        height: 6,
        indicatorPlacement: 'inside',
        status: row.status === 'QUEUED' ? 'default' : 'info'
      })
    }
  },
  {
    title: '当前步骤',
    key: 'currentStep',
    render: (row) =>
      h(
        'span',
        { class: 'text-xs', title: row.lastMessage || '' },
        row.currentStep || (row.status === 'FAILED' ? '失败' : '-')
      )
  },
  {
    title: '触发者',
    key: 'operator',
    width: 110,
    render: (row) =>
      h(
        'span',
        // hover tooltip 看原始 id (排查时用); 显示用 enricher 拼好的 name
        { class: 'text-xs', title: row.operator || '' },
        row.operatorName || row.operator || '-'
      )
  },
  {
    title: '入队时间',
    key: 'enqueuedAt',
    width: 150,
    render: (row) => h('span', { class: 'text-xs' }, formatDateTime(row.enqueuedAt))
  },
  {
    title: '耗时',
    key: 'elapsed',
    width: 90,
    render: (row) => {
      if (row.elapsedMs == null) return h('span', { class: 'text-zinc-400 text-xs' }, '-')
      const s = row.elapsedMs / 1000
      const txt = s < 60 ? `${s.toFixed(1)}s` : `${(s / 60).toFixed(1)}m`
      return h('span', { class: 'font-mono text-xs' }, txt)
    }
  },
  {
    title: '操作',
    key: 'actions',
    align: 'right',
    width: 130,
    render: (row) =>
      h('div', { class: 'flex gap-1 justify-end' }, [
        h(
          NButton,
          {
            size: 'tiny',
            quaternary: true,
            onClick: () => openDetail(row.id)
          },
          {
            icon: () => h(NIcon, null, { default: () => h(Eye) }),
            default: () => '详情'
          }
        ),
        row.status === 'QUEUED'
          ? h(
              NButton,
              {
                size: 'tiny',
                quaternary: true,
                type: 'warning',
                loading: cancelingId.value === row.id,
                onClick: () => onCancel(row)
              },
              {
                icon: () => h(NIcon, null, { default: () => h(Ban) }),
                default: () => '取消'
              }
            )
          : null
      ])
  }
])

const pagination = computed(() => ({
  page: query.pageNo,
  pageSize: query.pageSize,
  itemCount: total.value,
  pageSizes: [10, 20, 50, 100],
  showSizePicker: true,
  prefix: ({ itemCount }: { itemCount?: number }) => `共 ${itemCount ?? 0} 条`,
  onUpdatePage: (p: number) => {
    query.pageNo = p
    loadList()
  },
  onUpdatePageSize: (s: number) => {
    query.pageSize = s
    query.pageNo = 1
    loadList()
  }
}))

// 先把 op_config 拉好再渲染列表, 保证首屏行 label / 下拉都是中文
onMounted(async () => {
  await opConfigStore.ensureLoaded()
  loadList()
})

onUnmounted(() => {
  for (const un of unsubscribers.values()) un()
  unsubscribers.clear()
  if (finalRefreshTimer) window.clearTimeout(finalRefreshTimer)
})
</script>

<template>
  <div class="space-y-4">
    <!-- 顶部搜索栏 -->
    <NCard size="small">
      <div class="flex flex-wrap gap-3 items-end">
        <div>
          <div class="text-xs text-zinc-500 mb-1">状态</div>
          <NSelect
            v-model:value="query.status"
            :options="STATUS_OPTIONS"
            size="small"
            class="w-32"
          />
        </div>
        <div>
          <div class="text-xs text-zinc-500 mb-1">操作类型</div>
          <NSelect
            v-model:value="query.opType"
            :options="opTypeOptions"
            size="small"
            class="w-36"
          />
        </div>
        <div>
          <div class="text-xs text-zinc-500 mb-1">服务器 ID</div>
          <NInput
            v-model:value="query.serverId"
            size="small"
            placeholder="serverId 前缀"
            class="w-60"
            @keyup.enter="onSearch"
          />
        </div>
        <NButton type="primary" size="small" @click="onSearch">
          <template #icon><NIcon><Search /></NIcon></template>
          搜索
        </NButton>
        <NButton quaternary size="small" @click="resetQuery">
          <template #icon><NIcon><RefreshCcw /></NIcon></template>
          重置
        </NButton>
        <div class="flex-1"></div>
        <NButton quaternary size="small" @click="loadList(false)">
          <template #icon><NIcon><RefreshCcw /></NIcon></template>
          刷新
        </NButton>
      </div>
    </NCard>

    <!-- 表格 -->
    <NCard size="small" :content-style="{ padding: 0 }">
      <NDataTable
        :columns="columns"
        :data="list"
        :loading="loading"
        :pagination="pagination"
        :remote="true"
        :bordered="false"
        :row-key="(row: OpLog) => row.id"
        size="small"
      />
    </NCard>

    <!-- 详情弹框 -->
    <OpLogDetailDialog v-model="detailOpen" :op-id="detailOpId" />
  </div>
</template>
