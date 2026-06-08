<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import {
  Activity,
  Gauge,
  LineChart,
  RefreshCcw,
  Server as ServerIcon,
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
  getServerLandingQuota,
  type ServerLanding,
  type ServerLandingQuota
} from '@/api/resource/server-landing'
import {
  getServerSystemInfo,
  getServerUfwStatus,
  type ServerSystemInfo,
  type SystemdStatus
} from '@/api/resource/server-ops'
import { formatDateTime } from '@/utils/date'

/**
 * 落地机详情 — 监控面板 tab. 结构对齐线路机 MonitoringTab.
 *
 * <p>3 metric 卡片 (本月流量 / Agent 心跳 / SOCKS5 监听) + 流量详情 + 主机 UFW + 历史趋势.
 * <p>statusData (SOCKS5 服务状态) 由父组件下发, 仅用于 SOCKS5 监听 metric.
 * <p>主机 / UFW 走通用 /admin/resource/server/get-system-info + /get-ufw-status, 跟线路机一致.
 */
const props = defineProps<{
  detail: ServerLanding
  canManage: boolean
  isAgentOnline: boolean
  statusData: SystemdStatus | null
}>()

const message = useMessage()
const capacity = ref<ServerLandingQuota | null>(null)
const loading = ref(false)

// 主机 / UFW: 实时 SSH 探测, 懒加载
const hostInfo = ref<ServerSystemInfo | null>(null)
const ufwStatus = ref<string>('')
const hostLoading = ref(false)
const hostLoaded = ref(false)

async function loadCapacity() {
  if (!props.detail?.id) return
  loading.value = true
  try {
    capacity.value = await getServerLandingQuota(props.detail.id)
  } catch (e) {
    message.error('拉容量失败: ' + ((e as Error).message ?? ''))
  } finally {
    loading.value = false
  }
}

async function loadHostStatus() {
  if (!props.detail?.id) return
  hostLoading.value = true
  try {
    const [h, u] = await Promise.all([
      getServerSystemInfo(props.detail.id),
      getServerUfwStatus(props.detail.id)
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

onMounted(loadCapacity)
watch(() => props.detail?.id, loadCapacity)

// ===== 流量计算 =====
const GB = 1024 * 1024 * 1024
function bytesToGB(b?: number | null): number {
  if (b == null) return 0
  return Number((b / GB).toFixed(2))
}
const rxGB = computed(() => bytesToGB(capacity.value?.rxBytes))
const txGB = computed(() => bytesToGB(capacity.value?.txBytes))
const usedGB = computed(() => bytesToGB(capacity.value?.usedBytes))
const totalGB = computed(() => capacity.value?.totalGb ?? 0)
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

const RESET_POLICY_LABELS: Record<string, string> = {
  MONTHLY: '按月重置',
  FIXED: '永不重置'
}
const RESET_POLICY_TYPE: Record<string, 'info' | 'success' | 'default'> = {
  MONTHLY: 'success',
  FIXED: 'default'
}
const THROTTLE_LABELS: Record<string, string> = {
  NORMAL: '正常',
  THROTTLED: '限流中'
}

// ===== 心跳颜色 =====
const heartbeatColor = computed(() => {
  if (props.isAgentOnline) return '#16a34a'
  if (props.detail?.lastHeartbeatAt) return '#dc2626'
  return '#9ca3af'
})

// ===== SOCKS5 端口监听 =====
// 公共 systemd 接口只回 active 状态; dante 启动时端口绑不上就会进 failed, active=active 即可视为监听正常
const socks5Listening = computed<'unknown' | 'listening' | 'down'>(() => {
  if (!props.statusData) return 'unknown'
  return props.statusData.active === 'active' ? 'listening' : 'down'
})
const socks5StateLabel = computed(() => {
  if (socks5Listening.value === 'listening') return { text: '运行中', type: 'success' as const }
  if (socks5Listening.value === 'down') return { text: '未运行', type: 'error' as const }
  return { text: '未探测', type: 'default' as const }
})
</script>

<template>
  <NSpin :show="loading">
    <div class="space-y-3">

      <!-- 操作栏 -->
      <div class="action-bar">
        <NButton size="small" quaternary @click="loadCapacity">
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
              <NTag size="small" :type="isAgentOnline ? 'success' : (detail.lastHeartbeatAt ? 'error' : 'default')">
                {{ isAgentOnline ? '在线' : (detail.lastHeartbeatAt ? '离线' : '未上报') }}
              </NTag>
            </div>
            <div class="metric-sub mt-2">
              <div class="text-xs text-zinc-400">
                上次: {{ formatDateTime(detail.lastHeartbeatAt) || '—' }}
              </div>
            </div>
          </div>
        </NCard>

        <!-- SOCKS5 监听 (替换线路机的客户配额; landing 关注 socks5 端口状态) -->
        <NCard size="small" :bordered="false" class="metric-card">
          <div class="metric-header">
            <NIcon class="metric-icon"><ServerIcon :size="16" /></NIcon>
            <span class="metric-title">SOCKS5 监听</span>
          </div>
          <div class="metric-body">
            <div class="metric-main">
              <NTag size="small" :type="socks5StateLabel.type">{{ socks5StateLabel.text }}</NTag>
            </div>
            <div class="metric-sub mt-2 text-xs">
              <code class="font-mono">{{ detail.ipAddress }}:{{ detail.socks5Port ?? '—' }}</code>
              <div v-if="!canManage" class="text-zinc-400 mt-1">需要 SSH 凭据齐才能探测</div>
            </div>
          </div>
        </NCard>
      </div>

      <!-- ============ 流量详情 ============ -->
      <NCard size="small" :bordered="false" class="info-section">
        <template #header>
          <div class="section-header">
            <NIcon class="section-icon"><Wifi :size="14" /></NIcon>
            <span>流量详情</span>
          </div>
        </template>
        <NDescriptions
          bordered
          size="small"
          label-placement="left"
          :column="2"
          :label-style="{ width: '8rem', verticalAlign: 'middle' }"
        >
          <NDescriptionsItem label="限定带宽">
            <template v-if="capacity?.bandwidthMbps">
              <span class="num">{{ capacity.bandwidthMbps }}</span>
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
            <NTag v-if="capacity?.resetPolicy" size="small" :type="RESET_POLICY_TYPE[capacity.resetPolicy] || 'default'">
              {{ RESET_POLICY_LABELS[capacity.resetPolicy] || capacity.resetPolicy }}
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

      <!-- ============ 主机 + UFW (自 fetch, 跟线路机一致) ============ -->
      <NCard size="small" :bordered="false" class="info-section">
        <template #header>
          <div class="section-header">
            <NIcon class="section-icon"><ServerIcon :size="14" /></NIcon>
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
          <NIcon :size="32"><ServerIcon /></NIcon>
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
          流量 / 心跳趋势曲线待后端接入时序数据.<br>
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

/* 关键指标 3 卡 (跟线路机一致) */
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
.metric-body { min-height: 70px; }
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

/* rx/tx inline tag (跟线路机一致) */
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

/* 主机/UFW 段未加载时占位 (留高度防页面跳, 图标 + 短提示) */
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
.placeholder-block--warn {
  background: rgba(245, 158, 11, 0.05);
  border-color: rgba(245, 158, 11, 0.3);
  color: #b45309;
}
.placeholder-text {
  color: inherit;
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
