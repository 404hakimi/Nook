<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  Clock,
  Cpu,
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
  NTag
} from 'naive-ui'
import {
  AGENT_ONLINE_LABELS,
  AGENT_ONLINE_TAG_TYPE
} from '@/api/agent/agent'
import {
  pageServers,
  SERVER_LIFECYCLE_LABELS,
  SERVER_LIFECYCLE_TAG_TYPE,
  type ServerFrontlineListItem,
  type ResourceServerQuery
} from '@/api/resource/server'
import { useRegionStore } from '@/stores/region'
import { storeToRefs } from 'pinia'
import ServerCreateDialog from './dialogs/ServerCreateDialog.vue'
import RegionFlag from '@/components/RegionFlag.vue'
import RegionTreeFilter from '@/components/RegionTreeFilter.vue'

const router = useRouter()

const list = ref<ServerFrontlineListItem[]>([])
const total = ref(0)
const loading = ref(false)

// ===== 区域字典 (启动拉一次; 卡片标题 + 下拉 + 详情头部都映射用) =====
const regionStore = useRegionStore()
const { map: regionMap } = storeToRefs(regionStore)

// 左侧区域树筛选 (即时生效, 不走搜索按钮)
const regionFilter = ref<InstanceType<typeof RegionTreeFilter> | null>(null)
const regionCodes = ref<string[]>([])
function onRegionChange(codes: string[]) {
  regionCodes.value = codes
  pageNo.value = 1
  load()
}

// ===== 筛选表单 (本地 form state; 点搜索按钮才合到 applied) =====
const form = ref<{ name: string; host: string; lifecycleState: string | null; onlineState: string | null }>({
  name: '',
  host: '',
  lifecycleState: null,
  onlineState: null  // 客户端筛选 (个位数集群单页装下, 本地过滤即可)
})
const pageNo = ref(1)
const pageSize = ref(9)

async function load() {
  loading.value = true
  try {
    const params: ResourceServerQuery = {
      pageNo: pageNo.value,
      pageSize: pageSize.value
    }
    if (form.value.name.trim()) params.name = form.value.name.trim()
    if (form.value.host.trim()) params.host = form.value.host.trim()
    if (regionCodes.value.length) params.regionCodes = regionCodes.value
    if (form.value.lifecycleState) params.lifecycleState = form.value.lifecycleState
    const res = await pageServers(params)
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
  form.value = { name: '', host: '', lifecycleState: null, onlineState: null }
  regionCodes.value = []
  regionFilter.value?.reset()
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

const createOpen = ref(false)
function openCreate() {
  createOpen.value = true
}
function onCreated(serverId: string) {
  pageNo.value = 1
  load()
  router.push(`/servers/${serverId}`)
}

// ===== 流量进度 (bytes long, JS Number 2^53 内精确) =====
const GB = 1024 * 1024 * 1024
function trafficUsedGB(s: ServerFrontlineListItem): number {
  if (s.usedTrafficBytes == null) return 0
  return Number((s.usedTrafficBytes / GB).toFixed(1))
}
function trafficPercent(s: ServerFrontlineListItem): number {
  if (!s.monthlyTrafficGb) return 0
  return Math.min(100, Math.round((trafficUsedGB(s) / s.monthlyTrafficGb) * 100))
}
function trafficStatus(s: ServerFrontlineListItem): 'default' | 'success' | 'warning' | 'error' {
  if (!s.monthlyTrafficGb) return 'default'
  const p = trafficPercent(s)
  if (p >= 90) return 'error'
  if (p >= 70) return 'warning'
  return 'success'
}

// 心跳延迟拆成最高 2 个单位 (s / m / h / d), 避免 "86400s" 这种不可读数字
function formatElapsed(sec: number): { n: number; u: string }[] {
  if (sec < 60) return [{ n: sec, u: 's' }]
  if (sec < 3600) {
    const m = Math.floor(sec / 60)
    const s = sec % 60
    return s > 0 ? [{ n: m, u: 'm' }, { n: s, u: 's' }] : [{ n: m, u: 'min' }]
  }
  if (sec < 86400) {
    const h = Math.floor(sec / 3600)
    const m = Math.floor((sec % 3600) / 60)
    return m > 0 ? [{ n: h, u: 'h' }, { n: m, u: 'm' }] : [{ n: h, u: 'h' }]
  }
  const d = Math.floor(sec / 86400)
  const h = Math.floor((sec % 86400) / 3600)
  return h > 0 ? [{ n: d, u: 'd' }, { n: h, u: 'h' }] : [{ n: d, u: 'd' }]
}
// 超过 1h 视为 stale (上方 pill 只表达到 ≥5min, 这里再分一级)
function elapsedStale(sec: number): boolean {
  return sec >= 3600
}

function parseAgentVersion(v?: string): { role: string; ver: string } | null {
  if (!v) return null
  const m = /^(frontline|landing)-(.+)$/.exec(v)
  if (!m) return { role: '?', ver: v }
  return { role: m[1], ver: m[2] }
}

onMounted(async () => {
  // 区域字典 + server 分页独立, 并行拉 (字典走 store 全局去重)
  await Promise.all([
    regionStore.ensureLoaded(),
    load()
  ])
})
</script>

<template>
  <div class="ov-layout">
    <RegionTreeFilter ref="regionFilter" @change="onRegionChange" />
    <div class="overview-wrap space-y-4">

    <!-- ============ 头部 ============ -->
    <div class="page-header">
      <div class="flex items-center gap-3 flex-wrap mb-3">
        <div>
          <div class="text-lg font-semibold">线路机</div>
          <div class="text-xs text-zinc-500 mt-0.5">统一管理线路机 / Xray 节点 / Agent · 心跳 1min · 任务轮询 60s</div>
        </div>
        <div class="flex-1"></div>
        <NButton type="primary" size="small" @click="openCreate">
          <template #icon><NIcon><Plus :size="14" /></NIcon></template>
          新建线路机
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
          :key="s.id"
          class="server-card"
          @click="openDetail(s.id)"
        >
          <div class="card-stripe" :style="`background:${ONLINE_COLOR[s.onlineState] || '#9ca3af'}`"></div>

          <div class="card-body">
            <!-- header: flag + 名称 + lifecycle tag -->
            <div class="flex items-start gap-2 mb-2">
              <div class="flex-1 min-w-0">
                <div class="card-title truncate" :title="s.name">
                  <RegionFlag
                    v-if="s.region && regionMap[s.region]"
                    :code="regionMap[s.region].countryCode"
                    :fallback="regionMap[s.region].flagEmoji"
                    squared
                    :size="16"
                    :title="regionMap[s.region].displayName"
                  />
                  <NIcon v-else class="title-icon"><ServerCog :size="14" /></NIcon>
                  {{ s.name }}
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

            <!-- 状态 pill 行: 在线 + 配置同步 + Agent / Xray 装机完备度 (扫一眼识别健康度) -->
            <div class="status-pills">
              <span
                class="status-pill"
                :class="`pill-${AGENT_ONLINE_TAG_TYPE[s.onlineState] || 'default'}`"
                :title="`在线状态: ${AGENT_ONLINE_LABELS[s.onlineState]}`"
              >
                <span class="pill-dot" :class="{ pulse: s.onlineState === 'ONLINE' }"></span>
                <span class="pill-label">{{ AGENT_ONLINE_LABELS[s.onlineState] }}</span>
              </span>
              <span
                class="status-pill"
                :class="`pill-${parseAgentVersion(s.agentVersion) ? 'success' : 'default'}`"
                :title="parseAgentVersion(s.agentVersion) ? 'Agent 已安装' : '未装 Agent (进 Agent tab 安装)'"
              >
                <NIcon class="pill-icon"><Cpu :size="11" /></NIcon>
                <span class="pill-label">
                  Agent <template v-if="parseAgentVersion(s.agentVersion)">v{{ parseAgentVersion(s.agentVersion)!.ver }}</template>
                  <template v-else>未装</template>
                </span>
              </span>
              <span
                class="status-pill"
                :class="`pill-${s.xrayVersion ? 'success' : 'default'}`"
                :title="s.xrayVersion ? 'Xray 已安装' : '未装 Xray (进 Xray tab 安装)'"
              >
                <NIcon class="pill-icon"><ServerCog :size="11" /></NIcon>
                <span class="pill-label">
                  Xray <template v-if="s.xrayVersion">{{ s.xrayVersion }}</template>
                  <template v-else>未装</template>
                </span>
              </span>
            </div>

            <!-- 心跳延迟 (单字段, 一行展示) -->
            <div class="heartbeat-row" v-if="s.elapsedSec != null || s.onlineState !== 'NEVER'">
              <NIcon class="key-icon"><Clock :size="12" /></NIcon>
              <span class="status-key">心跳延迟</span>
              <span class="flex-1"></span>
              <span
                v-if="s.elapsedSec != null"
                class="elapsed"
                :class="{ 'elapsed-stale': elapsedStale(s.elapsedSec) }"
                :title="`${s.elapsedSec} 秒`"
              >
                <span v-for="(p, i) in formatElapsed(s.elapsedSec)" :key="i" class="num-sm">
                  {{ p.n }}<span class="unit-sm">{{ p.u }}</span>
                </span>
              </span>
              <span v-else class="text-zinc-400 text-xs">—</span>
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

          </div>
        </div>
      </div>

      <div v-if="total > 0" class="pagination-bar">
        <NPagination
          :page="pageNo"
          :page-size="pageSize"
          :item-count="total"
          :page-sizes="[9, 18, 36]"
          show-size-picker
          size="small"
          @update:page="onPageChange"
          @update:page-size="onPageSizeChange"
        />
      </div>
    </NSpin>

    <ServerCreateDialog v-model="createOpen" @created="onCreated" />
    </div>
  </div>
</template>

<style scoped>
.ov-layout {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}
.overview-wrap {
  flex: 1;
  min-width: 0;
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

/* 状态 pill 行: 卡片头部下方; 颜色编码 + 圆点 + 文字, 比 tag 更醒目 */
.status-pills {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}
.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 8px 3px 7px;
  font-size: 11px;
  font-weight: 500;
  line-height: 1;
  border-radius: 10px;
  border: 1px solid transparent;
  white-space: nowrap;
}
.pill-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  flex-shrink: 0;
}
.pill-dot.pulse {
  animation: pill-pulse 2s ease-in-out infinite;
}
@keyframes pill-pulse {
  0%, 100% { box-shadow: 0 0 0 0 currentColor; opacity: 1; }
  50% { box-shadow: 0 0 0 4px transparent; opacity: 0.6; }
}
.pill-icon {
  display: inline-flex;
  align-items: center;
  flex-shrink: 0;
}
.pill-label {
  line-height: 1;
}
.pill-success {
  background: rgba(22, 163, 74, 0.1);
  color: #16a34a;
  border-color: rgba(22, 163, 74, 0.22);
}
.pill-warning {
  background: rgba(202, 138, 4, 0.1);
  color: #ca8a04;
  border-color: rgba(202, 138, 4, 0.22);
}
.pill-error {
  background: rgba(220, 38, 38, 0.1);
  color: #dc2626;
  border-color: rgba(220, 38, 38, 0.22);
}
.pill-default {
  background: rgba(161, 161, 170, 0.1);
  color: #71717a;
  border-color: rgba(161, 161, 170, 0.22);
}
html[data-theme='dark'] .pill-success { color: #4ade80; background: rgba(74, 222, 128, 0.12); border-color: rgba(74, 222, 128, 0.25); }
html[data-theme='dark'] .pill-warning { color: #facc15; background: rgba(250, 204, 21, 0.12); border-color: rgba(250, 204, 21, 0.25); }
html[data-theme='dark'] .pill-error   { color: #f87171; background: rgba(248, 113, 113, 0.12); border-color: rgba(248, 113, 113, 0.25); }
html[data-theme='dark'] .pill-default { color: #a1a1aa; background: rgba(161, 161, 170, 0.14); border-color: rgba(161, 161, 170, 0.22); }

/* 心跳延迟行 (单字段, 替代之前的 status-grid) */
.heartbeat-row {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 8px;
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
/* .num-sm / .unit-sm 走 main.scss 全局 tokens (卡片紧凑变体) */

/* 心跳延迟: 复合单位 (如 2h 15m) 之间留 1 个 num-sm 字宽的间距 */
.elapsed > .num-sm + .num-sm {
  margin-left: 4px;
}
/* stale (≥1h) 提示色: 上方 pill 已表达健康度, 这里仅做"久未上报"附加视觉提醒 */
.elapsed-stale .num-sm,
.elapsed-stale .unit-sm {
  color: #ea580c;
}
html[data-theme='dark'] .elapsed-stale .num-sm,
html[data-theme='dark'] .elapsed-stale .unit-sm {
  color: #fb923c;
}

.traffic-row {
  margin-top: 10px;
}
.traffic-head {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
