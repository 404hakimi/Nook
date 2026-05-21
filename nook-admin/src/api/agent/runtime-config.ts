import request from '@/api/request'

/** Agent 运行时配置详情 + 同步状态. */
export interface AgentRuntimeConfig {
  serverId: string
  configYaml?: string
  updatedAt?: string
  updatedBy?: string
  appliedAt?: string
  appliedYamlMd5?: string
  /** NEVER_CONFIGURED / SYNCED / PENDING. */
  syncState: 'NEVER_CONFIGURED' | 'SYNCED' | 'PENDING'
}

export function getRuntimeConfig(serverId: string) {
  return request.get<unknown, AgentRuntimeConfig>(`/admin/agent-runtime-config/${serverId}`)
}

/** 保存 yaml; backend 派 config_reload task, agent 30s 内拉到 + 自重启. 返 taskId. */
export function saveRuntimeConfig(serverId: string, configYaml: string) {
  return request.put<unknown, string>(`/admin/agent-runtime-config/${serverId}`, { configYaml })
}
