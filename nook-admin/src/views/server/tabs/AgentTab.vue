<script setup lang="ts">
import { computed, ref } from 'vue'
import { ArrowUp, FileCog, History, Rocket } from 'lucide-vue-next'
import { NAlert, NButton, NIcon, NTag } from 'naive-ui'
import {
  AGENT_ONLINE_LABELS,
  AGENT_ONLINE_TAG_TYPE,
  CONFIG_SYNC_LABELS,
  CONFIG_SYNC_TAG_TYPE,
  type AgentListItem
} from '@/api/agent/agent'
import { formatDateTime } from '@/utils/date'
import AgentProvisionDialog from '@/views/agent/AgentProvisionDialog.vue'
import ConfigEditDialog from '@/views/agent/ConfigEditDialog.vue'
import AgentTaskHistoryDialog from '@/views/agent/AgentTaskHistoryDialog.vue'

const props = defineProps<{
  serverId: string
  agentInfo: AgentListItem | null
}>()
const emit = defineEmits<{ refresh: [] }>()

const provisionOpen = ref(false)
const configOpen = ref(false)
const historyOpen = ref(false)

const role = computed<'frontline' | 'landing'>(() => {
  const v = props.agentInfo?.agentVersion
  if (v && v.startsWith('landing-')) return 'landing'
  return 'frontline'
})

function onDispatched() {
  // 部署/升级派出去后, 延 3s 刷一次给 backend 写 + agent 心跳一个余量
  setTimeout(() => emit('refresh'), 3000)
}
function onConfigSaved() {
  emit('refresh')
}
</script>

<template>
  <div class="space-y-3">
    <NAlert v-if="!agentInfo?.agentVersion" type="warning" :show-icon="false" size="small">
      该 server 尚未装 agent — 点"部署 / 升级"开装机流.
    </NAlert>

    <!-- 当前 agent 状态摘要 -->
    <div v-if="agentInfo" class="grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
      <div class="info-row"><span class="k">agentVersion</span><code class="v">{{ agentInfo.agentVersion || '未装' }}</code></div>
      <div class="info-row"><span class="k">在线状态</span>
        <NTag size="tiny" :type="AGENT_ONLINE_TAG_TYPE[agentInfo.onlineState] || 'default'">
          {{ AGENT_ONLINE_LABELS[agentInfo.onlineState] }}
        </NTag>
        <span v-if="agentInfo.elapsedSec != null" class="text-zinc-400 font-mono">{{ agentInfo.elapsedSec }}s</span>
      </div>
      <div class="info-row"><span class="k">上次心跳</span><span class="v">{{ formatDateTime(agentInfo.lastHeartbeatAt) || '—' }}</span></div>
      <div class="info-row"><span class="k">tempUnhealthy</span><span class="v">{{ agentInfo.tempUnhealthy === 1 ? '是' : '否' }}</span></div>
      <div class="info-row"><span class="k">配置同步</span>
        <NTag size="tiny" :type="CONFIG_SYNC_TAG_TYPE[agentInfo.configSyncState || 'NEVER_CONFIGURED'] || 'default'">
          {{ CONFIG_SYNC_LABELS[agentInfo.configSyncState || 'NEVER_CONFIGURED'] }}
        </NTag>
      </div>
    </div>

    <!-- 操作按钮 -->
    <div class="flex gap-2 pt-2 border-t border-zinc-200">
      <NButton size="small" type="primary" @click="provisionOpen = true">
        <template #icon><NIcon><Rocket /></NIcon></template>
        部署 / 升级
      </NButton>
      <NButton size="small" type="info" @click="configOpen = true" :disabled="!agentInfo">
        <template #icon><NIcon><FileCog /></NIcon></template>
        改 agent 配置
      </NButton>
      <NButton size="small" quaternary @click="historyOpen = true">
        <template #icon><NIcon><History /></NIcon></template>
        任务历史
      </NButton>
      <div class="flex-1"></div>
      <NButton
        size="small"
        type="success"
        :disabled="!agentInfo || agentInfo.onlineState === 'OFFLINE' || agentInfo.onlineState === 'NEVER'"
        @click="provisionOpen = true"
      >
        <template #icon><NIcon><ArrowUp /></NIcon></template>
        一键升级 binary
      </NButton>
    </div>

    <AgentProvisionDialog
      v-model="provisionOpen"
      :initial-server-id="serverId"
      :initial-role="role"
      @dispatched="onDispatched"
    />
    <ConfigEditDialog
      v-model="configOpen"
      :server-id="serverId"
      :server-name="agentInfo?.serverName"
      :role="role"
      @saved="onConfigSaved"
    />
    <AgentTaskHistoryDialog
      v-model="historyOpen"
      :server-id="serverId"
      :server-name="agentInfo?.serverName"
    />
  </div>
</template>

<style scoped>
/* info-row / k / v 走 main.scss 全局 tokens */
</style>
