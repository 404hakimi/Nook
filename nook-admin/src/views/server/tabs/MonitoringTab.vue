<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import {
  Activity,
  Cpu,
  Gauge,
  LineChart,
  RefreshCcw,
  Server,
  ShieldCheck,
  Wifi
} from 'lucide-vue-next'
import {
  NAlert,
  NButton,
  NCard,
  NDescriptions,
  NDescriptionsItem,
  NIcon,
  NProgress,
  NSpin,
  NTag,
  useMessage
} from 'naive-ui'
import {
  getServerCapacity,
  type ServerCapacity
} from '@/api/resource/server'
import { getServerSystemInfo, getServerUfwStatus, type ServerSystemInfo } from '@/api/xray/server'
import { formatDateTime } from '@/utils/date'
import { AGENT_ONLINE_LABELS, AGENT_ONLINE_TAG_TYPE } from '@/api/agent/agent'
import type { ServerFrontlineListItem } from '@/api/resource/server'

const props = defineProps<{
  serverId: string
  agentInfo: ServerFrontlineListItem | null
}>()

const capacity = ref<ServerCapacity | null>(null)
const loading = ref(false)

// 主机 / UFW 状态: 走 SSH 实时拉, 不在 onMounted 跑, 避免每次进监控面板都触发远端命令; 用户点"加载"按钮才查
const hostInfo = ref<ServerSystemInfo | null>(null)
const ufwStatus = ref<string>('')
const hostLoading = ref(false)
const hostLoaded = ref(false)
const message = useMessage()

async function loadHostStatus() {
  if (!props.serverId) return
  hostLoading.value = true
  try {
    const [h, u] = await Promise.all([
      getServerSystemInfo(props.serverId),
      getServerUfwStatus(props.serverId)
    ])
    hostInfo.value = h
    ufwStatus.value = u
    hostLoaded.value = true
  } catch (e) {
    message.error('拉主机状态失败: ' + ((e as Error).message ?? ''))
  } finally {
    hostLoading.value = false
  }
}

async function load() {
  if (!props.serverId) return
  loading.value = true
  try {
    capacity.value = await getServerCapacity(props.serverId)
  } catch { /* */ } finally {
    loading.value = false
  }
}

onMounted(load)
watch(() => props.serverId, load)

// ===== 流量计算 (rx/tx 拆开 + 双向合计; bytes 是 long, JS Number 在 2^53 内精确) =====
const GB = 1024 * 1024 * 1024
function bytesToGB(b?: number | null): number {
  if (b == null) return 0
  return Number((b / GB).toFixed(2))
}
const rxGB = computed(() => bytesToGB(capacity.value?.rxBytes))
const txGB = computed(() => bytesToGB(capacity.value?.txBytes))
const usedGB = computed(() => bytesToGB(capacity.value?.usedTrafficBytes))
const totalGB = computed(() => capacity.value?.monthlyTrafficGb ?? 0)
const usedPercent = computed(() => {
  if (!totalGB.value) return 0
  return Math.min(100, Math.round((usedGB.value / totalGB.value) * 100))
})
const trafficStatus = computed<'default' | 'success' | 'warning' | 'error'>(() => {
  if (!totalGB.value) return 'default'
  if (usedPercent.value >= 90) return 'error'
  if (usedPercent.value >= 70) return 'warning'
  return 'success'
})
const throttled = computed(() => capacity.value?.throttleState === 'THROTTLED')

// ===== 标签化映射 (避免 NORMAL / FIXED 这种 raw value 暴露给运营) =====
const RESET_POLICY_LABELS: Record<string, string> = {
  BILLING_CYCLE: '按账单日',
  FIXED: '永不重置'
}
const RESET_POLICY_TYPE: Record<string, 'info' | 'success' | 'default'> = {
  BILLING_CYCLE: 'success',
  FIXED: 'default'
}
const THROTTLE_LABELS: Record<string, string> = {
  NORMAL: '正常',
  THROTTLED: '限流中'
}

// ===== 心跳分级 =====
const heartbeatColor = computed(() => {
  const s = props.agentInfo?.onlineState
  if (s === 'ONLINE') return '#16a34a'
  if (s === 'WARN') return '#ca8a04'
  if (s === 'TEMP_UNHEALTHY') return '#ea580c'
  if (s === 'OFFLINE') return '#dc2626'
  return '#9ca3af'
})
</script>

<template>
  <NSpin :show="loading">
    <div class="space-y-3">

      <!-- 操作栏 -->
      <div class="action-bar">
        <NButton size="small" quaternary @click="load">
          <template #icon><NIcon><RefreshCcw :size="14" /></NIcon></template>
          刷新数据
        </NButton>
        <div class="flex-1"></div>
        <NTag v-if="throttled" size="small" type="error">⚠ 限流中 (used ≥ 90%)</NTag>
      </div>

      <!-- ============ 关键指标卡片 (3 个) ============ -->
      <div class="metric-grid">
        <!-- 流量使用 -->
        <NCard size="small" :bordered="false" class="metric-card">
          <div class="metric-header">
            <NIcon class="metric-icon"><Gauge :size="16" /></NIcon>
            <span class="metric-title">本月流量</span>
          </div>
          <div v-if="totalGB > 0" class="metric-body">
            <div class="metric-main">
              <span class="metric-num">{{ usedGB }}</span>
              <span class="metric-unit">/ {{ totalGB }} GB</span>
            </div>
            <NProgress
              :percentage="usedPercent"
              :height="8"
              :status="trafficStatus"
              :show-indicator="false"
              class="mt-2"
            />
            <div class="metric-sub">
              已用 {{ usedPercent }}% · 剩 {{ Math.max(0, totalGB - usedGB).toFixed(2) }} GB
            </div>
          </div>
          <div v-else-if="capacity" class="metric-body">
            <div class="metric-main">
              <span class="metric-num">{{ usedGB }}</span>
              <span class="metric-unit">GB</span>
            </div>
            <div class="metric-sub mt-2">未设月度配额 (不限)</div>
          </div>
          <div v-else class="metric-body empty">
            <div class="text-zinc-400 text-sm">暂无数据</div>
            <div class="text-xs text-zinc-400 mt-1">远端流量上报后填充</div>
          </div>
        </NCard>

        <!-- 心跳健康 -->
        <NCard size="small" :bordered="false" class="metric-card">
          <div class="metric-header">
            <NIcon class="metric-icon"><Activity :size="16" /></NIcon>
            <span class="metric-title">Agent 心跳</span>
          </div>
          <div class="metric-body">
            <div class="metric-main">
              <span class="heartbeat-dot" :style="`background:${heartbeatColor}`"></span>
              <NTag v-if="agentInfo" size="small" :type="AGENT_ONLINE_TAG_TYPE[agentInfo.onlineState] || 'default'">
                {{ AGENT_ONLINE_LABELS[agentInfo.onlineState] }}
              </NTag>
              <span v-else class="text-zinc-400">未上报</span>
            </div>
            <div class="metric-sub mt-2">
              <div v-if="agentInfo?.elapsedSec != null">
                距今: <span class="font-mono">{{ agentInfo.elapsedSec }}s</span>
              </div>
              <div class="text-xs text-zinc-400 mt-1">
                上次: {{ formatDateTime(agentInfo?.lastHeartbeatAt) || '—' }}
              </div>
            </div>
          </div>
        </NCard>

        <!-- 客户连接配额 -->
        <NCard size="small" :bordered="false" class="metric-card">
          <div class="metric-header">
            <NIcon class="metric-icon"><Cpu :size="16" /></NIcon>
            <span class="metric-title">客户配额</span>
          </div>
          <div v-if="capacity" class="metric-body">
            <div class="metric-main">
              <span class="metric-num">—</span>
              <span class="metric-unit">/ {{ capacity.clientMaxCount ?? '—' }} 个</span>
            </div>
            <div class="metric-sub mt-2 text-xs text-zinc-400">
              已分配数待接入
            </div>
          </div>
        </NCard>
      </div>

      <!-- ============ 主机 + UFW (实时 SSH, 懒加载) ============ -->
      <NCard size="small" :bordered="false" class="info-section">
        <template #header>
          <div class="section-header">
            <NIcon class="section-icon"><Server :size="14" /></NIcon>
            <span>主机 / UFW 实时</span>
            <NTag size="tiny" type="default" class="ml-1">SSH</NTag>
            <span class="flex-1"></span>
            <NButton size="tiny" type="primary" :loading="hostLoading" @click="loadHostStatus">
              <template #icon><NIcon><RefreshCcw :size="12" /></NIcon></template>
              {{ hostLoaded ? '刷新' : '加载' }}
            </NButton>
          </div>
        </template>

        <div v-if="!hostLoaded && !hostLoading" class="placeholder-block">
          <NIcon :size="32"><Server /></NIcon>
          <span class="placeholder-text">点上方"加载"拉远端主机 / UFW 信息</span>
        </div>
        <div v-else-if="hostLoading && !hostInfo" class="placeholder-block">
          <NSpin :size="20" />
          <span class="placeholder-text">加载中…</span>
        </div>

        <template v-else>
          <NDescriptions
            v-if="hostInfo"
            bordered
            size="small"
            label-placement="left"
            :column="2"
            :label-style="{ width: '8rem' }"
            class="mb-3"
          >
            <NDescriptionsItem label="主机名">
              <code v-if="hostInfo.hostname" class="kbd">{{ hostInfo.hostname }}</code>
              <span v-else class="muted">—</span>
            </NDescriptionsItem>
            <NDescriptionsItem label="时区">
              <code v-if="hostInfo.timezone" class="kbd">{{ hostInfo.timezone }}</code>
              <span v-else class="muted">—</span>
            </NDescriptionsItem>
            <NDescriptionsItem label="系统">
              <span v-if="hostInfo.osRelease">{{ hostInfo.osRelease }}</span>
              <span v-else class="muted">—</span>
            </NDescriptionsItem>
            <NDescriptionsItem label="内核">
              <code v-if="hostInfo.kernel" class="kbd">{{ hostInfo.kernel }}</code>
              <span v-else class="muted">—</span>
            </NDescriptionsItem>
            <NDescriptionsItem label="运行时间">
              <span v-if="hostInfo.systemUptime">{{ hostInfo.systemUptime }}</span>
              <span v-else class="muted">—</span>
            </NDescriptionsItem>
            <NDescriptionsItem label="负载 (1/5/15m)">
              <code v-if="hostInfo.loadAvg" class="kbd">{{ hostInfo.loadAvg }}</code>
              <span v-else class="muted">—</span>
            </NDescriptionsItem>
            <NDescriptionsItem label="内存">
              <code v-if="hostInfo.memory" class="kbd">{{ hostInfo.memory }}</code>
              <span v-else class="muted">—</span>
            </NDescriptionsItem>
            <NDescriptionsItem label="磁盘 (/)">
              <code v-if="hostInfo.disk" class="kbd">{{ hostInfo.disk }}</code>
              <span v-else class="muted">—</span>
            </NDescriptionsItem>
          </NDescriptions>

          <div v-if="hostLoaded" class="ufw-block">
            <div class="text-xs text-zinc-500 mb-1 flex items-center gap-1">
              <NIcon><ShieldCheck :size="12" /></NIcon> UFW 防火墙
            </div>
            <pre class="ufw-pre">{{ ufwStatus || '(未获取到)' }}</pre>
          </div>
        </template>
      </NCard>

      <!-- ============ 流量详情 ============ -->
      <NCard size="small" :bordered="false" class="info-section">
        <template #header>
          <div class="section-header">
            <NIcon class="section-icon"><Wifi :size="14" /></NIcon>
            <span>流量详情</span>
          </div>
        </template>
        <!--
          布局: 左 = 承诺带宽 / 月度配额 (容量类); 右 = 本月已用 / 重置策略 (使用类).
          本月已用 行内拆 rx (下行) + tx (上行); 限流状态独占一行.
        -->
        <!--
          布局: 左 (承诺带宽 / 月度配额) | 右 (本月已用 / 重置策略); 限流状态 span=2.
          本月已用 单行展示 合计 + rx/tx, 让每行左右 cell 高度一致, 标签视觉对齐.
        -->
        <NDescriptions
          bordered
          size="small"
          label-placement="left"
          :column="2"
          :label-style="{ width: '8rem', verticalAlign: 'middle' }"
        >
          <NDescriptionsItem label="限定带宽">
            <template v-if="capacity?.bandwidthLimitMbps">
              <span class="num">{{ capacity.bandwidthLimitMbps }}</span>
              <span class="unit">Mbps</span>
            </template>
            <span v-else class="muted">不限</span>
          </NDescriptionsItem>
          <NDescriptionsItem label="本月已用">
            <span class="num">{{ usedGB }}</span>
            <span class="unit">GB</span>
            <template v-if="capacity?.rxBytes != null || capacity?.txBytes != null">
              <span class="rxtx-divider"></span>
              <span class="rxtx-tag rx">↓</span>
              <span class="num">{{ rxGB }}</span><span class="unit">GB</span>
              <span class="rxtx-tag tx">↑</span>
              <span class="num">{{ txGB }}</span><span class="unit">GB</span>
            </template>
          </NDescriptionsItem>
          <NDescriptionsItem label="月度配额">
            <template v-if="totalGB > 0">
              <span class="num">{{ totalGB }}</span>
              <span class="unit">GB</span>
            </template>
            <span v-else class="muted">不限</span>
          </NDescriptionsItem>
          <NDescriptionsItem label="重置策略">
            <NTag v-if="capacity?.quotaResetPolicy" size="small" :type="RESET_POLICY_TYPE[capacity.quotaResetPolicy] || 'default'">
              {{ RESET_POLICY_LABELS[capacity.quotaResetPolicy] || capacity.quotaResetPolicy }}
            </NTag>
            <span v-else class="muted">—</span>
          </NDescriptionsItem>
          <NDescriptionsItem label="限流状态" :span="2">
            <NTag size="small" :type="throttled ? 'error' : 'success'">
              {{ throttled ? THROTTLE_LABELS.THROTTLED : THROTTLE_LABELS.NORMAL }}
            </NTag>
            <span class="text-xs text-zinc-400 ml-2">月用量达 90% 自动切限流, 暂停参与新订阅分配</span>
          </NDescriptionsItem>
        </NDescriptions>
      </NCard>

      <!-- ============ 时序趋势 (placeholder) ============ -->
      <NCard size="small" :bordered="false" class="info-section">
        <template #header>
          <div class="section-header">
            <NIcon class="section-icon"><LineChart :size="14" /></NIcon>
            <span>历史趋势</span>
            <NTag size="tiny" type="default" class="ml-1">待接入</NTag>
          </div>
        </template>
        <NAlert type="info" :show-icon="false" size="small">
          流量 / 心跳趋势曲线待后端接入时序数据 (推荐方案: 每 5min snapshot 写表, 保留 30 天).<br>
          当前页面已展示当下快照 — 上方关键指标卡片实时刷新.
        </NAlert>
        <div class="chart-placeholder">
          <NIcon class="text-zinc-300"><LineChart :size="48" /></NIcon>
          <div class="text-zinc-400 text-sm mt-2">📊 折线图占位</div>
        </div>
      </NCard>

    </div>
  </NSpin>
</template>

<style scoped>
.action-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

/* 关键指标 3 卡 */
.metric-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}
@media (max-width: 900px) {
  .metric-grid { grid-template-columns: 1fr; }
}
.metric-card {
  background: linear-gradient(to bottom right, rgba(99, 102, 241, 0.04), transparent);
}
.metric-card :deep(.n-card__content) {
  padding: 14px 16px;
}
.metric-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #71717a;
  margin-bottom: 8px;
}
.metric-icon { color: #6366f1; }
.metric-title { font-weight: 500; }
.metric-body {
  min-height: 70px;
}
.metric-body.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}
.metric-main {
  display: flex;
  align-items: baseline;
  gap: 6px;
}
.metric-num {
  font-size: 28px;
  font-weight: 600;
  font-family: 'JetBrains Mono', monospace;
  color: #18181b;
  line-height: 1;
}
html[data-theme='dark'] .metric-num { color: #e4e4e7; }
.metric-unit {
  font-size: 13px;
  color: #71717a;
}
.metric-sub {
  font-size: 12px;
  color: #52525b;
}
html[data-theme='dark'] .metric-sub { color: #a1a1aa; }

.heartbeat-dot {
  display: inline-block;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  margin-right: 4px;
}

/* section / num / unit / muted 走 main.scss 全局 tokens */

/* rx/tx 拆分 inline tag (本月已用 单元内嵌) */
.rxtx-tag {
  display: inline-block;
  padding: 0 4px;
  font-size: 11px;
  border-radius: 3px;
  font-weight: 600;
  margin-left: 4px;
  margin-right: 2px;
}
.rxtx-tag.rx { background: rgba(34, 197, 94, 0.12); color: #16a34a; }
.rxtx-tag.tx { background: rgba(59, 130, 246, 0.12); color: #2563eb; }
.rxtx-divider {
  display: inline-block;
  width: 1px;
  height: 12px;
  background: rgba(127, 127, 127, 0.2);
  margin: 0 8px;
  vertical-align: middle;
}

.ufw-pre {
  font-family: 'JetBrains Mono', ui-monospace, monospace;
  font-size: 11px;
  line-height: 1.5;
  margin: 0;
  padding: 8px 10px;
  background: rgba(127, 127, 127, 0.06);
  border-radius: 4px;
  max-height: 12rem;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}

/* 主机/UFW 段未加载时的占位 (留出最低高度避免页面跳, 灰色图标 + 短提示) */
.placeholder-block {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-height: 80px;
  color: #a1a1aa;
  font-size: 13px;
  background: rgba(127, 127, 127, 0.03);
  border: 1px dashed rgba(127, 127, 127, 0.15);
  border-radius: 4px;
}
.placeholder-text {
  color: #71717a;
}

.chart-placeholder {
  margin-top: 10px;
  padding: 32px;
  border: 1px dashed rgba(127, 127, 127, 0.2);
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: rgba(127, 127, 127, 0.02);
}
</style>
