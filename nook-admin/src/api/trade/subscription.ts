import request from '@/api/request'

/** 订阅 (trade_subscription). */
export interface TradeSubscription {
  id: string
  memberUserId: string
  /** 会员邮箱 (后端 page-sub 补). */
  memberEmail?: string
  planId: string
  planName?: string
  xrayClientId: string
  /** 套餐总流量配额 (GB); 后端 page-sub 补. */
  trafficGb?: number
  /** 本周期已用流量 (字节); 后端 page-sub 补. */
  usedBytes?: number
  /** 所在线路机 server id; 后端 page-sub 补. */
  frontlineServerId?: string
  /** 所在线路机出网 IP; 后端 page-sub 补. */
  frontlineIp?: string
  /** 占用的落地机 server id; 后端 page-sub 补. */
  landingServerId?: string
  /** 占用的落地机出网 IP; 后端 page-sub 补. */
  landingIp?: string
  startedAt?: string
  expiresAt?: string
  /** ACTIVE / EXPIRED / CANCELLED. */
  status: string
  createdAt?: string
}

export interface TradeSubscriptionQuery {
  pageNo?: number
  pageSize?: number
  memberUserId?: string
  planId?: string
  status?: string
}

export interface PageResult<T> {
  total: number
  records: T[]
}

export const SUB_STATUS_LABELS: Record<string, string> = {
  ACTIVE: '生效中',
  EXPIRED: '已过期',
  CANCELLED: '已取消'
}

export const SUB_STATUS_TAG_TYPE: Record<string, 'success' | 'warning' | 'default'> = {
  ACTIVE: 'success',
  EXPIRED: 'warning',
  CANCELLED: 'default'
}

export const SUB_STATUS_OPTIONS = [
  { label: '全部', value: undefined as string | undefined },
  { label: '生效中', value: 'ACTIVE' },
  { label: '已过期', value: 'EXPIRED' },
  { label: '已取消', value: 'CANCELLED' }
]

export function pageTradeSubscription(params: TradeSubscriptionQuery) {
  return request.get<unknown, PageResult<TradeSubscription>>('/admin/trade/subscription/page-sub', { params })
}

/** 各套餐当前活跃订阅数 (planId → 数量); 套餐卡片展示订阅人数用. */
export function countActiveSubByPlan() {
  return request.get<unknown, Record<string, number>>('/admin/trade/subscription/count-active-by-plan')
}

/** admin 代客下单 (allocator 自动选址 + 开通). */
export function adminCreateSubscription(memberUserId: string, planId: string) {
  return request.post<unknown, TradeSubscription>('/admin/trade/subscription/create-sub', { memberUserId, planId })
}

export function cancelSubscription(id: string) {
  return request.post<unknown, boolean>('/admin/trade/subscription/cancel-sub', null, { params: { id } })
}
