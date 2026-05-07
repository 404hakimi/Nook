import { ref, watch } from 'vue'

export type Theme = 'light' | 'dark'

const THEME_KEY = 'nook-admin-theme'

// 模块级单例：全应用共享同一个主题状态
const theme = ref<Theme>(((localStorage.getItem(THEME_KEY) as Theme) || 'light'))

// 任何修改都同步到 <html data-theme> 与 localStorage
watch(
  theme,
  (v) => {
    document.documentElement.setAttribute('data-theme', v)
    localStorage.setItem(THEME_KEY, v)
  },
  { immediate: true }
)

export function useTheme() {
  function toggle() {
    theme.value = theme.value === 'light' ? 'dark' : 'light'
  }
  function set(v: Theme) {
    theme.value = v
  }
  return { theme, toggle, set }
}
