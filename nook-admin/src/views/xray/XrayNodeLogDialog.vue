<script setup lang="ts">
import { ref, watch } from 'vue'
import { RefreshCw, Search } from 'lucide-vue-next'
import {
  NButton,
  NIcon,
  NInput,
  NModal,
  NRadioButton,
  NRadioGroup,
  NSelect,
  NSpace,
  NSpin,
  useMessage
} from 'naive-ui'
import {
  getXrayLog,
  getXrayLogFile,
  type XrayLog,
  type XrayLogFileVariant,
  type XrayLogLevel
} from '@/api/xray/server'
import type { XrayNode } from '@/api/xray/node'

/** 日志源切换: file (xray 自己的 access/error.log, 默认) vs journal (systemctl 启停 / 启动报错). */
type LogSource = 'file' | 'journal'

interface Props {
  modelValue: boolean
  node?: XrayNode | null
}
const props = defineProps<Props>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
}>()

const message = useMessage()

const logLoading = ref(false)
const xrayLog = ref<XrayLog | null>(null)

const LOG_LINES_OPTIONS = [
  { label: '50 行', value: 50 },
  { label: '100 行', value: 100 },
  { label: '200 行', value: 200 },
  { label: '500 行', value: 500 },
  { label: '1000 行', value: 1000 }
]
const LOG_LEVEL_OPTIONS: { label: string; value: XrayLogLevel }[] = [
  { label: '全部', value: 'all' },
  { label: '警告以上', value: 'warning' },
  { label: '错误以上', value: 'err' }
]
const logLines = ref(100)
const logLevel = ref<XrayLogLevel>('all')
/** 日志源默认 file: 看真正的连接 / 错误; 切到 journal 排障启停问题. */
const logSource = ref<LogSource>('file')
/** file 模式下选 access (每个连接) 或 error (内部错误). */
const logVariant = ref<XrayLogFileVariant>('access')

const LOG_VARIANT_OPTIONS: { label: string; value: XrayLogFileVariant }[] = [
  { label: 'access.log', value: 'access' },
  { label: 'error.log', value: 'error' }
]
/**
 * 关键词输入: 服务端 grep -F -i 子串过滤, 大小写不敏感.
 * 允许字符: Unicode 字母/数字 + 空格 + ._-:/@ (后端 LOG_KEYWORD_PATTERN 同步校验).
 * 不要做正则; 想搜 IP / email / 类名 / 文件路径都够用.
 */
const logKeyword = ref('')

/** debounce 计时器: 用户停打 400ms 后才发请求, 避免每按一键都打 SSH */
let keywordTimer: ReturnType<typeof setTimeout> | null = null
function onKeywordChange(v: string) {
  logKeyword.value = v
  if (keywordTimer) clearTimeout(keywordTimer)
  keywordTimer = setTimeout(() => {
    keywordTimer = null
    runLog()
  }, 400)
}

/** 回车立即触发 (跳过 debounce); 输入框失焦也会被 naive 的 update:value 触发 onKeywordChange */
function onKeywordEnter() {
  if (keywordTimer) {
    clearTimeout(keywordTimer)
    keywordTimer = null
  }
  runLog()
}

watch(
  () => [props.modelValue, props.node?.serverId],
  ([open]) => {
    if (open) {
      xrayLog.value = null
      runLog()
    }
  }
)

async function runLog() {
  if (!props.node || logLoading.value) return
  logLoading.value = true
  try {
    if (logSource.value === 'file') {
      // file 源: 走 xray 自己的 access.log / error.log; 文件本身不分 level
      xrayLog.value = await getXrayLogFile(props.node.serverId, {
        variant: logVariant.value,
        lines: logLines.value,
        keyword: logKeyword.value || undefined
      })
    } else {
      xrayLog.value = await getXrayLog(props.node.serverId, {
        lines: logLines.value,
        level: logLevel.value,
        keyword: logKeyword.value || undefined
      })
    }
  } catch (e) {
    xrayLog.value = null
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
      <span>Xray 日志</span>
    </template>
    <template #header-extra>
      <span v-if="node" class="text-xs text-zinc-500">
        {{ node.serverName || node.serverId }} <span v-if="node.serverHost">({{ node.serverHost }})</span>
      </span>
    </template>

    <div class="flex gap-2 flex-wrap items-center justify-between mb-3">
      <div class="flex gap-2 items-center">
        <NRadioGroup
          v-model:value="logSource"
          size="small"
          :disabled="logLoading"
          @update:value="runLog"
        >
          <NRadioButton value="file" title="xray 自己的日志文件 (access/error.log); 业务连接 + 内部错误">文件日志</NRadioButton>
          <NRadioButton value="journal" title="systemctl journal; 启停 + 启动失败信息">systemd 日志</NRadioButton>
        </NRadioGroup>
        <NSelect
          v-if="logSource === 'file'"
          v-model:value="logVariant"
          :options="LOG_VARIANT_OPTIONS"
          size="small"
          class="w-28"
          :disabled="logLoading"
          @update:value="runLog"
        />
      </div>

      <div class="flex gap-2 items-center">
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
        v-if="logSource === 'journal'"
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
    </div>

    <NSpin :show="logLoading && !xrayLog">
      <pre
        class="text-xs max-h-[32rem] overflow-auto bg-zinc-900 text-zinc-100 px-4 py-3 rounded font-mono whitespace-pre-wrap break-all leading-relaxed min-h-32"
      ><code v-if="xrayLog?.log">{{ xrayLog.log }}</code><span v-else class="text-zinc-500">{{ logLoading ? '拉取中...' : (logKeyword ? `无 "${logKeyword}" 命中` : '点"刷新日志"拉取') }}</span></pre>
    </NSpin>

    <template #footer>
      <NSpace justify="end">
        <NButton size="small" @click="close">关闭</NButton>
      </NSpace>
    </template>
  </NModal>
</template>
