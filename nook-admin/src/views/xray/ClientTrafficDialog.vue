<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RefreshCw } from 'lucide-vue-next'
import { getClientTraffic, type XrayClient, type XrayClientTraffic } from '@/api/xray/client'

interface Props {
  modelValue: boolean
  inbound?: XrayClient | null
}
const props = defineProps<Props>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
}>()

const loading = ref(false)
const data = ref<XrayClientTraffic | null>(null)
const lastFetched = ref<Date | null>(null)

watch(
  () => [props.modelValue, props.inbound?.id],
  async ([open]) => {
    if (!open || !props.inbound) {
      data.value = null
      return
    }
    await refresh()
  }
)

async function refresh() {
  if (!props.inbound) return
  loading.value = true
  try {
    data.value = await getClientTraffic(props.inbound.id)
    lastFetched.value = new Date()
  } catch {
    data.value = null
  } finally {
    loading.value = false
  }
}

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

function fmtFetchedAt(d: Date | null): string {
  if (!d) return ''
  return d.toLocaleTimeString()
}

const usedBytes = computed(() => (data.value ? data.value.upBytes + data.value.downBytes : 0))
const usagePct = computed(() => {
  if (!data.value || !data.value.totalBytes) return 0
  return Math.min(100, Math.round((usedBytes.value / data.value.totalBytes) * 100))
})
const usageColor = computed(() => {
  if (usagePct.value >= 90) return 'progress-error'
  if (usagePct.value >= 70) return 'progress-warning'
  return 'progress-success'
})
const expiryDays = computed(() => {
  if (!data.value || !data.value.expiryEpochMillis) return null
  const days = Math.floor((data.value.expiryEpochMillis - Date.now()) / 86400_000)
  return days
})
</script>

<template>
  <dialog class="modal" :class="{ 'modal-open': modelValue }">
    <div class="modal-box max-w-md">
      <div class="flex items-center justify-between mb-4">
        <h3 class="text-lg font-semibold">流量与配额</h3>
        <button
          class="btn btn-ghost btn-xs"
          :disabled="loading"
          title="重新拉取"
          @click="refresh"
        >
          <span v-if="loading" class="loading loading-spinner loading-xs"></span>
          <RefreshCw v-else class="w-4 h-4" />
        </button>
      </div>

      <div v-if="loading && !data" class="py-12 text-center">
        <span class="loading loading-spinner"></span>
      </div>
      <div v-else-if="data" class="space-y-3 text-sm">
        <!-- 用量进度条 -->
        <div v-if="data.totalBytes > 0">
          <div class="flex justify-between text-xs mb-1">
            <span class="text-base-content/60">已用 {{ fmtBytes(usedBytes) }} / {{ fmtBytes(data.totalBytes) }}</span>
            <span class="font-mono">{{ usagePct }}%</span>
          </div>
          <progress :class="['progress', usageColor, 'w-full']" :value="usagePct" max="100"></progress>
        </div>
        <div v-else class="text-xs text-base-content/60 bg-base-200 rounded px-2 py-1">
          无流量上限：累计已用 {{ fmtBytes(usedBytes) }}
        </div>

        <div class="divider my-1"></div>

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
          <span class="text-base-content/60">到期</span>
          <span>
            {{ fmtExpiry(data.expiryEpochMillis) }}
            <span v-if="expiryDays !== null" class="ml-2 text-xs" :class="expiryDays < 7 ? 'text-error' : 'text-base-content/50'">
              ({{ expiryDays >= 0 ? `${expiryDays} 天后` : `已过期 ${-expiryDays} 天` }})
            </span>
          </span>
        </div>
        <div class="flex justify-between">
          <span class="text-base-content/60">启用</span>
          <span :class="data.enabled ? 'text-success' : 'text-error'">
            {{ data.enabled ? '✓ 启用' : '✗ 禁用' }}
          </span>
        </div>

        <div v-if="lastFetched" class="text-xs text-base-content/40 text-right pt-2">
          最近拉取: {{ fmtFetchedAt(lastFetched) }}
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
