<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ChevronDown, ChevronRight, FolderOpen, Rocket } from 'lucide-vue-next'
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
import { pageServers, type ResourceServer } from '@/api/resource/server'

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

// server 没传时弹框内自带选择器 (Xray 节点页顶部"部署/重装"入口走这条路径);
// server 传了直接锁定 (服务器列表行操作进来走这条路径, 当前已搬走)
const pickedServer = ref<ResourceServer | null>(null)
const serverOptions = ref<{ label: string; value: string; raw: ResourceServer }[]>([])
const serversLoading = ref(false)
const effectiveServer = computed<ResourceServer | null>(
  () => props.server ?? pickedServer.value
)

async function loadServerOptions() {
  if (serversLoading.value) return
  serversLoading.value = true
  try {
    // 上限 200 个; 真有这么多 server 时再加分页选择器
    const res = await pageServers({ pageNo: 1, pageSize: 200 })
    serverOptions.value = res.records.map((s) => ({
      label: `${s.name} (${s.host})`,
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

const TIMEZONE_OPTIONS = [
  { label: '不修改 (skip)', value: 'skip' },
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

/** 项目认可的 Xray 稳定版; 升级时改这里. (后端不再有 fallback, 必须前端传值) */
const XRAY_DEFAULT_VERSION = 'v26.3.27'

const XRAY_VERSION_OPTIONS = [
  { label: `${XRAY_DEFAULT_VERSION} (稳定版, 推荐)`, value: XRAY_DEFAULT_VERSION },
  { label: 'latest (最新, 风险自负)', value: 'latest' }
]

const LOG_LEVEL_OPTIONS = [
  { label: 'warning (默认, 仅异常记录)', value: 'warning' as const },
  { label: 'info (含 access log 行级别)', value: 'info' as const },
  { label: 'debug (排障用, 噪声大)', value: 'debug' as const },
  { label: 'error (仅错误)', value: 'error' as const },
  { label: 'none (关日志)', value: 'none' as const }
]

const RESTART_POLICY_OPTIONS = [
  { label: 'on-failure (默认, 仅非 0 退出码重启)', value: 'on-failure' as const },
  { label: 'always (任何停止都重启, 含 OOM/手动 stop)', value: 'always' as const },
  { label: 'no (不自动重启, 调试用)', value: 'no' as const }
]

const DEFAULT_INSTALL_DIR = '/home/xray'

const form = reactive<LineServerInstallDTO>({
  xrayVersion: XRAY_DEFAULT_VERSION,
  installDir: DEFAULT_INSTALL_DIR,
  slotPortBase: 30000,
  slotPoolSize: 50,
  xrayApiPort: 8080,
  logDir: '',
  logLevel: 'warning',
  restartPolicy: 'on-failure',
  enableOnBoot: true,
  forceReinstall: false,
  installUfw: true,
  timezone: 'Asia/Shanghai'
})

/** logDir 派生值 (installDir 末尾去掉多余 / 后拼 /logs); placeholder + 提交派生都用 */
const derivedLogDir = computed(() => {
  const d = form.installDir.replace(/\/+$/, '')
  return d ? `${d}/logs` : ''
})

/** 安装路径展示块: 只读, 跟随 installDir 实时联动 */
const installPaths = computed(() => {
  const d = form.installDir.replace(/\/+$/, '') || DEFAULT_INSTALL_DIR
  const log = form.logDir.trim() || derivedLogDir.value
  return [
    { label: 'binary', path: `${d}/bin/xray` },
    { label: 'config', path: `${d}/etc/xray/config.json` },
    { label: 'share', path: `${d}/share/xray/  (geo 数据)` },
    { label: 'log', path: log ? `${log}/{access,error}.log` : '-' },
    { label: 'systemd', path: '/etc/systemd/system/xray.service  (固定)' },
    { label: 'PATH 软链', path: '/usr/local/bin/xray → ' + `${d}/bin/xray` }
  ]
})

const advancedOpen = ref(false)

watch(
  () => [props.modelValue, props.server?.id],
  ([open]) => {
    if (!open) return
    Object.keys(errors).forEach((k) => delete errors[k])
    output.value = ''
    advancedOpen.value = false
    pickedServer.value = null
    // server 没传时, 进弹框先拉一次列表给 NSelect 用
    if (!props.server) {
      loadServerOptions()
    }
  }
)

function validate() {
  Object.keys(errors).forEach((k) => delete errors[k])
  // installDir 必须绝对路径; 黑名单后端兜底校验, 前端只防低级错误
  if (!form.installDir.trim() || !form.installDir.startsWith('/')) {
    errors.installDir = '必须以 / 开头的绝对路径'
  }
  if (form.xrayApiPort < 1 || form.xrayApiPort > 65535) errors.xrayApiPort = '端口范围 1-65535'
  if (form.slotPortBase < 1024 || form.slotPortBase > 60000) errors.slotPortBase = '端口范围 1024-60000'
  if (form.slotPoolSize < 1 || form.slotPoolSize > 200) errors.slotPoolSize = '槽位数 1-200'
  // slot 端口段不能覆盖 xray api 端口
  const slotEnd = form.slotPortBase + form.slotPoolSize
  if (form.xrayApiPort >= form.slotPortBase && form.xrayApiPort <= slotEnd) {
    errors.xrayApiPort = `不能落在 slot 端口段 ${form.slotPortBase}-${slotEnd} 内`
  }
  // logDir 留空 OK (后端派生); 给了就必须绝对路径
  if (form.logDir.trim() && !form.logDir.startsWith('/')) {
    errors.logDir = '必须以 / 开头的绝对路径 (留空走默认 <installDir>/logs)'
  }
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
    // logDir 留空走后端派生 (<installDir>/logs); trim 后空字符串原样传, 后端识别空字符串 = 派生
    const dto: LineServerInstallDTO = {
      xrayVersion: form.xrayVersion,
      installDir: form.installDir.trim(),
      slotPortBase: form.slotPortBase,
      slotPoolSize: form.slotPoolSize,
      xrayApiPort: form.xrayApiPort,
      logDir: form.logDir.trim(),
      logLevel: form.logLevel,
      restartPolicy: form.restartPolicy,
      enableOnBoot: form.enableOnBoot,
      forceReinstall: form.forceReinstall,
      installUfw: form.installUfw,
      timezone: form.timezone || 'skip'
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
        <span>一键部署 Xray (1:1 + slot 模型)</span>
      </div>
    </template>
    <template #header-extra>
      <span v-if="effectiveServer" class="text-xs text-zinc-500">
        {{ effectiveServer.name }} ({{ effectiveServer.host }})
      </span>
    </template>

    <NForm
      :model="form"
      label-placement="top"
      require-mark-placement="right-hanging"
      size="small"
    >
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

      <!-- ===== 基础参数 ===== -->
      <div class="grid grid-cols-1 sm:grid-cols-2 gap-x-4">
        <NFormItem required>
          <template #label>
            <span>Xray 版本</span>
            <span class="text-xs text-zinc-400 ml-2">推荐稳定版</span>
          </template>
          <NSelect
            v-model:value="form.xrayVersion"
            :options="XRAY_VERSION_OPTIONS"
            :disabled="installing"
          />
        </NFormItem>
        <NFormItem
          required
          :validation-status="errors.installDir ? 'error' : undefined"
          :feedback="errors.installDir"
        >
          <template #label>
            <span>安装目录</span>
            <span class="text-xs text-zinc-400 ml-2">binary / config / share 全在此目录下</span>
          </template>
          <NInput
            v-model:value="form.installDir"
            :disabled="installing"
            placeholder="/home/xray"
            :input-props="{ style: 'font-family: monospace' }"
          />
        </NFormItem>

        <NFormItem
          required
          :validation-status="errors.xrayApiPort ? 'error' : undefined"
          :feedback="errors.xrayApiPort"
        >
          <template #label>
            <span>Xray API 端口</span>
            <span class="text-xs text-zinc-400 ml-2">仅 127.0.0.1; xray api adi/rmi 用</span>
          </template>
          <NInputNumber
            v-model:value="form.xrayApiPort"
            :min="1"
            :max="65535"
            :disabled="installing"
            class="w-full"
          />
        </NFormItem>
        <NFormItem
          :validation-status="errors.logDir ? 'error' : undefined"
          :feedback="errors.logDir"
        >
          <template #label>
            <span>日志目录</span>
            <span class="text-xs text-zinc-400 ml-2">留空走默认 (派生自安装目录)</span>
          </template>
          <NInput
            v-model:value="form.logDir"
            :disabled="installing"
            :placeholder="derivedLogDir || '/home/xray/logs'"
            :input-props="{ style: 'font-family: monospace' }"
          />
        </NFormItem>

        <NFormItem
          required
          :validation-status="errors.slotPortBase ? 'error' : undefined"
          :feedback="errors.slotPortBase"
        >
          <template #label>
            <span>Slot 端口段起点</span>
            <span class="text-xs text-zinc-400 ml-2">每客户独享一个端口</span>
          </template>
          <NInputNumber
            v-model:value="form.slotPortBase"
            :min="1024"
            :max="60000"
            :disabled="installing"
            class="w-full"
          />
        </NFormItem>
        <NFormItem
          required
          :validation-status="errors.slotPoolSize ? 'error' : undefined"
          :feedback="errors.slotPoolSize"
        >
          <template #label>
            <span>Slot 池大小</span>
            <span class="text-xs text-zinc-400 ml-2">该 server 客户上限 (含冗余)</span>
          </template>
          <NInputNumber
            v-model:value="form.slotPoolSize"
            :min="1"
            :max="200"
            :disabled="installing"
            class="w-full"
          />
        </NFormItem>
      </div>

      <!-- ===== 安装路径只读展示 (跟随 installDir 实时联动) ===== -->
      <div class="mt-2 mb-4 p-3 rounded bg-zinc-50 dark:bg-zinc-800/50 border border-zinc-200 dark:border-zinc-700">
        <div class="flex items-center gap-1 text-xs font-semibold text-zinc-500 mb-2">
          <NIcon :size="14"><FolderOpen /></NIcon>
          安装路径预览
        </div>
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-1 text-xs">
          <div v-for="p in installPaths" :key="p.label" class="flex items-baseline gap-2">
            <span class="text-zinc-500 w-16 flex-shrink-0">{{ p.label }}</span>
            <span class="font-mono text-zinc-700 dark:text-zinc-300 break-all">{{ p.path }}</span>
          </div>
        </div>
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
          <NFormItem>
            <template #label>
              <span>日志级别</span>
              <span class="text-xs text-zinc-400 ml-2">config.log.loglevel</span>
            </template>
            <NSelect
              v-model:value="form.logLevel"
              :options="LOG_LEVEL_OPTIONS"
              :disabled="installing"
            />
          </NFormItem>
          <NFormItem>
            <template #label>
              <span>Systemd 重启策略</span>
              <span class="text-xs text-zinc-400 ml-2">Restart=</span>
            </template>
            <NSelect
              v-model:value="form.restartPolicy"
              :options="RESTART_POLICY_OPTIONS"
              :disabled="installing"
            />
          </NFormItem>

          <NFormItem>
            <template #label>
              <span>时区</span>
              <span class="text-xs text-zinc-400 ml-2">影响日志/到期判定</span>
            </template>
            <NSelect
              v-model:value="form.timezone"
              :options="TIMEZONE_OPTIONS"
              :disabled="installing"
            />
          </NFormItem>

          <div class="sm:col-span-2 grid grid-cols-1 sm:grid-cols-2 gap-x-4 gap-y-2 mt-1">
            <NCheckbox v-model:checked="form.enableOnBoot" :disabled="installing">
              开机自启 Xray
            </NCheckbox>
            <NCheckbox v-model:checked="form.forceReinstall" :disabled="installing">
              强制重装
            </NCheckbox>
            <NCheckbox v-model:checked="form.installUfw" :disabled="installing">
              配置 UFW 防火墙 (22 + slot 端口段)
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
