import request from '@/api/request'

/** 套餐 (trade_plan); enabled 1=上架 0=下架. */
export interface TradePlan {
  id: string
  code: string
  name: string
  /** 产品区域 (匹配落地机/线路机). */
  regionCode: string
  /** 产品 IP 类型 (匹配落地机). */
  ipTypeId: string
  /** 月配额 GB. */
  trafficGb: number
  /** 带宽 Mbps. */
  bandwidthMbps: number
  periodDays: number
  price: number
  enabled: number
  remark?: string
  createdAt?: string
  /** 匹配落地机容量 (同区域+同类型+规格达标). */
  capacityTotal?: number
  capacityAvailable?: number
  capacityOccupied?: number
}

/** 套餐 创建/更新 入参; 更新时仅 name/remark 生效. */
export interface TradePlanSaveDTO {
  id?: string
  code: string
  name: string
  regionCode: string
  ipTypeId: string
  trafficGb: number
  bandwidthMbps: number
  periodDays: number
  price: number
  remark?: string
}

export interface TradePlanQuery {
  pageNo?: number
  pageSize?: number
  /** 区域码集合 (城市选单个, 国家选该国全部城市). */
  regionCodes?: string[]
  ipTypeId?: string
  enabled?: number
  keyword?: string
}

export interface PageResult<T> {
  total: number
  records: T[]
}

export function pageTradePlan(params: TradePlanQuery) {
  const { regionCodes, ...rest } = params
  // 后端 List<String> 用逗号串绑定, 避免 axios 默认数组序列化带 [] 绑不上
  return request.get<unknown, PageResult<TradePlan>>('/admin/trade/plan/page-plan', {
    params: { ...rest, regionCodes: regionCodes && regionCodes.length ? regionCodes.join(',') : undefined }
  })
}

export function getTradePlan(id: string) {
  return request.get<unknown, TradePlan>('/admin/trade/plan/get-plan', { params: { id } })
}

export function createTradePlan(dto: TradePlanSaveDTO) {
  return request.post<unknown, string>('/admin/trade/plan/create-plan', dto)
}

export function updateTradePlan(dto: TradePlanSaveDTO) {
  return request.put<unknown, boolean>('/admin/trade/plan/update-plan', dto)
}

export function toggleTradePlanEnabled(id: string, enabled: boolean) {
  return request.post<unknown, boolean>('/admin/trade/plan/update-plan-enabled', null, { params: { id, enabled } })
}

export function deleteTradePlan(id: string) {
  return request.delete<unknown, boolean>('/admin/trade/plan/delete-plan', { params: { id } })
}
