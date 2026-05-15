
<script setup lang="ts">
import { nextTick, reactive, ref, watch } from 'vue'
import { Cpu, Power, Settings2 } from 'lucide-vue-next'
import {
  NButton,
  NCard,
  NIcon,
  NInputNumber,
  NModal,
  NSpace,
  NSpin,
  useMessage
} from 'naive-ui'
import { useConfirm } from '@/composables/useConfirm'
import {
  enableBbrStream,
  enableSwapStream,
  type EnableSwapDTO
} from '@/api/xray/server'
import type { ResourceServer } from '@/api/resource/server'

interface Props {
  modelValue: boolean
  server?: ResourceServer | null
}
const props = defineProps<Props>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
}>()

const message = useMessage()
const { confirm } = useConfirm()

/** 当前正在执行的 op 名 ('' = 空闲); 同一时间只允许一个 op 运行, 避免远端竞态. */
const running = ref<'' | 'bbr' | 'swap'>('')
const output = ref('')
const outputRef = ref<HTMLPreElement | null>(null)
let abortCtrl: AbortController | null = null

const swapForm = reactive<EnableSwapDTO>({
  sizeMb: 1024
})

const errors = reactive<Record<string, string>>({})

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    Object.keys(errors).forEach((k) => delete errors[k])
    output.value = ''
    swapForm.sizeMb = 1024
  }
)

async function runStream(
  op: 'bbr' | 'swap',
  title: string,
  message_: string,
  invoke: (signal: AbortSignal) => Promise<void>
) {
  if (!props.server || running.value) return
  const ok = await confirm({ title, message: message_, type: 'warning', confirmText: '执行' })
  if (!ok) return
  running.value = op
  output.value = ''
  abortCtrl = new AbortController()
  try {
    await invoke(abortCtrl.signal)
    appendOutput('\n[nook] ✔ 操作完成\n')
    message.success(`${title} 完成`)
  } catch (e) {
    if ((e as Error).name === 'AbortError') {
      appendOutput('\n[nook] 用户已取消, 远端脚本可能已经在跑(无法终止)\n')
      message.warning('已取消, 但远端可能仍在执行')
    } else {
      appendOutput(`\n[error] ${(e as Error).message || ''}\n`)
      message.error(`${title}失败, 看输出日志定位`)
    }
  } finally {
    running.value = ''
    abortCtrl = null
  }
}

function onEnableBbr() {
  if (!props.server) return
  const id = props.server.id
  runStream(
    'bbr',
    '启用 BBR',
    `在 "${props.server.name}" 启用 BBR?`,
    (signal) => enableBbrStream(id, appendOutput, signal)
  )
}

function onEnableSwap() {
  Object.keys(errors).forEach((k) => delete errors[k])
  if (swapForm.sizeMb < 256 || swapForm.sizeMb > 8192) {
    errors.sizeMb = 'swap 范围 256-8192 MB'
    return
  }
  if (!props.server) return
  const id = props.server.id
  const dto: EnableSwapDTO = { sizeMb: swapForm.sizeMb }
  runStream(
    'swap',
    `启用 swap (${dto.sizeMb} MB)`,
    `在 "${props.server.name}" 启用 ${dto.sizeMb} MB swap?`,
    (signal) => enableSwapStream(id, dto, appendOutput, signal)
  )
}

const ANSI_RE = /\x1b\[[0-9;?]*[A-Za-z]/g

function appendOutput(chunk: string) {
  output.value += chunk.replace(ANSI_RE, '')
  nextTick(() => {
    if (outputRef.value) {
      outputRef.value.scrollTop = outputRef.value.scrollHeight
    }
  })
}

function close() {
  if (running.value) {
    abortCtrl?.abort()
    message.warning('已断开输出流, 远端脚本可能仍在后台跑')
  }
  emit('update:modelValue', false)
}
</script>

<template>
  <NModal
    :show="modelValue"
    preset="card"
    style="max-width: 64rem; width: 92vw"
    :bordered="false"
    :mask-closable="false"
    :close-on-esc="false"
    @update:show="(v: boolean) => emit('update:modelValue', v)"
  >
    <template #header>
      <div class="flex items-center gap-2">
        <NIcon :size="20" :depth="2"><Settings2 /></NIcon>
        <span>OS 调优</span>
      </div>
    </template>
    <template #header-extra>
      <span v-if="server" class="text-xs text-zinc-500">
        {{ server.name }} ({{ server.host }})
      </span>
    </template>

    <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
      <!-- BBR -->
      <NCard size="small" :title="undefined">
        <div class="flex items-center gap-2 mb-3">
          <NIcon :size="16"><Cpu /></NIcon>
          <span class="font-semibold text-sm">启用 BBR 拥塞控制</span>
        </div>
        <NButton
          type="primary"
          size="small"
          :loading="running === 'bbr'"
          :disabled="!!running"
          @click="onEnableBbr"
        >
          <template #icon><NIcon><Power /></NIcon></template>
          启用 BBR
        </NButton>
      </NCard>

      <!-- Swap -->
      <NCard size="small">
        <div class="flex items-center gap-2 mb-3">
          <NIcon :size="16"><Settings2 /></NIcon>
          <span class="font-semibold text-sm">启用 swap</span>
        </div>
        <div class="flex items-end gap-2">
          <div class="flex-1">
            <div class="text-xs text-zinc-500 mb-1">swap 大小 (MB)</div>
            <NInputNumber
              v-model:value="swapForm.sizeMb"
              :min="256"
              :max="8192"
              :step="256"
              :disabled="!!running"
              :status="errors.sizeMb ? 'error' : undefined"
              size="small"
              class="w-full"
            />
            <div v-if="errors.sizeMb" class="text-xs text-red-500 mt-1">{{ errors.sizeMb }}</div>
          </div>
          <NButton
            type="primary"
            size="small"
            :loading="running === 'swap'"
            :disabled="!!running"
            @click="onEnableSwap"
          >
            <template #icon><NIcon><Power /></NIcon></template>
            启用
          </NButton>
        </div>
      </NCard>
    </div>

    <!-- 输出区 -->
    <div class="mt-4">
      <div class="flex items-center justify-between mb-2">
        <div class="text-sm font-semibold text-zinc-500">远程输出 (实时)</div>
        <div v-if="running" class="flex items-center gap-2 text-xs text-zinc-500">
          <NSpin :size="14" />
          <span>{{ running === 'bbr' ? 'BBR 启用中' : 'swap 创建中' }}...</span>
        </div>
      </div>
      <pre
        ref="outputRef"
        class="text-xs max-h-72 min-h-32 overflow-auto bg-zinc-900 text-zinc-100 px-4 py-3 rounded whitespace-pre-wrap break-all font-mono leading-relaxed"
      ><code v-if="output">{{ output }}</code><span v-else class="text-zinc-500">远端 stdout 实时输出</span></pre>
    </div>

    <template #footer>
      <NSpace justify="end">
        <NButton size="small" :disabled="!!running" @click="close">关闭</NButton>
      </NSpace>
    </template>
  </NModal>
</template>
