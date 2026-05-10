<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import {
  MoreVertical,
  Pencil,
  Plus,
  RefreshCcw,
  Search,
  Server as ServerIcon,
  Terminal,
  Trash2,
  Zap
} from 'lucide-vue-next'
import {
  NButton,
  NCard,
  NDataTable,
  NDropdown,
  NIcon,
  NInput,
  NSelect,
  NSpace,
  NTag,
  useMessage,
  type DataTableColumns,
  type DropdownOption
} from 'naive-ui'
import { useConfirm } from '@/composables/useConfirm'
import {
  SERVER_STATUS_LABELS,
  deleteServer,
  pageServers,
  type ResourceServer,
  type ResourceServerQuery
} from '@/api/resource/server'
import { testServerConnectivity } from '@/api/xray/server'
import { formatDateTime } from '@/utils/date'
import ServerFormDialog from './ServerFormDialog.vue'
import ServerOpsDialog from './ServerOpsDialog.vue'
import { pageClients } from '@/api/xray/client'

const message = useMessage()
const { confirm } = useConfirm()

// ===== 列表 + 查询 =====
const STATUS_OPTIONS = [
  { label: '全部', value: undefined as number | undefined },
  { label: '运行', value: 1 },
  { label: '维护', value: 2 },
  { label: '下线', value: 3 }
]

const query = reactive<Required<Pick<ResourceServerQuery, 'pageNo' | 'pageSize'>> & ResourceServerQuery>({
  pageNo: 1,
  pageSize: 10,
  keyword: '',
  status: undefined,
  region: ''
})
const list = ref<ResourceServer[]>([])
const total = ref(0)
const loading = ref(false)

async function loadList() {
  loading.value = true
  try {
    const res = await pageServers({
      pageNo: query.pageNo,
      pageSize: query.pageSize,
      keyword: query.keyword || undefined,
      status: query.status,
      region: query.region || undefined
    })
    const maxPage = res.total > 0 ? Math.ceil(res.total / query.pageSize) : 1
    if (query.pageNo > maxPage) {
      query.pageNo = maxPage
      loading.value = false
      await loadList()
      return
    }
    list.value = res.records
    total.value = res.total
    // 刷完列表后异步拉每台 server 的活跃用户数
    loadActiveUserCounts()
  } catch {
    /* request 拦截器已 toast */
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.pageNo = 1
  query.keyword = ''
  query.status = undefined
  query.region = ''
  loadList()
}

function onSearch() {
  query.pageNo = 1
  loadList()
}

function statusTagType(s: number): 'success' | 'warning' | 'error' | 'default' {
  return s === 1 ? 'success' : s === 2 ? 'warning' : 'error'
}

// ===== 新增 / 编辑 =====
const formOpen = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const formServer = ref<ResourceServer | null>(null)

function openCreate() {
  formMode.value = 'create'
  formServer.value = null
  formOpen.value = true
}

function openEdit(s: ResourceServer) {
  formMode.value = 'edit'
  formServer.value = s
  formOpen.value = true
}

async function onFormSaved() {
  await loadList()
}

// ===== 删除 =====
async function onDelete(s: ResourceServer) {
  const ok = await confirm({
    title: '删除服务器',
    message: `确定删除服务器 "${s.name}" 吗？已关联的 IP / inbound 不会自动清理，请先妥善处理。`,
    type: 'danger',
    confirmText: '删除'
  })
  if (!ok) return
  try {
    await deleteServer(s.id)
    message.success('删除成功')
    loadList()
  } catch {
    /* */
  }
}

// ===== 测试连通性 =====
const testing = ref<Record<string, boolean>>({})

async function onTest(s: ResourceServer) {
  testing.value[s.id] = true
  try {
    const res = await testServerConnectivity(s.id)
    if (res.success) {
      message.success(`Xray gRPC 连通 (${res.elapsedMs}ms)`)
    } else {
      message.error(`${res.error || '探活失败'}`)
    }
  } catch {
    /* */
  } finally {
    testing.value[s.id] = false
  }
}

// ===== Xray 运维台 =====
const opsOpen = ref(false)
const opsTarget = ref<ResourceServer | null>(null)

function openOps(s: ResourceServer) {
  opsTarget.value = s
  opsOpen.value = true
}

// ===== 当前活跃用户数(每个 server 一个数字) =====
// 进列表后并发拉,失败不影响主流程,仅显示 "?" / "-"
const activeUserCount = ref<Record<string, number>>({})
async function loadActiveUserCounts() {
  if (!list.value.length) return
  const tasks = list.value.map(async (s) => {
    try {
      const res = await pageClients({ pageNo: 1, pageSize: 1, serverId: s.id, status: 1 })
      activeUserCount.value[s.id] = res.total
    } catch {
      // 忽略个别失败,UI 显示 -
    }
  })
  await Promise.allSettled(tasks)
}

// ===== 行操作菜单（NDropdown 选项 + 分发） =====
const ROW_ACTIONS: DropdownOption[] = [
  {
    label: '测速',
    key: 'test',
    icon: () => h(NIcon, null, { default: () => h(Zap) })
  },
  {
    label: '编辑',
    key: 'edit',
    icon: () => h(NIcon, null, { default: () => h(Pencil) })
  },
  {
    label: '运维',
    key: 'ops',
    icon: () => h(NIcon, null, { default: () => h(Terminal) })
  },
  { type: 'divider', key: 'd1' },
  {
    label: '删除',
    key: 'delete',
    props: { style: 'color: var(--n-error-color)' },
    icon: () => h(NIcon, { color: 'var(--n-error-color)' }, { default: () => h(Trash2) })
  }
]

function onRowAction(key: string | number, s: ResourceServer) {
  if (key === 'test') onTest(s)
  else if (key === 'edit') openEdit(s)
  else if (key === 'ops') openOps(s)
  else if (key === 'delete') onDelete(s)
}

// ===== 表格列定义 =====
const columns = computed<DataTableColumns<ResourceServer>>(() => [
  {
    title: '别名',
    key: 'name',
    render: (row) =>
      h('div', { class: 'flex items-center gap-2 whitespace-nowrap' }, [
        h(NIcon, { depth: 3 }, { default: () => h(ServerIcon) }),
        h('span', { class: 'font-medium' }, row.name)
      ])
  },
  {
    title: '主机',
    key: 'host',
    render: (row) =>
      h('span', { class: 'font-mono text-xs' }, [
        row.host,
        h('span', { class: 'text-zinc-400' }, `:${row.sshPort || 22}`)
      ])
  },
  {
    title: '区域',
    key: 'region',
    render: (row) => row.region || '-'
  },
  {
    title: '带宽',
    key: 'bandwidth',
    render: (row) => (row.totalBandwidth ? `${row.totalBandwidth} Mbps` : '-')
  },
  {
    title: '月流量',
    key: 'monthlyTraffic',
    render: (row) => {
      if (row.monthlyTrafficGb && row.monthlyTrafficGb > 0) {
        return row.monthlyTrafficGb >= 1000
          ? `${(row.monthlyTrafficGb / 1000).toFixed(1)} TB`
          : `${row.monthlyTrafficGb} GB`
      }
      return h('span', { class: 'text-zinc-400' }, '不限')
    }
  },
  {
    title: 'IP 数',
    key: 'totalIpCount',
    render: (row) => row.totalIpCount ?? 0
  },
  {
    title: '状态',
    key: 'status',
    render: (row) =>
      h(
        NTag,
        { size: 'small', type: statusTagType(row.status) },
        { default: () => SERVER_STATUS_LABELS[row.status] || row.status }
      )
  },
  {
    title: 'SSH',
    key: 'ssh',
    render: (row) => {
      const sshConfigured = !!row.sshPassword
      return h(
        NTag,
        {
          size: 'small',
          type: sshConfigured ? 'success' : 'default',
          bordered: !sshConfigured,
          title: sshConfigured ? 'SSH 已配置' : 'SSH 未配置'
        },
        { default: () => 'SSH' }
      )
    }
  },
  {
    title: '活跃用户',
    key: 'activeUsers',
    render: (row) =>
      h(
        'span',
        { class: 'font-mono text-sm' },
        activeUserCount.value[row.id] !== undefined ? String(activeUserCount.value[row.id]) : '-'
      )
  },
  {
    title: '创建时间',
    key: 'createdAt',
    width: 170,
    render: (row) => formatDateTime(row.createdAt)
  },
  {
    title: '操作',
    key: 'actions',
    align: 'right',
    width: 80,
    render: (row) =>
      h(
        NDropdown,
        {
          options: ROW_ACTIONS,
          trigger: 'click',
          onSelect: (key: string | number) => onRowAction(key, row)
        },
        {
          default: () =>
            h(
              NButton,
              { circle: true, quaternary: true, size: 'small' },
              { default: () => h(NIcon, null, { default: () => h(MoreVertical) }) }
            )
        }
      )
  }
])

const pagination = computed(() => ({
  page: query.pageNo,
  pageSize: query.pageSize,
  itemCount: total.value,
  pageSizes: [10, 20, 50],
  showSizePicker: true,
  prefix: ({ itemCount }: { itemCount: number }) => `共 ${itemCount} 条`,
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

onMounted(loadList)
</script>

<template>
  <div class="space-y-4">
    <!-- 顶部搜索栏 -->
    <NCard size="small">
      <div class="flex flex-wrap gap-3 items-end">
        <div>
          <div class="text-xs text-zinc-500 mb-1">关键词</div>
          <NInput
            v-model:value="query.keyword"
            size="small"
            placeholder="别名 / 主机"
            class="w-56"
            @keyup.enter="onSearch"
          />
        </div>
        <div>
          <div class="text-xs text-zinc-500 mb-1">状态</div>
          <NSelect
            v-model:value="query.status"
            :options="STATUS_OPTIONS"
            size="small"
            class="w-28"
          />
        </div>
        <div>
          <div class="text-xs text-zinc-500 mb-1">区域</div>
          <NInput
            v-model:value="query.region"
            size="small"
            placeholder="us-west / jp / ..."
            class="w-32"
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
        <NButton type="primary" size="small" @click="openCreate">
          <template #icon><NIcon><Plus /></NIcon></template>
          新增服务器
        </NButton>
      </div>
    </NCard>

    <!-- 表格 + 分页 -->
    <NCard size="small" :content-style="{ padding: 0 }">
      <NDataTable
        :columns="columns"
        :data="list"
        :loading="loading"
        :pagination="pagination"
        :remote="true"
        :bordered="false"
        :row-key="(row: ResourceServer) => row.id"
        size="small"
      />
    </NCard>

    <!-- 新增/编辑 弹框 -->
    <ServerFormDialog
      v-model="formOpen"
      :mode="formMode"
      :server="formServer"
      @saved="onFormSaved"
    />

    <!-- Xray 运维台 -->
    <ServerOpsDialog v-model="opsOpen" :server="opsTarget" />
  </div>
</template>
