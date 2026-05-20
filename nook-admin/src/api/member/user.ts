import request from '@/api/request'

/** 会员账户实体 (admin 视角, 与后端 AdminMemberRespVO 对齐). */
export interface MemberAccount {
  id: string
  email: string
  subToken: string
  /** 1=正常 2=禁用 */
  status: number
  lastLoginAt?: string
  lastLoginIp?: string
  remark?: string
  createdAt?: string
  updatedAt?: string
}

export interface MemberAccountQuery {
  pageNo?: number
  pageSize?: number
  /** 邮箱模糊匹配 */
  keyword?: string
  status?: number
}

export interface PageResult<T> {
  total: number
  records: T[]
}

export function pageMemberAccounts(params: MemberAccountQuery) {
  return request.get<unknown, PageResult<MemberAccount>>('/admin/member/users/page', { params })
}

export function getMemberAccountDetail(id: string) {
  return request.get<unknown, MemberAccount>(`/admin/member/users/${id}`)
}

export function disableMemberAccount(id: string) {
  return request.post<unknown, void>(`/admin/member/users/${id}/disable`)
}

export function enableMemberAccount(id: string) {
  return request.post<unknown, void>(`/admin/member/users/${id}/enable`)
}

export function updateMemberAccountRemark(id: string, remark: string) {
  return request.put<unknown, void>(`/admin/member/users/${id}/remark`, { remark })
}
