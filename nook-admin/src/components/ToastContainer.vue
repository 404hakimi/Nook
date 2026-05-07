<script setup lang="ts">
import { useToast } from '@/composables/useToast'

const { toasts } = useToast()

// DaisyUI alert 颜色按 type 映射
function alertClass(type: string) {
  switch (type) {
    case 'success': return 'alert-success'
    case 'error':   return 'alert-error'
    case 'warning': return 'alert-warning'
    default:        return 'alert-info'
  }
}
</script>

<template>
  <div class="toast toast-top toast-end z-[9999]">
    <div
      v-for="t in toasts"
      :key="t.id"
      :class="['alert', alertClass(t.type), 'shadow-lg']"
    >
      <span>{{ t.message }}</span>
    </div>
  </div>
</template>
