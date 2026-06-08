<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Activity,
  ArrowLeft,
  Copy,
  Cpu,
  Info,
  KeyRound,
  Server as ServerIcon,
  ServerCog
} from 'lucide-vue-next'
import {
  NButton,
  NCard,
  NEmpty,
  NIcon,
  NSpace,
  NSpin,
  NTabs,
  NTabPane,
  NTag,
  useMessage
} from 'naive-ui'

// API + types
import {
  AGENT_ONLINE_LABELS,
  AGENT_ONLINE_TAG_TYPE
} from '@/api/agent/agent'
import {
  getServerCredential,
  getServerDetailWithRuntime,
  SERVER_LIFECYCLE_LABELS,
  SERVER_LIFECYCLE_TAG_TYPE,
  type ServerCredential,
  type ServerFrontlineListItem
} from '@/api/resource/server'
import {
  SERVER_LANDING_LIFECYCLE_LABELS,
  SERVER_LANDING_LIFECYCLE_TAG_TYPE,
  SERVER_LANDING_STATUS_LABELS,
  getServerLandingDetail,
  getServerLandingInstall,
  type ServerLanding,
  type ServerLandingInstall
} from '@/api/resource/server-landing'
import { getServerSystemdStatus, type SystemdStatus } from '@/api/resource/server-ops'

/** dante 的固定 systemd unit 名; 公共 /get-systemd-status 接口靠 unit 参数区分. */
const DANTE_UNIT = 'danted'
import { IP_TYPE_CODE_LABELS } from '@/api/system/ip-type'
import { useRegionStore } from '@/stores/region'
import { useIpTypeStore } from '@/stores/ipType'
import { storeToRefs } from 'pinia'
import { formatDateTime } from '@/utils/date'
import RegionFlag from '@/components/RegionFlag.vue'

// Frontline tabs (server_type=frontline 用)
import MonitoringTab from './tabs/MonitoringTab.vue'
import ServerInfoTab from './tabs/ServerInfoTab.vue'
import SshTab from './tabs/SshTab.vue'
import XrayTab from './tabs/XrayTab.vue'
import AgentTab from './tabs/AgentTab.vue'

// Landing tabs (server_type=landing 用)
import LandingMonitoringTab from '@/views/resource/tabs/LandingMonitoringTab.vue'
import LandingInfoTab from '@/views/resource/tabs/LandingInfoTab.vue'
import LandingSocks5Tab from '@/views/resource/tabs/LandingSocks5Tab.vue'
import { relativeTime } from '@/views/resource/tabs/landingHelpers'

// Landing sub-dialogs (landing 详情独有)
import ServerLandingDeployDialog from '@/views/resource/ServerLandingDeployDialog.vue'
import ServerLandingTestDialog from '@/views/resource/ServerLandingTestDialog.vue'
import ServerLandingLogDialog from '@/views/resource/ServerLandingLogDialog.vue'
import ServerLandingCoreEditDialog from '@/views/resource/dialogs/ServerLandingCoreEditDialog.vue'
import ServerLandingBillingEditDialog from '@/views/resource/dialogs/ServerLandingBillingEditDialog.vue'
import ServerLandingQuotaEditDialog from '@/views/resource/dialogs/ServerLandingQuotaEditDialog.vue'
import ServerLandingSocks5EditDialog from '@/views/resource/dialogs/ServerLandingSocks5EditDialog.vue'

/**
 * 服务器详情 — 统一详情页 (frontline + landing 共用).
 *
 * <p>由路由 meta.serverType 决定渲染模式. Tab 套餐:
 * - 共用: 监控 / 信息 / SSH / Agent
 * - frontline 独有: Xray
 * - landing 独有: SOCKS5 服务
 *
 * 数据加载: 共用 getServerDetailWithRuntime 拿 runtime; landing 额外走 getServerLandingDetail + Install.
 * Landing sub-dialogs (core/credential/billing/capacity/socks5/deploy/test/log/agent) 由父组件挂载.
 */
const route = useRoute()
const router = useRouter()
const message = useMessage()

const serverId = computed(() => route.params.id as string)
/** 路由 meta 提供; 缺省 frontline (老路由兼容). */
const serverType = computed<'frontline' | 'landing'>(
  () => ((route.meta?.serverType as 'frontline' | 'landing') ?? 'frontline')
)
const isFrontline = computed(() => serverType.value === 'frontline')
const isLanding = computed(() => serverType.value === 'landing')

const activeTab = ref<string>(typeof route.query.tab === 'string' ? route.query.tab : 'monitor')
watch(activeTab, (t) => router.replace({ query: { ...route.query, tab: t } }))
watch(serverType, () => {
  // 路由切换 (跨类型) 时, tab 默认重置回 monitor 避免无效 tab
  if (!isValidTab(activeTab.value)) activeTab.value = 'monitor'
})

function isValidTab(t: string): boolean {
  const shared = ['monitor', 'info', 'ssh', 'agent']
  if (shared.includes(t)) return true
  if (isFrontline.value) return t === 'xray'
  if (isLanding.value) return t === 'socks5'
  return false
}

// ===== Frontline 数据 =====
const frontlineInfo = ref<ServerFrontlineListItem | null>(null)
// ===== Landing 数据 =====
const landingInfo = ref<ServerLanding | null>(null)
const installInfo = ref<ServerLandingInstall | null>(null)
const landingCredential = ref<ServerCredential | null>(null)
const error = ref<string>('')
const loading = ref(false)

const regionStore = useRegionStore()
const ipTypeStore = useIpTypeStore()
const { map: regionMap } = storeToRefs(regionStore)
const { list: ipTypes } = storeToRefs(ipTypeStore)

// ===== Landing SOCKS5 status (跨 3 个 landing tab 共用) =====
const statusLoading = ref(false)
const statusData = ref<SystemdStatus | null>(null)
const statusError = ref<string>('')

// ===== Landing sub-dialog mount state =====
const deployOpen = ref(false)
const testOpen = ref(false)
const logOpen = ref(false)
const coreEditOpen = ref(false)
const billingEditOpen = ref(false)
const capacityEditOpen = ref(false)
const socks5EditOpen = ref(false)

// ===== computed =====

/** 区域 (两边共用, frontline 用 frontlineInfo.region, landing 用 landingInfo.region) */
const regionCode = computed(() => isFrontline.value ? frontlineInfo.value?.region : landingInfo.value?.region)
const regionInfo = computed(() => regionCode.value ? regionMap.value[regionCode.value] : undefined)

function ipTypeName(ipTypeId?: string): string {
  if (!ipTypeId) return '-'
  const t = ipTypes.value.find((x) => x.id === ipTypeId)
  if (!t) return ipTypeId
  return IP_TYPE_CODE_LABELS[t.code] || t.name || t.code
}

// === Landing-only derived states ===
function statusTagType(status?: string) {
  switch (status) {
    case 'OCCUPIED': return 'warning'
    case 'AVAILABLE': return 'success'
    default: return 'default'
  }
}

const canTest = computed(() =>
  !!landingInfo.value?.ipAddress && !!landingInfo.value?.socks5Port
  && !!landingInfo.value?.socks5Username && !!landingInfo.value?.socks5Password)
// SSH 凭据走独立 credential 子表 (ServerLandingRespVO 不再带 sshPassword)
const canManage = computed(() =>
  landingInfo.value?.provisionMode === 1 && !!landingCredential.value?.sshPassword)
const isSelfDeploy = computed(() => landingInfo.value?.provisionMode === 1)
const isLive = computed(() => landingInfo.value?.lifecycleState === 'LIVE')
const isInstalling = computed(() =>
  landingInfo.value?.lifecycleState === 'INSTALLING' || landingInfo.value?.lifecycleState === 'READY')
const isAgentOnline = computed(() => {
  if (!landingInfo.value?.lastHeartbeatAt) return false
  return Date.now() - new Date(landingInfo.value.lastHeartbeatAt).getTime() < 5 * 60 * 1000
})

const agentHealthLabel = computed<{ text: string; type: 'success' | 'error' | 'default' }>(() => {
  if (!landingInfo.value?.lastHeartbeatAt) return { text: '未上线', type: 'default' }
  return isAgentOnline.value
    ? { text: '在线', type: 'success' }
    : { text: '离线', type: 'error' }
})

// ===== load (按 serverType 分支) =====
async function loadServer() {
  loading.value = true
  error.value = ''
  if (isFrontline.value) {
    try {
      frontlineInfo.value = await getServerDetailWithRuntime(serverId.value)
    } catch (e) {
      error.value = (e as Error).message || '加载失败'
    } finally {
      loading.value = false
    }
    return
  }
  // landing: 主表 + landing 子表 + install + runtime 聚合 (供 Agent tab 复用 frontline AgentTab) + credential (供 canManage 判断)
  // 与 frontline 分支对称: 加载期间保留旧值, await 完整体替换; 不预清空 — 清 landingInfo 会让落地机详情整块 (v-else-if) 卸载, 丢子组件状态 (如装机中的 Agent 部署弹窗)
  try {
    const [d, inst, runtime, cred] = await Promise.all([
      getServerLandingDetail(serverId.value),
      getServerLandingInstall(serverId.value).catch(() => null),
      getServerDetailWithRuntime(serverId.value).catch(() => null),
      getServerCredential(serverId.value).catch(() => null)
    ])
    landingInfo.value = d
    installInfo.value = inst
    frontlineInfo.value = runtime
    landingCredential.value = cred
    statusError.value = ''
    // monitor 是默认 tab, watch(activeTab) 不在首次挂载触发; 凭据加载完按需补一次 SOCKS5/dante 探测
    if (activeTab.value === 'monitor') void loadStatus()
  } catch (e) {
    error.value = (e as Error).message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function loadStatus() {
  if (!isLanding.value || !canManage.value) {
    if (!canManage.value) statusError.value = '需要 SSH 凭据齐才能探测'
    return
  }
  statusLoading.value = true
  statusError.value = ''
  try {
    statusData.value = await getServerSystemdStatus(serverId.value, DANTE_UNIT)
  } catch (e) {
    statusError.value = (e as Error).message || '拉取状态失败'
  } finally {
    statusLoading.value = false
  }
}

async function copyToClipboard(value: string | undefined, label: string) {
  if (!value) { message.warning(`${label} 为空`); return }
  try {
    await navigator.clipboard.writeText(value)
    message.success(`已复制 ${label}`)
  } catch { message.warning('复制失败') }
}

onMounted(async () => {
  // 字典走 store 全局去重; ipType 仅 landing 用, 但提前加载也无害
  const tasks: Promise<unknown>[] = [regionStore.ensureLoaded(), loadServer()]
  if (isLanding.value) tasks.push(ipTypeStore.ensureLoaded())
  await Promise.all(tasks)
})
watch(serverId, loadServer)

// landing 监控 tab 进入时自动拉一次远端 systemd 状态; SOCKS5 tab 不再自动拉, 需手动点 "刷新远端"
watch(activeTab, (tab) => {
  if (isLanding.value && tab === 'monitor' && !statusData.value
      && !statusLoading.value && !statusError.value && canManage.value) {
    void loadStatus()
  }
})

function back() {
  if (isLanding.value) router.push({ name: 'resource-server-landing' })
  else router.push('/servers')
}

function onSubDialogSaved() {
  void loadServer()
}

function onDeployInstalled() {
  void loadServer()
}

// === Frontline header 心跳点色 ===
const ONLINE_DOT: Record<string, string> = {
  ONLINE: '#16a34a',
  WARN: '#ca8a04',
  TEMP_UNHEALTHY: '#ea580c',
  OFFLINE: '#dc2626',
  NEVER: '#9ca3af'
}

// === Landing header 心跳点色 ===
const HEARTBEAT_DOT: Record<string, string> = {
  online: '#16a34a',
  warn: '#ca8a04',
  offline: '#dc2626',
  never: '#9ca3af'
}
const landingHeartbeatDotColor = computed(() => {
  if (!landingInfo.value?.agentToken) return HEARTBEAT_DOT.never
  if (!landingInfo.value?.lastHeartbeatAt) return HEARTBEAT_DOT.warn
  return isAgentOnline.value ? HEARTBEAT_DOT.online : HEARTBEAT_DOT.offline
})
</script>

<template>
  <div class="detail-wrap space-y-3">
    <!-- ============ 顶部头条 ============ -->
    <NCard size="small" :content-style="{ padding: '14px 16px' }" class="header-card">
      <div class="flex items-start gap-4">
        <NButton quaternary size="small" @click="back" class="mt-1">
          <template #icon><NIcon><ArrowLeft :size="16" /></NIcon></template>
        </NButton>

        <NSpin :show="loading" size="small" class="flex-1">
          <NEmpty v-if="!loading && error && !frontlineInfo && !landingInfo" :description="error" />

          <!-- ===== Frontline header ===== -->
          <div v-else-if="isFrontline" class="flex items-start gap-3 flex-wrap">
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
                <span class="text-xl font-semibold">{{ frontlineInfo?.name || serverId }}</span>
                <NTag
                  v-if="frontlineInfo"
                  size="small"
                  :type="SERVER_LIFECYCLE_TAG_TYPE[frontlineInfo.lifecycleState] || 'default'"
                >
                  {{ SERVER_LIFECYCLE_LABELS[frontlineInfo.lifecycleState] || frontlineInfo.lifecycleState }}
                </NTag>
                <NTag v-if="regionInfo" size="small" type="info" :bordered="false">
                  {{ regionInfo.displayName }}
                </NTag>
              </div>
              <div class="mt-1 text-xs text-zinc-500 font-mono">
                serverId: {{ serverId }} · host: {{ frontlineInfo?.host || '—' }}
              </div>
            </div>
            <div v-if="frontlineInfo" class="text-right text-xs">
              <div class="flex items-center justify-end gap-1.5 mb-1">
                <span class="status-dot" :style="`background:${ONLINE_DOT[frontlineInfo.onlineState] || '#9ca3af'}`"></span>
                <NTag size="small" :type="AGENT_ONLINE_TAG_TYPE[frontlineInfo.onlineState] || 'default'">
                  {{ AGENT_ONLINE_LABELS[frontlineInfo.onlineState] }}
                </NTag>
                <span v-if="frontlineInfo.elapsedSec != null" class="text-zinc-400 font-mono ml-1">{{ frontlineInfo.elapsedSec }}s</span>
              </div>
              <div class="text-zinc-400">
                上次心跳: {{ formatDateTime(frontlineInfo.lastHeartbeatAt) || '—' }}
              </div>
              <div v-if="frontlineInfo.agentVersion" class="text-zinc-400 mt-0.5 font-mono">
                {{ frontlineInfo.agentVersion }}
              </div>
            </div>
          </div>

          <!-- ===== Landing header ===== -->
          <div v-else-if="isLanding && landingInfo" class="flex items-start gap-3 flex-wrap">
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
                <span class="text-xl font-semibold">{{ landingInfo.name || landingInfo.ipAddress }}</span>
                <NTag size="small" :type="SERVER_LANDING_LIFECYCLE_TAG_TYPE[landingInfo.lifecycleState] || 'default'">
                  {{ SERVER_LANDING_LIFECYCLE_LABELS[landingInfo.lifecycleState] || landingInfo.lifecycleState }}
                </NTag>
                <NTag size="small" :type="statusTagType(landingInfo.status)">
                  {{ SERVER_LANDING_STATUS_LABELS[landingInfo.status] || landingInfo.status }}
                </NTag>
                <NTag size="small" :type="landingInfo.provisionMode === 1 ? 'success' : 'warning'" :bordered="false">
                  {{ landingInfo.provisionMode === 1 ? '自部署' : '第三方' }}
                </NTag>
                <NTag v-if="regionInfo" size="small" type="info" :bordered="false">
                  {{ regionInfo.displayName }}
                </NTag>
              </div>
              <div class="mt-1 text-xs text-zinc-500 font-mono flex items-center gap-1 flex-wrap">
                <span>id: {{ landingInfo.id }}</span>
                <span>·</span>
                <span>IP: {{ landingInfo.ipAddress }}</span>
                <NButton quaternary size="tiny" circle @click="copyToClipboard(landingInfo.ipAddress, 'IP 地址')">
                  <template #icon><NIcon><Copy /></NIcon></template>
                </NButton>
                <span>·</span>
                <span>IP 类型: {{ ipTypeName(landingInfo.ipTypeId) }}</span>
                <template v-if="landingInfo.remark">
                  <span>·</span>
                  <span>备注: {{ landingInfo.remark }}</span>
                </template>
              </div>
            </div>
            <div class="text-right text-xs">
              <div class="flex items-center justify-end gap-1.5 mb-1">
                <span class="status-dot" :style="`background:${landingHeartbeatDotColor}`"></span>
                <NTag size="small" :type="agentHealthLabel.type">
                  {{ agentHealthLabel.text }}
                </NTag>
                <span v-if="landingInfo.lastHeartbeatAt" class="text-zinc-400 font-mono ml-1">
                  {{ relativeTime(landingInfo.lastHeartbeatAt) }}
                </span>
              </div>
              <div v-if="landingInfo.occupiedByMemberId" class="text-zinc-500">
                占用: <span class="font-mono">{{ landingInfo.occupiedByMemberId }}</span>
              </div>
              <div class="text-zinc-400 mt-0.5">
                创建于 {{ formatDateTime(landingInfo.createdAt) }}
              </div>
            </div>
          </div>
        </NSpin>
      </div>
    </NCard>

    <!-- ============ Frontline Tabs ============ -->
    <NCard v-if="isFrontline" size="small" :content-style="{ padding: '0 16px' }">
      <NTabs v-model:value="activeTab" type="line" size="medium" pane-style="padding: 14px 0">
        <NTabPane name="monitor">
          <template #tab>
            <NSpace :size="6" align="center">
              <NIcon><Activity :size="14" /></NIcon>
              <span>监控面板</span>
            </NSpace>
          </template>
          <MonitoringTab :server-id="serverId" :agent-info="frontlineInfo" />
        </NTabPane>

        <NTabPane name="info">
          <template #tab>
            <NSpace :size="6" align="center">
              <NIcon><Info :size="14" /></NIcon>
              <span>{{ isLanding ? '落地机信息' : '线路机信息' }}</span>
            </NSpace>
          </template>
          <ServerInfoTab :server-id="serverId" @refresh="loadServer" />
        </NTabPane>

        <NTabPane name="ssh">
          <template #tab>
            <NSpace :size="6" align="center">
              <NIcon><KeyRound :size="14" /></NIcon>
              <span>SSH 凭据</span>
            </NSpace>
          </template>
          <SshTab :server-id="serverId" :host="frontlineInfo?.host" :lifecycle-state="frontlineInfo?.lifecycleState" />
        </NTabPane>

        <NTabPane name="xray">
          <template #tab>
            <NSpace :size="6" align="center">
              <NIcon><ServerCog :size="14" /></NIcon>
              <span>Xray 节点</span>
            </NSpace>
          </template>
          <XrayTab :server-id="serverId" :agent-info="frontlineInfo" />
        </NTabPane>

        <NTabPane name="agent">
          <template #tab>
            <NSpace :size="6" align="center">
              <NIcon><Cpu :size="14" /></NIcon>
              <span>Agent</span>
            </NSpace>
          </template>
          <AgentTab :server-id="serverId" :role="isLanding ? 'landing' : 'frontline'" :agent-info="frontlineInfo" @refresh="loadServer" />
        </NTabPane>
      </NTabs>
    </NCard>

    <!-- ============ Landing Tabs ============ -->
    <NCard v-else-if="isLanding && landingInfo" size="small" :content-style="{ padding: '0 16px' }">
      <NTabs v-model:value="activeTab" type="line" size="medium" pane-style="padding: 14px 0">
        <NTabPane name="monitor">
          <template #tab>
            <NSpace :size="6" align="center">
              <NIcon><Activity :size="14" /></NIcon>
              <span>监控面板</span>
            </NSpace>
          </template>
          <LandingMonitoringTab
            :detail="landingInfo"
            :can-manage="canManage"
            :is-agent-online="isAgentOnline"
            :status-data="statusData"
          />
        </NTabPane>

        <NTabPane name="info">
          <template #tab>
            <NSpace :size="6" align="center">
              <NIcon><Info :size="14" /></NIcon>
              <span>落地机信息</span>
            </NSpace>
          </template>
          <LandingInfoTab
            :detail="landingInfo"
            @edit-core="coreEditOpen = true"
            @edit-capacity="capacityEditOpen = true"
            @edit-billing="billingEditOpen = true"
            @refresh="loadServer"
          />
        </NTabPane>

        <NTabPane name="ssh">
          <template #tab>
            <NSpace :size="6" align="center">
              <NIcon><KeyRound :size="14" /></NIcon>
              <span>SSH 凭据</span>
            </NSpace>
          </template>
          <SshTab
            :server-id="serverId"
            :host="landingInfo?.ipAddress"
            :lifecycle-state="landingInfo?.lifecycleState"
          />
        </NTabPane>

        <NTabPane name="socks5">
          <template #tab>
            <NSpace :size="6" align="center">
              <NIcon><ServerIcon :size="14" /></NIcon>
              <span>SOCKS5 服务</span>
            </NSpace>
          </template>
          <LandingSocks5Tab
            :detail="landingInfo"
            :install-info="installInfo"
            :can-manage="canManage"
            :can-test="canTest"
            :is-self-deploy="isSelfDeploy"
            :is-installing="isInstalling"
            :is-live="isLive"
            :status-data="statusData"
            :status-loading="statusLoading"
            :status-error="statusError"
            @edit-socks5="socks5EditOpen = true"
            @open-deploy="deployOpen = true"
            @open-test="testOpen = true"
            @open-log="logOpen = true"
            @load-status="loadStatus"
          />
        </NTabPane>

        <NTabPane name="agent">
          <template #tab>
            <NSpace :size="6" align="center">
              <NIcon><Cpu :size="14" /></NIcon>
              <span>Agent</span>
            </NSpace>
          </template>
          <AgentTab :server-id="serverId" :role="isLanding ? 'landing' : 'frontline'" :agent-info="frontlineInfo" @refresh="loadServer" />
        </NTabPane>
      </NTabs>
    </NCard>

    <!-- ============ Landing sub-dialogs (仅 landing 模式挂载) ============ -->
    <template v-if="isLanding">
      <ServerLandingDeployDialog v-model="deployOpen" :server-id="serverId" @installed="onDeployInstalled" />
      <ServerLandingTestDialog v-model="testOpen" :ip="landingInfo" />
      <ServerLandingLogDialog v-model="logOpen" :ip="landingInfo" />
      <ServerLandingCoreEditDialog v-if="landingInfo" v-model="coreEditOpen" :server-landing="landingInfo" @saved="onSubDialogSaved" />
      <ServerLandingBillingEditDialog v-if="landingInfo" v-model="billingEditOpen" :server-id="landingInfo.id" @saved="onSubDialogSaved" />
      <ServerLandingQuotaEditDialog v-if="landingInfo" v-model="capacityEditOpen" :server-id="landingInfo.id" @saved="onSubDialogSaved" />
      <ServerLandingSocks5EditDialog v-if="landingInfo" v-model="socks5EditOpen" :server-id="landingInfo.id" @saved="onSubDialogSaved" />
    </template>
  </div>
</template>

<style scoped>
/* 限宽: 1280px 上限, 超大屏不撑满 */
.detail-wrap {
  max-width: 1280px;
  margin: 0 auto;
}
.header-card {
  background: linear-gradient(to right, rgba(99, 102, 241, 0.03), rgba(127, 127, 127, 0));
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
