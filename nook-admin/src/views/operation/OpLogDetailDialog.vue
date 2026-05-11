<script setup lang="ts">
import { ref, watch } from 'vue'
import { NDescriptions, NDescriptionsItem, NModal, NSpin, NTag, useMessage } from 'naive-ui'
import {
  OP_STATUS_META,
  OP_TYPE_LABELS,
  getOpLogDetail,
  type OpLog
} from '@/api/operation/op-log'
import { formatDateTime } from '@/utils/date'

const props = defineProps<{
  modelValue: boolean
  opId: string | null
}>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void }>()

const message = useMessage()
const loading = ref(false)
const detail = ref<OpLog | null>(null)

watch(
  () => [props.modelValue, props.opId] as const,
  async ([open, id]) => {
    if (!open || !id) return
    loading.value = true
    detail.value = null
    try {
      detail.value = await getOpLogDetail(id)
    } catch {
      message.error('详情拉取失败')
    } finally {
      loading.value = false
    }
  },
  { immediate: false }
)

function close() {
  emit('update:modelValue', false)
}

/** params_json 美化输出; 失败 fallback 原文 */
function prettyJson(raw?: string): string {
  if (!raw) return '-'
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}

function elapsedText(ms?: number): string {
  if (ms == null) return '-'
  const s = ms / 1000
  if (s < 60) return `${s.toFixed(2)}s`
  return `${(s / 60).toFixed(2)}m`
}
</script>

<template>
  <NModal
    :show="modelValue"
    preset="card"
    title="任务详情"
    style="width: 760px"
    :mask-closable="true"
    @update:show="close"
  >
    <NSpin :show="loading">
      <div v-if="detail" class="space-y-3">
        <NDescriptions label-placement="left" bordered size="small" :column="2">
          <NDescriptionsItem label="opId">
            <span class="font-mono text-xs">{{ detail.id }}</span>
          </NDescriptionsItem>
          <NDescriptionsItem label="状态">
            <NTag size="small" :type="OP_STATUS_META[detail.status]?.tagType">
              {{ OP_STATUS_META[detail.status]?.label || detail.status }}
            </NTag>
          </NDescriptionsItem>
          <NDescriptionsItem label="操作类型">
            {{ OP_TYPE_LABELS[detail.opType] || detail.opType }}
          </NDescriptionsItem>
          <NDescriptionsItem label="触发者">
            {{ detail.operator || '-' }}
          </NDescriptionsItem>
          <NDescriptionsItem label="server">
            <span class="font-mono text-xs">{{ detail.serverId }}</span>
          </NDescriptionsItem>
          <NDescriptionsItem label="target">
            <span class="font-mono text-xs">{{ detail.targetId || '-' }}</span>
          </NDescriptionsItem>
          <NDescriptionsItem label="入队时间">
            {{ formatDateTime(detail.enqueuedAt) }}
          </NDescriptionsItem>
          <NDescriptionsItem label="开始时间">
            {{ formatDateTime(detail.startedAt) }}
          </NDescriptionsItem>
          <NDescriptionsItem label="结束时间">
            {{ formatDateTime(detail.endedAt) }}
          </NDescriptionsItem>
          <NDescriptionsItem label="耗时">
            {{ elapsedText(detail.elapsedMs) }}
          </NDescriptionsItem>
          <NDescriptionsItem v-if="detail.status === 'RUNNING'" label="当前步骤" :span="2">
            {{ detail.currentStep || '-' }}
            <span v-if="detail.progressPct != null" class="ml-2 text-zinc-400 text-xs">
              ({{ detail.progressPct }}%)
            </span>
          </NDescriptionsItem>
          <NDescriptionsItem v-if="detail.lastMessage" label="最近消息" :span="2">
            <span class="text-xs">{{ detail.lastMessage }}</span>
          </NDescriptionsItem>
          <NDescriptionsItem v-if="detail.errorCode" label="错误码">
            <span class="font-mono text-xs text-red-500">{{ detail.errorCode }}</span>
          </NDescriptionsItem>
          <NDescriptionsItem v-if="detail.errorMsg" label="失败原因" :span="2">
            <pre class="whitespace-pre-wrap text-xs text-red-500">{{ detail.errorMsg }}</pre>
          </NDescriptionsItem>
        </NDescriptions>

        <div>
          <div class="text-xs text-zinc-500 mb-1">入参 JSON</div>
          <pre
            class="bg-zinc-50 dark:bg-zinc-800 rounded p-3 text-xs font-mono overflow-auto max-h-72"
          >{{ prettyJson(detail.paramsJson) }}</pre>
        </div>
      </div>
    </NSpin>
  </NModal>
</template>
