<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RefreshCw } from 'lucide-vue-next'
import {
  NAlert,
  NButton,
  NDescriptions,
  NDescriptionsItem,
  NIcon,
  NModal,
  NProgress,
  NSpace,
  NSpin
} from 'naive-ui'
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
// 用量等级 → NProgress status
const usageStatus = computed<'success' | 'warning' | 'error'>(() => {
  if (usagePct.value >= 90) return 'error'
  if (usagePct.value >= 70) return 'warning'
  return 'success'
})
const expiryDays = computed(() => {
  if (!data.value || !data.value.expiryEpochMillis) return null
  const days = Math.floor((data.value.expiryEpochMillis - Date.now()) / 86400_000)
  return days
})
</script>

<template>
  <NModal
    :show="modelValue"
    preset="card"
    style="max-width: 28rem"
    :bordered="false"
    :mask-closable="true"
    @update:show="(v: boolean) => emit('update:modelValue', v)"
  >
    <template #header>
      <span>流量与配额</span>
    </template>
    <template #header-extra>
      <NButton
        quaternary
        size="small"
        :loading="loading"
        :disabled="loading"
        title="重新拉取"
        @click="refresh"
      >
        <template #icon><NIcon><RefreshCw /></NIcon></template>
      </NButton>
    </template>

    <NSpin :show="loading && !data">
      <div class="min-h-[8rem]">
        <div v-if="data" class="space-y-3 text-sm">
          <!-- 用量进度条 -->
          <div v-if="data.totalBytes > 0">
            <div class="flex justify-between text-xs mb-1">
              <span class="text-zinc-500">
                已用 {{ fmtBytes(usedBytes) }} / {{ fmtBytes(data.totalBytes) }}
              </span>
              <span class="font-mono">{{ usagePct }}%</span>
            </div>
            <NProgress
              type="line"
              :percentage="usagePct"
              :status="usageStatus"
              :show-indicator="false"
            />
          </div>
          <NAlert v-else type="info" :show-icon="false" :bordered="false">
            <span class="text-xs">无流量上限：累计已用 {{ fmtBytes(usedBytes) }}</span>
          </NAlert>

          <NDescriptions :column="1" size="small" label-placement="left" bordered>
            <NDescriptionsItem label="Email">
              <span class="font-mono text-xs">{{ data.clientEmail }}</span>
            </NDescriptionsItem>
            <NDescriptionsItem label="上行">
              <span class="font-mono">{{ fmtBytes(data.upBytes) }}</span>
            </NDescriptionsItem>
            <NDescriptionsItem label="下行">
              <span class="font-mono">{{ fmtBytes(data.downBytes) }}</span>
            </NDescriptionsItem>
            <NDescriptionsItem label="到期">
              {{ fmtExpiry(data.expiryEpochMillis) }}
              <span
                v-if="expiryDays !== null"
                class="ml-2 text-xs"
                :style="expiryDays < 7 ? 'color: var(--n-error-color)' : 'color: var(--n-text-color-3)'"
              >
                ({{ expiryDays >= 0 ? `${expiryDays} 天后` : `已过期 ${-expiryDays} 天` }})
              </span>
            </NDescriptionsItem>
            <NDescriptionsItem label="启用">
              <span :style="data.enabled ? 'color: var(--n-success-color)' : 'color: var(--n-error-color)'">
                {{ data.enabled ? '✓ 启用' : '✗ 禁用' }}
              </span>
            </NDescriptionsItem>
          </NDescriptions>

          <div v-if="lastFetched" class="text-xs text-zinc-400 text-right pt-2">
            最近拉取: {{ fmtFetchedAt(lastFetched) }}
          </div>
        </div>
        <div v-else-if="!loading" class="py-8 text-center text-zinc-400">
          无法加载流量数据
        </div>
      </div>
    </NSpin>

    <template #footer>
      <NSpace justify="end">
        <NButton size="small" @click="close">关闭</NButton>
      </NSpace>
    </template>
  </NModal>
</template>
