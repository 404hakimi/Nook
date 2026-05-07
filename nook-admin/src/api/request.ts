import axios, {
  type AxiosInstance,
  type InternalAxiosRequestConfig,
  type AxiosResponse,
  AxiosError
} from 'axios'
import { useUserStore } from '@/stores/user'
import { useToast } from '@/composables/useToast'
import router from '@/router'
import * as Code from './errorCodes'
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

// 401 去重：多接口并发同时返回 1002 时只跳一次登录、只 toast 一次
let unauthorizedHandling = false

function handleUnauthorized(message: string) {
  if (unauthorizedHandling) return
  unauthorizedHandling = true
  const toast = useToast()
  const userStore = useUserStore()
  userStore.clear()
  toast.error(message || '登录已过期，请重新登录')
  router.push({ name: 'login' }).finally(() => {
    // 跳转完成后留 0.5s 再开放，避免短时间内的二次触发
    setTimeout(() => { unauthorizedHandling = false }, 500)
  })
}

// 响应拦截器
request.interceptors.response.use(
  (resp: AxiosResponse<ApiResult>) => {
    const body = resp.data
    if (body.code === Code.SUCCESS) {
      return body.data as never
    }

    const toast = useToast()
    switch (body.code) {
      case Code.UNAUTHORIZED:
        handleUnauthorized(body.message)
        break
      case Code.FORBIDDEN:
        toast.warning(body.message || '无权访问')
        break
      case Code.RATE_LIMITED:
        toast.warning(body.message || '请求过于频繁，请稍后再试')
        break
      case Code.PARAM_INVALID:
        toast.error(body.message || '参数错误')
        break
      case Code.INTERNAL_ERROR:
        toast.error(body.message || '服务器异常')
        break
      default:
        toast.error(body.message || '请求失败')
    }
    return Promise.reject(new Error(body.message || `code=${body.code}`))
  },
  (err: AxiosError<ApiResult>) => {
    // 显式取消的请求（如路由切换打断）静默
    if (axios.isCancel(err)) return Promise.reject(err)

    const toast = useToast()

    if (err.code === 'ECONNABORTED') {
      toast.error('请求超时，请稍后重试')
    } else if (!err.response) {
      // 没有 response 一般是网络问题或后端未启动
      toast.error('网络异常，请检查连接')
    } else {
      const status = err.response.status
      const body = err.response.data as ApiResult | undefined
      if (status === 401) {
        handleUnauthorized(body?.message || '登录已过期')
      } else if (status >= 500) {
        toast.error(body?.message || `服务器异常 (${status})`)
      } else if (status === 404) {
        toast.error('接口不存在')
      } else {
        toast.error(body?.message || `请求失败 (${status})`)
      }
    }
    return Promise.reject(err)
  }
)

export default request
