<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ChevronDown, ChevronRight, FolderOpen, Rocket, Shuffle } from 'lucide-vue-next'
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
import { pageServers } from '@/api/resource/server'

/** ServerInstallDialog 仅需 server.id + server.name; 实际可接受任何含 id/name 的形态 (ResourceServer / ServerFrontlineListItem). */
interface ServerTarget { id: string; name: string }

interface Props {
  modelValue: boolean
  server?: ServerTarget | null
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
/** systemd unit 文件路径默认值; 服务名 (basename = xray) 跟脚本 systemctl 命令绑死, 改路径要保留同 basename. */
const DEFAULT_SYSTEMD_UNIT_PATH = '/etc/systemd/system/xray.service'

/** Xray API loopback 端口随机区间; ≥20000 避开 well-known + 8080/8443 等常见占用. */
const XRAY_API_PORT_MIN = 20000
const XRAY_API_PORT_MAX = 65535

/** 随机生成 16 字符 ws path (16^16 取一段); 减少同节点 path 撞车 / 被识别概率 */
function randomWsPath(): string {
  const buf = new Uint8Array(8)
  crypto.getRandomValues(buf)
  return '/' + Array.from(buf, (b) => b.toString(16).padStart(2, '0')).join('')
}

/** 随机 xray API 端口 (20000-65535); 仅 127.0.0.1 监听, 给 xray api adi/rmi 调用用. */
function randomXrayApiPort(): number {
  const range = XRAY_API_PORT_MAX - XRAY_API_PORT_MIN + 1
  const buf = new Uint32Array(1)
  crypto.getRandomValues(buf)
  return XRAY_API_PORT_MIN + (buf[0] % range)
}

const form = reactive<LineServerInstallDTO>({
  xrayVersion: XRAY_DEFAULT_VERSION,
  installDir: DEFAULT_INSTALL_DIR,
  xrayBinaryPath: '',
  xrayConfigPath: '',
  xrayShareDir: '',
  xrayApiPort: randomXrayApiPort(),
  logDir: '',
  xraySystemdUnitPath: DEFAULT_SYSTEMD_UNIT_PATH,
  logLevel: 'warning',
  restartPolicy: 'on-failure',
  enableOnBoot: true,
  forceReinstall: false,
  installUfw: true,
  setTimezone: true,
  logRotate: true,
  // 部署期固定 vmess+ws+0.0.0.0; UI 置灰禁改, 协议适配阶段才放开
  protocol: 'vmess',
  transport: 'ws',
  listenIp: '0.0.0.0',
  sharedInboundPort: 443,
  wsPath: randomWsPath(),
  // 域名 + TLS 是当前唯一支持模式; 后端 useTls 字段恒为 true, UI 不再暴露开关
  useTls: true,
  domain: '',
  cfApiToken: '',
  tlsCertPath: '',
  tlsKeyPath: ''
})

/**
 * installDir 派生的全部约定路径; 用户没手动改 form 上对应字段时 placeholder + submit 都用这套.
 * "约定" 仅活在前端, 后端只 @NotBlank 校验 + 入库 + 透传给脚本, 完全不再派生.
 */
const derivedPaths = computed(() => {
  const d = (form.installDir || DEFAULT_INSTALL_DIR).replace(/\/+$/, '')
  // 路径布局 (扁平化, 都在 installDir 根下; 后端 50-xray.sh.tmpl 全部走 dto 透传):
  //   ${d}/bin/xray            二进制包
  //   ${d}/bin/                geo 数据 (geoip.dat / geosite.dat) 跟 binary 同目录
  //   ${d}/config.json         xray 主配置
  //   ${d}/access.log          xray log.access (logDir=${d})
  //   ${d}/error.log           xray log.error
  //   ${d}/tls/                cert + key
  return {
    xrayBinaryPath: `${d}/bin/xray`,
    xrayConfigPath: `${d}/config.json`,
    xrayShareDir: `${d}/bin`,
    logDir: d,
    tlsCertPath: `${d}/tls/cert.pem`,
    tlsKeyPath: `${d}/tls/key.pem`
  }
})

/** 安装路径预览; 表单字段空时回落到 derivedPaths, 跟实际提交对齐. */
const installPaths = computed(() => {
  const d = derivedPaths.value
  const log = form.logDir.trim() || d.logDir
  return [
    { label: '二进制包', path: form.xrayBinaryPath?.trim() || d.xrayBinaryPath },
    { label: 'config', path: form.xrayConfigPath?.trim() || d.xrayConfigPath },
    { label: 'share', path: (form.xrayShareDir?.trim() || d.xrayShareDir) + '  (geo 数据)' },
    { label: 'log', path: `${log}/{access,error}.log` },
    { label: 'systemd', path: form.xraySystemdUnitPath?.trim() || DEFAULT_SYSTEMD_UNIT_PATH }
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
  if (form.xrayApiPort < XRAY_API_PORT_MIN || form.xrayApiPort > XRAY_API_PORT_MAX) {
    errors.xrayApiPort = `端口范围 ${XRAY_API_PORT_MIN}-${XRAY_API_PORT_MAX}`
  }
  // logDir 留空 OK (后端派生); 给了就必须绝对路径
  if (form.logDir.trim() && !form.logDir.startsWith('/')) {
    errors.logDir = '必须以 / 开头的绝对路径 (留空走默认 <installDir>/logs)'
  }
  if (form.sharedInboundPort < 1 || form.sharedInboundPort > 65535) {
    errors.sharedInboundPort = '端口范围 1-65535'
  }
  if (!form.wsPath || !form.wsPath.startsWith('/') || !/^\/[A-Za-z0-9_\-/]{0,127}$/.test(form.wsPath)) {
    errors.wsPath = '必须 / 开头, 仅字母数字_-/'
  }
  // 走域名 + TLS 是当前唯一支持模式, domain / cfApiToken 必填
  if (!form.domain?.trim()) {
    errors.domain = '必填 (CDN CNAME 指向; 部署后申请 LE 证书走 CF DNS-01)'
  } else if (!/^([a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\.)+[a-zA-Z]{2,}$/.test(form.domain.trim())) {
    errors.domain = '域名格式非法'
  }
  if (!form.cfApiToken?.trim()) {
    errors.cfApiToken = '必填 (acme.sh DNS-01 签发证书用; 远端 cert 仍有效时脚本会自动跳过 acme)'
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
    // 全部路径字段空 → 用前端约定派生值兜底; 真正发到后端的是绝对路径, 后端 @NotBlank 校验后透传脚本 + 入库
    const d = derivedPaths.value
    const dto: LineServerInstallDTO = {
      xrayVersion: form.xrayVersion,
      installDir: form.installDir.trim(),
      xrayBinaryPath: form.xrayBinaryPath?.trim() || d.xrayBinaryPath,
      xrayConfigPath: form.xrayConfigPath?.trim() || d.xrayConfigPath,
      xrayShareDir: form.xrayShareDir?.trim() || d.xrayShareDir,
      xrayApiPort: form.xrayApiPort,
      logDir: form.logDir.trim() || d.logDir,
      xraySystemdUnitPath: form.xraySystemdUnitPath?.trim() || DEFAULT_SYSTEMD_UNIT_PATH,
      logLevel: form.logLevel,
      restartPolicy: form.restartPolicy,
      enableOnBoot: form.enableOnBoot,
      forceReinstall: form.forceReinstall,
      installUfw: form.installUfw,
      setTimezone: form.setTimezone,
      logRotate: form.logRotate,
      protocol: form.protocol,
      transport: form.transport,
      listenIp: form.listenIp,
      sharedInboundPort: form.sharedInboundPort,
      wsPath: form.wsPath.trim(),
      // 当前只支持 useTls=true + domain + CF Token; 三者已在 validate 强制必填
      useTls: true,
      domain: form.domain?.trim() ?? '',
      cfApiToken: form.cfApiToken?.trim() ?? '',
      tlsCertPath: form.tlsCertPath?.trim() || d.tlsCertPath,
      tlsKeyPath: form.tlsKeyPath?.trim() || d.tlsKeyPath
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
            <span class="text-xs text-zinc-400 ml-2">二进制包 / 配置 / 共享数据 全在此目录下</span>
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
            <span class="text-xs text-zinc-400 ml-2">仅 127.0.0.1; xray api adi/rmi 用; 进 dialog 自动随机 {{ XRAY_API_PORT_MIN }}-{{ XRAY_API_PORT_MAX }}</span>
            <NButton
              text
              size="tiny"
              class="ml-2"
              :disabled="installing"
              @click="form.xrayApiPort = randomXrayApiPort()"
            >
              <template #icon><NIcon><Shuffle /></NIcon></template>
              重新随机
            </NButton>
          </template>
          <NInputNumber
            v-model:value="form.xrayApiPort"
            :min="XRAY_API_PORT_MIN"
            :max="XRAY_API_PORT_MAX"
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
            :placeholder="derivedPaths.logDir || '/home/xray/logs'"
            :input-props="{ style: 'font-family: monospace' }"
          />
        </NFormItem>
      </div>

      <div class="text-sm font-semibold mt-4 mb-2">
        共享 inbound
        <span class="text-xs text-zinc-400 ml-2 font-normal">所有客户共用一个端口 + path; 协议 / 传输 / 监听 IP 当前固定, 协议适配后开放</span>
      </div>
      <div class="grid grid-cols-1 sm:grid-cols-3 gap-x-4">
        <NFormItem>
          <template #label>
            <span>协议</span>
            <span class="text-xs text-zinc-400 ml-2">固定 vmess</span>
          </template>
          <NInput :value="form.protocol" disabled :input-props="{ style: 'font-family: monospace' }" />
        </NFormItem>

        <NFormItem>
          <template #label>
            <span>传输</span>
            <span class="text-xs text-zinc-400 ml-2">固定 ws</span>
          </template>
          <NInput :value="form.transport" disabled :input-props="{ style: 'font-family: monospace' }" />
        </NFormItem>

        <NFormItem>
          <template #label>
            <span>监听 IP</span>
            <span class="text-xs text-zinc-400 ml-2">固定 0.0.0.0</span>
          </template>
          <NInput :value="form.listenIp" disabled :input-props="{ style: 'font-family: monospace' }" />
        </NFormItem>
      </div>
      <div class="grid grid-cols-1 sm:grid-cols-2 gap-x-4">
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

        <NFormItem
          required
          :validation-status="errors.wsPath ? 'error' : undefined"
          :feedback="errors.wsPath"
        >
          <template #label>
            <span>WS Path</span>
            <NButton
              text
              size="tiny"
              class="ml-2"
              :disabled="installing"
              @click="form.wsPath = randomWsPath()"
            >
              <template #icon><NIcon><Shuffle /></NIcon></template>
              重新随机
            </NButton>
          </template>
          <NInput
            v-model:value="form.wsPath"
            placeholder="/abc123"
            :disabled="installing"
            :input-props="{ style: 'font-family: monospace' }"
          />
        </NFormItem>
      </div>

      <!-- ===== 域名 + TLS (当前唯一支持模式) ===== -->
      <div class="text-sm font-semibold mt-4 mb-2 flex items-center gap-3">
        <span>域名 + TLS</span>
        <span class="text-xs text-zinc-400 font-normal">
          走 CDN CNAME, acme.sh DNS-01 申请 LE 证书; 不再支持 IP:port 直连模式
        </span>
      </div>
      <div class="grid grid-cols-1 sm:grid-cols-2 gap-x-4">
        <NFormItem
          required
          :validation-status="errors.domain ? 'error' : undefined"
          :feedback="errors.domain"
        >
          <template #label>
            <span>对外域名</span>
            <span class="text-xs text-zinc-400 ml-2">CDN CNAME 指向</span>
          </template>
          <NInput
            v-model:value="form.domain"
            placeholder="server01.example.com"
            :disabled="installing"
            :input-props="{ style: 'font-family: monospace' }"
          />
        </NFormItem>

        <NFormItem
          required
          :validation-status="errors.cfApiToken ? 'error' : undefined"
          :feedback="errors.cfApiToken || 'acme.sh DNS-01 签发证书用; 远端 cert 仍有效时脚本会自动跳过 acme 不烧 LE 周配额'"
        >
          <template #label>
            <span>Cloudflare API Token</span>
            <span class="text-xs text-zinc-400 ml-2">Zone:Read + DNS:Edit</span>
          </template>
          <NInput
            v-model:value="form.cfApiToken"
            type="password"
            show-password-on="click"
            placeholder="CF API Token"
            :disabled="installing"
            :input-props="{ autocomplete: 'new-password' }"
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
