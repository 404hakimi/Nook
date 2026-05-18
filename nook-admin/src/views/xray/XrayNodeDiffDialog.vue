<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { CheckCircle2, Copy, Info, RefreshCw, X as XIcon } from 'lucide-vue-next'
import {
  NAlert,
  NButton,
  NIcon,
  NModal,
  NSpace,
  NSpin,
  NTag,
  useMessage
} from 'naive-ui'
import { Graph, type Node } from '@antv/x6'
import {
  getSyncStatus,
  pageClients,
  replayServer,
  syncClient,
  type ReplayReport,
  type SyncStatus,
  type XrayClient
} from '@/api/xray/client'
import { IP_POOL_STATUS_LABELS, pageIpPool, type ResourceIpPool } from '@/api/resource/ip-pool'
import type { XrayNode } from '@/api/xray/node'

interface Props {
  modelValue: boolean
  node?: XrayNode | null
}
const props = defineProps<Props>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
}>()

const message = useMessage()

const loading = ref(false)
const replayLoading = ref(false)
const syncStatus = ref<SyncStatus | null>(null)
const clients = ref<XrayClient[]>([])
/** ipId → 完整 IP 池行 (含 status / region / socks5 凭据), 详情面板里 enrich 落地 IP 节点用 */
const ipPoolByIpId = ref<Record<string, ResourceIpPool>>({})

const graphContainer = ref<HTMLDivElement | null>(null)
let graph: Graph | null = null

// 行索引 → 该行所有节点 ID; 用于 hover 高亮整行
const rowNodes = new Map<number, string[]>()
// 节点 ID → 所在行的元数据 (clientId / 列序号 / 状态), 用于 click 操作
interface NodeMeta {
  rowIdx: number
  colIdx: number
  status: NodeStatus
  clientId?: string
  email?: string
  ipAddress?: string
  ipId?: string
  remoteTag?: string
}
const nodeMeta = new Map<string, NodeMeta>()
// 当前点中的节点详情, 显示在右侧信息面板
const selectedMeta = shallowRef<NodeMeta | null>(null)
const syncingClientId = ref<string | null>(null)

interface Column {
  clientId: string
  email: string
  ipAddress: string
  ipId: string
  userStatus: NodeStatus
  ruleStatus: NodeStatus
  outboundStatus: NodeStatus
}
type NodeStatus = 'ok' | 'stale' | 'orphan'

const columns = ref<Column[]>([])
const orphanEmails = ref<string[]>([])
const orphanRules = ref<string[]>([])
const orphanOutbounds = ref<string[]>([])

const totalStale = computed(() => {
  const s = syncStatus.value
  if (!s) return 0
  return s.staleDbEmails.length + s.staleDbOutbounds.length + s.staleDbRules.length
})

const totalOrphan = computed(() => {
  const s = syncStatus.value
  if (!s) return 0
  return s.orphanRemoteEmails.length + s.orphanRemoteOutbounds.length + s.orphanRemoteRules.length
})

watch(
  () => [props.modelValue, props.node?.serverId],
  ([open]) => {
    if (open) {
      runRefresh()
    } else {
      destroyGraph()
      syncStatus.value = null
      clients.value = []
      columns.value = []
      ipPoolByIpId.value = {}
      closeSelection()
    }
  }
)

async function runRefresh() {
  if (!props.node) return
  loading.value = true
  try {
    const [status, clientPage] = await Promise.all([
      getSyncStatus(props.node.serverId),
      pageClients({ serverId: props.node.serverId, pageNo: 1, pageSize: 500 })
    ])
    syncStatus.value = status
    clients.value = clientPage.records
    // 拉本轮涉及到的 IP 池行 (status=2 已占用) 给详情面板 enrich 用; 失败留空差异主流程不受影响
    try {
      const ipPage = await pageIpPool({ pageNo: 1, pageSize: 500, status: 2 })
      ipPoolByIpId.value = Object.fromEntries(ipPage.records.map((r) => [r.id, r]))
    } catch {
      ipPoolByIpId.value = {}
    }
    buildColumns()
    await nextTick()
    renderGraph()
  } catch (e) {
    syncStatus.value = null
    clients.value = []
    columns.value = []
    message.error('查看差异失败: ' + ((e as Error).message ?? ''))
  } finally {
    loading.value = false
  }
}

function buildColumns() {
  const s = syncStatus.value
  if (!s) {
    columns.value = []
    orphanEmails.value = []
    orphanRules.value = []
    orphanOutbounds.value = []
    return
  }
  const okEmails = new Set(s.okEmails)
  const staleEmails = new Set(s.staleDbEmails)
  const staleOutbounds = new Set(s.staleDbOutbounds)
  const staleRules = new Set(s.staleDbRules)

  columns.value = clients.value.map((c) => ({
    clientId: c.id,
    email: c.clientEmail,
    ipAddress: c.ipAddress || c.ipId.slice(0, 8),
    ipId: c.ipId,
    userStatus: okEmails.has(c.clientEmail)
      ? 'ok'
      : staleEmails.has(c.clientEmail)
        ? 'stale'
        : 'stale',
    ruleStatus: staleRules.has(c.id) ? 'stale' : 'ok',
    outboundStatus: staleOutbounds.has(c.id) ? 'stale' : 'ok'
  }))
  orphanEmails.value = s.orphanRemoteEmails
  orphanRules.value = s.orphanRemoteRules
  orphanOutbounds.value = s.orphanRemoteOutbounds
}

const COLOR = {
  ok: { fill: '#ffffff', stroke: '#d9f0db', accent: '#52c41a', text: '#262626' },
  stale: { fill: '#fffbe6', stroke: '#ffe58f', accent: '#fa8c16', text: '#262626' },
  orphan: { fill: '#fafafa', stroke: '#e5e7eb', accent: '#bfbfbf', text: '#737373' },
  inbound: { fill: '#e6f4ff', stroke: '#1677ff', accent: '#1677ff', text: '#0958d9' }
}

// 横向布局: 每客户一行, 4 列 (user → rule → outbound → 落地 IP); inbound 不入图, 顶部独立展示
const COL_GAP = 32
const NODE_WIDTH = 200
const NODE_HEIGHT = 36
const ROW_GAP = 6
const ROW_HEIGHT = NODE_HEIGHT + ROW_GAP
const ROW_HEADER_HEIGHT = 22
const COL_X = [32, 32 + 1 * (NODE_WIDTH + COL_GAP), 32 + 2 * (NODE_WIDTH + COL_GAP), 32 + 3 * (NODE_WIDTH + COL_GAP)]
const COL_TITLES = ['用户 (email)', '路由规则', 'socks5 出站', '落地 IP']

function destroyGraph() {
  if (graph) {
    graph.dispose()
    graph = null
  }
}

function renderGraph() {
  if (!graphContainer.value) return
  destroyGraph()
  rowNodes.clear()
  nodeMeta.clear()
  closeSelection()

  const cols = columns.value
  const orphanColCount = Math.max(orphanEmails.value.length, orphanRules.value.length, orphanOutbounds.value.length)

  // autoResize 让 X6 自己跟随容器 ResizeObserver, 不需要手动传 width/height; 容器靠 flex 自然撑满
  graph = new Graph({
    container: graphContainer.value,
    autoResize: true,
    background: { color: '#ffffff' },
    interacting: false,
    panning: { enabled: true },
    mousewheel: { enabled: true, modifiers: ['ctrl'] },
    connecting: { router: 'normal', connector: { name: 'rounded', args: { radius: 4 } } }
  })

  // 事件: hover 高亮整行 + click 选中. 用 enter cancels-pending-leave 的方式避免快速划过节点时闪烁
  graph.on('node:mouseenter', ({ node }) => {
    cancelPendingClearHL()
    highlightRow(nodeMeta.get(node.id)?.rowIdx)
  })
  graph.on('node:mouseleave', scheduleClearHL)
  graph.on('node:click', ({ node }) => {
    const meta = nodeMeta.get(node.id)
    if (meta) {
      selectedMeta.value = meta
      selectionKind.value = 'node'
    }
  })
  graph.on('blank:click', () => closeSelection())

  // 列头: 不带背景, 纯文字标签风格
  COL_TITLES.forEach((title, i) => {
    graph!.addNode({
      shape: 'rect',
      x: COL_X[i],
      y: 8,
      width: NODE_WIDTH,
      height: ROW_HEADER_HEIGHT,
      label: title,
      attrs: {
        body: { fill: 'transparent', stroke: 'transparent' },
        label: { fill: '#8c8c8c', fontSize: 11, fontWeight: 500, textAnchor: 'start', refX: 4 }
      }
    })
  })

  // DB 内每个客户一行: user → rule → outbound → 落地 IP
  cols.forEach((col, idx) => {
    drawRow(graph!, idx, col)
  })

  // orphan 行紧接在 DB 行下面
  if (orphanColCount > 0) {
    drawOrphanRows(graph, cols.length, orphanEmails.value, orphanRules.value, orphanOutbounds.value)
  }

  // 下一帧 autoResize 把 SVG 撑到容器尺寸后再做布局: 左上角锚定 + 按需缩放; zoomToFit 默认会居中, 不符合"上往下读"的列表语义
  requestAnimationFrame(anchorContentTopLeft)
}

function anchorContentTopLeft() {
  if (!graph || !graphContainer.value) return
  const cw = graphContainer.value.clientWidth
  const ch = graphContainer.value.clientHeight
  const totalRows = columns.value.length
    + Math.max(orphanEmails.value.length, orphanRules.value.length, orphanOutbounds.value.length)
  // 内容原点 = COL_X[0]=32, 用模型坐标算内容边框 (不要 getContentBBox, 它返回屏幕坐标随 transform 漂移)
  const contentW = COL_X[3] + NODE_WIDTH + 16
  const contentH = ROW_HEADER_HEIGHT + 20 + Math.max(1, totalRows) * ROW_HEIGHT + 16
  const PAD = 16
  const sW = (cw - PAD * 2) / contentW
  const sH = (ch - PAD * 2) / contentH
  const scale = Math.max(0.2, Math.min(1.4, Math.min(sW, sH)))
  // 用 matrix 一次性设 scale + translate: 模型点(0,0) → 视口点(PAD, PAD), 内容靠左上排列
  graph.matrix({ a: scale, b: 0, c: 0, d: scale, e: PAD, f: PAD })
}

// hover 高亮状态: currentRow 缓存当前高亮的行, 防止重复 apply 引起闪烁;
// leave → enter 之间留 60ms 缓冲, 若 60ms 内再次 enter (在同一行内滑动) 就取消清除
let currentHighlightRow: number | null = null
let pendingClearHL: ReturnType<typeof setTimeout> | null = null

function highlightRow(rowIdx: number | null | undefined) {
  const next = rowIdx ?? null
  if (currentHighlightRow === next) return
  currentHighlightRow = next
  if (!graph) return
  const targetSet = next == null ? null : new Set(rowNodes.get(next) ?? [])
  graph.getNodes().forEach((n) => {
    const op = !targetSet || targetSet.has(n.id) ? 1 : 0.2
    n.attr('body/opacity', op)
    n.attr('label/opacity', op)
  })
}

function scheduleClearHL() {
  if (pendingClearHL) clearTimeout(pendingClearHL)
  pendingClearHL = setTimeout(() => highlightRow(null), 60)
}
function cancelPendingClearHL() {
  if (pendingClearHL) { clearTimeout(pendingClearHL); pendingClearHL = null }
}

function rowY(rowIdx: number) {
  return ROW_HEADER_HEIGHT + 20 + rowIdx * ROW_HEIGHT
}

function drawRow(g: Graph, rowIdx: number, col: Column) {
  const y = rowY(rowIdx)
  const userNode = addCell(g, 0, rowIdx, y, col.userStatus, truncate(col.email, 28), {
    clientId: col.clientId, email: col.email, ipAddress: col.ipAddress, ipId: col.ipId
  })
  const ruleNode = addCell(g, 1, rowIdx, y, col.ruleStatus, `rule_${col.clientId.slice(0, 12)}…`, {
    clientId: col.clientId, email: col.email, ipAddress: col.ipAddress, ipId: col.ipId, remoteTag: `rule_${col.clientId}`
  })
  const outboundNode = addCell(g, 2, rowIdx, y, col.outboundStatus, `out_${col.clientId.slice(0, 12)}…`, {
    clientId: col.clientId, email: col.email, ipAddress: col.ipAddress, ipId: col.ipId, remoteTag: `out_${col.clientId}`
  })
  const landingNode = addCell(g, 3, rowIdx, y, 'ok', col.ipAddress, {
    clientId: col.clientId, email: col.email, ipAddress: col.ipAddress, ipId: col.ipId
  })
  rowNodes.set(rowIdx, [userNode.id, ruleNode.id, outboundNode.id, landingNode.id])
  g.addEdge({ source: { cell: userNode.id }, target: { cell: ruleNode.id }, attrs: edgeAttrs(col.ruleStatus === 'stale') })
  g.addEdge({ source: { cell: ruleNode.id }, target: { cell: outboundNode.id }, attrs: edgeAttrs(col.outboundStatus === 'stale') })
  g.addEdge({ source: { cell: outboundNode.id }, target: { cell: landingNode.id }, attrs: edgeAttrs() })
}

function drawOrphanRows(g: Graph, startRowIdx: number, emails: string[], rules: string[], outbounds: string[]) {
  const maxN = Math.max(emails.length, rules.length, outbounds.length)
  for (let i = 0; i < maxN; i++) {
    const rowIdx = startRowIdx + i
    const y = rowY(rowIdx)
    const ids: string[] = []
    if (emails[i]) ids.push(addCell(g, 0, rowIdx, y, 'orphan', truncate(emails[i], 28), { email: emails[i] }).id)
    if (rules[i]) ids.push(addCell(g, 1, rowIdx, y, 'orphan', `rule_${rules[i].slice(0, 12)}…`, { remoteTag: `rule_${rules[i]}` }).id)
    if (outbounds[i]) ids.push(addCell(g, 2, rowIdx, y, 'orphan', `out_${outbounds[i].slice(0, 12)}…`, { remoteTag: `out_${outbounds[i]}` }).id)
    // 落地 IP 列虚线占位, 保持视觉对齐
    const placeholder = g.addNode({
      shape: 'rect',
      x: COL_X[3],
      y,
      width: NODE_WIDTH,
      height: NODE_HEIGHT,
      label: '—',
      attrs: {
        body: { fill: 'transparent', stroke: '#f0f0f0', strokeWidth: 1, strokeDasharray: '2,3', rx: 6, ry: 6 },
        label: { fill: '#d9d9d9', fontSize: 12 }
      }
    })
    ids.push(placeholder.id)
    rowNodes.set(rowIdx, ids)
  }
}

/** 单元格 = 主体白底圆角矩形 + 左侧状态色条 (4px 宽); 同时注册 hover/click 元数据. */
function addCell(
  g: Graph,
  colIdx: number,
  rowIdx: number,
  y: number,
  status: NodeStatus,
  label: string,
  extra: Partial<NodeMeta>
): Node {
  const c = COLOR[status]
  const x = COL_X[colIdx]
  // 状态色条
  g.addNode({
    shape: 'rect',
    x,
    y,
    width: 4,
    height: NODE_HEIGHT,
    attrs: {
      body: { fill: c.accent, stroke: 'transparent', rx: 2, ry: 2 },
      label: { text: '' }
    },
    zIndex: 2
  })
  // 主体
  const node = g.addNode({
    shape: 'rect',
    x,
    y,
    width: NODE_WIDTH,
    height: NODE_HEIGHT,
    label,
    attrs: {
      body: {
        fill: c.fill,
        stroke: c.stroke,
        strokeWidth: 1,
        strokeDasharray: status === 'orphan' ? '3,3' : undefined,
        rx: 6,
        ry: 6,
        cursor: 'pointer'
      },
      label: {
        fill: c.text,
        fontSize: 11,
        textAnchor: 'start',
        refX: 14,
        textWrap: { width: NODE_WIDTH - 20, ellipsis: true }
      }
    },
    zIndex: 1
  })
  nodeMeta.set(node.id, { rowIdx, colIdx, status, ...extra })
  return node
}

function truncate(s: string, n: number) {
  return s.length > n ? s.slice(0, n - 1) + '…' : s
}

function edgeAttrs(stale = false) {
  return {
    line: {
      stroke: stale ? '#ffc069' : '#e5e7eb',
      strokeWidth: 1,
      strokeDasharray: stale ? '3,2' : undefined,
      targetMarker: { name: 'classic', size: 4 }
    }
  }
}

interface DetailRow {
  label: string
  value: string
  mono?: boolean
  active?: boolean
  copyable?: boolean
}
type SectionAccent = 'blue' | 'green' | 'amber'
interface PanelSection {
  heading: string
  rows: DetailRow[]
  /** 左侧色条 + 标题颜色 — blue=服务端, green=存储, amber=链路 */
  accent: SectionAccent
  /** 链路 section 行有 active 高亮态; 其他 section 不需要 */
  highlightActive?: boolean
}
interface PanelView {
  title: string
  /** 仅 graph-node 点选时显示状态标签 */
  status: NodeStatus | null
  sections: PanelSection[]
  /** 单条修复 clientId; null 表示当前 selection 不可操作 */
  actionClientId: string | null
  /** 头部背景色 (按状态/类型) */
  headerBg: string
}

/** 当前选中的对象类型: 图节点 / 顶部 chip 之一 */
type SelectionKind = 'node' | 'server' | 'xray-node' | 'inbound'
const selectionKind = shallowRef<SelectionKind | null>(null)

/** 详情面板内容: 按选中对象类型 (graph node / 顶部 chip) 分别构造服务端 + 存储两段视图 */
const panelView = computed<PanelView | null>(() => {
  const kind = selectionKind.value
  if (!kind) return null
  if (kind === 'server') return buildServerView()
  if (kind === 'xray-node') return buildXrayNodeView()
  if (kind === 'inbound') return buildInboundView()
  return buildGraphNodeView()
})

function buildServerView(): PanelView {
  const n = props.node
  return {
    title: '服务器',
    status: null,
    headerBg: '#dbe4ff',
    actionClientId: null,
    sections: [
      {
        heading: '服务端 (实时)',
        accent: 'blue',
        rows: [
          { label: '服务器名', value: n?.serverName ?? '—', mono: false },
          { label: '主机', value: n?.serverHost ?? '—', mono: true },
          { label: '说明', value: 'SSH 连接 + Xray API 走 loopback' }
        ]
      },
      {
        heading: '存储 (DB resource_server)',
        accent: 'green',
        rows: [
          { label: 'serverId', value: n?.serverId ?? '—', mono: true },
          { label: '服务器名', value: n?.serverName ?? '—' },
          { label: '主机', value: n?.serverHost ?? '—', mono: true }
        ]
      }
    ]
  }
}

function buildXrayNodeView(): PanelView {
  const n = props.node
  return {
    title: 'Xray 节点',
    status: null,
    headerBg: '#efdbff',
    actionClientId: null,
    sections: [
      {
        heading: '服务端 (xray runtime)',
        accent: 'blue',
        rows: [
          { label: 'Xray 版本', value: n?.xrayVersion ?? '—', mono: true },
          { label: 'gRPC API 端口', value: n?.xrayApiPort != null ? `127.0.0.1:${n.xrayApiPort}` : '—', mono: true },
          { label: '上次启动', value: n?.lastXrayUptime ?? '—' },
          { label: '最近部署', value: n?.installedAt ?? '—' }
        ]
      },
      {
        heading: '存储 (DB xray_node)',
        accent: 'green',
        rows: [
          { label: '安装目录', value: n?.xrayInstallDir ?? '—', mono: true },
          { label: 'binary', value: n?.xrayBinaryPath ?? '—', mono: true },
          { label: 'config.json', value: n?.xrayConfigPath ?? '—', mono: true },
          { label: 'share 目录', value: n?.xrayShareDir ?? '—', mono: true },
          { label: '日志目录', value: n?.xrayLogDir ?? '—', mono: true },
          { label: '落地上限', value: n?.touchdownSize != null ? String(n.touchdownSize) : '—', mono: true },
          { label: 'TLS 证书', value: n?.tlsCertPath ?? '—', mono: true },
          { label: 'TLS 私钥', value: n?.tlsKeyPath ?? '—', mono: true }
        ]
      }
    ]
  }
}

function buildInboundView(): PanelView {
  const n = props.node
  return {
    title: '共享 inbound',
    status: null,
    headerBg: '#bae0ff',
    actionClientId: null,
    sections: [
      {
        heading: '服务端 (xray inbound)',
        accent: 'blue',
        rows: [
          { label: 'tag', value: 'in_shared', mono: true, active: true },
          { label: '协议', value: n?.protocol ?? 'vmess', mono: true },
          { label: '传输', value: n?.transport ?? 'ws', mono: true },
          { label: '监听 IP', value: n?.listenIp ?? '0.0.0.0', mono: true },
          { label: '监听端口', value: n?.sharedInboundPort != null ? String(n.sharedInboundPort) : '—', mono: true },
          { label: 'WS path', value: n?.wsPath ?? '—', mono: true },
          { label: '对外域名', value: n?.domain ?? '—', mono: true }
        ]
      },
      {
        heading: '存储 (DB xray_node)',
        accent: 'green',
        rows: [
          { label: '说明', value: 'inbound 配置存在 xray_node 行上; 一服务器一份共享 inbound' }
        ]
      }
    ]
  }
}

function buildGraphNodeView(): PanelView {
  const m = selectedMeta.value
  if (!m) {
    return { title: '', status: null, headerBg: '#fff', sections: [], actionClientId: null }
  }
  const COL_TITLE = ['用户 (inbound user)', '路由规则', 'socks5 出站', '落地 IP']
  const ruleTag = m.clientId ? `rule_${m.clientId}` : ''
  const outTag = m.clientId ? `out_${m.clientId}` : ''
  const dbClient = m.clientId ? clients.value.find((c) => c.id === m.clientId) : null
  const ipPool = m.ipId ? ipPoolByIpId.value[m.ipId] : null

  // 服务端 section: 该节点远端实际状态
  const remoteByCol: Record<number, DetailRow[]> = {
    0: [
      { label: 'inbound', value: 'in_shared', mono: true },
      { label: 'user email', value: m.email ?? '—', mono: true, active: true },
      { label: 'vmess uuid', value: dbClient?.clientUuid ?? '—', mono: true }
    ],
    1: [
      { label: '完整 tag', value: m.remoteTag ?? ruleTag, mono: true, active: true },
      { label: '匹配 user_email', value: m.email ?? '—', mono: true },
      { label: '指向 outboundTag', value: outTag || '—', mono: true }
    ],
    2: [
      { label: '完整 tag', value: m.remoteTag ?? outTag, mono: true, active: true },
      { label: '协议', value: 'socks5 client' },
      { label: 'server addr', value: ipPool?.ipAddress ?? m.ipAddress ?? '—', mono: true },
      { label: 'server port', value: ipPool?.socks5Port != null ? String(ipPool.socks5Port) : '—', mono: true },
      { label: 'auth user', value: ipPool?.socks5Username ?? '—', mono: true }
    ],
    3: [
      { label: 'IP 地址', value: ipPool?.ipAddress ?? m.ipAddress ?? '—', mono: true, active: true },
      { label: 'SOCKS5 端口', value: ipPool?.socks5Port != null ? String(ipPool.socks5Port) : '—', mono: true },
      { label: 'region', value: ipPool?.region ?? '—' },
      { label: '最近健康检测', value: ipPool?.lastHealthAt ?? '—' }
    ]
  }

  const dbRows: DetailRow[] = [
    { label: 'clientId', value: m.clientId ?? '—', mono: true },
    { label: 'memberUserId', value: dbClient?.memberUserId ?? '—', mono: true },
    { label: 'clientEmail', value: dbClient?.clientEmail ?? m.email ?? '—', mono: true },
    { label: 'ipId', value: m.ipId ?? '—', mono: true },
    { label: 'ipAddress', value: ipPool?.ipAddress ?? m.ipAddress ?? '—', mono: true },
    { label: '客户端状态', value: clientStatusLabel(dbClient?.status), copyable: false },
    { label: '最近同步', value: dbClient?.lastSyncedAt ?? '—' },
    { label: '创建时间', value: dbClient?.createdAt ?? '—' }
  ]

  // 落地 IP 列额外显示一段独立的 "落地 IP (DB)" section, 把 IP 池行字段全摊开
  const ipPoolRows: DetailRow[] | null = (m.colIdx === 3 && ipPool) ? [
    { label: 'IP 状态', value: ipPool.status != null ? IP_POOL_STATUS_LABELS[ipPool.status] ?? String(ipPool.status) : '—', copyable: false },
    { label: 'region', value: ipPool.region ?? '—' },
    { label: 'ipType', value: ipPool.ipTypeId ?? '—', mono: true },
    { label: '分配会员', value: ipPool.assignedMemberId ?? '—', mono: true },
    { label: '分配时间', value: ipPool.assignedAt ?? '—' },
    { label: '分配次数', value: ipPool.assignCount != null ? String(ipPool.assignCount) : '—', copyable: false },
    { label: 'SOCKS5 端口', value: ipPool.socks5Port != null ? String(ipPool.socks5Port) : '—', mono: true },
    { label: 'SOCKS5 用户', value: ipPool.socks5Username ?? '—', mono: true }
  ] : null

  const chainRows: DetailRow[] = [
    { label: '用户 email', value: m.email ?? '—', mono: true, active: m.colIdx === 0 },
    { label: '路由规则', value: ruleTag || '—', mono: true, active: m.colIdx === 1 },
    { label: 'socks5 出站', value: outTag || '—', mono: true, active: m.colIdx === 2 },
    { label: '落地 IP', value: m.ipAddress ?? '—', mono: true, active: m.colIdx === 3 }
  ]

  const headerBg = m.status === 'stale' ? '#ffd591' : m.status === 'orphan' ? '#e4e4e7' : '#b7eb8f'

  const sections: PanelSection[] = [
    { heading: '服务端 (xray 远端实际)', accent: 'blue', rows: remoteByCol[m.colIdx] ?? [] },
    { heading: '存储 (DB xray_client)', accent: 'green', rows: dbRows }
  ]
  // 落地 IP 节点额外补 "IP 池详情" section (resource_ip_pool 行)
  if (ipPoolRows) {
    sections.push({ heading: '落地 IP 池 (DB resource_ip_pool)', accent: 'green', rows: ipPoolRows })
  }
  sections.push({ heading: '该客户全链路', accent: 'amber', rows: chainRows, highlightActive: true })

  return {
    title: COL_TITLE[m.colIdx] ?? '节点',
    status: m.status,
    headerBg,
    sections,
    actionClientId: selectedRowStale.value ? m.clientId ?? null : null
  }
}

function clientStatusLabel(code: number | undefined): string {
  if (code == null) return '—'
  const map: Record<number, string> = { 1: '运行', 2: '已停', 3: '待同步', 4: '远端缺失' }
  return map[code] ?? String(code)
}

/** 选中行是否有可修复项 (用于详情面板上"推送修复"按钮的展示与可用) */
const selectedRowStale = computed(() => {
  const m = selectedMeta.value
  const s = syncStatus.value
  if (!m || !s || !m.clientId) return false
  if (s.staleDbOutbounds.includes(m.clientId)) return true
  if (s.staleDbRules.includes(m.clientId)) return true
  if (m.email && s.staleDbEmails.includes(m.email)) return true
  return false
})

const replayButton = computed<{ label: string; disabled: boolean; tip: string }>(() => {
  if (!syncStatus.value) {
    return { label: '推送修复', disabled: true, tip: '加载中...' }
  }
  if (!syncStatus.value.reachable) {
    return { label: '推送修复', disabled: true, tip: '远端不可达, 无法推送' }
  }
  const n = totalStale.value
  if (n === 0) {
    return { label: '✓ 一切正常, 无需推送', disabled: true, tip: '远端与 DB 一致' }
  }
  return {
    label: `立即推送修复 ${n} 项`,
    disabled: false,
    tip: '按 DB 状态幂等推 user / rule / outbound 三段到远端'
  }
})

async function copyText(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    message.success('已复制')
  } catch {
    message.warning('复制失败, 请手动选中')
  }
}

/** 单条修复: 调 syncClient 把该 client 的三段配置幂等推到远端. */
async function onSyncOne(clientId: string) {
  if (syncingClientId.value) return
  syncingClientId.value = clientId
  try {
    await syncClient(clientId)
    message.success('已推送')
    await runRefresh()
    selectedMeta.value = null
  } catch (e) {
    message.error('修复失败: ' + ((e as Error).message ?? ''))
  } finally {
    syncingClientId.value = null
  }
}

async function onReplay() {
  if (!props.node || replayLoading.value || totalStale.value === 0) return
  const label = props.node.serverName || props.node.serverId.slice(0, 12)
  replayLoading.value = true
  try {
    const report: ReplayReport = await replayServer(props.node.serverId)
    const tip = `总 ${report.totalCount} · 已就绪 ${report.alreadyOkCount} · 推送 ${report.successCount}`
    if (report.failedClientIds.length === 0) {
      message.success(`${label}: 推送完成 (${tip})`)
    } else {
      message.warning(
        `${label}: 推送部分失败 ${tip} · 失败 ${report.failedClientIds.length} (已标 status=3 等下轮自动重试)`
      )
    }
    await runRefresh()
  } catch (e) {
    message.error('推送失败: ' + ((e as Error).message ?? ''))
  } finally {
    replayLoading.value = false
  }
}

function close() {
  emit('update:modelValue', false)
}

function selectChip(kind: SelectionKind) {
  selectedMeta.value = null
  selectionKind.value = kind
}

function closeSelection() {
  selectedMeta.value = null
  selectionKind.value = null
}

function statusTagType(s: NodeStatus): 'success' | 'warning' | 'default' {
  return s === 'ok' ? 'success' : s === 'stale' ? 'warning' : 'default'
}
function statusLabel(s: NodeStatus) {
  return s === 'ok' ? '正常' : s === 'stale' ? '缺配置' : '多配置'
}

function accentBar(a: SectionAccent) {
  return a === 'blue' ? 'bg-blue-500' : a === 'green' ? 'bg-emerald-500' : 'bg-amber-500'
}
function accentText(a: SectionAccent) {
  return a === 'blue' ? 'text-blue-700' : a === 'green' ? 'text-emerald-700' : 'text-amber-700'
}
function sectionHeaderBg(a: SectionAccent) {
  return a === 'blue' ? 'bg-blue-50/60' : a === 'green' ? 'bg-emerald-50/60' : 'bg-amber-50/60'
}

onBeforeUnmount(destroyGraph)
</script>

<template>
  <NModal
    :show="modelValue"
    preset="card"
    class="diff-fullscreen-modal"
    :bordered="false"
    :mask-closable="false"
    :close-on-esc="false"
    @update:show="(v: boolean) => emit('update:modelValue', v)"
  >
    <template #header>
      <span>查看差异 (远端 vs DB, 只读)</span>
    </template>
    <template #header-extra>
      <span v-if="node" class="text-xs text-zinc-500">
        {{ node.serverName || node.serverId }}
        <span v-if="node.serverHost">({{ node.serverHost }})</span>
      </span>
    </template>

    <!-- 摘要 + 操作栏 -->
    <div class="flex items-center justify-between mb-3">
      <div class="flex items-center gap-4 text-sm">
        <div class="flex items-center gap-1" title="远端配置与 DB 一致">
          <NIcon :size="14" color="#52c41a"><CheckCircle2 /></NIcon>
          <span class="text-zinc-500">正常 user</span>
          <span class="font-mono font-semibold">{{ syncStatus?.okEmails.length ?? 0 }}</span>
        </div>
        <div
          class="flex items-center gap-1"
          title="DB 期望存在但远端缺, 客户连不上 / 走默认出口; 点'立即推送'修复"
        >
          <span class="text-zinc-500">缺配置</span>
          <span
            class="font-mono font-semibold"
            :style="totalStale > 0 ? 'color: #fa8c16' : ''"
          >{{ totalStale }}</span>
        </div>
        <div
          class="flex items-center gap-1"
          title="远端存在但 DB 没对应客户端 (一般是 revoke 残留); 不影响其他客户, 不自动清"
        >
          <span class="text-zinc-500">多配置</span>
          <span
            class="font-mono font-semibold"
            :style="totalOrphan > 0 ? 'color: #8c8c8c' : ''"
          >{{ totalOrphan }}</span>
        </div>
      </div>
      <div class="flex items-center gap-2">
        <!-- 图例 -->
        <span class="text-xs text-zinc-400 mr-2 flex items-center gap-2">
          <span class="inline-flex items-center gap-1" title="远端配置与 DB 一致">
            <span class="inline-block w-3 h-3 rounded" style="background:#f6ffed;border:1px solid #52c41a"></span>
            正常
          </span>
          <span class="inline-flex items-center gap-1"
                title="DB 期望存在但远端缺, 客户连不上; 点'立即推送'修复">
            <span class="inline-block w-3 h-3 rounded" style="background:#fff7e6;border:2px solid #fa8c16"></span>
            缺配置
          </span>
          <span class="inline-flex items-center gap-1"
                title="远端存在但 DB 没对应客户端 (revoke 残留); 不自动清">
            <span class="inline-block w-3 h-3 rounded" style="background:#fafafa;border:1px dashed #bfbfbf"></span>
            多配置
          </span>
        </span>
        <NButton quaternary size="small" :loading="loading" @click="runRefresh">
          <template #icon><NIcon><RefreshCw /></NIcon></template>
          重新查看
        </NButton>
      </div>
    </div>

    <NAlert
      v-if="syncStatus && !syncStatus.reachable"
      type="error"
      :show-icon="true"
      class="!mb-3"
    >
      远端不可达 (SSH 不通或 xray 未起), 暂无法查看差异
    </NAlert>

    <NAlert
      v-else-if="!loading && columns.length === 0 && totalOrphan === 0"
      type="info"
      :show-icon="true"
      class="!mb-3"
    >
      <template #icon><NIcon><Info /></NIcon></template>
      该服务器下暂无任何客户端, 远端也无残留配置
    </NAlert>

    <div class="flex items-center justify-between mb-2 text-xs">
      <!-- 层级链: 服务器 → xray 节点 → 入站 → 每客户 (下方表格); chip 可点查看详情 -->
      <div class="flex items-center gap-1 flex-wrap">
        <button
          type="button"
          class="inline-flex items-center gap-1 px-2 py-1 rounded cursor-pointer transition-shadow hover:shadow-sm"
          :class="selectionKind === 'server' ? 'ring-2 ring-blue-300' : ''"
          style="background:#f0f5ff;border:1px solid #adc6ff;color:#1d39c4"
          @click="selectChip('server')"
        >
          <span class="text-[10px] opacity-70">服务器</span>
          <span class="font-mono">{{ node?.serverName || node?.serverId }}</span>
          <span v-if="node?.serverHost" class="text-[10px] opacity-60 font-mono">{{ node.serverHost }}</span>
        </button>
        <span class="text-zinc-300">→</span>
        <button
          type="button"
          class="inline-flex items-center gap-1 px-2 py-1 rounded cursor-pointer transition-shadow hover:shadow-sm"
          :class="selectionKind === 'xray-node' ? 'ring-2 ring-purple-300' : ''"
          style="background:#f9f0ff;border:1px solid #d3adf7;color:#531dab"
          @click="selectChip('xray-node')"
        >
          <span class="text-[10px] opacity-70">xray 节点</span>
          <span class="font-mono">api :{{ node?.xrayApiPort ?? '?' }}</span>
          <span v-if="node?.xrayVersion" class="text-[10px] opacity-60">{{ node.xrayVersion }}</span>
        </button>
        <span class="text-zinc-300">→</span>
        <button
          type="button"
          class="inline-flex items-center gap-1 px-2 py-1 rounded font-mono cursor-pointer transition-shadow hover:shadow-sm"
          :class="selectionKind === 'inbound' ? 'ring-2 ring-sky-300' : ''"
          style="background:#e6f4ff;border:1px solid #1677ff;color:#0958d9"
          @click="selectChip('inbound')"
        >
          <span class="text-[10px] opacity-70">共享 inbound</span>
          in_shared<span v-if="node?.sharedInboundPort">:{{ node.sharedInboundPort }}</span>
          <span v-if="node?.protocol" class="text-[10px] opacity-70">{{ node.protocol }}+{{ node.transport }}</span>
        </button>
        <span class="text-zinc-300">→</span>
        <span class="text-zinc-400">每客户一行: 用户 / 路由规则 / socks5 出站 / 落地 IP</span>
      </div>
      <div class="text-zinc-400 whitespace-nowrap ml-2">
        <NIcon :size="12"><Info /></NIcon>
        拖拽平移 · Ctrl+滚轮缩放
      </div>
    </div>

    <div class="flex-1 min-h-0 flex gap-3">
      <!-- 主图区 -->
      <div class="flex-1 min-w-0 relative rounded border border-zinc-200 dark:border-zinc-700">
        <div ref="graphContainer" class="absolute inset-0"></div>
        <div
          v-if="loading"
          class="absolute inset-0 flex items-center justify-center"
          style="background: rgba(255,255,255,0.6)"
        >
          <NSpin size="medium" />
        </div>
      </div>

      <!-- 详情面板: 服务端 / 存储 / 链路 分段; 每段独立卡片 + 左侧色条; graph 节点选中时含修复操作 -->
      <transition name="slide-fade">
        <div
          v-if="panelView"
          class="w-[28rem] shrink-0 rounded-lg border-2 border-zinc-300 dark:border-zinc-600 shadow-md flex flex-col overflow-hidden"
          style="background:#f4f4f5"
        >
          <!-- 标题栏 -->
          <div
            class="flex items-center gap-2 px-3 py-2.5 border-b-2 border-zinc-300 dark:border-zinc-600"
            :style="{ background: panelView.headerBg }"
          >
            <span class="font-semibold text-sm flex-1 text-zinc-800">{{ panelView.title }}</span>
            <NTag v-if="panelView.status" size="small" :type="statusTagType(panelView.status)">
              {{ statusLabel(panelView.status) }}
            </NTag>
            <NButton quaternary size="tiny" @click="closeSelection">
              <template #icon><NIcon><XIcon /></NIcon></template>
            </NButton>
          </div>

          <!-- 可滚动内容区: section 卡片堆叠, 各自带左侧色条 / 标题色 -->
          <div class="flex-1 min-h-0 overflow-y-auto p-2.5 space-y-2.5">
            <div
              v-for="sec in panelView.sections"
              :key="sec.heading"
              class="bg-white dark:bg-zinc-800 rounded-md border border-zinc-200 dark:border-zinc-700 shadow-sm overflow-hidden"
            >
              <!-- section 标题: 左侧色条 + 强调色文字 -->
              <div
                class="flex items-center gap-2 px-3 py-1.5 border-b border-zinc-100 dark:border-zinc-700"
                :class="sectionHeaderBg(sec.accent)"
              >
                <span class="inline-block w-1 h-3.5 rounded-sm" :class="accentBar(sec.accent)"></span>
                <span class="text-xs font-semibold" :class="accentText(sec.accent)">
                  {{ sec.heading }}
                </span>
              </div>
              <!-- section 行 -->
              <div class="px-3 py-2 text-xs space-y-1.5">
                <div
                  v-for="row in sec.rows"
                  :key="row.label"
                  class="flex items-start gap-2 rounded"
                  :class="sec.highlightActive && row.active
                    ? 'bg-amber-50 dark:bg-amber-900/20 px-1.5 -mx-1.5 py-0.5'
                    : (row.active ? 'font-medium' : '')"
                >
                  <span class="text-zinc-500 w-24 shrink-0 leading-5">
                    <template v-if="sec.highlightActive && row.active">▸ </template>{{ row.label }}
                  </span>
                  <span
                    class="flex-1 break-all leading-5 text-zinc-800 dark:text-zinc-200"
                    :class="row.mono ? 'font-mono text-[11px]' : ''"
                  >{{ row.value }}</span>
                  <NButton
                    v-if="row.value && row.value !== '—' && row.copyable !== false"
                    quaternary
                    size="tiny"
                    @click="copyText(row.value)"
                  >
                    <template #icon><NIcon><Copy /></NIcon></template>
                  </NButton>
                </div>
              </div>
            </div>
          </div>

          <!-- 操作: 仅图节点 + 有可修复项才显示 -->
          <div
            v-if="selectionKind === 'node' && selectedMeta"
            class="px-3 py-2 border-t-2 border-zinc-300 dark:border-zinc-600 bg-white dark:bg-zinc-800"
          >
            <NButton
              v-if="panelView.actionClientId"
              type="warning"
              size="small"
              :loading="syncingClientId === panelView.actionClientId"
              block
              @click="onSyncOne(panelView.actionClientId!)"
            >
              推送修复此项
            </NButton>
            <div
              v-else-if="selectedMeta.status === 'orphan'"
              class="text-zinc-500 text-[11px] leading-relaxed"
            >
              多配置: 远端存在但 DB 没对应客户端, 不自动清理.
              如需清除请在服务器手动 <code>xray api rmu / rmrules / rmo</code>.
            </div>
            <div v-else class="text-zinc-400 text-[11px] text-center py-1">
              该链路一切正常, 无需操作
            </div>
          </div>
        </div>
      </transition>
    </div>

    <template #footer>
      <NSpace justify="end">
        <NButton size="small" @click="close">关闭</NButton>
        <NButton
          type="warning"
          size="small"
          :loading="replayLoading"
          :disabled="replayButton.disabled"
          :title="replayButton.tip"
          @click="onReplay"
        >
          {{ replayButton.label }}
        </NButton>
      </NSpace>
    </template>
  </NModal>
</template>

<style>
/* preset=card 默认会给 NCard 套 max-width / 圆角 / padding, 全屏弹框需要全打掉.
   注意 naive-ui 用的是 .n-card-content (单连字符), 不是 BEM 双下划线; 之前错写成 __content 全没命中. */
.diff-fullscreen-modal.n-card,
.diff-fullscreen-modal .n-card {
  width: 100vw !important;
  max-width: 100vw !important;
  height: 100vh !important;
  max-height: 100vh !important;
  border-radius: 0 !important;
  display: flex !important;
  flex-direction: column !important;
}
.diff-fullscreen-modal .n-card-content {
  flex: 1 1 auto !important;
  min-height: 0 !important;
  display: flex !important;
  flex-direction: column !important;
}

/* 详情面板从右侧滑入 */
.slide-fade-enter-active,
.slide-fade-leave-active {
  transition: transform 180ms ease, opacity 180ms ease;
}
.slide-fade-enter-from,
.slide-fade-leave-to {
  transform: translateX(20px);
  opacity: 0;
}
</style>
