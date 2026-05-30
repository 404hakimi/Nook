<script setup lang="ts">
import { ref, watch } from 'vue'
import { NEmpty, NIcon, NModal, NSpin, NTimeline, NTimelineItem } from 'naive-ui'
import { ArrowRight } from 'lucide-vue-next'
import { formatDateTime } from '@/utils/date'
import {
  CHANGE_REASON_LABELS,
  CHANGE_REASON_TAG_TYPE,
  CHANGE_TYPE_LABELS,
  getSubscriptionChangeLog,
  type SubscriptionChangeLog
} from '@/api/trade/subscriptionChangeLog'

const props = defineProps<{
  modelValue: boolean
  subscriptionId?: string
  memberEmail?: string
}>()
const emit = defineEmits<{ 'update:modelValue': [boolean] }>()

const logs = ref<SubscriptionChangeLog[]>([])
const loading = ref(false)

async function load() {
  if (!props.subscriptionId) return
  loading.value = true
  try {
    logs.value = await getSubscriptionChangeLog(props.subscriptionId)
  } catch {
    logs.value = []
  } finally {
    loading.value = false
  }
}

watch(() => props.modelValue, (open) => {
  if (open) {
    logs.value = []
    load()
  }
})

/** NTimelineItem 的 type 由原因决定颜色; tag 的 default 兜底到 timeline 不认的值. */
function lineType(reason: string): 'success' | 'warning' | 'error' | 'info' | 'default' {
  return CHANGE_REASON_TAG_TYPE[reason] ?? 'default'
}
</script>

<template>
  <NModal
    :show="modelValue"
    preset="card"
    title="换机记录"
    style="width: 520px; max-width: 92vw"
    @update:show="(v: boolean) => emit('update:modelValue', v)"
  >
    <div v-if="memberEmail" class="text-xs text-zinc-400 -mt-2 mb-3">{{ memberEmail }}</div>
    <NSpin :show="loading">
      <NEmpty v-if="!loading && logs.length === 0" description="暂无换机记录" class="py-6" />
      <NTimeline v-else>
        <NTimelineItem
          v-for="l in logs"
          :key="l.id"
          :type="lineType(l.reason)"
          :title="`${CHANGE_TYPE_LABELS[l.changeType] ?? l.changeType} · ${CHANGE_REASON_LABELS[l.reason] ?? l.reason}`"
          :time="formatDateTime(l.createdAt)"
        >
          <div class="flex items-center gap-2 text-xs mt-0.5">
            <span class="font-mono" :class="l.oldServerIp ? '' : 'text-zinc-300 dark:text-zinc-600'">{{ l.oldServerIp ?? '—' }}</span>
            <NIcon :size="13" class="text-zinc-400"><ArrowRight /></NIcon>
            <span class="font-mono" :class="l.newServerIp ? '' : 'text-zinc-300 dark:text-zinc-600'">{{ l.newServerIp ?? '—' }}</span>
            <span class="text-zinc-400 ml-1">· {{ l.operator === 'system' ? '系统' : (l.operator ?? '').slice(0, 8) }}</span>
          </div>
        </NTimelineItem>
      </NTimeline>
    </NSpin>
  </NModal>
</template>
