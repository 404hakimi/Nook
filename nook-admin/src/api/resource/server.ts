import request from '@/api/request'
import { useUserStore } from '@/stores/user'

/**
 * 服务器实体 (v3): 主表存基础信息 + 装机生命周期; capacity / runtime 高频字段拆到子表.
 *
 * - lifecycleState 替代旧的 status (1=运行/2=维护/3=下线 → INSTALLING/READY/LIVE/RETIRED)
 * - region 改为字典码 (FK → resource_region.code), 表单走 listEnabledRegions 下拉
 * - 新增 v3 字段: domain / cfZoneId / cfRecordId / costMonthlyUsd / billingCycleDay / expiresAt / maxConcurrentClients
 * - monthlyTrafficGb 已迁到 resource_server_capacity 子表, 不在 server 主表
 */
export interface ResourceServer {
  id: string
  name: string
  host: string
  sshPort?: number
  sshUser?: string
  sshPassword?: string
  sshTimeoutSeconds?: number
  sshOpTimeoutSeconds?: number
  sshUploadTimeoutSeconds?: number
  installTimeoutSeconds?: number

  /** 运营商承诺峰值带宽 Mbps; 仅账面展示. */
  bandwidthMbps?: number

  /** 线路机域名 (e.g., jp-01.nook.com); LIVE 前置必填. */
  domain?: string

  /** Cloudflare Zone ID. */
  cfZoneId?: string

  /** Cloudflare DNS record ID. */
  cfRecordId?: string

  /** 月度成本 USD (内部成本核算). */
  costMonthlyUsd?: number

  /** 账单日 1-28; 月度流量重置参考. */
  billingCycleDay?: number

  /** 服务器到期日 (机房续费) YYYY-MM-DD. */
  expiresAt?: string

  /** allocator 硬上限; 1C1G=50-100, 2C2G=200, 4C4G=500, 8C8G=1000. */
  maxConcurrentClients?: number

  /** 装机生命周期; 取值 INSTALLING / READY / LIVE / RETIRED. */
  lifecycleState: string

  totalIpCount?: number
  idcProvider?: string

  /** 区域码 (FK → resource_region.code). */
  region?: string

  remark?: string
  createdAt?: string
  updatedAt?: string
}

export interface ResourceServerQuery {
  pageNo?: number
  pageSize?: number
  keyword?: string
  /** 装机生命周期过滤 (INSTALLING/READY/LIVE/RETIRED); 空=全部. */
  lifecycleState?: string
  region?: string
}

export interface ResourceServerSaveDTO {
  name?: string
  host?: string
  sshPort?: number
  sshUser?: string
  sshPassword?: string
  sshTimeoutSeconds?: number
  sshOpTimeoutSeconds?: number
  sshUploadTimeoutSeconds?: number
  installTimeoutSeconds?: number
  bandwidthMbps?: number
  domain?: string
  cfZoneId?: string
  cfRecordId?: string
  costMonthlyUsd?: number
  billingCycleDay?: number
  expiresAt?: string
  maxConcurrentClients?: number
  idcProvider?: string
  region?: string
  lifecycleState?: string
  remark?: string
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

/** SSH 列出远端网卡 (排除 lo); 失败返空 list, 前端 fallback 到 "auto". */
export function listNetworkInterfaces(id: string) {
  return request.get<unknown, string[]>('/admin/resource/server/network-interfaces', { params: { id } })
}

/** Agent 装机 meta: backend 已知数据, 前端 prefill 表单用; 用户可改. */
export interface AgentInstallMeta {
  /** Backend 公网 URL (config 读). */
  backendUrl: string
  /** Frontline + 选了 server 才有: xray binary 绝对路径. */
  xrayBin?: string
  /** Frontline + 选了 server 才有: xray api server 端口. */
  xrayApiPort?: number
  /** 选了 server 才有: SSH 默认 (resource_server 表读). */
  sshTimeoutSeconds?: number
  sshOpTimeoutSeconds?: number
  sshUploadTimeoutSeconds?: number
  installTimeoutSeconds?: number
}

export function getAgentInstallMeta(role: 'frontline' | 'landing', serverId?: string | null) {
  return request.get<unknown, AgentInstallMeta>(
    '/admin/agent/install-meta',
    { params: { role, serverId: serverId || undefined } }
  )
}

export function getServerDetail(id: string) {
  return request.get<unknown, ResourceServer>('/admin/resource/server/get', { params: { id } })
}

export function createServer(dto: ResourceServerSaveDTO) {
  return request.post<unknown, ResourceServer>('/admin/resource/server/create', dto)
}

export function updateServer(id: string, dto: ResourceServerSaveDTO) {
  return request.put<unknown, ResourceServer>('/admin/resource/server/update', dto, { params: { id } })
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
  backendTimeoutSeconds: number
  heartbeatIntervalSeconds: number
  nicIntervalSeconds: number
  /** auto | eth0 | ens5 ... */
  nicInterface: string
  pollerIntervalSeconds: number
  /** Frontline 必填; 前端从 /agent-install-meta 拿 xray_node 字段后回塞. landing 可省. */
  xrayBin?: string
  xrayApiPort?: number
  // 路径 + URL (前端默认 + 可改, backend 不兜底)
  nookHome: string
  binPath: string
  configPath: string
  systemdUnitPath: string
  backendUrl: string
  // SSH 参数 (per-install override; 不回写 resource_server)
  sshTimeoutSeconds: number
  sshOpTimeoutSeconds: number
  sshUploadTimeoutSeconds: number
  installTimeoutSeconds: number
}

/**
 * SSH 自动装 nook-agent (流式日志); 复用 resource_server 已存 SSH 凭据.
 * 调一次重置 agent_token + 把 token splice 进 dto.configYaml → SSH 跑装机脚本 → agent active.
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
