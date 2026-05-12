<script setup lang="ts">
import { computed, h, ref, watch } from 'vue'
import { Copy } from 'lucide-vue-next'
import {
  NButton,
  NDescriptions,
  NDescriptionsItem,
  NIcon,
  NModal,
  NProgress,
  NSpin,
  NTag,
  NTooltip,
  useMessage
} from 'naive-ui'
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
function prettyJson(raw?: string): string | null {
  if (!raw) return null
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

async function copyId(text: string | undefined | null, label: string) {
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    message.success(`${label} 已复制`)
  } catch {
    message.error('复制失败')
  }
}

/** 主体内容 + 一个小的复制按钮; 完整 id hover 后在 tooltip 里看. */
function renderIdSuffix(id?: string | null, copyLabel = 'ID') {
  if (!id) return null
  return h(
    NTooltip,
    { placement: 'top', trigger: 'hover' },
    {
      trigger: () =>
        h(
          NButton,
          {
            size: 'tiny',
            text: true,
            class: 'ml-1.5 align-middle text-zinc-400 hover:text-zinc-600',
            onClick: () => copyId(id, copyLabel)
          },
          { icon: () => h(NIcon, { size: 12 }, { default: () => h(Copy) }) }
        ),
      default: () =>
        h('span', { class: 'font-mono text-xs' }, id)
    }
  )
}

// 入参 JSON 渲染串 (null = 隐藏整个段)
const paramsPretty = computed(() => prettyJson(detail.value?.paramsJson || undefined))

// 终态 (DONE / FAILED / CANCELLED / TIMED_OUT) 不再显示进度条
const showProgress = computed(() => {
  const s = detail.value?.status
  return s === 'RUNNING' || s === 'QUEUED'
})

// label 列宽度 (px); 中文标签 4 字 + 留白
const LABEL_WIDTH = 96
</script>

<template>
  <NModal
    :show="modelValue"
    preset="card"
    title="任务详情"
    style="width: 780px"
    :mask-closable="true"
    @update:show="close"
  >
    <NSpin :show="loading">
      <div v-if="detail" class="space-y-4">
        <!-- 顶部摘要: opType + 状态 tag, 比 descriptions 第一行突出 -->
        <div class="flex items-center justify-between px-1">
          <div class="flex items-center gap-2">
            <span class="text-base font-medium">
              {{ OP_TYPE_LABELS[detail.opType] || detail.opType }}
            </span>
            <NTag size="small" :type="OP_STATUS_META[detail.status]?.tagType">
              {{ OP_STATUS_META[detail.status]?.label || detail.status }}
            </NTag>
          </div>
          <div class="flex items-center text-xs text-zinc-500">
            <span>opId</span>
            <span class="ml-2 font-mono">{{ detail.id.slice(0, 8) }}…</span>
            <component :is="renderIdSuffix(detail.id, 'opId')" />
          </div>
        </div>

        <!-- 运行中才显示进度条; QUEUED 显示 0% 提示在排队 -->
        <div v-if="showProgress" class="px-1">
          <div class="flex items-center justify-between text-xs mb-1">
            <span class="text-zinc-600 dark:text-zinc-300">
              {{ detail.currentStep || (detail.status === 'QUEUED' ? '等待中' : '执行中') }}
            </span>
            <span class="text-zinc-400">{{ detail.progressPct ?? 0 }}%</span>
          </div>
          <NProgress
            :percentage="detail.progressPct ?? 0"
            :height="6"
            :show-indicator="false"
            :status="detail.status === 'QUEUED' ? 'default' : 'info'"
          />
        </div>

        <NDescriptions
          label-placement="left"
          bordered
          size="small"
          :column="2"
          :label-style="{ width: `${LABEL_WIDTH}px`, whiteSpace: 'nowrap' }"
        >
          <NDescriptionsItem label="触发者">
            <span>{{ detail.operatorName || detail.operator || '-' }}</span>
            <!-- enricher 已把 SYSTEM/SCHEDULER 这种占位符的 operatorName 设为相同值, 这里不重复挂复制按钮 -->
            <component
              :is="renderIdSuffix(detail.operator, '触发者 ID')"
              v-if="detail.operator && detail.operator !== detail.operatorName"
            />
          </NDescriptionsItem>
          <NDescriptionsItem label="操作服务器">
            <span>{{ detail.serverName || detail.serverId || '-' }}</span>
            <component :is="renderIdSuffix(detail.serverId, 'serverId')" />
          </NDescriptionsItem>
          <NDescriptionsItem label="操作目标">
            <template v-if="detail.targetId">
              <span>{{ detail.targetName || detail.targetId }}</span>
              <component :is="renderIdSuffix(detail.targetId, 'targetId')" />
            </template>
            <span v-else class="text-zinc-400">-</span>
          </NDescriptionsItem>
          <NDescriptionsItem label="耗时">
            {{ elapsedText(detail.elapsedMs) }}
          </NDescriptionsItem>
          <NDescriptionsItem label="入队时间">
            {{ formatDateTime(detail.enqueuedAt) }}
          </NDescriptionsItem>
          <NDescriptionsItem label="开始时间">
            {{ formatDateTime(detail.startedAt) }}
          </NDescriptionsItem>
          <NDescriptionsItem label="结束时间" :span="2">
            {{ formatDateTime(detail.endedAt) }}
          </NDescriptionsItem>
          <NDescriptionsItem v-if="detail.lastMessage" label="最近消息" :span="2">
            <span class="text-xs">{{ detail.lastMessage }}</span>
          </NDescriptionsItem>
          <NDescriptionsItem v-if="detail.errorCode" label="错误码">
            <span class="font-mono text-xs text-red-500">{{ detail.errorCode }}</span>
          </NDescriptionsItem>
          <NDescriptionsItem v-if="detail.errorMsg" label="失败原因" :span="2">
            <pre class="whitespace-pre-wrap text-xs text-red-500 m-0">{{ detail.errorMsg }}</pre>
          </NDescriptionsItem>
        </NDescriptions>

        <!-- params_json 为空时整段隐藏, 不挂 "-" 占视觉 -->
        <div v-if="paramsPretty">
          <div class="text-xs text-zinc-500 mb-1">入参 JSON</div>
          <pre
            class="bg-zinc-50 dark:bg-zinc-800 rounded p-3 text-xs font-mono overflow-auto max-h-72 m-0"
          >{{ paramsPretty }}</pre>
        </div>
      </div>
    </NSpin>
  </NModal>
</template>
