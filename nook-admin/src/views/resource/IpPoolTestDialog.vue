<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { Zap } from 'lucide-vue-next'
import {
  NButton,
  NFormItem,
  NIcon,
  NInput,
  NInputNumber,
  NModal,
  NTag,
  useMessage
} from 'naive-ui'
import {
  SOCKS5_TEST_DEFAULTS,
  testIpPoolSocks5,
  type ResourceIpPool,
  type Socks5TestParams,
  type Socks5TestResult
} from '@/api/resource/ip-pool'

const props = defineProps<{
  modelValue: boolean
  ip: ResourceIpPool | null
}>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
}>()

const message = useMessage()

// 表单参数: 拷贝默认值进 reactive, 用户可改; 后端做 @NotNull + @Min/@Max 范围校验
const form = reactive<Socks5TestParams>({ ...SOCKS5_TEST_DEFAULTS })

const loading = ref(false)
const result = ref<Socks5TestResult | null>(null)

// 弹框打开或切换 IP → 重置上一轮结果; 表单值故意不重置, 让用户在多 IP 间复用同一组参数
watch(
  () => [props.modelValue, props.ip?.id] as const,
  ([open]) => {
    if (open) result.value = null
  }
)

function close() {
  emit('update:modelValue', false)
}

/** 客户端预校验, 避免无意义打到后端再被 @Valid 拒. */
function validate(): string | null {
  if (!form.echoUrl?.trim()) return '请求地址不能为空'
  if (!/^https?:\/\/.+/.test(form.echoUrl.trim())) return '请求地址必须是 http/https URL'
  if (form.connectTimeoutMs < 500 || form.connectTimeoutMs > 60_000) {
    return '建连超时需在 500-60000 ms'
  }
  if (form.readTimeoutMs < 500 || form.readTimeoutMs > 60_000) {
    return '读响应超时需在 500-60000 ms'
  }
  return null
}

async function runTest() {
  if (!props.ip) return
  const err = validate()
  if (err) {
    message.error(err)
    return
  }
  loading.value = true
  result.value = null
  try {
    result.value = await testIpPoolSocks5(props.ip.id, {
      echoUrl: form.echoUrl.trim(),
      connectTimeoutMs: form.connectTimeoutMs,
      readTimeoutMs: form.readTimeoutMs
    })
  } catch {
    // request 拦截器已 toast; 弹框保留空结果让用户看到"没结果"
  } finally {
    loading.value = false
  }
}

/** HTTP status 颜色提示: 2xx success, 3xx info, 4xx warning, 5xx error. */
function httpStatusTagType(s: number): 'success' | 'info' | 'warning' | 'error' | 'default' {
  if (s >= 200 && s < 300) return 'success'
  if (s >= 300 && s < 400) return 'info'
  if (s >= 400 && s < 500) return 'warning'
  if (s >= 500) return 'error'
  return 'default'
}

// 状态条文案
const statusText = computed(() => {
  if (!result.value) return ''
  if (!result.value.success) return '✘ 拨号失败'
  return '✔ 拨号完成'
})

const statusColor = computed(() => {
  if (!result.value) return ''
  return result.value.success ? 'text-emerald-500' : 'text-red-500'
})

async function copyConsole() {
  if (!result.value) return
  const lines: string[] = []
  lines.push(`GET ${result.value.echoUrl}`)
  lines.push(`via SOCKS5 ${props.ip?.ipAddress}:${props.ip?.socks5Port}`)
  lines.push(`connectTimeout=${result.value.connectTimeoutMs}ms readTimeout=${result.value.readTimeoutMs}ms`)
  lines.push('')
  if (result.value.success) {
    lines.push(`HTTP/1.x ${result.value.httpStatus}`)
    lines.push(`elapsed: ${result.value.elapsedMs} ms`)
    lines.push('')
    lines.push(result.value.rawResponse || '(empty body)')
  } else {
    lines.push(`ERROR: ${result.value.error}`)
    lines.push(`elapsed: ${result.value.elapsedMs} ms`)
  }
  try {
    await navigator.clipboard.writeText(lines.join('\n'))
    message.success('控制台输出已复制')
  } catch {
    message.error('复制失败')
  }
}
</script>

<template>
  <NModal
    :show="modelValue"
    preset="card"
    title="SOCKS5 拨号测试"
    style="width: 760px"
    :mask-closable="!loading"
    @update:show="close"
  >
    <div v-if="ip" class="space-y-4">
      <!-- 目标信息: 纯展示, 不参与判断 -->
      <div class="flex items-center gap-3 px-1 text-sm">
        <span class="text-zinc-500">目标</span>
        <span class="font-mono">{{ ip.ipAddress }}<span class="text-zinc-400">:{{ ip.socks5Port || '?' }}</span></span>
        <span v-if="ip.region" class="text-xs text-zinc-400">{{ ip.region }}</span>
      </div>

      <!-- 探测参数 -->
      <div class="grid grid-cols-2 gap-3">
        <NFormItem label="请求地址" label-placement="top" :show-feedback="false" class="col-span-2">
          <NInput
            v-model:value="form.echoUrl"
            placeholder="https://api.ipify.org/"
            :disabled="loading"
            clearable
            @keyup.enter="runTest"
          />
        </NFormItem>
        <NFormItem label="建连超时 (ms)" label-placement="top" :show-feedback="false">
          <NInputNumber
            v-model:value="form.connectTimeoutMs"
            :min="500"
            :max="60000"
            :step="500"
            :disabled="loading"
            class="w-full"
          />
        </NFormItem>
        <NFormItem label="读响应超时 (ms)" label-placement="top" :show-feedback="false">
          <NInputNumber
            v-model:value="form.readTimeoutMs"
            :min="500"
            :max="60000"
            :step="500"
            :disabled="loading"
            class="w-full"
          />
        </NFormItem>
      </div>

      <div class="flex justify-end">
        <NButton type="primary" :loading="loading" :disabled="loading" @click="runTest">
          <template #icon><NIcon><Zap /></NIcon></template>
          开始测试
        </NButton>
      </div>

      <!-- 控制台输出: 一块整, status / 耗时 / 响应体一次性都给 -->
      <template v-if="result">
        <div class="flex items-center gap-3 text-sm">
          <span :class="['font-medium', statusColor]">{{ statusText }}</span>
          <NTag
            v-if="result.success"
            size="small"
            :type="httpStatusTagType(result.httpStatus)"
          >
            HTTP {{ result.httpStatus }}
          </NTag>
          <span class="text-xs text-zinc-500">{{ result.elapsedMs }} ms</span>
          <div class="flex-1"></div>
          <NButton size="tiny" quaternary @click="copyConsole">复制全部</NButton>
        </div>
        <pre
          class="bg-zinc-900 text-zinc-100 rounded p-3 text-xs font-mono overflow-auto max-h-96 m-0 whitespace-pre-wrap break-all"
        ><span class="text-zinc-400"># GET {{ result.echoUrl }}</span>
<span class="text-zinc-400"># via SOCKS5 {{ ip.ipAddress }}:{{ ip.socks5Port }}</span>
<span class="text-zinc-400"># connectTimeout={{ result.connectTimeoutMs }}ms readTimeout={{ result.readTimeoutMs }}ms</span>

<template v-if="result.success">HTTP/1.x {{ result.httpStatus }}

{{ result.rawResponse || '(empty body)' }}</template><template v-else><span class="text-red-400">ERROR: {{ result.error }}</span></template>
        </pre>
      </template>
    </div>

    <template #footer>
      <div class="flex justify-end">
        <NButton @click="close">关闭</NButton>
      </div>
    </template>
  </NModal>
</template>
