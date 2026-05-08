<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Activity, Power, RefreshCw, Rocket, ScrollText } from 'lucide-vue-next'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import {
  sshLog,
  xrayRestart,
  xrayStatus,
  type XrayServiceStatus
} from '@/api/xray/server'
import type { ResourceServer } from '@/api/resource/server'
import ServerInstallDialog from './ServerInstallDialog.vue'

interface Props {
  modelValue: boolean
  server?: ResourceServer | null
}
const props = defineProps<Props>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
}>()

const toast = useToast()
const { confirm } = useConfirm()

type Tab = 'status' | 'log'
const activeTab = ref<Tab>('status')
const running = ref(false)

const status = ref<XrayServiceStatus | null>(null)
const logOutput = ref('')

const installOpen = ref(false)

watch(
  () => [props.modelValue, props.server?.id],
  ([open]) => {
    if (open) {
      activeTab.value = 'status'
      status.value = null
      logOutput.value = ''
      runStatus()
    }
  }
)

async function runStatus() {
  if (!props.server || running.value) return
  activeTab.value = 'status'
  running.value = true
  try {
    status.value = await xrayStatus(props.server.id)
  } catch (e) {
    status.value = null
    toast.error('拉取状态失败: ' + ((e as Error).message ?? ''))
  } finally {
    running.value = false
  }
}

async function runLog() {
  if (!props.server || running.value) return
  activeTab.value = 'log'
  running.value = true
  try {
    logOutput.value = await sshLog(props.server.id, 200)
  } catch (e) {
    logOutput.value = '[error] ' + ((e as Error).message ?? '')
    toast.error('拉取日志失败')
  } finally {
    running.value = false
  }
}

async function runRestart() {
  if (!props.server || running.value) return
  const ok = await confirm({
    title: '重启 Xray',
    message: `确认在 "${props.server.name}" 上 systemctl restart xray？所有客户端会断开重连(1-2 秒).`,
    type: 'warning',
    confirmText: '重启'
  })
  if (!ok) return
  running.value = true
  try {
    const out = await xrayRestart(props.server.id)
    toast.success('✔ 已重启')
    await runStatus()
    if (activeTab.value !== 'status') {
      logOutput.value = out
    }
  } catch (e) {
    toast.error('重启失败: ' + ((e as Error).message ?? ''))
  } finally {
    running.value = false
  }
}

function openInstall() {
  installOpen.value = true
}

async function onInstalled() {
  toast.success('安装完成,正在刷新状态')
  await runStatus()
}

function close() {
  emit('update:modelValue', false)
}

const activeBadge = computed(() => {
  const a = status.value?.active?.trim() ?? ''
  if (a === 'active') return { text: '运行中', cls: 'badge-success' }
  if (a === 'inactive') return { text: '未运行', cls: 'badge-error' }
  if (a === 'failed') return { text: '失败', cls: 'badge-error' }
  if (!a) return { text: '未知', cls: 'badge-ghost' }
  return { text: a, cls: 'badge-warning' }
})
</script>

<template>
  <dialog class="modal" :class="{ 'modal-open': modelValue }">
    <div class="modal-box max-w-4xl">
      <div class="flex items-center justify-between mb-4 gap-2 flex-wrap">
        <h3 class="text-lg font-semibold">
          Xray 运维台
          <span v-if="server" class="text-base-content/60 text-sm font-normal">
            — {{ server.name }} ({{ server.host }})
          </span>
        </h3>
        <button class="btn btn-ghost btn-sm" @click="close">关闭</button>
      </div>

      <div class="flex items-center justify-between mb-3 gap-2 flex-wrap">
        <div role="tablist" class="tabs tabs-bordered">
          <a
            role="tab"
            class="tab"
            :class="{ 'tab-active': activeTab === 'status' }"
            @click="runStatus"
          >
            <Activity class="w-4 h-4 mr-1" />状态
          </a>
          <a
            role="tab"
            class="tab"
            :class="{ 'tab-active': activeTab === 'log' }"
            @click="runLog"
          >
            <ScrollText class="w-4 h-4 mr-1" />日志
          </a>
        </div>
        <div class="flex gap-2 flex-wrap">
          <button
            class="btn btn-sm btn-ghost gap-1"
            :disabled="running"
            @click="activeTab === 'log' ? runLog() : runStatus()"
          >
            <RefreshCw class="w-4 h-4" />刷新
          </button>
          <button
            class="btn btn-sm btn-warning btn-outline gap-1"
            :disabled="running"
            @click="runRestart"
          >
            <Power class="w-4 h-4" />重启 Xray
          </button>
          <button
            class="btn btn-sm btn-primary gap-1"
            :disabled="running"
            @click="openInstall"
          >
            <Rocket class="w-4 h-4" />部署/重装
          </button>
        </div>
      </div>

      <div v-if="activeTab === 'status'">
        <div v-if="running && !status" class="py-12 text-center">
          <span class="loading loading-spinner"></span>
        </div>
        <div v-else-if="status" class="space-y-3">
          <div class="bg-base-200 rounded p-3 grid grid-cols-1 sm:grid-cols-2 gap-3 text-sm">
            <div>
              <div class="text-xs text-base-content/50">服务状态</div>
              <span :class="['badge badge-sm', activeBadge.cls]">{{ activeBadge.text }}</span>
            </div>
            <div>
              <div class="text-xs text-base-content/50">Xray 版本</div>
              <div class="font-mono text-xs">{{ status.version || '-' }}</div>
            </div>
            <div class="sm:col-span-2">
              <div class="text-xs text-base-content/50">启动时间</div>
              <div class="text-xs">{{ status.uptimeFrom || '-' }}</div>
            </div>
            <div class="sm:col-span-2">
              <div class="text-xs text-base-content/50">监听端口</div>
              <pre class="font-mono text-xs whitespace-pre-wrap break-all">{{ status.listening || '(未捕获)' }}</pre>
            </div>
          </div>
          <div>
            <div class="text-xs text-base-content/50 mb-1">最近日志(30 行)</div>
            <div class="mockup-code text-xs max-h-64 overflow-auto bg-base-300">
              <pre class="px-4 whitespace-pre-wrap break-all"><code>{{ status.log || '(无日志)' }}</code></pre>
            </div>
          </div>
        </div>
        <div v-else class="py-8 text-center text-base-content/40">
          点上方"刷新"或"重启"按钮触发
        </div>
      </div>

      <div v-else>
        <div class="mockup-code text-xs max-h-96 overflow-auto bg-base-300 text-base-content min-h-32">
          <div v-if="running" class="px-4 py-2">
            <span class="loading loading-spinner loading-xs mr-2"></span>
            拉取中...
          </div>
          <pre v-else-if="logOutput" class="px-4 whitespace-pre-wrap break-all"><code>{{ logOutput }}</code></pre>
          <div v-else class="px-4 py-2 text-base-content/40">点"刷新"拉日志</div>
        </div>
      </div>
    </div>
    <div class="modal-backdrop bg-black/40" @click="close"></div>

    <ServerInstallDialog v-model="installOpen" :server="server" @installed="onInstalled" />
  </dialog>
</template>
