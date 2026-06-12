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
  NButton,
  NCard,
  NDescriptions,
  NDescriptionsItem,
  NIcon,
  NPopconfirm,
  NSelect,
  NSpin,
  NTag,
  useDialog,
  useMessage
} from 'naive-ui'
import {
  deleteServer,
  getServerBilling,
  getServerQuota,
  getServerDetail,
  SERVER_LIFECYCLE_LABELS,
  SERVER_LIFECYCLE_TAG_TYPE,
  transitionFrontlineLifecycle,
  type ResourceServer,
  type ServerBilling,
  type ServerQuota
} from '@/api/resource/server'
import { formatDateTime } from '@/utils/date'
import ServerCoreEditDialog from '@/views/server/dialogs/ServerCoreEditDialog.vue'
import ServerBillingEditDialog from '@/views/server/dialogs/ServerBillingEditDialog.vue'
import ServerQuotaEditDialog from '@/views/server/dialogs/ServerQuotaEditDialog.vue'

const props = defineProps<{
  serverId: string
}>()
const emit = defineEmits<{ refresh: [] }>()

const message = useMessage()
const dialog = useDialog()

const detail = ref<ResourceServer | null>(null)
const billing = ref<ServerBilling | null>(null)
const capacity = ref<ServerQuota | null>(null)
const loading = ref(false)

async function load() {
  if (!props.serverId) return
  loading.value = true
  try {
    const [d, b, c] = await Promise.all([
      getServerDetail(props.serverId),
      getServerBilling(props.serverId),
      getServerQuota(props.serverId)
    ])
    detail.value = d
    billing.value = b
    capacity.value = c
  } catch { /* */ } finally {
    loading.value = false
  }
}

onMounted(load)
watch(() => props.serverId, load)

// ===== lifecycle 切换 =====
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
    title: '确认切换状态',
    content: `${SERVER_LIFECYCLE_LABELS[detail.value.lifecycleState] || detail.value.lifecycleState} → ${SERVER_LIFECYCLE_LABELS[targetLifecycle.value] || targetLifecycle.value} ?`,
    positiveText: '切换', negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await transitionFrontlineLifecycle(props.serverId, targetLifecycle.value!)
        message.success('已切换')
        targetLifecycle.value = null
        await load()
        emit('refresh')
      } catch { /* */ }
    }
  })
}

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
  if (!billing.value?.expiresAt) return null
  const d = new Date(billing.value.expiresAt).getTime()
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

// ===== 编辑 dialogs =====
const coreEditOpen = ref(false)
const billingEditOpen = ref(false)
const quotaEditOpen = ref(false)

function afterEdit() { load(); emit('refresh') }
</script>

<template>
  <NSpin :show="loading">
    <div v-if="detail" class="space-y-3">

      <!-- 状态管理: 切换生命周期 + 删除 -->
      <div class="lifecycle-bar">
        <NIcon class="lifecycle-bar-icon"><Power :size="16" /></NIcon>
        <span class="lifecycle-bar-title">状态管理</span>
        <span class="flex-1"></span>
        <NSelect
          v-model:value="targetLifecycle"
          :options="lifecycleOptions"
          size="small"
          placeholder="切换状态"
          class="w-32"
        />
        <NButton type="primary" size="small" :disabled="!targetLifecycle" @click="doTransition">
          <template #icon><NIcon><Power :size="14" /></NIcon></template>
          切换
        </NButton>
        <NPopconfirm @positive-click="onDelete">
          <template #trigger>
            <NButton size="small" type="error" quaternary>
              <template #icon><NIcon><Trash2 :size="14" /></NIcon></template>
              删除线路机
            </NButton>
          </template>
          级联硬删主记录 + 全部子表 (凭据 / 账面 / 配额 / 流量 / xray_install / xray_inbound 等), 不可撤销; 仍被生效凭证绑定的机器会被拒绝删除.
        </NPopconfirm>
      </div>

      <!-- === Section 1: 基础信息 === -->
      <NCard size="small" :bordered="false" class="info-section">
        <template #header>
          <div class="section-header">
            <NIcon class="section-icon"><Server :size="14" /></NIcon>
            <span>基础信息</span>
            <span class="flex-1"></span>
            <NButton size="tiny" quaternary type="primary" @click="coreEditOpen = true">
              <template #icon><NIcon><Edit3 :size="12" /></NIcon></template>
              编辑
            </NButton>
          </div>
        </template>
        <NDescriptions bordered size="small" label-placement="left" :column="2" label-style="width: 6rem">
          <NDescriptionsItem label="名称">{{ detail.name }}</NDescriptionsItem>
          <NDescriptionsItem label="生命周期">
            <NTag size="small" :type="SERVER_LIFECYCLE_TAG_TYPE[detail.lifecycleState] || 'default'">
              {{ SERVER_LIFECYCLE_LABELS[detail.lifecycleState] || detail.lifecycleState }}
            </NTag>
          </NDescriptionsItem>
          <NDescriptionsItem label="区域">
            <NTag v-if="detail.region" size="small">{{ detail.region }}</NTag>
            <span v-else class="muted">—</span>
          </NDescriptionsItem>
          <NDescriptionsItem label="机器 ID">
            <code class="kbd text-xs">{{ detail.id }}</code>
          </NDescriptionsItem>
        </NDescriptions>
      </NCard>

      <!-- === Section 2: 账面 (财务字段: 云厂商 / 成本 / 账单日 / 到期日) === -->
      <NCard size="small" :bordered="false" class="info-section">
        <template #header>
          <div class="section-header">
            <NIcon class="section-icon"><CircleDollarSign :size="14" /></NIcon>
            <span>账面信息</span>
            <span class="flex-1"></span>
            <NButton size="tiny" quaternary type="primary" @click="billingEditOpen = true">
              <template #icon><NIcon><Edit3 :size="12" /></NIcon></template>
              编辑
            </NButton>
          </div>
        </template>
        <!-- 云厂商 独占 + 成本/账单日/到期日 三等分 -->
        <NDescriptions bordered size="small" label-placement="left" :column="3" label-style="width: 6rem">
          <NDescriptionsItem label="云厂商" :span="3">
            <NTag v-if="billing?.idcProvider" size="small" type="info">{{ billing.idcProvider }}</NTag>
            <span v-else class="muted">—</span>
          </NDescriptionsItem>
          <NDescriptionsItem label="月成本">
            <template v-if="billing?.costMonthly != null">
              <span class="num">¥{{ billing.costMonthly }}</span>
              <span class="unit">/ 月</span>
            </template>
            <span v-else class="muted">—</span>
          </NDescriptionsItem>
          <NDescriptionsItem label="账单日">
            <template v-if="billing?.billingCycleDay">
              <span class="unit">每月</span>
              <span class="num">{{ billing.billingCycleDay }}</span>
              <span class="unit">号</span>
            </template>
            <span v-else class="muted">—</span>
          </NDescriptionsItem>
          <NDescriptionsItem label="到期日">
            <div v-if="billing?.expiresAt" class="flex items-center gap-2">
              <span class="num">{{ billing.expiresAt }}</span>
              <NTag size="tiny" :type="expiresTagType">
                <span v-if="expiresDaysLeft! < 0">已过期 {{ -expiresDaysLeft! }} 天</span>
                <span v-else>剩 {{ expiresDaysLeft }} 天</span>
              </NTag>
            </div>
            <span v-else class="muted">—</span>
          </NDescriptionsItem>
        </NDescriptions>
      </NCard>

      <!-- === Section 2b: 容量与流量 (业务阈值: 限定带宽 + 月流量阈值; 真实 enforce / throttle 状态机用) === -->
      <NCard size="small" :bordered="false" class="info-section">
        <template #header>
          <div class="section-header">
            <NIcon class="section-icon"><CircleDollarSign :size="14" /></NIcon>
            <span>容量与流量</span>
            <span class="text-xs text-zinc-400 ml-2">业务阈值 · agent tc / throttle 用</span>
            <span class="flex-1"></span>
            <NButton size="tiny" quaternary type="primary" @click="quotaEditOpen = true">
              <template #icon><NIcon><Edit3 :size="12" /></NIcon></template>
              编辑
            </NButton>
          </div>
        </template>
        <NDescriptions bordered size="small" label-placement="left" :column="2" label-style="width: 6rem">
          <NDescriptionsItem label="限定带宽">
            <template v-if="capacity?.bandwidthMbps">
              <span class="num">{{ capacity.bandwidthMbps }}</span>
              <span class="unit">Mbps</span>
              <span class="text-xs text-zinc-400 ml-2">远端带宽限速值</span>
            </template>
            <span v-else class="muted">不限</span>
          </NDescriptionsItem>
          <NDescriptionsItem label="月流量阈值">
            <template v-if="capacity?.totalGb">
              <span class="num">{{ capacity.totalGb }}</span>
              <span class="unit">GB / 月</span>
              <span class="text-xs text-zinc-400 ml-2">月用量达 90% 触发限流</span>
            </template>
            <span v-else class="muted">不限</span>
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
        <NDescriptions bordered size="small" label-placement="left" :column="1" label-style="width: 6rem">
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

    <ServerCoreEditDialog v-if="detail" v-model="coreEditOpen" :server="detail" @saved="afterEdit" />
    <ServerBillingEditDialog v-model="billingEditOpen" :server-id="serverId" @saved="afterEdit" />
    <ServerQuotaEditDialog v-model="quotaEditOpen" :server-id="serverId" @saved="afterEdit" />
  </NSpin>
</template>

<style scoped>
/* 字体 / 数值 / 段头 走 main.scss 全局 tokens */

/* 状态管理操作条: 浅色卡片条 + 左标题右操作, 区别于普通 action-bar, 更显眼 */
.lifecycle-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  margin-bottom: 4px;
  background: rgba(99, 102, 241, 0.05);
  border: 1px solid rgba(99, 102, 241, 0.14);
  border-radius: 6px;
}
.lifecycle-bar-icon {
  color: #6366f1;
}
.lifecycle-bar-title {
  font-size: 13px;
  font-weight: 500;
  color: #52525b;
}

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
