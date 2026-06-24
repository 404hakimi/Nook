<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ChevronDown, ChevronRight, Rocket } from 'lucide-vue-next'
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
import {
  xrayInstallStream,
  listRealityDest,
  getProtocolSchemas,
  type LineServerInstallDTO,
  type ProtocolSchema,
  type InboundFieldSchema
} from '@/api/xray/xray-install'
import { pageServers } from '@/api/resource/server'
import { listSystemDomain } from '@/api/system/domain'

/** ServerInstallDialog 仅需 server.id + server.name; 实际可接受任何含 id/name 的形态 (ResourceServer / ServerFrontlineListItem). */
interface ServerTarget { id: string; name: string }

interface Props {
  modelValue: boolean
  server?: ServerTarget | null
  /** 重装预填: { protocol, sharedInboundPort, formValues }; 新装传空走协议 schema 默认值. */
  prefill?: Record<string, unknown> | null
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

// server 没传时弹框内自带选择器 (Xray 节点页顶部"部署/重装"入口走这条路径);
// server 传了直接锁定 (服务器列表行操作进来走这条路径, 当前已搬走)
const pickedServer = ref<ServerTarget | null>(null)
const serverOptions = ref<{ label: string; value: string; raw: ServerTarget }[]>([])
const serversLoading = ref(false)
const effectiveServer = computed<ServerTarget | null>(
  () => props.server ?? pickedServer.value
)

async function loadServerOptions() {
  if (serversLoading.value) return
  serversLoading.value = true
  try {
    // 上限 200 个; 真有这么多 server 时再加分页选择器
    const res = await pageServers({ pageNo: 1, pageSize: 200 })
    serverOptions.value = res.records.map((s) => ({
      label: s.name,
      value: s.id,
      raw: s
    }))
  } catch {
    serverOptions.value = []
  } finally {
    serversLoading.value = false
  }
}

function onPickServer(serverId: string | null) {
  if (!serverId) {
    pickedServer.value = null
    return
  }
  const opt = serverOptions.value.find((o) => o.value === serverId)
  pickedServer.value = opt ? opt.raw : null
}

// ===== 协议 schema (后端 /list-protocols 数据驱动; 加协议前端零改) =====
const protocolSchemas = ref<ProtocolSchema[]>([])
const protocolOptions = computed(() =>
  protocolSchemas.value.map((s) => ({ label: s.label, value: s.protocol }))
)
const selectedSchema = computed<ProtocolSchema | undefined>(() =>
  protocolSchemas.value.find((s) => s.protocol === form.protocol)
)

async function loadProtocolSchemas() {
  try {
    protocolSchemas.value = (await getProtocolSchemas()) || []
  } catch {
    protocolSchemas.value = []
  }
}

// select 字段的候选来源注册表: optionsKey → loader; 加新候选源在这里加一项
const optionsCache = reactive<Record<string, { label: string; value: string }[]>>({})
const optionsLoaders: Record<string, () => Promise<{ label: string; value: string }[]>> = {
  domains: async () => (await listSystemDomain()).map((d) => ({ label: d.domain, value: d.id })),
  realityDest: async () => (await listRealityDest()).map((d) => ({ label: d.label, value: d.value }))
}
async function loadOptions(key: string) {
  if (optionsCache[key] || !optionsLoaders[key]) return
  try {
    optionsCache[key] = await optionsLoaders[key]()
  } catch {
    optionsCache[key] = []
  }
}
function loadSchemaOptions() {
  selectedSchema.value?.fields.forEach((f) => {
    if (f.type === 'select' && f.optionsKey) loadOptions(f.optionsKey)
  })
}

/** 项目认可的 Xray 稳定版; 升级时改这里. (后端不再有 fallback, 必须前端传值) */
const XRAY_DEFAULT_VERSION = 'v26.3.27'

const XRAY_VERSION_OPTIONS = [
  { label: `${XRAY_DEFAULT_VERSION} (稳定版, 推荐)`, value: XRAY_DEFAULT_VERSION },
  { label: 'latest (最新, 风险自负)', value: 'latest' }
]

/** 弹框表单态: 通用字段 (版本/开关/端口/协议); 协议特定字段在 paramsForm (schema 驱动). */
interface InstallFormState {
  xrayVersion: string
  enableOnBoot: boolean
  forceReinstall: boolean
  installUfw: boolean
  setTimezone: boolean
  logRotate: boolean
  protocol: string
  sharedInboundPort: number
}

const form = reactive<InstallFormState>({
  xrayVersion: XRAY_DEFAULT_VERSION,
  enableOnBoot: true,
  forceReinstall: false,
  installUfw: true,
  setTimezone: true,
  logRotate: true,
  protocol: 'vmess',
  sharedInboundPort: 443
})

/** 协议特定字段值 (key = schema 字段 name); schema 驱动动态渲染 + 提交 inbound.params. */
const paramsForm = reactive<Record<string, unknown>>({})

const advancedOpen = ref(false)

/** 用选中协议的 schema 默认值重置 paramsForm. */
function resetParamsForm() {
  Object.keys(paramsForm).forEach((k) => delete paramsForm[k])
  selectedSchema.value?.fields.forEach((f) => {
    paramsForm[f.name] = f.defaultValue ?? (f.type === 'number' ? null : '')
  })
}

/** 用户切协议: 重置该协议字段默认 + 拉候选. */
function onProtocolChange(p: string) {
  form.protocol = p
  resetParamsForm()
  loadSchemaOptions()
}

/** 重装态 (有 prefill = 从已装机器进来); 用于提示客户面改动会要求在用客户重拉订阅. */
const isReinstall = computed(() => !!props.prefill)

watch(
  () => [props.modelValue, props.server?.id],
  async ([open]) => {
    if (!open) return
    Object.keys(errors).forEach((k) => delete errors[k])
    output.value = ''
    advancedOpen.value = false
    pickedServer.value = null
    await loadProtocolSchemas()
    form.xrayVersion = (props.prefill?.xrayVersion as string) || XRAY_DEFAULT_VERSION
    // 协议: 重装用 prefill, 否则默认第一个
    form.protocol = (props.prefill?.protocol as string) || protocolSchemas.value[0]?.protocol || 'vmess'
    form.sharedInboundPort = (props.prefill?.sharedInboundPort as number) ?? 443
    resetParamsForm()
    // 重装: 用详情 formValues 覆盖默认
    const fv = props.prefill?.formValues as Record<string, unknown> | undefined
    if (fv) {
      Object.entries(fv).forEach(([k, v]) => {
        if (v !== null && v !== undefined) paramsForm[k] = v
      })
    }
    loadSchemaOptions()
    // server 没传时, 进弹框先拉一次列表给 NSelect 用
    if (!props.server) {
      loadServerOptions()
    }
  }
)

function validate() {
  Object.keys(errors).forEach((k) => delete errors[k])
  if (form.sharedInboundPort < 1 || form.sharedInboundPort > 65535) {
    errors.sharedInboundPort = '端口范围 1-65535'
  }
  selectedSchema.value?.fields.forEach((f) => {
    const v = paramsForm[f.name]
    const blank = v === undefined || v === null || v === ''
    // 静态必填 或 条件必填 (requiredWhenField 非空时本字段才必填)
    const required = f.required || (!!f.requiredWhenField && !!paramsForm[f.requiredWhenField])
    if (required && blank) {
      errors[f.name] = `${f.label} 必填`
      return
    }
    if (!blank && f.pattern && typeof v === 'string' && !new RegExp(f.pattern).test(v)) {
      errors[f.name] = `${f.label} 格式不正确`
    }
  })
  return Object.keys(errors).length === 0
}

async function onSubmit() {
  if (!validate()) return
  const target = effectiveServer.value
  if (!target) {
    message.warning('请先选择目标服务器')
    return
  }
  const ok = await confirm({
    title: '部署 Xray',
    message: `在 ${target.name} 部署 Xray ${form.xrayVersion}?`,
    type: 'warning',
    confirmText: '开始部署'
  })
  if (!ok) return
  installing.value = true
  output.value = ''
  abortCtrl = new AbortController()
  try {
    // 协议特定字段整体进 params (后端按 protocol 多态绑定); 空串原样传 (后端按 blank 判)
    const params: Record<string, unknown> = {}
    selectedSchema.value?.fields.forEach((f) => {
      const v = paramsForm[f.name]
      params[f.name] = typeof v === 'string' ? v.trim() : v
    })
    const dto: LineServerInstallDTO = {
      xrayVersion: form.xrayVersion,
      enableOnBoot: form.enableOnBoot,
      forceReinstall: form.forceReinstall,
      installUfw: form.installUfw,
      setTimezone: form.setTimezone,
      logRotate: form.logRotate,
      inbound: {
        protocol: form.protocol,
        sharedInboundPort: form.sharedInboundPort,
        params
      }
    }
    await xrayInstallStream(target.id, dto, appendOutput, abortCtrl.signal)
    message.success('部署完成')
    emit('installed')
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

// 剥 ANSI 颜色码 (\x1b[0;32m 等), 远端 apt/Xray 安装等命令可能带颜色
const ANSI_RE = /\x1b\[[0-9;?]*[A-Za-z]/g

function appendOutput(chunk: string) {
  output.value += chunk.replace(ANSI_RE, '')
  // 下一帧滚到底, 让用户始终看到最新一行
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

/** select 字段是否允许自定义输入 + 是否可清除 (派生自 schema). */
function fieldSelectProps(f: InboundFieldSchema) {
  return { tag: !!f.allowCustom, clearable: !f.required }
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
        <span>一键部署 Xray</span>
      </div>
    </template>
    <template #header-extra>
      <span v-if="effectiveServer" class="text-xs text-zinc-500">
        {{ effectiveServer.name }}
      </span>
    </template>

    <NForm
      :model="form"
      label-placement="top"
      require-mark-placement="right-hanging"
      size="small"
    >
      <!-- 重装提示: 已预填当前配置; 改客户面参数会要求在用客户重拉订阅 -->
      <div
        v-if="isReinstall"
        class="mb-3 px-3 py-2 rounded text-xs border bg-amber-50 dark:bg-amber-900/20 text-amber-700 dark:text-amber-300 border-amber-200 dark:border-amber-800"
      >
        重装已沿用当前装机配置,只改你需要改的。变更域名 / 端口 / wsPath 等客户面参数后,该机在用客户需<strong>重新拉取订阅</strong>才能恢复连接。
      </div>

      <!-- ===== 目标服务器 (没传 server prop 时显示选择器) ===== -->
      <NFormItem v-if="!server" required>
        <template #label>
          <span>目标服务器</span>
          <span class="text-xs text-zinc-400 ml-2">从已登记的服务器中选择</span>
        </template>
        <NSelect
          :value="pickedServer?.id ?? null"
          :options="serverOptions"
          :loading="serversLoading"
          :disabled="installing"
          filterable
          placeholder="选择要部署/重装 Xray 的服务器"
          @update:value="onPickServer"
        />
      </NFormItem>

      <!-- ===== 基础参数 (安装目录/端口/路径/日志 等由后端 XrayInstallDefaults 固定, 前端不再展示) ===== -->
      <div class="grid grid-cols-1 sm:grid-cols-2 gap-x-4">
        <NFormItem required>
          <template #label>
            <span>Xray 版本</span>
            <span class="text-xs text-zinc-400 ml-2">推荐稳定版; 安装目录 / 端口 / 路径由后端固定</span>
          </template>
          <NSelect
            v-model:value="form.xrayVersion"
            :options="XRAY_VERSION_OPTIONS"
            :disabled="installing"
          />
        </NFormItem>
      </div>

      <div class="text-sm font-semibold mt-4 mb-2">
        共享 inbound
        <span class="text-xs text-zinc-400 ml-2 font-normal">所有客户共用一个端口; 协议字段由后端 schema 驱动</span>
      </div>
      <div class="grid grid-cols-1 sm:grid-cols-2 gap-x-4">
        <NFormItem required>
          <template #label>
            <span>协议</span>
            <span class="text-xs text-zinc-400 ml-2">加协议后端自动出现在这里</span>
          </template>
          <NSelect
            :value="form.protocol"
            :options="protocolOptions"
            :disabled="installing"
            @update:value="onProtocolChange"
          />
        </NFormItem>

        <NFormItem
          required
          :validation-status="errors.sharedInboundPort ? 'error' : undefined"
          :feedback="errors.sharedInboundPort"
        >
          <template #label>
            <span>监听端口</span>
            <span class="text-xs text-zinc-400 ml-2">默认 443</span>
          </template>
          <NInputNumber
            v-model:value="form.sharedInboundPort"
            :min="1"
            :max="65535"
            :disabled="installing"
            class="w-full"
          />
        </NFormItem>
      </div>

      <!-- ===== 协议特定字段 (schema 驱动动态渲染) ===== -->
      <div class="grid grid-cols-1 sm:grid-cols-2 gap-x-4">
        <NFormItem
          v-for="field in selectedSchema?.fields || []"
          :key="field.name"
          :required="field.required"
          :validation-status="errors[field.name] ? 'error' : undefined"
          :feedback="errors[field.name]"
        >
          <template #label>
            <span>{{ field.label }}</span>
          </template>
          <NInputNumber
            v-if="field.type === 'number'"
            v-model:value="(paramsForm[field.name] as number | null)"
            :disabled="installing"
            class="w-full"
          />
          <NSelect
            v-else-if="field.type === 'select'"
            v-model:value="(paramsForm[field.name] as string | null)"
            :options="optionsCache[field.optionsKey || ''] || []"
            :disabled="installing"
            filterable
            :tag="fieldSelectProps(field).tag"
            :clearable="fieldSelectProps(field).clearable"
            :placeholder="field.placeholder"
          />
          <NInput
            v-else
            v-model:value="(paramsForm[field.name] as string)"
            :disabled="installing"
            :placeholder="field.placeholder"
            :input-props="{ style: 'font-family: monospace' }"
          />
        </NFormItem>
      </div>

      <!-- ===== 高级设置 (默认折叠) ===== -->
      <div class="mt-2 pt-3 border-t border-zinc-200 dark:border-zinc-700">
        <button
          type="button"
          class="flex items-center gap-1 text-sm font-semibold text-zinc-500 hover:text-zinc-700 dark:hover:text-zinc-300 mb-2"
          :disabled="installing"
          @click="advancedOpen = !advancedOpen"
        >
          <NIcon :size="14">
            <ChevronDown v-if="advancedOpen" />
            <ChevronRight v-else />
          </NIcon>
          高级设置
        </button>

        <div v-if="advancedOpen" class="grid grid-cols-1 sm:grid-cols-2 gap-x-4 gap-y-1">
          <div class="sm:col-span-2 grid grid-cols-1 sm:grid-cols-2 gap-x-4 gap-y-2 mt-1">
            <NCheckbox v-model:checked="form.enableOnBoot" :disabled="installing">
              开机自启 Xray
            </NCheckbox>
            <NCheckbox v-model:checked="form.forceReinstall" :disabled="installing">
              强制重装
            </NCheckbox>
            <NCheckbox v-model:checked="form.installUfw" :disabled="installing">
              配置 UFW 防火墙 (22 + 共享 inbound 端口)
            </NCheckbox>
            <NCheckbox v-model:checked="form.setTimezone" :disabled="installing">
              设置时区 Asia/Shanghai
            </NCheckbox>
            <NCheckbox
              v-model:checked="form.logRotate"
              :disabled="installing"
              title="size-based 滚 + gzip 压缩 + copytruncate 0 中断; 低配机推荐开启避免日志填满磁盘"
            >
              日志轮转 (access 50M + error 10M 自动滚)
            </NCheckbox>
          </div>
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
      ><code v-if="output">{{ output }}</code><span v-else class="text-zinc-500">{{ installing ? '准备中...' : '远端 stdout 实时输出' }}</span></pre>
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
          {{ installing ? '部署中...' : '开始部署' }}
        </NButton>
      </NSpace>
    </template>
  </NModal>
</template>
