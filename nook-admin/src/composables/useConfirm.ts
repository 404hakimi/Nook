import { useDialog } from 'naive-ui'

export type ConfirmType = 'info' | 'warning' | 'danger'

export interface ConfirmOptions {
  title?: string
  message: string
  confirmText?: string
  cancelText?: string
  type?: ConfirmType
}

/**
 * 业务侧 confirm 入口; 桥接到 Naive UI useDialog 让原调用方式 `const { confirm } = useConfirm()` 不变。
 *
 * <p>type 映射: danger → 红色按钮 / warning → 橙色 / info → 蓝色; 由 Naive UI 主题色直接控制。
 * 必须在 setup() 内调用; 全局 NDialogProvider 已在 App.vue 挂一次。
 */
export function useConfirm() {
  const dialog = useDialog()

  function confirm(opts: ConfirmOptions): Promise<boolean> {
    return new Promise((resolve) => {
      const fn = opts.type === 'danger'
          ? dialog.error
          : opts.type === 'warning'
              ? dialog.warning
              : dialog.info
      // 任意分支只允许 resolve 一次, 防 onClose 与 onPositiveClick 重复触发
      let settled = false
      const settle = (v: boolean) => {
        if (settled) return
        settled = true
        resolve(v)
      }
      fn({
        title: opts.title ?? '提示',
        content: opts.message,
        positiveText: opts.confirmText ?? '确定',
        negativeText: opts.cancelText ?? '取消',
        // 危险动作给 "确定" 按钮加 error 主色; warning / info 走默认强调色
        positiveButtonProps: opts.type === 'danger' ? { type: 'error' } : undefined,
        onPositiveClick: () => settle(true),
        onNegativeClick: () => settle(false),
        // 用户点遮罩 / 按 ESC 关闭 → 走取消语义, 防 Promise 泄漏
        onClose: () => settle(false),
        onMaskClick: () => settle(false)
      })
    })
  }

  return { confirm }
}
