import request from '@/api/request'
import type { PageResult } from '@/api/operation/op-log'

/** Xray 节点 (后端 XrayNodeRespVO); 与 resource_server 1:1 关联 */
export interface XrayNode {
  serverId: string
  /** 后端 enrich 的服务器别名; 已删 server 字段为空, 前端 fallback 用 serverId */
  serverName?: string
  /** 后端 enrich 的服务器主机 */
  serverHost?: string
  xrayVersion?: string
  xrayApiPort?: number
  xrayLogDir?: string
  xrayInstallDir?: string
  slotPoolSize?: number
  slotPortBase?: number
  /** 上次探测到的 xray 启动时间, 用于判断是否需 replay; 重装时清零 */
  lastXrayUptime?: string
  /** 最近一次部署完成时间 (重装会覆写) */
  installedAt?: string
  createdAt?: string
  updatedAt?: string
}

export interface XrayNodePageQuery {
  pageNo?: number
  pageSize?: number
  serverId?: string
  xrayVersion?: string
}

export function pageXrayNode(params: XrayNodePageQuery) {
  return request.get<unknown, PageResult<XrayNode>>('/admin/node/xray-nodes', { params })
}

export function getXrayNodeDetail(serverId: string) {
  return request.get<unknown, XrayNode>(`/admin/node/xray-nodes/${serverId}`)
}

/** Slot 占用视图项 (后端 XraySlotItemRespVO); slot_pool + xray_node + xray_client 三表派生 */
export interface XraySlotItem {
  slotIndex: number
  listenPort: number
  /** 0=空闲 1=已占用 */
  used: number
  clientId?: string | null
  clientEmail?: string | null
  protocol?: string | null
  transport?: string | null
  /** 1=运行 2=已停 3=待同步 4=远端已不存在 */
  clientStatus?: number | null
}

export function getSlotPoolView(serverId: string) {
  return request.get<unknown, XraySlotItem[]>(`/admin/node/xray-nodes/${serverId}/slots`)
}
