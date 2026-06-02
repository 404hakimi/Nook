<script setup lang="ts">
import { computed, ref } from 'vue'
import {
  Calendar,
  CircleDollarSign,
  Edit3,
  FileText,
  Globe,
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
  NTag,
  useDialog,
  useMessage
} from 'naive-ui'
import {
  SERVER_LANDING_LIFECYCLE_LABELS,
  SERVER_LANDING_LIFECYCLE_TAG_TYPE,
  SERVER_LANDING_STATUS_LABELS,
  deleteServerLanding,
  type ServerLanding
} from '@/api/resource/server-landing'
import { transitionServerLifecycle } from '@/api/resource/server'
import { IP_TYPE_CODE_LABELS } from '@/api/system/ip-type'
import { useIpTypeStore } from '@/stores/ipType'
import { storeToRefs } from 'pinia'
import { formatDateTime } from '@/utils/date'
import { statusTagType } from './landingHelpers'

/**
 * 落地机详情 — 信息 tab. 结构对齐线路机 ServerInfoTab: NCard + NDescriptions.
 *
 * <p>操作栏: lifecycle 切换 + 删除.
 * <p>5 段: 基础信息 / 占用与部署 / 账面 / 容量与流量 / 时间备注. 编辑通过 emit 交父组件挂 dialog.
 */
const props = defineProps<{ detail: ServerLanding }>()
const emit = defineEmits<{
  'edit-core': []
  'edit-capacity': []
  'edit-billing': []
  refresh: []
}>()

const message = useMessage()
const dialog = useDialog()

const ipTypeStore = useIpTypeStore()
const { list: ipTypes } = storeToRefs(ipTypeStore)

const ipTypeName = computed(() => {
  if (!props.detail.ipTypeId) return '—'
  const t = ipTypes.value.find((x) => x.id === props.detail.ipTypeId)
  if (!t) return props.detail.ipTypeId
  return IP_TYPE_CODE_LABELS[t.code] || t.name || t.code
})

// ===== lifecycle 切换 =====
const lifecycleOptions = [
  { label: '装机中', value: 'INSTALLING' },
  { label: '待上线', value: 'READY' },
  { label: '运行中', value: 'LIVE' },
  { label: '已停用', value: 'RETIRED' }
]
const targetLifecycle = ref<string | null>(null)
async function doTransition() {
  if (!targetLifecycle.value) return
  dialog.warning({
    title: '确认切换 lifecycle',
    content: `${props.detail.lifecycleState} → ${targetLifecycle.value} ?`,
    positiveText: '切换', negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await transitionServerLifecycle(props.detail.id, targetLifecycle.value!)
        message.success('已切换')
        targetLifecycle.value = null
        emit('refresh')
      } catch { /* */ }
    }
  })
}

async function onDelete() {
  try {
    await deleteServerLanding(props.detail.id)
    message.success('已删除')
    location.href = '/resource/server-landing'
  } catch { /* */ }
}

// ===== 到期天数 =====
const expiresDaysLeft = computed(() => {
  if (!props.detail.expiresAt) return null
  const d = new Date(props.detail.expiresAt).getTime()
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

const trafficUsagePercent = computed(() => {
  const limitGb = props.detail.monthlyTrafficGb
  if (!limitGb || limitGb <= 0) return null
  const usedGb = (props.detail.usedTrafficBytes ?? 0) / 1024 / 1024 / 1024
  return Math.min(100, Math.round((usedGb / limitGb) * 100))
})
</script>

<template>
  <div class="space-y-3">
    <!-- 操作栏: lifecycle 切换 + 删除 -->
    <div class="action-bar">
      <NSelect
        v-model:value="targetLifecycle"
        :options="lifecycleOptions"
        size="small"
        placeholder="切换 lifecycle"
        class="w-32"
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
            删除落地机
          </NButton>
        </template>
        软删主记录, 保留装机历史 / op_log, 不可撤销.
      </NPopconfirm>
    </div>

    <!-- === Section 1: 基础信息 === -->
    <NCard size="small" :bordered="false" class="info-section">
      <template #header>
        <div class="section-header">
          <NIcon class="section-icon"><Server :size="14" /></NIcon>
          <span>基础信息</span>
          <span class="flex-1"></span>
          <NButton size="tiny" quaternary type="primary" @click="emit('edit-core')">
            <template #icon><NIcon><Edit3 :size="12" /></NIcon></template>
            编辑
          </NButton>
        </div>
      </template>
      <NDescriptions bordered size="small" label-placement="left" :column="2" label-style="width: 6rem">
        <NDescriptionsItem label="名称">{{ detail.name }}</NDescriptionsItem>
        <NDescriptionsItem label="生命周期">
          <NTag size="small" :type="SERVER_LANDING_LIFECYCLE_TAG_TYPE[detail.lifecycleState] || 'default'">
            {{ SERVER_LANDING_LIFECYCLE_LABELS[detail.lifecycleState] || detail.lifecycleState }}
          </NTag>
        </NDescriptionsItem>
        <NDescriptionsItem label="区域">
          <NTag v-if="detail.region" size="small">{{ detail.region }}</NTag>
          <span v-else class="muted">—</span>
        </NDescriptionsItem>
        <NDescriptionsItem label="IP 地址">
          <code class="kbd">{{ detail.ipAddress }}</code>
        </NDescriptionsItem>
        <NDescriptionsItem label="服务器 ID" :span="2">
          <code class="kbd text-xs">{{ detail.id }}</code>
        </NDescriptionsItem>
      </NDescriptions>
    </NCard>

    <!-- === Section 2: 占用与部署 (landing 专属: IP 类型 / 部署模式 / 占用状态 / 当前会员 / 占用时间) === -->
    <NCard size="small" :bordered="false" class="info-section">
      <template #header>
        <div class="section-header">
          <NIcon class="section-icon"><Globe :size="14" /></NIcon>
          <span>占用与部署</span>
        </div>
      </template>
      <NDescriptions bordered size="small" label-placement="left" :column="2" label-style="width: 6rem">
        <NDescriptionsItem label="IP 类型">
          <span>{{ ipTypeName }}</span>
        </NDescriptionsItem>
        <NDescriptionsItem label="部署模式">
          <NTag size="small" :type="detail.provisionMode === 1 ? 'success' : 'info'">
            {{ detail.provisionMode === 1 ? '自部署' : '第三方' }}
          </NTag>
        </NDescriptionsItem>
        <NDescriptionsItem label="占用状态">
          <NTag size="small" :type="statusTagType(detail.status)">
            {{ SERVER_LANDING_STATUS_LABELS[detail.status] || detail.status }}
          </NTag>
        </NDescriptionsItem>
        <NDescriptionsItem label="当前会员">
          <code v-if="detail.occupiedByMemberId" class="kbd text-xs">{{ detail.occupiedByMemberId }}</code>
          <span v-else class="muted">—</span>
        </NDescriptionsItem>
        <NDescriptionsItem label="占用时间">
          <span v-if="detail.occupiedAt">{{ formatDateTime(detail.occupiedAt) }}</span>
          <span v-else class="muted">—</span>
        </NDescriptionsItem>
      </NDescriptions>
    </NCard>

    <!-- === Section 3: 账面 (财务字段; 仅记录) === -->
    <NCard size="small" :bordered="false" class="info-section">
      <template #header>
        <div class="section-header">
          <NIcon class="section-icon"><CircleDollarSign :size="14" /></NIcon>
          <span>账面信息</span>
          <span class="text-xs text-zinc-400 ml-2">仅记录, 不参与实际控制</span>
          <span class="flex-1"></span>
          <NButton size="tiny" quaternary type="primary" @click="emit('edit-billing')">
            <template #icon><NIcon><Edit3 :size="12" /></NIcon></template>
            编辑
          </NButton>
        </div>
      </template>
      <NDescriptions bordered size="small" label-placement="left" :column="3" label-style="width: 6rem">
        <NDescriptionsItem label="月成本">
          <template v-if="detail.costMonthly != null">
            <span class="num">¥{{ detail.costMonthly }}</span>
            <span class="unit">/ 月</span>
          </template>
          <span v-else class="muted">—</span>
        </NDescriptionsItem>
        <NDescriptionsItem label="账单日">
          <template v-if="detail.billingCycleDay">
            <span class="unit">每月</span>
            <span class="num">{{ detail.billingCycleDay }}</span>
            <span class="unit">号</span>
          </template>
          <span v-else class="muted">—</span>
        </NDescriptionsItem>
        <NDescriptionsItem label="到期日">
          <div v-if="detail.expiresAt" class="flex items-center gap-2">
            <span class="num">{{ detail.expiresAt }}</span>
            <NTag size="tiny" :type="expiresTagType">
              <span v-if="expiresDaysLeft! < 0">已过期 {{ -expiresDaysLeft! }} 天</span>
              <span v-else>剩 {{ expiresDaysLeft }} 天</span>
            </NTag>
          </div>
          <span v-else class="muted">—</span>
        </NDescriptionsItem>
      </NDescriptions>
    </NCard>

    <!-- === Section 4: 容量与流量 (业务阈值 + 当期已用) === -->
    <NCard size="small" :bordered="false" class="info-section">
      <template #header>
        <div class="section-header">
          <NIcon class="section-icon"><CircleDollarSign :size="14" /></NIcon>
          <span>容量与流量</span>
          <span class="text-xs text-zinc-400 ml-2">业务阈值 · agent tc / throttle 用</span>
          <span class="flex-1"></span>
          <NButton size="tiny" quaternary type="primary" @click="emit('edit-capacity')">
            <template #icon><NIcon><Edit3 :size="12" /></NIcon></template>
            编辑
          </NButton>
        </div>
      </template>
      <NDescriptions bordered size="small" label-placement="left" :column="2" label-style="width: 6rem">
        <NDescriptionsItem label="限定带宽">
          <template v-if="detail.bandwidthLimitMbps">
            <span class="num">{{ detail.bandwidthLimitMbps }}</span>
            <span class="unit">Mbps</span>
            <span class="text-xs text-zinc-400 ml-2">dante 限速值</span>
          </template>
          <span v-else class="muted">不限</span>
        </NDescriptionsItem>
        <NDescriptionsItem label="月流量阈值">
          <template v-if="detail.monthlyTrafficGb">
            <span class="num">{{ detail.monthlyTrafficGb }}</span>
            <span class="unit">GB / 月</span>
            <span class="text-xs text-zinc-400 ml-2">月用量达 90% 触发限流</span>
          </template>
          <span v-else class="muted">不限</span>
        </NDescriptionsItem>
        <NDescriptionsItem label="本期已用" :span="2">
          <span class="num">{{ ((detail.usedTrafficBytes ?? 0) / 1024 / 1024 / 1024).toFixed(2) }}</span>
          <span class="unit">GB</span>
          <NTag
            v-if="trafficUsagePercent != null"
            size="tiny"
            :type="trafficUsagePercent >= 90 ? 'error' : (trafficUsagePercent >= 70 ? 'warning' : 'success')"
            class="ml-2"
          >
            {{ trafficUsagePercent }}%
          </NTag>
          <NTag
            size="tiny"
            :type="detail.throttleState === 'THROTTLED' ? 'warning' : 'success'"
            class="ml-2"
          >
            {{ detail.throttleState === 'THROTTLED' ? '已触发限流' : '正常' }}
          </NTag>
        </NDescriptionsItem>
      </NDescriptions>
    </NCard>

    <!-- === Section 5: 时间 + 备注 === -->
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
</template>

<style scoped>
.remark-block {
  margin-top: 8px;
  padding: 8px 10px;
  background: rgba(127, 127, 127, 0.04);
  border-radius: 4px;
  border-left: 3px solid #a1a1aa;
}
</style>
