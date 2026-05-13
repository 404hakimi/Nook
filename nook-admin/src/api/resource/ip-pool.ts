import request from '@/api/request'
import { useUserStore } from '@/stores/user'

/** IP 池条目: SOCKS5 落地节点; 一条 = 一台跑了 3proxy 的小 VPS, ip_address 即用户对外暴露的"独享 IP", 同时也是 SOCKS5 服务监听地址. */
export interface ResourceIpPool {
  id: string
  region: string
  ipTypeId: string
  ipAddress: string
  socks5Port?: number
  socks5Username?: string
  /** 明文 SOCKS5 密码 — DB 明文存储, 后台受信场景直接下发, 编辑时 fill 进 type=password 输入框 */
  socks5Password?: string
  /** 1=available 2=occupied 3=testing 4=blacklisted 5=cooling 6=degraded */
  status: number
  assignedMemberId?: string
  assignedAt?: string
  coolingUntil?: string
  assignCount?: number
  lastHealthAt?: string
  remark?: string
  createdAt?: string
  updatedAt?: string
}

export interface ResourceIpPoolQuery {
  pageNo?: number
  pageSize?: number
  keyword?: string
  status?: number
  region?: string
  ipTypeId?: string
}

export interface ResourceIpPoolSaveDTO {
  region?: string
  ipTypeId?: string
  ipAddress?: string
  socks5Port?: number
  socks5Username?: string
  socks5Password?: string
  status?: number
  remark?: string
}

export interface PageResult<T> {
  total: number
  records: T[]
}

/** 状态码 → 中文展示标签; UI 端用此映射, 颜色映射由 view 自行决定 (IpPoolList.statusTagType). */
export const IP_POOL_STATUS_LABELS: Record<number, string> = {
  1: '可分配',
  2: '已占用',
  3: '测试中',
  4: '黑名单',
  5: '冷却中',
  6: '降级'
}

export function pageIpPool(params: ResourceIpPoolQuery) {
  return request.get<unknown, PageResult<ResourceIpPool>>('/admin/resource/ip-pool/page', { params })
}

export function getIpPoolDetail(id: string) {
  return request.get<unknown, ResourceIpPool>('/admin/resource/ip-pool/get', { params: { id } })
}

export function createIpPool(dto: ResourceIpPoolSaveDTO) {
  return request.post<unknown, ResourceIpPool>('/admin/resource/ip-pool/create', dto)
}

export function updateIpPool(id: string, dto: ResourceIpPoolSaveDTO) {
  return request.put<unknown, ResourceIpPool>('/admin/resource/ip-pool/update', dto, { params: { id } })
}

export function deleteIpPool(id: string) {
  return request.delete<unknown, void>('/admin/resource/ip-pool/delete', { params: { id } })
}

/** 退订: occupied → cooling, 一段时间后由调度器扫回 available. */
export function releaseIpPool(id: string) {
  return request.post<unknown, void>('/admin/resource/ip-pool/release', null, { params: { id } })
}

/** SOCKS5 测试入参; 全部必填, 后端做非空 + 范围校验, 不再兜底. */
export interface Socks5TestParams {
  /** echo-IP 端点; 必须 http/https */
  echoUrl: string
  /** TCP 建连超时毫秒; 500-60000 */
  connectTimeoutMs: number
  /** HTTP 读响应超时毫秒; 500-60000 */
  readTimeoutMs: number
}

/** Socks5TestParams 的前端默认值; 给弹框初始化用, 用户可改. */
export const SOCKS5_TEST_DEFAULTS: Readonly<Socks5TestParams> = Object.freeze({
  echoUrl: 'https://api.ipify.org/',
  connectTimeoutMs: 5000,
  readTimeoutMs: 10000
})

/**
 * SOCKS5 拨号测试结果; 后端不解析响应体, 透传 HTTP status + body 原文给前端控制台.
 * success 仅表示"拨号 + HTTP 往返完成", 4xx/5xx 也算 success=true.
 */
export interface Socks5TestResult {
  success: boolean
  elapsedMs: number
  echoUrl: string
  connectTimeoutMs: number
  readTimeoutMs: number
  /** HTTP 响应状态码; success=true 时有值, 拨号失败时 0 */
  httpStatus: number
  /** 响应体原文; success=true 时有值 (可能空串) */
  rawResponse?: string
  /** 拨号失败原因; 仅 success=false 时有值 */
  error?: string
}

/**
 * 通过该 IP 的 SOCKS5 凭据拨号公网 echo-IP 端点, 验证 SOCKS5 是否真的工作 + 出网 IP 是否符合预期.
 *
 * @param id     resource_ip_pool.id
 * @param params 全字段必填; 调用方一般 `{ ...SOCKS5_TEST_DEFAULTS, echoUrl: 用户输入 }` 拼造
 */
export function testIpPoolSocks5(id: string, params: Socks5TestParams) {
  return request.post<unknown, Socks5TestResult>('/admin/resource/ip-pool/test-socks5', params, { params: { id } })
}

// ===== SOCKS5 独立部署 (走 nook-biz-node Socks5Controller, 流式 HTTP, 不绑定 IP 池条目) =====

export interface Socks5InstallDTO {
  // 远端主机 SSH 凭据 (一次性, 不存)
  sshHost: string
  sshPort: number
  sshUser: string
  /** SSH 密码 (必填) */
  sshPassword: string
  /** SSH 会话握手超时(秒); 5-600 */
  sshTimeoutSeconds: number
  /** SSH 单条命令超时(秒); 5-300 */
  sshOpTimeoutSeconds: number
  /** SCP 上传超时(秒); 5-600 */
  sshUploadTimeoutSeconds: number
  /** 安装脚本最大耗时(秒); 60-3600 */
  installTimeoutSeconds: number

  // SOCKS5 服务参数 — 部署成功后由前端按需调 createIpPool 落库
  socksPort: number
  socksUser: string
  socksPass: string
  /** UFW allow from 来源 CIDR; 推荐填中转线路服务器公网 IP */
  allowFrom?: string
  installUfw: boolean
}

/**
 * 流式部署 SOCKS5 — 后端 chunked transfer 边跑边吐 stdout, 前端 fetch + ReadableStream 边读边回调。
 * 部署不再绑定 IP 池条目; 部署成功后由前端按需调 createIpPool 把 SOCKS5 凭据落库。
 */
export async function installSocks5Stream(
  dto: Socks5InstallDTO,
  onChunk: (chunk: string) => void,
  signal?: AbortSignal
): Promise<void> {
  const userStore = useUserStore()
  const res = await fetch('/api/admin/resource/ip-pool/install-socks5', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: userStore.token
    },
    body: JSON.stringify(dto),
    signal
  })
  if (res.status === 401) {
    userStore.clear()
    throw new Error('登录已过期, 请重新登录')
  }
  if (!res.ok) {
    const text = await res.text()
    throw new Error(`HTTP ${res.status}: ${text || res.statusText}`)
  }
  if (!res.body) {
    throw new Error('当前浏览器不支持流式响应 (Response.body 为空)')
  }
  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    const text = decoder.decode(value, { stream: true })
    if (text) onChunk(text)
  }
  const tail = decoder.decode()
  if (tail) onChunk(tail)
}
