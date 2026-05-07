<script setup lang="ts">
import { computed } from 'vue'
import { AlertTriangle, AlertCircle, Info } from 'lucide-vue-next'
import { useConfirm, type ConfirmType } from '@/composables/useConfirm'

const { state, handleConfirm, handleCancel } = useConfirm()

const iconMap: Record<ConfirmType, typeof AlertTriangle> = {
  info: Info,
  warning: AlertTriangle,
  danger: AlertCircle
}

const iconClassMap: Record<ConfirmType, string> = {
  info: 'text-info bg-info/10',
  warning: 'text-warning bg-warning/10',
  danger: 'text-error bg-error/10'
}

const confirmBtnClass = computed(() => {
  switch (state.value.type) {
    case 'danger':  return 'btn-error'
    case 'warning': return 'btn-warning'
    default:        return 'btn-primary'
  }
})
</script>

<template>
  <Teleport to="body">
    <!-- 用 modal-open 控制显隐；不直接调 dialog.showModal()，避免与 Vue 状态不同步 -->
    <dialog class="modal" :class="{ 'modal-open': state.open }">
      <div class="modal-box max-w-md">
        <div class="flex items-start gap-3">
          <div :class="['w-10 h-10 rounded-full flex items-center justify-center shrink-0', iconClassMap[state.type]]">
            <component :is="iconMap[state.type]" class="w-5 h-5" />
          </div>
          <div class="flex-1">
            <h3 class="font-semibold text-base">{{ state.title }}</h3>
            <p class="text-sm text-base-content/70 mt-2 whitespace-pre-line">{{ state.message }}</p>
          </div>
        </div>
        <div class="modal-action mt-6">
          <button class="btn btn-ghost btn-sm" @click="handleCancel">{{ state.cancelText }}</button>
          <button :class="['btn btn-sm', confirmBtnClass]" @click="handleConfirm">{{ state.confirmText }}</button>
        </div>
      </div>
      <!-- 点击遮罩等同取消 -->
      <div class="modal-backdrop bg-black/40" @click="handleCancel"></div>
    </dialog>
  </Teleport>
</template>
