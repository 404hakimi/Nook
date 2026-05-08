<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { CheckCircle2, Plus, Rocket } from 'lucide-vue-next'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { installSocks5Stream, type Socks5InstallDTO } from '@/api/resource/ip-pool'

interface Props {
  modelValue: boolean
}
const props = defineProps<Props>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  /** 部署成功后用户点 "添加到 IP 池", 把这些字段交给父组件预填到新增表单。 */
  (e: 'add-to-pool', payload: {
    socks5Host: string
    socks5Port: number
    socks5Username: string
    socks5Password: string
  }): void
}>()

const toast = useToast()
const { confirm } = useConfirm()

const installing = ref(false)
/** 部署是否已成功完成 (流式跑完且未抛错), 决定是否展示 "添加到 IP 池" 按钮。 */
const deployed = ref(false)
const output = ref('')
const errors = reactive<Record<string, string>>({})
const outputRef = ref<HTMLPreElement | null>(null)
let abortCtrl: AbortController | null = null

const form = reactive({
  // SSH 凭据 (一次性, 不入库)
  sshHost: '',
  sshPort: 22,
  sshUser: 'root',
  sshPassword: '',
  sshPrivateKey: '',
  sshTimeoutSeconds: 60,

  // SOCKS5 服务参数 (部署后用作 IP 池录入凭据)
  socksPort: 1080,
  socksUser: '',
  socksPass: '',
  allowFrom: '',
  installUfw: true
})

/** 部署完成后, "添加到 IP 池" 按钮把这些值发给父组件。 */
const deployedSocks5 = computed(() => ({
  socks5Host: form.sshHost.trim(),
  socks5Port: form.socksPort,
  socks5Username: form.socksUser.trim(),
  socks5Password: form.socksPass
}))

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    Object.keys(errors).forEach((k) => delete errors[k])
    output.value = ''
    deployed.value = false
    Object.assign(form, {
      sshHost: '',
      sshPort: 22,
      sshUser: 'root',
      sshPassword: '',
      sshPrivateKey: '',
      sshTimeoutSeconds: 60,
      socksPort: 1080,
      socksUser: '',
      socksPass: '',
      allowFrom: '',
      installUfw: true
    })
  }
)

function validate() {
  Object.keys(errors).forEach((k) => delete errors[k])
  if (!form.sshHost.trim()) errors.sshHost = '请输入 SSH 主机'
  if (form.sshPort < 1 || form.sshPort > 65535) errors.sshPort = '端口范围 1-65535'
  if (!form.sshUser.trim()) errors.sshUser = '请输入 SSH 用户'
  if (!form.sshPassword && !form.sshPrivateKey) errors.sshAuth = '请填 SSH 密码或私钥之一'
  if (form.sshTimeoutSeconds < 5 || form.sshTimeoutSeconds > 600) errors.sshTimeoutSeconds = 'SSH 超时 5-600 秒'
  if (form.socksPort < 1 || form.socksPort > 65535) errors.socksPort = '端口范围 1-65535'
  if (!form.socksUser.trim()) errors.socksUser = '请输入 SOCKS5 用户名'
  if (!form.socksPass) errors.socksPass = '请输入 SOCKS5 密码'
  return Object.keys(errors).length === 0
}

async function onSubmit() {
  if (!validate()) return
  const ok = await confirm({
    title: '部署 SOCKS5 落地节点',
    message:
      `将通过 SSH 连接 ${form.sshHost}:${form.sshPort} 安装 Xray + SOCKS5 inbound。\n\n` +
      `SOCKS5 端口: ${form.socksPort}, 用户: ${form.socksUser}\n` +
      `仅支持 Ubuntu 22.04+, 已存在的 Xray 配置会先备份再覆盖。`,
    type: 'warning',
    confirmText: '开始部署'
  })
  if (!ok) return

  installing.value = true
  deployed.value = false
  output.value = ''
  abortCtrl = new AbortController()
  try {
    const dto: Socks5InstallDTO = {
      sshHost: form.sshHost.trim(),
      sshPort: form.sshPort,
      sshUser: form.sshUser.trim(),
      sshPassword: form.sshPassword || undefined,
      sshPrivateKey: form.sshPrivateKey || undefined,
      sshTimeoutSeconds: form.sshTimeoutSeconds,
      socksPort: form.socksPort,
      socksUser: form.socksUser.trim(),
      socksPass: form.socksPass,
      allowFrom: form.allowFrom.trim() || undefined,
      installUfw: form.installUfw
    }
    await installSocks5Stream(dto, appendOutput, abortCtrl.signal)
    deployed.value = true
    toast.success('部署完成, 可一键添加到 IP 池')
  } catch (e) {
    if ((e as Error).name === 'AbortError') {
      appendOutput('\n[nook] 用户已取消, 远端脚本可能已经在跑(无法终止)\n')
      toast.warning('已取消, 但远端可能仍在执行')
    } else {
      appendOutput(`\n[error] ${(e as Error).message || ''}\n`)
      toast.error('部署失败, 看输出日志定位')
    }
  } finally {
    installing.value = false
    abortCtrl = null
  }
}

function onAddToPool() {
  emit('add-to-pool', deployedSocks5.value)
  emit('update:modelValue', false)
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
  if (installing.value) {
    abortCtrl?.abort()
    toast.warning('已断开输出流, 远端脚本可能仍在后台跑')
  }
  emit('update:modelValue', false)
}
</script>

<template>
  <dialog class="modal" :class="{ 'modal-open': modelValue }">
    <div class="modal-box max-w-3xl">
      <h3 class="text-lg font-semibold flex items-center gap-2 mb-1">
        <Rocket class="w-5 h-5 text-primary" />
        部署 SOCKS5 落地节点
      </h3>
      <p class="text-xs text-base-content/50 mb-4">
        在远端主机上自动安装 Xray + SOCKS5 inbound; SSH 凭据仅本次使用、不入库。
        部署成功后可一键把 SOCKS5 凭据 (host=出网 IP / port / 用户 / 密码) 添加到 IP 池。
      </p>

      <!-- SSH 凭据 -->
      <div class="text-sm font-semibold text-base-content/70 mb-2">SSH 凭据 (一次性)</div>
      <div class="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div class="sm:col-span-2">
          <label class="label py-1"><span class="label-text">SSH 主机 <span class="text-error">*</span></span></label>
          <input
            v-model="form.sshHost"
            type="text"
            placeholder="部署目标主机 (通常 = 出网 IP)"
            :disabled="installing"
            class="input input-bordered input-sm w-full font-mono"
            :class="{ 'input-error': errors.sshHost }"
          />
          <div v-if="errors.sshHost" class="text-error text-xs mt-1">{{ errors.sshHost }}</div>
        </div>
        <div>
          <label class="label py-1"><span class="label-text">SSH 端口</span></label>
          <input
            v-model.number="form.sshPort"
            type="number"
            min="1"
            max="65535"
            :disabled="installing"
            class="input input-bordered input-sm w-full"
            :class="{ 'input-error': errors.sshPort }"
          />
        </div>
        <div>
          <label class="label py-1"><span class="label-text">SSH 用户</span></label>
          <input
            v-model="form.sshUser"
            type="text"
            :disabled="installing"
            class="input input-bordered input-sm w-full"
            :class="{ 'input-error': errors.sshUser }"
          />
        </div>
        <div class="sm:col-span-2">
          <label class="label py-1">
            <span class="label-text">SSH 超时 (秒)</span>
            <span class="label-text-alt text-base-content/50">5-600, 跨洲建议 60-120</span>
          </label>
          <input
            v-model.number="form.sshTimeoutSeconds"
            type="number"
            min="5"
            max="600"
            :disabled="installing"
            class="input input-bordered input-sm w-full"
            :class="{ 'input-error': errors.sshTimeoutSeconds }"
          />
        </div>
        <div class="sm:col-span-3">
          <label class="label py-1"><span class="label-text">SSH 密码</span></label>
          <input
            v-model="form.sshPassword"
            type="password"
            autocomplete="new-password"
            :disabled="installing"
            class="input input-bordered input-sm w-full"
          />
        </div>
        <div class="sm:col-span-3">
          <label class="label py-1"><span class="label-text">SSH 私钥 (PEM)</span></label>
          <textarea
            v-model="form.sshPrivateKey"
            rows="3"
            placeholder="-----BEGIN OPENSSH PRIVATE KEY-----..."
            :disabled="installing"
            class="textarea textarea-bordered w-full text-xs font-mono"
          ></textarea>
        </div>
        <div v-if="errors.sshAuth" class="sm:col-span-3 text-error text-xs">{{ errors.sshAuth }}</div>
      </div>

      <!-- SOCKS5 服务参数 -->
      <div class="text-sm font-semibold text-base-content/70 mt-6 mb-2">SOCKS5 服务参数</div>
      <div class="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div>
          <label class="label py-1"><span class="label-text">SOCKS5 端口 <span class="text-error">*</span></span></label>
          <input
            v-model.number="form.socksPort"
            type="number"
            min="1"
            max="65535"
            :disabled="installing"
            class="input input-bordered input-sm w-full"
            :class="{ 'input-error': errors.socksPort }"
          />
        </div>
        <div>
          <label class="label py-1"><span class="label-text">用户名 <span class="text-error">*</span></span></label>
          <input
            v-model="form.socksUser"
            type="text"
            :disabled="installing"
            class="input input-bordered input-sm w-full"
            :class="{ 'input-error': errors.socksUser }"
          />
        </div>
        <div>
          <label class="label py-1"><span class="label-text">密码 <span class="text-error">*</span></span></label>
          <input
            v-model="form.socksPass"
            type="password"
            autocomplete="new-password"
            :disabled="installing"
            class="input input-bordered input-sm w-full"
            :class="{ 'input-error': errors.socksPass }"
          />
        </div>
        <div class="sm:col-span-2">
          <label class="label py-1">
            <span class="label-text">UFW allow_from</span>
            <span class="label-text-alt text-base-content/50">推荐填中转线路公网 IP</span>
          </label>
          <input
            v-model="form.allowFrom"
            type="text"
            placeholder="留空 = 0.0.0.0/0"
            :disabled="installing"
            class="input input-bordered input-sm w-full font-mono"
          />
        </div>
        <div>
          <label class="cursor-pointer label justify-start gap-2 py-1 mt-6">
            <input
              v-model="form.installUfw"
              type="checkbox"
              :disabled="installing"
              class="checkbox checkbox-sm"
            />
            <span class="label-text">配置 UFW</span>
          </label>
        </div>
      </div>

      <!-- 输出区 -->
      <div class="mt-4">
        <div class="flex items-center justify-between mb-2">
          <div class="text-sm font-semibold text-base-content/70">远程输出 (实时)</div>
          <div v-if="installing" class="flex items-center gap-1 text-xs text-base-content/60">
            <span class="loading loading-spinner loading-xs"></span>
            <span>实时回传中...</span>
          </div>
          <div v-else-if="deployed" class="flex items-center gap-1 text-xs text-success">
            <CheckCircle2 class="w-4 h-4" />
            <span>部署完成</span>
          </div>
        </div>
        <pre
          ref="outputRef"
          class="text-xs max-h-72 overflow-auto bg-neutral text-neutral-content min-h-32 px-4 py-3 rounded whitespace-pre-wrap break-all font-mono leading-relaxed"
        ><code v-if="output">{{ output }}</code><span v-else class="text-neutral-content/50">{{ installing ? '准备中...' : '点 "开始部署" 触发, 远端 stdout 会逐行回传到这里' }}</span></pre>
      </div>

      <div class="modal-action mt-6">
        <button class="btn btn-ghost btn-sm" :disabled="installing" @click="close">关闭</button>
        <!-- 部署成功后展示 "添加到 IP 池"; 失败状态再次点 "开始部署" 重试 -->
        <button
          v-if="deployed"
          class="btn btn-success btn-sm gap-1"
          @click="onAddToPool"
        >
          <Plus class="w-4 h-4" />
          添加到 IP 池
        </button>
        <button
          v-else
          class="btn btn-primary btn-sm gap-1"
          :disabled="installing"
          @click="onSubmit"
        >
          <span v-if="installing" class="loading loading-spinner loading-xs"></span>
          <Rocket v-else class="w-4 h-4" />
          {{ installing ? '部署中...' : '开始部署' }}
        </button>
      </div>
    </div>
    <div class="modal-backdrop bg-black/40" @click="close"></div>
  </dialog>
</template>
