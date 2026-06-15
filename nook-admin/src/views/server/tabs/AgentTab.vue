<script setup lang="ts">
import { computed, ref } from 'vue'
import { FileText, HelpCircle, Rocket } from 'lucide-vue-next'
import { NButton, NDescriptions, NDescriptionsItem, NIcon, NTag, NTooltip } from 'naive-ui'
import {
  AGENT_ONLINE,
  AGENT_ONLINE_LABELS,
  AGENT_ONLINE_TAG_TYPE
} from '@/api/agent/agent'
import type { ServerFrontlineListItem } from '@/api/resource/server'
import { formatDateTime } from '@/utils/date'
import AgentDeployDialog from '@/views/agent/AgentDeployDialog.vue'
import AgentLogDialog from '@/views/agent/AgentLogDialog.vue'

const props = defineProps<{
  serverId: string
  /** 必填 — 不依赖 agentInfo 推断 (未装机时 agentVersion 是空, 推断不出来) */
  role: 'frontline' | 'landing'
  agentInfo: ServerFrontlineListItem | null
}>()
const emit = defineEmits<{ refresh: [] }>()

const deployOpen = ref(false)
const logOpen = ref(false)

const provisioned = computed(() => !!props.agentInfo?.agentVersion)

const healthLabel = computed(() => {
  if (!provisioned.value) return { text: '未装', type: 'default' as const }
  const a = props.agentInfo!
  if (a.onlineState === AGENT_ONLINE.OFFLINE) return { text: '离线', type: 'error' as const }
  if (a.onlineState === AGENT_ONLINE.TEMP_UNHEALTHY) return { text: '心跳不稳定', type: 'warning' as const }
  if (a.onlineState === AGENT_ONLINE.ONLINE) return { text: '正常', type: 'success' as const }
  return { text: AGENT_ONLINE_LABELS[a.onlineState] || '?', type: 'default' as const }
})

const hostLabel = computed(() => {
  const a = props.agentInfo
  if (!a) return ''
  return a.name + (a.agentVersion ? ` (${a.agentVersion})` : '')
})

function onDeployed() {
  // 部署成功后延 3s 刷, 给 backend 写 + agent 心跳一个余量
  setTimeout(() => emit('refresh'), 3000)
}
</script>

<template>
  <div class="space-y-3">
    <!-- ===== 操作栏: 部署 / 升级 拆两个独立按钮 ===== -->
    <div class="tab-actions">
      <NButton
        v-if="!provisioned"
        size="small"
        type="primary"
        @click="deployOpen = true"
      >
        <template #icon><NIcon><Rocket /></NIcon></template>
        部署 agent
      </NButton>
      <NButton
        v-if="provisioned"
        size="small"
        type="primary"
        @click="deployOpen = true"
        title="重新跑 SSH 装机流 (覆盖 binary + config + systemd unit + 重启)"
      >
        <template #icon><NIcon><Rocket /></NIcon></template>
        重新部署
      </NButton>
      <NButton size="small" quaternary :disabled="!provisioned" @click="logOpen = true">
        <template #icon><NIcon><FileText /></NIcon></template>
        查看日志
      </NButton>
    </div>

    <!-- ===== 未装: empty-hint ===== -->
    <div v-if="!provisioned" class="empty-hint">
      <NIcon :size="18"><Rocket /></NIcon>
      <div>
        <div class="font-semibold">该 server 尚未装 agent</div>
        <div class="text-xs text-zinc-500 mt-1">点 "部署 agent" 开装机流</div>
      </div>
    </div>

    <!-- ===== 已装: NDescriptions 摘要 ===== -->
    <NDescriptions
      v-else
      bordered size="small" label-placement="left" :column="2"
      label-style="width: 7rem; white-space: nowrap"
    >
      <NDescriptionsItem label="agent 版本">
        <code class="kbd">{{ agentInfo!.agentVersion || '未装' }}</code>
      </NDescriptionsItem>
      <NDescriptionsItem label="在线状态">
        <NTag size="tiny" :type="AGENT_ONLINE_TAG_TYPE[agentInfo!.onlineState] || 'default'">
          {{ AGENT_ONLINE_LABELS[agentInfo!.onlineState] }}
        </NTag>
        <span v-if="agentInfo!.elapsedSec != null" class="text-zinc-400 font-mono ml-2">{{ agentInfo!.elapsedSec }}s</span>
      </NDescriptionsItem>
      <NDescriptionsItem label="上次心跳">
        <code class="kbd">{{ formatDateTime(agentInfo!.lastHeartbeatAt) || '—' }}</code>
      </NDescriptionsItem>
      <NDescriptionsItem>
        <template #label>
          <span>健康状态</span>
          <NTooltip trigger="hover">
            <template #trigger>
              <NIcon class="hint-icon"><HelpCircle :size="12" /></NIcon>
            </template>
            "心跳不稳定" = 连续 3-5min 没收到心跳, 暂停分配新订阅; 继续无心跳 5min+ 触发掉线.
          </NTooltip>
        </template>
        <NTag size="tiny" :type="healthLabel.type">{{ healthLabel.text }}</NTag>
      </NDescriptionsItem>
    </NDescriptions>

    <!-- ===== Dialogs ===== -->
    <AgentDeployDialog
      v-model="deployOpen"
      :source-id="serverId"
      :role="role"
      :host-label="hostLabel"
      @deployed="onDeployed"
    />
    <AgentLogDialog
      v-model="logOpen"
      :server-id="serverId"
      :label="hostLabel"
    />
  </div>
</template>

<style scoped>
.tab-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  padding-bottom: 12px;
  margin-bottom: 4px;
  border-bottom: 1px dashed var(--n-divider-color, #efeff5);
  flex-wrap: wrap;
}
.empty-hint {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 18px 20px;
  background: var(--n-action-color, #fafafa);
  border: 1px dashed var(--n-border-color, #efeff5);
  border-radius: 6px;
  color: var(--n-text-color-2, #555);
}
.kbd {
  font-family: 'JetBrains Mono', 'Cascadia Code', Consolas, monospace;
  font-size: 12px;
  color: var(--n-text-color-1, #222);
  padding: 1px 6px;
  background: var(--n-action-color, #f5f5f5);
  border-radius: 3px;
}
.hint-icon {
  margin-left: 4px;
  vertical-align: middle;
  color: #a1a1aa;
  cursor: help;
}
</style>
