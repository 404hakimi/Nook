import request from '@/api/request'

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

/** Xray service 状态(结构化)。 */
export interface XrayServiceStatus {
  active?: string
  version?: string
  uptimeFrom?: string
  listening?: string
  log?: string
}

export function xrayStatus(serverId: string) {
  return request.get<unknown, XrayServiceStatus>(`/admin/xray/servers/${serverId}/xray/status`)
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
}

/** 一键安装/重装线路服务器 — 耗时 1-5 分钟,前端调用要给长 timeout. */
export function xrayInstall(serverId: string, dto: LineServerInstallDTO) {
  return request.post<unknown, string>(
    `/admin/xray/servers/${serverId}/xray/install`,
    dto,
    { timeout: 10 * 60 * 1000 } // 10 min
  )
}
