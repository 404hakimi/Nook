<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { CheckCircle2, Plus, Rocket, Shuffle } from 'lucide-vue-next'
import {
  NButton,
  NCheckbox,
  NForm,
  NFormItem,
  NIcon,
  NInput,
  NInputGroup,
  NInputNumber,
  NModal,
  NSelect,
  NSpace,
  NSpin,
  useMessage
} from 'naive-ui'
import { useConfirm } from '@/composables/useConfirm'
import {
  DANTE_LOG_LEVEL_DEFAULT,
  DANTE_LOG_LEVEL_OPTIONS,
  installSocks5Stream,
  type Socks5InstallDTO
} from '@/api/resource/ip-pool'

interface Props {
  modelValue: boolean
}
const props = defineProps<Props>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  /** 部署成功后用户点 "添加到 IP 池", 把这些字段交给父组件预填到新增表单。 */
  (e: 'add-to-pool', payload: {
    ipAddress: string
    socks5Port: number
    socks5Username: string
    socks5Password: string
    /** dante 日志级别 (空格分隔关键字). */
    logLevel?: string
    /** dante logoutput 路径; 空 = 用 installDir/logs/sockd.log 兜底. */
    logPath?: string
    /** systemd 开机自启 (1/0). */
    autostartEnabled: number
    /** UFW 是否配置 (1/0). */
    firewallEnabled: number
    /** UFW allow_from CIDR; 空 = 0.0.0.0/0. */
    firewallAllowFrom?: string
    /** SOCKS5 安装目录. */
    installDir?: string
    /** SSH 主机 (= sshHost form 值, 通常 = ipAddress); 后续运维操作 (详情/日志) 用 */
    sshHost: string
    sshPort: number
    sshUser: string
    /** SSH 密码; 跟 SOCKS5 密码同口径明文落库, 后台受信网络场景. */
    sshPassword: string
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

/**
 * 随机 SOCKS5 端口生成; 避开 1024 以下特权端口和常见服务范围, 走 20000-60000 高位段.
 * 每次打开部署弹框都重新随机一个, 减少用户用同一默认端口导致冲突 / 被扫的概率.
 */
function randomSocksPort(): number {
  return Math.floor(Math.random() * (60000 - 20000 + 1)) + 20000
}

/**
 * 加密强度随机字母数字串; 用 crypto.getRandomValues 而非 Math.random, 密码场景不允许伪随机.
 * 字符集纯字母数字, 避开符号 (URL / shell 不用 encode, 复制粘贴 / curl 全顺畅).
 */
function randomAlnum(len: number, mixedCase: boolean): string {
  const lower = 'abcdefghijklmnopqrstuvwxyz'
  const upper = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'
  const digit = '0123456789'
  const charset = (mixedCase ? lower + upper : lower) + digit
  const buf = new Uint32Array(len)
  crypto.getRandomValues(buf)
  let out = ''
  for (let i = 0; i < len; i++) out += charset[buf[i] % charset.length]
  return out
}

/** 随机 SOCKS5 用户名: 头位小写字母 + 7 位 alphanumeric; PAM 友好 (部分实现要求 lowercase 开头). */
function randomSocksUser(): string {
  const lower = 'abcdefghijklmnopqrstuvwxyz'
  const buf = new Uint8Array(1)
  crypto.getRandomValues(buf)
  return lower[buf[0] % lower.length] + randomAlnum(7, false)
}

/** 随机 SOCKS5 密码: 16 字符 mixed-case alphanumeric, ~95 bit 熵, 对得起明文落库的口径. */
function randomSocksPass(): string {
  return randomAlnum(16, true)
}

const form = reactive({
  // SSH 凭据 (一次性, 不入库)
  sshHost: '',
  sshPort: 22,
  sshUser: 'root',
  sshPassword: '',
  sshTimeoutSeconds: 60,
  sshOpTimeoutSeconds: 60,
  // SCP 上传 dante 安装脚本 (单文件 ~5KB) 走默认 180; 高延迟出海链路够用
  sshUploadTimeoutSeconds: 180,
  installTimeoutSeconds: 600,

  // SOCKS5 服务参数 (部署后用作 IP 池录入凭据)
  socksPort: randomSocksPort(),
  socksUser: '',
  socksPass: '',
  allowFrom: '',
  installUfw: true,

  // dante 高级配置 (有合理默认, 让用户改 log / 自启 / 防火墙时不用改部署脚本)
  // logLevel 走预设下拉, 默认 "警告"; 自由字符串依然合法但不再开口
  logLevel: DANTE_LOG_LEVEL_DEFAULT,
  /** 日志路径; 留空走 {installDir}/logs/sockd.log 兜底, placeholder 即兜底值 */
  logPath: '',
  autostartEnabled: true,
  /** SOCKS5 安装目录; logs/info.txt 等运维资产放这里, 跟 xray 部署习惯一致 */
  installDir: '/home/socks5'
})

/** logPath 占位符随 installDir 联动, 给用户看出兜底规则. */
const logPathPlaceholder = computed(
  () => `${form.installDir.trim() || '/home/socks5'}/logs/sockd.log`
)

/**
 * 部署完成后, "添加到 IP 池" 按钮把这些值发给父组件; ipAddress 默认 = sshHost (出网 IP).
 * 高级配置同步带过去, 否则 IP 池条目记录的 dante 配置会跟远端实际状态对不上.
 */
const deployedSocks5 = computed(() => ({
  ipAddress: form.sshHost.trim(),
  socks5Port: form.socksPort,
  socks5Username: form.socksUser.trim(),
  socks5Password: form.socksPass,
  logLevel: form.logLevel.trim() || undefined,
  logPath: form.logPath.trim() || undefined,
  autostartEnabled: form.autostartEnabled ? 1 : 0,
  firewallEnabled: form.installUfw ? 1 : 0,
  firewallAllowFrom: form.allowFrom.trim() || undefined,
  installDir: form.installDir.trim() || undefined,
  sshHost: form.sshHost.trim(),
  sshPort: form.sshPort,
  sshUser: form.sshUser.trim(),
  sshPassword: form.sshPassword
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
      sshUploadTimeoutSeconds: 180,
      installTimeoutSeconds: 600,
      // 每次打开都重新随机, 避免用户反复用同一端口
      socksPort: randomSocksPort(),
      socksUser: '',
      socksPass: '',
      allowFrom: '',
      installUfw: true,
      logLevel: DANTE_LOG_LEVEL_DEFAULT,
      logPath: '',
      autostartEnabled: true,
      installDir: '/home/socks5'
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
    title: '部署 SOCKS5',
    message: `在 ${form.sshHost}:${form.sshPort} 部署 SOCKS5 (端口 ${form.socksPort})?`,
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
      installUfw: form.installUfw,
      logLevel: form.logLevel.trim() || undefined,
      logPath: form.logPath.trim() || undefined,
      autostartEnabled: form.autostartEnabled,
      installDir: form.installDir.trim() || undefined
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
    style="max-width: 72rem; width: 92vw"
    :bordered="false"
    :mask-closable="false"
    :close-on-esc="false"
    @update:show="(v: boolean) => emit('update:modelValue', v)"
  >
    <template #header>
      <div class="flex items-center gap-2">
        <NIcon :size="20" :depth="2"><Rocket /></NIcon>
        <span>部署 SOCKS5 落地节点</span>
      </div>
    </template>

    <NForm
      :model="form"
      label-placement="top"
      require-mark-placement="right-hanging"
      size="small"
    >
      <div class="text-sm font-semibold mb-2">SSH 凭据 (一次性)</div>
      <div class="grid grid-cols-1 sm:grid-cols-6 gap-x-4">
        <div class="sm:col-span-3">
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

      <div class="text-sm font-semibold mt-4 mb-2">
        超时配置
      </div>
      <div class="grid grid-cols-2 sm:grid-cols-4 gap-x-4">
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
      <div class="grid grid-cols-1 sm:grid-cols-4 gap-x-4">
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
          <NInputGroup>
            <NInput v-model:value="form.socksUser" :disabled="installing" />
            <NButton
              :disabled="installing"
              title="生成随机用户名"
              @click="form.socksUser = randomSocksUser()"
            >
              <template #icon><NIcon><Shuffle /></NIcon></template>
            </NButton>
          </NInputGroup>
        </NFormItem>

        <NFormItem
          label="密码"
          required
          :validation-status="errors.socksPass ? 'error' : undefined"
          :feedback="errors.socksPass"
        >
          <NInputGroup>
            <NInput
              v-model:value="form.socksPass"
              type="password"
              show-password-on="click"
              :disabled="installing"
              :input-props="{ autocomplete: 'new-password' }"
            />
            <NButton
              :disabled="installing"
              title="生成随机 16 位密码"
              @click="form.socksPass = randomSocksPass()"
            >
              <template #icon><NIcon><Shuffle /></NIcon></template>
            </NButton>
          </NInputGroup>
        </NFormItem>

        <div class="sm:col-span-3">
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

      <div class="text-sm font-semibold mt-4 mb-2">
        高级配置
        <span class="text-xs text-zinc-400 ml-2 font-normal">(dante 日志 / 自启等; 保留默认即可)</span>
      </div>
      <div class="grid grid-cols-1 sm:grid-cols-4 gap-x-4">
        <div class="sm:col-span-2">
          <NFormItem>
            <template #label>
              <span>日志级别</span>
              <span class="text-xs text-zinc-400 ml-2">事件关键字组合</span>
            </template>
            <NSelect
              v-model:value="form.logLevel"
              :options="[...DANTE_LOG_LEVEL_OPTIONS]"
              :disabled="installing"
            />
          </NFormItem>
        </div>

        <NFormItem label=" ">
          <NCheckbox v-model:checked="form.autostartEnabled" :disabled="installing">
            开机自启
          </NCheckbox>
        </NFormItem>

        <div class="sm:col-span-2">
          <NFormItem>
            <template #label>
              <span>安装目录</span>
              <span class="text-xs text-zinc-400 ml-2">默认 /home/socks5</span>
            </template>
            <NInput
              v-model:value="form.installDir"
              placeholder="/home/socks5"
              :disabled="installing"
              :input-props="{ style: 'font-family: monospace' }"
            />
          </NFormItem>
        </div>

        <div class="sm:col-span-2">
          <NFormItem>
            <template #label>
              <span>日志路径</span>
              <span class="text-xs text-zinc-400 ml-2">留空 = 安装目录/logs/sockd.log</span>
            </template>
            <NInput
              v-model:value="form.logPath"
              :placeholder="logPathPlaceholder"
              :disabled="installing"
              :input-props="{ style: 'font-family: monospace' }"
            />
          </NFormItem>
        </div>
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
      ><code v-if="output">{{ output }}</code><span v-else class="text-zinc-500">{{ installing ? '准备中...' : '远端 stdout 实时输出' }}</span></pre>
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
