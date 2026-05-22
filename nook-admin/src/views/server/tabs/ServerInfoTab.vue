<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import {
  Calendar,
  CircleDollarSign,
  Edit3,
  FileText,
  Power,
  Server,
  Trash2
} from 'lucide-vue-next'
import {
  NAlert,
  NButton,
  NCard,
  NDescriptions,
  NDescriptionsItem,
  NIcon,
  NPopconfirm,
  NProgress,
  NSelect,
  NSpin,
  NTag,
  useDialog,
  useMessage
} from 'naive-ui'
import {
  deleteServer,
  getServerDetail,
  SERVER_LIFECYCLE_LABELS,
  SERVER_LIFECYCLE_TAG_TYPE,
  transitionServerLifecycle,
  type ResourceServer
} from '@/api/resource/server'
import { formatDateTime } from '@/utils/date'
import ServerFormDialog from '@/views/resource/ServerFormDialog.vue'
import type { AgentListItem } from '@/api/agent/agent'

const props = defineProps<{
  serverId: string
  agentInfo: AgentListItem | null
}>()
const emit = defineEmits<{ refresh: [] }>()

const message = useMessage()
const dialog = useDialog()

const detail = ref<ResourceServer | null>(null)
const loading = ref(false)

async function load() {
  if (!props.serverId) return
  loading.value = true
  try {
    detail.value = await getServerDetail(props.serverId)
  } catch { /* */ } finally {
    loading.value = false
  }
}

onMounted(load)
watch(() => props.serverId, load)

const lifecycleOptions = [
  { label: '装机中', value: 'INSTALLING' },
  { label: '待上线', value: 'READY' },
  { label: '运行中', value: 'LIVE' },
  { label: '已退役', value: 'RETIRED' }
]
const targetLifecycle = ref<string | null>(null)
async function doTransition() {
  if (!targetLifecycle.value || !detail.value) return
  dialog.warning({
    title: '确认切换 lifecycle',
    content: `${detail.value.lifecycleState} → ${targetLifecycle.value} ?`,
    positiveText: '切换', negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await transitionServerLifecycle(props.serverId, targetLifecycle.value!)
        message.success('已切换')
        targetLifecycle.value = null
        await load()
        emit('refresh')
      } catch { /* */ }
    }
  })
}

const editOpen = ref(false)
async function onDelete() {
  try {
    await deleteServer(props.serverId)
    message.success('已删除')
    emit('refresh')
    location.href = '/servers'
  } catch { /* */ }
}

// ===== 到期天数 =====
const expiresDaysLeft = computed(() => {
  if (!detail.value?.expiresAt) return null
  const d = new Date(detail.value.expiresAt).getTime()
  const now = Date.now()
  return Math.floor((d - now) / (24 * 3600 * 1000))
})
const expiresTagType = computed(() => {
  const d = expiresDaysLeft.value
  if (d == null) return 'default'
  if (d < 0) return 'error'
  if (d < 7) return 'warning'
  return 'success'
})
</script>

<template>
  <NSpin :show="loading">
    <div v-if="detail" class="space-y-3">

      <!-- 操作栏 -->
      <div class="action-bar">
        <NButton size="small" type="primary" @click="editOpen = true">
          <template #icon><NIcon><Edit3 :size="14" /></NIcon></template>
          编辑服务器信息
        </NButton>
        <NSelect
          v-model:value="targetLifecycle"
          :options="lifecycleOptions"
          size="small"
          placeholder="切换 lifecycle"
          class="w-44"
        />
        <NButton size="small" :disabled="!targetLifecycle" @click="doTransition">
          <template #icon><NIcon><Power :size="14" /></NIcon></template>
          切换
        </NButton>
        <div class="flex-1"></div>
        <NPopconfirm @positive-click="onDelete">
          <template #trigger>
            <NButton size="small" type="error" quaternary>
              <template #icon><NIcon><Trash2 :size="14" /></NIcon></template>
              删除 server
            </NButton>
          </template>
          软删主记录, 保留 xray_node / agent_task / op_log 历史, 不可撤销.
        </NPopconfirm>
      </div>

      <!-- === Section 1: 基础信息 === -->
      <NCard size="small" :bordered="false" class="info-section">
        <template #header>
          <div class="section-header">
            <NIcon class="section-icon"><Server :size="14" /></NIcon>
            <span>基础信息</span>
          </div>
        </template>
        <NDescriptions bordered size="small" label-placement="left" :column="2" label-style="width: 6rem">
          <NDescriptionsItem label="名称">{{ detail.name }}</NDescriptionsItem>
          <NDescriptionsItem label="生命周期">
            <NTag size="small" :type="SERVER_LIFECYCLE_TAG_TYPE[detail.lifecycleState] || 'default'">
              {{ SERVER_LIFECYCLE_LABELS[detail.lifecycleState] || detail.lifecycleState }}
            </NTag>
          </NDescriptionsItem>
          <NDescriptionsItem label="Host">
            <code class="kbd">{{ detail.host }}</code>
          </NDescriptionsItem>
          <NDescriptionsItem label="域名">
            <code v-if="detail.domain" class="kbd">{{ detail.domain }}</code>
            <span v-else class="muted">未配置</span>
          </NDescriptionsItem>
          <NDescriptionsItem label="区域">
            <NTag v-if="detail.region" size="small">{{ detail.region }}</NTag>
            <span v-else class="muted">—</span>
          </NDescriptionsItem>
          <NDescriptionsItem label="服务器 ID">
            <code class="kbd text-xs">{{ detail.id }}</code>
          </NDescriptionsItem>
        </NDescriptions>
      </NCard>

      <!-- === Section 2: 厂商 / 账单 / 容量 (合并) === -->
      <NCard size="small" :bordered="false" class="info-section">
        <template #header>
          <div class="section-header">
            <NIcon class="section-icon"><CircleDollarSign :size="14" /></NIcon>
            <span>厂商 / 账单 / 容量</span>
          </div>
        </template>
        <NDescriptions bordered size="small" label-placement="left" :column="2" label-style="width: 6rem">
          <NDescriptionsItem label="云厂商">
            <NTag v-if="detail.idcProvider" size="small" type="info">{{ detail.idcProvider }}</NTag>
            <span v-else class="muted">—</span>
          </NDescriptionsItem>
          <NDescriptionsItem label="承诺带宽">
            <span v-if="detail.bandwidthMbps != null"><b class="num">{{ detail.bandwidthMbps }}</b> <span class="unit">Mbps</span></span>
            <span v-else class="muted">—</span>
          </NDescriptionsItem>
          <NDescriptionsItem label="最大客户数">
            <span v-if="detail.maxConcurrentClients != null"><b class="num">{{ detail.maxConcurrentClients }}</b> <span class="unit">个</span></span>
            <span v-else class="muted">—</span>
          </NDescriptionsItem>
          <NDescriptionsItem label="IP 总数">
            <span v-if="detail.totalIpCount != null"><b class="num">{{ detail.totalIpCount }}</b> <span class="unit">个</span></span>
            <span v-else class="muted">—</span>
          </NDescriptionsItem>
          <NDescriptionsItem label="月成本">
            <span v-if="detail.costMonthlyUsd != null" class="num">${{ detail.costMonthlyUsd }} <span class="unit">/ 月</span></span>
            <span v-else class="muted">—</span>
          </NDescriptionsItem>
          <NDescriptionsItem label="账单日">
            <span v-if="detail.billingCycleDay">每月 <b class="num">{{ detail.billingCycleDay }}</b> 日</span>
            <span v-else class="muted">—</span>
          </NDescriptionsItem>
          <NDescriptionsItem label="到期日">
            <div v-if="detail.expiresAt" class="flex items-center gap-2">
              <span class="num font-mono">{{ detail.expiresAt }}</span>
              <NTag size="tiny" :type="expiresTagType">
                <span v-if="expiresDaysLeft! < 0">已过期 {{ -expiresDaysLeft! }} 天</span>
                <span v-else-if="expiresDaysLeft! < 7">剩 {{ expiresDaysLeft }} 天</span>
                <span v-else>剩 {{ expiresDaysLeft }} 天</span>
              </NTag>
            </div>
            <span v-else class="muted">—</span>
          </NDescriptionsItem>
        </NDescriptions>

        <!-- 月度流量进度条 (占位; backend 暂无 API, P 后续接入) -->
        <div class="traffic-block">
          <div class="flex items-center justify-between mb-1">
            <span class="text-xs text-zinc-500">本月流量 (待接入)</span>
            <span class="text-xs text-zinc-400 font-mono">— GB / — GB</span>
          </div>
          <NProgress :percentage="0" :height="6" :show-indicator="false" status="default" />
        </div>
      </NCard>

      <!-- === Section 3: DNS / CDN === -->
      <NCard
        v-if="detail.cfZoneId || detail.cfRecordId || detail.domain"
        size="small" :bordered="false" class="info-section"
      >
        <template #header>
          <div class="section-header">
            <span>DNS / Cloudflare</span>
          </div>
        </template>
        <NDescriptions bordered size="small" label-placement="left" :column="1" label-style="width: 8rem">
          <NDescriptionsItem label="CF Zone ID">
            <code v-if="detail.cfZoneId" class="kbd text-xs">{{ detail.cfZoneId }}</code>
            <span v-else class="muted">未配置</span>
          </NDescriptionsItem>
          <NDescriptionsItem label="CF Record ID">
            <code v-if="detail.cfRecordId" class="kbd text-xs">{{ detail.cfRecordId }}</code>
            <span v-else class="muted">未配置</span>
          </NDescriptionsItem>
        </NDescriptions>
      </NCard>

      <!-- === Section 4: 时间 + 备注 === -->
      <NCard size="small" :bordered="false" class="info-section">
        <template #header>
          <div class="section-header">
            <NIcon class="section-icon"><Calendar :size="14" /></NIcon>
            <span>时间 / 备注</span>
          </div>
        </template>
        <NDescriptions bordered size="small" label-placement="left" :column="2" label-style="width: 6rem">
          <NDescriptionsItem label="创建时间">{{ formatDateTime(detail.createdAt) }}</NDescriptionsItem>
          <NDescriptionsItem label="更新时间">{{ formatDateTime(detail.updatedAt) }}</NDescriptionsItem>
        </NDescriptions>
        <div v-if="detail.remark" class="remark-block">
          <div class="text-xs text-zinc-500 mb-1 flex items-center gap-1">
            <NIcon><FileText :size="12" /></NIcon> 备注
          </div>
          <div class="text-sm whitespace-pre-line">{{ detail.remark }}</div>
        </div>
      </NCard>

    </div>

    <ServerFormDialog v-model="editOpen" mode="edit" :server="detail" @saved="() => { load(); emit('refresh') }" />
  </NSpin>
</template>

<style scoped>
/* 字体 / 数值 / 段头 / info-section 内边距统一走 main.scss 全局 tokens */

.traffic-block {
  margin-top: 10px;
  padding: 8px 10px;
  background: rgba(127, 127, 127, 0.03);
  border-radius: 4px;
}

.remark-block {
  margin-top: 8px;
  padding: 8px 10px;
  background: rgba(127, 127, 127, 0.04);
  border-radius: 4px;
  border-left: 3px solid #a1a1aa;
}
</style>
