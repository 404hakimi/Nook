import request from '@/api/request'
import type { AgentOnlineState } from '@/api/resource/server'

export type { AgentOnlineState }

/** Online state → 中文标签. */
export const AGENT_ONLINE_LABELS: Record<AgentOnlineState, string> = {
  ONLINE: '在线',
  WARN: '心跳延迟 (1-3min)',
  TEMP_UNHEALTHY: '暂时不健康 (3-5min)',
  OFFLINE: '掉线 (≥5min)',
  NEVER: '未上报'
}

export const AGENT_ONLINE_TAG_TYPE: Record<AgentOnlineState, 'success' | 'warning' | 'error' | 'default'> = {
  ONLINE: 'success',
  WARN: 'warning',
  TEMP_UNHEALTHY: 'warning',
  OFFLINE: 'error',
  NEVER: 'default'
}

/** journalctl 级别过滤 (跟后端 ServerProbe.readJournalLog 对齐). */
export type AgentLogLevel = 'all' | 'warning' | 'err'

/** journalctl 日志快照 (跟后端 ServiceLogRespVO 对齐). */
export interface AgentLog {
  unit?: string
  lines: number
  level: AgentLogLevel
  keyword?: string
  log?: string
}

/** 拉 agent (nook-agent) 的 journalctl 日志; 走通用 /admin/resource/server/get-service-log?unit=nook-agent. */
export function getAgentLog(
  serverId: string,
  opts?: { lines?: number; level?: AgentLogLevel; keyword?: string }
) {
  return request.get<unknown, AgentLog>('/admin/resource/server/get-service-log', {
    params: {
      id: serverId,
      unit: 'nook-agent',
      lines: opts?.lines,
      level: opts?.level === 'all' ? undefined : opts?.level,
      keyword: opts?.keyword?.trim() || undefined
    }
  })
}
