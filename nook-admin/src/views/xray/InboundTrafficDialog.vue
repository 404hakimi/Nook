<script setup lang="ts">
import { ref, watch } from 'vue'
import { getInboundTraffic, type XrayInbound, type XrayInboundTraffic } from '@/api/xray/inbound'

interface Props {
  modelValue: boolean
  inbound?: XrayInbound | null
}
const props = defineProps<Props>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
}>()

const loading = ref(false)
const data = ref<XrayInboundTraffic | null>(null)

watch(
  () => [props.modelValue, props.inbound?.id],
  async ([open]) => {
    if (!open || !props.inbound) {
      data.value = null
      return
    }
    loading.value = true
    try {
      data.value = await getInboundTraffic(props.inbound.id)
    } catch {
      data.value = null
    } finally {
      loading.value = false
    }
  }
)

function close() {
  emit('update:modelValue', false)
}

function fmtBytes(n: number): string {
  if (!n) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let v = n
  let i = 0
  while (v >= 1024 && i < units.length - 1) {
    v /= 1024
    i++
  }
  return `${v.toFixed(2)} ${units[i]}`
}

function fmtExpiry(ms: number): string {
  if (!ms) return '永不过期'
  return new Date(ms).toLocaleString()
}
</script>

<template>
  <dialog class="modal" :class="{ 'modal-open': modelValue }">
    <div class="modal-box max-w-md">
      <h3 class="text-lg font-semibold mb-4">流量与配额</h3>

      <div v-if="loading" class="py-12 text-center">
        <span class="loading loading-spinner"></span>
      </div>
      <div v-else-if="data" class="space-y-2 text-sm">
        <div class="flex justify-between border-b border-base-200 pb-2">
          <span class="text-base-content/60">Email</span>
          <span class="font-mono">{{ data.clientEmail }}</span>
        </div>
        <div class="flex justify-between border-b border-base-200 pb-2">
          <span class="text-base-content/60">上行</span>
          <span class="font-mono">{{ fmtBytes(data.upBytes) }}</span>
        </div>
        <div class="flex justify-between border-b border-base-200 pb-2">
          <span class="text-base-content/60">下行</span>
          <span class="font-mono">{{ fmtBytes(data.downBytes) }}</span>
        </div>
        <div class="flex justify-between border-b border-base-200 pb-2">
          <span class="text-base-content/60">配额</span>
          <span class="font-mono">{{ data.totalBytes ? fmtBytes(data.totalBytes) : '不限' }}</span>
        </div>
        <div class="flex justify-between border-b border-base-200 pb-2">
          <span class="text-base-content/60">到期</span>
          <span>{{ fmtExpiry(data.expiryEpochMillis) }}</span>
        </div>
        <div class="flex justify-between">
          <span class="text-base-content/60">启用</span>
          <span :class="data.enabled ? 'text-success' : 'text-error'">
            {{ data.enabled ? '✓ 启用' : '✗ 禁用' }}
          </span>
        </div>
      </div>
      <div v-else class="py-8 text-center text-base-content/40">无法加载流量数据</div>

      <div class="modal-action mt-6">
        <button class="btn btn-ghost btn-sm" @click="close">关闭</button>
      </div>
    </div>
    <div class="modal-backdrop bg-black/40" @click="close"></div>
  </dialog>
</template>
