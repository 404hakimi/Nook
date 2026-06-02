<script setup lang="ts">
import { ref, watch } from 'vue'
import { NEmpty, NIcon, NModal, NSpin, NTag, NTimeline, NTimelineItem } from 'naive-ui'
import { ArrowRight, User } from 'lucide-vue-next'
import { formatDateTime } from '@/utils/date'
import {
  CHANGE_REASON_LABELS,
  CHANGE_REASON_TAG_TYPE,
  CHANGE_TYPE_LABELS,
  CHANGE_TYPE_TAG_TYPE,
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
          :time="formatDateTime(l.createdAt)"
        >
          <div class="flex items-center gap-1.5 flex-wrap">
            <NTag size="small" :bordered="false" :type="CHANGE_TYPE_TAG_TYPE[l.changeType] ?? 'default'">
              {{ CHANGE_TYPE_LABELS[l.changeType] ?? l.changeType }}
            </NTag>
            <NTag size="small" :bordered="false" :type="lineType(l.reason)">
              {{ CHANGE_REASON_LABELS[l.reason] ?? l.reason }}
            </NTag>
          </div>
          <div class="flex items-center gap-2 text-xs mt-1.5">
            <!-- 有来源机=换机, 显示 旧→新; 无来源机(初始开通)直接显示目标机 -->
            <template v-if="l.oldServerIp">
              <span class="font-mono text-zinc-400">{{ l.oldServerIp }}</span>
              <NIcon :size="13" class="text-zinc-400"><ArrowRight /></NIcon>
              <span class="font-mono font-medium">{{ l.newServerIp ?? '—' }}</span>
            </template>
            <span v-else class="font-mono font-medium">{{ l.newServerIp ?? '—' }}</span>
            <span class="ml-auto inline-flex items-center gap-1 text-zinc-400">
              <NIcon :size="11"><User /></NIcon>
              {{ l.operator === 'system' ? '系统' : (l.operator ?? '').slice(0, 8) }}
            </span>
          </div>
        </NTimelineItem>
      </NTimeline>
    </NSpin>
  </NModal>
</template>
