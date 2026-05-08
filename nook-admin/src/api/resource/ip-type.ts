import request from '@/api/request'

/** IP 类型: ISP / 机房 / 家宽; 由 99_seed.sql 初始化, 运营一般不动. */
export interface ResourceIpType {
  id: string
  /** 类型编码: isp / datacenter / residential */
  code: string
  name: string
  description?: string
  sortOrder: number
  /** IP 退订后冷却分钟数; 不同类型可不同 */
  coolingMinutes: number
}

export const IP_TYPE_CODE_LABELS: Record<string, string> = {
  isp: 'ISP',
  datacenter: '机房',
  residential: '家宽'
}

/** 全量列出, 已按 sortOrder 升序; 用于 IP 池录入 / 套餐配置下拉. */
export function listIpTypes() {
  return request.get<unknown, ResourceIpType[]>('/admin/resource/ip-types')
}
