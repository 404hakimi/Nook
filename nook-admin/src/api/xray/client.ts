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

/**
 * 拉协议级凭据明文; 分享给会员前必须用这个接口取真 UUID,
 * 列表 / 详情接口下发的 clientUuid 是 mask 形式 (xxx***xxxx).
 */
export function getClientCredential(id: string) {
  return request.get<unknown, XrayClientCredential>('/admin/xray/client/get-xray-client-credential', { params: { id } })
}

// 远端 vs DB 的差异修复 (sync/replay/diff) 已由 agent reconcile 周期自愈取代, 不再走后端 SSH.
