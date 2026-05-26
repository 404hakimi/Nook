<script setup lang="ts">
import { computed } from 'vue'
import {
  AlertCircle,
  CheckCircle2,
  KeyRound,
  Plug,
  RefreshCcw,
  Server as ServerIcon
} from 'lucide-vue-next'
import { NButton, NIcon, NTag } from 'naive-ui'
import type { ServerLanding, Socks5ServiceStatus } from '@/api/resource/server-landing'

/**
 * 落地机详情 — 监控面板 tab.
 *
 * <p>展示部署进度 + 端口可达 + 远端主机信息 (SSH 探测得到).
 * statusData 由父组件统一拉取并下发, 这里只渲染.
 */
const props = defineProps<{
  detail: ServerLanding
  /** SSH 凭据齐全 (provisionMode=1 且 sshPassword 已配置) 才能 SSH 探测远端 */
  canManage: boolean
  /** Agent 是否在线 (5min 内有心跳) */
  isAgentOnline: boolean
  /** SSH 是否齐 (host + user + password). */
  sshComplete: boolean
  /** SOCKS5 是否完成安装 + LIVE. */
  socks5Installed: boolean
  statusData: Socks5ServiceStatus | null
  statusLoading: boolean
  statusError: string
}>()
const emit = defineEmits<{ 'load-status': [] }>()

// ===== 部署进度 4 step =====
const deploySteps = computed(() => [
  { label: 'SSH 凭据', done: props.sshComplete },
  { label: '部署 SOCKS5', done: props.socks5Installed },
  { label: '安装 Agent', done: !!props.detail.lastHeartbeatAt },
  { label: '心跳健康', done: props.isAgentOnline }
])

const sshReachable = computed<'unknown' | 'open' | 'closed'>(() => {
  if (props.statusError) return 'closed'
  if (!props.statusData) return 'unknown'
  return props.statusData.hostInfo ? 'open' : 'unknown'
})
const socks5Listening = computed<'unknown' | 'listening' | 'down'>(() => {
  if (!props.statusData) return 'unknown'
  if (props.statusData.active !== 'active') return 'down'
  if (props.statusData.listening && props.detail.socks5Port
      && props.statusData.listening.includes(`:${props.detail.socks5Port}`)) {
    return 'listening'
  }
  return 'down'
})
</script>

<template>
  <!-- 部署进度时间线 -->
  <div class="section-title">部署进度</div>
  <div class="deploy-steps">
    <div
      v-for="(step, idx) in deploySteps"
      :key="step.label"
      class="deploy-step"
      :class="{ 'deploy-step--done': step.done }"
    >
      <div class="deploy-step__dot">
        <NIcon v-if="step.done" :size="14"><CheckCircle2 /></NIcon>
        <span v-else>{{ idx + 1 }}</span>
      </div>
      <span>{{ step.label }}</span>
    </div>
  </div>

  <!-- 端口可达 -->
  <div class="section-title mt-4 flex items-center justify-between">
    <span>端口可达</span>
    <NButton
      size="tiny"
      quaternary
      :loading="statusLoading"
      :disabled="!canManage"
      @click="emit('load-status')"
    >
      <template #icon><NIcon><Plug /></NIcon></template>
      {{ statusData ? '重新探测' : '探测' }}
    </NButton>
  </div>
  <div v-if="!canManage" class="hint text-warning">
    <NIcon :size="14"><AlertCircle /></NIcon>
    需要 SSH 凭据齐才能探测; 去 SSH 凭据 tab 补全
  </div>
  <div v-else class="grid grid-cols-1 sm:grid-cols-2 gap-3">
    <div class="port-card" :class="`port-card--${sshReachable}`">
      <NIcon :size="20"><KeyRound /></NIcon>
      <div class="flex-1">
        <div class="text-xs text-zinc-500">SSH</div>
        <div class="font-mono text-sm">{{ detail.ipAddress || '-' }}:{{ detail.sshPort ?? 22 }}</div>
      </div>
      <NTag
        size="small"
        :type="sshReachable === 'open' ? 'success' : (sshReachable === 'closed' ? 'error' : 'default')"
      >
        {{ sshReachable === 'open' ? '可达' : (sshReachable === 'closed' ? '不可达' : '未探测') }}
      </NTag>
    </div>
    <div class="port-card" :class="`port-card--${socks5Listening}`">
      <NIcon :size="20"><ServerIcon /></NIcon>
      <div class="flex-1">
        <div class="text-xs text-zinc-500">SOCKS5 (dante)</div>
        <div class="font-mono text-sm">{{ detail.ipAddress }}:{{ detail.socks5Port ?? '-' }}</div>
      </div>
      <NTag
        size="small"
        :type="socks5Listening === 'listening' ? 'success' : (socks5Listening === 'down' ? 'error' : 'default')"
      >
        {{ socks5Listening === 'listening' ? '监听中' : (socks5Listening === 'down' ? '未监听' : '未探测') }}
      </NTag>
    </div>
  </div>

  <!-- 远端主机信息 -->
  <div class="section-title mt-4 flex items-center justify-between">
    <span>远端主机信息</span>
    <NButton
      size="tiny"
      quaternary
      :loading="statusLoading"
      :disabled="!canManage"
      @click="emit('load-status')"
    >
      <template #icon><NIcon><RefreshCcw /></NIcon></template>
      {{ statusData ? '刷新' : '拉取' }}
    </NButton>
  </div>
  <div v-if="!canManage" class="empty-hint">
    <NIcon :size="18"><AlertCircle /></NIcon>
    <div>需要 SSH 凭据齐才能拉远端主机信息</div>
  </div>
  <div v-else-if="!statusData && !statusLoading && !statusError" class="hint">
    点 "拉取" 探测远端
  </div>
  <div v-else-if="statusError" class="hint text-error">{{ statusError }}</div>
  <div v-else-if="statusData?.hostInfo" class="info-grid">
    <div class="info-row"><span class="k">主机名</span><code class="v">{{ statusData.hostInfo.hostname || '-' }}</code></div>
    <div class="info-row"><span class="k">时区</span><span class="v">{{ statusData.hostInfo.timezone || '-' }}</span></div>
    <div class="info-row info-row--full"><span class="k">OS</span><span class="v text-xs">{{ statusData.hostInfo.osRelease || '-' }}</span></div>
    <div class="info-row info-row--full"><span class="k">内核</span><code class="v text-xs">{{ statusData.hostInfo.kernel || '-' }}</code></div>
    <div class="info-row"><span class="k">系统已运行</span><span class="v">{{ statusData.hostInfo.systemUptime || '-' }}</span></div>
    <div class="info-row"><span class="k">Load avg</span><code class="v">{{ statusData.hostInfo.loadAvg || '-' }}</code></div>
    <div class="info-row info-row--full"><span class="k">内存</span><pre class="status-pre">{{ statusData.hostInfo.memory || '-' }}</pre></div>
    <div class="info-row info-row--full"><span class="k">磁盘</span><pre class="status-pre">{{ statusData.hostInfo.disk || '-' }}</pre></div>
    <div v-if="statusData.ufwStatus" class="info-row info-row--full"><span class="k">UFW</span><pre class="status-pre">{{ statusData.ufwStatus }}</pre></div>
  </div>
  <div v-else class="hint">远端未返回 hostInfo</div>
</template>

<style scoped src="./landing-tabs.scss"></style>
