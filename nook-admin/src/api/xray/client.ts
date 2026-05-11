import request from '@/api/request'

/** Xray client 实体 (一个会员在某线路+落地 IP 上的 client 凭据); 与后端 ClientRespVO 对齐. */
export interface XrayClient {
  id: string
  serverId: string
  ipId: string
  /**
   * 落地 IP 地址 (后端按 ipId 在 resource_ip_pool 里 enrich); 已删/缺失时后端会留空, 由前端 fallback 到 ipId 显示.
   */
  ipAddress?: string
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
  /** 协议 vless / vmess / trojan. */
  protocol: 'vless' | 'vmess' | 'trojan'
  /** 传输层 tcp / ws / grpc / h2 / quic; 当前 streamSettings 仅 tcp 走通. */
  transport: 'tcp' | 'ws' | 'grpc' | 'h2' | 'quic'
  /** 监听 IP, 0.0.0.0 = 所有 IPv4 接口, :: = 所有 IPv6 接口. */
  listenIp: string
  /** 流量上限(字节); 0/不传 = 不限. */
  totalBytes?: number
  /** 到期时间戳(毫秒); 0/不传 = 永久. */
  expiryEpochMillis?: number
  /** 单客户端最多并发源 IP 数; 0/不传 = 不限, 上限 100. */
  limitIp?: number
  /** vless flow, 例 xtls-rprx-vision; vmess / trojan 必须留空. */
  flow?: string
}

/**
 * 实时流量出参 (后端 ClientTrafficRespVO); 字段名以"挂在 inbound 上的 client 实体 id"语义对齐.
 *
 * <p>字节字段双形态: *Bytes (number, 精确数值, 比较/排序) + *BytesText (string, "190.50 KB" 这种人读串, 直接展示).
 * usagePct 在 totalBytes=0 时为 null, 前端按 null 判断"无上限"分支即可, 不需再看 totalBytes 数值.
 */
export interface XrayClientTraffic {
  inboundEntityId: string
  clientEmail: string

  upBytes: number
  upBytesText: string

  downBytes: number
  downBytesText: string

  /** 已用 = upBytes + downBytes; 后端预算好, 前端不再加. */
  usedBytes: number
  usedBytesText: string

  /** 流量上限(字节); 0=不限. */
  totalBytes: number
  /** 流量上限人读字符串; 0 时为 "无限制" 占位串, 与 totalBytes=0 等价. */
  totalBytesText: string

  /** 用量百分比 (0-100); totalBytes=0 时为 null 表示"无上限不适用". */
  usagePct: number | null

  /** 到期时间戳(毫秒); 0=永久. */
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

// ===== reconciler 对账接口 =====

/** server 远端 vs DB 对账结果. */
export interface SyncStatus {
  serverId: string
  /** 能否 SSH 连通 + xray 跑着. */
  reachable: boolean
  /** DB 与远端均有 (双方对齐). */
  okTags: string[]
  /** DB 有但远端缺失; 调 sync 推回去. */
  staleDbTags: string[]
  /** 远端有但 DB 没有 (排除静态预置 api); 视为孤儿. */
  orphanRemoteTags: string[]
}

/** server 全量 replay 报告. */
export interface ReplayReport {
  serverId: string
  /** DB 里 status≠2 的 client 总数. */
  totalCount: number
  /** 远端已对齐, 跳过未推 (避免无谓断连). */
  alreadyOkCount: number
  /** 实际推成功数 (远端缺失 → 重建). */
  successCount: number
  /** 推失败的 client id; 已标 status=3, 下轮 reconciler 重试. */
  failedClientIds: string[]
}

/** 拉 server 远端 vs DB 对账状态; 不修改任何状态, 只读. */
export function getSyncStatus(serverId: string) {
  return request.get<unknown, SyncStatus>(`/admin/node/xray/client/server/${serverId}/sync-status`)
}

/** 把单条 client 推到远端 (幂等: 远端有就先删再加). */
export function syncClient(id: string) {
  return request.post<unknown, void>(`/admin/node/xray/client/${id}/sync`)
}

/** 把 server 下所有 status≠2 的 client 全推一遍, 返回报告. */
export function replayServer(serverId: string) {
  return request.post<unknown, ReplayReport>(`/admin/node/xray/client/server/${serverId}/replay`)
}
