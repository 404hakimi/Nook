<script setup lang="ts">
import { ref, watch } from 'vue'
import { Activity, Power, ScrollText, Save } from 'lucide-vue-next'
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

type Tab = 'status' | 'log' | 'restart' | 'backup'
const activeTab = ref<Tab>('status')
const output = ref('')
const running = ref(false)

watch(
  () => [props.modelValue, props.server],
  ([open]) => {
    if (open) {
      activeTab.value = 'status'
      output.value = ''
      // 默认打开就拉一次状态
      runStatus()
    }
  }
)

async function runStatus() {
  if (!props.server) return
  activeTab.value = 'status'
  await runOp(() => sshStatus(props.server!.id), '拉取状态失败')
}

async function runLog() {
  if (!props.server) return
  activeTab.value = 'log'
  await runOp(() => sshLog(props.server!.id, 200), '拉取日志失败')
}

async function runRestart() {
  if (!props.server) return
  const ok = await confirm({
    title: '重启 x-ui',
    message: `确认在 "${props.server.name}" 上执行 systemctl restart x-ui？短暂中断期间所有客户端连接会断开重连。`,
    type: 'warning',
    confirmText: '重启'
  })
  if (!ok) return
  activeTab.value = 'restart'
  await runOp(() => sshRestart(props.server!.id), '重启失败')
}

async function runBackup() {
  if (!props.server) return
  activeTab.value = 'backup'
  await runOp(() => sshBackupDb(props.server!.id), '备份失败')
}

async function runOp<T>(call: () => Promise<T>, errMsg: string) {
  running.value = true
  output.value = ''
  try {
    const res = await call()
    output.value = String(res ?? '')
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

      <div role="tablist" class="tabs tabs-bordered mb-3">
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
        <a
          role="tab"
          class="tab"
          :class="{ 'tab-active': activeTab === 'restart' }"
          @click="runRestart"
        >
          <Power class="w-4 h-4 mr-1" />重启
        </a>
        <a
          role="tab"
          class="tab"
          :class="{ 'tab-active': activeTab === 'backup' }"
          @click="runBackup"
        >
          <Save class="w-4 h-4 mr-1" />备份 DB
        </a>
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
