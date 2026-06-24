import request from '@/api/request'

/** Xray inbound 共享配置 (业务可热改); 跟 resource_server 1:1 */
export interface XrayInbound {
  serverId: string
  /** 共享 inbound 协议判别键 (vmess/vless) */
  protocol?: string
  /** 共享 inbound 监听端口 */
  sharedInboundPort?: number
  /** 协议字段值 (key = 协议 formSchema 字段 name; vmess: wsPath/domainId/subdomain, vless: realityDest); 重装预填用 */
  formValues?: Record<string, unknown>
  createdAt?: string
  updatedAt?: string
}

/** 按 serverId 取 inbound 共享配置 (server detail tab 用); 未装时返 null */
export function getXrayInbound(serverId: string) {
  return request.get<unknown, XrayInbound | null>('/admin/xray/inbound/get-xray-inbound', { params: { serverId } })
}
