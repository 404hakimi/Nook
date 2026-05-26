<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import { ArrowUp, FileCog, History, RefreshCcw, Rocket } from 'lucide-vue-next'
import {
  NBadge,
  NButton,
  NCard,
  NDataTable,
  NIcon,
  NTabs,
  NTabPane,
  NTag,
  useMessage,
  type DataTableColumns
} from 'naive-ui'
import {
  AGENT_ONLINE_LABELS,
  AGENT_ONLINE_TAG_TYPE,
  CONFIG_SYNC_LABELS,
  CONFIG_SYNC_TAG_TYPE
} from '@/api/agent/agent'
import {
  listAllFrontlineServers,
  type ServerFrontlineListItem
} from '@/api/resource/server'
import ConfigEditDialog from './ConfigEditDialog.vue'
import AgentDeployDialog from './AgentDeployDialog.vue'
import AgentUpgradeDialog from './AgentUpgradeDialog.vue'
import AgentTaskHistoryDialog from './AgentTaskHistoryDialog.vue'
import { formatDateTime } from '@/utils/date'

const message = useMessage()

const list = ref<ServerFrontlineListItem[]>([])
const loading = ref(false)

async function loadList() {
  loading.value = true
  try {
    list.value = await listAllFrontlineServers()
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
  const groups = { frontline: [] as ServerFrontlineListItem[], landing: [] as ServerFrontlineListItem[], unprovisioned: [] as ServerFrontlineListItem[] }
  for (const a of list.value) {
    const r = parseRole(a.agentVersion)
    if (r === 'unprovisioned') groups.unprovisioned.push(a)
    else if (r === 'landing') groups.landing.push(a)
    else groups.frontline.push(a) // frontline + legacy
  }
  return groups
})

// ===== 部署 / 升级 (拆两个独立 dialog) =====
const deployOpen = ref(false)
const upgradeOpen = ref(false)
const provisionTarget = ref<ServerFrontlineListItem | null>(null)

const provisionRole = computed<'frontline' | 'landing'>(() => {
  if (!provisionTarget.value) return 'frontline'
  return parseRole(provisionTarget.value.agentVersion) === 'landing' ? 'landing' : 'frontline'
})

function openDeploy(row: ServerFrontlineListItem) {
  provisionTarget.value = row
  deployOpen.value = true
}

function openUpgrade(row: ServerFrontlineListItem) {
  provisionTarget.value = row
  upgradeOpen.value = true
}

const columns = computed<DataTableColumns<ServerFrontlineListItem>>(() => [
  {
    title: '服务器',
    key: 'serverName',
    render: (row) =>
      h('div', { class: 'flex flex-col leading-tight' }, [
        h('span', { class: 'font-medium' }, row.name),
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
        // 已装 → 升级; 未装 → 部署. 两种状态各自一个按钮, 文案/icon 明确, 不再 "部署/升级" 二合一
        row.agentVersion
          ? h(
              NButton,
              {
                size: 'tiny',
                quaternary: true,
                type: 'success',
                disabled: row.onlineState === 'OFFLINE' || row.onlineState === 'NEVER',
                onClick: () => openUpgrade(row),
                title: '一键升级 binary (走 task 链路, 仅替换 binary)'
              },
              { icon: () => h(NIcon, null, { default: () => h(ArrowUp) }), default: () => '升级' }
            )
          : h(
              NButton,
              {
                size: 'tiny',
                quaternary: true,
                type: 'primary',
                onClick: () => openDeploy(row),
                title: 'SSH 自动装 agent (首次部署)'
              },
              { icon: () => h(NIcon, null, { default: () => h(Rocket) }), default: () => '部署' }
            ),
        h(
          NButton,
          {
            size: 'tiny',
            quaternary: true,
            onClick: () => openHistory(row),
            title: '查看该 server 最近的 task 历史 (升级 / 改配置 / xray_*)'
          },
          { icon: () => h(NIcon, null, { default: () => h(History) }), default: () => '历史' }
        )
      ])
  }
])

// ===== 改配置弹窗 =====
const configOpen = ref(false)
const configTarget = ref<ServerFrontlineListItem | null>(null)
const configTargetRole = computed<'frontline' | 'landing'>(() => {
  const v = configTarget.value?.agentVersion
  if (v && v.startsWith('landing-')) return 'landing'
  return 'frontline'
})
function openConfig(row: ServerFrontlineListItem) {
  configTarget.value = row
  configOpen.value = true
}

// ===== 任务历史弹窗 =====
const historyOpen = ref(false)
const historyTarget = ref<ServerFrontlineListItem | null>(null)
function openHistory(row: ServerFrontlineListItem) {
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
        <!-- 顶部统一入口去掉; admin 走 server 详情页 → Agent tab 入口, 或者在本表行内点 -->
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
            :row-key="(row: ServerFrontlineListItem) => row.id"
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
            :row-key="(row: ServerFrontlineListItem) => row.id"
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
            :row-key="(row: ServerFrontlineListItem) => row.id"
            size="small"
          />
        </NTabPane>
      </NTabs>
    </NCard>

    <ConfigEditDialog
      v-model="configOpen"
      :server-id="configTarget?.id ?? null"
      :server-name="configTarget?.name"
      :role="configTargetRole"
      @saved="loadList"
    />
    <AgentDeployDialog
      v-if="provisionTarget"
      v-model="deployOpen"
      :source-id="provisionTarget.id"
      :role="provisionRole"
      :host-label="provisionTarget.name"
      @deployed="loadListAfterDispatch"
    />
    <AgentUpgradeDialog
      v-if="provisionTarget"
      v-model="upgradeOpen"
      :server-id="provisionTarget.id"
      :host-label="provisionTarget.name"
      :agent-info="provisionTarget"
      @upgraded="loadListAfterDispatch"
    />
    <AgentTaskHistoryDialog
      v-model="historyOpen"
      :server-id="historyTarget?.id ?? null"
      :server-name="historyTarget?.name"
    />
  </div>
</template>
