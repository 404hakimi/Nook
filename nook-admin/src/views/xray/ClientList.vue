<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import {
  Activity,
  MoreVertical,
  Pencil,
  Plus,
  RefreshCcw,
  RefreshCw,
  RotateCw,
  Search,
  Share2,
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
  type DataTableColumns,
  type DropdownOption
} from 'naive-ui'
import { useConfirm } from '@/composables/useConfirm'
import {
  CLIENT_STATUS_LABELS,
  pageClients,
  resetClientTraffic,
  revokeClient,
  rotateClient,
  syncClient,
  type XrayClient,
  type XrayClientQuery
} from '@/api/xray/client'
import { pageServers, type ResourceServer } from '@/api/resource/server'
import { formatDateTime } from '@/utils/date'
import ClientEditDialog from './ClientEditDialog.vue'
import ClientProvisionDialog from './ClientProvisionDialog.vue'
import ClientShareDialog from './ClientShareDialog.vue'
import ClientTrafficDialog from './ClientTrafficDialog.vue'
import IpPoolDetailDialog from '@/views/resource/IpPoolDetailDialog.vue'

const message = useMessage()
const { confirm } = useConfirm()

const STATUS_OPTIONS = [
  { label: '全部', value: undefined as number | undefined },
  { label: '运行', value: 1 },
  { label: '已停', value: 2 },
  { label: '待同步', value: 3 },
  { label: '远端缺失', value: 4 }
]

const query = reactive<Required<Pick<XrayClientQuery, 'pageNo' | 'pageSize'>> & XrayClientQuery>({
  pageNo: 1,
  pageSize: 10,
  keyword: '',
  serverId: '',
  memberUserId: '',
  ipId: '',
  status: undefined
})
const list = ref<XrayClient[]>([])
const total = ref(0)
const loading = ref(false)
const advancedOpen = ref(false)

// serverId → 服务器名/host 缓存；进入页面时拉一次，便于把 inbound 行里的裸 UUID 翻译成可读
const serverMap = ref<Record<string, ResourceServer>>({})
async function loadServerMap() {
  try {
    // 服务器总数有限，一把拉 200 条够用
    const res = await pageServers({ pageNo: 1, pageSize: 200 })
    serverMap.value = Object.fromEntries(res.records.map((s) => [s.id, s]))
  } catch {
    /* 拉不到不影响主流程，列表会回落到显示原始 ID */
  }
}
function serverLabel(id: string): string {
  const s = serverMap.value[id]
  return s ? s.name : id.slice(0, 8) + '…'
}
function serverHost(id: string): string {
  return serverMap.value[id]?.host ?? ''
}

async function loadList() {
  loading.value = true
  try {
    const res = await pageClients({
      pageNo: query.pageNo,
      pageSize: query.pageSize,
      keyword: query.keyword || undefined,
      serverId: query.serverId || undefined,
      memberUserId: query.memberUserId || undefined,
      ipId: query.ipId || undefined,
      status: query.status
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
    /* */
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.pageNo = 1
  query.keyword = ''
  query.serverId = ''
  query.memberUserId = ''
  query.ipId = ''
  query.status = undefined
  loadList()
}
function onSearch() {
  query.pageNo = 1
  loadList()
}

// 状态码 → NTag type 映射
function statusType(s: number): 'success' | 'warning' | 'error' | 'info' | 'default' {
  if (s === 1) return 'success'
  if (s === 2) return 'default'
  if (s === 3) return 'warning'
  if (s === 4) return 'error'
  return 'info'
}

// ===== Provision =====
const provisionOpen = ref(false)
async function onProvisioned() {
  await loadList()
}

// 行级操作 in-flight 标记；防止用户狂点同一行触发多次请求
const busy = ref<Record<string, boolean>>({})

// ===== 删除 (revoke) =====
async function onRevoke(e: XrayClient) {
  if (busy.value[e.id]) return
  const ok = await confirm({
    title: '吊销客户端',
    message: `确定吊销 ${e.clientEmail}？\n远端 client 会被删除，DB 软删，会员将立即断开。`,
    type: 'danger',
    confirmText: '吊销'
  })
  if (!ok) return
  busy.value[e.id] = true
  try {
    await revokeClient(e.id)
    message.success('已吊销')
    loadList()
  } catch { /* */ } finally {
    busy.value[e.id] = false
  }
}

// ===== 轮换 UUID (rotate) =====
async function onRotate(e: XrayClient) {
  if (busy.value[e.id]) return
  const ok = await confirm({
    title: '轮换密钥',
    message: `重新生成 ${e.clientEmail} 的 UUID/密钥；旧客户端配置立即失效需要更新。`,
    type: 'warning',
    confirmText: '轮换'
  })
  if (!ok) return
  busy.value[e.id] = true
  try {
    await rotateClient(e.id)
    message.success('已轮换')
    loadList()
  } catch { /* */ } finally {
    busy.value[e.id] = false
  }
}

// ===== 流量清零 =====
async function onResetTraffic(e: XrayClient) {
  if (busy.value[e.id]) return
  const ok = await confirm({
    title: '清零流量',
    message: `清零 ${e.clientEmail} 的累计上下行计数？`,
    type: 'warning',
    confirmText: '清零'
  })
  if (!ok) return
  busy.value[e.id] = true
  try {
    await resetClientTraffic(e.id)
    message.success('已清零')
  } catch { /* */ } finally {
    busy.value[e.id] = false
  }
}

// ===== 同步到远端 (reconciler 入口); 幂等 = 远端有就先删再加, 用 DB 现有 UUID =====
async function onSync(e: XrayClient) {
  if (busy.value[e.id]) return
  const ok = await confirm({
    title: '同步到远端',
    message: `把 ${e.clientEmail} 按 DB 状态推到远端 xray? 客户连接会断 1-2 秒重连.`,
    type: 'warning',
    confirmText: '同步'
  })
  if (!ok) return
  busy.value[e.id] = true
  try {
    await syncClient(e.id)
    message.success('已同步')
    loadList()
  } catch { /* */ } finally {
    busy.value[e.id] = false
  }
}

// ===== 看流量 =====
const trafficOpen = ref(false)
const trafficTarget = ref<XrayClient | null>(null)
function openTraffic(e: XrayClient) {
  trafficTarget.value = e
  trafficOpen.value = true
}

// ===== 分享 (生成订阅链接给会员客户端导入) =====
const shareOpen = ref(false)
const shareTarget = ref<XrayClient | null>(null)
function openShare(e: XrayClient) {
  shareTarget.value = e
  shareOpen.value = true
}

// ===== 编辑（本地元数据：listenIp/Port/transport/status） =====
const editOpen = ref(false)
const editTarget = ref<XrayClient | null>(null)
function openEdit(e: XrayClient) {
  editTarget.value = e
  editOpen.value = true
}
async function onEdited() {
  await loadList()
}

// ===== IP 详情弹框 (点 IP 列触发, 仅展示, 不在这里改 IP) =====
const ipDetailOpen = ref(false)
const ipDetailId = ref<string>('')
function openIpDetail(ipId: string) {
  // 兼容老数据 / IP 已删的行: 没有 ipId 不弹, 列已经显示空, 点了也无意义
  if (!ipId) return
  ipDetailId.value = ipId
  ipDetailOpen.value = true
}

// ===== 行操作菜单 =====
const ROW_ACTIONS: DropdownOption[] = [
  {
    label: '分享',
    key: 'share',
    icon: () => h(NIcon, null, { default: () => h(Share2) })
  },
  {
    label: '流量',
    key: 'traffic',
    icon: () => h(NIcon, null, { default: () => h(Activity) })
  },
  {
    label: '编辑',
    key: 'edit',
    icon: () => h(NIcon, null, { default: () => h(Pencil) })
  },
  { type: 'divider', key: 'd1' },
  {
    label: '同步到远端',
    key: 'sync',
    icon: () => h(NIcon, null, { default: () => h(RefreshCw) })
  },
  {
    label: '轮换密钥',
    key: 'rotate',
    icon: () => h(NIcon, null, { default: () => h(RotateCw) })
  },
  {
    label: '清零流量',
    key: 'reset-traffic',
    icon: () => h(NIcon, null, { default: () => h(Zap) })
  },
  { type: 'divider', key: 'd2' },
  {
    label: '吊销',
    key: 'revoke',
    props: { style: 'color: var(--n-error-color)' },
    icon: () => h(NIcon, { color: 'var(--n-error-color)' }, { default: () => h(Trash2) })
  }
]

function onRowAction(key: string | number, row: XrayClient) {
  if (key === 'share') openShare(row)
  else if (key === 'traffic') openTraffic(row)
  else if (key === 'edit') openEdit(row)
  else if (key === 'sync') onSync(row)
  else if (key === 'rotate') onRotate(row)
  else if (key === 'reset-traffic') onResetTraffic(row)
  else if (key === 'revoke') onRevoke(row)
}

// ===== 表格列定义 =====
const columns = computed<DataTableColumns<XrayClient>>(() => [
  {
    title: 'Client Email',
    key: 'clientEmail',
    render: (row) => h('span', { class: 'font-mono text-xs' }, row.clientEmail)
  },
  {
    title: '协议',
    key: 'protocol',
    width: 90,
    render: (row) => h(NTag, { size: 'small', bordered: true }, { default: () => row.protocol })
  },
  {
    title: '服务器',
    key: 'serverId',
    render: (row) =>
      h('div', { class: 'flex flex-col' }, [
        h('span', { class: 'text-sm', title: row.serverId }, serverLabel(row.serverId)),
        serverHost(row.serverId)
          ? h('span', { class: 'font-mono text-xs text-zinc-500' }, serverHost(row.serverId))
          : null
      ])
  },
  {
    title: 'Inbound 引用',
    key: 'externalInboundRef',
    render: (row) => h('span', { class: 'font-mono text-xs' }, row.externalInboundRef)
  },
  {
    title: '会员 ID',
    key: 'memberUserId',
    render: (row) => h('span', { class: 'font-mono text-xs' }, row.memberUserId)
  },
  {
    title: '落地 IP',
    key: 'ipAddress',
    render: (row) => {
      // 后端 enrich 失败 (IP 已删 / 异常) 时回落显示 ipId 截断, 鼠标悬停看完整 id, 仍可点开看详情
      const display = row.ipAddress || (row.ipId ? row.ipId.slice(0, 8) + '…' : '-')
      return h(
        NButton,
        {
          text: true,
          type: 'primary',
          size: 'small',
          disabled: !row.ipId,
          title: row.ipAddress ? row.ipId : '点击查看 IP 详情',
          onClick: () => openIpDetail(row.ipId)
        },
        { default: () => h('span', { class: 'font-mono text-xs' }, display) }
      )
    }
  },
  {
    title: '状态',
    key: 'status',
    width: 100,
    render: (row) =>
      h(
        NTag,
        { size: 'small', type: statusType(row.status) },
        { default: () => CLIENT_STATUS_LABELS[row.status] || row.status }
      )
  },
  {
    title: '最近同步',
    key: 'lastSyncedAt',
    width: 170,
    render: (row) =>
      h(
        'span',
        { class: 'text-sm text-zinc-600 dark:text-zinc-400 whitespace-nowrap' },
        formatDateTime(row.lastSyncedAt)
      )
  },
  {
    title: '创建时间',
    key: 'createdAt',
    width: 170,
    render: (row) =>
      h(
        'span',
        { class: 'text-sm text-zinc-600 dark:text-zinc-400 whitespace-nowrap' },
        formatDateTime(row.createdAt)
      )
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
              { circle: true, quaternary: true, size: 'small', disabled: busy.value[row.id] },
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

const advancedFilterCount = computed(() => {
  let n = 0
  if (query.serverId) n++
  if (query.memberUserId) n++
  if (query.ipId) n++
  return n
})

onMounted(() => {
  // 并发拉服务器映射 + inbound 列表，二者无依赖
  loadServerMap()
  loadList()
})
</script>

<template>
  <div class="space-y-4">
    <!-- 顶部搜索 -->
    <NCard size="small">
      <!-- 主筛选行：日常 90% 场景够用 -->
      <div class="flex flex-wrap gap-3 items-end">
        <div>
          <div class="text-xs text-zinc-500 mb-1">关键词</div>
          <NInput
            v-model:value="query.keyword"
            size="small"
            placeholder="client email"
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
        <NButton type="primary" size="small" @click="onSearch">
          <template #icon><NIcon><Search /></NIcon></template>
          搜索
        </NButton>
        <NButton quaternary size="small" @click="resetQuery">
          <template #icon><NIcon><RefreshCcw /></NIcon></template>
          重置
        </NButton>
        <NButton quaternary size="small" @click="advancedOpen = !advancedOpen">
          高级筛选
          <NTag
            v-if="advancedFilterCount > 0"
            size="small"
            type="primary"
            class="ml-2"
          >
            {{ advancedFilterCount }}
          </NTag>
        </NButton>
        <div class="flex-1"></div>
        <NButton type="primary" size="small" @click="provisionOpen = true">
          <template #icon><NIcon><Plus /></NIcon></template>
          手动 Provision
        </NButton>
      </div>

      <!-- 高级筛选：默认折叠；按 ID 精确定位时才展开 -->
      <div v-if="advancedOpen" class="flex flex-wrap gap-3 items-end pt-3 mt-3 border-t border-zinc-200 dark:border-zinc-700">
        <div>
          <div class="text-xs text-zinc-500 mb-1">服务器 ID</div>
          <NInput
            v-model:value="query.serverId"
            size="small"
            class="w-72"
            :input-props="{ style: 'font-family: monospace' }"
            @keyup.enter="onSearch"
          />
        </div>
        <div>
          <div class="text-xs text-zinc-500 mb-1">会员 ID</div>
          <NInput
            v-model:value="query.memberUserId"
            size="small"
            class="w-72"
            :input-props="{ style: 'font-family: monospace' }"
            @keyup.enter="onSearch"
          />
        </div>
        <div>
          <div class="text-xs text-zinc-500 mb-1">IP ID</div>
          <NInput
            v-model:value="query.ipId"
            size="small"
            class="w-72"
            :input-props="{ style: 'font-family: monospace' }"
            @keyup.enter="onSearch"
          />
        </div>
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
        :row-key="(row: XrayClient) => row.id"
        size="small"
      />
    </NCard>

    <ClientProvisionDialog v-model="provisionOpen" @saved="onProvisioned" />
    <ClientTrafficDialog v-model="trafficOpen" :inbound="trafficTarget" />
    <ClientEditDialog
      v-model="editOpen"
      :inbound="editTarget"
      :server-map="serverMap"
      @saved="onEdited"
    />
    <!-- ShareDialog 自己拉 reveal 接口拿明文凭据, 不依赖父组件传 serverMap -->
    <ClientShareDialog v-model="shareOpen" :client="shareTarget" />
    <!-- 点击列表 IP 列触发: 只读详情, 不带改 IP 入口 (改 IP 走 IP 池管理页) -->
    <IpPoolDetailDialog v-model="ipDetailOpen" :ip-id="ipDetailId" />
  </div>
</template>
