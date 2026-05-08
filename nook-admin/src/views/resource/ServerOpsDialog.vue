<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Activity, Cpu, HardDrive, MemoryStick, Power, RefreshCw, Rocket, ScrollText, Server, Timer } from 'lucide-vue-next'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import {
  getServerSystemInfo,
  getXrayLog,
  getXrayServiceStatus,
  xrayRestart,
  type ServerSystemInfo,
  type XrayLog,
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
/** status tab 的拉取 (并发拉 system + service); 与 log tab 独立, 互不阻塞。 */
const statusLoading = ref(false)
const logLoading = ref(false)
const restarting = ref(false)

const systemInfo = ref<ServerSystemInfo | null>(null)
const serviceStatus = ref<XrayServiceStatus | null>(null)
const xrayLog = ref<XrayLog | null>(null)

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
      systemInfo.value = null
      serviceStatus.value = null
      xrayLog.value = null
      runStatus()
    }
  }
)

/**
 * 拉系统信息 + Xray 服务状态; 两个独立接口并发, 单边失败不阻塞另一边。
 * 任一失败仅 toast 提示, 字段保持 null 让模板回落到 "-".
 */
async function runStatus() {
  if (!props.server || statusLoading.value) return
  statusLoading.value = true
  const id = props.server.id
  const [sysRes, svcRes] = await Promise.allSettled([
    getServerSystemInfo(id),
    getXrayServiceStatus(id)
  ])
  if (sysRes.status === 'fulfilled') {
    systemInfo.value = sysRes.value
  } else {
    systemInfo.value = null
    toast.error('拉系统信息失败: ' + ((sysRes.reason as Error)?.message ?? ''))
  }
  if (svcRes.status === 'fulfilled') {
    serviceStatus.value = svcRes.value
  } else {
    serviceStatus.value = null
    toast.error('拉 Xray 服务状态失败: ' + ((svcRes.reason as Error)?.message ?? ''))
  }
  statusLoading.value = false
}

/** 拉日志, 独立请求; 用户在 log tab 切行数/级别会触发。 */
async function runLog() {
  if (!props.server || logLoading.value) return
  logLoading.value = true
  try {
    xrayLog.value = await getXrayLog(props.server.id, {
      lines: logLines.value,
      level: logLevel.value
    })
  } catch (e) {
    xrayLog.value = null
    toast.error('拉日志失败: ' + ((e as Error).message ?? ''))
  } finally {
    logLoading.value = false
  }
}

/** 切到日志 Tab; 首次进入或参数变更后拉一次。 */
function showLogTab() {
  activeTab.value = 'log'
  if (!xrayLog.value) runLog()
}

async function runRestart() {
  if (!props.server || restarting.value) return
  const ok = await confirm({
    title: '重启 Xray',
    message: `确认在 "${props.server.name}" 上 systemctl restart xray？所有客户端会断开重连(1-2 秒).`,
    type: 'warning',
    confirmText: '重启'
  })
  if (!ok) return
  restarting.value = true
  try {
    await xrayRestart(props.server.id)
    toast.success('✔ 已重启')
    await runStatus()
  } catch (e) {
    toast.error('重启失败: ' + ((e as Error).message ?? ''))
  } finally {
    restarting.value = false
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
  const a = serviceStatus.value?.active?.trim() ?? ''
  if (a === 'active') return { text: '运行中', cls: 'badge-success' }
  if (a === 'inactive') return { text: '未运行', cls: 'badge-error' }
  if (a === 'failed') return { text: '失败', cls: 'badge-error' }
  if (!a) return { text: '未知', cls: 'badge-ghost' }
  return { text: a, cls: 'badge-warning' }
})

/** 顶栏统一 disabled 信号: 任意拉取或重启进行中都禁用按钮 */
const anyBusy = computed(() => statusLoading.value || logLoading.value || restarting.value)
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
          <!-- 日志选项: 仅在 log tab 才有意义; 切换会触发 runLog 单独刷新 -->
          <template v-if="activeTab === 'log'">
            <Select
              v-model="logLines"
              :options="LOG_LINES_OPTIONS"
              width="w-24"
              :disabled="logLoading"
              @change="runLog"
            />
            <Select
              v-model="logLevel"
              :options="LOG_LEVEL_OPTIONS"
              width="w-28"
              :disabled="logLoading"
              @change="runLog"
            />
            <button
              class="btn btn-sm btn-ghost gap-1"
              :disabled="logLoading"
              @click="runLog"
            >
              <RefreshCw class="w-4 h-4" />刷新日志
            </button>
          </template>
          <button
            v-else
            class="btn btn-sm btn-ghost gap-1"
            :disabled="statusLoading"
            @click="runStatus"
          >
            <RefreshCw class="w-4 h-4" />刷新状态
          </button>
          <button
            class="btn btn-sm btn-warning btn-outline gap-1"
            :disabled="anyBusy"
            @click="runRestart"
          >
            <Power class="w-4 h-4" />重启 Xray
          </button>
          <button
            class="btn btn-sm btn-primary gap-1"
            :disabled="anyBusy"
            @click="openInstall"
          >
            <Rocket class="w-4 h-4" />部署/重装
          </button>
        </div>
      </div>

      <div v-if="activeTab === 'status'">
        <div v-if="statusLoading && !systemInfo && !serviceStatus" class="py-12 text-center">
          <span class="loading loading-spinner"></span>
        </div>
        <div v-else class="space-y-3">
          <!-- 系统基本信息 -->
          <div class="bg-base-200 rounded p-3">
            <div class="text-xs font-semibold text-base-content/60 mb-2 flex items-center gap-1">
              <Server class="w-3.5 h-3.5" /> 系统信息
            </div>
            <div v-if="systemInfo" class="grid grid-cols-2 sm:grid-cols-4 gap-3 text-sm">
              <div>
                <div class="text-xs text-base-content/50">主机名</div>
                <div class="font-mono text-xs truncate" :title="systemInfo.hostname">{{ systemInfo.hostname || '-' }}</div>
              </div>
              <div>
                <div class="text-xs text-base-content/50">时区</div>
                <div class="font-mono text-xs">{{ systemInfo.timezone || '-' }}</div>
              </div>
              <div class="col-span-2">
                <div class="text-xs text-base-content/50">操作系统 / 内核</div>
                <div class="text-xs">{{ systemInfo.osRelease || '-' }} <span class="text-base-content/40 font-mono">{{ systemInfo.kernel }}</span></div>
              </div>
              <div class="col-span-2">
                <div class="text-xs text-base-content/50 flex items-center gap-1"><Timer class="w-3 h-3" /> 系统已运行</div>
                <div class="text-xs">{{ systemInfo.systemUptime || '-' }}</div>
              </div>
              <div class="col-span-2">
                <div class="text-xs text-base-content/50 flex items-center gap-1"><Cpu class="w-3 h-3" /> 负载均值 (1/5/15min)</div>
                <div class="font-mono text-xs">{{ systemInfo.loadAvg || '-' }}</div>
              </div>
              <div>
                <div class="text-xs text-base-content/50 flex items-center gap-1"><MemoryStick class="w-3 h-3" /> 内存</div>
                <div class="font-mono text-xs">{{ systemInfo.memory || '-' }}</div>
              </div>
              <div>
                <div class="text-xs text-base-content/50 flex items-center gap-1"><HardDrive class="w-3 h-3" /> 磁盘 (/)</div>
                <div class="font-mono text-xs">{{ systemInfo.disk || '-' }}</div>
              </div>
            </div>
            <div v-else class="text-xs text-base-content/40 py-2">(未获取到)</div>
          </div>

          <!-- Xray 服务 -->
          <div class="bg-base-200 rounded p-3">
            <div class="text-xs font-semibold text-base-content/60 mb-2 flex items-center gap-1">
              <Activity class="w-3.5 h-3.5" /> Xray 服务
            </div>
            <div v-if="serviceStatus" class="grid grid-cols-1 sm:grid-cols-2 gap-3 text-sm">
              <div>
                <div class="text-xs text-base-content/50">运行状态</div>
                <span :class="['badge badge-sm', activeBadge.cls]">{{ activeBadge.text }}</span>
              </div>
              <div>
                <div class="text-xs text-base-content/50">Xray 版本</div>
                <div class="font-mono text-xs">{{ serviceStatus.version || '-' }}</div>
              </div>
              <div class="sm:col-span-2">
                <div class="text-xs text-base-content/50">启动时间</div>
                <div class="text-xs">{{ serviceStatus.uptimeFrom || '-' }}</div>
              </div>
              <div class="sm:col-span-2">
                <div class="text-xs text-base-content/50">监听端口</div>
                <pre class="font-mono text-xs whitespace-pre-wrap break-all m-0">{{ serviceStatus.listening || '(未捕获)' }}</pre>
              </div>
            </div>
            <div v-else class="text-xs text-base-content/40 py-2">(未获取到)</div>
          </div>
        </div>
      </div>

      <div v-else>
        <pre
          class="text-xs max-h-[32rem] overflow-auto bg-neutral text-neutral-content px-4 py-3 rounded font-mono whitespace-pre-wrap break-all leading-relaxed min-h-32"
        ><span v-if="logLoading"><span class="loading loading-spinner loading-xs mr-2"></span>拉取中...</span><code v-else-if="xrayLog?.log">{{ xrayLog.log }}</code><span v-else class="text-neutral-content/50">点"刷新日志"拉取</span></pre>
      </div>
    </div>
    <div class="modal-backdrop bg-black/40" @click="close"></div>

    <ServerInstallDialog v-model="installOpen" :server="server" @installed="onInstalled" />
  </dialog>
</template>
