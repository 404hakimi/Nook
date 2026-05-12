import request from '@/api/request'
import type { OpType } from './op-log'

/** 后端 op_config 行 */
export interface OpConfig {
  id: string
  opType: OpType | string
  name: string
  execTimeoutSeconds: number
  waitTimeoutSeconds: number
  maxRetry: number
  enabled: boolean
  description?: string
  updatedAt?: string
}

/** OpType 下拉选项 (含是否已 configured 标记, Create 弹框过滤已建项) */
export interface OpTypeOption {
  opType: OpType | string
  configured: boolean
}

/** 创建入参; opType + name 必填 */
export interface OpConfigCreateReq {
  opType: string
  name: string
  execTimeoutSeconds: number
  waitTimeoutSeconds: number
  maxRetry?: number
  enabled?: boolean
  description?: string
}

/** 编辑入参; opType 不可改 (由 Service 维护); 其余必填 */
export interface OpConfigSaveReq {
  name: string
  execTimeoutSeconds: number
  waitTimeoutSeconds: number
  maxRetry: number
  enabled: boolean
  description?: string
}

export function listOpConfig() {
  return request.get<unknown, OpConfig[]>('/admin/operation/op-config')
}

export function listOpTypeOptions() {
  return request.get<unknown, OpTypeOption[]>('/admin/operation/op-config/op-types')
}

export function getOpConfigDetail(id: string) {
  return request.get<unknown, OpConfig>(`/admin/operation/op-config/${id}`)
}

/** 新建; 同 opType 重复后端会抛 OP_CONFIG_DUPLICATE */
export function createOpConfig(body: OpConfigCreateReq) {
  return request.post<unknown, string>('/admin/operation/op-config', body)
}

export function updateOpConfig(id: string, body: OpConfigSaveReq) {
  return request.put<unknown, boolean>(`/admin/operation/op-config/${id}`, body)
}

/** 删除 → 该 opType 失去配置, 后续 enqueue 会被 isEnabled=false 拒绝 */
export function deleteOpConfig(id: string) {
  return request.delete<unknown, boolean>(`/admin/operation/op-config/${id}`)
}
