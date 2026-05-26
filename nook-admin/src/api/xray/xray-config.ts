import request from '@/api/request'

/** Xray inbound 共享配置 (业务可热改); 跟 resource_server 1:1 */
export interface XrayConfig {
  serverId: string
  /** 共享 inbound 协议 (vmess/trojan/...) */
  protocol?: string
  /** 共享 inbound 传输 (ws/tcp/...) */
  transport?: string
  /** 共享 inbound 监听 IP */
  listenIp?: string
  /** 共享 inbound 监听端口 */
  sharedInboundPort?: number
  /** WebSocket transport path */
  wsPath?: string
  /** 对外域名 (CDN CNAME 指向) */
  domain?: string
  tlsCertPath?: string
  tlsKeyPath?: string
  createdAt?: string
  updatedAt?: string
}

/** 按 serverId 取 inbound 共享配置 (server detail tab 用); 未装时返 null */
export function getXrayConfig(serverId: string) {
  return request.get<unknown, XrayConfig | null>('/admin/xray/config/get-xray-config', { params: { serverId } })
}
