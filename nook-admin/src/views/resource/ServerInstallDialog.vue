<script setup lang="ts">
import { nextTick, reactive, ref, watch } from 'vue'
import { Rocket } from 'lucide-vue-next'
import {
  NButton,
  NCheckbox,
  NForm,
  NFormItem,
  NIcon,
  NInputNumber,
  NInput,
  NModal,
  NSelect,
  NSpace,
  NSpin,
  useMessage
} from 'naive-ui'
import { useConfirm } from '@/composables/useConfirm'
import { xrayInstallStream, type LineServerInstallDTO } from '@/api/xray/server'
import type { ResourceServer } from '@/api/resource/server'

interface Props {
  modelValue: boolean
  server?: ResourceServer | null
}
const props = defineProps<Props>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'installed'): void
}>()

const message = useMessage()
const { confirm } = useConfirm()

const installing = ref(false)
const output = ref('')
const errors = reactive<Record<string, string>>({})
const outputRef = ref<HTMLPreElement | null>(null)
let abortCtrl: AbortController | null = null

const TIMEZONE_OPTIONS = [
  { label: '不修改', value: '' },
  { label: 'Asia/Shanghai (北京)', value: 'Asia/Shanghai' },
  { label: 'Asia/Hong_Kong', value: 'Asia/Hong_Kong' },
  { label: 'Asia/Tokyo', value: 'Asia/Tokyo' },
  { label: 'Asia/Singapore', value: 'Asia/Singapore' },
  { label: 'UTC', value: 'UTC' },
  { label: 'America/Los_Angeles', value: 'America/Los_Angeles' },
  { label: 'America/New_York', value: 'America/New_York' },
  { label: 'Europe/London', value: 'Europe/London' },
  { label: 'Europe/Frankfurt', value: 'Europe/Frankfurt' }
]

const form = reactive<Required<LineServerInstallDTO>>({
  vmessPort: 443,
  xrayApiPort: 62789,
  logDir: '/var/log/xray',
  installUfw: true,
  enableBbr: true,
  timezone: 'Asia/Shanghai'
})

watch(
  () => [props.modelValue, props.server?.id],
  ([open]) => {
    if (!open) return
    Object.keys(errors).forEach((k) => delete errors[k])
    output.value = ''
    // 默认值参考服务器现有的 xrayGrpcPort(如有)
    if (props.server?.xrayGrpcPort) form.xrayApiPort = props.server.xrayGrpcPort
  }
)

function validate() {
  Object.keys(errors).forEach((k) => delete errors[k])
  if (form.vmessPort < 1 || form.vmessPort > 65535) errors.vmessPort = '端口范围 1-65535'
  if (form.xrayApiPort < 1 || form.xrayApiPort > 65535) errors.xrayApiPort = '端口范围 1-65535'
  if (form.vmessPort === form.xrayApiPort) errors.xrayApiPort = '不能与 vmess 端口相同'
  return Object.keys(errors).length === 0
}

async function onSubmit() {
  if (!validate() || !props.server) return
  const ok = await confirm({
    title: '一键安装/重装 Xray',
    message: `将在 ${props.server.name} 上安装 Xray + 标配配置（约 1-5 分钟）。\n\n如已存在 Xray 配置会先备份再覆盖。\n\n下方日志会实时显示远端输出。`,
    type: 'warning',
    confirmText: '开始安装'
  })
  if (!ok) return
  installing.value = true
  output.value = ''
  abortCtrl = new AbortController()
  try {
    const dto: LineServerInstallDTO = {
      vmessPort: form.vmessPort,
      xrayApiPort: form.xrayApiPort,
      logDir: form.logDir,
      installUfw: form.installUfw,
      enableBbr: form.enableBbr,
      timezone: form.timezone || undefined
    }
    await xrayInstallStream(props.server.id, dto, appendOutput, abortCtrl.signal)
    message.success('安装完成')
    emit('installed')
  } catch (e) {
    if ((e as Error).name === 'AbortError') {
      appendOutput('\n[nook] 用户已取消, 远端脚本可能已经在跑(无法终止)\n')
      message.warning('已取消, 但远端可能仍在执行')
    } else {
      appendOutput(`\n[error] ${(e as Error).message || ''}\n`)
      message.error('安装失败, 看输出日志定位')
    }
  } finally {
    installing.value = false
    abortCtrl = null
  }
}

// 剥 ANSI 颜色码 (\x1b[0;32m 等) 与 OSC 序列, 远端 apt/Xray 安装等命令可能带颜色
const ANSI_RE = /\x1b\[[0-9;?]*[A-Za-z]/g

function appendOutput(chunk: string) {
  output.value += chunk.replace(ANSI_RE, '')
  // 下一帧滚到底,让用户始终看到最新一行
  nextTick(() => {
    if (outputRef.value) {
      outputRef.value.scrollTop = outputRef.value.scrollHeight
    }
  })
}

function close() {
  if (installing.value) {
    // 关弹框时主动 abort, 但 SSH 命令在远端继续跑(无法 kill)
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
    style="max-width: 48rem"
    :bordered="false"
    :mask-closable="false"
    @update:show="(v: boolean) => emit('update:modelValue', v)"
  >
    <template #header>
      <div class="flex items-center gap-2">
        <NIcon :size="20" :depth="2"><Rocket /></NIcon>
        <span>一键部署 Xray</span>
      </div>
    </template>
    <template #header-extra>
      <span v-if="server" class="text-xs text-zinc-500">
        {{ server.name }} ({{ server.host }})
      </span>
    </template>

    <p class="text-xs text-zinc-500 mb-4">
      将远程执行 nook 自带的安装脚本（仅支持 Ubuntu 22.04+），装纯 Xray 内核 + 标配
      xray.json（含 grpc-api）。
      <strong>不会装 3x-ui</strong>。已有配置会先备份。
    </p>

    <NForm
      :model="form"
      label-placement="top"
      require-mark-placement="right-hanging"
      size="small"
    >
      <div class="grid grid-cols-1 sm:grid-cols-2 gap-x-4">
        <NFormItem
          required
          :validation-status="errors.vmessPort ? 'error' : undefined"
          :feedback="errors.vmessPort"
        >
          <template #label>
            <span>vmess 入站端口</span>
            <span class="text-xs text-zinc-400 ml-2">客户连接用</span>
          </template>
          <NInputNumber
            v-model:value="form.vmessPort"
            :min="1"
            :max="65535"
            :disabled="installing"
            class="w-full"
          />
        </NFormItem>
        <NFormItem
          required
          :validation-status="errors.xrayApiPort ? 'error' : undefined"
          :feedback="errors.xrayApiPort"
        >
          <template #label>
            <span>Xray gRPC 端口</span>
            <span class="text-xs text-zinc-400 ml-2">仅 127.0.0.1 监听</span>
          </template>
          <NInputNumber
            v-model:value="form.xrayApiPort"
            :min="1"
            :max="65535"
            :disabled="installing"
            class="w-full"
          />
        </NFormItem>
        <div class="sm:col-span-2">
          <NFormItem label="日志目录">
            <NInput
              v-model:value="form.logDir"
              :disabled="installing"
              :input-props="{ style: 'font-family: monospace' }"
            />
          </NFormItem>
        </div>
        <div>
          <NCheckbox v-model:checked="form.installUfw" :disabled="installing">
            配置 UFW 防火墙
          </NCheckbox>
          <p class="text-xs text-zinc-500 ml-6 mt-1">仅放 22 + vmess 端口</p>
        </div>
        <div>
          <NCheckbox v-model:checked="form.enableBbr" :disabled="installing">
            启用 BBR 拥塞控制
          </NCheckbox>
          <p class="text-xs text-zinc-500 ml-6 mt-1">提升跨境吞吐</p>
        </div>
        <div class="sm:col-span-2 mt-2">
          <NFormItem>
            <template #label>
              <span>时区</span>
              <span class="text-xs text-zinc-400 ml-2">系统时区, 影响日志/到期判定</span>
            </template>
            <NSelect
              v-model:value="form.timezone"
              :options="TIMEZONE_OPTIONS"
              :disabled="installing"
            />
          </NFormItem>
        </div>
      </div>
    </NForm>

    <!-- 输出区: 流式追加, 自动滚到最底 -->
    <div class="mt-4">
      <div class="flex items-center justify-between mb-2">
        <div class="text-sm font-semibold text-zinc-500">远程输出 (实时)</div>
        <div v-if="installing" class="flex items-center gap-2 text-xs text-zinc-500">
          <NSpin :size="14" />
          <span>实时回传中...</span>
        </div>
      </div>
      <pre
        ref="outputRef"
        class="text-xs max-h-72 overflow-auto bg-zinc-900 text-zinc-100 min-h-32 px-4 py-3 rounded whitespace-pre-wrap break-all font-mono leading-relaxed"
      ><code v-if="output">{{ output }}</code><span v-else class="text-zinc-500">{{ installing ? '准备中...' : '点"开始安装"触发, 远端 stdout 会逐行回传到这里' }}</span></pre>
    </div>

    <template #footer>
      <NSpace justify="end">
        <NButton size="small" :disabled="installing" @click="close">关闭</NButton>
        <NButton
          type="primary"
          size="small"
          :loading="installing"
          :disabled="installing"
          @click="onSubmit"
        >
          <template #icon>
            <NIcon><Rocket /></NIcon>
          </template>
          {{ installing ? '安装中...' : '开始安装' }}
        </NButton>
      </NSpace>
    </template>
  </NModal>
</template>
