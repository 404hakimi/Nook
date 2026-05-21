<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import { ArrowUp, FileCog, History, RefreshCcw, Trash2 } from 'lucide-vue-next'
import {
  NBadge,
  NButton,
  NCard,
  NDataTable,
  NIcon,
  NInput,
  NModal,
  NSpace,
  NTabs,
  NTabPane,
  NTag,
  useMessage,
  type DataTableColumns
} from 'naive-ui'
import { useConfirm } from '@/composables/useConfirm'
import {
  AGENT_ONLINE_LABELS,
  AGENT_ONLINE_TAG_TYPE,
  CONFIG_SYNC_LABELS,
  CONFIG_SYNC_TAG_TYPE,
  listAgents,
  truncateLog,
  type AgentListItem
} from '@/api/agent/agent'
import ConfigEditDialog from './ConfigEditDialog.vue'
import AgentProvisionDialog from './AgentProvisionDialog.vue'
import AgentTaskHistoryDialog from './AgentTaskHistoryDialog.vue'
import { formatDateTime } from '@/utils/date'

const message = useMessage()
const { confirm } = useConfirm()

const list = ref<AgentListItem[]>([])
const loading = ref(false)

async function loadList() {
  loading.value = true
  try {
    list.value = await listAgents()
  } catch {
    /* request 拦截器已 toast */
  } finally {
    loading.value = false
  }
}

/** Dispatched 后延 3s 刷一次, 给 backend 写入 + agent 心跳一个余量. */
function loadListAfterDispatch() {
  setTimeout(loadList, 3000)
}

// ===== Tab 切换 (按 role 分组) =====
type TabKey = 'frontline' | 'landing' | 'unprovisioned'
const activeTab = ref<TabKey>('frontline')

/** Parse agent role from agentVersion ("frontline-X.Y.Z" / "landing-X.Y.Z" / 空 / 老版本无前缀). */
function parseRole(av?: string): 'frontline' | 'landing' | 'unprovisioned' | 'legacy' {
  if (!av) return 'unprovisioned'
  if (av.startsWith('frontline-')) return 'frontline'
  if (av.startsWith('landing-')) return 'landing'
  return 'legacy' // 老 agent (无前缀), 当 frontline 看
}

const groups = computed(() => {
  const groups = { frontline: [] as AgentListItem[], landing: [] as AgentListItem[], unprovisioned: [] as AgentListItem[] }
  for (const a of list.value) {
    const r = parseRole(a.agentVersion)
    if (r === 'unprovisioned') groups.unprovisioned.push(a)
    else if (r === 'landing') groups.landing.push(a)
    else groups.frontline.push(a) // frontline + legacy
  }
  return groups
})

const currentList = computed(() => groups.value[activeTab.value])

// ===== 部署 / 升级 (统一 dialog) =====
const provisionOpen = ref(false)
const provisionInitialServerId = ref<string | null>(null)
const provisionInitialRole = ref<'frontline' | 'landing'>('frontline')

function openProvision(row?: AgentListItem) {
  provisionInitialServerId.value = row?.serverId ?? null
  // 行内点 → 用该 agent role; 顶部点 → 用当前 tab role (未装 tab 默认 frontline)
  if (row) {
    const r = parseRole(row.agentVersion)
    provisionInitialRole.value = r === 'landing' ? 'landing' : 'frontline'
  } else {
    provisionInitialRole.value = activeTab.value === 'landing' ? 'landing' : 'frontline'
  }
  provisionOpen.value = true
}

// ===== 清日志弹窗 =====
const truncateOpen = ref(false)
const truncateTarget = ref<AgentListItem | null>(null)
const truncatePathsText = ref('')
const truncateSubmitting = ref(false)

function openTruncate(row: AgentListItem) {
  truncateTarget.value = row
  truncatePathsText.value = '/home/socks5/logs/sockd.log\n/home/xray/logs/access.log\n/var/log/journal/'
  truncateOpen.value = true
}

async function confirmTruncate() {
  const paths = truncatePathsText.value.split(/\r?\n/).map((s) => s.trim()).filter(Boolean)
  if (paths.length === 0) {
    message.error('至少给一个路径')
    return
  }
  const ok = await confirm({
    title: '一键清日志',
    message: `将 truncate ${paths.length} 个日志文件 (保留 inode); 不可恢复. 继续?`,
    type: 'danger',
    confirmText: '清空'
  })
  if (!ok) return
  truncateSubmitting.value = true
  try {
    const taskId = await truncateLog(truncateTarget.value!.serverId, { paths })
    message.success(`清日志任务已派发 taskId=${taskId.slice(0, 8)}…`)
    truncateOpen.value = false
  } catch {
    /* */
  } finally {
    truncateSubmitting.value = false
  }
}

const columns = computed<DataTableColumns<AgentListItem>>(() => [
  {
    title: '服务器',
    key: 'serverName',
    render: (row) =>
      h('div', { class: 'flex flex-col leading-tight' }, [
        h('span', { class: 'font-medium' }, row.serverName),
        h('span', { class: 'text-xs text-zinc-500 font-mono' }, row.host)
      ])
  },
  {
    title: '生命周期',
    key: 'lifecycleState',
    width: 100,
    render: (row) => row.lifecycleState
  },
  {
    title: '角色 / 版本',
    key: 'agentVersion',
    width: 180,
    render: (row) => {
      if (!row.agentVersion) return h('span', { class: 'text-zinc-400' }, '-')
      // agentVersion = "frontline-0.7.0" / "landing-0.7.0" / 老版本可能没前缀
      const m = /^(frontline|landing)-(.+)$/.exec(row.agentVersion)
      if (!m) {
        return h('span', { class: 'font-mono text-xs' }, row.agentVersion)
      }
      const role = m[1]
      const ver = m[2]
      const tagType = role === 'frontline' ? 'info' : 'success'
      return h('div', { class: 'flex items-center gap-1.5' }, [
        h(NTag, { size: 'tiny', type: tagType }, { default: () => role }),
        h('span', { class: 'font-mono text-xs' }, 'v' + ver)
      ])
    }
  },
  {
    title: '在线状态',
    key: 'onlineState',
    width: 180,
    render: (row) =>
      h(
        NTag,
        { size: 'small', type: AGENT_ONLINE_TAG_TYPE[row.onlineState] || 'default' },
        { default: () => AGENT_ONLINE_LABELS[row.onlineState] || row.onlineState }
      )
  },
  {
    title: '心跳延迟',
    key: 'elapsedSec',
    width: 110,
    render: (row) => {
      if (row.elapsedSec == null) return h('span', { class: 'text-zinc-400' }, '-')
      const s = row.elapsedSec
      let color: string
      if (s < 60) color = '#16a34a' // green
      else if (s < 180) color = '#ca8a04' // yellow
      else if (s < 300) color = '#ea580c' // orange
      else color = '#dc2626' // red
      return h('div', { class: 'flex items-center gap-1.5' }, [
        h('span', { style: `display:inline-block;width:8px;height:8px;border-radius:50%;background:${color}` }),
        h('span', { class: 'font-mono text-xs', style: `color:${color}` }, `${s}s`)
      ])
    }
  },
  {
    title: '上次心跳',
    key: 'lastHeartbeatAt',
    width: 170,
    render: (row) => formatDateTime(row.lastHeartbeatAt)
  },
  {
    title: '配置同步',
    key: 'configSyncState',
    width: 110,
    render: (row) => {
      const s = row.configSyncState || 'NEVER_CONFIGURED'
      return h(NTag, { size: 'small', type: CONFIG_SYNC_TAG_TYPE[s] || 'default' },
        { default: () => CONFIG_SYNC_LABELS[s] || s })
    }
  },
  {
    title: '操作',
    key: 'actions',
    align: 'right',
    width: 340,
    render: (row) =>
      h('div', { class: 'flex gap-1 justify-end flex-nowrap' }, [
        h(
          NButton,
          {
            size: 'tiny',
            quaternary: true,
            type: 'info',
            onClick: () => openConfig(row),
            title: '编辑 agent 运行时配置 (yaml); 保存后 30s 内自动应用'
          },
          { icon: () => h(NIcon, null, { default: () => h(FileCog) }), default: () => '改配置' }
        ),
        h(
          NButton,
          {
            size: 'tiny',
            quaternary: true,
            type: 'primary',
            onClick: () => openProvision(row),
            title: '部署 / 升级 Agent (预选这台 server)'
          },
          { icon: () => h(NIcon, null, { default: () => h(ArrowUp) }), default: () => '部署/升级' }
        ),
        h(
          NButton,
          {
            size: 'tiny',
            quaternary: true,
            type: 'warning',
            onClick: () => openTruncate(row),
            title: '清空指定日志文件 (truncate -s 0, 保留 inode 不重启服务)'
          },
          { icon: () => h(NIcon, null, { default: () => h(Trash2) }), default: () => '清日志' }
        ),
        h(
          NButton,
          {
            size: 'tiny',
            quaternary: true,
            onClick: () => openHistory(row),
            title: '查看该 server 最近的 task 历史 (升级 / 改配置 / 清日志 / xray_*)'
          },
          { icon: () => h(NIcon, null, { default: () => h(History) }), default: () => '历史' }
        )
      ])
  }
])

// ===== 改配置弹窗 =====
const configOpen = ref(false)
const configTarget = ref<AgentListItem | null>(null)
const configTargetRole = computed<'frontline' | 'landing'>(() => {
  const v = configTarget.value?.agentVersion
  if (v && v.startsWith('landing-')) return 'landing'
  return 'frontline'
})
function openConfig(row: AgentListItem) {
  configTarget.value = row
  configOpen.value = true
}

// ===== 任务历史弹窗 =====
const historyOpen = ref(false)
const historyTarget = ref<AgentListItem | null>(null)
function openHistory(row: AgentListItem) {
  historyTarget.value = row
  historyOpen.value = true
}

onMounted(loadList)
</script>

<template>
  <div class="space-y-4">
    <NCard size="small">
      <div class="flex items-center gap-3">
        <span class="text-sm font-semibold">Agent 状态总览</span>
        <span class="text-xs text-zinc-500">心跳每 1min, 任务轮询 30s, NIC 5min</span>
        <div class="flex-1"></div>
        <NButton type="info" size="small" @click="openProvision()">
          <template #icon><NIcon><ArrowUp /></NIcon></template>
          部署 / 升级 Agent
        </NButton>
        <NButton quaternary size="small" :loading="loading" @click="loadList">
          <template #icon><NIcon><RefreshCcw /></NIcon></template>
          刷新
        </NButton>
      </div>
    </NCard>

    <NCard size="small" :content-style="{ padding: '0 12px' }">
      <NTabs v-model:value="activeTab" type="line" size="small" pane-style="padding: 0">
        <NTabPane name="frontline">
          <template #tab>
            <span>Frontline <NBadge :value="groups.frontline.length" :max="99" :show-zero="true" :type="activeTab === 'frontline' ? 'info' : 'default'" /></span>
          </template>
          <NDataTable
            :columns="columns"
            :data="groups.frontline"
            :loading="loading"
            :bordered="false"
            :row-key="(row: AgentListItem) => row.serverId"
            size="small"
          />
        </NTabPane>
        <NTabPane name="landing">
          <template #tab>
            <span>Landing <NBadge :value="groups.landing.length" :max="99" :show-zero="true" :type="activeTab === 'landing' ? 'success' : 'default'" /></span>
          </template>
          <NDataTable
            :columns="columns"
            :data="groups.landing"
            :loading="loading"
            :bordered="false"
            :row-key="(row: AgentListItem) => row.serverId"
            size="small"
          />
        </NTabPane>
        <NTabPane name="unprovisioned">
          <template #tab>
            <span>未装 <NBadge :value="groups.unprovisioned.length" :max="99" :show-zero="true" type="warning" /></span>
          </template>
          <NDataTable
            :columns="columns"
            :data="groups.unprovisioned"
            :loading="loading"
            :bordered="false"
            :row-key="(row: AgentListItem) => row.serverId"
            size="small"
          />
        </NTabPane>
      </NTabs>
    </NCard>

    <ConfigEditDialog
      v-model="configOpen"
      :server-id="configTarget?.serverId ?? null"
      :server-name="configTarget?.serverName"
      :role="configTargetRole"
      @saved="loadList"
    />
    <AgentProvisionDialog
      v-model="provisionOpen"
      :initial-server-id="provisionInitialServerId"
      :initial-role="provisionInitialRole"
      @dispatched="loadListAfterDispatch"
    />
    <AgentTaskHistoryDialog
      v-model="historyOpen"
      :server-id="historyTarget?.serverId ?? null"
      :server-name="historyTarget?.serverName"
    />

    <!-- 清日志弹窗 -->
    <NModal
      :show="truncateOpen"
      preset="card"
      title="一键清日志"
      style="max-width: 40rem"
      :bordered="false"
      :mask-closable="false"
      @update:show="(v: boolean) => (truncateOpen = v)"
    >
      <p class="text-sm mb-3">目标: <span class="font-mono">{{ truncateTarget?.serverName }}</span>; 路径白名单 <code class="text-xs">/var/log /home/socks5/logs /home/xray/logs</code></p>
      <NForm size="small" label-placement="top">
        <NFormItem label="日志文件路径 (一行一个)" required>
          <NInput
            v-model:value="truncatePathsText"
            type="textarea"
            :autosize="{ minRows: 4, maxRows: 12 }"
            placeholder="/home/socks5/logs/sockd.log"
            :input-props="{ style: 'font-family: monospace' }"
          />
        </NFormItem>
      </NForm>
      <template #footer>
        <NSpace justify="end">
          <NButton size="small" @click="truncateOpen = false">取消</NButton>
          <NButton type="warning" size="small" :loading="truncateSubmitting" @click="confirmTruncate">清空</NButton>
        </NSpace>
      </template>
    </NModal>
  </div>
</template>
