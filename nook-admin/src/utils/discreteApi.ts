import type { DialogApi, MessageApi } from 'naive-ui'

/**
 * Naive UI 的 useMessage / useDialog 必须在 NMessageProvider / NDialogProvider 子树的 setup 内调用,
 * 但 axios 拦截器 / pinia action / store getter 等模块级代码不在 setup 上下文里, 直接调会报错。
 *
 * <p>本文件提供一对全局 setter / getter: 应用启动时由 DiscreteApiProvider.vue (挂在 App.vue 内层)
 * 把 useMessage() / useDialog() 实例传过来; 模块级代码通过 message / dialog 这两个 Proxy 透传调用。
 *
 * <p>时序保证: App.vue 必须先 mount, 实例才会被 set; axios 拦截器在用户首次发请求时才触发, 此时实例已就绪。
 */
let _messageApi: MessageApi | null = null
let _dialogApi: DialogApi | null = null

export function setMessageApi(api: MessageApi) {
  _messageApi = api
}
export function setDialogApi(api: DialogApi) {
  _dialogApi = api
}

/**
 * 模块级 message; 用法 import { message } from '@/utils/discreteApi'; message.error('xxx')
 * 调用前若实例未就绪 (App 未 mount) 会抛错; 实际业务中不会触发这个分支。
 */
export const message: MessageApi = new Proxy({} as MessageApi, {
  get(_t, key: string) {
    if (!_messageApi) {
      throw new Error('Naive UI message API 未就绪; 确认 App.vue 已 mount + DiscreteApiProvider 已加载')
    }
    return (_messageApi as Record<string, unknown>)[key]
  }
})

export const dialog: DialogApi = new Proxy({} as DialogApi, {
  get(_t, key: string) {
    if (!_dialogApi) {
      throw new Error('Naive UI dialog API 未就绪; 确认 App.vue 已 mount + DiscreteApiProvider 已加载')
    }
    return (_dialogApi as Record<string, unknown>)[key]
  }
})
