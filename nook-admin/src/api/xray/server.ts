import request from '@/api/request'
import { useUserStore } from '@/stores/user'

/** 探活结果。 */
export interface ConnectivityTestResult {
  success: boolean
  elapsedMs: number
  error?: string
}

/** 远端 inbound 列表项。 */
export interface RemoteInbound {
  externalInboundRef: string
  remark?: string
  protocol?: string
  port?: number
  enabled?: boolean
  clientCount?: number
}

/** 探活：调 backend.verifyConnectivity；返回耗时 + 是否成功。 */
export function testServerConnectivity(serverId: string) {
  return request.post<unknown, ConnectivityTestResult>(`/admin/xray/servers/${serverId}/test`)
}

/** 列远端 inbound（给运营在 inbound 关联界面下拉用）。 */
export function listRemoteInbounds(serverId: string) {
  return request.get<unknown, RemoteInbound[]>(`/admin/xray/servers/${serverId}/inbounds`)
}

/** SSH: 看 x-ui 服务状态。 */
export function sshStatus(serverId: string) {
  return request.get<unknown, string>(`/admin/xray/servers/${serverId}/ssh/status`)
}

/** SSH: 拉最近 N 行 x-ui 日志。 */
export function sshLog(serverId: string, lines = 100) {
  return request.get<unknown, string>(`/admin/xray/servers/${serverId}/ssh/log`, { params: { lines } })
}

/** SSH: 重启 x-ui。 */
export function sshRestart(serverId: string) {
  return request.post<unknown, string>(`/admin/xray/servers/${serverId}/ssh/restart`)
}

/** SSH: 备份 SQLite 数据库到远端 /tmp。 */
export function sshBackupDb(serverId: string) {
  return request.post<unknown, string>(`/admin/xray/servers/${serverId}/ssh/backup-db`)
}

/** 操作系统级别基本信息; 不依赖 Xray 是否在跑。 */
export interface ServerSystemInfo {
  hostname?: string
  kernel?: string
  osRelease?: string
  systemUptime?: string
  loadAvg?: string
  memory?: string
  disk?: string
  timezone?: string
}

/** Xray systemd 服务运行状态; 不含日志。 */
export interface XrayServiceStatus {
  active?: string
  version?: string
  uptimeFrom?: string
  listening?: string
}

/** 日志级别过滤 (journalctl -p 语义)。 */
export type XrayLogLevel = 'all' | 'warning' | 'err'

/** Xray journalctl 日志快照。 */
export interface XrayLog {
  lines: number
  level: XrayLogLevel
  log?: string
}

/** 拉服务器系统基本信息 (hostname / 内存 / 磁盘 / 时区 等)。 */
export function getServerSystemInfo(serverId: string) {
  return request.get<unknown, ServerSystemInfo>(`/admin/xray/servers/${serverId}/system-info`)
}

/** 拉 Xray systemd 服务运行状态 (active / version / 启动时间 / 监听端口); 不含日志。 */
export function getXrayServiceStatus(serverId: string) {
  return request.get<unknown, XrayServiceStatus>(`/admin/xray/servers/${serverId}/service-status`)
}

/** 拉 Xray journalctl 日志, 按行数 + 级别过滤。 */
export function getXrayLog(
  serverId: string,
  opts?: { lines?: number; level?: XrayLogLevel }
) {
  return request.get<unknown, XrayLog>(`/admin/xray/servers/${serverId}/log`, {
    params: {
      lines: opts?.lines,
      level: opts?.level === 'all' ? undefined : opts?.level
    }
  })
}

export function xrayRestart(serverId: string) {
  return request.post<unknown, string>(`/admin/xray/servers/${serverId}/xray/restart`)
}

export interface LineServerInstallDTO {
  vmessPort: number
  xrayApiPort: number
  logDir?: string
  installUfw?: boolean
  enableBbr?: boolean
  /** IANA tz, 如 Asia/Shanghai / UTC; 留空则不改 */
  timezone?: string
}

/**
 * 一键安装/重装 — 流式接口.
 * 后端用 chunked transfer 边跑边吐 stdout, 前端 fetch + ReadableStream 边读边回调 onChunk.
 *
 * - axios 不支持 response 流式, 这里直接用 fetch + 手写认证头.
 * - signal 支持外部 abort (用户中途关弹框时取消请求).
 * - 整体超时由 server 端 ResponseBodyEmitter 设了 15 分钟; 前端不再额外限时.
 */
export async function xrayInstallStream(
  serverId: string,
  dto: LineServerInstallDTO,
  onChunk: (chunk: string) => void,
  signal?: AbortSignal
): Promise<void> {
  const userStore = useUserStore()
  const res = await fetch(`/api/admin/xray/servers/${serverId}/xray/install`, {
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
  // 收尾解码空状态(避免 multibyte 字符断在 chunk 边界丢字)
  const tail = decoder.decode()
  if (tail) onChunk(tail)
}
