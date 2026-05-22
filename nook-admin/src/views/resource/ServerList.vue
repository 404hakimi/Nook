<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import {
  ArrowRightLeft,
  Pencil,
  Plus,
  RefreshCcw,
  Search,
  Server as ServerIcon,
  Settings2,
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
  NTag,
  useMessage,
  type DataTableColumns
} from 'naive-ui'
import { useConfirm } from '@/composables/useConfirm'
import {
  SERVER_LIFECYCLE_LABELS,
  SERVER_LIFECYCLE_OPTIONS,
  SERVER_LIFECYCLE_TAG_TYPE,
  deleteServer,
  pageServers,
  transitionServerLifecycle,
  type ResourceServer,
  type ResourceServerQuery
} from '@/api/resource/server'
import { listEnabledRegions, type ResourceRegion } from '@/api/resource/region'
import { testServerConnectivity } from '@/api/xray/server'
import { formatDateTime } from '@/utils/date'
import ServerFormDialog from './ServerFormDialog.vue'
import ServerOpsDialog from './ServerOpsDialog.vue'
import ServerOsTuneDialog from './ServerOsTuneDialog.vue'

const message = useMessage()
const { confirm } = useConfirm()

const query = reactive<Required<Pick<ResourceServerQuery, 'pageNo' | 'pageSize'>> & ResourceServerQuery>({
  pageNo: 1,
  pageSize: 10,
  keyword: '',
  lifecycleState: undefined,
  region: ''
})
const list = ref<ResourceServer[]>([])
const total = ref(0)
const loading = ref(false)

// 区域字典 (表单 + 列表过滤共用)
const regions = ref<ResourceRegion[]>([])
const regionOptions = computed(() => [
  { label: '全部', value: '' },
  ...regions.value.map((r) => ({ label: `${r.flagEmoji || ''} ${r.displayName}`, value: r.code }))
])

async function loadRegions() {
  try {
    regions.value = await listEnabledRegions()
  } catch {
    /* */
  }
}

async function loadList() {
  loading.value = true
  try {
    const res = await pageServers({
      pageNo: query.pageNo,
      pageSize: query.pageSize,
      keyword: query.keyword || undefined,
      lifecycleState: query.lifecycleState,
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
  } catch {
    /* request 拦截器已 toast */
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.pageNo = 1
  query.keyword = ''
  query.lifecycleState = undefined
  query.region = ''
  loadList()
}

function onSearch() {
  query.pageNo = 1
  loadList()
}

function regionDisplay(code?: string): string {
  if (!code) return '-'
  const r = regions.value.find((x) => x.code === code)
  return r ? `${r.flagEmoji || ''} ${r.displayName}` : code
}

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

async function onDelete(s: ResourceServer) {
  const ok = await confirm({
    title: '删除服务器',
    message: `删除服务器 "${s.name}"?`,
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

const opsOpen = ref(false)
const opsTarget = ref<ResourceServer | null>(null)

function openOps(s: ResourceServer) {
  opsTarget.value = s
  opsOpen.value = true
}

const osTuneOpen = ref(false)
const osTuneTarget = ref<ResourceServer | null>(null)

function openOsTune(s: ResourceServer) {
  osTuneTarget.value = s
  osTuneOpen.value = true
}


// ===== lifecycle 流转 =====
const LIFECYCLE_DROPDOWN_OPTIONS = [
  { label: '装机中 INSTALLING', key: 'INSTALLING' },
  { label: '待上线 READY', key: 'READY' },
  { label: '运行中 LIVE', key: 'LIVE' },
  { label: '已退役 RETIRED', key: 'RETIRED' }
]

async function onLifecycleSelect(s: ResourceServer, target: string) {
  if (s.lifecycleState === target) return
  const targetLabel = SERVER_LIFECYCLE_LABELS[target] || target
  const ok = await confirm({
    title: '切换生命周期',
    message: `把服务器 "${s.name}" 从 ${SERVER_LIFECYCLE_LABELS[s.lifecycleState]} 切到 ${targetLabel}?`,
    confirmText: '切换'
  })
  if (!ok) return
  try {
    await transitionServerLifecycle(s.id, target)
    message.success(`已切换到 ${targetLabel}`)
    loadList()
  } catch {
    /* */
  }
}

const columns = computed<DataTableColumns<ResourceServer>>(() => [
  {
    title: 'IDC',
    key: 'idcProvider',
    width: 110,
    render: (row) =>
      row.idcProvider
        ? h('span', null, row.idcProvider)
        : h('span', { class: 'text-zinc-400' }, '-')
  },
  {
    title: '区域',
    key: 'region',
    width: 160,
    render: (row) => h('span', null, regionDisplay(row.region))
  },
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
    title: '域名',
    key: 'domain',
    render: (row) =>
      row.domain
        ? h('span', { class: 'font-mono text-xs' }, row.domain)
        : h('span', { class: 'text-zinc-400' }, '-')
  },
  {
    title: '带宽',
    key: 'bandwidthMbps',
    width: 90,
    render: (row) => (row.bandwidthMbps ? `${row.bandwidthMbps} Mbps` : '-')
  },
  {
    title: '客户数上限',
    key: 'maxConcurrentClients',
    width: 100,
    render: (row) => (row.maxConcurrentClients ?? '-')
  },
  {
    title: '生命周期',
    key: 'lifecycleState',
    width: 100,
    render: (row) =>
      h(
        NTag,
        {
          size: 'small',
          type: SERVER_LIFECYCLE_TAG_TYPE[row.lifecycleState] || 'default'
        },
        { default: () => SERVER_LIFECYCLE_LABELS[row.lifecycleState] || row.lifecycleState }
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
    width: 360,
    render: (row) =>
      h('div', { class: 'flex gap-1 justify-end flex-nowrap' }, [
        h(
          NButton,
          {
            size: 'tiny',
            quaternary: true,
            type: 'warning',
            loading: !!testing.value[row.id],
            disabled: !!testing.value[row.id],
            onClick: () => onTest(row),
            title: 'SSH 探活 + Xray gRPC 测速'
          },
          {
            icon: () => h(NIcon, null, { default: () => h(Zap) }),
            default: () => '测速'
          }
        ),
        h(
          NButton,
          {
            size: 'tiny',
            quaternary: true,
            onClick: () => openEdit(row),
            title: '编辑服务器元数据'
          },
          {
            icon: () => h(NIcon, null, { default: () => h(Pencil) }),
            default: () => '编辑'
          }
        ),
        h(
          NDropdown,
          {
            trigger: 'click',
            options: LIFECYCLE_DROPDOWN_OPTIONS,
            onSelect: (key: string) => onLifecycleSelect(row, key)
          },
          {
            default: () =>
              h(
                NButton,
                {
                  size: 'tiny',
                  quaternary: true,
                  type: 'info',
                  title: '切换 lifecycle (INSTALLING/READY/LIVE/RETIRED)'
                },
                {
                  icon: () => h(NIcon, null, { default: () => h(ArrowRightLeft) }),
                  default: () => '流转'
                }
              )
          }
        ),
        h(
          NButton,
          {
            size: 'tiny',
            quaternary: true,
            onClick: () => openOps(row),
            title: '查看服务器系统信息 (hostname / 内存 / 磁盘 等)'
          },
          {
            icon: () => h(NIcon, null, { default: () => h(Terminal) }),
            default: () => '服务器信息'
          }
        ),
        h(
          NButton,
          {
            size: 'tiny',
            quaternary: true,
            onClick: () => openOsTune(row),
            title: '调优 OS 内核 (BBR / swap)'
          },
          {
            icon: () => h(NIcon, null, { default: () => h(Settings2) }),
            default: () => 'OS 调优'
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

const pagination = computed(() => ({
  page: query.pageNo,
  pageSize: query.pageSize,
  itemCount: total.value,
  pageSizes: [10, 20, 50],
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

onMounted(() => {
  loadRegions()
  loadList()
})
</script>

<template>
  <div class="space-y-4">
    <NCard size="small">
      <div class="flex flex-wrap gap-3 items-end">
        <div>
          <div class="text-xs text-zinc-500 mb-1">关键词</div>
          <NInput
            v-model:value="query.keyword"
            size="small"
            placeholder="别名 / 主机 / 域名"
            class="w-56"
            @keyup.enter="onSearch"
          />
        </div>
        <div>
          <div class="text-xs text-zinc-500 mb-1">生命周期</div>
          <NSelect
            v-model:value="query.lifecycleState"
            :options="SERVER_LIFECYCLE_OPTIONS"
            size="small"
            class="w-32"
          />
        </div>
        <div>
          <div class="text-xs text-zinc-500 mb-1">区域</div>
          <NSelect
            v-model:value="query.region"
            :options="regionOptions"
            size="small"
            class="w-40"
            placeholder="选区域"
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

    <ServerFormDialog
      v-model="formOpen"
      :mode="formMode"
      :server="formServer"
      @saved="onFormSaved"
    />

    <ServerOpsDialog v-model="opsOpen" :server="opsTarget" />
    <ServerOsTuneDialog v-model="osTuneOpen" :server="osTuneTarget" />
  </div>
</template>
