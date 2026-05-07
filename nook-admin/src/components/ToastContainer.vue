<script setup lang="ts">
import { CheckCircle2, XCircle, AlertTriangle, Info } from 'lucide-vue-next'
import { useToast, type ToastType } from '@/composables/useToast'

const { toasts } = useToast()

// DaisyUI alert 颜色按 type 映射
function alertClass(type: ToastType) {
  switch (type) {
    case 'success': return 'alert-success'
    case 'error':   return 'alert-error'
    case 'warning': return 'alert-warning'
    default:        return 'alert-info'
  }
}

function iconFor(type: ToastType) {
  switch (type) {
    case 'success': return CheckCircle2
    case 'error':   return XCircle
    case 'warning': return AlertTriangle
    default:        return Info
  }
}
</script>

<template>
  <Teleport to="body">
    <!-- 顶部偏移 5rem 确保始终在 navbar 之下；右侧固定 1rem -->
    <div class="fixed top-20 right-4 z-[9999] flex flex-col gap-2 pointer-events-none w-80 max-w-[calc(100vw-2rem)]">
      <TransitionGroup name="toast">
        <div
          v-for="t in toasts"
          :key="t.id"
          :class="['alert', alertClass(t.type), 'shadow-xl pointer-events-auto']"
        >
          <component :is="iconFor(t.type)" class="w-5 h-5 shrink-0" />
          <span class="flex-1 text-sm">{{ t.message }}</span>
        </div>
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<style scoped>
.toast-enter-active,
.toast-leave-active {
  transition: transform 0.3s ease, opacity 0.3s ease;
}
.toast-enter-from {
  transform: translateX(100%);
  opacity: 0;
}
.toast-leave-to {
  transform: translateX(100%);
  opacity: 0;
}
.toast-leave-active {
  position: absolute;
  width: 100%;
}
</style>
