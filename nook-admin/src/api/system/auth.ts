import request from '@/api/request'

export interface LoginRequest {
  username: string
  password: string
}

export interface SystemUser {
  id: string
  username: string
  realName?: string
  email?: string
  phone?: string
  role: string
  status: number
  lastLoginAt?: string
}

export interface LoginVO {
  token: string
  expiresIn: number
  user: SystemUser
}

/** 登录。 */
export function login(req: LoginRequest) {
  return request.post<unknown, LoginVO>('/admin/system/auth/login', req)
}

/** 登出（幂等）。 */
export function logout() {
  return request.post<unknown, void>('/admin/system/auth/logout')
}

/** 获取当前登录用户信息。 */
export function getCurrentUser() {
  return request.get<unknown, SystemUser>('/admin/system/auth/me')
}
