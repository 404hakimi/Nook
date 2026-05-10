<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { CheckCircle2, Plus, Rocket } from 'lucide-vue-next'
import {
  NButton,
  NCheckbox,
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

const message = useMessage()
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
  sshTimeoutSeconds: 60,
  sshOpTimeoutSeconds: 60,
  sshUploadTimeoutSeconds: 60,
  installTimeoutSeconds: 600,

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
      sshTimeoutSeconds: 60,
      sshOpTimeoutSeconds: 60,
      sshUploadTimeoutSeconds: 60,
      installTimeoutSeconds: 600,
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
  if (!form.sshPassword) errors.sshPassword = '请填 SSH 密码'
  if (form.sshTimeoutSeconds < 5 || form.sshTimeoutSeconds > 600) errors.sshTimeoutSeconds = 'SSH 握手超时 5-600 秒'
  if (form.sshOpTimeoutSeconds < 5 || form.sshOpTimeoutSeconds > 300) errors.sshOpTimeoutSeconds = 'SSH 单条命令超时 5-300 秒'
  if (form.sshUploadTimeoutSeconds < 5 || form.sshUploadTimeoutSeconds > 600) errors.sshUploadTimeoutSeconds = 'SCP 上传超时 5-600 秒'
  if (form.installTimeoutSeconds < 60 || form.installTimeoutSeconds > 3600) errors.installTimeoutSeconds = '安装超时 60-3600 秒'
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
      sshPassword: form.sshPassword,
      sshTimeoutSeconds: form.sshTimeoutSeconds,
      sshOpTimeoutSeconds: form.sshOpTimeoutSeconds,
      sshUploadTimeoutSeconds: form.sshUploadTimeoutSeconds,
      installTimeoutSeconds: form.installTimeoutSeconds,
      socksPort: form.socksPort,
      socksUser: form.socksUser.trim(),
      socksPass: form.socksPass,
      allowFrom: form.allowFrom.trim() || undefined,
      installUfw: form.installUfw
    }
    await installSocks5Stream(dto, appendOutput, abortCtrl.signal)
    deployed.value = true
    message.success('部署完成, 可一键添加到 IP 池')
  } catch (e) {
    if ((e as Error).name === 'AbortError') {
      appendOutput('\n[nook] 用户已取消, 远端脚本可能已经在跑(无法终止)\n')
      message.warning('已取消, 但远端可能仍在执行')
    } else {
      appendOutput(`\n[error] ${(e as Error).message || ''}\n`)
      message.error('部署失败, 看输出日志定位')
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
        <span>部署 SOCKS5 落地节点</span>
      </div>
    </template>

    <p class="text-xs text-zinc-500 mb-4">
      在远端主机上自动安装 Xray + SOCKS5 inbound; SSH 凭据仅本次使用、不入库。
      部署成功后可一键把 SOCKS5 凭据 (host=出网 IP / port / 用户 / 密码) 添加到 IP 池。
    </p>

    <NForm
      :model="form"
      label-placement="top"
      require-mark-placement="right-hanging"
      size="small"
    >
      <div class="text-sm font-semibold mb-2">SSH 凭据 (一次性)</div>
      <div class="grid grid-cols-1 sm:grid-cols-3 gap-x-4">
        <div class="sm:col-span-2">
          <NFormItem
            label="SSH 主机"
            required
            :validation-status="errors.sshHost ? 'error' : undefined"
            :feedback="errors.sshHost"
          >
            <NInput
              v-model:value="form.sshHost"
              placeholder="部署目标主机 (通常 = 出网 IP)"
              :disabled="installing"
              :input-props="{ style: 'font-family: monospace' }"
            />
          </NFormItem>
        </div>

        <NFormItem
          label="SSH 端口"
          :validation-status="errors.sshPort ? 'error' : undefined"
          :feedback="errors.sshPort"
        >
          <NInputNumber
            v-model:value="form.sshPort"
            :min="1"
            :max="65535"
            :disabled="installing"
            style="width: 100%"
          />
        </NFormItem>

        <NFormItem
          label="SSH 用户"
          :validation-status="errors.sshUser ? 'error' : undefined"
          :feedback="errors.sshUser"
        >
          <NInput v-model:value="form.sshUser" :disabled="installing" />
        </NFormItem>

        <div class="sm:col-span-2"></div>

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
              :disabled="installing"
              :input-props="{ autocomplete: 'new-password' }"
              placeholder="必填"
            />
          </NFormItem>
        </div>
      </div>

      <div class="text-sm font-semibold mt-4 mb-2">
        超时配置
      </div>
      <div class="grid grid-cols-1 sm:grid-cols-2 gap-x-4">
        <NFormItem
          :validation-status="errors.sshTimeoutSeconds ? 'error' : undefined"
          :feedback="errors.sshTimeoutSeconds"
        >
          <template #label>
            <span>SSH 握手超时 (秒)</span>
            <span class="text-xs text-zinc-400 ml-2">5-600</span>
          </template>
          <NInputNumber
            v-model:value="form.sshTimeoutSeconds"
            :min="5"
            :max="600"
            :disabled="installing"
            style="width: 100%"
          />
        </NFormItem>
        <NFormItem
          :validation-status="errors.sshOpTimeoutSeconds ? 'error' : undefined"
          :feedback="errors.sshOpTimeoutSeconds"
        >
          <template #label>
            <span>SSH 单条命令超时 (秒)</span>
            <span class="text-xs text-zinc-400 ml-2">5-300</span>
          </template>
          <NInputNumber
            v-model:value="form.sshOpTimeoutSeconds"
            :min="5"
            :max="300"
            :disabled="installing"
            style="width: 100%"
          />
        </NFormItem>
        <NFormItem
          :validation-status="errors.sshUploadTimeoutSeconds ? 'error' : undefined"
          :feedback="errors.sshUploadTimeoutSeconds"
        >
          <template #label>
            <span>SCP 上传超时 (秒)</span>
            <span class="text-xs text-zinc-400 ml-2">5-600</span>
          </template>
          <NInputNumber
            v-model:value="form.sshUploadTimeoutSeconds"
            :min="5"
            :max="600"
            :disabled="installing"
            style="width: 100%"
          />
        </NFormItem>
        <NFormItem
          :validation-status="errors.installTimeoutSeconds ? 'error' : undefined"
          :feedback="errors.installTimeoutSeconds"
        >
          <template #label>
            <span>安装超时 (秒)</span>
            <span class="text-xs text-zinc-400 ml-2">60-3600</span>
          </template>
          <NInputNumber
            v-model:value="form.installTimeoutSeconds"
            :min="60"
            :max="3600"
            :disabled="installing"
            style="width: 100%"
          />
        </NFormItem>
      </div>

      <div class="text-sm font-semibold mt-4 mb-2">SOCKS5 服务参数</div>
      <div class="grid grid-cols-1 sm:grid-cols-3 gap-x-4">
        <NFormItem
          label="SOCKS5 端口"
          required
          :validation-status="errors.socksPort ? 'error' : undefined"
          :feedback="errors.socksPort"
        >
          <NInputNumber
            v-model:value="form.socksPort"
            :min="1"
            :max="65535"
            :disabled="installing"
            style="width: 100%"
          />
        </NFormItem>

        <NFormItem
          label="用户名"
          required
          :validation-status="errors.socksUser ? 'error' : undefined"
          :feedback="errors.socksUser"
        >
          <NInput v-model:value="form.socksUser" :disabled="installing" />
        </NFormItem>

        <NFormItem
          label="密码"
          required
          :validation-status="errors.socksPass ? 'error' : undefined"
          :feedback="errors.socksPass"
        >
          <NInput
            v-model:value="form.socksPass"
            type="password"
            show-password-on="click"
            :disabled="installing"
            :input-props="{ autocomplete: 'new-password' }"
          />
        </NFormItem>

        <div class="sm:col-span-2">
          <NFormItem>
            <template #label>
              <span>UFW allow_from</span>
              <span class="text-xs text-zinc-400 ml-2">推荐填中转线路公网 IP</span>
            </template>
            <NInput
              v-model:value="form.allowFrom"
              placeholder="留空 = 0.0.0.0/0"
              :disabled="installing"
              :input-props="{ style: 'font-family: monospace' }"
            />
          </NFormItem>
        </div>

        <NFormItem label=" ">
          <NCheckbox v-model:checked="form.installUfw" :disabled="installing">
            配置 UFW
          </NCheckbox>
        </NFormItem>
      </div>
    </NForm>

    <!-- 输出区 -->
    <div class="mt-4">
      <div class="flex items-center justify-between mb-2">
        <div class="text-sm font-semibold">远程输出 (实时)</div>
        <div v-if="installing" class="flex items-center gap-2 text-xs text-zinc-500">
          <NSpin :size="14" />
          <span>实时回传中...</span>
        </div>
        <div
          v-else-if="deployed"
          class="flex items-center gap-1 text-xs"
          style="color: var(--n-success-color, #18a058)"
        >
          <NIcon :size="16"><CheckCircle2 /></NIcon>
          <span>部署完成</span>
        </div>
      </div>
      <pre
        ref="outputRef"
        class="text-xs max-h-72 min-h-32 overflow-auto bg-zinc-900 text-zinc-100 px-4 py-3 rounded whitespace-pre-wrap break-all font-mono leading-relaxed"
      ><code v-if="output">{{ output }}</code><span v-else class="text-zinc-500">{{ installing ? '准备中...' : '点 "开始部署" 触发, 远端 stdout 会逐行回传到这里' }}</span></pre>
    </div>

    <template #footer>
      <NSpace justify="end">
        <NButton size="small" :disabled="installing" @click="close">关闭</NButton>
        <!-- 部署成功后展示 "添加到 IP 池"; 失败状态再次点 "开始部署" 重试 -->
        <NButton
          v-if="deployed"
          type="success"
          size="small"
          @click="onAddToPool"
        >
          <template #icon><NIcon><Plus /></NIcon></template>
          添加到 IP 池
        </NButton>
        <NButton
          v-else
          type="primary"
          size="small"
          :loading="installing"
          :disabled="installing"
          @click="onSubmit"
        >
          <template #icon>
            <NIcon><Rocket /></NIcon>
          </template>
          {{ installing ? '部署中...' : '开始部署' }}
        </NButton>
      </NSpace>
    </template>
  </NModal>
</template>
