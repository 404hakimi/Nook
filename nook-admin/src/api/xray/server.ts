import request from '@/api/request'

/** 探活结果。 */
export interface ConnectivityTestResult {
  success: boolean
  elapsedMs: number
  backendType?: string
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
