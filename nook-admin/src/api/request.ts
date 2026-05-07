import axios, { type AxiosInstance, type InternalAxiosRequestConfig, type AxiosResponse } from 'axios'
import { useUserStore } from '@/stores/user'
import { useToast } from '@/composables/useToast'
import router from '@/router'
import type { ApiResult } from './types'

/** axios 实例：开发期走 vite proxy 转发到 :8080；生产打包后由 nginx 反代。 */
const request: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 15000
})

// 请求拦截器：携带 sa-token
request.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const userStore = useUserStore()
  if (userStore.token) {
    // 与后端 application.yml 中 sa-token.token-name 一致
    config.headers.set('Authorization', userStore.token)
  }
  return config
})

// 响应拦截器：把 ApiResult 拆开；非 0 抛错并统一提示
request.interceptors.response.use(
  (resp: AxiosResponse<ApiResult>) => {
    const body = resp.data
    if (body.code === 0) {
      return body.data as never
    }
    const toast = useToast()
    // 1002 = CommonErrorCode.UNAUTHORIZED；token 失效，清登录态并回登录页
    if (body.code === 1002) {
      const userStore = useUserStore()
      userStore.clear()
      router.push({ name: 'login' })
    }
    toast.error(body.message || '请求失败')
    return Promise.reject(new Error(body.message))
  },
  (err) => {
    const toast = useToast()
    toast.error(err?.response?.data?.message || err.message || '网络异常')
    return Promise.reject(err)
  }
)

export default request
