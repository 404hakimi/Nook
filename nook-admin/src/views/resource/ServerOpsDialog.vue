<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  Activity,
  Cpu,
  HardDrive,
  MemoryStick,
  Power,
  PowerOff,
  RefreshCw,
  Rocket,
  Server,
  Timer
} from 'lucide-vue-next'
import {
  NButton,
  NCard,
  NIcon,
  NModal,
  NSelect,
  NSpace,
  NSpin,
  NTabPane,
  NTabs,
  NTag,
  useMessage
} from 'naive-ui'
import { useConfirm } from '@/composables/useConfirm'
import {
  getServerSystemInfo,
  getXrayLog,
  getXrayServiceStatus,
  xrayAutostart,
  xrayRestart,
  type ServerSystemInfo,
  type XrayLog,
  type XrayLogLevel,
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

const message = useMessage()
const { confirm } = useConfirm()

type Tab = 'status' | 'log'
const activeTab = ref<Tab>('status')
/** status tab 的拉取 (并发拉 system + service); 与 log tab 独立, 互不阻塞。 */
const statusLoading = ref(false)
const logLoading = ref(false)
const restarting = ref(false)
const togglingAutostart = ref(false)

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
    message.error('拉系统信息失败: ' + ((sysRes.reason as Error)?.message ?? ''))
  }
  if (svcRes.status === 'fulfilled') {
    serviceStatus.value = svcRes.value
  } else {
    serviceStatus.value = null
    message.error('拉 Xray 服务状态失败: ' + ((svcRes.reason as Error)?.message ?? ''))
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
    message.error('拉日志失败: ' + ((e as Error).message ?? ''))
  } finally {
    logLoading.value = false
  }
}

/** 切到日志 Tab; 首次进入或参数变更后拉一次。 */
function onTabChange(tab: Tab) {
  activeTab.value = tab
  if (tab === 'log' && !xrayLog.value) runLog()
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
    message.success('已重启')
    await runStatus()
  } catch (e) {
    message.error('重启失败: ' + ((e as Error).message ?? ''))
  } finally {
    restarting.value = false
  }
}

/**
 * 切换 Xray 开机自启 (systemctl enable/disable); 切完会重新拉一次状态以拿最新 is-enabled 结果.
 * 当前 enabled 字符串是 unknown/空时, 默认按"开启"语义执行.
 */
async function runToggleAutostart() {
  if (!props.server || togglingAutostart.value) return
  const currentlyEnabled = serviceStatus.value?.enabled?.trim() === 'enabled'
  const target = !currentlyEnabled
  const ok = await confirm({
    title: target ? '开启开机自启' : '关闭开机自启',
    message: target
      ? `确认在 "${props.server.name}" 上 systemctl enable xray？`
      : `确认在 "${props.server.name}" 上 systemctl disable xray？关闭后服务器重启不会自动起 Xray.`,
    type: target ? 'info' : 'warning',
    confirmText: target ? '开启' : '关闭'
  })
  if (!ok) return
  togglingAutostart.value = true
  try {
    await xrayAutostart(props.server.id, target)
    message.success(target ? '已开启开机自启' : '已关闭开机自启')
    await runStatus()
  } catch (e) {
    message.error('切换失败: ' + ((e as Error).message ?? ''))
  } finally {
    togglingAutostart.value = false
  }
}

function openInstall() {
  installOpen.value = true
}

async function onInstalled() {
  message.success('安装完成,正在刷新状态')
  await runStatus()
}

function close() {
  emit('update:modelValue', false)
}

const activeBadge = computed<{ text: string; type: 'success' | 'error' | 'warning' | 'default' }>(
  () => {
    const a = serviceStatus.value?.active?.trim() ?? ''
    if (a === 'active') return { text: '运行中', type: 'success' }
    if (a === 'inactive') return { text: '未运行', type: 'error' }
    if (a === 'failed') return { text: '失败', type: 'error' }
    if (!a) return { text: '未知', type: 'default' }
    return { text: a, type: 'warning' }
  }
)

/** is-enabled 字符串 → 中文徽章; static / masked / 空都归到默认色, 与 enable/disable 主路径区分. */
const autostartBadge = computed<{ text: string; type: 'success' | 'error' | 'warning' | 'default' }>(
  () => {
    const e = serviceStatus.value?.enabled?.trim() ?? ''
    if (e === 'enabled') return { text: '已启用', type: 'success' }
    if (e === 'disabled') return { text: '未启用', type: 'warning' }
    if (e === 'static') return { text: 'static', type: 'default' }
    if (e === 'masked') return { text: 'masked', type: 'error' }
    if (!e) return { text: '未知', type: 'default' }
    return { text: e, type: 'default' }
  }
)
const autostartCurrentlyEnabled = computed(
  () => serviceStatus.value?.enabled?.trim() === 'enabled'
)

/** 顶栏统一 disabled 信号: 任意拉取或重启 / 切自启进行中都禁用按钮 */
const anyBusy = computed(
  () => statusLoading.value || logLoading.value || restarting.value || togglingAutostart.value
)
</script>

<template>
  <NModal
    :show="modelValue"
    preset="card"
    style="max-width: 56rem"
    :bordered="false"
    :mask-closable="true"
    @update:show="(v: boolean) => emit('update:modelValue', v)"
  >
    <template #header>
      <span>Xray 管理</span>
    </template>
    <template #header-extra>
      <span v-if="server" class="text-xs text-zinc-500">
        {{ server.name }} ({{ server.host }})
      </span>
    </template>

    <div class="flex items-center justify-between mb-3 gap-2 flex-wrap">
      <NTabs
        :value="activeTab"
        type="line"
        size="small"
        @update:value="(v: Tab) => onTabChange(v)"
      >
        <NTabPane name="status" tab="状态" />
        <NTabPane name="log" tab="日志" />
      </NTabs>
      <div class="flex gap-2 flex-wrap items-center">
        <!-- 日志选项: 仅在 log tab 才有意义; 切换会触发 runLog 单独刷新 -->
        <template v-if="activeTab === 'log'">
          <NSelect
            v-model:value="logLines"
            :options="LOG_LINES_OPTIONS"
            size="small"
            class="w-24"
            :disabled="logLoading"
            @update:value="runLog"
          />
          <NSelect
            v-model:value="logLevel"
            :options="LOG_LEVEL_OPTIONS"
            size="small"
            class="w-28"
            :disabled="logLoading"
            @update:value="runLog"
          />
          <NButton quaternary size="small" :disabled="logLoading" @click="runLog">
            <template #icon><NIcon><RefreshCw /></NIcon></template>
            刷新日志
          </NButton>
        </template>
        <NButton
          v-else
          quaternary
          size="small"
          :disabled="statusLoading"
          @click="runStatus"
        >
          <template #icon><NIcon><RefreshCw /></NIcon></template>
          刷新状态
        </NButton>
        <NButton
          size="small"
          :type="autostartCurrentlyEnabled ? 'default' : 'success'"
          :disabled="anyBusy"
          :loading="togglingAutostart"
          :title="autostartCurrentlyEnabled
            ? 'systemctl disable xray (重启不再自动起)'
            : 'systemctl enable xray (重启自动起)'"
          @click="runToggleAutostart"
        >
          <template #icon>
            <NIcon><PowerOff v-if="autostartCurrentlyEnabled" /><Power v-else /></NIcon>
          </template>
          {{ autostartCurrentlyEnabled ? '关闭自启' : '开启自启' }}
        </NButton>
        <NButton type="warning" size="small" :disabled="anyBusy" @click="runRestart">
          <template #icon><NIcon><Power /></NIcon></template>
          重启 Xray
        </NButton>
        <NButton type="primary" size="small" :disabled="anyBusy" @click="openInstall">
          <template #icon><NIcon><Rocket /></NIcon></template>
          部署/重装
        </NButton>
      </div>
    </div>

    <div v-if="activeTab === 'status'">
      <NSpin :show="statusLoading && !systemInfo && !serviceStatus">
        <div class="space-y-3 min-h-[8rem]">
          <!-- 系统基本信息 -->
          <NCard size="small">
            <div class="text-xs font-semibold text-zinc-500 mb-2 flex items-center gap-1">
              <NIcon :size="14"><Server /></NIcon>
              系统信息
            </div>
            <div v-if="systemInfo" class="grid grid-cols-2 sm:grid-cols-4 gap-3 text-sm">
              <div>
                <div class="text-xs text-zinc-500">主机名</div>
                <div class="font-mono text-xs truncate" :title="systemInfo.hostname">
                  {{ systemInfo.hostname || '-' }}
                </div>
              </div>
              <div>
                <div class="text-xs text-zinc-500">时区</div>
                <div class="font-mono text-xs">{{ systemInfo.timezone || '-' }}</div>
              </div>
              <div class="col-span-2">
                <div class="text-xs text-zinc-500">操作系统 / 内核</div>
                <div class="text-xs">
                  {{ systemInfo.osRelease || '-' }}
                  <span class="text-zinc-400 font-mono">{{ systemInfo.kernel }}</span>
                </div>
              </div>
              <div class="col-span-2">
                <div class="text-xs text-zinc-500 flex items-center gap-1">
                  <NIcon :size="12"><Timer /></NIcon> 系统已运行
                </div>
                <div class="text-xs">{{ systemInfo.systemUptime || '-' }}</div>
              </div>
              <div class="col-span-2">
                <div class="text-xs text-zinc-500 flex items-center gap-1">
                  <NIcon :size="12"><Cpu /></NIcon> 负载均值 (1/5/15min)
                </div>
                <div class="font-mono text-xs">{{ systemInfo.loadAvg || '-' }}</div>
              </div>
              <div>
                <div class="text-xs text-zinc-500 flex items-center gap-1">
                  <NIcon :size="12"><MemoryStick /></NIcon> 内存
                </div>
                <div class="font-mono text-xs">{{ systemInfo.memory || '-' }}</div>
              </div>
              <div>
                <div class="text-xs text-zinc-500 flex items-center gap-1">
                  <NIcon :size="12"><HardDrive /></NIcon> 磁盘 (/)
                </div>
                <div class="font-mono text-xs">{{ systemInfo.disk || '-' }}</div>
              </div>
            </div>
            <div v-else class="text-xs text-zinc-400 py-2">(未获取到)</div>
          </NCard>

          <!-- Xray 服务 -->
          <NCard size="small">
            <div class="text-xs font-semibold text-zinc-500 mb-2 flex items-center gap-1">
              <NIcon :size="14"><Activity /></NIcon>
              Xray 服务
            </div>
            <div
              v-if="serviceStatus"
              class="grid grid-cols-1 sm:grid-cols-2 gap-3 text-sm"
            >
              <div>
                <div class="text-xs text-zinc-500">运行状态</div>
                <NTag size="small" :type="activeBadge.type">{{ activeBadge.text }}</NTag>
              </div>
              <div>
                <div class="text-xs text-zinc-500">开机自启</div>
                <NTag size="small" :type="autostartBadge.type">{{ autostartBadge.text }}</NTag>
              </div>
              <div>
                <div class="text-xs text-zinc-500">Xray 版本</div>
                <div class="font-mono text-xs">{{ serviceStatus.version || '-' }}</div>
              </div>
              <div class="sm:col-span-2">
                <div class="text-xs text-zinc-500">启动时间</div>
                <div class="text-xs">{{ serviceStatus.uptimeFrom || '-' }}</div>
              </div>
              <div class="sm:col-span-2">
                <div class="text-xs text-zinc-500">监听端口</div>
                <pre class="font-mono text-xs whitespace-pre-wrap break-all m-0">{{
                  serviceStatus.listening || '(未捕获)'
                }}</pre>
              </div>
            </div>
            <div v-else class="text-xs text-zinc-400 py-2">(未获取到)</div>
          </NCard>
        </div>
      </NSpin>
    </div>

    <div v-else>
      <NSpin :show="logLoading && !xrayLog">
        <pre
          class="text-xs max-h-[32rem] overflow-auto bg-zinc-900 text-zinc-100 px-4 py-3 rounded font-mono whitespace-pre-wrap break-all leading-relaxed min-h-32"
        ><code v-if="xrayLog?.log">{{ xrayLog.log }}</code><span v-else class="text-zinc-500">{{ logLoading ? '拉取中...' : '点"刷新日志"拉取' }}</span></pre>
      </NSpin>
    </div>

    <template #footer>
      <NSpace justify="end">
        <NButton size="small" @click="close">关闭</NButton>
      </NSpace>
    </template>

    <ServerInstallDialog v-model="installOpen" :server="server" @installed="onInstalled" />
  </NModal>
</template>
