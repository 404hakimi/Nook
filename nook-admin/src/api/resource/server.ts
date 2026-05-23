import request from '@/api/request'
import { useUserStore } from '@/stores/user'

/**
 * 服务器实体 - 核心字段 (SSH 凭据 / 账面 / DNS 拆到 1:1 子表, 见 ServerCredential / ServerBilling / ServerDns).
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

/** SSH 凭据 (1:1, /admin/resource/server/{id}/credential). */
export interface ServerCredential {
  serverId?: string
  host: string
  sshPort: number
  sshUser?: string
  sshPassword?: string
  sshTimeoutSeconds?: number
  sshOpTimeoutSeconds?: number
  sshUploadTimeoutSeconds?: number
  installTimeoutSeconds?: number
}

/** 账面 (1:1, /admin/resource/server/{id}/billing); 仅财务字段, 业务带宽 / 流量阈值在 capacity. */
export interface ServerBilling {
  serverId?: string
  idcProvider?: string
  costMonthlyUsd?: number
  billingCycleDay?: number
  expiresAt?: string
}

/** DNS 绑定 (1:1, /admin/resource/server/{id}/dns). */
export interface ServerDns {
  serverId?: string
  domain?: string
  cfZoneId?: string
  cfRecordId?: string
}

/** 创建参数 (top-level core + 嵌套 credential 必填, billing/dns 可空). */
export interface ResourceServerCreateDTO {
  name: string
  region: string
  totalIpCount?: number
  remark?: string
  lifecycleState?: string
  credential: ServerCredential
  billing?: ServerBilling
  dns?: ServerDns
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
  region?: string
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
  return request.get<unknown, PageResult<ResourceServer>>('/admin/resource/server/page', { params })
}

/** Server NIC 流量配额 + 已用 + 业务带宽阈值 (监控面板用); 未上报过 NIC 时返 null. */
export interface ServerCapacity {
  serverId: string
  /** 业务月流量阈值 GB; 0/null = 不限. throttle 状态机 90% 触发基数. */
  monthlyTrafficGb?: number
  /** 业务限定带宽 Mbps; 0 = 不限. agent tc qdisc 真实 enforce. */
  bandwidthLimitMbps?: number
  rxBytes?: number
  txBytes?: number
  usedTrafficBytes?: number
  quotaResetPolicy?: string
  throttleState?: string
}

/** 业务阈值编辑入参 (PUT /admin/resource/server/{id}/capacity). */
export interface ServerCapacityUpdateDTO {
  monthlyTrafficGb?: number
  bandwidthLimitMbps?: number
}

export function getServerCapacity(id: string) {
  return request.get<unknown, ServerCapacity | null>('/admin/resource/server/capacity', { params: { id } })
}

/** 更新业务阈值 (月流量配额 + 限定带宽); agent tc / throttle 状态机用. */
export function updateServerCapacity(id: string, dto: ServerCapacityUpdateDTO) {
  return request.put<unknown, boolean>(`/admin/resource/server/${id}/capacity`, dto)
}

/** SSH 列出远端网卡 (排除 lo); 失败返空 list, 前端 fallback 到 "auto". */
export function listNetworkInterfaces(id: string) {
  return request.get<unknown, string[]>('/admin/resource/server/network-interfaces', { params: { id } })
}

/** Agent 装机 host 表; frontline → SERVER, landing → IP_POOL. */
export type AgentHostType = 'SERVER' | 'IP_POOL'

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
  role: 'frontline' | 'landing',
  hostType: AgentHostType,
  hostId?: string | null
) {
  return request.get<unknown, AgentInstallMeta>(
    '/admin/agent/install-meta',
    { params: { role, hostType, hostId: hostId || undefined } }
  )
}

export function getServerDetail(id: string) {
  return request.get<unknown, ResourceServer>('/admin/resource/server/get', { params: { id } })
}

/** 取 SSH 凭据 (编辑 dialog prefill; 密码字段空着, 改密码才填). */
export function getServerCredential(id: string) {
  return request.get<unknown, ServerCredential | null>(`/admin/resource/server/${id}/credential`)
}

/** 取账面. */
export function getServerBilling(id: string) {
  return request.get<unknown, ServerBilling | null>(`/admin/resource/server/${id}/billing`)
}

/** 取 DNS 绑定. */
export function getServerDns(id: string) {
  return request.get<unknown, ServerDns | null>(`/admin/resource/server/${id}/dns`)
}

export function createServer(dto: ResourceServerCreateDTO) {
  return request.post<unknown, ResourceServer>('/admin/resource/server/create', dto)
}

/** 更新核心 (name/region/totalIp/remark; lifecycle 走 /lifecycle). */
export function updateServerCore(id: string, dto: ResourceServerCoreUpdateDTO) {
  return request.put<unknown, boolean>(`/admin/resource/server/${id}/core`, dto)
}

/** 更新 SSH 凭据 (LIVE 后 host/port 硬锁; 密码空保留原值). */
export function updateServerCredential(id: string, dto: ServerCredential) {
  return request.put<unknown, boolean>(`/admin/resource/server/${id}/credential`, dto)
}

/** 更新账面. */
export function updateServerBilling(id: string, dto: ServerBilling) {
  return request.put<unknown, boolean>(`/admin/resource/server/${id}/billing`, dto)
}

/** 更新 DNS 绑定. */
export function updateServerDns(id: string, dto: ServerDns) {
  return request.put<unknown, boolean>(`/admin/resource/server/${id}/dns`, dto)
}

export function deleteServer(id: string) {
  return request.delete<unknown, void>('/admin/resource/server/delete', { params: { id } })
}

/** 切换 lifecycle_state; admin 上线 / 退役流转用. */
export function transitionServerLifecycle(id: string, state: string) {
  return request.post<unknown, boolean>(
    '/admin/resource/server/lifecycle',
    null,
    { params: { id, state } }
  )
}

export interface AgentInstallDTO {
  role: 'frontline' | 'landing'
  /** SERVER (frontline) / IP_POOL (landing); 后端按此分叉去 resource_server 或 resource_ip_pool 查. */
  hostType: AgentHostType
  backendTimeoutSeconds: number
  heartbeatIntervalSeconds: number
  nicIntervalSeconds: number
  /** auto | eth0 | ens5 ... */
  nicInterface: string
  pollerIntervalSeconds: number
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
  id: string,
  dto: AgentInstallDTO,
  onChunk: (chunk: string) => void,
  signal?: AbortSignal
): Promise<void> {
  const userStore = useUserStore()
  const url = `/api/admin/agent/install?id=${encodeURIComponent(id)}`
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
