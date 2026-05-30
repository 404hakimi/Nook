import request from '@/api/request'

/** 订阅换机日志 (trade_subscription_change_log). */
export interface SubscriptionChangeLog {
  id: string
  subscriptionId: string
  memberUserId: string
  /** 会员邮箱 (后端补). */
  memberEmail?: string
  /** FRONTLINE / LANDING. */
  changeType: string
  oldServerId?: string
  /** 原机器出网 IP (后端补). */
  oldServerIp?: string
  newServerId?: string
  /** 新机器出网 IP (后端补). */
  newServerIp?: string
  /** OPEN / FAULT / TRAFFIC_EXHAUSTED / CUSTOMER_REQUEST / MANUAL / RELEASE. */
  reason: string
  /** 操作者 admin id; 系统触发为 system. */
  operator: string
  createdAt?: string
}

export interface SubscriptionChangeLogQuery {
  pageNo?: number
  pageSize?: number
  subscriptionId?: string
  memberUserId?: string
  changeType?: string
  reason?: string
}

export interface PageResult<T> {
  total: number
  records: T[]
}

export const CHANGE_TYPE_LABELS: Record<string, string> = {
  FRONTLINE: '线路机',
  LANDING: '落地机'
}

export const CHANGE_TYPE_TAG_TYPE: Record<string, 'info' | 'warning'> = {
  FRONTLINE: 'info',
  LANDING: 'warning'
}

export const CHANGE_TYPE_OPTIONS = [
  { label: '全部', value: undefined as string | undefined },
  { label: '线路机', value: 'FRONTLINE' },
  { label: '落地机', value: 'LANDING' }
]

export const CHANGE_REASON_LABELS: Record<string, string> = {
  OPEN: '初始开通',
  FAULT: '机器故障',
  TRAFFIC_EXHAUSTED: '流量耗尽',
  CUSTOMER_REQUEST: '客户换IP',
  MANUAL: '手动调整',
  RELEASE: '退订释放'
}

export const CHANGE_REASON_TAG_TYPE: Record<string, 'success' | 'warning' | 'error' | 'info' | 'default'> = {
  OPEN: 'success',
  FAULT: 'error',
  TRAFFIC_EXHAUSTED: 'warning',
  CUSTOMER_REQUEST: 'info',
  MANUAL: 'default',
  RELEASE: 'default'
}

export const CHANGE_REASON_OPTIONS = [
  { label: '全部', value: undefined as string | undefined },
  { label: '初始开通', value: 'OPEN' },
  { label: '机器故障', value: 'FAULT' },
  { label: '流量耗尽', value: 'TRAFFIC_EXHAUSTED' },
  { label: '客户换IP', value: 'CUSTOMER_REQUEST' },
  { label: '手动调整', value: 'MANUAL' },
  { label: '退订释放', value: 'RELEASE' }
]

export function pageSubscriptionChangeLog(params: SubscriptionChangeLogQuery) {
  return request.get<unknown, PageResult<SubscriptionChangeLog>>('/admin/trade/subscription/change-log/page', { params })
}

/** 某订阅的换机历史 (按时间倒序). */
export function getSubscriptionChangeLog(subscriptionId: string) {
  return request.get<unknown, SubscriptionChangeLog[]>('/admin/trade/subscription/change-log', { params: { subscriptionId } })
}
