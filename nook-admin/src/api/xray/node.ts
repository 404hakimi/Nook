import request from '@/api/request'
import type { PageResult } from '@/api/operation/op-log'

/** Xray 节点; 与 resource_server 一一对应 */
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
  /** xray binary 绝对路径; install 时落库, 不前端拼接 */
  xrayBinaryPath?: string
  /** xray config.json 绝对路径; install 时落库 */
  xrayConfigPath?: string
  /** xray share 目录 (geo*.dat); install 时落库 */
  xrayShareDir?: string
  /** 全节点固定常量, 后端 convert 时回填 (/etc/systemd/system/xray.service) */
  xraySystemdUnitPath?: string
  /** 该 node 最多挂载的落地 IP 数量 (软上限). */
  touchdownSize?: number
  sharedInboundPort?: number
  wsPath?: string
  domain?: string
  tlsCertPath?: string
  tlsKeyPath?: string
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
  return request.get<unknown, PageResult<XrayNode>>('/admin/xray/node/page', { params })
}

export function getXrayNodeDetail(serverId: string) {
  return request.get<unknown, XrayNode>('/admin/xray/node/get', { params: { serverId } })
}
