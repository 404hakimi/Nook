import request from '@/api/request'

/** 服务器列表/详情响应（密码字段不下发，改"是否已配置"布尔标志）。 */
export interface ResourceServer {
  id: string
  name: string
  host: string
  sshPort?: number
  sshUser?: string
  sshAuthConfigured?: boolean
  /** SSH 命令最大耗时（秒）；30 较合理，跨洲网络/拉日志慢可调到 60-120 */
  sshTimeoutSeconds?: number
  /** "threexui" / "xray-grpc" */
  backendType: string
  panelBaseUrl?: string
  panelUsername?: string
  panelPasswordConfigured?: boolean
  /** 0=否 1=是 */
  panelIgnoreTls?: number
  /** backend HTTP/gRPC 调用超时(秒)；20 较合理，跨洲可调到 60 */
  backendTimeoutSeconds?: number
  xrayGrpcHost?: string
  xrayGrpcPort?: number
  /** 带宽峰值速率 Mbps */
  totalBandwidth?: number
  /** 月流量额度 GB；null/0 表示不限或未配置 */
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
  backendType?: string
  region?: string
}

export interface ResourceServerSaveDTO {
  name?: string
  host?: string
  sshPort?: number
  sshUser?: string
  /** 留空表示保留旧值；想要明确清空当前没有专门接口（TODO） */
  sshPassword?: string
  sshPrivateKey?: string
  sshTimeoutSeconds?: number
  backendType?: string
  panelBaseUrl?: string
  panelUsername?: string
  panelPassword?: string
  /** 0=否 1=是 */
  panelIgnoreTls?: number
  backendTimeoutSeconds?: number
  xrayGrpcHost?: string
  xrayGrpcPort?: number
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

export const BACKEND_TYPE_LABELS: Record<string, string> = {
  threexui: '3x-ui 面板',
  'xray-grpc': 'Xray gRPC'
}

export const SERVER_STATUS_LABELS: Record<number, string> = {
  1: '运行',
  2: '维护',
  3: '下线'
}

export function pageServers(params: ResourceServerQuery) {
  return request.get<unknown, PageResult<ResourceServer>>('/admin/resource/servers', { params })
}

export function getServerDetail(id: string) {
  return request.get<unknown, ResourceServer>(`/admin/resource/servers/${id}`)
}

export function createServer(dto: ResourceServerSaveDTO) {
  return request.post<unknown, ResourceServer>('/admin/resource/servers', dto)
}

export function updateServer(id: string, dto: ResourceServerSaveDTO) {
  return request.put<unknown, ResourceServer>(`/admin/resource/servers/${id}`, dto)
}

export function deleteServer(id: string) {
  return request.delete<unknown, void>(`/admin/resource/servers/${id}`)
}
