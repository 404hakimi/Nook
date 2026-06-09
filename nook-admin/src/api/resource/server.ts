import request from '@/api/request'
import { useUserStore } from '@/stores/user'

/**
 * 服务器实体 - 核心字段; SSH 凭据 / 账面 / 线路机扩展 拆到 1:1 子表 (get-server / detail 返回)
 */
export interface ResourceServer {
  id: string
  name: string

  /** 装机生命周期; 取值 INSTALLING / READY / LIVE / RETIRED. */
  lifecycleState: string

  totalIpCount?: number

  /** 区域码 (FK → resource_region.code). */
  region?: string

  remark?: string
  createdAt?: string
  updatedAt?: string
}

/** Agent 在线状态. */
export type AgentOnlineState = 'ONLINE' | 'WARN' | 'TEMP_UNHEALTHY' | 'OFFLINE' | 'NEVER'

/**
 * 线路机列表项 (page-frontline 返回): 主表 + agent 运行时聚合.
 *
 * <p>详情接口 get-server 仅返回 ResourceServer 核心字段; 此 VO 多了 4 子表 + agent_runtime_config 拼装.
 */
export interface ServerFrontlineListItem {
  id: string
  name: string
  /** SSH 主机 (= server.ip_address; canonical). */
  host?: string
  region?: string
  /** INSTALLING / READY / LIVE / RETIRED. */
  lifecycleState: string

  agentVersion?: string
  /** xray 安装版本; null = 未装 xray. */
  xrayVersion?: string
  lastHeartbeatAt?: string
  elapsedSec?: number
  onlineState: AgentOnlineState

  /** 总流量配额 GB; 0/null = 不限. */
  totalGb?: number
  rxBytes?: number
  txBytes?: number
  usedBytes?: number
  /** NORMAL / THROTTLED. */
  throttleState?: string
}

/** SSH 凭据 (1:1, /admin/resource/server/credential?id=...). host = server.ip_address (canonical). */
export interface ServerCredential {
  serverId?: string
  sshPort: number
  sshUser?: string
  sshPassword?: string
  sshTimeoutSeconds?: number
  sshOpTimeoutSeconds?: number
  sshUploadTimeoutSeconds?: number
  installTimeoutSeconds?: number
}

/** 账面 (1:1, /admin/resource/server/billing?id=...); 仅财务字段, 业务带宽 / 流量配额在 quota. */
export interface ServerBilling {
  serverId?: string
  idcProvider?: string
  /** 月成本 CNY. */
  costMonthly?: number
  billingCycleDay?: number
  expiresAt?: string
}

/** 创建参数 (frontline / landing 共用; 按 serverType 分发). */
export interface ResourceServerCreateDTO {
  /** frontline / landing; 决定主表 server_type 与是否走 landing 子表初始化. */
  serverType: 'frontline' | 'landing'
  /** frontline 必填 + 唯一; landing 可空 (后端用 'landing-{ipAddress}' 兜底). */
  name?: string
  /** 出网真实 IP / 域名 (= SSH 连接目标; canonical). */
  ipAddress: string
  region: string
  /** landing 必填; frontline 不传. */
  ipTypeId?: string
  remark?: string
  lifecycleState?: string
  credential: ServerCredential
}

/** 核心字段更新 (lifecycle 走 /lifecycle 接口). */
export interface ResourceServerCoreUpdateDTO {
  name: string
  region: string
  totalIpCount?: number
  remark?: string
}

export interface ResourceServerQuery {
  pageNo?: number
  pageSize?: number
  name?: string
  host?: string
  /** 装机生命周期过滤 (INSTALLING/READY/LIVE/RETIRED); 空=全部. */
  lifecycleState?: string
  /** 区域码集合 (城市选单个, 国家选该国全部城市; 空=全部). */
  regionCodes?: string[]
}

export interface PageResult<T> {
  total: number
  records: T[]
}

/** 装机生命周期 → 中文标签 + 颜色; UI 用 Tag 展示. */
export const SERVER_LIFECYCLE_LABELS: Record<string, string> = {
  INSTALLING: '装机中',
  READY: '待上线',
  LIVE: '运行中',
  RETIRED: '已退役'
}

export const SERVER_LIFECYCLE_TAG_TYPE: Record<string, 'info' | 'warning' | 'success' | 'default'> = {
  INSTALLING: 'info',
  READY: 'warning',
  LIVE: 'success',
  RETIRED: 'default'
}

export const SERVER_LIFECYCLE_OPTIONS = [
  { label: '全部', value: undefined as string | undefined },
  { label: '装机中', value: 'INSTALLING' },
  { label: '待上线', value: 'READY' },
  { label: '运行中', value: 'LIVE' },
  { label: '已退役', value: 'RETIRED' }
]

export function pageServers(params: ResourceServerQuery) {
  const { regionCodes, ...rest } = params
  // 后端 List<String> 用逗号串绑定, 避免 axios 默认数组序列化带 [] 绑不上
  return request.get<unknown, PageResult<ServerFrontlineListItem>>('/admin/resource/server-frontline/page-frontline', {
    params: { ...rest, regionCodes: regionCodes && regionCodes.length ? regionCodes.join(',') : undefined }
  })
}

/** 按区域统计机器数 (线路机 + 落地机); 区域码 → 机器数. */
export function getServerRegionUsage() {
  return request.get<unknown, Record<string, number>>('/admin/resource/server/get-region-usage')
}

/** 拉全部线路机 (单页 100; 个位数集群够用). 跨页 / 大规模请用 pageServers. */
export async function listAllFrontlineServers(): Promise<ServerFrontlineListItem[]> {
  const res = await pageServers({ pageNo: 1, pageSize: 100 })
  return res.records || []
}

/** 单条 server 详情 + agent 运行时聚合 (frontline / landing 共用; detail 页 header + Agent tab 用). */
export function getServerDetailWithRuntime(id: string) {
  return request.get<unknown, ServerFrontlineListItem>('/admin/resource/server/get-detail-with-runtime', { params: { id } })
}

/** Server 配额上限 + 当周期已用 (监控面板用); 未上报过 NIC 时返 null. */
export interface ServerQuota {
  serverId: string
  /** 总流量配额 GB; 0/null = 不限. throttle 状态机触发基数 (建议填机房配额的 ~90%). */
  totalGb?: number
  /** 出站带宽上限 Mbps; 供套餐分配不超卖, 0/空=不参与分配; 不做 tc 整形 (真实限速在落地机). */
  bandwidthMbps?: number
  rxBytes?: number
  txBytes?: number
  usedBytes?: number
  resetPolicy?: string
  /** 按月流量重置日 1-28; 固定不重置时为空. */
  resetDay?: number
  throttleState?: string
}

/** 配额上限编辑入参 (PUT /admin/resource/server/update-quota?id=...). */
export interface ServerQuotaUpdateDTO {
  totalGb?: number
  bandwidthMbps?: number
  /** 重置策略: MONTHLY / FIXED. */
  resetPolicy?: string
  /** 按月流量重置日 1-28; MONTHLY 必填, FIXED 忽略. */
  resetDay?: number
}

/** 周期重置策略选项. */
export const SERVER_QUOTA_RESET_POLICY_OPTIONS = [
  { label: '按月重置 (MONTHLY, 默认)', value: 'MONTHLY' },
  { label: '永不重置 (FIXED)', value: 'FIXED' }
] as const

/** 限流状态 → 中文标签 (read-only, 由 throttle 状态机维护). */
export const SERVER_THROTTLE_STATE_LABELS: Record<string, string> = {
  NORMAL: '正常',
  THROTTLED: '已触发限流'
}

export function getServerQuota(id: string) {
  return request.get<unknown, ServerQuota | null>('/admin/resource/server/get-quota', { params: { id } })
}

/** 更新配额上限 (总流量配额 + 出站带宽); agent tc / throttle 状态机用. */
export function updateServerQuota(id: string, dto: ServerQuotaUpdateDTO) {
  return request.put<unknown, boolean>('/admin/resource/server/update-quota', dto, { params: { id } })
}

/** SSH 列出远端网卡 (排除 lo); 失败返空 list, 前端 fallback 到 "auto". */
export function listNetworkInterfaces(id: string) {
  return request.get<unknown, string[]>('/admin/resource/server/list-network-interface', { params: { id } })
}

/** 探活结果 (后端 ConnectivityTestRespVO). */
export interface ConnectivityTestResult {
  success: boolean
  elapsedMs: number
  error?: string
}

/** 探活: SSH 跑 'true' 验证可达性; 失败已包成 success=false 不抛错. */
export function testServerConnectivity(serverId: string) {
  return request.post<unknown, ConnectivityTestResult>('/admin/resource/server/test-connectivity', null, { params: { id: serverId } })
}

/** Agent 角色 (跟后端 AgentRole enum 一致); 同时也是 agent_type 落库值. */
export type AgentType = 'frontline' | 'landing'

/** Agent 装机 meta: backend 已知数据, 前端 prefill 表单用; 用户可改. */
export interface AgentInstallMeta {
  backendUrl: string
  xrayBin?: string
  xrayApiPort?: number
  sshTimeoutSeconds?: number
  sshOpTimeoutSeconds?: number
  sshUploadTimeoutSeconds?: number
  installTimeoutSeconds?: number
  /** Landing + 选了 ipId 才填: ip_pool.ip_address (admin 展示用). */
  ipAddress?: string
}

export function getAgentInstallMeta(
  role: AgentType,
  sourceId?: string | null
) {
  return request.get<unknown, AgentInstallMeta>(
    '/admin/agent/get-install-meta',
    { params: { role, sourceId: sourceId || undefined } }
  )
}

export function getServerDetail(id: string) {
  return request.get<unknown, ResourceServer>('/admin/resource/server/get-server', { params: { id } })
}

/** 取 SSH 凭据 (编辑 dialog prefill; 密码字段空着, 改密码才填). */
export function getServerCredential(id: string) {
  return request.get<unknown, ServerCredential | null>('/admin/resource/server/get-credential', { params: { id } })
}

/** 取账面. */
export function getServerBilling(id: string) {
  return request.get<unknown, ServerBilling | null>('/admin/resource/server/get-billing', { params: { id } })
}

export function createServer(dto: ResourceServerCreateDTO) {
  return request.post<unknown, ResourceServer>('/admin/resource/server/create-server', dto)
}

/** 更新核心 (name/region/totalIp/remark; lifecycle 走 /transition-lifecycle). */
export function updateServerCore(id: string, dto: ResourceServerCoreUpdateDTO) {
  return request.put<unknown, boolean>('/admin/resource/server/update-core', dto, { params: { id } })
}

/** 更新 SSH 凭据 (LIVE 后 host/port 硬锁; 密码空保留原值). */
export function updateServerCredential(id: string, dto: ServerCredential) {
  return request.put<unknown, boolean>('/admin/resource/server/update-credential', dto, { params: { id } })
}

/** 更新账面. */
export function updateServerBilling(id: string, dto: ServerBilling) {
  return request.put<unknown, boolean>('/admin/resource/server/update-billing', dto, { params: { id } })
}

export function deleteServer(id: string) {
  return request.delete<unknown, void>('/admin/resource/server/delete-server', { params: { id } })
}

/** 切换线路机 lifecycle_state (上线 / 退役). */
export function transitionFrontlineLifecycle(id: string, state: string) {
  return request.post<unknown, boolean>(
    '/admin/resource/server-frontline/transition-lifecycle',
    null,
    { params: { id, state } }
  )
}

export interface AgentInstallDTO {
  /** agent 角色; frontline → resource_server, landing → resource_server (server_type=landing) + socks5_install. */
  role: AgentType
  backendTimeoutSeconds: number
  heartbeatIntervalSeconds: number
  nicIntervalSeconds: number
  /** auto | eth0 | ens5 ... */
  nicInterface: string
  /** Frontline reconcile (对账) 间隔 (秒); landing 忽略. */
  reconcileIntervalSeconds?: number
  xrayBin?: string
  xrayApiPort?: number
  nookHome: string
  binPath: string
  configPath: string
  systemdUnitPath: string
  backendUrl: string
  sshTimeoutSeconds: number
  sshOpTimeoutSeconds: number
  sshUploadTimeoutSeconds: number
  installTimeoutSeconds: number
}

/**
 * SSH 自动装 nook-agent (流式日志); 复用 resource_server 已存 SSH 凭据.
 */
export function agentInstallStream(
  sourceId: string,
  dto: AgentInstallDTO,
  onChunk: (chunk: string) => void,
  signal?: AbortSignal
): Promise<void> {
  const userStore = useUserStore()
  const url = `/api/admin/agent/install-agent?sourceId=${encodeURIComponent(sourceId)}`
  return fetch(url, {
    method: 'POST',
    headers: {
      Authorization: userStore.token,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(dto),
    signal
  }).then(async (res) => {
    if (res.status === 401) { userStore.clear(); throw new Error('登录已过期') }
    if (!res.ok) throw new Error(`HTTP ${res.status}: ${(await res.text()) || res.statusText}`)
    if (!res.body) throw new Error('浏览器不支持流式响应')
    const reader = res.body.getReader()
    const decoder = new TextDecoder()
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      const t = decoder.decode(value, { stream: true })
      if (t) onChunk(t)
    }
    const tail = decoder.decode()
    if (tail) onChunk(tail)
  })
}
