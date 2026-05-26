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

/** 已启用区域列表; 用于表单下拉. */
export function listEnabledRegions() {
  return request.get<unknown, SystemRegion[]>('/admin/system/region/list-enabled-region')
}
