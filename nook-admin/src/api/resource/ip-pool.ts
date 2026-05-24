import request from '@/api/request'
import { useUserStore } from '@/stores/user'

/**
 * IP 池条目 (v3): SOCKS5 落地节点; ip_address 即用户独享对外 IP.
 *
 * - lifecycleState (装机): INSTALLING/READY/LIVE/RETIRED
 * - status (占用): AVAILABLE/RESERVED/OCCUPIED/COOLING (旧 6 值 TINYINT → 新 4 值字符串 ENUM)
 * - assignedMemberId/assignedAt → occupiedByMemberId/occupiedAt (v3 改名)
 * - region 改为字典码 (FK → resource_region.code)
 */
export interface ResourceIpPool {
  id: string
  /** 区域码 (FK → resource_region.code). */
  region: string
  ipTypeId: string
  /** 装机生命周期: INSTALLING/READY/LIVE/RETIRED. */
  lifecycleState: string
  ipAddress: string
  socks5Port?: number
  socks5Username?: string
  /** 明文 SOCKS5 密码; DB 明文存储, 编辑时 fill 进 type=password. */
  socks5Password?: string
  /** 占用状态: AVAILABLE / RESERVED / OCCUPIED / COOLING. */
  status: string
  occupiedByMemberId?: string
  occupiedAt?: string
  coolingUntil?: string
  reservedExpiresAt?: string
  lastHealthAt?: string
  /** 部署模式: 1=自部署 2=第三方. */
  provisionMode?: number
  /** dante 日志关键字组合 (空格分隔); 例 'connect disconnect error'. */
  logLevel?: string
  /** dante logoutput 路径; 例 /var/log/sockd.log. */
  logPath?: string
  /** systemd 开机自启 (1=enable 0=disable). */
  autostartEnabled?: number
  /** 部署时是否配 UFW (1=配 0=跳过). */
  firewallEnabled?: number
  /** SOCKS5 安装目录; 默认 /home/socks5; logs/info.txt 等运维资产放这里. */
  installDir?: string
  /** SSH 主机 (默认 = ipAddress); 后续运维 (详情/日志/切自启) 用 */
  sshHost?: string
  /** SSH 端口 (默认 22). */
  sshPort?: number
  /** SSH 用户. */
  sshUser?: string
  /** 明文 SSH 密码; 后台受信网络场景下发. */
  sshPassword?: string
  /** 采购带宽上限 (Mbps); 仅账面记录. */
  bandwidthMbps?: number
  /** 采购流量上限 (GB); 仅账面记录. */
  trafficQuotaGb?: number
  /** 月度成本 USD. */
  costMonthlyUsd?: number
  /** 账单日 1-28. */
  billingCycleDay?: number
  /** IP 到期日 YYYY-MM-DD. */
  expiresAt?: string
  /** dante 实际限速 Mbps; 0=不限. */
  bandwidthLimitMbps?: number
  remark?: string
  createdAt?: string
  updatedAt?: string
}

/** IP 池核心字段 (拆 dialog 用; lifecycle 走 /lifecycle 接口). */
export interface IpPoolCoreUpdateDTO {
  region: string
  ipTypeId: string
  ipAddress: string
  provisionMode: number
  remark?: string
}

/** SSH 凭据 (provision_mode=1 用; sshPassword 留空 = 保留原值). */
export interface IpPoolCredential {
  ipId?: string
  sshHost?: string
  sshPort?: number
  sshUser?: string
  sshPassword?: string
}

/** 账面 (仅记录, 不 enforce). */
export interface IpPoolBilling {
  ipId?: string
  bandwidthMbps?: number
  trafficQuotaGb?: number
  costMonthlyUsd?: number
  billingCycleDay?: number
  expiresAt?: string
}

/** dante 配置 + 限速; socks5Password 留空 = 保留原值. */
export interface IpPoolSocks5 {
  ipId?: string
  socks5Port: number
  socks5Username?: string
  socks5Password?: string
  logLevel?: string
  logPath: string
  autostartEnabled?: number
  firewallEnabled?: number
  installDir?: string
  /** dante 实际限速 Mbps; 0=不限. */
  bandwidthLimitMbps?: number
}

export interface ResourceIpPoolQuery {
  pageNo?: number
  pageSize?: number
  keyword?: string
  /** 装机生命周期过滤. */
  lifecycleState?: string
  /** 占用状态过滤. */
  status?: string
  region?: string
  ipTypeId?: string
}

export interface ResourceIpPoolSaveDTO {
  region?: string
  ipTypeId?: string
  lifecycleState?: string
  ipAddress?: string
  socks5Port?: number
  socks5Username?: string
  socks5Password?: string
  /** 部署模式: 1=SELF_DEPLOY 自部署, 2=EXTERNAL 第三方; 后端 @NotNull, create/update 都必填. */
  provisionMode?: number
  /** dante 日志 (空格分隔关键字); 留空走默认 'connect disconnect error'. */
  logLevel?: string
  /** dante logoutput 路径; 留空走默认 /var/log/sockd.log. */
  logPath?: string
  /** systemd 开机自启 (1/0); 留空默认 1. */
  autostartEnabled?: number
  /** 部署时是否配 UFW (1/0); 留空默认 1. */
  firewallEnabled?: number
  /** SOCKS5 安装目录; 默认 /home/socks5. */
  installDir?: string
  /** SSH 主机 (默认 = ipAddress). */
  sshHost?: string
  /** SSH 端口 (默认 22). */
  sshPort?: number
  /** SSH 用户. */
  sshUser?: string
  /** SSH 密码; Update 留空 = 保留原值. */
  sshPassword?: string
  /** 采购带宽上限 (Mbps); 留空 = 不限/未填. */
  bandwidthMbps?: number
  /** 采购流量上限 (GB); 留空 = 不限/未填. */
  trafficQuotaGb?: number
  /** 月度成本 USD. */
  costMonthlyUsd?: number
  /** 账单日 1-28. */
  billingCycleDay?: number
  /** IP 到期日 YYYY-MM-DD. */
  expiresAt?: string
  remark?: string
}

/** 部署模式枚举值 → 中文标签; 与后端 ResourceIpPoolProvisionModeEnum 对齐. */
export const IP_POOL_PROVISION_MODE_LABELS: Record<number, string> = {
  1: '自部署',
  2: '第三方'
}

/**
 * dante 日志级别预设 (实际是 dante log 事件关键字组合, 不是 syslog level).
 *
 * - 仅错误: 极简, 只记录失败
 * - 警告: 连接事件 + 错误 (默认; 既能审计又不淹没日志)
 * - 详细: 加上 IO 操作 + TCP 信息, 用于排障 / 审计
 *
 * 6 种关键字含义 (dante 文档): connect / disconnect / data / error / iooperation / tcpinfo.
 */
export const DANTE_LOG_LEVEL_OPTIONS = [
  { label: '仅错误', value: 'error' },
  { label: '警告', value: 'connect disconnect error' },
  { label: '详细', value: 'connect disconnect error iooperation tcpinfo' }
] as const

/** 新建 IP 池 / 部署 SOCKS5 时的默认级别 ("警告"). */
export const DANTE_LOG_LEVEL_DEFAULT = 'connect disconnect error'

export interface PageResult<T> {
  total: number
  records: T[]
}

/** 装机生命周期 → 中文标签 + 颜色. */
export const IP_POOL_LIFECYCLE_LABELS: Record<string, string> = {
  INSTALLING: '装机中',
  READY: '待上线',
  LIVE: '运行中',
  RETIRED: '已退役'
}

export const IP_POOL_LIFECYCLE_TAG_TYPE: Record<string, 'info' | 'warning' | 'success' | 'default'> = {
  INSTALLING: 'info',
  READY: 'warning',
  LIVE: 'success',
  RETIRED: 'default'
}

export const IP_POOL_LIFECYCLE_OPTIONS = [
  { label: '全部', value: undefined as string | undefined },
  { label: '装机中', value: 'INSTALLING' },
  { label: '待上线', value: 'READY' },
  { label: '运行中', value: 'LIVE' },
  { label: '已退役', value: 'RETIRED' }
]

/** 占用状态 → 中文标签 (v3: 4 值 ENUM, 不再是 number). */
export const IP_POOL_STATUS_LABELS: Record<string, string> = {
  AVAILABLE: '可分配',
  RESERVED: '预占中',
  OCCUPIED: '已占用',
  COOLING: '冷却中'
}

export const IP_POOL_STATUS_OPTIONS = [
  { label: '全部', value: undefined as string | undefined },
  { label: '可分配', value: 'AVAILABLE' },
  { label: '预占中', value: 'RESERVED' },
  { label: '已占用', value: 'OCCUPIED' },
  { label: '冷却中', value: 'COOLING' }
]

export function pageIpPool(params: ResourceIpPoolQuery) {
  return request.get<unknown, PageResult<ResourceIpPool>>('/admin/resource/ip-pool/page', { params })
}

export function getIpPoolDetail(id: string) {
  return request.get<unknown, ResourceIpPool>('/admin/resource/ip-pool/get', { params: { id } })
}

export function createIpPool(dto: ResourceIpPoolSaveDTO) {
  return request.post<unknown, ResourceIpPool>('/admin/resource/ip-pool/create', dto)
}

export function updateIpPool(id: string, dto: ResourceIpPoolSaveDTO) {
  return request.put<unknown, ResourceIpPool>('/admin/resource/ip-pool/update', dto, { params: { id } })
}

export function deleteIpPool(id: string) {
  return request.delete<unknown, void>('/admin/resource/ip-pool/delete', { params: { id } })
}

/** 退订: occupied → cooling, 一段时间后由调度器扫回 available. */
export function releaseIpPool(id: string) {
  return request.post<unknown, void>('/admin/resource/ip-pool/release', null, { params: { id } })
}

/** 切换 lifecycle_state; admin 上线 / 退役流转用. */
export function transitionIpPoolLifecycle(id: string, state: string) {
  return request.post<unknown, boolean>(
    '/admin/resource/ip-pool/lifecycle',
    null,
    { params: { id, state } }
  )
}

// ===== 子表分段编辑 (拆 4 个 dialog 各自调) =====

/** 更新核心字段 (region/ipTypeId/ipAddress/provisionMode/remark; lifecycle 走 /lifecycle). */
export function updateIpPoolCore(id: string, dto: IpPoolCoreUpdateDTO) {
  return request.put<unknown, boolean>(`/admin/resource/ip-pool/${id}/core`, dto)
}

/** 取 SSH 凭据 (编辑 dialog prefill; 密码字段空着, 改密码才填). */
export function getIpPoolCredential(id: string) {
  return request.get<unknown, IpPoolCredential | null>(`/admin/resource/ip-pool/${id}/credential`)
}

/** 更新 SSH 凭据 (sshPassword 留空 = 保留原值). */
export function updateIpPoolCredential(id: string, dto: IpPoolCredential) {
  return request.put<unknown, boolean>(`/admin/resource/ip-pool/${id}/credential`, dto)
}

/** 取账面. */
export function getIpPoolBilling(id: string) {
  return request.get<unknown, IpPoolBilling | null>(`/admin/resource/ip-pool/${id}/billing`)
}

/** 更新账面. */
export function updateIpPoolBilling(id: string, dto: IpPoolBilling) {
  return request.put<unknown, boolean>(`/admin/resource/ip-pool/${id}/billing`, dto)
}

/** 取 dante 配置 + 限速. */
export function getIpPoolSocks5(id: string) {
  return request.get<unknown, IpPoolSocks5 | null>(`/admin/resource/ip-pool/${id}/socks5`)
}

/** 更新 dante 配置 + 限速 (socks5Password 留空 = 保留原值; 改 bandwidthLimitMbps 触发链路校验). */
export function updateIpPoolSocks5(id: string, dto: IpPoolSocks5) {
  return request.put<unknown, boolean>(`/admin/resource/ip-pool/${id}/socks5`, dto)
}

// ===== SOCKS5 落地节点 运维 (走 IP 池条目存储的 SSH 凭据, 不再问用户) =====

/** SOCKS5 (dante) 服务运行状态; 与 xray Status 同口径. */
export interface Socks5ServiceStatus {
  /** systemd unit, 固定 "danted" */
  unit?: string
  /** active / inactive / failed / unknown */
  active?: string
  /** dpkg-query 拿到的 dante 包版本 */
  version?: string
  /** ActiveEnterTimestamp 重格式化后的字符串 */
  uptimeFrom?: string
  /** ss -ltn 抓的 socks5 端口监听行 (多行, 前端按 \n 拆分展示) */
  listening?: string
  /** is-enabled 输出: enabled / disabled / static / masked */
  enabled?: string
  /** ufw status verbose 输出原文 */
  ufwStatus?: string
  /** 远端主机基本信息; 详情弹框默认折叠展示 */
  hostInfo?: HostInfo
}

/** 远端主机基本信息; 与 xray server.ts 同结构, 复用一份语义 (跟后端 HostInfoRespVO 对齐). */
export interface HostInfo {
  hostname?: string
  kernel?: string
  osRelease?: string
  systemUptime?: string
  loadAvg?: string
  memory?: string
  disk?: string
  timezone?: string
}

/** SOCKS5 日志级别过滤 (复用 xray 同语义). */
export type Socks5LogLevel = 'all' | 'warning' | 'err'

/** SOCKS5 日志快照 (复用 xray ServiceLog 同结构). */
export interface Socks5Log {
  unit?: string
  lines: number
  level: Socks5LogLevel
  keyword?: string
  log?: string
}

/** 拉 SOCKS5 (dante) 服务状态 + version / 监听端口. */
export function getSocks5Status(id: string) {
  return request.get<unknown, Socks5ServiceStatus>('/admin/resource/ip-pool/socks5-status', { params: { id } })
}

/** 切 SOCKS5 开机自启 (systemctl enable/disable + DB.autostart_enabled 同步). */
export function setSocks5Autostart(id: string, enabled: boolean) {
  return request.post<unknown, boolean>(
    '/admin/resource/ip-pool/socks5-autostart', null, { params: { id, enabled } })
}

/** 拉 SOCKS5 (dante) journalctl 日志. */
export function getSocks5Log(
  id: string,
  opts?: { lines?: number; level?: Socks5LogLevel; keyword?: string }
) {
  return request.get<unknown, Socks5Log>('/admin/resource/ip-pool/socks5-log', {
    params: {
      id,
      lines: opts?.lines,
      level: opts?.level === 'all' ? undefined : opts?.level,
      keyword: opts?.keyword?.trim() || undefined
    }
  })
}

/**
 * 拉 SOCKS5 (dante) 自己的日志文件 (DB.log_path 指向, 默认 /home/socks5/logs/sockd.log).
 * 跟 systemd journal 互补 — file 才有真正的拨号 / 流量记录.
 */
export function getSocks5LogFile(
  id: string,
  opts?: { lines?: number; keyword?: string }
) {
  return request.get<unknown, Socks5Log>('/admin/resource/ip-pool/socks5-log-file', {
    params: {
      id,
      lines: opts?.lines,
      keyword: opts?.keyword?.trim() || undefined
    }
  })
}

/** SOCKS5 测试入参; 全部必填, 后端做非空 + 范围校验, 不再兜底. */
export interface Socks5TestParams {
  /** echo-IP 端点; 必须 http/https */
  echoUrl: string
  /** TCP 建连超时毫秒; 500-60000 */
  connectTimeoutMs: number
  /** HTTP 读响应超时毫秒; 500-60000 */
  readTimeoutMs: number
}

/** Socks5TestParams 的前端默认值; 给弹框初始化用, 用户可改. */
export const SOCKS5_TEST_DEFAULTS: Readonly<Socks5TestParams> = Object.freeze({
  echoUrl: 'https://api.ipify.org/',
  connectTimeoutMs: 5000,
  readTimeoutMs: 10000
})

/**
 * SOCKS5 拨号测试结果; 后端不解析响应体, 透传 HTTP status + body 原文给前端控制台.
 * success 仅表示"拨号 + HTTP 往返完成", 4xx/5xx 也算 success=true.
 */
export interface Socks5TestResult {
  success: boolean
  elapsedMs: number
  echoUrl: string
  connectTimeoutMs: number
  readTimeoutMs: number
  /** HTTP 响应状态码; success=true 时有值, 拨号失败时 0 */
  httpStatus: number
  /** 响应体原文; success=true 时有值 (可能空串) */
  rawResponse?: string
  /** 拨号失败原因; 仅 success=false 时有值 */
  error?: string
}

/**
 * 通过该 IP 的 SOCKS5 凭据拨号公网 echo-IP 端点, 验证 SOCKS5 是否真的工作 + 出网 IP 是否符合预期.
 *
 * @param id     resource_ip_pool.id
 * @param params 全字段必填; 调用方一般 `{ ...SOCKS5_TEST_DEFAULTS, echoUrl: 用户输入 }` 拼造
 */
export function testIpPoolSocks5(id: string, params: Socks5TestParams) {
  return request.post<unknown, Socks5TestResult>('/admin/resource/ip-pool/test-socks5', params, { params: { id } })
}

// ===== SOCKS5 独立部署 (走 nook-biz-node Socks5Controller, 流式 HTTP, 不绑定 IP 池条目) =====

/**
 * SOCKS5 装机入参. 装机成功后 backend 事务内一次性落 6 行 (主表 + 5 子表),
 * 通过 lineSink 推 `[nook] ✔ 已落库 ipId=<id>` 给前端跳详情.
 *
 * 默认值规则: 前端 form 给 default, 后端 @NotBlank/@NotNull 强校验, 不再 blankToDefault 兜底.
 */
export interface Socks5InstallDTO {
  // 主表: 资源归属
  region: string
  ipTypeId: string
  remark?: string

  // SSH 凭据 (落 credential 子表)
  sshHost: string
  sshPort: number
  sshUser: string
  sshPassword: string
  sshTimeoutSeconds: number
  sshOpTimeoutSeconds: number
  sshUploadTimeoutSeconds: number
  installTimeoutSeconds: number

  // dante 业务配置 (落 socks5 子表)
  socksPort: number
  socksUser: string
  socksPass: string
  logLevel: string
  installUfw: boolean

  // 装机产物 (落 install 子表; 前端 default, 后端校验)
  logPath: string
  autostartEnabled: boolean
  logRotate: boolean
  installDir: string
  confPath: string
  pamFile: string
  pwdFile: string
  systemdUnit: string
}

/**
 * SOCKS5 凭据热同步入参; SSH 凭据 ad-hoc (一次性), SOCKS5 内容从 DB 现值读, 不在入参里.
 */
export interface Socks5SyncCredsDTO {
  sshUser: string
  sshPassword: string
  sshPort: number
  sshTimeoutSeconds: number
  sshOpTimeoutSeconds: number
  sshUploadTimeoutSeconds: number
  installTimeoutSeconds: number
}

/**
 * 流式同步 SOCKS5 凭据 — landing dante config 热更新 + fra-line outbound 重建.
 * 跟 installSocks5Stream 同款 chunked transfer 模式.
 */
export async function syncSocks5CredsStream(
  ipId: string,
  dto: Socks5SyncCredsDTO,
  onChunk: (chunk: string) => void,
  signal?: AbortSignal
): Promise<void> {
  const userStore = useUserStore()
  const res = await fetch(`/api/admin/resource/ip-pool/sync-creds?id=${encodeURIComponent(ipId)}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: userStore.token
    },
    body: JSON.stringify(dto),
    signal
  })
  if (res.status === 401) {
    userStore.clear()
    throw new Error('登录已过期, 请重新登录')
  }
  if (!res.ok) {
    const text = await res.text()
    throw new Error(`HTTP ${res.status}: ${text || res.statusText}`)
  }
  if (!res.body) {
    throw new Error('当前浏览器不支持流式响应 (Response.body 为空)')
  }
  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    const text = decoder.decode(value, { stream: true })
    if (text) onChunk(text)
  }
  const tail = decoder.decode()
  if (tail) onChunk(tail)
}

/**
 * 流式部署 SOCKS5 — 后端 chunked transfer 边跑边吐 stdout, 前端 fetch + ReadableStream 边读边回调。
 * 部署不再绑定 IP 池条目; 部署成功后由前端按需调 createIpPool 把 SOCKS5 凭据落库。
 */
export async function installSocks5Stream(
  dto: Socks5InstallDTO,
  onChunk: (chunk: string) => void,
  signal?: AbortSignal
): Promise<void> {
  const userStore = useUserStore()
  const res = await fetch('/api/admin/resource/ip-pool/install-socks5', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: userStore.token
    },
    body: JSON.stringify(dto),
    signal
  })
  if (res.status === 401) {
    userStore.clear()
    throw new Error('登录已过期, 请重新登录')
  }
  if (!res.ok) {
    const text = await res.text()
    throw new Error(`HTTP ${res.status}: ${text || res.statusText}`)
  }
  if (!res.body) {
    throw new Error('当前浏览器不支持流式响应 (Response.body 为空)')
  }
  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    const text = decoder.decode(value, { stream: true })
    if (text) onChunk(text)
  }
  const tail = decoder.decode()
  if (tail) onChunk(tail)
}
