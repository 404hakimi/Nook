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
  deleteServerLanding,
  type ServerLanding
} from '@/api/resource/server-landing'
import { transitionLandingLifecycle } from '@/api/resource/server-landing'
import { SERVER_THROTTLE_STATE, SERVER_THROTTLE_STATE_LABELS } from '@/api/resource/server'
import { IP_TYPE_CODE_LABELS } from '@/api/system/ip-type'
import { useIpTypeStore } from '@/stores/ipType'
import { storeToRefs } from 'pinia'
import { formatDateTime } from '@/utils/date'

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
    title: '确认切换状态',
    content: `${SERVER_LANDING_LIFECYCLE_LABELS[props.detail.lifecycleState] || props.detail.lifecycleState} → ${SERVER_LANDING_LIFECYCLE_LABELS[targetLifecycle.value] || targetLifecycle.value} ?`,
    positiveText: '切换', negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await transitionLandingLifecycle(props.detail.id, targetLifecycle.value!)
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
  const limitGb = props.detail.totalGb
  if (!limitGb || limitGb <= 0) return null
  const usedGb = (props.detail.usedBytes ?? 0) / 1024 / 1024 / 1024
  return Math.min(100, Math.round((usedGb / limitGb) * 100))
})
</script>

<template>
  <div class="space-y-3">
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
            删除落地机
          </NButton>
        </template>
        级联删除主记录与全部子表 (凭据 / 账面 / 配额 / 流量 / 装机 等), 不可撤销; 装机中 / 待上线可直接删, 运行中 / 已退役若仍被占用则拒绝.
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
          <template v-if="detail.bandwidthMbps">
            <span class="num">{{ detail.bandwidthMbps }}</span>
            <span class="unit">Mbps</span>
            <span class="text-xs text-zinc-400 ml-2">dante 限速值</span>
          </template>
          <span v-else class="muted">不限</span>
        </NDescriptionsItem>
        <NDescriptionsItem label="月流量阈值">
          <template v-if="detail.totalGb">
            <span class="num">{{ detail.totalGb }}</span>
            <span class="unit">GB / 月</span>
            <span class="text-xs text-zinc-400 ml-2">月用量达 90% 触发限流</span>
          </template>
          <span v-else class="muted">不限</span>
        </NDescriptionsItem>
        <NDescriptionsItem label="本期已用" :span="2">
          <span class="num">{{ ((detail.usedBytes ?? 0) / 1024 / 1024 / 1024).toFixed(2) }}</span>
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
            :type="detail.throttleState === SERVER_THROTTLE_STATE.THROTTLED ? 'warning' : 'success'"
            class="ml-2"
          >
            {{ SERVER_THROTTLE_STATE_LABELS[detail.throttleState || SERVER_THROTTLE_STATE.NORMAL] }}
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
/* 状态管理操作条: 与线路机详情一致, 浅色卡片条 + 左标题右操作 */
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

.remark-block {
  margin-top: 8px;
  padding: 8px 10px;
  background: rgba(127, 127, 127, 0.04);
  border-radius: 4px;
  border-left: 3px solid #a1a1aa;
}
</style>
