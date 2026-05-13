<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import { Plus, Pencil, Trash2, RefreshCcw } from 'lucide-vue-next'
import {
  NButton,
  NCard,
  NDataTable,
  NIcon,
  NTag,
  useMessage,
  type DataTableColumns
} from 'naive-ui'
import { useConfirm } from '@/composables/useConfirm'
import {
  deleteOpConfig,
  listOpConfig,
  type OpConfig
} from '@/api/operation/op-config'
import { useOpConfigStore } from '@/stores/opConfig'
import { formatDateTime } from '@/utils/date'
import OpConfigEditDialog from './OpConfigEditDialog.vue'

const message = useMessage()
const { confirm } = useConfirm()
const opConfigStore = useOpConfigStore()

// 本页用全字段, 自己拉; 跨页面共享的 label 走 store (精简数据)
const list = ref<OpConfig[]>([])
const loading = ref(false)

async function loadList() {
  loading.value = true
  try {
    list.value = await listOpConfig()
    // 同时通知 store 重拉, 让 OpLog 等共享 label 的页面拿到最新中文名
    await opConfigStore.reload()
  } catch {
    /* request 拦截器已 toast */
  } finally {
    loading.value = false
  }
}

// ===== 编辑/创建弹框 =====
const editOpen = ref(false)
const editing = ref<OpConfig | null>(null)

function openCreate() {
  editing.value = null
  editOpen.value = true
}

function openEdit(row: OpConfig) {
  editing.value = row
  editOpen.value = true
}

// ===== 删除 =====
async function onDelete(row: OpConfig) {
  const ok = await confirm({
    title: '删除配置',
    message: `删除 ${row.name} 的配置?`,
    type: 'danger',
    confirmText: '删除',
    cancelText: '取消'
  })
  if (!ok) return
  try {
    await deleteOpConfig(row.id)
    message.success('已删除')
    loadList()
  } catch {
    /* */
  }
}

const columns = computed<DataTableColumns<OpConfig>>(() => [
  {
    title: '操作类型',
    key: 'opType',
    width: 200,
    render: (row) =>
      h('div', { class: 'flex flex-col gap-0.5' }, [
        h('span', { class: 'font-medium' }, row.name),
        h('span', { class: 'font-mono text-xs text-zinc-500' }, row.opType)
      ])
  },
  {
    title: '执行超时',
    key: 'execTimeoutSeconds',
    width: 110,
    render: (row) => h('span', { class: 'font-mono text-xs' }, `${row.execTimeoutSeconds}s`)
  },
  {
    title: '等待超时',
    key: 'waitTimeoutSeconds',
    width: 110,
    render: (row) => h('span', { class: 'font-mono text-xs' }, `${row.waitTimeoutSeconds}s`)
  },
  {
    title: '重试',
    key: 'maxRetry',
    width: 70,
    render: (row) => h('span', { class: 'font-mono text-xs' }, String(row.maxRetry ?? 0))
  },
  {
    title: '状态',
    key: 'enabled',
    width: 90,
    render: (row) =>
      row.enabled
        ? h(NTag, { size: 'small', type: 'success' }, { default: () => '启用' })
        : h(NTag, { size: 'small', type: 'error' }, { default: () => '停用' })
  },
  {
    title: '备注',
    key: 'description',
    render: (row) =>
      h(
        'span',
        { class: 'text-xs text-zinc-500', title: row.description || '' },
        row.description || '-'
      )
  },
  {
    title: '更新时间',
    key: 'updatedAt',
    width: 150,
    render: (row) =>
      h(
        'span',
        { class: 'text-xs text-zinc-500' },
        row.updatedAt ? formatDateTime(row.updatedAt) : '-'
      )
  },
  {
    title: '操作',
    key: 'actions',
    align: 'right',
    width: 160,
    render: (row) =>
      h('div', { class: 'flex gap-1 justify-end' }, [
        h(
          NButton,
          { size: 'tiny', quaternary: true, onClick: () => openEdit(row) },
          {
            icon: () => h(NIcon, null, { default: () => h(Pencil) }),
            default: () => '编辑'
          }
        ),
        h(
          NButton,
          {
            size: 'tiny',
            quaternary: true,
            type: 'error',
            onClick: () => onDelete(row)
          },
          {
            icon: () => h(NIcon, null, { default: () => h(Trash2) }),
            default: () => '删除'
          }
        )
      ])
  }
])

onMounted(loadList)
</script>

<template>
  <div class="space-y-4">
    <NCard size="small">
      <div class="flex items-end gap-2">
        <div>
          <h2 class="text-base font-semibold mb-0.5">Op 调度配置</h2>
          <div class="text-xs text-zinc-500">
            按 OpType 配置执行 / 等待超时 + 启停; 留空走 yml 兜底, 改动立即生效无需重启
          </div>
        </div>
        <div class="flex-1"></div>
        <NButton type="primary" size="small" @click="openCreate">
          <template #icon><NIcon><Plus /></NIcon></template>
          新建配置
        </NButton>
        <NButton quaternary size="small" @click="loadList">
          <template #icon><NIcon><RefreshCcw /></NIcon></template>
          刷新
        </NButton>
      </div>
    </NCard>

    <NCard size="small" :content-style="{ padding: 0 }">
      <NDataTable
        :columns="columns"
        :data="list"
        :loading="loading"
        :bordered="false"
        :row-key="(row: OpConfig) => row.id"
        size="small"
      />
    </NCard>

    <OpConfigEditDialog v-model="editOpen" :record="editing" @saved="loadList" />
  </div>
</template>
