<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import {
  ArrowRightLeft,
  Copy,
  Eye,
  FileText,
  Globe2,
  KeyRound,
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
  NDropdown,
  NIcon,
  NInput,
  NSelect,
  NTag,
  NTooltip,
  useMessage,
  type DataTableColumns
} from 'naive-ui'
import { useConfirm } from '@/composables/useConfirm'
import {
  IP_POOL_LIFECYCLE_LABELS,
  IP_POOL_LIFECYCLE_OPTIONS,
  IP_POOL_LIFECYCLE_TAG_TYPE,
  IP_POOL_STATUS_LABELS,
  IP_POOL_STATUS_OPTIONS,
  deleteIpPool,
  pageIpPool,
  releaseIpPool,
  transitionIpPoolLifecycle,
  type ResourceIpPool,
  type ResourceIpPoolQuery
} from '@/api/resource/ip-pool'
import { listEnabledRegions, type ResourceRegion } from '@/api/resource/region'
import { IP_TYPE_CODE_LABELS, listIpTypes, type ResourceIpType } from '@/api/resource/ip-type'
import { formatDateTime } from '@/utils/date'
import IpPoolFormDialog from './IpPoolFormDialog.vue'
import IpPoolDeployDialog from './IpPoolDeployDialog.vue'
import IpPoolCreateChoiceDialog from './IpPoolCreateChoiceDialog.vue'
import IpPoolTestDialog from './IpPoolTestDialog.vue'
import IpPoolSyncCredsDialog from './IpPoolSyncCredsDialog.vue'
import IpPoolStatusDialog from './IpPoolStatusDialog.vue'
import IpPoolLogDialog from './IpPoolLogDialog.vue'
import IpPoolCoreEditDialog from './dialogs/IpPoolCoreEditDialog.vue'
import IpPoolCredentialEditDialog from './dialogs/IpPoolCredentialEditDialog.vue'
import IpPoolBillingEditDialog from './dialogs/IpPoolBillingEditDialog.vue'
import IpPoolSocks5EditDialog from './dialogs/IpPoolSocks5EditDialog.vue'
import AgentProvisionDialog from '@/views/agent/AgentProvisionDialog.vue'

const message = useMessage()
const { confirm } = useConfirm()

const ipTypes = ref<ResourceIpType[]>([])
const ipTypeOptions = computed(() => [
  { label: '全部类型', value: '' },
  ...ipTypes.value.map((t) => ({ label: t.name + (IP_TYPE_CODE_LABELS[t.code] ? ` (${IP_TYPE_CODE_LABELS[t.code]})` : ''), value: t.id }))
])

const regions = ref<ResourceRegion[]>([])
const regionOptions = computed(() => [
  { label: '全部', value: '' },
  ...regions.value.map((r) => ({ label: `${r.flagEmoji || ''} ${r.displayName}`, value: r.code }))
])

function regionDisplay(code?: string): string {
  if (!code) return '-'
  const r = regions.value.find((x) => x.code === code)
  return r ? `${r.flagEmoji || ''} ${r.displayName}` : code
}

const query = reactive<Required<Pick<ResourceIpPoolQuery, 'pageNo' | 'pageSize'>> & ResourceIpPoolQuery>({
  pageNo: 1,
  pageSize: 10,
  keyword: '',
  lifecycleState: undefined,
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
    const res = await pageIpPool({
      pageNo: query.pageNo,
      pageSize: query.pageSize,
      keyword: query.keyword || undefined,
      lifecycleState: query.lifecycleState,
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
  query.lifecycleState = undefined
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

/** 状态 → NTag 颜色映射. */
function statusTagType(status: string): 'success' | 'info' | 'warning' | 'default' {
  switch (status) {
    case 'AVAILABLE': return 'success'
    case 'RESERVED': return 'warning'
    case 'OCCUPIED': return 'info'
    case 'COOLING': return 'default'
    default: return 'default'
  }
}

// ===== lifecycle 流转 =====
const LIFECYCLE_DROPDOWN_OPTIONS = [
  { label: '装机中 INSTALLING', key: 'INSTALLING' },
  { label: '待上线 READY', key: 'READY' },
  { label: '运行中 LIVE', key: 'LIVE' },
  { label: '已退役 RETIRED', key: 'RETIRED' }
]

async function onLifecycleSelect(ip: ResourceIpPool, target: string) {
  if (ip.lifecycleState === target) return
  const targetLabel = IP_POOL_LIFECYCLE_LABELS[target] || target
  const ok = await confirm({
    title: '切换生命周期',
    message: `把 IP ${ip.ipAddress} 从 ${IP_POOL_LIFECYCLE_LABELS[ip.lifecycleState]} 切到 ${targetLabel}?`,
    confirmText: '切换'
  })
  if (!ok) return
  try {
    await transitionIpPoolLifecycle(ip.id, target)
    message.success(`已切换到 ${targetLabel}`)
    loadList()
  } catch {
    /* */
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
  logLevel?: string
  logPath?: string
  autostartEnabled?: number
  firewallEnabled?: number
  installDir?: string
  sshHost?: string
  sshPort?: number
  sshUser?: string
  sshPassword?: string
} | null>(null)

// Phase 2 后入口走 IpPoolCreateChoiceDialog → 自部署 → IpPoolDeployDialog → onAddToPoolFromDeploy
// 不再保留"直接打开空 form 跳过装机"的旧路径

function openEdit(ip: ResourceIpPool) {
  formMode.value = 'edit'
  formIp.value = ip
  formSocksPrefill.value = null
  formOpen.value = true
}

// ===== 分段编辑 (4 个独立 dialog, 跟 server 拆 dialog 同模式) =====
const coreEditOpen = ref(false)
const credentialEditOpen = ref(false)
const billingEditOpen = ref(false)
const socks5EditOpen = ref(false)
const editingIp = ref<ResourceIpPool | null>(null)

function openCoreEdit(ip: ResourceIpPool) {
  editingIp.value = ip
  coreEditOpen.value = true
}
function openCredentialEdit(ip: ResourceIpPool) {
  editingIp.value = ip
  credentialEditOpen.value = true
}
function openBillingEdit(ip: ResourceIpPool) {
  editingIp.value = ip
  billingEditOpen.value = true
}
function openSocks5Edit(ip: ResourceIpPool) {
  editingIp.value = ip
  socks5EditOpen.value = true
}

const EDIT_DROPDOWN_OPTIONS = [
  { label: '核心信息 (区域/类型/IP/部署模式)', key: 'core' },
  { label: 'SSH 凭据', key: 'credential' },
  { label: '账面 (带宽/成本/到期)', key: 'billing' },
  { label: 'dante 配置 + 限速', key: 'socks5' },
  { type: 'divider' as const, key: 'sep' },
  { label: '整段表单 (兼容)', key: 'form' }
]

// ===== 装 landing agent (provisionMode=1 自部署才显示) =====
const provisionOpen = ref(false)
const provisionIpId = ref<string | null>(null)

function openProvision(ip: ResourceIpPool) {
  provisionIpId.value = ip.id
  provisionOpen.value = true
}

function onEditSelect(ip: ResourceIpPool, key: string) {
  switch (key) {
    case 'core': openCoreEdit(ip); break
    case 'credential': openCredentialEdit(ip); break
    case 'billing': openBillingEdit(ip); break
    case 'socks5': openSocks5Edit(ip); break
    case 'form': openEdit(ip); break
  }
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

// ===== 新增 IP 入口 - 方式选择 (自部署 / 第三方) =====
const createChoiceOpen = ref(false)

function openCreateChoice() {
  createChoiceOpen.value = true
}

// ===== 独立部署 SOCKS5 (从 choice dialog 选自部署进入; 不绑定 IP 行) =====
const deployOpen = ref(false)

function openDeploy() {
  deployOpen.value = true
}

/** 部署成功后用户点 "添加到 IP 池": 把凭据 + dante 高级配置一并交给 FormDialog 预填走 create 流程。 */
function onAddToPoolFromDeploy(payload: {
  ipAddress: string
  socks5Port: number
  socks5Username: string
  socks5Password: string
  logLevel?: string
  logPath?: string
  autostartEnabled: number
  firewallEnabled: number
  installDir?: string
  sshHost: string
  sshPort: number
  sshUser: string
  sshPassword: string
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

// ===== 同步 SOCKS5 凭据 (自部署 IP, 改 user/pass/log 后推到远端 + 重建 outbound) =====
const syncCredsOpen = ref(false)
const syncCredsTarget = ref<ResourceIpPool | null>(null)

function openSyncCreds(ip: ResourceIpPool) {
  syncCredsTarget.value = ip
  syncCredsOpen.value = true
}

function onSynced() {
  // 同步完后没有 DB 状态变更, 但刷一下确保最新 updated_at 等字段同步
  loadList()
}

// ===== SOCKS5 服务详情 / 日志 (走 IP 池条目存储的 SSH 凭据) =====
const statusOpen = ref(false)
const statusTarget = ref<ResourceIpPool | null>(null)
const logOpen = ref(false)
const logTarget = ref<ResourceIpPool | null>(null)

function openStatus(ip: ResourceIpPool) {
  statusTarget.value = ip
  statusOpen.value = true
}
function openLog(ip: ResourceIpPool) {
  logTarget.value = ip
  logOpen.value = true
}

/** 详情/日志/切自启都依赖 IP 池条目里存储的 SSH 凭据; 部署模式 = 自部署且 password 已落库才有意义. */
function canManage(ip: ResourceIpPool): boolean {
  return ip.provisionMode === 1 && !!ip.sshPassword
}

// 行操作直接平铺为一行小按钮; 不再折叠到 dropdown

/**
 * 拼标准 socks5:// URL: socks5://[user[:pass]@]host:port
 * user/pass 走 encodeURIComponent 防特殊字符 (@ : / 等) 破坏 URL 结构.
 * 客户端 (v2rayN / 浏览器扩展 / curl --proxy) 都吃这个格式.
 */
function buildSocks5Url(ip: ResourceIpPool): string | null {
  if (!ip.ipAddress || !ip.socks5Port) return null
  const user = ip.socks5Username?.trim() ?? ''
  const pass = ip.socks5Password ?? ''
  let auth = ''
  if (user) {
    auth = encodeURIComponent(user)
    if (pass) auth += ':' + encodeURIComponent(pass)
    auth += '@'
  }
  return `socks5://${auth}${ip.ipAddress}:${ip.socks5Port}`
}

async function copySocks5Url(ip: ResourceIpPool) {
  const url = buildSocks5Url(ip)
  if (!url) {
    message.warning('SOCKS5 信息不完整, 无法复制')
    return
  }
  try {
    await navigator.clipboard.writeText(url)
    // 提示里隐去密码避免日志泄漏
    const masked = ip.socks5Password
      ? url.replace(/:[^@]*@/, ':***@')
      : url
    message.success(`已复制: ${masked}`)
  } catch {
    message.error('复制失败 (浏览器可能不支持 clipboard API)')
  }
}

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
  { title: '区域', key: 'region', width: 140, render: (row) => regionDisplay(row.region) },
  { title: '类型', key: 'ipTypeId', render: (row) => ipTypeName(row.ipTypeId) },
  {
    title: 'SOCKS5',
    key: 'socks5',
    render: (row) => {
      if (!row.socks5Port) {
        return h('span', { class: 'text-xs text-zinc-400' }, '未部署')
      }
      // 显示 host:port / user; 无密码时 user 后面挂红字提示
      const segments: ReturnType<typeof h>[] = [
        h('span', { class: 'font-mono text-xs' }, [
          row.ipAddress,
          h('span', { class: 'text-zinc-400' }, `:${row.socks5Port}`)
        ])
      ]
      if (row.socks5Username) {
        segments.push(
          h('span', { class: 'ml-1 font-mono text-xs text-zinc-500' }, `/ ${row.socks5Username}`)
        )
      }
      if (!row.socks5Password) {
        segments.push(
          h('span', { class: 'ml-2 text-xs', style: 'color: var(--n-warning-color, #f0a020)' }, '(无密码)')
        )
      }
      // 复制按钮: 拷标准 socks5:// URL; hover 提示 + 隐藏的密码 mask 显示
      segments.push(
        h(
          NTooltip,
          { placement: 'top', trigger: 'hover' },
          {
            trigger: () =>
              h(
                NButton,
                {
                  size: 'tiny',
                  quaternary: true,
                  class: 'ml-1',
                  onClick: (e: MouseEvent) => {
                    e.stopPropagation()
                    copySocks5Url(row)
                  }
                },
                { icon: () => h(NIcon, null, { default: () => h(Copy) }) }
              ),
            default: () => h('div', { class: 'text-xs' }, '复制为 socks5://user:password@host:port')
          }
        )
      )
      return h('div', { class: 'flex items-center flex-wrap' }, segments)
    }
  },
  {
    title: '生命周期',
    key: 'lifecycleState',
    width: 90,
    render: (row) =>
      h(
        NTag,
        { size: 'small', type: IP_POOL_LIFECYCLE_TAG_TYPE[row.lifecycleState] || 'default' },
        { default: () => IP_POOL_LIFECYCLE_LABELS[row.lifecycleState] || row.lifecycleState }
      )
  },
  {
    title: '占用状态',
    key: 'status',
    width: 90,
    render: (row) =>
      h(
        NTag,
        { size: 'small', type: statusTagType(row.status) },
        { default: () => IP_POOL_STATUS_LABELS[row.status] || row.status }
      )
  },
  {
    title: '带宽/流量',
    key: 'spec',
    width: 130,
    render: (row) => {
      const bw = row.bandwidthMbps == null ? '∞' : `${row.bandwidthMbps} Mbps`
      const tq = row.trafficQuotaGb == null ? '∞' : `${row.trafficQuotaGb} GB`
      return h('div', { class: 'flex flex-col gap-0.5 font-mono text-xs leading-tight' }, [
        h('span', { class: 'text-zinc-500', title: '采购带宽 (Mbps); ∞ = 不限/未填' }, bw),
        h('span', { class: 'text-zinc-500', title: '采购流量 (GB); ∞ = 不限/未填' }, tq)
      ])
    }
  },
  {
    title: '当前会员',
    key: 'occupiedByMemberId',
    render: (row) =>
      h('span', { class: 'font-mono text-xs text-zinc-500' }, row.occupiedByMemberId || '-')
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
    width: 420,
    render: (row) =>
      h('div', { class: 'flex gap-1 justify-end flex-nowrap' }, [
        // 服务状态: 自部署 + SSH 凭据齐全时露出, 弹框内查 dante 服务状态 + 切自启
        canManage(row)
          ? h(
              NButton,
              {
                size: 'tiny',
                quaternary: true,
                onClick: () => openStatus(row),
                title: '查看 dante 运行状态 / 版本 / 监听端口 / UFW / 主机信息; 弹窗内可切自启'
              },
              {
                icon: () => h(NIcon, null, { default: () => h(Eye) }),
                default: () => '服务状态'
              }
            )
          : null,
        // 日志: 自部署 + SSH 凭据齐全时露出, journalctl -u danted
        canManage(row)
          ? h(
              NButton,
              {
                size: 'tiny',
                quaternary: true,
                onClick: () => openLog(row),
                title: '查看 dante journalctl 日志 (50-1000 行, 按级别 / 关键词过滤)'
              },
              {
                icon: () => h(NIcon, null, { default: () => h(FileText) }),
                default: () => '日志'
              }
            )
          : null,
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
        // 装 landing agent: 仅自部署 (provisionMode=1) 显示, 走 SSH 装机
        row.provisionMode === 1
          ? h(
              NButton,
              {
                size: 'tiny',
                quaternary: true,
                type: 'success',
                onClick: () => openProvision(row),
                title: 'SSH 自动装 nook-landing-agent (后续 dante 限速 / 改配置走 task 链路)'
              },
              {
                icon: () => h(NIcon, null, { default: () => h(Rocket) }),
                default: () => '装 agent'
              }
            )
          : null,
        h(
          NDropdown,
          {
            trigger: 'click',
            options: EDIT_DROPDOWN_OPTIONS,
            onSelect: (key: string) => onEditSelect(row, key)
          },
          {
            default: () =>
              h(
                NButton,
                { size: 'tiny', quaternary: true, title: '分段编辑 (核心/凭据/账面/dante 或整段)' },
                {
                  icon: () => h(NIcon, null, { default: () => h(Pencil) }),
                  default: () => '编辑'
                }
              )
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
                { size: 'tiny', quaternary: true, type: 'info', title: '切换 lifecycle' },
                {
                  icon: () => h(NIcon, null, { default: () => h(ArrowRightLeft) }),
                  default: () => '流转'
                }
              )
          }
        ),
        // 同步凭据: 仅自部署 (provisionMode=1) 且 SOCKS5 配齐的 IP 才显示
        row.provisionMode === 1 && canTest(row)
          ? h(
              NButton,
              {
                size: 'tiny',
                quaternary: true,
                type: 'primary',
                onClick: () => openSyncCreds(row),
                title: '把 DB 当前 SOCKS5 配置推到远端 + 重建 client outbound'
              },
              {
                icon: () => h(NIcon, null, { default: () => h(KeyRound) }),
                default: () => '同步'
              }
            )
          : null,
        // 退订: 仅"已占用" (OCCUPIED) 才有意义, 其它状态隐藏
        row.status === 'OCCUPIED'
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
  await Promise.all([loadIpTypes(), loadRegions()])
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
          <div class="text-xs text-zinc-500 mb-1">生命周期</div>
          <NSelect
            v-model:value="query.lifecycleState"
            :options="IP_POOL_LIFECYCLE_OPTIONS"
            size="small"
            class="w-28"
          />
        </div>
        <div>
          <div class="text-xs text-zinc-500 mb-1">占用状态</div>
          <NSelect
            v-model:value="query.status"
            :options="IP_POOL_STATUS_OPTIONS"
            size="small"
            class="w-28"
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
          type="primary"
          size="small"
          @click="openCreateChoice"
          title="选择方式 (自部署 / 第三方)"
        >
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

    <!-- 新增 IP 方式选择: 自部署 / 第三方 (第三方 Coming Soon) -->
    <IpPoolCreateChoiceDialog
      v-model="createChoiceOpen"
      @choose-self-deploy="openDeploy"
    />

    <!-- 独立部署 SOCKS5 弹框: 从 choice dialog 选自部署进入; 成功后 "添加到 IP 池" 走 onAddToPoolFromDeploy 接力 -->
    <IpPoolDeployDialog v-model="deployOpen" @add-to-pool="onAddToPoolFromDeploy" />

    <!-- SOCKS5 测试弹框: 让用户选 echo-IP 端点 + 看完整请求结果 -->
    <IpPoolTestDialog v-model="testOpen" :ip="testTarget" />

    <!-- SOCKS5 凭据同步弹框: 自部署 IP 改 user/pass/log 后推到远端 -->
    <IpPoolSyncCredsDialog v-model="syncCredsOpen" :ip="syncCredsTarget" @synced="onSynced" />

    <!-- SOCKS5 服务状态: 走存储的 SSH 凭据, 看 dante 运行状态 / 版本 / 监听端口, 含切自启 -->
    <IpPoolStatusDialog v-model="statusOpen" :ip="statusTarget" @changed="loadList" />

    <!-- SOCKS5 日志: journalctl -u danted, lines + level + keyword 过滤 -->
    <IpPoolLogDialog v-model="logOpen" :ip="logTarget" />

    <!-- 分段编辑 dialog (跟 server 拆 dialog 同模式); v-if 让每次切换 IP 重建 dialog, watch immediate=true 立即 fetch 回填 -->
    <IpPoolCoreEditDialog v-if="editingIp" v-model="coreEditOpen" :ip-pool="editingIp" @saved="onFormSaved" />
    <IpPoolCredentialEditDialog v-if="editingIp" v-model="credentialEditOpen" :ip-id="editingIp.id" :ip-address="editingIp.ipAddress" @saved="onFormSaved" />
    <IpPoolBillingEditDialog v-if="editingIp" v-model="billingEditOpen" :ip-id="editingIp.id" @saved="onFormSaved" />
    <IpPoolSocks5EditDialog v-if="editingIp" v-model="socks5EditOpen" :ip-id="editingIp.id" @saved="onFormSaved" />
    <AgentProvisionDialog
      v-model="provisionOpen"
      :initial-server-id="provisionIpId"
      initial-role="landing"
      @dispatched="onFormSaved"
    />
  </div>
</template>
