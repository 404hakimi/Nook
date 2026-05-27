<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { ArrowUp, CheckCircle2, Clock, XCircle } from 'lucide-vue-next'
import {
  NAlert,
  NButton,
  NIcon,
  NModal,
  NTag,
  useDialog,
  useMessage
} from 'naive-ui'
import {
  AGENT_ONLINE_LABELS,
  AGENT_ONLINE_TAG_TYPE,
  upgradeAgent
} from '@/api/agent/agent'
import {
  getServerDetailWithRuntime,
  type ServerFrontlineListItem
} from '@/api/resource/server'

/**
 * Agent 升级 dialog — 在 server 已装好 agent + 心跳在线的前提下, 派 upgrade task
 * 给远端 agent 自己拉新 binary + 重启自身. 不走 SSH, 全部走 task 链路.
 *
 * UI 故意简化: 仅一个确认按钮 + 状态轮询; 装机参数 / 路径都沿用现有 config.yml 不重写.
 */
const props = defineProps<{
  modelValue: boolean
  /** server id; 必须是已装 agent 的 server. */
  serverId: string
  /** 父展示用 (header 副标题): server 名 + 当前版本. */
  hostLabel?: string
  /** 父传当前 agent 状态; 用于 disabled 判断 + 升级前后对比. */
  agentInfo: ServerFrontlineListItem | null
}>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'upgraded'): void
}>()

const message = useMessage()
const dialog = useDialog()

const open = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

// ===== 升级流程状态 =====
const upgrading = ref(false)
const dispatchedTaskId = ref<string | null>(null)
const oldVersion = ref<string | null>(null)
const newVersion = ref<string | null>(null)
type Status = 'IDLE' | 'WAITING' | 'COMPLETED' | 'TIMEOUT'
const status = ref<Status>('IDLE')
const elapsed = ref(0)
let pollTimer: ReturnType<typeof setInterval> | null = null
const POLL_INTERVAL_MS = 5000
const POLL_TIMEOUT_S = 180

function stopPolling() {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
}

const onlineState = computed(() => props.agentInfo?.onlineState)
const offline = computed(() =>
  !props.agentInfo || onlineState.value === 'OFFLINE' || onlineState.value === 'NEVER'
)

function doUpgrade() {
  if (offline.value) {
    message.error('agent 不在线, 升级会一直 PENDING')
    return
  }
  dialog.warning({
    title: '确认升级',
    content: '会派 upgrade task, agent 拉到后自动 download 新 binary + 重启 (~10-20s 断 1 次心跳).',
    positiveText: '升级', negativeText: '取消',
    onPositiveClick: async () => {
      upgrading.value = true
      try {
        const taskId = await upgradeAgent(props.serverId)
        dispatchedTaskId.value = taskId
        oldVersion.value = props.agentInfo?.agentVersion ?? '?'
        status.value = 'WAITING'
        elapsed.value = 0
        message.success(`已派 task ${taskId.slice(0, 8)}…`)
        emit('upgraded')
        startPolling()
      } catch { /* */ } finally {
        upgrading.value = false
      }
    }
  })
}

function startPolling() {
  stopPolling()
  pollTimer = setInterval(async () => {
    elapsed.value += POLL_INTERVAL_MS / 1000
    try {
      const a = await getServerDetailWithRuntime(props.serverId).catch(() => null)
      if (a && a.agentVersion && a.agentVersion !== oldVersion.value && a.onlineState === 'ONLINE') {
        newVersion.value = a.agentVersion
        status.value = 'COMPLETED'
        stopPolling()
      }
    } catch { /* */ }
    if (elapsed.value >= POLL_TIMEOUT_S && status.value === 'WAITING') {
      status.value = 'TIMEOUT'
      stopPolling()
    }
  }, POLL_INTERVAL_MS)
}

const statusTag = computed(() => {
  switch (status.value) {
    case 'WAITING': return { text: `⏳ 等待 ${elapsed.value}s`, type: 'info' as const, icon: Clock }
    case 'COMPLETED': return { text: `✅ 已升级 ${oldVersion.value} → ${newVersion.value}`, type: 'success' as const, icon: CheckCircle2 }
    case 'TIMEOUT': return { text: '⚠ 超时未检测到新版本 (agent 可能离线 / 升级失败)', type: 'warning' as const, icon: XCircle }
    default: return null
  }
})

// ===== lifecycle =====
watch(open, (v) => {
  if (v) {
    // 重置状态
    status.value = 'IDLE'
    dispatchedTaskId.value = null
    oldVersion.value = null
    newVersion.value = null
    elapsed.value = 0
  } else {
    stopPolling()
  }
})

onUnmounted(() => { stopPolling() })
</script>

<template>
  <NModal
    :show="open"
    preset="card"
    style="max-width: 36rem"
    :bordered="false"
    @update:show="(v: boolean) => (open = v)"
  >
    <template #header>
      <div class="flex items-center gap-2">
        <NIcon :size="18"><ArrowUp /></NIcon>
        <span>升级 Agent 版本</span>
      </div>
    </template>
    <template #header-extra>
      <span v-if="hostLabel" class="text-xs text-zinc-500 font-mono">{{ hostLabel }}</span>
    </template>

    <!-- 当前 agent 摘要 -->
    <div v-if="agentInfo" class="summary">
      <div class="summary-row">
        <span class="k">当前版本</span>
        <code class="kbd">{{ agentInfo.agentVersion || '未装' }}</code>
      </div>
      <div class="summary-row">
        <span class="k">在线状态</span>
        <NTag size="tiny" :type="AGENT_ONLINE_TAG_TYPE[agentInfo.onlineState] || 'default'">
          {{ AGENT_ONLINE_LABELS[agentInfo.onlineState] }}
        </NTag>
        <span v-if="agentInfo.elapsedSec != null" class="text-zinc-400 font-mono">{{ agentInfo.elapsedSec }}s</span>
      </div>
    </div>

    <NAlert v-if="offline" type="warning" :show-icon="false" size="small" class="mt-3">
      agent 不在线 — 升级 task 会一直 PENDING. 等 agent 上线再升级.
    </NAlert>
    <NAlert v-else type="info" :show-icon="false" size="small" class="mt-3">
      仅替换 binary, 约 10-20s 重启窗口; xray / socks5 不动.
    </NAlert>

    <!-- 升级进度 -->
    <div v-if="statusTag" class="progress-block mt-3">
      <NTag size="medium" :type="statusTag.type">{{ statusTag.text }}</NTag>
      <span v-if="dispatchedTaskId" class="text-xs text-zinc-500 ml-2">
        task <code class="kbd">{{ dispatchedTaskId.slice(0, 8) }}…</code>
      </span>
    </div>

    <template #footer>
      <div class="flex gap-2 items-center">
        <NButton
          v-if="status !== 'COMPLETED'"
          type="primary"
          size="small"
          :loading="upgrading || status === 'WAITING'"
          :disabled="offline || status === 'WAITING'"
          @click="doUpgrade"
        >
          <template #icon><NIcon><ArrowUp /></NIcon></template>
          {{ status === 'WAITING' ? '等待 agent 自重启...' : '一键升级' }}
        </NButton>
        <div class="flex-1"></div>
        <NButton size="small" :disabled="status === 'WAITING'" @click="open = false">
          {{ status === 'COMPLETED' ? '完成' : '关闭' }}
        </NButton>
      </div>
    </template>
  </NModal>
</template>

<style scoped>
.summary {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px 12px;
  background: var(--n-action-color, #fafafa);
  border-radius: 4px;
  border: 1px solid var(--n-border-color, #efeff5);
}
.summary-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}
.summary-row .k {
  width: 5rem;
  color: var(--n-text-color-3, #999);
  font-size: 12px;
}
.kbd {
  font-family: 'JetBrains Mono', 'Cascadia Code', Consolas, monospace;
  font-size: 12px;
  color: var(--n-text-color-1, #222);
  padding: 1px 6px;
  background: #fff;
  border-radius: 3px;
  border: 1px solid var(--n-border-color, #efeff5);
}
.progress-block {
  padding: 10px 12px;
  background: var(--n-action-color, #fafafa);
  border-radius: 4px;
  border: 1px solid var(--n-border-color, #efeff5);
}
</style>
