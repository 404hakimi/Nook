import request from '@/api/request'
import { useUserStore } from '@/stores/user'

/** IP 池条目: SOCKS5 落地节点; 一条 = 一台跑了 3proxy 的小 VPS, ip_address 即用户对外暴露的"独享 IP". */
export interface ResourceIpPool {
  id: string
  region: string
  ipTypeId: string
  ipAddress: string
  socks5Host?: string
  socks5Port?: number
  socks5Username?: string
  /** 后端不会下发原文密码, 仅以布尔值标记是否已配置 */
  socks5PasswordConfigured?: boolean
  /** 1=available 2=occupied 3=testing 4=blacklisted 5=cooling 6=degraded */
  status: number
  assignedMemberId?: string
  assignedAt?: string
  coolingUntil?: string
  score?: number | string
  scamalyticsScore?: number
  ipqsScore?: number
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
  socks5Host?: string
  socks5Port?: number
  socks5Username?: string
  /** 编辑时留空 = 保留原值 */
  socks5Password?: string
  status?: number
  score?: number
  scamalyticsScore?: number
  ipqsScore?: number
  remark?: string
}

export interface PageResult<T> {
  total: number
  records: T[]
}

/**
 * 状态码 → 展示标签 + DaisyUI badge 颜色 class.
 * 记得在前端列表里 @apply 这些 class.
 */
export const IP_POOL_STATUS_LABELS: Record<number, string> = {
  1: '可分配',
  2: '已占用',
  3: '测试中',
  4: '黑名单',
  5: '冷却中',
  6: '降级'
}

export const IP_POOL_STATUS_BADGE_CLASS: Record<number, string> = {
  1: 'badge-success',
  2: 'badge-info',
  3: 'badge-warning',
  4: 'badge-error',
  5: 'badge-warning',
  6: 'badge-ghost'
}

export function pageIpPool(params: ResourceIpPoolQuery) {
  return request.get<unknown, PageResult<ResourceIpPool>>('/admin/resource/ip-pool', { params })
}

export function getIpPoolDetail(id: string) {
  return request.get<unknown, ResourceIpPool>(`/admin/resource/ip-pool/${id}`)
}

export function createIpPool(dto: ResourceIpPoolSaveDTO) {
  return request.post<unknown, ResourceIpPool>('/admin/resource/ip-pool', dto)
}

export function updateIpPool(id: string, dto: ResourceIpPoolSaveDTO) {
  return request.put<unknown, ResourceIpPool>(`/admin/resource/ip-pool/${id}`, dto)
}

export function deleteIpPool(id: string) {
  return request.delete<unknown, void>(`/admin/resource/ip-pool/${id}`)
}

/** 退订: occupied → cooling, 一段时间后由调度器扫回 available. */
export function releaseIpPool(id: string) {
  return request.post<unknown, void>(`/admin/resource/ip-pool/${id}/release`)
}

// ===== SOCKS5 一键部署 (走 xray 模块的运维接口, 用流式 HTTP) =====

export interface IpSocks5InstallDTO {
  // 远端主机 SSH 凭据 (一次性, 不存)
  sshHost: string
  sshPort: number
  sshUser: string
  /** sshPassword 与 sshPrivateKey 二选一 */
  sshPassword?: string
  sshPrivateKey?: string
  sshTimeoutSeconds?: number

  // SOCKS5 服务参数
  socksPort: number
  socksUser: string
  socksPass: string
  /** UFW allow from 来源 CIDR; 推荐填中转线路服务器公网 IP */
  allowFrom?: string
  installUfw?: boolean
}

/**
 * 部署 SOCKS5 — 流式接口.
 * 后端用 chunked transfer 边跑边吐 stdout; 前端 fetch + ReadableStream 边读边回调 onChunk.
 *
 * 部署成功后, 后端会把 socksPort/socksUser/socksPass 回写到 IP 池条目.
 * 前端展示完日志后建议刷新一下列表拉最新值.
 */
export async function installSocks5Stream(
  ipId: string,
  dto: IpSocks5InstallDTO,
  onChunk: (chunk: string) => void,
  signal?: AbortSignal
): Promise<void> {
  const userStore = useUserStore()
  const res = await fetch(`/api/admin/xray/ip-pool/${ipId}/install-socks5`, {
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
