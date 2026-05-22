<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Activity, ArrowLeft, Cpu, Info, KeyRound, ServerCog } from 'lucide-vue-next'
import {
  NButton,
  NCard,
  NIcon,
  NSpace,
  NSpin,
  NTabs,
  NTabPane,
  NTag
} from 'naive-ui'
import {
  AGENT_ONLINE_LABELS,
  AGENT_ONLINE_TAG_TYPE,
  listAgents,
  type AgentListItem
} from '@/api/agent/agent'
import { SERVER_LIFECYCLE_LABELS, SERVER_LIFECYCLE_TAG_TYPE } from '@/api/resource/server'
import { listEnabledRegions, type ResourceRegion } from '@/api/resource/region'
import RegionFlag from '@/components/RegionFlag.vue'
import { formatDateTime } from '@/utils/date'
import MonitoringTab from './tabs/MonitoringTab.vue'
import ServerInfoTab from './tabs/ServerInfoTab.vue'
import SshTab from './tabs/SshTab.vue'
import XrayTab from './tabs/XrayTab.vue'
import AgentTab from './tabs/AgentTab.vue'

const route = useRoute()
const router = useRouter()

const serverId = computed(() => route.params.id as string)

const activeTab = ref<string>(typeof route.query.tab === 'string' ? route.query.tab : 'monitor')
watch(activeTab, (t) => {
  router.replace({ query: { ...route.query, tab: t } })
})

const agentInfo = ref<AgentListItem | null>(null)
const loading = ref(false)

const regions = ref<ResourceRegion[]>([])
const regionMap = computed<Record<string, ResourceRegion>>(() => {
  const m: Record<string, ResourceRegion> = {}
  for (const r of regions.value) m[r.code] = r
  return m
})
const regionInfo = computed(() => {
  const code = agentInfo.value?.region
  return code ? regionMap.value[code] : undefined
})

async function loadServer() {
  loading.value = true
  try {
    const all = await listAgents()
    agentInfo.value = all.find((x) => x.serverId === serverId.value) ?? null
  } catch { /* */ } finally {
    loading.value = false
  }
}

onMounted(async () => {
  try { regions.value = await listEnabledRegions() } catch { /* */ }
  await loadServer()
})
watch(serverId, loadServer)

function back() {
  router.push('/servers')
}

const ONLINE_DOT: Record<string, string> = {
  ONLINE: '#16a34a',
  WARN: '#ca8a04',
  TEMP_UNHEALTHY: '#ea580c',
  OFFLINE: '#dc2626',
  NEVER: '#9ca3af'
}
</script>

<template>
  <div class="detail-wrap space-y-3">
    <!-- ============ 顶部头条: server 名 + host + 状态摘要 ============ -->
    <NCard size="small" :content-style="{ padding: '14px 16px' }" class="header-card">
      <div class="flex items-start gap-4">
        <NButton quaternary size="small" @click="back" class="mt-1">
          <template #icon><NIcon><ArrowLeft :size="16" /></NIcon></template>
        </NButton>

        <NSpin :show="loading" size="small" class="flex-1">
          <div class="flex items-start gap-3 flex-wrap">
            <!-- 左: 国旗 + 名称 + 地区 + lifecycle 标签 -->
            <div class="flex-1 min-w-0">
              <div class="flex items-center gap-2 flex-wrap">
                <RegionFlag
                  v-if="regionInfo"
                  :code="regionInfo.countryCode"
                  :fallback="regionInfo.flagEmoji"
                  :size="26"
                  :title="regionInfo.displayName"
                  class="header-flag"
                />
                <span class="text-xl font-semibold">{{ agentInfo?.serverName || serverId }}</span>
                <NTag
                  v-if="agentInfo"
                  size="small"
                  :type="SERVER_LIFECYCLE_TAG_TYPE[agentInfo.lifecycleState] || 'default'"
                >
                  {{ SERVER_LIFECYCLE_LABELS[agentInfo.lifecycleState] || agentInfo.lifecycleState }}
                </NTag>
                <NTag v-if="regionInfo" size="small" type="info" :bordered="false">
                  {{ regionInfo.displayName }}
                </NTag>
              </div>
              <div class="mt-1 text-xs text-zinc-500 font-mono">
                serverId: {{ serverId }} · host: {{ agentInfo?.host || '—' }}
              </div>
            </div>

            <!-- 右: agent 状态点 + 心跳秒数 + 上次心跳 -->
            <div v-if="agentInfo" class="text-right text-xs">
              <div class="flex items-center justify-end gap-1.5 mb-1">
                <span
                  class="status-dot"
                  :style="`background:${ONLINE_DOT[agentInfo.onlineState] || '#9ca3af'}`"
                ></span>
                <NTag size="small" :type="AGENT_ONLINE_TAG_TYPE[agentInfo.onlineState] || 'default'">
                  {{ AGENT_ONLINE_LABELS[agentInfo.onlineState] }}
                </NTag>
                <span v-if="agentInfo.elapsedSec != null" class="text-zinc-400 font-mono ml-1">{{ agentInfo.elapsedSec }}s</span>
              </div>
              <div class="text-zinc-400">
                上次心跳: {{ formatDateTime(agentInfo.lastHeartbeatAt) || '—' }}
              </div>
              <div v-if="agentInfo.agentVersion" class="text-zinc-400 mt-0.5 font-mono">
                {{ agentInfo.agentVersion }}
              </div>
            </div>
          </div>
        </NSpin>
      </div>
    </NCard>

    <!-- ============ Tabs ============ -->
    <NCard size="small" :content-style="{ padding: '0 16px' }">
      <NTabs v-model:value="activeTab" type="line" size="medium" pane-style="padding: 14px 0">
        <NTabPane name="monitor">
          <template #tab>
            <NSpace :size="6" align="center">
              <NIcon><Activity :size="14" /></NIcon>
              <span>监控面板</span>
            </NSpace>
          </template>
          <MonitoringTab :server-id="serverId" :agent-info="agentInfo" />
        </NTabPane>

        <NTabPane name="info">
          <template #tab>
            <NSpace :size="6" align="center">
              <NIcon><Info :size="14" /></NIcon>
              <span>服务器信息</span>
            </NSpace>
          </template>
          <ServerInfoTab :server-id="serverId" :agent-info="agentInfo" @refresh="loadServer" />
        </NTabPane>

        <NTabPane name="ssh">
          <template #tab>
            <NSpace :size="6" align="center">
              <NIcon><KeyRound :size="14" /></NIcon>
              <span>SSH 凭据</span>
            </NSpace>
          </template>
          <SshTab :server-id="serverId" />
        </NTabPane>

        <NTabPane name="xray">
          <template #tab>
            <NSpace :size="6" align="center">
              <NIcon><ServerCog :size="14" /></NIcon>
              <span>Xray 节点</span>
            </NSpace>
          </template>
          <XrayTab :server-id="serverId" />
        </NTabPane>

        <NTabPane name="agent">
          <template #tab>
            <NSpace :size="6" align="center">
              <NIcon><Cpu :size="14" /></NIcon>
              <span>Agent</span>
            </NSpace>
          </template>
          <AgentTab :server-id="serverId" :agent-info="agentInfo" @refresh="loadServer" />
        </NTabPane>
      </NTabs>
    </NCard>
  </div>
</template>

<style scoped>
/* 限宽: 1280px 上限, 超大屏不撑满 — VPS 面板风格 */
.detail-wrap {
  max-width: 1280px;
  margin: 0 auto;
}
.header-card {
  background: linear-gradient(to right, rgba(99, 102, 241, 0.03), rgba(127, 127, 127, 0.0));
}
.status-dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
}
.header-flag {
  filter: drop-shadow(0 1px 2px rgba(0, 0, 0, 0.1));
}
</style>
