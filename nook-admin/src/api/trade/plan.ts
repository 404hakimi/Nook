import request from '@/api/request'

/** 套餐 (trade_plan); enabled 1=上架 0=下架. */
export interface TradePlan {
  id: string
  code: string
  name: string
  regionCode: string
  ipTypeId?: string
  /** 月配额 GB. */
  trafficGb: number
  /** 账面带宽 Mbps (仅展示). */
  bandwidthMbps?: number
  periodDays: number
  /** 同时连接 IP 数; 0=不限. */
  limitIp: number
  priceCny: number
  costBasisCny?: number
  enabled: number
  remark?: string
  createdAt?: string
  /** SKU 池容量 (LIVE 落地机数). */
  capacityTotal?: number
  capacityAvailable?: number
  capacityOccupied?: number
}

/** 套餐 创建/更新 入参; 更新时仅 name/bandwidthMbps/costBasisCny/remark 生效. */
export interface TradePlanSaveDTO {
  id?: string
  code: string
  name: string
  regionCode: string
  ipTypeId?: string
  trafficGb: number
  bandwidthMbps?: number
  periodDays: number
  limitIp?: number
  priceCny: number
  costBasisCny?: number
  remark?: string
}

/** 套餐关联资源 (含 enrich). */
export interface TradePlanResource {
  id: string
  resourceType: 'FRONTLINE' | 'LANDING'
  resourceId: string
  enabled: number
  name?: string
  ipAddress?: string
  lifecycleState?: string
  /** 占用状态 (仅 landing). */
  landingStatus?: string
}

export interface TradePlanQuery {
  pageNo?: number
  pageSize?: number
  regionCode?: string
  ipTypeId?: string
  enabled?: number
  keyword?: string
}

export interface PageResult<T> {
  total: number
  records: T[]
}

export function pageTradePlan(params: TradePlanQuery) {
  return request.get<unknown, PageResult<TradePlan>>('/admin/trade/plan/page-plan', { params })
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
  return request.post<unknown, boolean>('/admin/trade/plan/toggle-enabled', null, { params: { id, enabled } })
}

export function deleteTradePlan(id: string) {
  return request.delete<unknown, boolean>('/admin/trade/plan/delete-plan', { params: { id } })
}

export function bindTradePlanResource(planId: string, resourceType: string, resourceId: string) {
  return request.post<unknown, boolean>('/admin/trade/plan/bind-resource', { planId, resourceType, resourceId })
}

export function unbindTradePlanResource(id: string) {
  return request.post<unknown, boolean>('/admin/trade/plan/unbind-resource', null, { params: { id } })
}

export function listTradePlanResource(planId: string) {
  return request.get<unknown, TradePlanResource[]>('/admin/trade/plan/list-resource', { params: { planId } })
}
