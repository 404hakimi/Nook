import request from '@/api/request'

/** Xray 实例元数据 (装机契约 / 部署事实); 跟 resource_server 1:1 */
export interface XrayInstall {
  serverId: string
  /** 服务器别名 (后端 enrich) */
  serverName?: string
  /** 服务器主机 (后端 enrich) */
  serverHost?: string
  xrayVersion?: string
  xrayApiPort?: number
  xrayInstallDir?: string
  /** xray binary 绝对路径; 装机时落库 */
  xrayBinaryPath?: string
  /** xray config.json 绝对路径; 装机时落库 */
  xrayInboundPath?: string
  /** xray share 目录 (geo*.dat); 装机时落库 */
  xrayShareDir?: string
  xrayLogDir?: string
  /** 全节点固定常量, 后端回填 (/etc/systemd/system/xray.service) */
  xraySystemdUnitPath?: string
  /** 最近一次部署完成时间 (重装覆写) */
  installedAt?: string
  /** 上次探测到的 xray 启动时间; 重装清零 */
  lastXrayUptime?: string
  createdAt?: string
  updatedAt?: string
}

/** 按 serverId 取 xray 实例元数据 (server detail tab 用); 不存在抛 SERVER_STATE_NOT_FOUND */
export function getXrayInstall(serverId: string) {
  return request.get<unknown, XrayInstall>('/admin/xray/install/get-xray-server', { params: { serverId } })
}
