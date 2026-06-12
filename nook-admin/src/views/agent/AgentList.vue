<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import { RefreshCcw, Rocket } from 'lucide-vue-next'
import {
  NBadge,
  NButton,
  NCard,
  NDataTable,
  NIcon,
  NTabs,
  NTabPane,
  NTag,
  type DataTableColumns
} from 'naive-ui'
import {
  AGENT_ONLINE_LABELS,
  AGENT_ONLINE_TAG_TYPE
} from '@/api/agent/agent'
import {
  listAllFrontlineServers,
  SERVER_LIFECYCLE_LABELS,
  SERVER_LIFECYCLE_TAG_TYPE,
  type ServerFrontlineListItem
} from '@/api/resource/server'
import AgentDeployDialog from './AgentDeployDialog.vue'
import { formatDateTime } from '@/utils/date'

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

// ===== 部署 (装机 / 重新部署 共用一个 dialog) =====
const deployOpen = ref(false)
const provisionTarget = ref<ServerFrontlineListItem | null>(null)

const provisionRole = computed<'frontline' | 'landing'>(() => {
  if (!provisionTarget.value) return 'frontline'
  return parseRole(provisionTarget.value.agentVersion) === 'landing' ? 'landing' : 'frontline'
})

function openDeploy(row: ServerFrontlineListItem) {
  provisionTarget.value = row
  deployOpen.value = true
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
    render: (row) => h(NTag, { size: 'small', type: SERVER_LIFECYCLE_TAG_TYPE[row.lifecycleState] || 'default' },
      { default: () => SERVER_LIFECYCLE_LABELS[row.lifecycleState] || row.lifecycleState })
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
    title: '操作',
    key: 'actions',
    align: 'right',
    width: 340,
    render: (row) =>
      h('div', { class: 'flex gap-1 justify-end flex-nowrap' }, [
        // 未装 → 部署; 已装 → 重新部署 (都走 SSH 装机流, 覆盖 binary+config+重启)
        h(
          NButton,
          {
            size: 'tiny',
            quaternary: true,
            type: 'primary',
            onClick: () => openDeploy(row),
            title: row.agentVersion ? '重新部署 (覆盖 binary+config+重启)' : 'SSH 自动装 agent (首次部署)'
          },
          { icon: () => h(NIcon, null, { default: () => h(Rocket) }), default: () => (row.agentVersion ? '重新部署' : '部署') }
        )
      ])
  }
])

onMounted(loadList)
</script>

<template>
  <div class="space-y-4">
    <NCard size="small">
      <div class="flex items-center gap-3">
        <span class="text-sm font-semibold">Agent 状态总览</span>
        <span class="text-xs text-zinc-500">心跳每 1min, NIC 5min</span>
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

    <AgentDeployDialog
      v-if="provisionTarget"
      v-model="deployOpen"
      :source-id="provisionTarget.id"
      :role="provisionRole"
      :host-label="provisionTarget.name"
      @deployed="loadListAfterDispatch"
    />
  </div>
</template>
