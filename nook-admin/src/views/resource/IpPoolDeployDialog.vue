<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { CheckCircle2, Rocket, Shuffle } from 'lucide-vue-next'
import {
  NButton,
  NCard,
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

interface IpTypeOption {
  id: string
  code: string
  name: string
}

interface RegionOption {
  code: string
  displayName?: string
}

interface Props {
  modelValue: boolean
  ipTypes: IpTypeOption[]
  regions: RegionOption[]
}
const props = defineProps<Props>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  /** 装机成功 + 后端事务内一次性入池后, 推 ipId 给父组件刷新列表 */
  (e: 'installed', ipId: string): void
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
  // 主表: 资源归属 (Phase 4 装机自动入池新加)
  region: '',
  ipTypeId: '',
  remark: '',

  // SSH 凭据 (装机用 + 落 credential 子表)
  sshHost: '',
  sshPort: 22,
  sshUser: 'root',
  sshPassword: '',
  sshTimeoutSeconds: 60,
  sshOpTimeoutSeconds: 60,
  // SCP 上传 dante 安装脚本 (单文件 ~5KB) 走默认 180; 高延迟出海链路够用
  sshUploadTimeoutSeconds: 180,
  installTimeoutSeconds: 600,

  // SOCKS5 服务参数 (落 socks5 子表)
  socksPort: randomSocksPort(),
  socksUser: '',
  socksPass: '',
  installUfw: true,

  // dante 业务配置
  logLevel: DANTE_LOG_LEVEL_DEFAULT,
  logPath: '',
  autostartEnabled: true,
  logRotate: true,

  // 装机产物路径 (落 install 子表; 前端 default 集中到 INSTALL_DIR 下方便 SSH 进机器探查)
  // PAM 例外: Linux PAM 库硬编码读 /etc/pam.d/<service>, 必须留 OS 位置
  // sockd.conf 写到 INSTALL_DIR 下, 装机脚本同时建 symlink /etc/danted.conf → 实际路径 (apt 包默认读 /etc/danted.conf)
  installDir: '/home/socks5',
  confPath: '/home/socks5/etc/danted.conf',
  pamFile: '/etc/pam.d/sockd',
  pwdFile: '/home/socks5/etc/sockd.passwd',
  systemdUnit: 'danted'
})

const ipTypeOptions = computed(() =>
  props.ipTypes.map((t) => ({ label: `${t.name} (${t.code})`, value: t.id }))
)
const regionOptions = computed(() =>
  props.regions.map((r) => ({ label: `${r.code}${r.displayName ? ' ' + r.displayName : ''}`, value: r.code }))
)

/** logPath 占位符随 installDir 联动, 给用户看出兜底规则. */
const logPathPlaceholder = computed(
  () => `${form.installDir.trim() || '/home/socks5'}/logs/sockd.log`
)

// Phase 4: 装机成功后由后端事务内一次性落库; "添加到 IP 池" 二次按钮已废弃

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    Object.keys(errors).forEach((k) => delete errors[k])
    output.value = ''
    deployed.value = false
    Object.assign(form, {
      region: '',
      ipTypeId: props.ipTypes[0]?.id ?? '',
      remark: '',
      sshHost: '',
      sshPort: 22,
      sshUser: 'root',
      sshPassword: '',
      sshTimeoutSeconds: 60,
      sshOpTimeoutSeconds: 60,
      sshUploadTimeoutSeconds: 180,
      installTimeoutSeconds: 600,
      socksPort: randomSocksPort(),
      socksUser: '',
      socksPass: '',
      installUfw: true,
      logLevel: DANTE_LOG_LEVEL_DEFAULT,
      logPath: '',
      autostartEnabled: true,
      logRotate: true,
      installDir: '/home/socks5',
      confPath: '/home/socks5/etc/danted.conf',
      pamFile: '/etc/pam.d/sockd',
      pwdFile: '/home/socks5/etc/sockd.passwd',
      systemdUnit: 'danted'
    })
  }
)

function validate() {
  Object.keys(errors).forEach((k) => delete errors[k])
  if (!form.region.trim()) errors.region = '请选区域'
  if (!form.ipTypeId) errors.ipTypeId = '请选 IP 类型'
  if (!form.sshHost.trim()) errors.sshHost = '请输入 SSH 主机'
  if (form.sshPort < 1 || form.sshPort > 65535) errors.sshPort = '端口范围 1-65535'
  if (!form.sshUser.trim()) errors.sshUser = '请输入 SSH 用户'
  if (!form.sshPassword) errors.sshPassword = '请填 SSH 密码'
  if (!form.installDir.trim()) errors.installDir = '请输入安装目录'
  if (!form.confPath.trim()) errors.confPath = '请输入 sockd.conf 路径'
  if (!form.pamFile.trim()) errors.pamFile = '请输入 PAM 配置路径'
  if (!form.pwdFile.trim()) errors.pwdFile = '请输入密码文件路径'
  if (!form.systemdUnit.trim()) errors.systemdUnit = '请输入 systemd unit 名'
  if (form.sshTimeoutSeconds < 5 || form.sshTimeoutSeconds > 600) errors.sshTimeoutSeconds = 'SSH 握手超时 5-600 秒'
  if (form.sshOpTimeoutSeconds < 5 || form.sshOpTimeoutSeconds > 300) errors.sshOpTimeoutSeconds = 'SSH 单条命令超时 5-300 秒'
  if (form.sshUploadTimeoutSeconds < 5 || form.sshUploadTimeoutSeconds > 600) errors.sshUploadTimeoutSeconds = 'SCP 上传超时 5-600 秒'
  if (form.installTimeoutSeconds < 60 || form.installTimeoutSeconds > 3600) errors.installTimeoutSeconds = '安装超时 60-3600 秒'
  if (form.socksPort < 1 || form.socksPort > 65535) errors.socksPort = '端口范围 1-65535'
  if (!form.socksUser.trim()) errors.socksUser = '请输入 SOCKS5 用户名'
  if (!form.socksPass) errors.socksPass = '请输入 SOCKS5 密码'
  return Object.keys(errors).length === 0
}

/** 装机成功后, 后端通过 lineSink 推 `[nook] ✔ 已落库 ipId=XXX` 一行; 前端正则提取 */
const IP_ID_MARKER_RE = /\[nook\] ✔ 已落库 ipId=([a-f0-9]{32})/i
let parsedIpId: string = ''

async function onSubmit() {
  if (!validate()) return
  const ok = await confirm({
    title: '部署 SOCKS5',
    message: `在 ${form.sshHost}:${form.sshPort} 部署 SOCKS5 (端口 ${form.socksPort}); 装机成功后会自动入池 (region=${form.region}).`,
    type: 'warning',
    confirmText: '开始部署'
  })
  if (!ok) return

  installing.value = true
  deployed.value = false
  parsedIpId = ''
  output.value = ''
  abortCtrl = new AbortController()
  try {
    const dto: Socks5InstallDTO = {
      region: form.region.trim(),
      ipTypeId: form.ipTypeId,
      remark: form.remark.trim() || undefined,
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
      installUfw: form.installUfw,
      logLevel: form.logLevel.trim(),
      logPath: form.logPath.trim() || logPathPlaceholder.value,
      autostartEnabled: form.autostartEnabled,
      logRotate: form.logRotate,
      installDir: form.installDir.trim(),
      confPath: form.confPath.trim(),
      pamFile: form.pamFile.trim(),
      pwdFile: form.pwdFile.trim(),
      systemdUnit: form.systemdUnit.trim()
    }
    await installSocks5Stream(dto, appendOutput, abortCtrl.signal)
    deployed.value = true
    if (parsedIpId) {
      message.success(`部署完成, 已自动入池 (ipId=${parsedIpId.slice(0, 8)}...)`)
      emit('installed', parsedIpId)
      emit('update:modelValue', false)
    } else {
      message.warning('部署完成但未解析到 ipId, 请手动刷新列表确认')
    }
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

const ANSI_RE = /\x1b\[[0-9;?]*[A-Za-z]/g

function appendOutput(chunk: string) {
  const clean = chunk.replace(ANSI_RE, '')
  output.value += clean
  // 提取 backend 推的 ipId marker, 用于装机成功后自动跳详情
  if (!parsedIpId) {
    const m = clean.match(IP_ID_MARKER_RE)
    if (m) parsedIpId = m[1]
  }
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
      <!-- Card 1: 资源归属 (落主表) -->
      <NCard size="small" :bordered="true" class="mb-3">
        <template #header>
          <div class="text-sm font-semibold">① 资源归属</div>
        </template>
        <template #header-extra>
          <span class="text-xs text-zinc-400">落 resource_ip_pool 主表</span>
        </template>
      <div class="grid grid-cols-1 sm:grid-cols-6 gap-x-4">
        <div class="sm:col-span-2">
          <NFormItem
            label="区域"
            required
            :validation-status="errors.region ? 'error' : undefined"
            :feedback="errors.region"
          >
            <NSelect
              v-model:value="form.region"
              :options="regionOptions"
              :disabled="installing"
              filterable
              placeholder="选区域 / 输入过滤"
            />
          </NFormItem>
        </div>
        <div class="sm:col-span-2">
          <NFormItem
            label="IP 类型"
            required
            :validation-status="errors.ipTypeId ? 'error' : undefined"
            :feedback="errors.ipTypeId"
          >
            <NSelect
              v-model:value="form.ipTypeId"
              :options="ipTypeOptions"
              :disabled="installing"
              placeholder="选 IP 类型"
            />
          </NFormItem>
        </div>
        <div class="sm:col-span-2">
          <NFormItem label="备注">
            <NInput v-model:value="form.remark" :disabled="installing" placeholder="可选, 运营备注" />
          </NFormItem>
        </div>
      </div>
      </NCard>

      <!-- Card 2: SSH 凭据 (装机用 + 落 credential 子表) -->
      <NCard size="small" :bordered="true" class="mb-3">
        <template #header>
          <div class="text-sm font-semibold">② SSH 凭据</div>
        </template>
        <template #header-extra>
          <span class="text-xs text-zinc-400">装机用 + 落 credential 子表</span>
        </template>
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

      </NCard>

      <!-- Card 3: 超时配置 -->
      <NCard size="small" :bordered="true" class="mb-3">
        <template #header>
          <div class="text-sm font-semibold">③ 超时配置</div>
        </template>
        <template #header-extra>
          <span class="text-xs text-zinc-400">SSH 各阶段超时秒数</span>
        </template>
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

      </NCard>

      <!-- Card 4: SOCKS5 服务参数 (落 socks5 子表) -->
      <NCard size="small" :bordered="true" class="mb-3">
        <template #header>
          <div class="text-sm font-semibold">④ SOCKS5 服务参数</div>
        </template>
        <template #header-extra>
          <span class="text-xs text-zinc-400">dante 业务配置, 落 socks5 子表</span>
        </template>
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

        <NFormItem label="UFW">
          <NCheckbox v-model:checked="form.installUfw" :disabled="installing">
            配置 UFW (放行 SOCKS5 端口)
          </NCheckbox>
        </NFormItem>
      </div>

      </NCard>

      <!-- Card 5: dante 配置 + 装机产物 (落 install 子表; 默认值前端给, 保留即可) -->
      <NCard size="small" :bordered="true" class="mb-3">
        <template #header>
          <div class="text-sm font-semibold">⑤ dante 配置 + 装机产物</div>
        </template>
        <template #header-extra>
          <span class="text-xs text-zinc-400">默认值前端兜底, 后端校验</span>
        </template>
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
          <div class="flex flex-col gap-1">
            <NCheckbox v-model:checked="form.autostartEnabled" :disabled="installing">
              开机自启
            </NCheckbox>
            <NCheckbox
              v-model:checked="form.logRotate"
              :disabled="installing"
              title="sockd.log 50M 触发滚 + gzip 压缩 + copytruncate 0 中断; 低配机推荐开启"
            >
              日志轮转 (sockd.log 50M 自动滚)
            </NCheckbox>
          </div>
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

      <!-- 装机产物路径 (落 install 子表; 都有 default, 大多场景不用改) -->
      <details class="mt-3 cursor-pointer">
        <summary class="text-sm font-semibold text-zinc-500 select-none">
          更多路径 (sockd.conf / PAM / htpasswd / systemd unit; 默认即可)
        </summary>
        <div class="grid grid-cols-1 sm:grid-cols-4 gap-x-4 mt-2">
          <NFormItem
            label="sockd.conf"
            :validation-status="errors.confPath ? 'error' : undefined"
            :feedback="errors.confPath"
          >
            <NInput v-model:value="form.confPath" :disabled="installing"
                    :input-props="{ style: 'font-family: monospace' }" />
          </NFormItem>
          <NFormItem
            :validation-status="errors.pamFile ? 'error' : undefined"
            :feedback="errors.pamFile"
          >
            <template #label>
              <span>PAM file</span>
              <span class="text-xs text-zinc-400 ml-2">OS 强制 /etc/pam.d/</span>
            </template>
            <NInput v-model:value="form.pamFile" :disabled="installing"
                    :input-props="{ style: 'font-family: monospace' }" />
          </NFormItem>
          <NFormItem
            label="htpasswd file"
            :validation-status="errors.pwdFile ? 'error' : undefined"
            :feedback="errors.pwdFile"
          >
            <NInput v-model:value="form.pwdFile" :disabled="installing"
                    :input-props="{ style: 'font-family: monospace' }" />
          </NFormItem>
          <NFormItem
            label="systemd unit"
            :validation-status="errors.systemdUnit ? 'error' : undefined"
            :feedback="errors.systemdUnit"
          >
            <NInput v-model:value="form.systemdUnit" :disabled="installing"
                    :input-props="{ style: 'font-family: monospace' }" />
          </NFormItem>
        </div>
      </details>
      </NCard>
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
        <!-- Phase 4: 装机成功 → 后端事务内一次性落库; 不再需要 "添加到 IP 池" 二次操作 -->
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
          {{ installing ? '部署中...' : '开始部署' }}
        </NButton>
      </NSpace>
    </template>
  </NModal>
</template>
