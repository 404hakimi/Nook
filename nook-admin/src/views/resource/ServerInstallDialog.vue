<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { Rocket } from 'lucide-vue-next'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { xrayInstall, type LineServerInstallDTO } from '@/api/xray/server'
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

const toast = useToast()
const { confirm } = useConfirm()

const installing = ref(false)
const output = ref('')
const errors = reactive<Record<string, string>>({})

const form = reactive<Required<LineServerInstallDTO>>({
  vmessPort: 443,
  xrayApiPort: 62789,
  logDir: '/var/log/xray',
  installUfw: true,
  enableBbr: true
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
    message: `将在 ${props.server.name} 上安装 Xray + 标配配置（约 1-5 分钟，期间 SSH 连接持续保持）。\n\n如已存在 Xray 配置会先备份再覆盖。`,
    type: 'warning',
    confirmText: '开始安装'
  })
  if (!ok) return
  installing.value = true
  output.value = '正在执行安装脚本，请耐心等待...\n'
  try {
    const dto: LineServerInstallDTO = {
      vmessPort: form.vmessPort,
      xrayApiPort: form.xrayApiPort,
      logDir: form.logDir,
      installUfw: form.installUfw,
      enableBbr: form.enableBbr
    }
    const res = await xrayInstall(props.server.id, dto)
    output.value = String(res ?? '')
    toast.success('安装完成')
    emit('installed')
  } catch (e) {
    output.value += `\n[error] ${(e as Error).message || ''}`
    toast.error('安装失败，看输出日志')
  } finally {
    installing.value = false
  }
}

function close() {
  if (installing.value) {
    toast.warning('安装进行中，关闭只是隐藏窗口，进程在后台继续跑')
  }
  emit('update:modelValue', false)
}
</script>

<template>
  <dialog class="modal" :class="{ 'modal-open': modelValue }">
    <div class="modal-box max-w-3xl">
      <h3 class="text-lg font-semibold flex items-center gap-2 mb-1">
        <Rocket class="w-5 h-5 text-primary" />
        一键部署 Xray
        <span v-if="server" class="text-sm font-normal text-base-content/60">— {{ server.name }} ({{ server.host }})</span>
      </h3>
      <p class="text-xs text-base-content/50 mb-4">
        将远程执行 nook 自带的安装脚本（仅支持 Ubuntu 22.04+），装纯 Xray 内核 + 标配 xray.json（含 grpc-api）。
        <strong>不会装 3x-ui</strong>。已有配置会先备份。
      </p>

      <!-- 参数区 -->
      <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div>
          <label class="label py-1">
            <span class="label-text">vmess 入站端口 <span class="text-error">*</span></span>
            <span class="label-text-alt text-base-content/50">客户连接用</span>
          </label>
          <input
            v-model.number="form.vmessPort"
            type="number"
            min="1"
            max="65535"
            :disabled="installing"
            class="input input-bordered input-sm w-full"
            :class="{ 'input-error': errors.vmessPort }"
          />
          <div v-if="errors.vmessPort" class="text-error text-xs mt-1">{{ errors.vmessPort }}</div>
        </div>
        <div>
          <label class="label py-1">
            <span class="label-text">Xray gRPC 端口 <span class="text-error">*</span></span>
            <span class="label-text-alt text-base-content/50">仅 127.0.0.1 监听</span>
          </label>
          <input
            v-model.number="form.xrayApiPort"
            type="number"
            min="1"
            max="65535"
            :disabled="installing"
            class="input input-bordered input-sm w-full"
            :class="{ 'input-error': errors.xrayApiPort }"
          />
          <div v-if="errors.xrayApiPort" class="text-error text-xs mt-1">{{ errors.xrayApiPort }}</div>
        </div>
        <div class="sm:col-span-2">
          <label class="label py-1"><span class="label-text">日志目录</span></label>
          <input
            v-model="form.logDir"
            type="text"
            :disabled="installing"
            class="input input-bordered input-sm w-full font-mono"
          />
        </div>
        <div>
          <label class="cursor-pointer label justify-start gap-2 py-1">
            <input
              v-model="form.installUfw"
              type="checkbox"
              :disabled="installing"
              class="checkbox checkbox-sm"
            />
            <span class="label-text">配置 UFW 防火墙</span>
          </label>
          <p class="text-xs text-base-content/50 ml-7">仅放 22 + vmess 端口</p>
        </div>
        <div>
          <label class="cursor-pointer label justify-start gap-2 py-1">
            <input
              v-model="form.enableBbr"
              type="checkbox"
              :disabled="installing"
              class="checkbox checkbox-sm"
            />
            <span class="label-text">启用 BBR 拥塞控制</span>
          </label>
          <p class="text-xs text-base-content/50 ml-7">提升跨境吞吐</p>
        </div>
      </div>

      <!-- 输出区 -->
      <div class="mt-4">
        <div class="text-sm font-semibold text-base-content/70 mb-2">远程输出</div>
        <div class="mockup-code text-xs max-h-72 overflow-auto bg-base-300 text-base-content min-h-32">
          <div v-if="installing && !output" class="px-4 py-2">
            <span class="loading loading-spinner loading-xs mr-2"></span>
            正在安装中...约 1-5 分钟
          </div>
          <pre v-else-if="output" class="px-4 whitespace-pre-wrap break-all"><code>{{ output }}</code></pre>
          <div v-else class="px-4 py-2 text-base-content/40">点"开始安装"触发</div>
        </div>
      </div>

      <div class="modal-action mt-6">
        <button class="btn btn-ghost btn-sm" :disabled="installing" @click="close">关闭</button>
        <button
          class="btn btn-primary btn-sm gap-1"
          :disabled="installing"
          @click="onSubmit"
        >
          <span v-if="installing" class="loading loading-spinner loading-xs"></span>
          <Rocket v-else class="w-4 h-4" />
          {{ installing ? '安装中...' : '开始安装' }}
        </button>
      </div>
    </div>
    <div class="modal-backdrop bg-black/40" @click="close"></div>
  </dialog>
</template>
