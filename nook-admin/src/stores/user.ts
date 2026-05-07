import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as apiLogin, logout as apiLogout, getCurrentUser, type LoginRequest, type SystemUser } from '@/api/system/auth'

const TOKEN_KEY = 'nook-admin-token'

export const useUserStore = defineStore('user', () => {
  // token 持久化到 localStorage，刷新后能继续走鉴权接口
  const token = ref<string>(localStorage.getItem(TOKEN_KEY) || '')
  const user = ref<SystemUser | null>(null)

  function setToken(t: string) {
    token.value = t
    localStorage.setItem(TOKEN_KEY, t)
  }

  function clear() {
    token.value = ''
    user.value = null
    localStorage.removeItem(TOKEN_KEY)
  }

  async function login(req: LoginRequest) {
    const data = await apiLogin(req)
    setToken(data.token)
    user.value = data.user
    return data
  }

  async function logout() {
    try {
      await apiLogout()
    } finally {
      // 无论后端调用成功与否，本地都清掉
      clear()
    }
  }

  /** 路由守卫用：刷新页面后用 token 拉一次用户信息。 */
  async function fetchCurrentUser() {
    if (!token.value) return null
    user.value = await getCurrentUser()
    return user.value
  }

  return { token, user, setToken, clear, login, logout, fetchCurrentUser }
})
