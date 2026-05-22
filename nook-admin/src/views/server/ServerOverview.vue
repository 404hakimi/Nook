<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  Activity,
  ArrowUp,
  Box,
  Clock,
  Cpu,
  Database,
  FileCog,
  Gauge,
  Globe,
  MapPin,
  Plus,
  RefreshCcw,
  RotateCcw,
  Search,
  ServerCog
} from 'lucide-vue-next'
import {
  NButton,
  NCard,
  NEmpty,
  NIcon,
  NInput,
  NPagination,
  NProgress,
  NSelect,
  NSpin,
  NTag,
  useMessage
} from 'naive-ui'
import {
  AGENT_ONLINE_LABELS,
  AGENT_ONLINE_TAG_TYPE,
  CONFIG_SYNC_LABELS,
  CONFIG_SYNC_TAG_TYPE,
  pageAgents,
  type AgentListItem,
  type AgentPageQuery
} from '@/api/agent/agent'
import { SERVER_LIFECYCLE_LABELS, SERVER_LIFECYCLE_TAG_TYPE } from '@/api/resource/server'
import { listEnabledRegions, type ResourceRegion } from '@/api/resource/region'
import { formatDateTime } from '@/utils/date'
import RegionFlag from '@/components/RegionFlag.vue'
import { h } from 'vue'

const router = useRouter()
const message = useMessage()

const list = ref<AgentListItem[]>([])
const total = ref(0)
const loading = ref(false)

// ===== 区域字典 (启动拉一次; 卡片标题 + 下拉 + 详情头部都映射用) =====
const regions = ref<ResourceRegion[]>([])
const regionMap = computed<Record<string, ResourceRegion>>(() => {
  const m: Record<string, ResourceRegion> = {}
  for (const r of regions.value) m[r.code] = r
  return m
})

// 区域下拉: 用 render label 渲染 RegionFlag (countryCode → SVG 国旗) + displayName
const regionOptions = computed(() => {
  return [
    { label: '全部地区', value: null as string | null },
    ...regions.value.map((r) => ({
      label: r.displayName,
      value: r.code,
      countryCode: r.countryCode,
      flagEmoji: r.flagEmoji
    }))
  ]
})

// Naive UI NSelect render-label hook
function renderRegionLabel(option: { label: string; countryCode?: string; flagEmoji?: string }) {
  if (!option.countryCode) return option.label
  return h('span', { style: 'display:flex; align-items:center; gap:6px;' }, [
    h(RegionFlag, { code: option.countryCode, fallback: option.flagEmoji, size: 14 }),
    option.label
  ])
}

// ===== 筛选表单 (本地 form state; 点搜索按钮才合到 applied) =====
const form = ref<{ name: string; host: string; region: string | null; lifecycleState: string | null; onlineState: string | null }>({
  name: '',
  host: '',
  region: null,
  lifecycleState: null,
  onlineState: null  // 客户端筛选 (个位数集群单页装下, 本地过滤即可)
})
const pageNo = ref(1)
const pageSize = ref(20)

async function load() {
  loading.value = true
  try {
    const params: AgentPageQuery = {
      pageNo: pageNo.value,
      pageSize: pageSize.value
    }
    if (form.value.name.trim()) params.name = form.value.name.trim()
    if (form.value.host.trim()) params.host = form.value.host.trim()
    if (form.value.region) params.region = form.value.region
    if (form.value.lifecycleState) params.lifecycleState = form.value.lifecycleState
    const res = await pageAgents(params)
    list.value = res.records || []
    total.value = res.total || 0
  } catch { /* */ } finally {
    loading.value = false
  }
}

function doSearch() {
  pageNo.value = 1
  load()
}

function doReset() {
  form.value = { name: '', host: '', region: null, lifecycleState: null, onlineState: null }
  pageNo.value = 1
  load()
}

// 翻页 / 改页 size 触发, 跟 form 解耦
function onPageChange(p: number) { pageNo.value = p; load() }
function onPageSizeChange(s: number) { pageSize.value = s; pageNo.value = 1; load() }

const lifecycleOptions = [
  { label: '全部生命周期', value: null },
  { label: '装机中', value: 'INSTALLING' },
  { label: '待上线', value: 'READY' },
  { label: '运行中', value: 'LIVE' },
  { label: '已退役', value: 'RETIRED' }
]
const onlineOptions = [
  { label: '全部状态', value: null },
  { label: '在线 (ONLINE/WARN)', value: 'UP' },
  { label: '暂时不健康', value: 'TEMP_UNHEALTHY' },
  { label: '掉线 (≥5min)', value: 'OFFLINE' },
  { label: '未上报', value: 'NEVER' }
]

// onlineState 当前页过滤; 单页 20 条对小集群 = 全集过滤
const filtered = computed(() => {
  if (!form.value.onlineState) return list.value
  return list.value.filter((s) => {
    if (form.value.onlineState === 'UP') return s.onlineState === 'ONLINE' || s.onlineState === 'WARN'
    return s.onlineState === form.value.onlineState
  })
})

// ===== 健康摘要 (当前页) =====
const stats = computed(() => {
  const t = list.value.length
  let online = 0, warn = 0, offline = 0, never = 0
  for (const s of list.value) {
    if (s.onlineState === 'ONLINE') online++
    else if (s.onlineState === 'WARN' || s.onlineState === 'TEMP_UNHEALTHY') warn++
    else if (s.onlineState === 'OFFLINE') offline++
    else never++
  }
  return { t, online, warn, offline, never }
})

const ONLINE_COLOR: Record<string, string> = {
  ONLINE: '#16a34a',
  WARN: '#ca8a04',
  TEMP_UNHEALTHY: '#ea580c',
  OFFLINE: '#dc2626',
  NEVER: '#9ca3af'
}

function openDetail(serverId: string) {
  router.push(`/servers/${serverId}`)
}
function openTab(serverId: string, tab: string, ev: Event) {
  ev.stopPropagation()
  router.push({ path: `/servers/${serverId}`, query: { tab } })
}

function openCreate() {
  message.info('新建 server 待后续接入 (详情页编辑入口已 OK)')
}

// ===== 流量进度 (bytes long, JS Number 2^53 内精确) =====
const GB = 1024 * 1024 * 1024
function trafficUsedGB(s: AgentListItem): number {
  if (s.usedTrafficBytes == null) return 0
  return Number((s.usedTrafficBytes / GB).toFixed(1))
}
function trafficPercent(s: AgentListItem): number {
  if (!s.monthlyTrafficGb) return 0
  return Math.min(100, Math.round((trafficUsedGB(s) / s.monthlyTrafficGb) * 100))
}
function trafficStatus(s: AgentListItem): 'default' | 'success' | 'warning' | 'error' {
  if (!s.monthlyTrafficGb) return 'default'
  const p = trafficPercent(s)
  if (p >= 90) return 'error'
  if (p >= 70) return 'warning'
  return 'success'
}

function parseAgentVersion(v?: string): { role: string; ver: string } | null {
  if (!v) return null
  const m = /^(frontline|landing)-(.+)$/.exec(v)
  if (!m) return { role: '?', ver: v }
  return { role: m[1], ver: m[2] }
}

onMounted(async () => {
  try { regions.value = await listEnabledRegions() } catch { /* */ }
  await load()
})
</script>

<template>
  <div class="overview-wrap space-y-4">

    <!-- ============ 头部 ============ -->
    <div class="page-header">
      <div class="flex items-center gap-3 flex-wrap mb-3">
        <div>
          <div class="text-lg font-semibold">服务器</div>
          <div class="text-xs text-zinc-500 mt-0.5">统一管理服务器 / Xray 节点 / Agent · 心跳 1min · 任务轮询 60s</div>
        </div>
        <div class="flex-1"></div>
        <NButton type="primary" size="small" @click="openCreate">
          <template #icon><NIcon><Plus :size="14" /></NIcon></template>
          新建 server
        </NButton>
        <NButton quaternary size="small" :loading="loading" @click="load">
          <template #icon><NIcon><RefreshCcw :size="14" /></NIcon></template>
          刷新
        </NButton>
      </div>

      <!-- 健康摘要条 -->
      <div class="stats-strip">
        <div class="stat-item">
          <span class="stat-num">{{ total }}</span>
          <span class="stat-label">总数</span>
        </div>
        <div class="stat-divider"></div>
        <div class="stat-item">
          <span class="stat-dot" style="background:#16a34a"></span>
          <span class="stat-num text-green-600">{{ stats.online }}</span>
          <span class="stat-label">在线</span>
        </div>
        <div class="stat-item">
          <span class="stat-dot" style="background:#ca8a04"></span>
          <span class="stat-num text-yellow-600">{{ stats.warn }}</span>
          <span class="stat-label">不健康</span>
        </div>
        <div class="stat-item">
          <span class="stat-dot" style="background:#dc2626"></span>
          <span class="stat-num text-red-600">{{ stats.offline }}</span>
          <span class="stat-label">掉线</span>
        </div>
        <div class="stat-item">
          <span class="stat-dot" style="background:#9ca3af"></span>
          <span class="stat-num text-zinc-500">{{ stats.never }}</span>
          <span class="stat-label">未上报</span>
        </div>
        <span class="text-xs text-zinc-400 ml-1">(当前页 {{ stats.t }} 条)</span>
      </div>

      <!-- 筛选表单: 标签化 + 手动搜索 / 重置 -->
      <div class="filter-form">
        <div class="filter-item">
          <label class="filter-label">名称</label>
          <NInput
            v-model:value="form.name"
            placeholder="server name / domain"
            clearable
            size="small"
            class="w-44"
            @keyup.enter="doSearch"
          />
        </div>
        <div class="filter-item">
          <label class="filter-label">IP / Host</label>
          <NInput
            v-model:value="form.host"
            placeholder="如 192.168 或完整 IP"
            clearable
            size="small"
            class="w-44"
            @keyup.enter="doSearch"
          />
        </div>
        <div class="filter-item">
          <label class="filter-label">地区</label>
          <NSelect
            v-model:value="form.region"
            :options="regionOptions"
            :render-label="renderRegionLabel as any"
            size="small"
            class="w-44"
            clearable
            filterable
          />
        </div>
        <div class="filter-item">
          <label class="filter-label">生命周期</label>
          <NSelect
            v-model:value="form.lifecycleState"
            :options="lifecycleOptions"
            size="small"
            class="w-32"
            clearable
          />
        </div>
        <div class="filter-item">
          <label class="filter-label">在线状态</label>
          <NSelect
            v-model:value="form.onlineState"
            :options="onlineOptions"
            size="small"
            class="w-40"
            clearable
          />
        </div>
        <div class="filter-actions">
          <NButton type="primary" size="small" @click="doSearch">
            <template #icon><NIcon><Search :size="14" /></NIcon></template>
            搜索
          </NButton>
          <NButton size="small" @click="doReset">
            <template #icon><NIcon><RotateCcw :size="14" /></NIcon></template>
            重置
          </NButton>
        </div>
      </div>
    </div>

    <!-- ============ 卡片网格 ============ -->
    <NSpin :show="loading">
      <NEmpty v-if="!loading && filtered.length === 0" description="无匹配 server" />
      <div v-else class="server-grid">
        <div
          v-for="s in filtered"
          :key="s.serverId"
          class="server-card"
          @click="openDetail(s.serverId)"
        >
          <div class="card-stripe" :style="`background:${ONLINE_COLOR[s.onlineState] || '#9ca3af'}`"></div>

          <div class="card-body">
            <!-- header: flag + 名称 + lifecycle tag -->
            <div class="flex items-start gap-2 mb-2">
              <div class="flex-1 min-w-0">
                <div class="card-title truncate" :title="s.serverName">
                  <RegionFlag
                    v-if="s.region && regionMap[s.region]"
                    :code="regionMap[s.region].countryCode"
                    :fallback="regionMap[s.region].flagEmoji"
                    squared
                    :size="16"
                    :title="regionMap[s.region].displayName"
                  />
                  <NIcon v-else class="title-icon"><ServerCog :size="14" /></NIcon>
                  {{ s.serverName }}
                </div>
                <div class="card-subtitle truncate">
                  <NIcon class="text-zinc-400"><Globe :size="11" /></NIcon>
                  <span>{{ s.host }}</span>
                  <template v-if="s.region && regionMap[s.region]">
                    <span class="region-sep">·</span>
                    <NIcon class="text-zinc-400"><MapPin :size="10" /></NIcon>
                    <span>{{ regionMap[s.region].displayName }}</span>
                  </template>
                </div>
              </div>
              <NTag size="tiny" :type="SERVER_LIFECYCLE_TAG_TYPE[s.lifecycleState] || 'default'">
                {{ SERVER_LIFECYCLE_LABELS[s.lifecycleState] || s.lifecycleState }}
              </NTag>
            </div>

            <!-- 状态摘要 -->
            <div class="status-grid">
              <div class="status-cell">
                <div class="status-key">
                  <NIcon class="key-icon"><Box :size="12" /></NIcon>
                  <span>Agent</span>
                </div>
                <div class="status-val">
                  <span v-if="parseAgentVersion(s.agentVersion)" class="text-xs num">
                    v{{ parseAgentVersion(s.agentVersion)!.ver }}
                  </span>
                  <span v-else class="text-zinc-400 text-xs">未装</span>
                </div>
              </div>
              <div class="status-cell">
                <div class="status-key">
                  <NIcon class="key-icon"><Activity :size="12" /></NIcon>
                  <span>状态</span>
                </div>
                <div class="status-val">
                  <NTag size="tiny" :type="AGENT_ONLINE_TAG_TYPE[s.onlineState] || 'default'">
                    {{ AGENT_ONLINE_LABELS[s.onlineState] }}
                  </NTag>
                </div>
              </div>
              <div class="status-cell">
                <div class="status-key">
                  <NIcon class="key-icon"><Database :size="12" /></NIcon>
                  <span>配置同步</span>
                </div>
                <div class="status-val">
                  <NTag size="tiny" :type="CONFIG_SYNC_TAG_TYPE[s.configSyncState || 'NEVER_CONFIGURED'] || 'default'">
                    {{ CONFIG_SYNC_LABELS[s.configSyncState || 'NEVER_CONFIGURED'] }}
                  </NTag>
                </div>
              </div>
              <div class="status-cell">
                <div class="status-key">
                  <NIcon class="key-icon"><Clock :size="12" /></NIcon>
                  <span>心跳延迟</span>
                </div>
                <div class="status-val">
                  <span v-if="s.elapsedSec != null" class="num-sm">{{ s.elapsedSec }}<span class="unit-sm">s</span></span>
                  <span v-else class="text-zinc-400 text-xs">—</span>
                </div>
              </div>
            </div>

            <!-- 流量进度 -->
            <div class="traffic-row">
              <div class="traffic-head">
                <NIcon class="text-zinc-400"><Gauge :size="11" /></NIcon>
                <span class="status-key">本月流量</span>
                <span class="flex-1"></span>
                <template v-if="s.monthlyTrafficGb">
                  <span class="num-sm">{{ trafficUsedGB(s) }}</span>
                  <span class="unit-sm">/ {{ s.monthlyTrafficGb }} GB</span>
                </template>
                <template v-else-if="s.usedTrafficBytes != null">
                  <span class="num-sm">{{ trafficUsedGB(s) }}</span>
                  <span class="unit-sm">GB · 不限</span>
                </template>
                <span v-else class="text-zinc-400 text-xs">—</span>
              </div>
              <NProgress
                v-if="s.monthlyTrafficGb"
                :percentage="trafficPercent(s)"
                :height="4"
                :status="trafficStatus(s)"
                :show-indicator="false"
                class="mt-1"
              />
            </div>

            <div class="last-seen">
              <NIcon class="text-zinc-400"><Clock :size="11" /></NIcon>
              <span>上次心跳: {{ formatDateTime(s.lastHeartbeatAt) || '—' }}</span>
            </div>

            <div class="card-actions" @click.stop>
              <NButton size="tiny" quaternary type="info" @click="openTab(s.serverId, 'xray', $event)">
                <template #icon><NIcon><ServerCog :size="12" /></NIcon></template>
                Xray
              </NButton>
              <NButton size="tiny" quaternary type="primary" @click="openTab(s.serverId, 'agent', $event)">
                <template #icon><NIcon><Cpu :size="12" /></NIcon></template>
                Agent
              </NButton>
              <NButton size="tiny" quaternary @click="openTab(s.serverId, 'agent', $event)">
                <template #icon><NIcon><FileCog :size="12" /></NIcon></template>
                改配置
              </NButton>
              <NButton size="tiny" quaternary type="success" @click="openTab(s.serverId, 'agent', $event)">
                <template #icon><NIcon><ArrowUp :size="12" /></NIcon></template>
                升级
              </NButton>
            </div>
          </div>
        </div>
      </div>

      <div v-if="total > 0" class="pagination-bar">
        <NPagination
          :page="pageNo"
          :page-size="pageSize"
          :item-count="total"
          :page-sizes="[10, 20, 50]"
          show-size-picker
          size="small"
          @update:page="onPageChange"
          @update:page-size="onPageSizeChange"
        />
      </div>
    </NSpin>
  </div>
</template>

<style scoped>
.overview-wrap {
  max-width: 1280px;
  margin: 0 auto;
}
.page-header {
  background: linear-gradient(180deg, rgba(99, 102, 241, 0.03), transparent);
  padding: 14px 16px;
  border-radius: 6px;
  border: 1px solid rgba(127, 127, 127, 0.1);
}

/* 健康摘要条 */
.stats-strip {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 10px 14px;
  background: rgba(127, 127, 127, 0.05);
  border-radius: 6px;
  border: 1px solid rgba(127, 127, 127, 0.08);
}
.stat-item {
  display: flex;
  align-items: baseline;
  gap: 5px;
}
.stat-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.stat-num {
  font-size: 18px;
  font-weight: 600;
  font-family: 'JetBrains Mono', monospace;
}
.stat-label {
  font-size: 12px;
  color: #71717a;
}
.stat-divider {
  width: 1px;
  height: 18px;
  background: rgba(127, 127, 127, 0.2);
}

/* 标签化筛选 */
.filter-form {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 10px 14px;
  margin-top: 14px;
  padding: 10px 12px;
  background: rgba(127, 127, 127, 0.03);
  border: 1px dashed rgba(127, 127, 127, 0.15);
  border-radius: 6px;
}
.filter-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.filter-label {
  font-size: 11px;
  color: #71717a;
  font-weight: 500;
}
.filter-actions {
  display: flex;
  gap: 6px;
  margin-left: auto;
}

/* 卡片网格 */
.server-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 12px;
}
.server-card {
  display: flex;
  background: var(--card-color, #fff);
  border: 1px solid rgba(127, 127, 127, 0.15);
  border-radius: 8px;
  cursor: pointer;
  transition: transform 0.15s ease, box-shadow 0.15s ease, border-color 0.15s ease;
  overflow: hidden;
}
.server-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 14px -4px rgba(0, 0, 0, 0.1);
  border-color: rgba(99, 102, 241, 0.4);
}
html[data-theme='dark'] .server-card {
  background: #1f1f23;
}
.card-stripe {
  width: 4px;
  flex-shrink: 0;
}
.card-body {
  flex: 1;
  padding: 12px 14px;
  min-width: 0;
}
.card-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: #18181b;
}
.title-icon { color: #6366f1; }
html[data-theme='dark'] .card-title { color: #e4e4e7; }
.card-subtitle {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: #71717a;
  font-family: 'JetBrains Mono', monospace;
  margin-top: 2px;
  flex-wrap: wrap;
}
.region-sep {
  color: #d4d4d8;
  margin: 0 1px;
}

/* 状态网格 */
.status-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px 12px;
  margin-top: 8px;
}
.status-cell {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.status-key {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: #71717a;
}
.key-icon {
  color: #a1a1aa;
}
.status-val {
  display: flex;
  align-items: center;
  min-height: 18px;
}
/* .num-sm / .unit-sm 走 main.scss 全局 tokens (卡片紧凑变体) */

.traffic-row {
  margin-top: 10px;
}
.traffic-head {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
}

.last-seen {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: #a1a1aa;
  margin-top: 8px;
  padding-top: 6px;
  border-top: 1px dotted rgba(127, 127, 127, 0.12);
}

.card-actions {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px dashed rgba(127, 127, 127, 0.15);
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
