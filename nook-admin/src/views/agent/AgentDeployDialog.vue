<script setup lang="ts">
import { computed, nextTick, onUnmounted, reactive, ref, watch } from 'vue'
import { ChevronDown, ChevronRight, HelpCircle, RefreshCcw, Rocket } from 'lucide-vue-next'
import {
  NAlert,
  NButton,
  NDivider,
  NForm,
  NFormItem,
  NIcon,
  NInput,
  NInputNumber,
  NModal,
  NSelect,
  NTag,
  NTooltip,
  useDialog,
  useMessage
} from 'naive-ui'
import {
  agentInstallStream,
  getAgentInstallMeta,
  listNetworkInterfaces,
  type AgentInstallDTO,
  type AgentInstallMeta,
  type AgentType
} from '@/api/resource/server'

/**
 * Agent 部署 (SSH 装机) dialog — 从父详情页传 sourceId + role 进来.
 *
 * admin 进来确认 / 改参数 / 看流式日志; 已装机的 server 再点即"重新部署".
 * role: frontline (sourceId = resource_server.id) / landing (sourceId = resource_server.id, server_type=landing).
 */
const props = defineProps<{
  modelValue: boolean
  /** 装机源 id; frontline → server id, landing → ip_pool id. */
  sourceId: string
  /** agent 角色; 同时决定 sourceId 来源表. */
  role: AgentType
  /** 父展示用 (header 副标题): server name / ip address. */
  hostLabel?: string
}>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'deployed'): void
}>()

const message = useMessage()
const dialog = useDialog()

const open = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

// ===== install meta + NIC + form =====
const installMeta = ref<AgentInstallMeta | null>(null)
const nicOptions = ref<{ label: string; value: string }[]>([
  { label: 'auto (默认路由出口网卡)', value: 'auto' }
])
const nicLoading = ref(false)

function defaultForm(): AgentInstallDTO {
  return {
    role: props.role,
    backendTimeoutSeconds: 120,
    heartbeatIntervalSeconds: 60,
    nicIntervalSeconds: 300,
    nicInterface: 'auto',
    reconcileIntervalSeconds: 300,
    nookHome: '/home/nook-agent',
    binPath: '/home/nook-agent/nook-agent',
    configPath: '/home/nook-agent/config.yml',
    systemdUnitPath: '/etc/systemd/system/nook-agent.service',
    backendUrl: '',
    sshTimeoutSeconds: 60,
    sshOpTimeoutSeconds: 60,
    sshUploadTimeoutSeconds: 300,
    installTimeoutSeconds: 720
  }
}
const form = reactive<AgentInstallDTO>(defaultForm())

function applyMetaToForm(target: AgentInstallDTO, m: AgentInstallMeta | null) {
  if (!m) return
  if (m.backendUrl) target.backendUrl = m.backendUrl
  if (m.sshTimeoutSeconds != null) target.sshTimeoutSeconds = m.sshTimeoutSeconds
  if (m.sshOpTimeoutSeconds != null) target.sshOpTimeoutSeconds = m.sshOpTimeoutSeconds
  if (m.sshUploadTimeoutSeconds != null) target.sshUploadTimeoutSeconds = m.sshUploadTimeoutSeconds
  if (m.installTimeoutSeconds != null) target.installTimeoutSeconds = m.installTimeoutSeconds
}

async function loadMeta() {
  try {
    const m = await getAgentInstallMeta(props.role, props.sourceId)
    installMeta.value = m
    applyMetaToForm(form, m)
  } catch {
    installMeta.value = null
  }
}

/**
 * 手动触发: SSH 拉远端网卡列表填充下拉.
 *
 * <p>不在 dialog 打开时自动拉, 避免每次打开都触发一次 SSH (装机前默认 auto 就能跑);
 * 用户想精确指定网卡时点 "拉取网卡" 按钮再拉.
 */
async function loadNic() {
  nicLoading.value = true
  try {
    const ifaces = await listNetworkInterfaces(props.sourceId)
    nicOptions.value = [
      { label: 'auto (默认路由出口网卡)', value: 'auto' },
      ...ifaces.map((n) => ({ label: n, value: n }))
    ]
    if (ifaces.length === 0) {
      message.warning('远端未返回网卡列表 (可能 SSH 失败或机器无网卡)')
    } else {
      message.success(`已拉到 ${ifaces.length} 块网卡`)
    }
  } catch {
    nicOptions.value = [{ label: 'auto (默认路由出口网卡)', value: 'auto' }]
  } finally {
    nicLoading.value = false
  }
}

/** frontline 缺 xray 信息 = 完整重排下 agent 先装 (xray 之后由 agent 部署); 仅提示, 不阻止. */
const xrayMissing = computed(() =>
  props.role === 'frontline'
  && (!installMeta.value?.xrayBin || installMeta.value?.xrayApiPort == null)
)

// ===== 流式装机 =====
const deploying = ref(false)
const deployLog = ref('')
// 部署信息 / SSH 参数 各自独立折叠; admin 一般直接装机, 都有合理默认值
const deployInfoOpen = ref(false)
const sshParamsOpen = ref(false)
const deployLogRef = ref<HTMLPreElement | null>(null)
let deployAbort: AbortController | null = null

function appendLog(t: string) {
  deployLog.value += t
  nextTick(() => {
    if (deployLogRef.value) deployLogRef.value.scrollTop = deployLogRef.value.scrollHeight
  })
}

function startDeploy() {
  dialog.warning({
    title: '确认部署',
    content: `在 ${props.hostLabel || props.sourceId} 上部署 ${props.role} agent?`,
    positiveText: '开始', negativeText: '取消',
    onPositiveClick: () => { actuallyDeploy() }
  })
}

async function actuallyDeploy() {
  deployLog.value = ''
  deploying.value = true
  deployAbort = new AbortController()
  try {
    const dto: AgentInstallDTO = {
      ...form,
      role: props.role,
      xrayBin: props.role === 'frontline' ? installMeta.value?.xrayBin : undefined,
      xrayApiPort: props.role === 'frontline' ? installMeta.value?.xrayApiPort : undefined
    }
    await agentInstallStream(props.sourceId, dto, appendLog, deployAbort.signal)
    appendLog('\n[nook-admin] ✅ 部署完成\n')
    emit('deployed')
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : String(e)
    appendLog(`\n[nook-admin] ❌ 失败: ${msg}\n`)
    message.error(msg)
  } finally {
    deploying.value = false
    deployAbort = null
  }
}

function cancelDeploy() {
  if (deployAbort) deployAbort.abort()
}

async function resetForm() {
  const fresh = defaultForm()
  await loadMeta()
  applyMetaToForm(fresh, installMeta.value)
  Object.assign(form, fresh)
}

// ===== lifecycle =====
watch(open, async (v) => {
  if (v) {
    Object.assign(form, defaultForm())
    installMeta.value = null
    nicOptions.value = [{ label: 'auto (默认路由出口网卡)', value: 'auto' }]
    deployLog.value = ''
    deployInfoOpen.value = false
    sshParamsOpen.value = false
    // 只自动拉装机 meta; 网卡列表 SSH 慢, 改手动按钮触发 (默认 auto 已够大多数场景)
    await loadMeta()
  } else {
    if (deployAbort) deployAbort.abort()
  }
})

onUnmounted(() => { if (deployAbort) deployAbort.abort() })
</script>

<template>
  <NModal
    :show="open"
    preset="card"
    style="max-width: 56rem"
    :bordered="false"
    :mask-closable="false"
    @update:show="(v: boolean) => (open = v)"
  >
    <template #header>
      <div class="flex items-center gap-2">
        <NIcon :size="18"><Rocket /></NIcon>
        <span>部署 Agent</span>
        <NTag size="tiny" :type="role === 'frontline' ? 'success' : 'info'">{{ role }}</NTag>
      </div>
    </template>
    <template #header-extra>
      <span v-if="hostLabel" class="text-xs text-zinc-500 font-mono">{{ hostLabel }}</span>
    </template>

    <NAlert v-if="xrayMissing" type="info" :show-icon="false" size="small" class="mb-3">
      完整重排: 先装 Agent, Xray 之后到「Xray 管理」由 Agent 部署 (此时 xray 参数留空正常).
    </NAlert>

    <!-- label-width 固定 11rem; v-if 折叠 / 展开切换 form-item 集合时 label-width="auto" 会重算导致跨 section 错位.
         需要容纳最长 label "SSH 单条命令超时 (秒)". -->
    <NForm :model="form" label-placement="left" label-width="11rem" size="small">
      <!-- ===== Agent 参数 ===== -->
      <NDivider title-placement="left" class="form-section">
        <span class="text-xs text-zinc-500">Agent 参数</span>
      </NDivider>
      <NFormItem path="backendTimeoutSeconds">
        <template #label>
          请求超时 (秒)
          <NTooltip trigger="hover">
            <template #trigger><NIcon class="hint"><HelpCircle :size="14" /></NIcon></template>
            Agent 调用后端 HTTP 接口的客户端超时.
          </NTooltip>
        </template>
        <NInputNumber v-model:value="form.backendTimeoutSeconds" :min="5" :max="600" class="w-40" />
      </NFormItem>
      <NFormItem path="heartbeatIntervalSeconds">
        <template #label>
          心跳间隔 (秒)
          <NTooltip trigger="hover">
            <template #trigger><NIcon class="hint"><HelpCircle :size="14" /></NIcon></template>
            Agent 上报心跳的频率. 后端 60 秒无心跳标记延迟, 180 秒标记不健康, 300 秒标记掉线.
          </NTooltip>
        </template>
        <NInputNumber v-model:value="form.heartbeatIntervalSeconds" :min="10" :max="3600" class="w-40" />
      </NFormItem>
      <NFormItem path="nicIntervalSeconds">
        <template #label>
          流量上报间隔 (秒)
          <NTooltip trigger="hover">
            <template #trigger><NIcon class="hint"><HelpCircle :size="14" /></NIcon></template>
            网卡流量上报频率.
          </NTooltip>
        </template>
        <NInputNumber v-model:value="form.nicIntervalSeconds" :min="60" :max="3600" class="w-40" />
      </NFormItem>
      <NFormItem path="nicInterface">
        <template #label>
          上报网卡
          <NTooltip trigger="hover">
            <template #trigger><NIcon class="hint"><HelpCircle :size="14" /></NIcon></template>
            上报采样使用的网卡名. auto = 自动使用默认路由出口网卡. 想精确指定时点右侧"拉取网卡"按钮拉远端列表.
          </NTooltip>
        </template>
        <div class="flex items-center gap-2">
          <NSelect
            v-model:value="form.nicInterface"
            :options="nicOptions"
            :loading="nicLoading"
            tag
            filterable
            class="w-60"
          />
          <NButton size="small" :loading="nicLoading" @click="loadNic">
            <template #icon><NIcon><RefreshCcw :size="14" /></NIcon></template>
            拉取网卡
          </NButton>
        </div>
      </NFormItem>
      <NFormItem path="reconcileIntervalSeconds">
        <template #label>
          对账间隔 (秒)
          <NTooltip trigger="hover">
            <template #trigger><NIcon class="hint"><HelpCircle :size="14" /></NIcon></template>
            <span v-if="role === 'frontline'">线路机 reconcile (对账) 周期: 拉后端期望态跟本地 xray 比对, 缺补多删自愈. 默认 300 秒 (5min).</span>
            <span v-else>落地机 tc 限速 (对账) 周期: 拉后端期望带宽跟本地 tc 比对, 不一致才改. 默认 300 秒 (5min).</span>
          </NTooltip>
        </template>
        <NInputNumber v-model:value="form.reconcileIntervalSeconds" :min="30" :max="3600" class="w-40" />
      </NFormItem>

      <!-- ===== 部署信息 (默认折叠) ===== -->
      <div class="section-toggle">
        <button
          type="button"
          class="section-btn"
          :disabled="deploying"
          @click="deployInfoOpen = !deployInfoOpen"
        >
          <NIcon :size="14">
            <ChevronDown v-if="deployInfoOpen" />
            <ChevronRight v-else />
          </NIcon>
          部署信息
        </button>
      </div>
      <div v-if="deployInfoOpen">
        <NFormItem path="backendUrl"><template #label>Backend URL</template>
          <NInput v-model:value="form.backendUrl" placeholder="https://your-backend.example.com" class="w-96" />
        </NFormItem>
        <NFormItem path="nookHome"><template #label>装机根目录</template>
          <NInput v-model:value="form.nookHome" class="w-96" />
        </NFormItem>
        <NFormItem path="binPath"><template #label>Binary 路径</template>
          <NInput v-model:value="form.binPath" class="w-96" />
        </NFormItem>
        <NFormItem path="configPath"><template #label>Config 路径</template>
          <NInput v-model:value="form.configPath" class="w-96" />
        </NFormItem>
        <NFormItem path="systemdUnitPath"><template #label>systemd unit 路径</template>
          <NInput v-model:value="form.systemdUnitPath" class="w-96" />
        </NFormItem>
      </div>

      <!-- ===== SSH 参数 (默认折叠) ===== -->
      <div class="section-toggle">
        <button
          type="button"
          class="section-btn"
          :disabled="deploying"
          @click="sshParamsOpen = !sshParamsOpen"
        >
          <NIcon :size="14">
            <ChevronDown v-if="sshParamsOpen" />
            <ChevronRight v-else />
          </NIcon>
          SSH 参数
        </button>
      </div>
      <div v-if="sshParamsOpen">
        <NFormItem path="sshTimeoutSeconds"><template #label>SSH 握手超时 (秒)</template>
          <NInputNumber v-model:value="form.sshTimeoutSeconds" :min="5" :max="600" class="w-40" />
        </NFormItem>
        <NFormItem path="sshOpTimeoutSeconds"><template #label>SSH 单条命令超时 (秒)</template>
          <NInputNumber v-model:value="form.sshOpTimeoutSeconds" :min="5" :max="300" class="w-40" />
        </NFormItem>
        <NFormItem path="sshUploadTimeoutSeconds"><template #label>SCP 上传超时 (秒)</template>
          <NInputNumber v-model:value="form.sshUploadTimeoutSeconds" :min="5" :max="600" class="w-40" />
        </NFormItem>
        <NFormItem path="installTimeoutSeconds">
          <template #label>
            安装整体超时 (秒)
            <NTooltip trigger="hover">
              <template #trigger><NIcon class="hint"><HelpCircle :size="14" /></NIcon></template>
              整条 SSH 装机脚本的执行上限, 含下载 / 安装包 / systemd 启用等步骤; 默认 600 秒.
            </NTooltip>
          </template>
          <NInputNumber v-model:value="form.installTimeoutSeconds" :min="60" :max="3600" class="w-40" />
        </NFormItem>
      </div>
    </NForm>

    <!-- 实时输出 -->
    <pre v-if="deployLog" ref="deployLogRef" class="deploy-log">{{ deployLog }}</pre>

    <template #footer>
      <div class="flex gap-2 pt-1 items-center">
        <NButton type="primary" size="small" :loading="deploying" :disabled="deploying" @click="startDeploy">
          <template #icon><NIcon><Rocket /></NIcon></template>
          SSH 自动装机
        </NButton>
        <NButton size="small" :disabled="deploying" @click="resetForm">重置表单</NButton>
        <NButton v-if="deploying" size="small" @click="cancelDeploy">中止</NButton>
        <div class="flex-1"></div>
        <NButton size="small" :disabled="deploying" @click="open = false">关闭</NButton>
      </div>
    </template>
  </NModal>
</template>

<style scoped>
.hint {
  margin-left: 4px;
  vertical-align: middle;
  color: #a1a1aa;
  cursor: help;
}
.form-section {
  margin-top: 8px !important;
  margin-bottom: 4px !important;
}
.section-toggle {
  margin-top: 8px;
  padding-top: 6px;
  border-top: 1px dashed var(--n-divider-color, #efeff5);
}
.section-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 0;
  background: transparent;
  border: none;
  cursor: pointer;
  font-size: 13px;
  color: var(--n-text-color-2, #555);
}
.section-btn:hover {
  color: var(--n-text-color-1, #222);
}
.section-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.deploy-log {
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  line-height: 1.5;
  background: #1c1c20;
  color: #e4e4e7;
  padding: 8px 10px;
  border-radius: 4px;
  height: 16rem;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  margin: 12px 0 0 0;
}
</style>
