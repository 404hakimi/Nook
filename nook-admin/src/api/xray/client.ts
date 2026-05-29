import request from '@/api/request'

/**
 * Xray client 实体 (一个会员在某线路+落地 IP 上的 client 凭据); 与后端 ClientRespVO 对齐.
 *
 * inbound 维度 (protocol / transport / listenIp / listenPort) 不存 xray_client, 是 server 级共享配置;
 * 后端 convert 时按 serverId 关联 xray_config + 常量 enrich, server 未装 xray 时这些字段会为空.
 */
export interface XrayClient {
  id: string
  serverId: string
  /** server 别名 (后端 enrich); server 已删时为空, 前端 fallback 到 serverId */
  serverName?: string
  /** server 主机 (后端 enrich) */
  serverHost?: string
  ipId: string
  /**
   * 落地 IP 地址 (后端按 ipId 在落地机表里 enrich); 已删/缺失时后端会留空, 由前端 fallback 到 ipId 显示.
   */
  ipAddress?: string
  memberUserId: string
  /** 共享 inbound 协议 (后端 enrich, 来源 XrayConstants); server 未装 xray 时为空. */
  protocol?: string
  /** 共享 inbound 传输 (后端 enrich, 来源 XrayConstants). */
  transport?: string
  /** 共享 inbound 监听 IP (后端 enrich, 来源 XrayConstants). */
  listenIp?: string
  /** 共享 inbound 监听端口 (后端 enrich, 来源 xray_config.sharedInboundPort). */
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
  /** 客户端连接的 host; domain 不空时下发 domain, 否则下发 server.host */
  serverHost: string
  listenPort: number
  transport?: string
  /** WS path. */
  wsPath?: string
  /** TLS 启用标志; true 时 URI 加 security=tls + sni */
  tlsEnabled?: boolean
  /** TLS SNI (= domain); tlsEnabled=true 时有值 */
  sni?: string
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

// ===== 后端 XrayClientController @ /admin/xray/client =====

export function pageClients(params: XrayClientQuery) {
  return request.get<unknown, PageResult<XrayClient>>('/admin/xray/client/page-xray-client', { params })
}

export function rotateClient(id: string) {
  return request.post<unknown, XrayClient>('/admin/xray/client/rotate-xray-client', null, { params: { id } })
}

export function getClientTraffic(id: string) {
  return request.get<unknown, XrayClientTraffic>('/admin/xray/client/get-xray-client-traffic', { params: { id } })
}

export function resetClientTraffic(id: string) {
  return request.post<unknown, void>('/admin/xray/client/reset-xray-client-traffic', null, { params: { id } })
}

/**
 * 拉协议级凭据明文; 分享给会员前必须用这个接口取真 UUID,
 * 列表 / 详情接口下发的 clientUuid 是 mask 形式 (xxx***xxxx).
 */
export function getClientCredential(id: string) {
  return request.get<unknown, XrayClientCredential>('/admin/xray/client/get-xray-client-credential', { params: { id } })
}

// ===== 差异检查 / 同步接口 =====

/** server 远端 vs DB 差异结果 (1:N 模型, 三维度: user / outbound / rule). */
export interface SyncStatus {
  serverId: string
  /** 能否 SSH 连通 + xray api 可读. */
  reachable: boolean

  /** 共享 inbound 上 user email: DB + 远端均有. */
  okEmails: string[]
  /** 共享 inbound 上 user email: DB 有 / 远端缺 (客户连不上, 推 sync 修). */
  staleDbEmails: string[]
  /** 共享 inbound 上 user email: 远端有 / DB 没 (孤儿, 不自动清). */
  orphanRemoteEmails: string[]

  /** clientId: DB 有但远端缺 socks 出站 (流量进 blackhole 兜底被丢, 推 sync 修). */
  staleDbOutbounds: string[]
  /** clientId: 远端有 socks 出站但 DB 没对应 client (孤儿, 不自动清). */
  orphanRemoteOutbounds: string[]

  /** clientId: DB 有 client 但远端 rule_<clientId> 缺. */
  staleDbRules: string[]
  /** clientId: 远端有 rule_<clientId> 但 DB 没对应 client (孤儿). */
  orphanRemoteRules: string[]
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

/** 拉 server 远端 vs DB 差异状态; 不修改任何状态, 只读. */
export function getSyncStatus(serverId: string) {
  return request.get<unknown, SyncStatus>('/admin/xray/client/get-sync-status', { params: { serverId } })
}

/** 把单条 client 推到远端 (幂等: 远端有就先删再加). */
export function syncClient(id: string) {
  return request.post<unknown, void>('/admin/xray/client/sync-xray-client', null, { params: { id } })
}

/** 把 server 下所有 status≠2 的 client 全推一遍, 返回报告. */
export function replayServer(serverId: string) {
  return request.post<unknown, ReplayReport>('/admin/xray/client/replay-xray-server', null, { params: { serverId } })
}
