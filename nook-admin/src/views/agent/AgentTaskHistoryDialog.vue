<script setup lang="ts">
import { computed, h, reactive, ref, watch } from 'vue'
import { RefreshCcw } from 'lucide-vue-next'
import {
  NButton,
  NCode,
  NDataTable,
  NIcon,
  NModal,
  NSelect,
  NTag,
  type DataTableColumns,
  type PaginationProps
} from 'naive-ui'
import { pageAgentTasks, type AgentTaskHistoryItem } from '@/api/agent/agent'
import { formatDateTime } from '@/utils/date'

const props = defineProps<{
  modelValue: boolean
  serverId: string | null
  serverName?: string
}>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void }>()

const open = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const tasks = ref<AgentTaskHistoryItem[]>([])
const loading = ref(false)
const total = ref(0)

const query = reactive({
  pageNo: 1,
  pageSize: 20,
  taskType: undefined as string | undefined,
  status: undefined as string | undefined
})

const TASK_TYPE_OPTIONS = [
  { label: '全部类型', value: undefined as string | undefined },
  { label: 'agent_upgrade', value: 'agent_upgrade' },
  { label: 'config_reload', value: 'config_reload' },
  { label: 'truncate_log', value: 'truncate_log' },
  { label: 'ping', value: 'ping' }
]
const STATUS_OPTIONS = [
  { label: '全部状态', value: undefined as string | undefined },
  { label: 'PENDING', value: 'PENDING' },
  { label: 'PICKED', value: 'PICKED' },
  { label: 'SUCCESS', value: 'SUCCESS' },
  { label: 'FAILED', value: 'FAILED' }
]

async function load() {
  if (!props.serverId) return
  loading.value = true
  try {
    const r = await pageAgentTasks(props.serverId, { ...query })
    tasks.value = r.records
    total.value = r.total
  } catch { /* */ } finally {
    loading.value = false
  }
}

watch(open, (v) => {
  if (v) {
    query.pageNo = 1
    query.taskType = undefined
    query.status = undefined
    load()
  } else {
    tasks.value = []
    total.value = 0
  }
})

watch(() => [query.taskType, query.status], () => {
  query.pageNo = 1
  load()
})

const pagination = computed<PaginationProps>(() => ({
  page: query.pageNo,
  pageSize: query.pageSize,
  itemCount: total.value,
  showSizePicker: true,
  pageSizes: [10, 20, 50],
  prefix: ({ itemCount }) => `共 ${itemCount} 条`,
  onChange: (p: number) => { query.pageNo = p; load() },
  onUpdatePageSize: (s: number) => { query.pageSize = s; query.pageNo = 1; load() }
}))

const STATUS_TAG: Record<string, 'success' | 'warning' | 'error' | 'info' | 'default'> = {
  SUCCESS: 'success',
  PICKED: 'info',
  PENDING: 'warning',
  FAILED: 'error'
}

/** 派发 → 完成 时长; 未完成显示 "-". */
function duration(row: AgentTaskHistoryItem): string {
  if (!row.createdAt) return '-'
  if (row.status !== 'SUCCESS' && row.status !== 'FAILED') return '-'
  if (!row.updatedAt) return '-'
  const startMs = new Date(row.createdAt.replace(' ', 'T')).getTime()
  const endMs = new Date(row.updatedAt.replace(' ', 'T')).getTime()
  if (Number.isNaN(startMs) || Number.isNaN(endMs)) return '-'
  const s = Math.max(0, Math.floor((endMs - startMs) / 1000))
  if (s < 60) return `${s}s`
  if (s < 3600) return `${Math.floor(s / 60)}m${s % 60}s`
  return `${Math.floor(s / 3600)}h${Math.floor((s % 3600) / 60)}m`
}

function prettyJson(s?: string): string {
  if (!s) return ''
  try {
    return JSON.stringify(JSON.parse(s), null, 2)
  } catch {
    return s
  }
}

/** 结果摘要: 按 taskType 出友好字符串; 失败原文截断 60 字. */
function summary(row: AgentTaskHistoryItem): string {
  if (!row.resultPayload) {
    if (row.status === 'PENDING') return '等待 agent 拾取…'
    if (row.status === 'PICKED') return 'agent 已拾取, 处理中…'
    return '—'
  }
  try {
    const r = JSON.parse(row.resultPayload)
    if (row.taskType === 'agent_upgrade' && r.version) return `→ v${r.version}`
    if (row.taskType === 'config_reload' && r.md5) return `md5=${r.md5.slice(0, 8)}… (${r.bytes}B)`
    if (row.taskType === 'truncate_log' && r.results) {
      const c = (r.results.match(/释放 \d+ bytes/g) || []).length
      return `清了 ${c} 个文件`
    }
    if (r.error) return `❌ ${r.error.slice(0, 60)}`
    if (r.raw) return r.raw.slice(0, 60)
    return JSON.stringify(r).slice(0, 60)
  } catch {
    return row.resultPayload.slice(0, 60)
  }
}

const expandedRowKeys = ref<string[]>([])

const columns = computed<DataTableColumns<AgentTaskHistoryItem>>(() => [
  {
    type: 'expand',
    renderExpand: (row) =>
      h('div', { class: 'task-detail' }, [
        h('div', { class: 'detail-grid' }, [
          h('div', null, [
            h('div', { class: 'detail-label' }, '派发 payload (admin → agent)'),
            h('div', { class: 'detail-code' }, [
              row.taskPayload
                ? h(NCode, { code: prettyJson(row.taskPayload), language: 'json', wordWrap: true })
                : h('span', { class: 'detail-empty' }, '无')
            ])
          ]),
          h('div', null, [
            h('div', { class: 'detail-label' }, '结果 payload (agent → admin)'),
            h('div', { class: 'detail-code' }, [
              row.resultPayload
                ? h(NCode, { code: prettyJson(row.resultPayload), language: 'json', wordWrap: true })
                : h('span', { class: 'detail-empty' }, '尚未上报')
            ])
          ])
        ]),
        h('div', { class: 'detail-meta' }, [
          h('span', null, [h('b', null, 'Task ID '), h('code', null, row.id)]),
          h('span', null, [h('b', null, '派发 '), formatDateTime(row.createdAt)]),
          row.pickedAt ? h('span', null, [h('b', null, '拾取 '), formatDateTime(row.pickedAt)]) : null,
          (row.status === 'SUCCESS' || row.status === 'FAILED')
            ? h('span', null, [h('b', null, '完成 '), formatDateTime(row.updatedAt)])
            : null,
          row.retryCount ? h('span', null, [h('b', null, '重试 '), `${row.retryCount} 次`]) : null
        ].filter(Boolean))
      ])
  },
  {
    title: '类型',
    key: 'taskType',
    width: 150,
    render: (row) => h('code', { class: 'cell-type' }, row.taskType)
  },
  {
    title: '状态',
    key: 'status',
    width: 110,
    render: (row) =>
      h(NTag, { size: 'small', type: STATUS_TAG[row.status] || 'default', bordered: false },
        { default: () => row.status })
  },
  {
    title: '派发时间',
    key: 'createdAt',
    width: 165,
    render: (row) => h('span', { class: 'cell-time' }, formatDateTime(row.createdAt))
  },
  {
    title: '时长',
    key: 'duration',
    width: 90,
    render: (row) => h('span', { class: 'cell-duration' }, duration(row))
  },
  {
    title: '摘要',
    key: 'summary',
    render: (row) => {
      const s = summary(row)
      const isErr = s.startsWith('❌')
      return h('span', {
        class: isErr ? 'cell-summary cell-summary-err' : 'cell-summary',
        title: row.resultPayload || row.taskPayload || ''
      }, s)
    }
  }
])
</script>

<template>
  <NModal
    :show="open"
    preset="card"
    :title="`任务历史: ${serverName ?? ''}`"
    style="width: 90vw; max-width: 80rem"
    :bordered="false"
    :mask-closable="true"
    @update:show="(v: boolean) => (open = v)"
  >
    <div class="space-y-3">
      <div class="flex items-center gap-2 flex-wrap">
        <NSelect
          v-model:value="query.taskType"
          :options="TASK_TYPE_OPTIONS"
          size="small"
          class="w-40"
          placeholder="类型筛选"
        />
        <NSelect
          v-model:value="query.status"
          :options="STATUS_OPTIONS"
          size="small"
          class="w-32"
          placeholder="状态筛选"
        />
        <span class="text-xs text-zinc-500">点行展开看 payload</span>
        <div class="flex-1"></div>
        <NButton size="small" quaternary :loading="loading" @click="load">
          <template #icon><NIcon><RefreshCcw /></NIcon></template>
          刷新
        </NButton>
      </div>
      <NDataTable
        v-model:expanded-row-keys="expandedRowKeys"
        :columns="columns"
        :data="tasks"
        :loading="loading"
        :bordered="false"
        :row-key="(row: AgentTaskHistoryItem) => row.id"
        :pagination="pagination"
        remote
        size="small"
        :max-height="560"
      />
    </div>

    <template #footer>
      <div class="flex justify-end">
        <NButton size="small" @click="open = false">关闭</NButton>
      </div>
    </template>
  </NModal>
</template>

<style scoped>
.cell-type {
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  color: #475569;
}
html[data-theme='dark'] .cell-type { color: #cbd5e1; }
.cell-time {
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
}
.cell-duration {
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  color: #71717a;
}
.cell-summary {
  font-size: 12px;
}
.cell-summary-err {
  color: #dc2626;
}
html[data-theme='dark'] .cell-summary-err { color: #f87171; }

.task-detail {
  padding: 12px 8px;
  background: rgba(127, 127, 127, 0.04);
  border-radius: 4px;
}
.detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 12px;
}
.detail-label {
  font-size: 11px;
  color: #64748b;
  font-weight: 600;
  margin-bottom: 4px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.detail-code {
  background: rgba(127, 127, 127, 0.06);
  border: 1px solid rgba(127, 127, 127, 0.2);
  border-radius: 4px;
  padding: 8px;
  max-height: 240px;
  overflow: auto;
  font-size: 11px;
}
.detail-empty {
  color: #94a3b8;
  font-size: 12px;
}
.detail-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  font-size: 11px;
  color: #64748b;
  border-top: 1px solid rgba(127, 127, 127, 0.2);
  padding-top: 8px;
}
.detail-meta b {
  color: #475569;
  font-weight: 600;
  margin-right: 4px;
}
.detail-meta code {
  font-family: 'JetBrains Mono', monospace;
}
html[data-theme='dark'] .detail-meta { color: #a1a1aa; }
html[data-theme='dark'] .detail-meta b { color: #d4d4d8; }
</style>
