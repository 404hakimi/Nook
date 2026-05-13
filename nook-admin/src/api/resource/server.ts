import request from '@/api/request'

/**
 * 服务器列表/详情响应; 纯硬件 + SSH 凭据 + 三档 SSH/install 超时 (按地区差异化).
 *
 * <p>SSH 密码以明文下发 (DB 明文存, 后台受信场景), 编辑时 fill 进 type=password 输入框.
 * <p>Xray 配置 (api 端口 / slot 池等) 不在本响应里, 走 xray_node 接口单独取.
 */
export interface ResourceServer {
  id: string
  name: string
  host: string
  sshPort?: number
  sshUser?: string
  sshPassword?: string
  /** SSH 会话握手超时(秒); 5-300, 默认 30 */
  sshTimeoutSeconds?: number
  /** SSH 单条命令最大耗时(秒); 5-300, 默认 30; xray api/systemctl/journalctl 共用 */
  sshOpTimeoutSeconds?: number
  /** SCP 上传单文件超时(秒); 5-600, 默认 30 */
  sshUploadTimeoutSeconds?: number
  /** 一次安装脚本最大耗时(秒); 60-3600, 默认 600 */
  installTimeoutSeconds?: number
  /** 带宽峰值速率 Mbps */
  totalBandwidth?: number
  /** 月流量额度 GB; null/0 表示不限或未配置 */
  monthlyTrafficGb?: number
  totalIpCount?: number
  idcProvider?: string
  region?: string
  /** 1=运行 2=维护 3=下线 */
  status: number
  remark?: string
  createdAt?: string
  updatedAt?: string
}

export interface ResourceServerQuery {
  pageNo?: number
  pageSize?: number
  keyword?: string
  status?: number
  region?: string
}

export interface ResourceServerSaveDTO {
  name?: string
  host?: string
  sshPort?: number
  sshUser?: string
  sshPassword?: string
  sshTimeoutSeconds?: number
  sshOpTimeoutSeconds?: number
  sshUploadTimeoutSeconds?: number
  installTimeoutSeconds?: number
  totalBandwidth?: number
  monthlyTrafficGb?: number
  idcProvider?: string
  region?: string
  status?: number
  remark?: string
}

export interface PageResult<T> {
  total: number
  records: T[]
}

export const SERVER_STATUS_LABELS: Record<number, string> = {
  1: '运行',
  2: '维护',
  3: '下线'
}

export function pageServers(params: ResourceServerQuery) {
  return request.get<unknown, PageResult<ResourceServer>>('/admin/resource/server/page', { params })
}

export function getServerDetail(id: string) {
  return request.get<unknown, ResourceServer>('/admin/resource/server/get', { params: { id } })
}

export function createServer(dto: ResourceServerSaveDTO) {
  return request.post<unknown, ResourceServer>('/admin/resource/server/create', dto)
}

export function updateServer(id: string, dto: ResourceServerSaveDTO) {
  return request.put<unknown, ResourceServer>('/admin/resource/server/update', dto, { params: { id } })
}

export function deleteServer(id: string) {
  return request.delete<unknown, void>('/admin/resource/server/delete', { params: { id } })
}
