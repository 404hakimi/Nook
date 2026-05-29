<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { Save, RefreshCcw } from 'lucide-vue-next'
import {
  NAlert,
  NButton,
  NIcon,
  NModal,
  NSpace,
  NSpin,
  NTag,
  useMessage
} from 'naive-ui'
import { getRuntimeConfig, saveRuntimeConfig, type AgentRuntimeConfig } from '@/api/agent/runtime-config'
import { formatDateTime } from '@/utils/date'

const props = defineProps<{
  modelValue: boolean
  serverId: string | null
  serverName?: string
  /** 'frontline' / 'landing'; 决定 NEVER_CONFIGURED 时默认模板. 父组件从 agentVersion parse. */
  role?: 'frontline' | 'landing'
}>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void; (e: 'saved'): void }>()

const message = useMessage()

const open = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const loading = ref(false)
const saving = ref(false)
const data = ref<AgentRuntimeConfig | null>(null)
const draft = ref('')

// === 共用 yaml 前半 (backend / heartbeat / nic / poller) ===
const SHARED_HEAD = `# === backend 通信 ===
backend:
  # 后端公网地址; agent 所有 push (心跳/NIC/task-result) 都打这
  api_url: http://CHANGE-ME-BACKEND-URL
  # HTTP 客户端超时 (秒); 跨境网络抖动可调大 (e.g., 60)
  timeout_seconds: 30

# === 心跳 ===
heartbeat:
  # 心跳间隔 (秒); backend 端 60s 没收到 → WARN, 180s → TEMP_UNHEALTHY, 300s → OFFLINE
  interval_seconds: 60

# === NIC 网卡流量 (vnstat 读) ===
nic:
  # 上报间隔 (秒); 默认 5min
  interval_seconds: 300
  # 网卡名; "auto" 自动用默认路由出口网卡; 多网卡手填 eth0/ens5
  interface: auto

# === 任务轮询 (升级/改配置/清日志/xray_* 都走 task 队列) ===
poller:
  # 轮询间隔 (秒); 越短任务越实时, 但 backend 请求量越大
  interval_seconds: 30
`

// === Frontline 专属 (跑 xray) ===
const FRONTLINE_TAIL = `
# === Xray 集成 (frontline 专属; agent 启动自检 xray 是否真装了) ===
xray:
  # xray 二进制路径; agent 自检文件存在才挂 xray collector
  bin: /usr/local/bin/xray
  # xray inbound dispatcher API gRPC 端口
  api_port: 10085
  # 客户流量 stats 上报间隔 (秒)
  stats_interval_seconds: 300
  # reconcile (对账) 周期 (秒); 拉后端期望态跟本地 xray 比对, 缺补多删自愈; 默认 5min
  reconcile_interval_seconds: 300
`

// === Landing 专属 (跑 tc 限速; socks5 仍由 backend SSH 装) ===
const LANDING_TAIL = `
# === 落地机限速 (landing 专属; agent 跑 tc 整形出口网卡) ===
landing:
  # tc 限速 (对账) 周期 (秒); 拉后端期望带宽 (= 占用本机的套餐带宽) 跟本地 tc 比对, 不一致才改; 默认 5min
  bandwidth_reconcile_interval_seconds: 300

# === Socks5 集成 (landing 专属) ===
# socks5 接管由 backend SSH 完成; 后续 sprint agent 接管时这里加配置
`

const FRONTLINE_TEMPLATE = `# ============================================================================
# nook-frontline-agent 运行时配置 (线路机; 跑 xray)
# admin 在管理端改, agent 30s 内自动拉到并自重启生效
# ----------------------------------------------------------------------------

` + SHARED_HEAD + FRONTLINE_TAIL

const LANDING_TEMPLATE = `# ============================================================================
# nook-landing-agent 运行时配置 (落地机; 跑 socks5)
# admin 在管理端改, agent 30s 内自动拉到并自重启生效
# ----------------------------------------------------------------------------

` + SHARED_HEAD + LANDING_TAIL

const DEFAULT_TEMPLATE = computed(() =>
  props.role === 'landing' ? LANDING_TEMPLATE : FRONTLINE_TEMPLATE
)

async function load() {
  if (!props.serverId) return
  loading.value = true
  try {
    data.value = await getRuntimeConfig(props.serverId)
    draft.value = data.value?.configYaml || DEFAULT_TEMPLATE.value
  } catch {
    /* 已 toast */
  } finally {
    loading.value = false
  }
}

async function onSave() {
  if (!props.serverId || !draft.value.trim()) {
    message.error('yaml 不能为空')
    return
  }
  saving.value = true
  try {
    const taskId = await saveRuntimeConfig(props.serverId, draft.value)
    message.success(`已派发 (taskId=${taskId.slice(0, 8)}…) — agent 30 秒内自动应用`)
    emit('saved')
    open.value = false
  } catch {
    /* */
  } finally {
    saving.value = false
  }
}

const syncStateLabel = computed(() => {
  const s = data.value?.syncState
  if (s === 'SYNCED') return { text: '✅ 已同步', type: 'success' as const }
  if (s === 'PENDING') return { text: '⏳ 待 agent 应用', type: 'warning' as const }
  return { text: '⚪ 未配置过', type: 'default' as const }
})

watch(open, (v) => {
  if (!v) { data.value = null; draft.value = '' }
})

// 行号 (跟 textarea 行数同步)
const lineNumbers = computed(() => {
  const n = (draft.value.match(/\n/g)?.length ?? 0) + 1
  return Array.from({ length: n }, (_, i) => String(i + 1).padStart(3, ' ')).join('\n')
})

// 字节数 (UTF-8); template 拿不到全局 Blob, 在 setup 算
const draftStats = computed(() => {
  const lines = (draft.value.match(/\n/g)?.length ?? 0) + 1
  const bytes = new TextEncoder().encode(draft.value).length
  return { lines, bytes }
})

// 用原生 textarea (NInput textarea 默认带自适应高度, 跟我们的固定容器 + 同步滚动方案冲突)
const lineNumsRef = ref<HTMLElement | null>(null)
const taRef = ref<HTMLTextAreaElement | null>(null)

function onTaScroll(e: Event) {
  const ta = e.target as HTMLTextAreaElement
  if (lineNumsRef.value && ta) {
    lineNumsRef.value.scrollTop = ta.scrollTop
  }
}

// dialog 打开 = 拉数据 + 自动 focus textarea
watch(open, async (v) => {
  if (v) {
    await load()
    await nextTick()
  }
}, { immediate: false })
</script>

<style scoped>
/* 编辑器: 原生 textarea 拿不到 Naive 组件内的 --n-text-color 变量, 直接 data-theme 切色 */
.yaml-editor-wrap {
  display: flex;
  align-items: stretch;
  border: 1px solid rgba(127, 127, 127, 0.25);
  border-radius: 4px;
  overflow: hidden;
  background: #fafafc; /* 亮色默认 */
  height: 500px;
}
.yaml-line-nums {
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  color: #94a3b8; /* slate-400, 亮暗都偏淡 */
  background: rgba(127, 127, 127, 0.08);
  padding: 8px 6px 8px 8px;
  margin: 0;
  user-select: none;
  white-space: pre;
  text-align: right;
  border-right: 1px solid rgba(127, 127, 127, 0.25);
  line-height: 1.5;
  overflow: hidden;
  flex-shrink: 0;
}
.yaml-editor-ta {
  flex: 1;
  border: none;
  outline: none;
  resize: none;
  padding: 8px 10px;
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  line-height: 1.5;
  background: transparent;
  color: #1e293b; /* slate-800, 亮色 */
  caret-color: #1e293b;
  overflow: auto;
  min-width: 0;
  tab-size: 2;
  white-space: pre;
  word-wrap: normal;
  overflow-wrap: normal;
}

/* 暗色: html[data-theme='dark'] 由 useTheme 设 */
html[data-theme='dark'] .yaml-editor-wrap {
  background: #1c1c20;
  border-color: rgba(255, 255, 255, 0.12);
}
html[data-theme='dark'] .yaml-line-nums {
  background: rgba(255, 255, 255, 0.04);
  color: #71717a;
  border-right-color: rgba(255, 255, 255, 0.12);
}
html[data-theme='dark'] .yaml-editor-ta {
  color: #e4e4e7;
  caret-color: #e4e4e7;
}
.yaml-editor-ta::selection {
  background: rgba(99, 102, 241, 0.35);
}
</style>

<template>
  <NModal
    :show="open"
    preset="card"
    :title="`运行时配置: ${serverName ?? ''}`"
    style="max-width: 50rem"
    :bordered="false"
    :mask-closable="false"
    @update:show="(v: boolean) => (open = v)"
  >
    <NSpin :show="loading">
      <div class="space-y-2">
        <div class="flex items-center gap-2 text-xs">
          <NTag size="small" :type="syncStateLabel.type">{{ syncStateLabel.text }}</NTag>
          <span class="text-zinc-500" v-if="data?.updatedAt">
            {{ formatDateTime(data.updatedAt) }}
            <span v-if="data?.appliedAt"> · 应用 {{ formatDateTime(data.appliedAt) }}</span>
          </span>
          <div class="flex-1"></div>
          <NButton size="tiny" quaternary :disabled="loading" @click="load">
            <template #icon><NIcon><RefreshCcw /></NIcon></template>
            刷新
          </NButton>
        </div>

        <div class="yaml-editor-wrap">
          <pre ref="lineNumsRef" class="yaml-line-nums">{{ lineNumbers }}</pre>
          <textarea
            ref="taRef"
            v-model="draft"
            class="yaml-editor-ta"
            placeholder="yaml..."
            spellcheck="false"
            @scroll="onTaScroll"
          ></textarea>
        </div>

        <div class="text-right text-xs text-zinc-500 font-mono">
          {{ draftStats.lines }} 行 · {{ draftStats.bytes }} 字节
        </div>
      </div>
    </NSpin>

    <template #footer>
      <NSpace justify="end">
        <NButton size="small" @click="open = false">取消</NButton>
        <NButton type="primary" size="small" :loading="saving" @click="onSave">
          <template #icon><NIcon><Save /></NIcon></template>
          保存 + 派发
        </NButton>
      </NSpace>
    </template>
  </NModal>
</template>
