import request from '@/api/request'

/** Xray client 实体 (一个会员在某线路+落地 IP 上的 client 凭据); 与后端 XrayClientRespVO 对齐。 */
export interface XrayClient {
  id: string
  serverId: string
  ipId: string
  memberUserId: string
  /** 挂到的远端 Xray inbound tag */
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

export interface XrayClientQuery {
  pageNo?: number
  pageSize?: number
  keyword?: string
  serverId?: string
  memberUserId?: string
  ipId?: string
  status?: number
}

export interface XrayClientUpdateDTO {
  listenIp?: string
  listenPort?: number
  transport?: string
  /** 1=运行 2=已停 3=待同步 4=远端缺失 */
  status?: number
}

export interface XrayClientProvisionDTO {
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

export interface XrayClientTraffic {
  clientEntityId: string
  clientEmail: string
  upBytes: number
  downBytes: number
  /** 流量上限(字节); 0=不限 */
  totalBytes: number
  /** 到期时间戳(毫秒); 0=永久 */
  expiryEpochMillis: number
  enabled: boolean
}

/**
 * 协议级凭据明文 (UUID / password 等); 仅在分享场景按需拉取, 不在列表 / 详情接口里下发。
 */
export interface XrayClientCredential {
  id: string
  /** 明文 UUID (vless/vmess) 或 password (trojan) */
  clientUuid: string
  clientEmail: string
  protocol: string
  /** 客户端连接的 host (resource_server.host) */
  serverHost: string
  listenPort: number
  transport?: string
}

export interface PageResult<T> {
  total: number
  records: T[]
}

export const CLIENT_STATUS_LABELS: Record<number, string> = {
  1: '运行',
  2: '已停',
  3: '待同步',
  4: '远端缺失'
}

export function pageClients(params: XrayClientQuery) {
  return request.get<unknown, PageResult<XrayClient>>('/admin/xray/clients', { params })
}

export function getClientDetail(id: string) {
  return request.get<unknown, XrayClient>(`/admin/xray/clients/${id}`)
}

export function provisionClient(dto: XrayClientProvisionDTO) {
  return request.post<unknown, XrayClient>('/admin/xray/clients/provision', dto)
}

export function updateClient(id: string, dto: XrayClientUpdateDTO) {
  return request.put<unknown, XrayClient>(`/admin/xray/clients/${id}`, dto)
}

export function revokeClient(id: string) {
  return request.delete<unknown, void>(`/admin/xray/clients/${id}`)
}

export function rotateClient(id: string) {
  return request.post<unknown, XrayClient>(`/admin/xray/clients/${id}/rotate`)
}

export function getClientTraffic(id: string) {
  return request.get<unknown, XrayClientTraffic>(`/admin/xray/clients/${id}/traffic`)
}

export function resetClientTraffic(id: string) {
  return request.post<unknown, void>(`/admin/xray/clients/${id}/reset-traffic`)
}

/**
 * 拉协议级凭据明文; 分享给会员前必须用这个接口取真 UUID,
 * 列表 / 详情接口下发的 clientUuid 是 mask 形式 (xxx***xxxx)。
 */
export function getClientCredential(id: string) {
  return request.get<unknown, XrayClientCredential>(`/admin/xray/clients/${id}/credential`)
}
