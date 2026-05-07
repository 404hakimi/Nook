import { ref } from 'vue'

export type ToastType = 'success' | 'error' | 'info' | 'warning'

export interface ToastItem {
  id: number
  message: string
  type: ToastType
}

// 模块级单例：跨组件共享同一个队列
const toasts = ref<ToastItem[]>([])
let counter = 0

function show(message: string, type: ToastType = 'info', durationMs = 3000) {
  const id = ++counter
  toasts.value.push({ id, message, type })
  // 到期自动移除；用户也可以悬浮触发额外逻辑后续再说
  setTimeout(() => {
    toasts.value = toasts.value.filter((t) => t.id !== id)
  }, durationMs)
}

export function useToast() {
  return {
    toasts,
    success: (msg: string) => show(msg, 'success'),
    error: (msg: string) => show(msg, 'error'),
    info: (msg: string) => show(msg, 'info'),
    warning: (msg: string) => show(msg, 'warning')
  }
}
