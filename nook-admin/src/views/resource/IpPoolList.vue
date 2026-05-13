<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import {
  Globe2,
  Pencil,
  Plus,
  RefreshCcw,
  Rocket,
  Search,
  Trash2,
  Undo2,
  Zap
} from 'lucide-vue-next'
import {
  NButton,
  NCard,
  NDataTable,
  NIcon,
  NInput,
  NSelect,
  NTag,
  useMessage,
  type DataTableColumns
} from 'naive-ui'
import { useConfirm } from '@/composables/useConfirm'
import {
  IP_POOL_STATUS_LABELS,
  deleteIpPool,
  pageIpPool,
  releaseIpPool,
  type ResourceIpPool,
  type ResourceIpPoolQuery
} from '@/api/resource/ip-pool'
import { IP_TYPE_CODE_LABELS, listIpTypes, type ResourceIpType } from '@/api/resource/ip-type'
import { formatDateTime } from '@/utils/date'
import IpPoolFormDialog from './IpPoolFormDialog.vue'
import IpPoolDeployDialog from './IpPoolDeployDialog.vue'
import IpPoolTestDialog from './IpPoolTestDialog.vue'

const message = useMessage()
const { confirm } = useConfirm()

// ===== 列表 + 查询 =====
const STATUS_OPTIONS = [
  { label: '全部', value: undefined as number | undefined },
  { label: '可分配', value: 1 },
  { label: '已占用', value: 2 },
  { label: '测试中', value: 3 },
  { label: '黑名单', value: 4 },
  { label: '冷却中', value: 5 },
  { label: '降级', value: 6 }
]

const ipTypes = ref<ResourceIpType[]>([])
const ipTypeOptions = computed(() => [
  { label: '全部类型', value: '' },
  ...ipTypes.value.map((t) => ({ label: t.name + (IP_TYPE_CODE_LABELS[t.code] ? ` (${IP_TYPE_CODE_LABELS[t.code]})` : ''), value: t.id }))
])

const query = reactive<Required<Pick<ResourceIpPoolQuery, 'pageNo' | 'pageSize'>> & ResourceIpPoolQuery>({
  pageNo: 1,
  pageSize: 10,
  keyword: '',
  status: undefined,
  region: '',
  ipTypeId: ''
})
const list = ref<ResourceIpPool[]>([])
const total = ref(0)
const loading = ref(false)

async function loadIpTypes() {
  try {
    ipTypes.value = await listIpTypes()
    if (!ipTypes.value.length) {
      message.warning('IP 类型为空, 请先初始化 resource_ip_type')
    }
  } catch (e) {
    console.error('[ip-pool] 加载 IP 类型失败:', e)
  }
}

async function loadList() {
  loading.value = true
  try {
    const res = await pageIpPool({
      pageNo: query.pageNo,
      pageSize: query.pageSize,
      keyword: query.keyword || undefined,
      status: query.status,
      region: query.region || undefined,
      ipTypeId: query.ipTypeId || undefined
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
  query.status = undefined
  query.region = ''
  query.ipTypeId = ''
  loadList()
}

function onSearch() {
  query.pageNo = 1
  loadList()
}

function ipTypeName(typeId: string) {
  const t = ipTypes.value.find((x) => x.id === typeId)
  if (!t) return typeId
  const label = IP_TYPE_CODE_LABELS[t.code] ?? t.code
  return `${t.name} · ${label}`
}

// 状态 → NTag 颜色映射 (替代 daisy 的 IP_POOL_STATUS_BADGE_CLASS)
function statusTagType(status: number): 'success' | 'info' | 'warning' | 'error' | 'default' {
  switch (status) {
    case 1: return 'success'
    case 2: return 'info'
    case 3: return 'warning'
    case 4: return 'error'
    case 5: return 'warning'
    case 6: return 'default'
    default: return 'default'
  }
}

// ===== 新增 / 编辑 =====
const formOpen = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const formIp = ref<ResourceIpPool | null>(null)
/** 由 DeployDialog 部署成功 → 添加到 IP 池 时预填的字段; 仅 create 时生效。 */
const formSocksPrefill = ref<{
  ipAddress: string
  socks5Port: number
  socks5Username: string
  socks5Password: string
} | null>(null)

function openCreate() {
  formMode.value = 'create'
  formIp.value = null
  formSocksPrefill.value = null
  formOpen.value = true
}

function openEdit(ip: ResourceIpPool) {
  formMode.value = 'edit'
  formIp.value = ip
  formSocksPrefill.value = null
  formOpen.value = true
}

function onFormSaved() {
  loadList()
}

// ===== 删除 =====
async function onDelete(ip: ResourceIpPool) {
  const ok = await confirm({
    title: '删除 IP',
    message: `从池中删除 ${ip.ipAddress}?`,
    type: 'danger',
    confirmText: '删除'
  })
  if (!ok) return
  try {
    await deleteIpPool(ip.id)
    message.success('删除成功')
    loadList()
  } catch {
    /* */
  }
}

// ===== 退订 (occupied → cooling) =====
async function onRelease(ip: ResourceIpPool) {
  const ok = await confirm({
    title: '退订 IP',
    message: `把 ${ip.ipAddress} 置为冷却中?`,
    type: 'warning',
    confirmText: '退订'
  })
  if (!ok) return
  try {
    await releaseIpPool(ip.id)
    message.success('已置冷却')
    loadList()
  } catch {
    /* */
  }
}

// ===== 独立部署 SOCKS5 (顶部按钮触发, 不绑定 IP 行) =====
const deployOpen = ref(false)

function openDeploy() {
  deployOpen.value = true
}

/** 部署成功后用户点 "添加到 IP 池": 把凭据交给 FormDialog 预填走 create 流程。 */
function onAddToPoolFromDeploy(payload: {
  ipAddress: string
  socks5Port: number
  socks5Username: string
  socks5Password: string
}) {
  formMode.value = 'create'
  formIp.value = null
  formSocksPrefill.value = payload
  formOpen.value = true
}

/** SOCKS5 凭据是否齐全, 决定是否能触发"测试"按钮 (拨号需要这些参数)。 */
function canTest(ip: ResourceIpPool): boolean {
  return !!ip.ipAddress
      && !!ip.socks5Port
      && !!ip.socks5Username
      && !!ip.socks5Password
}

// ===== SOCKS5 连通性测试 (走弹框, 让用户自选 echo-IP 端点 + 看完整请求结果) =====
const testOpen = ref(false)
const testTarget = ref<ResourceIpPool | null>(null)

function openTest(ip: ResourceIpPool) {
  testTarget.value = ip
  testOpen.value = true
}

// 行操作直接平铺为一行小按钮; 不再折叠到 dropdown

// ===== 表格列定义 =====
const columns = computed<DataTableColumns<ResourceIpPool>>(() => [
  {
    title: 'IP 地址',
    key: 'ipAddress',
    render: (row) =>
      h('div', { class: 'flex items-center gap-2' }, [
        h(NIcon, { size: 16, depth: 3 }, { default: () => h(Globe2) }),
        h('span', { class: 'font-mono' }, row.ipAddress)
      ])
  },
  { title: '区域', key: 'region', render: (row) => row.region || '-' },
  { title: '类型', key: 'ipTypeId', render: (row) => ipTypeName(row.ipTypeId) },
  {
    title: 'SOCKS5',
    key: 'socks5',
    render: (row) => {
      if (!row.socks5Port) {
        return h('span', { class: 'text-xs text-zinc-400' }, '未部署')
      }
      const children: ReturnType<typeof h>[] = [
        h('span', { class: 'font-mono text-xs' }, [
          row.ipAddress,
          h('span', { class: 'text-zinc-400' }, `:${row.socks5Port}`)
        ])
      ]
      if (row.socks5Username) {
        children.push(
          h('span', { class: 'ml-1 font-mono text-xs text-zinc-500' }, `/ ${row.socks5Username}`)
        )
      }
      children.push(
        row.socks5Password
          ? h(NTag, { size: 'small', type: 'success', class: 'ml-1' }, { default: () => 'pass' })
          : h(NTag, { size: 'small', bordered: true, class: 'ml-1' }, { default: () => 'no-pass' })
      )
      return h('div', { class: 'flex items-center flex-wrap' }, children)
    }
  },
  {
    title: '状态',
    key: 'status',
    render: (row) =>
      h(
        NTag,
        { size: 'small', type: statusTagType(row.status) },
        { default: () => IP_POOL_STATUS_LABELS[row.status] || row.status }
      )
  },
  { title: '分配次数', key: 'assignCount', render: (row) => row.assignCount ?? 0 },
  {
    title: '当前会员',
    key: 'assignedMemberId',
    render: (row) =>
      h('span', { class: 'font-mono text-xs text-zinc-500' }, row.assignedMemberId || '-')
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
    // 4 个按钮按需展示, 宽度按"测试 + 编辑 + 退订 + 删除"取上限
    width: 220,
    render: (row) =>
      h('div', { class: 'flex gap-1 justify-end' }, [
        // 测试: 仅 SOCKS5 凭据齐全时露出, 否则该 IP 无法发起拨号
        canTest(row)
          ? h(
              NButton,
              {
                size: 'tiny',
                quaternary: true,
                type: 'warning',
                onClick: () => openTest(row)
              },
              {
                icon: () => h(NIcon, null, { default: () => h(Zap) }),
                default: () => '测试'
              }
            )
          : null,
        h(
          NButton,
          { size: 'tiny', quaternary: true, onClick: () => openEdit(row) },
          {
            icon: () => h(NIcon, null, { default: () => h(Pencil) }),
            default: () => '编辑'
          }
        ),
        // 退订: 仅"已占用"(status=2) 才有意义, 其它状态隐藏
        row.status === 2
          ? h(
              NButton,
              {
                size: 'tiny',
                quaternary: true,
                onClick: () => onRelease(row)
              },
              {
                icon: () => h(NIcon, null, { default: () => h(Undo2) }),
                default: () => '退订'
              }
            )
          : null,
        h(
          NButton,
          { size: 'tiny', quaternary: true, type: 'error', onClick: () => onDelete(row) },
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

onMounted(async () => {
  await loadIpTypes()
  await loadList()
})
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
            placeholder="IP 地址"
            class="w-48"
            :input-props="{ style: 'font-family: monospace' }"
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
        <div>
          <div class="text-xs text-zinc-500 mb-1">类型</div>
          <NSelect
            v-model:value="query.ipTypeId"
            :options="ipTypeOptions"
            size="small"
            class="w-44"
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
        <NButton
          type="info"
          size="small"
          @click="openDeploy"
          title="远端部署 SOCKS5; 成功后可一键添加到 IP 池"
        >
          <template #icon><NIcon><Rocket /></NIcon></template>
          部署 SOCKS5
        </NButton>
        <NButton type="primary" size="small" @click="openCreate">
          <template #icon><NIcon><Plus /></NIcon></template>
          新增 IP
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
        :row-key="(row: ResourceIpPool) => row.id"
        size="small"
      />
    </NCard>

    <!-- 新增/编辑 弹框: 仅保存 IP 池条目元数据 (SOCKS5 凭据); 部署走顶部 "部署 SOCKS5" 按钮 -->
    <IpPoolFormDialog
      v-model="formOpen"
      :mode="formMode"
      :ip="formIp"
      :ip-types="ipTypes"
      :socks-prefill="formSocksPrefill"
      @saved="onFormSaved"
    />

    <!-- 独立部署 SOCKS5 弹框: 不绑定 IP 行; 成功后 "添加到 IP 池" 走 onAddToPoolFromDeploy 接力 -->
    <IpPoolDeployDialog v-model="deployOpen" @add-to-pool="onAddToPoolFromDeploy" />

    <!-- SOCKS5 测试弹框: 让用户选 echo-IP 端点 + 看完整请求结果 -->
    <IpPoolTestDialog v-model="testOpen" :ip="testTarget" />
  </div>
</template>
