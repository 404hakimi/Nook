import request from '@/api/request'

/** 会员账户实体 (admin 视角, 与后端 MemberRespVO 对齐). */
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
  return request.get<unknown, PageResult<MemberAccount>>('/admin/member/user/page-user', { params })
}

export function getMemberAccountDetail(id: string) {
  return request.get<unknown, MemberAccount>('/admin/member/user/get-user', { params: { id } })
}

export function disableMemberAccount(id: string) {
  return request.post<unknown, void>('/admin/member/user/disable-user', null, { params: { id } })
}

export function enableMemberAccount(id: string) {
  return request.post<unknown, void>('/admin/member/user/enable-user', null, { params: { id } })
}

export function updateMemberAccountRemark(id: string, remark: string) {
  return request.put<unknown, void>('/admin/member/user/update-remark', { remark }, { params: { id } })
}

/** 管理员指定新密码重置会员密码; 重置后后端踢出该会员已有会话. */
export function resetMemberAccountPassword(id: string, password: string) {
  return request.put<unknown, void>('/admin/member/user/reset-password', { password }, { params: { id } })
}

/** 会员订阅分享 URL (客户端导入; 后端用公网 base + sub_token 拼). */
export function getMemberSubUrl(id: string) {
  return request.get<unknown, string>('/admin/member/user/get-sub-url', { params: { id } })
}
