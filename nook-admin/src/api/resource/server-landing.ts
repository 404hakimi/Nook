import request from '@/api/request'
import { useUserStore } from '@/stores/user'
import type { AgentOnlineState } from '@/api/resource/server'

/**
 * 落地机 (SOCKS5 落地节点): server_type='landing' 的 resource_server 行 + resource_server_landing 子表.
 *
 * - lifecycleState (装机): INSTALLING/READY/LIVE/RETIRED
 * - status (占用): AVAILABLE/RESERVED/OCCUPIED
 * - region 为字典码 (FK → resource_region.code)
 */
export interface ServerLanding {
  id: string
  /** 服务器别名 (resource_server.name; landing 创建时由用户填). */
  name: string
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
  /** 占用状态: AVAILABLE / RESERVED / OCCUPIED. */
  status: string
  occupiedByMemberId?: string
  occupiedAt?: string
  reservedExpiresAt?: string
  lastHeartbeatAt?: string
  /** agent 上报版本 (形如 landing-0.8.2); null = 未上报. */
  agentVersion?: string
  /** 在线状态 (与线路机同一判定); 空视为 NEVER. */
  onlineState?: AgentOnlineState
  /** 距上次心跳秒数; null = 从未上报. */
  elapsedSec?: number
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
  /** 装机完成时间 (install 子表 installed_at; null = 未装机) */
  installedAt?: string
  /** dante 探测版本 (sockd -v); null = 未装. */
  danteVersion?: string
  /** landing agent 鉴权 token (装机时自动生成; mask 展示) */
  agentToken?: string
  // SSH 主机 = ipAddress (canonical); 不再单独维护 sshHost
  /** SSH 端口 (默认 22). */
  sshPort?: number
  /** SSH 用户. */
  sshUser?: string
  /** 明文 SSH 密码; 后台受信网络场景下发. */
  sshPassword?: string
  /** 月成本 CNY. */
  costMonthly?: number
  /** 账单日 1-28. */
  billingCycleDay?: number
  /** IP 到期日 YYYY-MM-DD. */
  expiresAt?: string
  /** dante 实际限速 Mbps (quota 子表); 0=不限. */
  bandwidthMbps?: number
  /** 总流量配额 GB (quota 子表); null/0=不限. */
  totalGb?: number
  /** 当周期累计已用字节 (quota 子表; agent push). */
  usedBytes?: number
  /** 重置策略. */
  resetPolicy?: string
  /** NORMAL / THROTTLED. */
  throttleState?: string
  remark?: string
  createdAt?: string
  updatedAt?: string
}

/** 落地机核心字段 (拆 dialog 用; lifecycle 走 /lifecycle 接口). */
export interface ServerLandingCoreUpdateDTO {
  region: string
  ipTypeId: string
  ipAddress: string
  provisionMode: number
  remark?: string
}

/** 账面 (纯财务记录; 实际带宽/流量配额在 ServerLandingQuota). */
export interface ServerLandingBilling {
  serverId?: string
  /** 月成本 CNY. */
  costMonthly?: number
  billingCycleDay?: number
  expiresAt?: string
}

/** dante 配置; socks5Password 留空 = 保留原值. 限速字段拆到 ServerLandingQuota. */
export interface ServerLandingSocks5 {
  serverId?: string
  socks5Port: number
  socks5Username?: string
  socks5Password?: string
  logLevel?: string
  logPath?: string
  autostartEnabled?: number
  firewallEnabled?: number
  installDir?: string
}

/** SOCKS5 装机事实 (resource_server_landing_install 子表; 路径/版本/systemd 服务名等). */
export interface ServerLandingInstall {
  serverId?: string
  /** sockd -v 探测到的 dante 版本. */
  danteVersion?: string
  /** 安装根目录. */
  installDir?: string
  /** dante logoutput 路径. */
  logPath?: string
  /** sockd.conf 绝对路径. */
  confPath?: string
  /** PAM 配置文件路径. */
  pamFile?: string
  /** htpasswd 密码文件路径. */
  pwdFile?: string
  /** systemd 服务名. */
  systemdUnit?: string
  /** systemd 开机自启 1/0. */
  autostartEnabled?: number
  /** 装机时是否配过 UFW. */
  firewallEnabled?: number
  /** 是否配过 logrotate. */
  logRotateEnabled?: number
  /** 装机完成时间. */
  installedAt?: string
  /** 探测到的 dante 启动时间. */
  lastDanteUptime?: string
}

/** 落地机配额监控 (限速 + 总流量配额 + 累计已用; 跟 server quota 一致). */
export interface ServerLandingQuota {
  serverId?: string
  /** dante 实际限速 Mbps; 0=不限. */
  bandwidthMbps: number
  /** 总流量配额 GB; null/0=不限. */
  totalGb?: number
  /** 当周期累计已用 byte (agent push). */
  usedBytes?: number
  rxBytes?: number
  txBytes?: number
  /** MONTHLY / FIXED. */
  resetPolicy?: string
  /** 按月流量重置日 1-28; FIXED 时为空. */
  resetDay?: number
  /** NORMAL / THROTTLED. */
  throttleState?: string
}

export interface ServerLandingQuery {
  pageNo?: number
  pageSize?: number
  keyword?: string
  /** 装机生命周期过滤. */
  lifecycleState?: string
  /** 占用状态过滤. */
  status?: string
  /** 区域码集合 (城市选单个, 国家选该国全部城市; 空=全部). */
  regionCodes?: string[]
  ipTypeId?: string
}

/** 部署模式枚举值 → 中文标签. */
export const SERVER_LANDING_PROVISION_MODE_LABELS: Record<number, string> = {
  1: '自部署',
  2: '第三方'
}

/**
 * dante 日志记录范围预设 (per-rule `log:` 关键字组合; dante 没有 syslog 风格的 info/warn/error 级别).
 *
 * 日志行开头的 `info` / `error` 标签是 dante 给每个事件类型硬编码的, 用户改不了;
 * 这里控制的是"记不记某类事件", 不是"用什么严重度记".
 *
 * 6 种事件关键字 (dante 文档): connect / disconnect / data / error / iooperation / tcpinfo.
 */
export const DANTE_LOG_LEVEL_OPTIONS = [
  { label: '只记失败', value: 'error' },
  { label: '标准 (连接 + 失败)', value: 'connect disconnect error' },
  { label: '全量 (含 IO/TCP)', value: 'connect disconnect error iooperation tcpinfo' }
] as const

export interface PageResult<T> {
  total: number
  records: T[]
}

/** 装机生命周期 → 中文标签 + 颜色. */
export const SERVER_LANDING_LIFECYCLE_LABELS: Record<string, string> = {
  INSTALLING: '装机中',
  READY: '待上线',
  LIVE: '运行中',
  RETIRED: '已停用'
}

export const SERVER_LANDING_LIFECYCLE_TAG_TYPE: Record<string, 'info' | 'warning' | 'success' | 'default'> = {
  INSTALLING: 'info',
  READY: 'warning',
  LIVE: 'success',
  RETIRED: 'default'
}

export const SERVER_LANDING_LIFECYCLE_OPTIONS = [
  { label: '全部', value: undefined as string | undefined },
  { label: '装机中', value: 'INSTALLING' },
  { label: '待上线', value: 'READY' },
  { label: '运行中', value: 'LIVE' },
  { label: '已停用', value: 'RETIRED' }
]

/** 占用状态 → 中文标签. */
export const SERVER_LANDING_STATUS_LABELS: Record<string, string> = {
  AVAILABLE: '可分配',
  RESERVED: '预占中',
  OCCUPIED: '已占用'
}

export const SERVER_LANDING_STATUS_OPTIONS = [
  { label: '全部', value: undefined as string | undefined },
  { label: '可分配', value: 'AVAILABLE' },
  { label: '预占中', value: 'RESERVED' },
  { label: '已占用', value: 'OCCUPIED' }
]

export function pageServerLanding(params: ServerLandingQuery) {
  const { regionCodes, ...rest } = params
  // 后端 List<String> 用逗号串绑定, 避免 axios 默认数组序列化带 [] 绑不上
  return request.get<unknown, PageResult<ServerLanding>>('/admin/resource/server-landing/page-landing', {
    params: { ...rest, regionCodes: regionCodes && regionCodes.length ? regionCodes.join(',') : undefined }
  })
}

/** 落地机总览统计 (顶部 stats 卡片用) */
export interface ServerLandingSummary {
  total: number
  installing: number
  ready: number
  live: number
  retired: number
  available: number
  occupied: number
  cooling: number
  reserved: number
}

export function getServerLandingSummary() {
  return request.get<unknown, ServerLandingSummary>('/admin/resource/server-landing/get-summary')
}

export function getServerLandingDetail(id: string) {
  return request.get<unknown, ServerLanding>('/admin/resource/server-landing/get-landing', { params: { id } })
}

export function deleteServerLanding(id: string) {
  return request.delete<unknown, void>('/admin/resource/server-landing/delete-landing', { params: { id } })
}

/** 切换落地机 lifecycle_state (上线 / 退役). */
export function transitionLandingLifecycle(id: string, state: string) {
  return request.post<unknown, boolean>(
    '/admin/resource/server-landing/transition-lifecycle',
    null,
    { params: { id, state } }
  )
}

// ===== 子表分段编辑 (拆 4 个 dialog 各自调) =====

/** 更新核心字段 (region/ipTypeId/ipAddress/provisionMode/remark; lifecycle 走 /transition-lifecycle). */
export function updateServerLandingCore(id: string, dto: ServerLandingCoreUpdateDTO) {
  return request.put<unknown, boolean>('/admin/resource/server-landing/update-core', dto, { params: { id } })
}

/** 取账面. */
export function getServerLandingBilling(id: string) {
  return request.get<unknown, ServerLandingBilling | null>('/admin/resource/server-landing/get-billing', { params: { id } })
}

/** 更新账面. */
export function updateServerLandingBilling(id: string, dto: ServerLandingBilling) {
  return request.put<unknown, boolean>('/admin/resource/server-landing/update-billing', dto, { params: { id } })
}

/** 取 dante 配置 + 限速. */
export function getServerLandingSocks5(id: string) {
  return request.get<unknown, ServerLandingSocks5 | null>('/admin/resource/server-landing/get-socks5', { params: { id } })
}

/** 更新 dante 配置 (socks5Password 留空 = 保留原值; 限速走 quota endpoint). */
export function updateServerLandingSocks5(id: string, dto: ServerLandingSocks5) {
  return request.put<unknown, boolean>('/admin/resource/server-landing/update-socks5', dto, { params: { id } })
}

/** 取装机事实 (路径/版本/systemd 名等; 装机完成后才有数据). */
export function getServerLandingInstall(id: string) {
  return request.get<unknown, ServerLandingInstall | null>('/admin/resource/server-landing/get-install', { params: { id } })
}

/** 取配额监控 (限速 / 总流量配额 / 累计 / throttle 状态). */
export function getServerLandingQuota(id: string) {
  return request.get<unknown, ServerLandingQuota | null>('/admin/resource/server-landing/get-quota', { params: { id } })
}

/** 更新配额配置 (限速 + 总流量配额 + 重置策略; rx/tx/throttle 由 agent / 状态机改不在此). */
export function updateServerLandingQuota(id: string, dto: ServerLandingQuota) {
  return request.put<unknown, boolean>('/admin/resource/server-landing/update-quota', dto, { params: { id } })
}

/** 周期重置策略选项. */
export const QUOTA_RESET_POLICY_OPTIONS = [
  { label: '按月重置 (MONTHLY, 默认)', value: 'MONTHLY' },
  { label: '永不重置 (FIXED)', value: 'FIXED' }
] as const

export const THROTTLE_STATE_LABELS: Record<string, string> = {
  NORMAL: '正常',
  THROTTLED: '已触发限流'
}

// ===== SOCKS5 落地节点 运维 (走落地机条目存储的 SSH 凭据, 不再问用户) =====
// dante systemd 状态走公共 getServerSystemdStatus(id, 'danted'); version 从 ServerLandingInstall.danteVersion 拿
// 主机/UFW 走通用 /admin/resource/server/get-system-info / get-ufw-status

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

/** 切 SOCKS5 开机自启 (systemctl enable/disable + DB.autostart_enabled 同步). */
export function setSocks5Autostart(id: string, enabled: boolean) {
  return request.post<unknown, boolean>(
    '/admin/resource/server-landing/set-socks5-autostart', null, { params: { id, enabled } })
}

/** 拉 SOCKS5 (dante) journalctl 日志. */
export function getSocks5Log(
  id: string,
  opts?: { lines?: number; level?: Socks5LogLevel; keyword?: string }
) {
  return request.get<unknown, Socks5Log>('/admin/resource/server-landing/get-socks5-log', {
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
  return request.get<unknown, Socks5Log>('/admin/resource/server-landing/get-socks5-log-file', {
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
 * 通过该落地机的 SOCKS5 凭据拨号公网 echo-IP 端点, 验证 SOCKS5 是否真的工作 + 出网 IP 是否符合预期.
 *
 * @param id     resource_server.id
 * @param params 全字段必填; 调用方一般 `{ ...SOCKS5_TEST_DEFAULTS, echoUrl: 用户输入 }` 拼造
 */
export function testServerLandingSocks5(id: string, params: Socks5TestParams) {
  return request.post<unknown, Socks5TestResult>('/admin/resource/server-landing/test-socks5-dial', params, { params: { id } })
}

// ===== SOCKS5 装机 (装机入参由前端 prefill 默认值, 用户可改; 后端写回 landing DO 再跑脚本) =====

/** 装机入参 (跟后端 ServerLandingDeployReqVO 字段对齐). */
export interface ServerLandingDeployDTO {
  /** SOCKS5 监听端口 (首次装机前端随机, 重装时置灰) */
  socks5Port: number
  socks5Username: string
  socks5Password: string
  logLevel: string
  logPath: string
  installDir: string
  confPath: string
  pamFile: string
  pwdFile: string
  systemdUnit: string
  /** 1=enable 0=disable */
  autostartEnabled: number
  /** 1=配 UFW 0=跳过 */
  firewallEnabled: number
  /** 1=配 logrotate 0=跳过 */
  logRotateEnabled: number
}

/** 装机非凭据部分的默认值 (SOCKS5 port/user/password 由前端随机生成, 不在此). */
export const LANDING_DEPLOY_DEFAULTS = Object.freeze({
  logLevel: 'connect disconnect error',
  logPath: '/home/socks5/logs/sockd.log',
  installDir: '/home/socks5',
  confPath: '/home/socks5/etc/danted.conf',
  pamFile: '/etc/pam.d/sockd',
  pwdFile: '/home/socks5/etc/sockd.passwd',
  systemdUnit: 'danted',
  autostartEnabled: 1,
  firewallEnabled: 1,
  logRotateEnabled: 1
})

/**
 * 流式装机 SOCKS5 — 针对已存在的落地机条目 (lifecycle=INSTALLING/READY) 跑装机脚本.
 *
 * 装机成功后端: update install.installed_at + 主表 lifecycle → LIVE + agent_token 补全
 */
export async function installServerLandingSocks5Stream(
  serverId: string,
  dto: ServerLandingDeployDTO,
  onChunk: (chunk: string) => void,
  signal?: AbortSignal
): Promise<void> {
  const userStore = useUserStore()
  const res = await fetch(`/api/admin/resource/server-landing/install-socks5?id=${encodeURIComponent(serverId)}`, {
    method: 'POST',
    headers: {
      Authorization: userStore.token,
      'Content-Type': 'application/json'
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
