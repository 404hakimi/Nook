<script setup lang="ts">
import { computed, h, ref, watch } from 'vue'
import { Activity, CheckCircle2, RefreshCw, ShieldAlert } from 'lucide-vue-next'
import {
  NButton,
  NCard,
  NDataTable,
  NIcon,
  NModal,
  NSelect,
  NSpace,
  NSpin,
  NTabPane,
  NTabs,
  NTag,
  useMessage,
  type DataTableColumns
} from 'naive-ui'
import {
  getXrayLog,
  getXrayServiceStatus,
  type XrayLog,
  type XrayLogLevel,
  type XrayServiceStatus
} from '@/api/xray/server'
import { getSyncStatus, type SyncStatus } from '@/api/xray/client'
import { getSlotPoolView, type XrayNode, type XraySlotItem } from '@/api/xray/node'

interface Props {
  modelValue: boolean
  node?: XrayNode | null
}
const props = defineProps<Props>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
}>()

const message = useMessage()

type Tab = 'status' | 'slots' | 'log'
const activeTab = ref<Tab>('status')

const statusLoading = ref(false)
const slotsLoading = ref(false)
const logLoading = ref(false)
const syncStatusLoading = ref(false)

const serviceStatus = ref<XrayServiceStatus | null>(null)
const slots = ref<XraySlotItem[]>([])
const xrayLog = ref<XrayLog | null>(null)
const syncStatus = ref<SyncStatus | null>(null)

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

watch(
  () => [props.modelValue, props.node?.serverId],
  ([open]) => {
    if (open) {
      activeTab.value = 'status'
      serviceStatus.value = null
      slots.value = []
      xrayLog.value = null
      syncStatus.value = null
      runStatus()
    }
  }
)

async function runStatus() {
  if (!props.node || statusLoading.value) return
  statusLoading.value = true
  try {
    serviceStatus.value = await getXrayServiceStatus(props.node.serverId)
  } catch (e) {
    serviceStatus.value = null
    message.error('拉 Xray 服务状态失败: ' + ((e as Error).message ?? ''))
  } finally {
    statusLoading.value = false
  }
}

async function runLog() {
  if (!props.node || logLoading.value) return
  logLoading.value = true
  try {
    xrayLog.value = await getXrayLog(props.node.serverId, {
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

async function runSlots() {
  if (!props.node || slotsLoading.value) return
  slotsLoading.value = true
  try {
    slots.value = await getSlotPoolView(props.node.serverId)
  } catch (e) {
    slots.value = []
    message.error('拉 Slot 占用失败: ' + ((e as Error).message ?? ''))
  } finally {
    slotsLoading.value = false
  }
}

function onTabChange(tab: Tab) {
  activeTab.value = tab
  if (tab === 'slots' && slots.value.length === 0 && !slotsLoading.value) runSlots()
  if (tab === 'log' && !xrayLog.value) runLog()
}

// ===== Slot 表格列定义 =====
const CLIENT_STATUS_META: Record<number, { label: string; type: 'success' | 'warning' | 'error' | 'default' }> = {
  1: { label: '运行', type: 'success' },
  2: { label: '已停', type: 'default' },
  3: { label: '待同步', type: 'warning' },
  4: { label: '远端缺失', type: 'error' }
}

const slotColumns = computed<DataTableColumns<XraySlotItem>>(() => [
  {
    title: '槽位',
    key: 'slotIndex',
    width: 70,
    render: (r) => h('span', { class: 'font-mono text-xs' }, r.slotIndex)
  },
  {
    title: '端口',
    key: 'listenPort',
    width: 90,
    render: (r) => h('span', { class: 'font-mono text-xs' }, r.listenPort)
  },
  {
    title: '状态',
    key: 'used',
    width: 90,
    render: (r) =>
      r.used
        ? h(NTag, { size: 'small', type: 'info' }, { default: () => '已占用' })
        : h(NTag, { size: 'small', type: 'default' }, { default: () => '空闲' })
  },
  {
    title: '协议',
    key: 'protocol',
    width: 100,
    render: (r) =>
      r.protocol
        ? h(
            'span',
            { class: 'font-mono text-xs' },
            r.transport ? `${r.protocol}+${r.transport}` : r.protocol
          )
        : h('span', { class: 'text-zinc-400 text-xs' }, '-')
  },
  {
    title: '客户',
    key: 'client',
    render: (r) => {
      if (!r.used) return h('span', { class: 'text-zinc-400 text-xs' }, '-')
      const status = r.clientStatus != null ? CLIENT_STATUS_META[r.clientStatus] : null
      return h('div', { class: 'flex items-center gap-2' }, [
        h(
          'span',
          { class: 'text-xs', title: r.clientId || '' },
          r.clientEmail || r.clientId || '(已删)'
        ),
        status
          ? h(NTag, { size: 'small', type: status.type, bordered: false }, { default: () => status.label })
          : null
      ])
    }
  }
])

async function runSyncStatus() {
  if (!props.node || syncStatusLoading.value) return
  syncStatusLoading.value = true
  try {
    syncStatus.value = await getSyncStatus(props.node.serverId)
  } catch (e) {
    syncStatus.value = null
    message.error('对账失败: ' + ((e as Error).message ?? ''))
  } finally {
    syncStatusLoading.value = false
  }
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
      <span>Xray 节点详情</span>
    </template>
    <template #header-extra>
      <span v-if="node" class="text-xs text-zinc-500">
        {{ node.serverName || node.serverId }} <span v-if="node.serverHost">({{ node.serverHost }})</span>
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
        <NTabPane name="slots" tab="Slot 占用" />
        <NTabPane name="log" tab="日志" />
      </NTabs>
      <div class="flex gap-2 flex-wrap items-center">
        <NButton
          v-if="activeTab === 'slots'"
          quaternary
          size="small"
          :disabled="slotsLoading"
          @click="runSlots"
        >
          <template #icon><NIcon><RefreshCw /></NIcon></template>
          刷新 Slot
        </NButton>
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
        <NButton v-else quaternary size="small" :disabled="statusLoading" @click="runStatus">
          <template #icon><NIcon><RefreshCw /></NIcon></template>
          刷新状态
        </NButton>
      </div>
    </div>

    <div v-if="activeTab === 'status'">
      <NSpin :show="statusLoading && !serviceStatus">
        <div class="space-y-3 min-h-[8rem]">
          <NCard size="small">
            <div class="text-xs font-semibold text-zinc-500 mb-2 flex items-center gap-1">
              <NIcon :size="14"><Activity /></NIcon>
              Xray 服务
            </div>
            <div v-if="serviceStatus" class="grid grid-cols-1 sm:grid-cols-2 gap-3 text-sm">
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

          <NCard size="small">
            <div class="flex items-center justify-between mb-2">
              <div class="text-xs font-semibold text-zinc-500 flex items-center gap-1">
                <NIcon :size="14"><ShieldAlert /></NIcon>
                远端 vs DB 对账
              </div>
              <NButton
                quaternary
                size="tiny"
                :loading="syncStatusLoading"
                @click="runSyncStatus"
              >
                <template #icon><NIcon><RefreshCw /></NIcon></template>
                {{ syncStatus ? '重新对账' : '开始对账' }}
              </NButton>
            </div>

            <div v-if="!syncStatus" class="text-xs text-zinc-400 py-2">
              点"开始对账"拉远端 inbound list 跟 DB 比对; 不自动触发以免 1c2g 机型 SSH 拥塞.
            </div>
            <div v-else class="space-y-2 text-sm">
              <div v-if="!syncStatus.reachable">
                <NTag size="small" type="error">不可达</NTag>
                <span class="text-xs text-zinc-500 ml-2">SSH 不通或 xray 未起, 跳过本次对账</span>
              </div>
              <template v-else>
                <div class="grid grid-cols-3 gap-2">
                  <div class="flex items-center gap-1">
                    <NIcon :size="14" color="var(--n-success-color)"><CheckCircle2 /></NIcon>
                    <span class="text-xs text-zinc-500">OK</span>
                    <span class="font-mono font-semibold">{{ syncStatus.okTags.length }}</span>
                  </div>
                  <div class="flex items-center gap-1">
                    <span class="text-xs text-zinc-500">DB 有远端无</span>
                    <span
                      class="font-mono font-semibold"
                      :style="syncStatus.staleDbTags.length > 0 ? 'color: var(--n-warning-color)' : ''"
                    >{{ syncStatus.staleDbTags.length }}</span>
                  </div>
                  <div class="flex items-center gap-1">
                    <span class="text-xs text-zinc-500">远端孤儿</span>
                    <span
                      class="font-mono font-semibold"
                      :style="syncStatus.orphanRemoteTags.length > 0 ? 'color: var(--n-info-color)' : ''"
                    >{{ syncStatus.orphanRemoteTags.length }}</span>
                  </div>
                </div>

                <div
                  v-if="syncStatus.staleDbTags.length > 0"
                  class="text-xs"
                  style="color: var(--n-warning-color)"
                >
                  缺失: {{ syncStatus.staleDbTags.join(', ') }}
                  <span class="text-zinc-500 ml-1">- 关闭弹框后点行内"Replay"恢复</span>
                </div>
                <div
                  v-if="syncStatus.orphanRemoteTags.length > 0"
                  class="text-xs text-zinc-500"
                >
                  孤儿: {{ syncStatus.orphanRemoteTags.join(', ') }}
                  <span class="text-zinc-400 ml-1">- 当前不自动清理</span>
                </div>
                <div
                  v-if="syncStatus.staleDbTags.length === 0 && syncStatus.orphanRemoteTags.length === 0"
                  class="text-xs"
                  style="color: var(--n-success-color)"
                >
                  ✓ 已对齐
                </div>
              </template>
            </div>
          </NCard>
        </div>
      </NSpin>
    </div>

    <div v-else-if="activeTab === 'slots'">
      <NSpin :show="slotsLoading && slots.length === 0">
        <NCard size="small" :content-style="{ padding: 0 }">
          <NDataTable
            :columns="slotColumns"
            :data="slots"
            :loading="slotsLoading"
            :bordered="false"
            :row-key="(r: XraySlotItem) => r.slotIndex"
            size="small"
            :max-height="480"
          />
        </NCard>
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
  </NModal>
</template>
