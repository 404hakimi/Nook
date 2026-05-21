<script setup lang="ts">
import { computed, h, ref, watch } from 'vue'
import { RefreshCcw } from 'lucide-vue-next'
import {
  NButton,
  NCode,
  NDataTable,
  NIcon,
  NModal,
  NTag,
  type DataTableColumns
} from 'naive-ui'
import { listAgentTasks, type AgentTaskHistoryItem } from '@/api/agent/agent'
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

async function load() {
  if (!props.serverId) return
  loading.value = true
  try {
    tasks.value = await listAgentTasks(props.serverId, 50)
  } catch { /* */ } finally {
    loading.value = false
  }
}

watch(open, (v) => {
  if (v) load()
  else tasks.value = []
})

const STATUS_TAG: Record<string, 'success' | 'warning' | 'error' | 'info' | 'default'> = {
  SUCCESS: 'success',
  PICKED: 'info',
  PENDING: 'warning',
  FAILED: 'error'
}

const STATUS_ICON: Record<string, string> = {
  SUCCESS: '✅',
  PICKED: '⏳',
  PENDING: '○',
  FAILED: '❌'
}

/** 计算时长 (派发 → 完成); 未完成显示 "-". */
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

/** 把可能是字符串或对象的 payload 转成漂亮的 JSON. */
function prettyJson(s?: string): string {
  if (!s) return ''
  try {
    return JSON.stringify(JSON.parse(s), null, 2)
  } catch {
    return s
  }
}

/** 结果摘要 — 解析常见字段; 失败时显示原文截断. */
function summary(row: AgentTaskHistoryItem): string {
  if (!row.resultPayload) {
    if (row.status === 'PENDING') return '等待 agent 拾取...'
    if (row.status === 'PICKED') return 'agent 已拾取, 处理中...'
    return '—'
  }
  try {
    const r = JSON.parse(row.resultPayload)
    // 各类型友好摘要
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
                : h('span', { class: 'text-zinc-400' }, '无')
            ])
          ]),
          h('div', null, [
            h('div', { class: 'detail-label' }, '结果 payload (agent → admin)'),
            h('div', { class: 'detail-code' }, [
              row.resultPayload
                ? h(NCode, { code: prettyJson(row.resultPayload), language: 'json', wordWrap: true })
                : h('span', { class: 'text-zinc-400' }, '尚未上报')
            ])
          ])
        ]),
        h('div', { class: 'detail-meta' }, [
          h('span', null, [h('b', null, 'Task ID: '), h('code', { class: 'font-mono' }, row.id)]),
          h('span', null, [h('b', null, '派发: '), formatDateTime(row.createdAt)]),
          row.pickedAt ? h('span', null, [h('b', null, 'Agent 拾取: '), formatDateTime(row.pickedAt)]) : null,
          (row.status === 'SUCCESS' || row.status === 'FAILED')
            ? h('span', null, [h('b', null, '完成: '), formatDateTime(row.updatedAt)])
            : null,
          row.retryCount ? h('span', null, [h('b', null, '重试: '), `${row.retryCount} 次`]) : null
        ].filter(Boolean))
      ])
  },
  {
    title: '类型',
    key: 'taskType',
    width: 150,
    render: (row) => h('code', { class: 'font-mono text-xs' }, row.taskType)
  },
  {
    title: '状态',
    key: 'status',
    width: 100,
    render: (row) =>
      h(NTag, { size: 'small', type: STATUS_TAG[row.status] || 'default' },
        { default: () => `${STATUS_ICON[row.status] || ''} ${row.status}` })
  },
  {
    title: '派发时间',
    key: 'createdAt',
    width: 160,
    render: (row) => h('span', { class: 'font-mono text-xs' }, formatDateTime(row.createdAt))
  },
  {
    title: '时长',
    key: 'duration',
    width: 80,
    render: (row) => h('span', { class: 'font-mono text-xs text-zinc-500' }, duration(row))
  },
  {
    title: '摘要',
    key: 'summary',
    render: (row) => {
      const s = summary(row)
      const isErr = s.startsWith('❌')
      return h('span', {
        class: isErr ? 'text-xs text-red-600' : 'text-xs',
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
    :title="`任务历史: ${serverName ?? ''} (最近 50 条; 点行展开看 payload)`"
    style="width: 90vw; max-width: 80rem"
    :bordered="false"
    :mask-closable="true"
    @update:show="(v: boolean) => (open = v)"
  >
    <div class="space-y-3">
      <div class="flex items-center gap-2">
        <span class="text-xs text-zinc-500">
          含: agent_upgrade / config_reload / truncate_log / xray_* / ping
        </span>
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
        size="small"
        :max-height="600"
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
.task-detail {
  padding: 12px 8px;
  background: rgba(0, 0, 0, 0.02);
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
}
.detail-code {
  background: var(--n-color);
  border: 1px solid var(--n-border-color);
  border-radius: 4px;
  padding: 8px;
  max-height: 240px;
  overflow: auto;
  font-size: 11px;
}
.detail-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  font-size: 11px;
  color: #64748b;
  border-top: 1px solid var(--n-border-color);
  padding-top: 8px;
}
.detail-meta b {
  color: #475569;
  font-weight: 600;
}
</style>
