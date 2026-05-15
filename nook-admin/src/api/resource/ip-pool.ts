import request from '@/api/request'
import { useUserStore } from '@/stores/user'

/** IP 池条目: SOCKS5 落地节点 (基于 dante-server + PAM 用户认证); ip_address 即用户对外暴露的"独享 IP", 同时也是 SOCKS5 服务监听地址. */
export interface ResourceIpPool {
  id: string
  region: string
  ipTypeId: string
  ipAddress: string
  socks5Port?: number
  socks5Username?: string
  /** 明文 SOCKS5 密码 — DB 明文存储, 后台受信场景直接下发, 编辑时 fill 进 type=password 输入框 */
  socks5Password?: string
  /** 1=available 2=occupied 3=testing 4=blacklisted 5=cooling 6=degraded */
  status: number
  assignedMemberId?: string
  assignedAt?: string
  coolingUntil?: string
  assignCount?: number
  lastHealthAt?: string
  /** 部署模式: 1=自部署 2=第三方; 详情接口下发, 编辑表单 fill 回填. */
  provisionMode?: number
  /** dante 日志关键字组合 (空格分隔); 例 'connect disconnect error'. */
  logLevel?: string
  /** dante logoutput 路径; 例 /var/log/sockd.log. */
  logPath?: string
  /** systemd 开机自启 (1=enable 0=disable). */
  autostartEnabled?: number
  /** 部署时是否配 UFW (1=配 0=跳过). */
  firewallEnabled?: number
  /** UFW allow 来源 CIDR; 空 = 0.0.0.0/0. */
  firewallAllowFrom?: string
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
  /** 采购带宽上限 (Mbps); null = 不限/未填; 仅账面记录, 后续套餐侧消费. */
  bandwidthMbps?: number
  /** 采购流量上限 (GB); null = 不限/未填; 仅账面记录. */
  trafficQuotaGb?: number
  remark?: string
  createdAt?: string
  updatedAt?: string
}

export interface ResourceIpPoolQuery {
  pageNo?: number
  pageSize?: number
  keyword?: string
  status?: number
  region?: string
  ipTypeId?: string
}

export interface ResourceIpPoolSaveDTO {
  region?: string
  ipTypeId?: string
  ipAddress?: string
  socks5Port?: number
  socks5Username?: string
  socks5Password?: string
  status?: number
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
  /** UFW allow 来源 CIDR; 空 = 0.0.0.0/0. */
  firewallAllowFrom?: string
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

/** 状态码 → 中文展示标签; UI 端用此映射, 颜色映射由 view 自行决定 (IpPoolList.statusTagType). */
export const IP_POOL_STATUS_LABELS: Record<number, string> = {
  1: '可分配',
  2: '已占用',
  3: '测试中',
  4: '黑名单',
  5: '冷却中',
  6: '降级'
}

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

// ===== SOCKS5 落地节点 运维 (走 IP 池条目存储的 SSH 凭据, 不再问用户) =====

/** SOCKS5 (dante) 服务运行状态; 与 xray Status 同口径, 让前端复刻 XrayNodeStatusDialog. */
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

export interface Socks5InstallDTO {
  // 远端主机 SSH 凭据 (一次性, 不存)
  sshHost: string
  sshPort: number
  sshUser: string
  /** SSH 密码 (必填) */
  sshPassword: string
  /** SSH 会话握手超时(秒); 5-600 */
  sshTimeoutSeconds: number
  /** SSH 单条命令超时(秒); 5-300 */
  sshOpTimeoutSeconds: number
  /** SCP 上传超时(秒); 5-600 */
  sshUploadTimeoutSeconds: number
  /** 安装脚本最大耗时(秒); 60-3600 */
  installTimeoutSeconds: number

  // SOCKS5 服务参数 — 部署成功后由前端按需调 createIpPool 落库
  socksPort: number
  socksUser: string
  socksPass: string
  /** UFW allow from 来源 CIDR; 推荐填中转线路服务器公网 IP */
  allowFrom?: string
  installUfw: boolean

  /** dante log 关键字 (空格分隔); 留空走默认 'connect disconnect error'. */
  logLevel?: string
  /** dante logoutput 路径; 留空走默认 $INSTALL_DIR/logs/sockd.log. */
  logPath?: string
  /** systemd 开机自启 (不传默认 true). */
  autostartEnabled?: boolean
  /** SOCKS5 安装目录; 留空走默认 /home/socks5. */
  installDir?: string
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
