import request from '@/api/request'

/** op_log.status; 终态四种, 活跃两种. */
export type OpStatus =
  | 'QUEUED'
  | 'RUNNING'
  | 'DONE'
  | 'FAILED'
  | 'CANCELLED'
  | 'TIMED_OUT'

/** 后端 OpType 枚举名; 新加 op 时同步更新这里. */
export type OpType =
  | 'XRAY_RESTART'
  | 'SERVER_PROVISION'
  | 'SERVER_AUTOSTART'
  | 'CLIENT_PROVISION'
  | 'CLIENT_REVOKE'
  | 'CLIENT_ROTATE'
  | 'CLIENT_SYNC'
  | 'SERVER_REPLAY'
  | 'SERVER_RECONCILE'

/** 单条 op_log; 列表/详情共用; 列表场景 paramsJson/errorMsg 为 null. */
export interface OpLog {
  id: string
  serverId: string
  /** 后端 OpLogEnricher 补充: resource_server.name; 缺失时显示 serverId 兜底 */
  serverName?: string
  opType: OpType
  targetId?: string
  /** 后端 OpLogEnricher 补充: 业务侧目标友好名 (如 xray_client.client_email) */
  targetName?: string
  /** admin id 或系统占位 (SYSTEM / SCHEDULER); UI 优先取 operatorName */
  operator?: string
  /** 后端 OpLogEnricher 补充: admin realName/username; 系统调度直接是占位符字面值 */
  operatorName?: string
  status: OpStatus
  currentStep?: string
  progressPct?: number
  lastMessage?: string
  errorCode?: string
  errorMsg?: string
  paramsJson?: string
  enqueuedAt: string
  startedAt?: string
  endedAt?: string
  /** started_at → ended_at 毫秒差; 未结束为 null */
  elapsedMs?: number
}

export interface OpLogPageQuery {
  pageNo?: number
  pageSize?: number
  status?: OpStatus
  serverId?: string
  opType?: OpType
}

export interface PageResult<T> {
  total: number
  records: T[]
}

/** status → 中文 + 颜色 (Naive UI tag type). */
export const OP_STATUS_META: Record<
  OpStatus,
  { label: string; tagType: 'default' | 'info' | 'success' | 'warning' | 'error' }
> = {
  QUEUED: { label: '排队中', tagType: 'default' },
  RUNNING: { label: '执行中', tagType: 'info' },
  DONE: { label: '已完成', tagType: 'success' },
  FAILED: { label: '失败', tagType: 'error' },
  CANCELLED: { label: '已取消', tagType: 'warning' },
  TIMED_OUT: { label: '超时', tagType: 'error' }
}

// opType 中文显示从 op_config 表拉, 见 stores/opConfig.ts; 旧 OP_TYPE_LABELS 硬编码已移除

/** 分页查询. */
export function pageOpLog(params: OpLogPageQuery) {
  return request.get<unknown, PageResult<OpLog>>('/admin/operation/op-log/page', { params })
}

/** 单条详情, 含 paramsJson / errorMsg. */
export function getOpLogDetail(id: string) {
  return request.get<unknown, OpLog>('/admin/operation/op-log/get', { params: { id } })
}

/** 取消 QUEUED; 返 true=成功取消, false=已非 QUEUED (前端可刷新). */
export function cancelOpLog(id: string) {
  return request.post<unknown, boolean>('/admin/operation/op-log/cancel', null, { params: { id } })
}
