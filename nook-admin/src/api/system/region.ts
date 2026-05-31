import request from '@/api/request'

/** 区域字典条目 (与后端 SystemRegionRespVO 对齐). */
export interface SystemRegion {
  /** 区域码: JP-TYO / US-LAX / HK 等; 主键. */
  code: string
  countryCode: string
  countryName: string
  city?: string
  displayName: string
  flagEmoji?: string
  /** 1=启用 0=停用 */
  enabled: number
}

/** 区域 新增/编辑 入参 (编辑时 code 不可改). */
export interface SystemRegionSaveDTO {
  code: string
  countryCode: string
  countryName: string
  city?: string
  displayName: string
  flagEmoji?: string
}

/** 已启用区域列表; 用于表单下拉. */
export function listEnabledRegions() {
  return request.get<unknown, SystemRegion[]>('/admin/system/region/list-enabled-region')
}

/** admin 全量区域列表 (关键字 + 启用状态过滤). */
export function listRegions(params?: { keyword?: string; enabled?: number }) {
  return request.get<unknown, SystemRegion[]>('/admin/system/region/list-region', { params })
}

export function createRegion(dto: SystemRegionSaveDTO) {
  return request.post<unknown, string>('/admin/system/region/create-region', dto)
}

export function updateRegion(dto: SystemRegionSaveDTO) {
  return request.put<unknown, boolean>('/admin/system/region/update-region', dto)
}

export function toggleRegionEnabled(code: string, enabled: boolean) {
  return request.post<unknown, boolean>('/admin/system/region/update-region-enabled', null, { params: { code, enabled } })
}
