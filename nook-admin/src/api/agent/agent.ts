import request from '@/api/request'

/** Agent 在线状态. */
export type AgentOnlineState = 'ONLINE' | 'WARN' | 'TEMP_UNHEALTHY' | 'OFFLINE' | 'NEVER'

/** Agent 列表项. */
export interface AgentListItem {
  serverId: string
  serverName: string
  host: string
  /** INSTALLING / READY / LIVE / RETIRED */
  lifecycleState: string
  agentVersion?: string
  lastHeartbeatAt?: string
  tempUnhealthy?: number
  elapsedSec?: number
  onlineState: AgentOnlineState

  /** 运行时配置: NEVER_CONFIGURED / SYNCED / PENDING. */
  configSyncState?: ConfigSyncState
}

export type ConfigSyncState = 'NEVER_CONFIGURED' | 'SYNCED' | 'PENDING'

export const CONFIG_SYNC_LABELS: Record<ConfigSyncState, string> = {
  NEVER_CONFIGURED: '未配置',
  SYNCED: '已同步',
  PENDING: '待应用'
}

export const CONFIG_SYNC_TAG_TYPE: Record<ConfigSyncState, 'default' | 'success' | 'warning'> = {
  NEVER_CONFIGURED: 'default',
  SYNCED: 'success',
  PENDING: 'warning'
}

export interface AgentDetail extends AgentListItem {
  lastAgentSeenIp?: string
  consecutiveMiss?: number
  /** agent_token 末 8 位脱敏. */
  agentTokenSuffix?: string
}

/** 清日志入参; path 必须落白名单 (/var/log /home/socks5/logs /home/xray/logs). */
export interface TruncateLogReq {
  paths: string[]
}

export function listAgents() {
  return request.get<unknown, AgentListItem[]>('/admin/agent/list')
}

export function getAgentDetail(serverId: string) {
  return request.get<unknown, AgentDetail>(`/admin/agent/${serverId}`)
}

/** 派升级 task; agent 拉 backend 当前部署的 binary, 无版本选择. 返 taskId. */
export function upgradeAgent(serverId: string) {
  return request.post<unknown, string>(`/admin/agent/${serverId}/upgrade`)
}

/** Agent task 历史项 (升级 / 改配置 / 清日志 / xray_* / ping). */
export interface AgentTaskHistoryItem {
  id: string
  taskType: string
  /** PENDING / PICKED / SUCCESS / FAILED. */
  status: string
  taskPayload?: string
  resultPayload?: string
  retryCount?: number
  createdAt?: string
  pickedAt?: string
  updatedAt?: string
}

/** 取某 server 最近 N 条 task. */
export function listAgentTasks(serverId: string, limit = 20) {
  return request.get<unknown, AgentTaskHistoryItem[]>(`/admin/agent/${serverId}/tasks`, {
    params: { limit }
  })
}

/** 派清日志 task; 返回 taskId. */
export function truncateLog(serverId: string, req: TruncateLogReq) {
  return request.post<unknown, string>(`/admin/agent/${serverId}/truncate-log`, req)
}

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
