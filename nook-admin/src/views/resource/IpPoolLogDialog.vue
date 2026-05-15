<script setup lang="ts">
import { ref, watch } from 'vue'
import { RefreshCw, Search } from 'lucide-vue-next'
import {
  NButton,
  NIcon,
  NInput,
  NModal,
  NSelect,
  NSpace,
  NSpin,
  useMessage
} from 'naive-ui'
import {
  getSocks5Log,
  type ResourceIpPool,
  type Socks5Log,
  type Socks5LogLevel
} from '@/api/resource/ip-pool'

interface Props {
  modelValue: boolean
  ip?: ResourceIpPool | null
}
const props = defineProps<Props>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
}>()

const message = useMessage()

const logLoading = ref(false)
const danteLog = ref<Socks5Log | null>(null)

const LOG_LINES_OPTIONS = [
  { label: '50 行', value: 50 },
  { label: '100 行', value: 100 },
  { label: '200 行', value: 200 },
  { label: '500 行', value: 500 },
  { label: '1000 行', value: 1000 }
]
const LOG_LEVEL_OPTIONS: { label: string; value: Socks5LogLevel }[] = [
  { label: '全部', value: 'all' },
  { label: '警告以上', value: 'warning' },
  { label: '错误以上', value: 'err' }
]
const logLines = ref(100)
const logLevel = ref<Socks5LogLevel>('all')
const logKeyword = ref('')

let keywordTimer: ReturnType<typeof setTimeout> | null = null
function onKeywordChange(v: string) {
  logKeyword.value = v
  if (keywordTimer) clearTimeout(keywordTimer)
  keywordTimer = setTimeout(() => {
    keywordTimer = null
    runLog()
  }, 400)
}

function onKeywordEnter() {
  if (keywordTimer) {
    clearTimeout(keywordTimer)
    keywordTimer = null
  }
  runLog()
}

watch(
  () => [props.modelValue, props.ip?.id],
  ([open]) => {
    if (open) {
      danteLog.value = null
      runLog()
    }
  }
)

async function runLog() {
  if (!props.ip || logLoading.value) return
  logLoading.value = true
  try {
    danteLog.value = await getSocks5Log(props.ip.id, {
      lines: logLines.value,
      level: logLevel.value,
      keyword: logKeyword.value || undefined
    })
  } catch (e) {
    danteLog.value = null
    message.error('拉日志失败: ' + ((e as Error).message ?? ''))
  } finally {
    logLoading.value = false
  }
}

function close() {
  emit('update:modelValue', false)
}
</script>

<template>
  <NModal
    :show="modelValue"
    preset="card"
    style="max-width: 64rem"
    :bordered="false"
    :mask-closable="true"
    @update:show="(v: boolean) => emit('update:modelValue', v)"
  >
    <template #header>
      <span>SOCKS5 日志</span>
    </template>
    <template #header-extra>
      <span v-if="ip" class="text-xs text-zinc-500">
        {{ ip.ipAddress }}<span v-if="ip.socks5Port" class="text-zinc-400">:{{ ip.socks5Port }}</span>
      </span>
    </template>

    <div class="flex gap-2 flex-wrap items-center justify-end mb-3">
      <NInput
        :value="logKeyword"
        size="small"
        class="w-60"
        placeholder="搜关键词 (子串/不区分大小写)"
        clearable
        :disabled="logLoading"
        @update:value="onKeywordChange"
        @keyup.enter="onKeywordEnter"
      >
        <template #prefix><NIcon><Search /></NIcon></template>
      </NInput>
      <NSelect
        v-model:value="logLines"
        :options="LOG_LINES_OPTIONS"
        size="small"
        class="w-24"
        :disabled="logLoading"
        @update:value="runLog"
      />
      <NSelect
        v-model:value="logLevel"
        :options="LOG_LEVEL_OPTIONS"
        size="small"
        class="w-28"
        :disabled="logLoading"
        @update:value="runLog"
      />
      <NButton quaternary size="small" :disabled="logLoading" @click="runLog">
        <template #icon><NIcon><RefreshCw /></NIcon></template>
        刷新日志
      </NButton>
    </div>

    <NSpin :show="logLoading && !danteLog">
      <pre
        class="text-xs max-h-[32rem] overflow-auto bg-zinc-900 text-zinc-100 px-4 py-3 rounded font-mono whitespace-pre-wrap break-all leading-relaxed min-h-32"
      ><code v-if="danteLog?.log">{{ danteLog.log }}</code><span v-else class="text-zinc-500">{{ logLoading ? '拉取中...' : (logKeyword ? `无 "${logKeyword}" 命中` : '点"刷新日志"拉取') }}</span></pre>
    </NSpin>

    <template #footer>
      <NSpace justify="end">
        <NButton size="small" @click="close">关闭</NButton>
      </NSpace>
    </template>
  </NModal>
</template>
