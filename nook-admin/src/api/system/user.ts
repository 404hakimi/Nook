import request from '@/api/request'

/** 后台用户实体（与后端 SystemUserVO 对齐）。 */
export interface SystemUser {
  id: string
  username: string
  realName?: string
  email?: string
  /** super_admin / operator / devops */
  role: string
  /** 1=正常 2=禁用 */
  status: number
  lastLoginAt?: string
  lastLoginIp?: string
  remark?: string
  createdAt?: string
}

export interface SystemUserQuery {
  pageNo?: number
  pageSize?: number
  keyword?: string
  status?: number
  role?: string
}

export interface CreateSystemUserDTO {
  username: string
  password: string
  realName?: string
  email?: string
  role: string
  remark?: string
}

export interface UpdateSystemUserDTO {
  realName?: string
  email?: string
  role?: string
  status?: number
  remark?: string
}

export interface PageResult<T> {
  total: number
  records: T[]
}

/** 角色码到中文展示名。 */
export const ROLE_LABELS: Record<string, string> = {
  super_admin: '超级管理员',
  operator: '运营',
  devops: '运维'
}

export function pageSystemUsers(params: SystemUserQuery) {
  return request.get<unknown, PageResult<SystemUser>>('/admin/system/users', { params })
}

export function getSystemUserDetail(id: string) {
  return request.get<unknown, SystemUser>(`/admin/system/users/${id}`)
}

export function createSystemUser(dto: CreateSystemUserDTO) {
  return request.post<unknown, SystemUser>('/admin/system/users', dto)
}

export function updateSystemUser(id: string, dto: UpdateSystemUserDTO) {
  return request.put<unknown, SystemUser>(`/admin/system/users/${id}`, dto)
}

export function deleteSystemUser(id: string) {
  return request.delete<unknown, void>(`/admin/system/users/${id}`)
}

export function resetSystemUserPassword(id: string, newPassword: string) {
  return request.put<unknown, void>(`/admin/system/users/${id}/password`, { newPassword })
}
