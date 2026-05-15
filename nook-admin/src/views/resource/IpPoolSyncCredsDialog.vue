<script setup lang="ts">
import { nextTick, reactive, ref, watch } from 'vue'
import { CheckCircle2, RefreshCcw } from 'lucide-vue-next'
import {
  NButton,
  NForm,
  NFormItem,
  NIcon,
  NInput,
  NInputNumber,
  NModal,
  NSpace,
  NSpin,
  useMessage
} from 'naive-ui'
import { useConfirm } from '@/composables/useConfirm'
import { syncSocks5CredsStream, type ResourceIpPool, type Socks5SyncCredsDTO } from '@/api/resource/ip-pool'

interface Props {
  modelValue: boolean
  ip: ResourceIpPool | null
}
const props = defineProps<Props>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'synced'): void
}>()

const message = useMessage()
const { confirm } = useConfirm()

const running = ref(false)
const finished = ref(false)
const output = ref('')
const errors = reactive<Record<string, string>>({})
const outputRef = ref<HTMLPreElement | null>(null)
let abortCtrl: AbortController | null = null

const form = reactive({
  // SSH 凭据 (一次性, 不入库)
  sshUser: 'root',
  sshPort: 22,
  sshPassword: '',
  sshTimeoutSeconds: 60,
  sshOpTimeoutSeconds: 60,
  sshUploadTimeoutSeconds: 60,
  installTimeoutSeconds: 120
})

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    Object.keys(errors).forEach((k) => delete errors[k])
    output.value = ''
    finished.value = false
    Object.assign(form, {
      sshUser: 'root',
      sshPort: 22,
      sshPassword: '',
      sshTimeoutSeconds: 60,
      sshOpTimeoutSeconds: 60,
      sshUploadTimeoutSeconds: 60,
      installTimeoutSeconds: 120
    })
  }
)

function validate() {
  Object.keys(errors).forEach((k) => delete errors[k])
  if (!form.sshUser.trim()) errors.sshUser = '请输入 SSH 用户'
  if (form.sshPort < 1 || form.sshPort > 65535) errors.sshPort = '端口范围 1-65535'
  if (!form.sshPassword) errors.sshPassword = '请填 SSH 密码'
  return Object.keys(errors).length === 0
}

async function onSubmit() {
  if (!validate() || !props.ip) return
  const ok = await confirm({
    title: '同步 SOCKS5 凭据',
    message: `把 DB 当前的 SOCKS5 配置推到 ${props.ip.ipAddress}? 客户端 outbound 也会重建。`,
    type: 'warning',
    confirmText: '开始同步'
  })
  if (!ok) return

  running.value = true
  finished.value = false
  output.value = ''
  abortCtrl = new AbortController()
  try {
    const dto: Socks5SyncCredsDTO = {
      sshUser: form.sshUser.trim(),
      sshPort: form.sshPort,
      sshPassword: form.sshPassword,
      sshTimeoutSeconds: form.sshTimeoutSeconds,
      sshOpTimeoutSeconds: form.sshOpTimeoutSeconds,
      sshUploadTimeoutSeconds: form.sshUploadTimeoutSeconds,
      installTimeoutSeconds: form.installTimeoutSeconds
    }
    await syncSocks5CredsStream(props.ip.id, dto, appendOutput, abortCtrl.signal)
    finished.value = true
    message.success('同步完成')
    emit('synced')
  } catch (e) {
    if ((e as Error).name === 'AbortError') {
      appendOutput('\n[nook] 用户已取消, 远端可能仍在执行\n')
      message.warning('已取消, 但远端可能仍在执行')
    } else {
      appendOutput(`\n[error] ${(e as Error).message || ''}\n`)
      message.error('同步失败, 看输出日志定位')
    }
  } finally {
    running.value = false
    abortCtrl = null
  }
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
    style="max-width: 56rem; width: 92vw"
    :bordered="false"
    :mask-closable="false"
    :close-on-esc="false"
    @update:show="(v: boolean) => emit('update:modelValue', v)"
  >
    <template #header>
      <div class="flex items-center gap-2">
        <NIcon :size="20" :depth="2"><RefreshCcw /></NIcon>
        <span>同步 SOCKS5 凭据</span>
      </div>
    </template>
    <template #header-extra>
      <span v-if="ip" class="text-xs text-zinc-500 font-mono">
        {{ ip.ipAddress }}<span class="text-zinc-400">:{{ ip.socks5Port }}</span>
        / {{ ip.socks5Username }}
      </span>
    </template>

    <NForm
      :model="form"
      label-placement="top"
      require-mark-placement="right-hanging"
      size="small"
    >
      <div class="text-sm font-semibold mb-2">SSH 凭据 (一次性, 不入库)</div>
      <div class="grid grid-cols-1 sm:grid-cols-3 gap-x-4">
        <NFormItem
          label="SSH 用户"
          :validation-status="errors.sshUser ? 'error' : undefined"
          :feedback="errors.sshUser"
        >
          <NInput v-model:value="form.sshUser" :disabled="running" />
        </NFormItem>

        <NFormItem
          label="SSH 端口"
          :validation-status="errors.sshPort ? 'error' : undefined"
          :feedback="errors.sshPort"
        >
          <NInputNumber
            v-model:value="form.sshPort"
            :min="1"
            :max="65535"
            :disabled="running"
            style="width: 100%"
          />
        </NFormItem>

        <NFormItem label="超时 (秒)">
          <NInputNumber
            v-model:value="form.installTimeoutSeconds"
            :min="30"
            :max="600"
            :disabled="running"
            style="width: 100%"
          />
        </NFormItem>

        <div class="sm:col-span-3">
          <NFormItem
            label="SSH 密码"
            required
            :validation-status="errors.sshPassword ? 'error' : undefined"
            :feedback="errors.sshPassword"
          >
            <NInput
              v-model:value="form.sshPassword"
              type="password"
              show-password-on="click"
              :disabled="running"
              :input-props="{ autocomplete: 'new-password' }"
              placeholder="必填"
            />
          </NFormItem>
        </div>
      </div>
    </NForm>

    <div class="mt-3">
      <div class="flex items-center justify-between mb-2">
        <div class="text-sm font-semibold">远程输出 (实时)</div>
        <div v-if="running" class="flex items-center gap-2 text-xs text-zinc-500">
          <NSpin :size="14" />
          <span>同步中...</span>
        </div>
        <div
          v-else-if="finished"
          class="flex items-center gap-1 text-xs"
          style="color: var(--n-success-color, #18a058)"
        >
          <NIcon :size="16"><CheckCircle2 /></NIcon>
          <span>已同步</span>
        </div>
      </div>
      <pre
        ref="outputRef"
        class="text-xs max-h-72 min-h-28 overflow-auto bg-zinc-900 text-zinc-100 px-4 py-3 rounded whitespace-pre-wrap break-all font-mono leading-relaxed"
      ><code v-if="output">{{ output }}</code><span v-else class="text-zinc-500">{{ running ? '准备中...' : '远端 stdout 实时输出' }}</span></pre>
    </div>

    <template #footer>
      <NSpace justify="end">
        <NButton size="small" :disabled="running" @click="close">关闭</NButton>
        <NButton
          type="primary"
          size="small"
          :loading="running"
          :disabled="running || finished"
          @click="onSubmit"
        >
          <template #icon><NIcon><RefreshCcw /></NIcon></template>
          {{ running ? '同步中...' : (finished ? '已完成' : '开始同步') }}
        </NButton>
      </NSpace>
    </template>
  </NModal>
</template>
