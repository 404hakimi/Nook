<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import {
  CheckCircle2,
  Copy,
  Eye,
  FileText,
  Globe2,
  KeyRound,
  MoreHorizontal,
  Pencil,
  Plus,
  RefreshCcw,
  Rocket,
  Search,
  Trash2,
  Undo2,
  Users,
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
  getIpPoolSummary,
  pageIpPool,
  releaseIpPool,
  transitionIpPoolLifecycle,
  type ResourceIpPool,
  type ResourceIpPoolQuery,
  type ResourceIpPoolSummary
} from '@/api/resource/ip-pool'
import { listEnabledRegions, type ResourceRegion } from '@/api/resource/region'
import { IP_TYPE_CODE_LABELS, listIpTypes, type ResourceIpType } from '@/api/resource/ip-type'
import IpPoolFormDialog from './IpPoolFormDialog.vue'
import IpPoolDeployDialog from './IpPoolDeployDialog.vue'
import IpPoolCreateDialog from './IpPoolCreateDialog.vue'
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
const summary = ref<ResourceIpPoolSummary>({
  total: 0, installing: 0, ready: 0, live: 0, retired: 0,
  available: 0, occupied: 0, cooling: 0, reserved: 0
})

async function loadSummary() {
  try {
    summary.value = await getIpPoolSummary()
  } catch { /* silently */ }
}

/** 点 stats 卡片 = 按 lifecycle 或 status 过滤 (再点同一个清掉过滤) */
function applyStatsFilter(opts: { lifecycleState?: string; status?: string }) {
  if (opts.lifecycleState != null) {
    query.lifecycleState = query.lifecycleState === opts.lifecycleState ? undefined : opts.lifecycleState as never
  }
  if (opts.status != null) {
    query.status = query.status === opts.status ? undefined : opts.status as never
  }
  query.pageNo = 1
  void loadList()
}

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

// ===== Phase 5: 创建/装机分离 (效仿服务器) =====
// choice 选自部署 → IpPoolCreateDialog (配置同步落库) → 询问立即装机 → IpPoolDeployDialog (流式装机)
const createOpen = ref(false)
const deployOpen = ref(false)
const deployIpId = ref<string | null>(null)

function openCreate() {
  createOpen.value = true
}

/** Create 成功 (lifecycle=INSTALLING) → 刷新列表; 用户选立即装机时 install-now 单独触发 */
function onCreatedAfterChoice(ipId: string) {
  void ipId
  void loadList()
  void loadSummary()
}

/** Create 后用户选 "立即装机" → 打开 DeployDialog 跑流式装机 */
function onInstallNow(ipId: string) {
  deployIpId.value = ipId
  deployOpen.value = true
}

/** 列表行点 "安装/重装 SOCKS5" → 打开 DeployDialog */
function openDeploy(ip: ResourceIpPool) {
  deployIpId.value = ip.id
  deployOpen.value = true
}

/** 装机完成 (lifecycle 切到 LIVE) → 刷新列表 + summary */
function onDeployInstalled(ipId: string) {
  void ipId
  void loadList()
  void loadSummary()
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
  // 当前会员 / 创建时间 移到详情 tab, 列表瘦身
  {
    title: '操作',
    key: 'actions',
    align: 'right',
    width: 200,
    render: (row) => {
      // 3 个 inline 主操作 (详情 / 测试 / 编辑) + 一个"更多" dropdown 收纳运维 / lifecycle / 退订 / 删除
      const isInstalling = row.lifecycleState === 'INSTALLING' || row.lifecycleState === 'READY'
      const isLive = row.lifecycleState === 'LIVE'
      const moreOptions = [
        // SOCKS5 装机 / 重装 (自部署模式)
        row.provisionMode === 1 && isInstalling
          ? { label: '装机 SOCKS5', key: 'deploy', icon: () => h(NIcon, null, { default: () => h(Rocket) }) }
          : null,
        row.provisionMode === 1 && isLive
          ? { label: '重装 SOCKS5', key: 'deploy', icon: () => h(NIcon, null, { default: () => h(Rocket) }) }
          : null,
        canManage(row) ? { label: '查看 dante 状态', key: 'status', icon: () => h(NIcon, null, { default: () => h(Eye) }) } : null,
        canManage(row) ? { label: '查看日志', key: 'log', icon: () => h(NIcon, null, { default: () => h(FileText) }) } : null,
        row.provisionMode === 1 ? { label: '装 landing agent', key: 'provision' } : null,
        row.provisionMode === 1 && canTest(row)
          ? { label: '同步 SOCKS5 凭据', key: 'sync', icon: () => h(NIcon, null, { default: () => h(KeyRound) }) }
          : null,
        { type: 'divider', key: 'd1' },
        ...LIFECYCLE_DROPDOWN_OPTIONS.map((o) => ({ ...o, key: `lc:${o.key}` })),
        { type: 'divider', key: 'd2' },
        row.status === 'OCCUPIED'
          ? { label: '退订到 cooling', key: 'release', icon: () => h(NIcon, null, { default: () => h(Undo2) }) }
          : null,
        { label: '删除 IP', key: 'delete', icon: () => h(NIcon, null, { default: () => h(Trash2) }) }
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      ].filter(Boolean) as any[]

      function onMoreSelect(key: string) {
        switch (key) {
          case 'deploy': return openDeploy(row)
          case 'status': return openStatus(row)
          case 'log': return openLog(row)
          case 'provision': return openProvision(row)
          case 'sync': return openSyncCreds(row)
          case 'release': return onRelease(row)
          case 'delete': return onDelete(row)
          default:
            if (key.startsWith('lc:')) return onLifecycleSelect(row, key.slice(3))
        }
      }

      return h('div', { class: 'flex gap-1 justify-end flex-nowrap' }, [
        // 主 1: 测试 — 拨号自检, SOCKS5 凭据齐全才出
        canTest(row)
          ? h(
              NButton,
              { size: 'tiny', quaternary: true, type: 'warning', onClick: () => openTest(row), title: '拨号自检 SOCKS5' },
              { icon: () => h(NIcon, null, { default: () => h(Zap) }), default: () => '测试' }
            )
          : null,
        // 主 2: 编辑 (分段下拉)
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
                { size: 'tiny', quaternary: true, title: '编辑分段配置' },
                { icon: () => h(NIcon, null, { default: () => h(Pencil) }), default: () => '编辑' }
              )
          }
        ),
        // 主 3: 更多 (运维 / lifecycle 流转 / 退订 / 删除全部收纳)
        h(
          NDropdown,
          { trigger: 'click', options: moreOptions, onSelect: onMoreSelect },
          {
            default: () =>
              h(
                NButton,
                { size: 'tiny', quaternary: true, title: '更多操作' },
                { icon: () => h(NIcon, null, { default: () => h(MoreHorizontal) }) }
              )
          }
        )
      ])
    }
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
  await Promise.all([loadList(), loadSummary()])
})
</script>

<template>
  <div class="space-y-4">
    <!-- 顶部统计卡片 (点击 = 切换过滤; 再点同一个清掉过滤) -->
    <div class="grid grid-cols-2 sm:grid-cols-4 gap-4">
      <!-- 卡片 1: 总 IP (zinc/灰色 - neutral) -->
      <div
        class="stat-card stat-card--zinc"
        :class="{ 'stat-card--active': !query.lifecycleState && !query.status }"
        @click="() => { query.lifecycleState = undefined; query.status = undefined; query.pageNo = 1; loadList() }"
      >
        <div class="stat-card__accent" />
        <div class="stat-card__body">
          <div class="stat-card__label">总 IP</div>
          <div class="stat-card__value">{{ summary.total }}</div>
          <div class="stat-card__hint">全量, 含已退役</div>
        </div>
        <div class="stat-card__icon">
          <NIcon :size="28"><Globe2 /></NIcon>
        </div>
      </div>

      <!-- 卡片 2: 已部署 LIVE (green) -->
      <div
        class="stat-card stat-card--green"
        :class="{ 'stat-card--active': query.lifecycleState === 'LIVE' }"
        @click="applyStatsFilter({ lifecycleState: 'LIVE' })"
      >
        <div class="stat-card__accent" />
        <div class="stat-card__body">
          <div class="stat-card__label">已部署 LIVE</div>
          <div class="stat-card__value">{{ summary.live }}</div>
          <div class="stat-card__hint">SOCKS5 已起 + 可对外服务</div>
        </div>
        <div class="stat-card__icon">
          <NIcon :size="28"><Rocket /></NIcon>
        </div>
      </div>

      <!-- 卡片 3: 可分配 AVAILABLE (blue) -->
      <div
        class="stat-card stat-card--blue"
        :class="{ 'stat-card--active': query.status === 'AVAILABLE' }"
        @click="applyStatsFilter({ status: 'AVAILABLE' })"
      >
        <div class="stat-card__accent" />
        <div class="stat-card__body">
          <div class="stat-card__label">可分配</div>
          <div class="stat-card__value">{{ summary.available }}</div>
          <div class="stat-card__hint">allocator 可给新订阅</div>
        </div>
        <div class="stat-card__icon">
          <NIcon :size="28"><CheckCircle2 /></NIcon>
        </div>
      </div>

      <!-- 卡片 4: 已占用 OCCUPIED (orange) -->
      <div
        class="stat-card stat-card--orange"
        :class="{ 'stat-card--active': query.status === 'OCCUPIED' }"
        @click="applyStatsFilter({ status: 'OCCUPIED' })"
      >
        <div class="stat-card__accent" />
        <div class="stat-card__body">
          <div class="stat-card__label">已占用</div>
          <div class="stat-card__value">{{ summary.occupied }}</div>
          <div class="stat-card__hint">已分配给会员订阅</div>
        </div>
        <div class="stat-card__icon">
          <NIcon :size="28"><Users /></NIcon>
        </div>
      </div>
    </div>

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
      @choose-self-deploy="openCreate"
    />

    <!-- Phase 5: 自部署 创建 dialog (配置 sync 落库 lifecycle=INSTALLING; 不跑 SSH) -->
    <IpPoolCreateDialog
      v-model="createOpen"
      :ip-types="ipTypes"
      :regions="regions"
      @created="onCreatedAfterChoice"
      @install-now="onInstallNow"
    />

    <!-- 装机 SOCKS5 dialog (针对已有 ipId 跑流式装机 + lifecycle → LIVE) -->
    <IpPoolDeployDialog
      v-model="deployOpen"
      :ip-id="deployIpId"
      @installed="onDeployInstalled"
    />

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

<style scoped>
/* ===== Stats 卡片: 左侧色条 accent + 大图标 + hover 抬升 + active 边框高亮 ===== */
.stat-card {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 20px;
  background: var(--n-card-color, #fff);
  border: 1px solid var(--n-border-color, #efeff5);
  border-radius: 8px;
  cursor: pointer;
  overflow: hidden;
  transition: transform 0.15s ease, box-shadow 0.15s ease, border-color 0.15s ease;
}
.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}
.stat-card--active {
  border-color: currentColor;
  box-shadow: 0 0 0 1px currentColor inset;
}
.stat-card__accent {
  position: absolute;
  top: 0;
  left: 0;
  width: 4px;
  height: 100%;
  background: currentColor;
}
.stat-card__body {
  flex: 1;
  min-width: 0;
}
.stat-card__label {
  font-size: 13px;
  color: var(--n-text-color-3, #707070);
  margin-bottom: 4px;
}
.stat-card__value {
  font-size: 30px;
  font-weight: 600;
  line-height: 1.1;
  color: currentColor;
}
.stat-card__hint {
  font-size: 11px;
  color: var(--n-text-color-3, #909399);
  margin-top: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.stat-card__icon {
  flex-shrink: 0;
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  background: color-mix(in srgb, currentColor 12%, transparent);
  color: currentColor;
}

/* 颜色 accent: 把 currentColor 设到卡片上, 子元素 (accent / value / icon) 全部继承 */
.stat-card--zinc   { color: #71717a; }
.stat-card--green  { color: #18a058; }
.stat-card--blue   { color: #2080f0; }
.stat-card--orange { color: #f0a020; }
</style>
