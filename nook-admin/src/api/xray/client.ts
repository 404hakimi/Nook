import request from '@/api/request'

/** Xray client 实体 (一个会员在某线路+落地 IP 上的 client 凭据); 与后端 ClientRespVO 对齐. */
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
  /** list/detail 接口下发的是 mask 形式 (前 8 + *** + 后 8); 明文走 /credential 接口 */
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
  protocol: string
  totalBytes?: number
  expiryEpochMillis?: number
  limitIp?: number
  flow?: string
  // 1:1 + slot 模型下 nook 自动决定, 前端可不传; 传了也会被业务侧覆写 (后端 @Deprecated)
  externalInboundRef?: string
  transport?: string
  listenIp?: string
  listenPort?: number
}

/** 实时流量出参 (后端 ClientTrafficRespVO); 字段名以"挂在 inbound 上的 client 实体 id"语义对齐. */
export interface XrayClientTraffic {
  inboundEntityId: string
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
 * 协议级凭据明文 (UUID / password 等); 仅在分享场景按需拉取, 不在列表 / 详情接口里下发.
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

// ===== 后端 XrayClientController @ /admin/node/xray/client =====

export function pageClients(params: XrayClientQuery) {
  return request.get<unknown, PageResult<XrayClient>>('/admin/node/xray/client', { params })
}

export function getClientDetail(id: string) {
  return request.get<unknown, XrayClient>(`/admin/node/xray/client/${id}`)
}

export function provisionClient(dto: XrayClientProvisionDTO) {
  return request.post<unknown, XrayClient>('/admin/node/xray/client/provision', dto)
}

export function updateClient(id: string, dto: XrayClientUpdateDTO) {
  return request.put<unknown, XrayClient>(`/admin/node/xray/client/${id}`, dto)
}

export function revokeClient(id: string) {
  return request.delete<unknown, void>(`/admin/node/xray/client/${id}`)
}

export function rotateClient(id: string) {
  return request.post<unknown, XrayClient>(`/admin/node/xray/client/${id}/rotate`)
}

export function getClientTraffic(id: string) {
  return request.get<unknown, XrayClientTraffic>(`/admin/node/xray/client/${id}/traffic`)
}

export function resetClientTraffic(id: string) {
  return request.post<unknown, void>(`/admin/node/xray/client/${id}/reset-traffic`)
}

/**
 * 拉协议级凭据明文; 分享给会员前必须用这个接口取真 UUID,
 * 列表 / 详情接口下发的 clientUuid 是 mask 形式 (xxx***xxxx).
 */
export function getClientCredential(id: string) {
  return request.get<unknown, XrayClientCredential>(`/admin/node/xray/client/${id}/credential`)
}
