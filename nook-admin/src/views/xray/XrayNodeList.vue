<script setup lang="ts">
import { computed, h, onMounted, reactive, ref, type Component } from 'vue'
import { Eye, FileText, GitCompareArrows, LayoutGrid, Power, RefreshCcw, Rocket, Search } from 'lucide-vue-next'
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
import { xrayRestart } from '@/api/xray/server'
import { formatDateTime } from '@/utils/date'
import ServerInstallDialog from '@/views/resource/ServerInstallDialog.vue'
import XrayNodeStatusDialog from './XrayNodeStatusDialog.vue'
import XrayNodeSlotsDialog from './XrayNodeSlotsDialog.vue'
import XrayNodeLogDialog from './XrayNodeLogDialog.vue'
import XrayNodeDiffDialog from './XrayNodeDiffDialog.vue'

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
type RowOp = 'restart'
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

// ===== 行内: 详情 / Slot / 日志 / 查看差异 四个独立弹窗 =====
const statusOpen = ref(false)
const slotsOpen = ref(false)
const logOpen = ref(false)
const diffOpen = ref(false)
const dialogTarget = ref<XrayNode | null>(null)

function openStatus(n: XrayNode) {
  dialogTarget.value = n
  statusOpen.value = true
}
function openSlots(n: XrayNode) {
  dialogTarget.value = n
  slotsOpen.value = true
}
function openLog(n: XrayNode) {
  dialogTarget.value = n
  logOpen.value = true
}
function openDiff(n: XrayNode) {
  dialogTarget.value = n
  diffOpen.value = true
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
    width: 400,
    render: (row) => {
      const busy = rowBusy.value[row.serverId]
      return h('div', { class: 'flex gap-1 justify-end flex-nowrap' }, [
        renderActionButton({
          tooltip: '查看 Xray 运行状态 / 版本 / 监听端口; 弹窗内可切换开机自启',
          icon: Eye,
          label: '详情',
          disabled: !!busy,
          onClick: () => openStatus(row)
        }),
        renderActionButton({
          tooltip: '对比远端 inbound 与 DB 差异 (只读); 有缺失时可一键推送修复',
          icon: GitCompareArrows,
          label: '查看差异',
          disabled: !!busy,
          onClick: () => openDiff(row)
        }),
        renderActionButton({
          tooltip: '查看该 server 上 slot 池 50 个槽位的占用情况 + 绑定 client',
          icon: LayoutGrid,
          label: 'Slot 占用',
          disabled: !!busy,
          onClick: () => openSlots(row)
        }),
        renderActionButton({
          tooltip: '查看 xray access / error 日志, 支持 50-1000 行 + 按级别过滤',
          icon: FileText,
          label: '日志',
          disabled: !!busy,
          onClick: () => openLog(row)
        }),
        renderActionButton({
          tooltip: '在远端 systemctl restart xray; 所有客户端会断开重连约 1-2 秒',
          icon: Power,
          label: '重启',
          buttonType: 'warning',
          loading: busy === 'restart',
          disabled: !!busy && busy !== 'restart',
          onClick: () => onRestart(row)
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

    <!-- 详情: 状态 + 自启开关 -->
    <XrayNodeStatusDialog v-model="statusOpen" :node="dialogTarget" />
    <!-- 查看差异 + 推送修复 -->
    <XrayNodeDiffDialog v-model="diffOpen" :node="dialogTarget" />
    <!-- Slot 占用 -->
    <XrayNodeSlotsDialog v-model="slotsOpen" :node="dialogTarget" />
    <!-- 日志 -->
    <XrayNodeLogDialog v-model="logOpen" :node="dialogTarget" />
  </div>
</template>
