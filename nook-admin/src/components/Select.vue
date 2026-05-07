<script setup lang="ts" generic="T">
import { computed } from 'vue'
import { ChevronDown } from 'lucide-vue-next'

/**
 * DaisyUI dropdown 实现的 Select：
 *   - 触发器外观对齐 input-bordered；展开面板走 menu 而非浏览器原生 option，
 *     避免 native <select> 弹层被浏览器接管、与主题脱节(暗色模式/状态色都失真)。
 *   - 通过 :focus-within 自动展开；选项点击后 blur(document.activeElement) 收起。
 */
interface Option {
  label: string
  value: T
}

const props = withDefaults(
  defineProps<{
    modelValue: T
    options: Option[]
    placeholder?: string
    size?: 'xs' | 'sm' | 'md' | 'lg'
    /** Tailwind 宽度类，默认 w-full */
    width?: string
    disabled?: boolean
    /** 校验失败时显示 error 边框 */
    error?: boolean
    /** 触发器/面板水平对齐 */
    align?: 'start' | 'end'
    /** 面板展开方向；底部控件用 'top' 向上展开避免出页 */
    direction?: 'bottom' | 'top'
  }>(),
  {
    size: 'sm',
    placeholder: '请选择',
    width: 'w-full',
    align: 'start',
    direction: 'bottom'
  }
)

const emit = defineEmits<{
  (e: 'update:modelValue', v: T): void
  /** 与 native <select> 的 change 对齐：值变化后触发，便于父组件接 onSearch 等副作用 */
  (e: 'change', v: T): void
}>()

const selectedLabel = computed(() => {
  const found = props.options.find((o) => o.value === props.modelValue)
  return found?.label ?? props.placeholder
})

function pick(value: T) {
  if (value === props.modelValue) {
    blurActive()
    return
  }
  emit('update:modelValue', value)
  emit('change', value)
  blurActive()
}

function blurActive() {
  if (document.activeElement instanceof HTMLElement) document.activeElement.blur()
}

const triggerSize = computed(() => {
  switch (props.size) {
    case 'xs':
      return 'h-6 min-h-6 text-xs px-2'
    case 'md':
      return 'h-12 min-h-12 text-base px-3'
    case 'lg':
      return 'h-16 min-h-16 text-lg px-4'
    case 'sm':
    default:
      return 'h-8 min-h-8 text-sm px-3'
  }
})
</script>

<template>
  <div
    class="dropdown"
    :class="[
      width,
      align === 'end' ? 'dropdown-end' : '',
      direction === 'top' ? 'dropdown-top' : '',
      disabled ? 'pointer-events-none opacity-60' : ''
    ]"
  >
    <div
      :tabindex="disabled ? -1 : 0"
      role="button"
      :class="[
        'flex items-center justify-between gap-2 cursor-pointer select-none',
        'border bg-base-100 rounded-lg transition-colors',
        error ? 'border-error' : 'border-base-300 hover:border-base-content/40 focus:border-primary',
        triggerSize,
        width
      ]"
    >
      <span class="truncate font-normal">{{ selectedLabel }}</span>
      <ChevronDown class="w-4 h-4 shrink-0 opacity-60" />
    </div>
    <ul
      tabindex="0"
      :class="[
        'dropdown-content menu menu-sm bg-base-100 rounded-box shadow-lg border border-base-200 z-30 p-1 max-h-72 overflow-y-auto flex-nowrap',
        direction === 'top' ? 'mb-1' : 'mt-1',
        width
      ]"
    >
      <li v-for="o in options" :key="String(o.value)">
        <a :class="{ active: o.value === modelValue }" @click="pick(o.value)">{{ o.label }}</a>
      </li>
    </ul>
  </div>
</template>
