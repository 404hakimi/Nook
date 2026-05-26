<script setup lang="ts">
import { AlertCircle, Server as ServerIcon } from 'lucide-vue-next'
import { NButton, NIcon, NTag, NTooltip } from 'naive-ui'
import type { ServerLanding } from '@/api/resource/server-landing'
import { formatDateTime } from '@/utils/date'
import { relativeTime, maskSecret } from './landingHelpers'

/**
 * 落地机详情 — Agent tab.
 *
 * <p>展示 agent 安装状态 / 健康状态 / token (mask) / 最近心跳; 提供"安装 Agent"按钮.
 */
defineProps<{
  detail: ServerLanding
  isSelfDeploy: boolean
  agentProvisioned: boolean
  agentHealthLabel: { text: string; type: 'success' | 'error' | 'default' }
}>()
const emit = defineEmits<{ 'open-provision': [] }>()
</script>

<template>
  <div class="tab-actions">
    <NButton v-if="isSelfDeploy" size="small" type="primary" @click="emit('open-provision')">
      <template #icon><NIcon><ServerIcon /></NIcon></template>
      {{ agentProvisioned ? '重装 Agent' : '安装 Agent' }}
    </NButton>
  </div>

  <div v-if="!isSelfDeploy" class="empty-hint">
    <NIcon :size="18"><AlertCircle /></NIcon>
    <div>第三方 SOCKS5 不需要 Agent</div>
  </div>
  <div v-else class="info-grid">
    <div class="info-row"><span class="k">安装状态</span>
      <NTag size="tiny" :type="agentProvisioned ? 'success' : 'default'">
        {{ agentProvisioned ? '已安装' : '未安装' }}
      </NTag>
      <span v-if="!agentProvisioned && detail.agentToken" class="text-xs text-zinc-400 ml-1">
        (等待装机 + 首次心跳)
      </span>
    </div>
    <div class="info-row"><span class="k">健康状态</span>
      <NTag size="tiny" :type="agentHealthLabel.type">{{ agentHealthLabel.text }}</NTag>
    </div>
    <div v-if="detail.agentToken" class="info-row info-row--full"><span class="k">Agent token</span>
      <NTooltip>
        <template #trigger>
          <code class="v">{{ maskSecret(detail.agentToken) }}</code>
        </template>
        <span class="text-xs">出于安全, token 仅 mask 展示</span>
      </NTooltip>
    </div>
    <div v-if="detail.lastHeartbeatAt" class="info-row info-row--full"><span class="k">最近心跳</span>
      <span class="v">{{ relativeTime(detail.lastHeartbeatAt) }}
        <span class="text-zinc-400 ml-1">({{ formatDateTime(detail.lastHeartbeatAt) }})</span>
      </span>
    </div>
    <div class="info-row info-row--full"><span class="k">说明</span>
      <span class="v text-xs text-zinc-500">Agent 装好后每 30s 心跳; 在线 = 5min 内有心跳</span>
    </div>
  </div>
</template>

<style scoped src="./landing-tabs.scss"></style>
