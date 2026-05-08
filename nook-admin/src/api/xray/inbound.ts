import request from '@/api/request'

/** Xray inbound (client 配置) 实体；与后端 XrayInboundRespVO 对齐。 */
export interface XrayInbound {
  id: string
  serverId: string
  ipId: string
  memberUserId: string
  backendType: string
  externalInboundRef: string
  protocol: string
  transport?: string
  listenIp?: string
  listenPort?: number
  clientUuid: string
  clientEmail: string
  /** 1=运行 2=已停 3=待同步 4=远端已不存在 */
  status: number
  lastSyncedAt?: string
  createdAt?: string
  updatedAt?: string
}

export interface XrayInboundQuery {
  pageNo?: number
  pageSize?: number
  keyword?: string
  serverId?: string
  memberUserId?: string
  ipId?: string
  backendType?: string
  status?: number
}

export interface XrayInboundProvisionDTO {
  serverId: string
  ipId: string
  memberUserId: string
  externalInboundRef: string
  protocol: string
  transport?: string
  listenIp?: string
  listenPort?: number
  totalBytes?: number
  expiryEpochMillis?: number
  limitIp?: number
  flow?: string
}

export interface XrayInboundTraffic {
  inboundEntityId: string
  clientEmail: string
  upBytes: number
  downBytes: number
  /** 流量上限(字节)；0=不限 */
  totalBytes: number
  /** 到期时间戳(毫秒)；0=永久 */
  expiryEpochMillis: number
  enabled: boolean
}

export interface PageResult<T> {
  total: number
  records: T[]
}

export const INBOUND_STATUS_LABELS: Record<number, string> = {
  1: '运行',
  2: '已停',
  3: '待同步',
  4: '远端缺失'
}

export function pageInbounds(params: XrayInboundQuery) {
  return request.get<unknown, PageResult<XrayInbound>>('/admin/xray/inbounds', { params })
}

export function getInboundDetail(id: string) {
  return request.get<unknown, XrayInbound>(`/admin/xray/inbounds/${id}`)
}

export function provisionInbound(dto: XrayInboundProvisionDTO) {
  return request.post<unknown, XrayInbound>('/admin/xray/inbounds/provision', dto)
}

export function revokeInbound(id: string) {
  return request.delete<unknown, void>(`/admin/xray/inbounds/${id}`)
}

export function rotateInbound(id: string) {
  return request.post<unknown, XrayInbound>(`/admin/xray/inbounds/${id}/rotate`)
}

export function getInboundTraffic(id: string) {
  return request.get<unknown, XrayInboundTraffic>(`/admin/xray/inbounds/${id}/traffic`)
}

export function resetInboundTraffic(id: string) {
  return request.post<unknown, void>(`/admin/xray/inbounds/${id}/reset-traffic`)
}
