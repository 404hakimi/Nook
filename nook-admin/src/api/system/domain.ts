import request from '@/api/request'

/** 系统域名 (含 Cloudflare 配置); xray_install.domainId 绑定它. */
export interface SystemDomain {
  id: string
  /** 域名 (FQDN). */
  domain: string
  /** Cloudflare Zone ID. */
  cfZoneId?: string
  /** Cloudflare DNS A 记录 ID. */
  cfRecordId?: string
  /** Cloudflare API Token (DNS-01 签发/续期). */
  cfApiToken?: string
  remark?: string
  createdAt?: string
  updatedAt?: string
}

/** 域名保存入参 (创建留空 id, 更新带 id). */
export interface SystemDomainSaveDTO {
  id?: string
  domain: string
  cfZoneId?: string
  cfRecordId?: string
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
export function updateSystemDomain(dto: SystemDomainSaveDTO) {
  return request.put<unknown, boolean>('/admin/system/domain/update-domain', dto)
}

/** 删除域名. */
export function deleteSystemDomain(id: string) {
  return request.delete<unknown, void>('/admin/system/domain/delete-domain', { params: { id } })
}
