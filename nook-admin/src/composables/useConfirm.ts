import { ref } from 'vue'

export type ConfirmType = 'info' | 'warning' | 'danger'

export interface ConfirmOptions {
  title?: string
  message: string
  confirmText?: string
  cancelText?: string
  type?: ConfirmType
}

interface ConfirmState extends Required<Omit<ConfirmOptions, 'type'>> {
  open: boolean
  type: ConfirmType
  resolve: ((v: boolean) => void) | null
}

// 模块级单例：跨组件共享对话框状态；同一时刻只允许一个 confirm
const state = ref<ConfirmState>({
  open: false,
  title: '提示',
  message: '',
  confirmText: '确定',
  cancelText: '取消',
  type: 'info',
  resolve: null
})

export function useConfirm() {
  function confirm(opts: ConfirmOptions): Promise<boolean> {
    return new Promise((resolve) => {
      // 上一个未关闭的 confirm 视为取消，避免 promise 泄漏
      if (state.value.resolve) state.value.resolve(false)
      state.value = {
        open: true,
        title: opts.title ?? '提示',
        message: opts.message,
        confirmText: opts.confirmText ?? '确定',
        cancelText: opts.cancelText ?? '取消',
        type: opts.type ?? 'info',
        resolve
      }
    })
  }

  function handleConfirm() {
    state.value.resolve?.(true)
    state.value.open = false
    state.value.resolve = null
  }

  function handleCancel() {
    state.value.resolve?.(false)
    state.value.open = false
    state.value.resolve = null
  }

  return { state, confirm, handleConfirm, handleCancel }
}
