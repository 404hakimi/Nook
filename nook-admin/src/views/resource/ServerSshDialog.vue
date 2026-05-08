<script setup lang="ts">
import { ref, watch } from 'vue'
import { Activity, Power, RefreshCw, ScrollText, Save } from 'lucide-vue-next'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import {
  sshBackupDb,
  sshLog,
  sshRestart,
  sshStatus
} from '@/api/xray/server'
import type { ResourceServer } from '@/api/resource/server'

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

// 只读 Tab，本质是"看哪段输出"——切 Tab 不再触发命令
type Tab = 'status' | 'log'
const activeTab = ref<Tab>('status')
const output = ref('')
const running = ref(false)

watch(
  () => [props.modelValue, props.server],
  ([open]) => {
    if (open) {
      activeTab.value = 'status'
      output.value = ''
      // 默认进来就拉一次状态
      runStatus()
    }
  }
)

async function runStatus() {
  if (!props.server || running.value) return
  activeTab.value = 'status'
  await runOp(() => sshStatus(props.server!.id), '拉取状态失败')
}

async function runLog() {
  if (!props.server || running.value) return
  activeTab.value = 'log'
  await runOp(() => sshLog(props.server!.id, 200), '拉取日志失败')
}

async function runRestart() {
  if (!props.server || running.value) return
  const ok = await confirm({
    title: '重启 x-ui',
    message: `确认在 "${props.server.name}" 上执行 systemctl restart x-ui？短暂中断期间所有客户端连接会断开重连。`,
    type: 'warning',
    confirmText: '重启'
  })
  if (!ok) return
  await runOp(() => sshRestart(props.server!.id), '重启失败', '✔ 已重启')
}

async function runBackup() {
  if (!props.server || running.value) return
  await runOp(() => sshBackupDb(props.server!.id), '备份失败', '✔ 已备份到 /tmp/x-ui.db.bak')
}

async function runOp<T>(call: () => Promise<T>, errMsg: string, successMsg?: string) {
  running.value = true
  output.value = ''
  try {
    const res = await call()
    output.value = String(res ?? '')
    if (successMsg) toast.success(successMsg)
  } catch (e) {
    output.value = `[error] ${errMsg}: ${(e as Error).message || ''}`
    toast.error(errMsg)
  } finally {
    running.value = false
  }
}

function close() {
  emit('update:modelValue', false)
}
</script>

<template>
  <dialog class="modal" :class="{ 'modal-open': modelValue }">
    <div class="modal-box max-w-4xl">
      <div class="flex items-center justify-between mb-4">
        <h3 class="text-lg font-semibold">
          SSH 控制台
          <span v-if="server" class="text-base-content/60 text-sm font-normal">
            — {{ server.name }} ({{ server.host }})
          </span>
        </h3>
        <button class="btn btn-ghost btn-sm" @click="close">关闭</button>
      </div>

      <!-- 上方：只读 Tab(状态/日志) 与 写操作(重启/备份) 分开布局 -->
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
        <div class="flex gap-2">
          <button
            class="btn btn-sm btn-ghost"
            :disabled="running"
            title="刷新当前 Tab"
            @click="activeTab === 'log' ? runLog() : runStatus()"
          >
            <RefreshCw class="w-4 h-4" />刷新
          </button>
          <button
            class="btn btn-sm btn-warning btn-outline"
            :disabled="running"
            @click="runRestart"
          >
            <Power class="w-4 h-4" />重启 x-ui
          </button>
          <button
            class="btn btn-sm btn-outline"
            :disabled="running"
            @click="runBackup"
          >
            <Save class="w-4 h-4" />备份 DB
          </button>
        </div>
      </div>

      <div class="mockup-code text-xs max-h-96 overflow-auto bg-base-300 text-base-content min-h-32">
        <div v-if="running" class="px-4 py-2">
          <span class="loading loading-spinner loading-xs mr-2"></span>
          执行中...
        </div>
        <pre v-else-if="output" class="px-4 whitespace-pre-wrap break-all"><code>{{ output }}</code></pre>
        <div v-else class="px-4 py-2 text-base-content/40">点上面的 Tab 触发命令</div>
      </div>
    </div>
    <div class="modal-backdrop bg-black/40" @click="close"></div>
  </dialog>
</template>
