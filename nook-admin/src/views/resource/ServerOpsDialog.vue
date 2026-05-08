<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Activity, Cpu, HardDrive, MemoryStick, Power, RefreshCw, Rocket, ScrollText, Server, Timer } from 'lucide-vue-next'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import {
  xrayRestart,
  xrayStatus,
  type XrayLogLevel,
  type XrayServiceStatus
} from '@/api/xray/server'
import type { ResourceServer } from '@/api/resource/server'
import Select from '@/components/Select.vue'
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

// 日志可调: 行数 + 级别过滤. 控制 status 与 log Tab 共用.
const LOG_LINES_OPTIONS = [
  { label: '50 行', value: 50 },
  { label: '100 行', value: 100 },
  { label: '200 行', value: 200 },
  { label: '500 行', value: 500 },
  { label: '1000 行', value: 1000 }
]
const LOG_LEVEL_OPTIONS: { label: string; value: XrayLogLevel }[] = [
  { label: '全部', value: 'all' },
  { label: '警告以上', value: 'warning' },
  { label: '错误以上', value: 'err' }
]
const logLines = ref(100)
const logLevel = ref<XrayLogLevel>('all')

const installOpen = ref(false)

watch(
  () => [props.modelValue, props.server?.id],
  ([open]) => {
    if (open) {
      activeTab.value = 'status'
      status.value = null
      runStatus()
    }
  }
)

async function runStatus() {
  if (!props.server || running.value) return
  activeTab.value = 'status'
  running.value = true
  try {
    status.value = await xrayStatus(props.server.id, {
      logLines: logLines.value,
      logLevel: logLevel.value
    })
  } catch (e) {
    status.value = null
    toast.error('拉取状态失败: ' + ((e as Error).message ?? ''))
  } finally {
    running.value = false
  }
}

/** 切到日志 Tab; 共用 status.log 数据(同一接口已返回, 不重复请求) */
function showLogTab() {
  activeTab.value = 'log'
  if (!status.value) {
    runStatus()
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
    await xrayRestart(props.server.id)
    toast.success('✔ 已重启')
    await runStatus()
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
            @click="activeTab = 'status'"
          >
            <Activity class="w-4 h-4 mr-1" />状态
          </a>
          <a
            role="tab"
            class="tab"
            :class="{ 'tab-active': activeTab === 'log' }"
            @click="showLogTab"
          >
            <ScrollText class="w-4 h-4 mr-1" />日志
          </a>
        </div>
        <div class="flex gap-2 flex-wrap items-center">
          <!-- 日志选项: 行数 + 级别. 任一改动会重拉一次状态(同接口已含 log) -->
          <Select
            v-model="logLines"
            :options="LOG_LINES_OPTIONS"
            width="w-24"
            :disabled="running"
            @change="runStatus"
          />
          <Select
            v-model="logLevel"
            :options="LOG_LEVEL_OPTIONS"
            width="w-28"
            :disabled="running"
            @change="runStatus"
          />
          <button
            class="btn btn-sm btn-ghost gap-1"
            :disabled="running"
            @click="runStatus"
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
          <!-- 系统基本信息 -->
          <div class="bg-base-200 rounded p-3">
            <div class="text-xs font-semibold text-base-content/60 mb-2 flex items-center gap-1">
              <Server class="w-3.5 h-3.5" /> 系统信息
            </div>
            <div class="grid grid-cols-2 sm:grid-cols-4 gap-3 text-sm">
              <div>
                <div class="text-xs text-base-content/50">主机名</div>
                <div class="font-mono text-xs truncate" :title="status.hostname">{{ status.hostname || '-' }}</div>
              </div>
              <div>
                <div class="text-xs text-base-content/50">时区</div>
                <div class="font-mono text-xs">{{ status.timezone || '-' }}</div>
              </div>
              <div class="col-span-2">
                <div class="text-xs text-base-content/50">操作系统 / 内核</div>
                <div class="text-xs">{{ status.osRelease || '-' }} <span class="text-base-content/40 font-mono">{{ status.kernel }}</span></div>
              </div>
              <div class="col-span-2">
                <div class="text-xs text-base-content/50 flex items-center gap-1"><Timer class="w-3 h-3" /> 系统已运行</div>
                <div class="text-xs">{{ status.systemUptime || '-' }}</div>
              </div>
              <div class="col-span-2">
                <div class="text-xs text-base-content/50 flex items-center gap-1"><Cpu class="w-3 h-3" /> 负载均值 (1/5/15min)</div>
                <div class="font-mono text-xs">{{ status.loadAvg || '-' }}</div>
              </div>
              <div>
                <div class="text-xs text-base-content/50 flex items-center gap-1"><MemoryStick class="w-3 h-3" /> 内存</div>
                <div class="font-mono text-xs">{{ status.memory || '-' }}</div>
              </div>
              <div>
                <div class="text-xs text-base-content/50 flex items-center gap-1"><HardDrive class="w-3 h-3" /> 磁盘 (/)</div>
                <div class="font-mono text-xs">{{ status.disk || '-' }}</div>
              </div>
            </div>
          </div>

          <!-- Xray 服务 -->
          <div class="bg-base-200 rounded p-3">
            <div class="text-xs font-semibold text-base-content/60 mb-2 flex items-center gap-1">
              <Activity class="w-3.5 h-3.5" /> Xray 服务
            </div>
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-3 text-sm">
              <div>
                <div class="text-xs text-base-content/50">运行状态</div>
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
                <pre class="font-mono text-xs whitespace-pre-wrap break-all m-0">{{ status.listening || '(未捕获)' }}</pre>
              </div>
            </div>
          </div>

          <!-- 日志预览 (与下方"日志 Tab"同源) -->
          <div>
            <div class="text-xs text-base-content/50 mb-1">最近日志</div>
            <pre
              class="text-xs max-h-72 overflow-auto bg-neutral text-neutral-content px-4 py-3 rounded font-mono whitespace-pre-wrap break-all leading-relaxed"
            ><code v-if="status.log">{{ status.log }}</code><span v-else class="text-neutral-content/50">(无日志)</span></pre>
          </div>
        </div>
        <div v-else class="py-8 text-center text-base-content/40">
          点上方"刷新"按钮触发
        </div>
      </div>

      <div v-else>
        <pre
          class="text-xs max-h-[32rem] overflow-auto bg-neutral text-neutral-content px-4 py-3 rounded font-mono whitespace-pre-wrap break-all leading-relaxed min-h-32"
        ><span v-if="running"><span class="loading loading-spinner loading-xs mr-2"></span>拉取中...</span><code v-else-if="status?.log">{{ status.log }}</code><span v-else class="text-neutral-content/50">点"刷新"拉日志</span></pre>
      </div>
    </div>
    <div class="modal-backdrop bg-black/40" @click="close"></div>

    <ServerInstallDialog v-model="installOpen" :server="server" @installed="onInstalled" />
  </dialog>
</template>
