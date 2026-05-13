<script setup lang="ts">
import { computed, h, onMounted, reactive, ref, type Component } from 'vue'
import { Eye, Power, PowerOff, RefreshCcw, Rocket, RotateCcw, Search } from 'lucide-vue-next'
import {
  NButton,
  NCard,
  NDataTable,
  NIcon,
  NInput,
  NTag,
  NTooltip,
  useMessage,
  type DataTableColumns
} from 'naive-ui'
import { useConfirm } from '@/composables/useConfirm'
import { pageXrayNode, type XrayNode, type XrayNodePageQuery } from '@/api/xray/node'
import {
  getXrayServiceStatus,
  xrayAutostart,
  xrayRestart
} from '@/api/xray/server'
import { replayServer, type ReplayReport } from '@/api/xray/client'
import { formatDateTime } from '@/utils/date'
import ServerInstallDialog from '@/views/resource/ServerInstallDialog.vue'
import XrayNodeOpsDialog from './XrayNodeOpsDialog.vue'

const message = useMessage()
const { confirm } = useConfirm()

const query = reactive<Required<Pick<XrayNodePageQuery, 'pageNo' | 'pageSize'>> & XrayNodePageQuery>({
  pageNo: 1,
  pageSize: 20,
  serverId: '',
  xrayVersion: ''
})
const list = ref<XrayNode[]>([])
const total = ref(0)
const loading = ref(false)

/** 行级 loading 状态: serverId → 当前进行中的操作 (null = 空闲); 用于按钮 loading + disabled */
type RowOp = 'restart' | 'autostart' | 'replay'
const rowBusy = ref<Record<string, RowOp | null>>({})

async function loadList() {
  loading.value = true
  try {
    const res = await pageXrayNode({
      pageNo: query.pageNo,
      pageSize: query.pageSize,
      serverId: query.serverId || undefined,
      xrayVersion: query.xrayVersion || undefined
    })
    const maxPage = res.total > 0 ? Math.ceil(res.total / query.pageSize) : 1
    if (query.pageNo > maxPage) {
      query.pageNo = maxPage
      loading.value = false
      await loadList()
      return
    }
    list.value = res.records
    total.value = res.total
  } catch {
    /* request 拦截器已 toast */
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.pageNo = 1
  loadList()
}

function resetQuery() {
  query.pageNo = 1
  query.serverId = ''
  query.xrayVersion = ''
  loadList()
}

// ===== 部署 / 重装 (顶栏入口, 自带服务器选择器) =====
const installOpen = ref(false)
function openInstall() {
  installOpen.value = true
}
function onInstalled() {
  loadList()
}

// ===== 行内: 详情 (打开纯查看弹框) =====
const opsOpen = ref(false)
const opsTarget = ref<XrayNode | null>(null)
function openDetail(n: XrayNode) {
  opsTarget.value = n
  opsOpen.value = true
}

// ===== 行内: 重启 Xray =====
async function onRestart(row: XrayNode) {
  if (rowBusy.value[row.serverId]) return
  const label = row.serverName || row.serverId.slice(0, 12)
  const ok = await confirm({
    title: '重启 Xray',
    message: `重启 "${label}" 上的 Xray?`,
    type: 'warning',
    confirmText: '重启'
  })
  if (!ok) return
  rowBusy.value[row.serverId] = 'restart'
  try {
    await xrayRestart(row.serverId)
    message.success(`${label}: 已重启`)
  } catch (e) {
    message.error('重启失败: ' + ((e as Error).message ?? ''))
  } finally {
    rowBusy.value[row.serverId] = null
  }
}

// ===== 行内: 切换自启 (动态: 先拉 status 拿当前 enabled, 再 confirm 反转) =====
async function onToggleAutostart(row: XrayNode) {
  if (rowBusy.value[row.serverId]) return
  const label = row.serverName || row.serverId.slice(0, 12)
  rowBusy.value[row.serverId] = 'autostart'
  try {
    const status = await getXrayServiceStatus(row.serverId)
    const currentlyEnabled = status.enabled?.trim() === 'enabled'
    const target = !currentlyEnabled
    const ok = await confirm({
      title: target ? '开启开机自启' : '关闭开机自启',
      message: target
        ? `开启 "${label}" 的 Xray 开机自启?`
        : `关闭 "${label}" 的 Xray 开机自启?`,
      type: target ? 'info' : 'warning',
      confirmText: target ? '开启' : '关闭'
    })
    if (!ok) {
      rowBusy.value[row.serverId] = null
      return
    }
    await xrayAutostart(row.serverId, target)
    message.success(`${label}: ${target ? '已开启自启' : '已关闭自启'}`)
  } catch (e) {
    message.error('切换自启失败: ' + ((e as Error).message ?? ''))
  } finally {
    rowBusy.value[row.serverId] = null
  }
}

// ===== 行内: Replay 全部 client =====
async function onReplay(row: XrayNode) {
  if (rowBusy.value[row.serverId]) return
  const label = row.serverName || row.serverId.slice(0, 12)
  const ok = await confirm({
    title: 'Replay 全部 client',
    message: `将 "${label}" 上所有 client 重推到远端?`,
    type: 'warning',
    confirmText: '开始 Replay'
  })
  if (!ok) return
  rowBusy.value[row.serverId] = 'replay'
  try {
    const report: ReplayReport = await replayServer(row.serverId)
    const tip = `总 ${report.totalCount} · 已对齐 ${report.alreadyOkCount} · 推送 ${report.successCount}`
    if (report.failedClientIds.length === 0) {
      message.success(
        report.successCount === 0
          ? `${label}: Replay 跳过, 全部 ${report.alreadyOkCount} 个 client 远端已对齐`
          : `${label}: Replay 完成 (${tip})`
      )
    } else {
      message.warning(
        `${label}: Replay 部分失败 ${tip} · 失败 ${report.failedClientIds.length} (已标 status=3 等下轮自动重试)`
      )
    }
  } catch (e) {
    message.error('Replay 失败: ' + ((e as Error).message ?? ''))
  } finally {
    rowBusy.value[row.serverId] = null
  }
}

const columns = computed<DataTableColumns<XrayNode>>(() => [
  {
    title: '服务器',
    key: 'server',
    width: 220,
    render: (row) =>
      h('div', { class: 'flex flex-col gap-0.5' }, [
        h(
          'span',
          { class: 'font-medium text-sm', title: row.serverId },
          row.serverName || row.serverId.slice(0, 12)
        ),
        row.serverHost
          ? h('span', { class: 'font-mono text-xs text-zinc-500' }, row.serverHost)
          : null
      ])
  },
  {
    title: 'Xray 版本',
    key: 'xrayVersion',
    width: 130,
    render: (row) =>
      row.xrayVersion
        ? h(NTag, { size: 'small', type: 'info', bordered: false }, { default: () => row.xrayVersion })
        : h('span', { class: 'text-zinc-400 text-xs' }, '-')
  },
  {
    title: 'API 端口',
    key: 'xrayApiPort',
    width: 90,
    render: (row) => h('span', { class: 'font-mono text-xs' }, row.xrayApiPort ?? '-')
  },
  {
    title: 'Slot 池',
    key: 'slot',
    width: 130,
    render: (row) => {
      if (row.slotPoolSize == null && row.slotPortBase == null) {
        return h('span', { class: 'text-zinc-400 text-xs' }, '-')
      }
      const size = row.slotPoolSize ?? '-'
      const base = row.slotPortBase ?? '-'
      return h('span', { class: 'font-mono text-xs' }, `size=${size} / base=${base}`)
    }
  },
  {
    title: '安装目录',
    key: 'xrayInstallDir',
    ellipsis: { tooltip: true },
    render: (row) =>
      h(
        'span',
        { class: 'font-mono text-xs text-zinc-600 dark:text-zinc-400' },
        row.xrayInstallDir || '-'
      )
  },
  {
    title: '上次探测启动',
    key: 'lastXrayUptime',
    width: 160,
    render: (row) =>
      row.lastXrayUptime
        ? h('span', { class: 'text-xs' }, formatDateTime(row.lastXrayUptime))
        : h('span', { class: 'text-zinc-400 text-xs', title: '尚未探测 / 重装后清空' }, '-')
  },
  {
    title: '最近部署',
    key: 'installedAt',
    width: 160,
    render: (row) =>
      h(
        'span',
        { class: 'text-xs' },
        row.installedAt ? formatDateTime(row.installedAt) : '-'
      )
  },
  {
    title: '操作',
    key: 'actions',
    align: 'right',
    width: 320,
    render: (row) => {
      const busy = rowBusy.value[row.serverId]
      return h('div', { class: 'flex gap-1 justify-end flex-nowrap' }, [
        renderActionButton({
          tooltip: '查看 Xray 运行状态 / 监听端口 / 日志 / 对账',
          icon: Eye,
          label: '详情',
          disabled: !!busy,
          onClick: () => openDetail(row)
        }),
        renderActionButton({
          tooltip: '在远端 systemctl restart xray; 所有客户端会断开重连约 1-2 秒',
          icon: Power,
          label: '重启',
          buttonType: 'warning',
          loading: busy === 'restart',
          disabled: !!busy && busy !== 'restart',
          onClick: () => onRestart(row)
        }),
        renderActionButton({
          tooltip: '点击后先查 systemctl is-enabled 当前状态, 再确认切换 (enable ↔ disable)',
          icon: PowerOff,
          label: '切换自启',
          loading: busy === 'autostart',
          disabled: !!busy && busy !== 'autostart',
          onClick: () => onToggleAutostart(row)
        }),
        renderActionButton({
          tooltip: '按 DB 状态把所有 client 重推到远端; server 重启或部署后状态恢复用',
          icon: RotateCcw,
          label: 'Replay',
          loading: busy === 'replay',
          disabled: !!busy && busy !== 'replay',
          onClick: () => onReplay(row)
        })
      ])
    }
  }
])

interface ActionButtonOpts {
  tooltip: string
  icon: Component
  label: string
  buttonType?: 'default' | 'warning'
  loading?: boolean
  disabled?: boolean
  onClick: () => void
}

/** 统一按钮渲染: hover 显示 tooltip 详细说明, 按钮本身只放短文案 */
function renderActionButton(o: ActionButtonOpts) {
  return h(
    NTooltip,
    { placement: 'top', trigger: 'hover' },
    {
      trigger: () =>
        h(
          NButton,
          {
            size: 'tiny',
            quaternary: true,
            type: o.buttonType,
            loading: o.loading,
            disabled: o.disabled,
            onClick: o.onClick
          },
          {
            icon: () => h(NIcon, null, { default: () => h(o.icon) }),
            default: () => o.label
          }
        ),
      default: () => h('div', { class: 'text-xs max-w-72' }, o.tooltip)
    }
  )
}

const pagination = computed(() => ({
  page: query.pageNo,
  pageSize: query.pageSize,
  itemCount: total.value,
  pageSizes: [10, 20, 50, 100],
  showSizePicker: true,
  prefix: ({ itemCount }: { itemCount?: number }) => `共 ${itemCount ?? 0} 条`,
  onUpdatePage: (p: number) => {
    query.pageNo = p
    loadList()
  },
  onUpdatePageSize: (s: number) => {
    query.pageSize = s
    query.pageNo = 1
    loadList()
  }
}))

onMounted(loadList)
</script>

<template>
  <div class="space-y-4">
    <!-- 顶部搜索栏 -->
    <NCard size="small">
      <div class="flex flex-wrap gap-3 items-end">
        <div>
          <div class="text-xs text-zinc-500 mb-1">服务器 ID</div>
          <NInput
            v-model:value="query.serverId"
            size="small"
            placeholder="serverId 精确"
            class="w-60"
            @keyup.enter="onSearch"
          />
        </div>
        <div>
          <div class="text-xs text-zinc-500 mb-1">Xray 版本</div>
          <NInput
            v-model:value="query.xrayVersion"
            size="small"
            placeholder="1.8 / v26"
            class="w-40"
            @keyup.enter="onSearch"
          />
        </div>
        <NButton type="primary" size="small" @click="onSearch">
          <template #icon><NIcon><Search /></NIcon></template>
          搜索
        </NButton>
        <NButton quaternary size="small" @click="resetQuery">
          <template #icon><NIcon><RefreshCcw /></NIcon></template>
          重置
        </NButton>
        <div class="flex-1"></div>
        <!-- 部署/重装: 放在刷新按钮左侧, 弹框内自带服务器选择器 -->
        <NButton type="primary" size="small" @click="openInstall">
          <template #icon><NIcon><Rocket /></NIcon></template>
          部署/重装
        </NButton>
        <NButton quaternary size="small" @click="loadList">
          <template #icon><NIcon><RefreshCcw /></NIcon></template>
          刷新
        </NButton>
      </div>
    </NCard>

    <!-- 表格 -->
    <NCard size="small" :content-style="{ padding: 0 }">
      <NDataTable
        :columns="columns"
        :data="list"
        :loading="loading"
        :pagination="pagination"
        :remote="true"
        :bordered="false"
        :row-key="(row: XrayNode) => row.serverId"
        size="small"
      />
    </NCard>

    <!-- 部署 / 重装 弹框 (server 不传 → 自带选择器) -->
    <ServerInstallDialog v-model="installOpen" :server="null" @installed="onInstalled" />

    <!-- 详情弹框 (纯查看, 状态 + 日志 + 对账) -->
    <XrayNodeOpsDialog v-model="opsOpen" :node="opsTarget" />
  </div>
</template>
