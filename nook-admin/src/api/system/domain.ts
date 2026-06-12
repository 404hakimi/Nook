import request from '@/api/request'

/** 系统域名 = 一级域名 (根域 + Cloudflare 配置); xray_install.domainId 绑它, 二级域名在 xray_install.subdomain. */
export interface SystemDomain {
  id: string
  /** 根域名 (一级域名, 如 karsu.cc). */
  domain: string
  /** Cloudflare Zone ID. */
  cfZoneId?: string
  /** Cloudflare API Token (DNS-01 签发/续期). */
  cfApiToken?: string
  remark?: string
  createdAt?: string
  updatedAt?: string
}

/** 域名保存入参 (创建 / 更新共用容器; 更新时 id 走 query 参数). */
export interface SystemDomainSaveDTO {
  id?: string
  domain: string
  cfZoneId?: string
  cfApiToken?: string
  remark?: string
}

/** 域名列表 (创建倒序; 管理页 + 装机下拉用). */
export function listSystemDomain() {
  return request.get<unknown, SystemDomain[]>('/admin/system/domain/list-domain')
}

/** 域名详情. */
export function getSystemDomain(id: string) {
  return request.get<unknown, SystemDomain>('/admin/system/domain/get-domain', { params: { id } })
}

/** 创建域名; 返回新 id. */
export function createSystemDomain(dto: SystemDomainSaveDTO) {
  return request.post<unknown, string>('/admin/system/domain/create-domain', dto)
}

/** 更新域名. */
export function updateSystemDomain(id: string, dto: SystemDomainSaveDTO) {
  return request.put<unknown, void>('/admin/system/domain/update-domain', dto, { params: { id } })
}

/** 删除域名. */
export function deleteSystemDomain(id: string) {
  return request.delete<unknown, void>('/admin/system/domain/delete-domain', { params: { id } })
}
