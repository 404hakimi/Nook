<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ChevronDown, ChevronRight, Rocket, Shuffle } from 'lucide-vue-next'
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
import { xrayInstallStream, listRealityDest, type LineServerInstallDTO } from '@/api/xray/xray-install'
import { pageServers } from '@/api/resource/server'
import { listSystemDomain, type SystemDomain } from '@/api/system/domain'

/** ServerInstallDialog 仅需 server.id + server.name; 实际可接受任何含 id/name 的形态 (ResourceServer / ServerFrontlineListItem). */
interface ServerTarget { id: string; name: string }

interface Props {
  modelValue: boolean
  server?: ServerTarget | null
  /** 重装时用当前装机配置预填整张表单 (wsPath/端口/路径/协议/域名等); 新装传空走默认 + 随机. */
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

// ===== 域名下拉 (system_domain; 选了 = 走 TLS, 留空 = 纯 vmess+ws) =====
const domains = ref<SystemDomain[]>([])
const domainsLoading = ref(false)
// REALITY dest 候选 (vless 协议下拉)
const realityDestOptions = ref<{ label: string; value: string }[]>([])
async function loadRealityDest() {
  try {
    const list = await listRealityDest()
    realityDestOptions.value = list.map((d) => ({ label: d.label, value: d.value }))
  } catch {
    realityDestOptions.value = []
  }
}
const domainOptions = computed(() => domains.value.map((d) => ({ label: d.domain, value: d.id })))
async function loadDomains() {
  domainsLoading.value = true
  try {
    domains.value = await listSystemDomain()
  } catch {
    domains.value = []
  } finally {
    domainsLoading.value = false
  }
}

/** 项目认可的 Xray 稳定版; 升级时改这里. (后端不再有 fallback, 必须前端传值) */
const XRAY_DEFAULT_VERSION = 'v26.3.27'

const XRAY_VERSION_OPTIONS = [
  { label: `${XRAY_DEFAULT_VERSION} (稳定版, 推荐)`, value: XRAY_DEFAULT_VERSION },
  { label: 'latest (最新, 风险自负)', value: 'latest' }
]

/** 随机生成 16 字符 ws path (16^16 取一段); 减少同节点 path 撞车 / 被识别概率 */
function randomWsPath(): string {
  const buf = new Uint8Array(8)
  crypto.getRandomValues(buf)
  return '/' + Array.from(buf, (b) => b.toString(16).padStart(2, '0')).join('')
}

const form = reactive<LineServerInstallDTO>({
  xrayVersion: XRAY_DEFAULT_VERSION,
  enableOnBoot: true,
  forceReinstall: false,
  installUfw: true,
  setTimezone: true,
  logRotate: true,
  // 协议: vmess+ws (绑域名走 tls) 或 vless+reality; transport 随协议联动
  protocol: 'vmess',
  transport: 'ws',
  listenIp: '0.0.0.0',
  realityDest: undefined,
  sharedInboundPort: 443,
  wsPath: randomWsPath(),
  // 域名绑定: 根域 system_domain.id + 二级标签; 选了走 TLS, 留空则 xray 退化纯 vmess+ws
  domainId: undefined,
  subdomain: ''
})

const advancedOpen = ref(false)

/** 重装: 用当前装机配置覆盖表单 (只覆盖有值的字段, 未持久化项保留默认); 新装无 prefill 保持默认 + 随机 wsPath. */
function applyPrefill() {
  const p = props.prefill
  if (!p) return
  const clean: Record<string, unknown> = {}
  for (const [k, v] of Object.entries(p)) {
    if (v !== undefined && v !== null && v !== '') clean[k] = v
  }
  Object.assign(form, clean)
}

/** 重装态 (有 prefill = 从已装机器进来); 用于提示客户面改动会要求在用客户重拉订阅. */
const isReinstall = computed(() => !!props.prefill)

/** vless 协议走 reality (tcp, 不绑域名); vmess 走 ws. */
const isReality = computed(() => form.protocol === 'vless')
watch(
  () => form.protocol,
  (p) => {
    form.transport = p === 'vless' ? 'tcp' : 'ws'
  }
)

/** 完整 FQDN 预览 = 二级标签 + 选中根域; 缺任一则空. */
const fqdnPreview = computed(() => {
  const sub = (form.subdomain || '').trim()
  const root = domains.value.find((d) => d.id === form.domainId)?.domain
  return form.domainId && root && sub ? `${sub}.${root}` : ''
})

watch(
  () => [props.modelValue, props.server?.id],
  ([open]) => {
    if (!open) return
    Object.keys(errors).forEach((k) => delete errors[k])
    output.value = ''
    advancedOpen.value = false
    pickedServer.value = null
    applyPrefill()
    loadDomains()
    loadRealityDest()
    // server 没传时, 进弹框先拉一次列表给 NSelect 用
    if (!props.server) {
      loadServerOptions()
    }
  }
)

function validate() {
  Object.keys(errors).forEach((k) => delete errors[k])
  const isRealityProto = form.protocol === 'vless'
  if (form.sharedInboundPort < 1 || form.sharedInboundPort > 65535) {
    errors.sharedInboundPort = '端口范围 1-65535'
  }
  if (isRealityProto) {
    // vless+reality 必须选偷取目标站; ws path 在 reality 下隐藏, 不校验
    if (!form.realityDest) {
      errors.realityDest = '请选择 REALITY 目标站'
    }
  } else if (!form.wsPath || !form.wsPath.startsWith('/') || !/^\/[A-Za-z0-9_\-/]{0,127}$/.test(form.wsPath)) {
    errors.wsPath = '必须 / 开头, 仅字母数字_-/'
  }
  // 选了根域 (domainId) 则二级域名必填; 都不选则不走域名 + TLS
  if (form.domainId && !form.subdomain?.trim()) {
    errors.subdomain = '选了根域后, 二级域名必填'
  } else if (form.subdomain?.trim() && !/^(?!-)[A-Za-z0-9-]{1,63}(?<!-)(\.(?!-)[A-Za-z0-9-]{1,63}(?<!-))*$/.test(form.subdomain.trim())) {
    errors.subdomain = '只能含字母数字与连字符 (可多级, 点分隔)'
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
    const dto: LineServerInstallDTO = {
      xrayVersion: form.xrayVersion,
      enableOnBoot: form.enableOnBoot,
      forceReinstall: form.forceReinstall,
      installUfw: form.installUfw,
      setTimezone: form.setTimezone,
      logRotate: form.logRotate,
      protocol: form.protocol,
      transport: form.protocol === 'vless' ? 'tcp' : form.transport,
      listenIp: form.listenIp,
      sharedInboundPort: form.sharedInboundPort,
      wsPath: form.wsPath.trim(),
      realityDest: form.protocol === 'vless' ? form.realityDest : undefined,
      // 选了根域走 TLS (根域 / CF Token 由 system_domain 提供, 二级标签拼 FQDN); 留空则后端 useTls=false
      domainId: form.domainId || undefined,
      subdomain: form.domainId ? (form.subdomain?.trim() || undefined) : undefined
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
        <span class="text-xs text-zinc-400 ml-2 font-normal">所有客户共用一个端口 + path; 协议 / 传输 / 监听 IP 当前固定, 协议适配后开放</span>
      </div>
      <div class="grid grid-cols-1 sm:grid-cols-3 gap-x-4">
        <NFormItem>
          <template #label>
            <span>协议</span>
            <span class="text-xs text-zinc-400 ml-2">vmess+ws 或 vless+reality</span>
          </template>
          <NSelect
            v-model:value="form.protocol"
            :options="[{ label: 'VMess + WS', value: 'vmess' }, { label: 'VLESS + REALITY', value: 'vless' }]"
            :disabled="installing"
          />
        </NFormItem>

        <NFormItem>
          <template #label>
            <span>传输</span>
            <span class="text-xs text-zinc-400 ml-2">随协议 (vmess=ws, reality=tcp)</span>
          </template>
          <NInput :value="form.transport" disabled :input-props="{ style: 'font-family: monospace' }" />
        </NFormItem>

        <NFormItem
          v-if="isReality"
          required
          :validation-status="errors.realityDest ? 'error' : undefined"
          :feedback="errors.realityDest"
        >
          <template #label>
            <span>REALITY 目标站</span>
            <span class="text-xs text-zinc-400 ml-2">客户端 SNI 伪装成它</span>
          </template>
          <NSelect
            v-model:value="form.realityDest"
            :options="realityDestOptions"
            :disabled="installing"
            placeholder="选偷取的目标真站"
          />
        </NFormItem>
        <NFormItem v-else>
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
          v-if="!isReality"
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

      <!-- ===== 域名绑定 (根域 system_domain + 二级标签; 选了走 TLS, 留空纯 vmess+ws) ===== -->
      <div v-if="!isReality" class="text-sm font-semibold mt-4 mb-2 flex items-center gap-3">
        <span>域名 + TLS</span>
        <span class="text-xs text-zinc-400 font-normal">
          选根域 + 填二级标签 = 走 CDN + acme.sh DNS-01 签发 LE 证书; 根域留空 = 不启用域名 (xray 退化纯 vmess+ws)
        </span>
      </div>
      <div v-if="!isReality" class="grid grid-cols-1 sm:grid-cols-2 gap-x-4">
        <NFormItem>
          <template #label>
            <span>根域名 (一级域名)</span>
            <span class="text-xs text-zinc-400 ml-2">在「系统配置 → 域名」预先登记; 含 CF Token</span>
          </template>
          <NSelect
            v-model:value="form.domainId"
            :options="domainOptions"
            :loading="domainsLoading"
            :disabled="installing"
            clearable
            filterable
            placeholder="选根域 (留空 = 不启用 TLS)"
          />
        </NFormItem>

        <NFormItem
          :validation-status="errors.subdomain ? 'error' : undefined"
          :feedback="errors.subdomain"
        >
          <template #label>
            <span>二级域名</span>
            <span class="text-xs text-zinc-400 ml-2">本机专属, 如 frontline-jp-1</span>
          </template>
          <NInput
            v-model:value="form.subdomain"
            :disabled="installing || !form.domainId"
            placeholder="frontline-jp-1"
            :input-props="{ style: 'font-family: monospace' }"
          />
        </NFormItem>
      </div>
      <div v-if="form.domainId" class="-mt-1 mb-1 text-xs text-zinc-500">
        完整域名: <code class="font-mono text-zinc-700 dark:text-zinc-300">{{ fqdnPreview || '（填二级域名）' }}</code>
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
