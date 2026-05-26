<script setup lang="ts">
import { computed } from 'vue'
import { Pencil } from 'lucide-vue-next'
import { NButton, NIcon, NTag } from 'naive-ui'
import {
  SERVER_LANDING_STATUS_LABELS,
  type ServerLanding
} from '@/api/resource/server-landing'
import { IP_TYPE_CODE_LABELS } from '@/api/system/ip-type'
import { useIpTypeStore } from '@/stores/ipType'
import { storeToRefs } from 'pinia'
import { formatDateTime } from '@/utils/date'
import { statusTagType, formatBytes } from './landingHelpers'

const props = defineProps<{ detail: ServerLanding }>()
const emit = defineEmits<{
  'edit-core': []
  'edit-capacity': []
  'edit-billing': []
}>()

const ipTypeStore = useIpTypeStore()
const { list: ipTypes } = storeToRefs(ipTypeStore)

function ipTypeName(ipTypeId?: string): string {
  if (!ipTypeId) return '-'
  const t = ipTypes.value.find((x) => x.id === ipTypeId)
  if (!t) return ipTypeId
  return IP_TYPE_CODE_LABELS[t.code] || t.name || t.code
}

const trafficUsagePercent = computed(() => {
  const limitGb = props.detail.monthlyTrafficGb
  if (!limitGb || limitGb <= 0) return null
  const usedGb = (props.detail.usedTrafficBytes ?? 0) / 1024 / 1024 / 1024
  return Math.min(100, Math.round((usedGb / limitGb) * 100))
})
</script>

<template>
  <!-- 资源归属 -->
  <div class="section-header">
    <span class="section-title">资源归属</span>
    <div class="section-action">
      <NButton size="tiny" quaternary type="primary" @click="emit('edit-core')">
        <template #icon><NIcon><Pencil /></NIcon></template>
        编辑
      </NButton>
    </div>
  </div>
  <div class="info-grid">
    <div class="info-row"><span class="k">IP 地址</span><code class="v">{{ detail.ipAddress }}</code></div>
    <div class="info-row"><span class="k">部署模式</span><span class="v">{{ detail.provisionMode === 1 ? '自部署' : '第三方' }}</span></div>
    <div class="info-row"><span class="k">区域</span><span class="v">{{ detail.region || '-' }}</span></div>
    <div class="info-row"><span class="k">类型</span><span class="v">{{ ipTypeName(detail.ipTypeId) }}</span></div>
    <div class="info-row"><span class="k">占用状态</span>
      <NTag size="tiny" :type="statusTagType(detail.status)">{{ SERVER_LANDING_STATUS_LABELS[detail.status] || detail.status }}</NTag>
    </div>
    <div class="info-row"><span class="k">当前会员</span><code class="v">{{ detail.occupiedByMemberId || '-' }}</code></div>
    <div class="info-row"><span class="k">占用时间</span><span class="v">{{ formatDateTime(detail.occupiedAt) || '—' }}</span></div>
    <div v-if="detail.coolingUntil" class="info-row"><span class="k">冷却到期</span><span class="v">{{ formatDateTime(detail.coolingUntil) }}</span></div>
    <div v-if="detail.remark" class="info-row info-row--full"><span class="k">备注</span><span class="v">{{ detail.remark }}</span></div>
  </div>

  <!-- 容量 — 实际控制 -->
  <div class="section-header mt-4">
    <span class="section-title">容量 — 实际控制</span>
    <div class="section-action">
      <NButton size="tiny" quaternary type="primary" @click="emit('edit-capacity')">
        <template #icon><NIcon><Pencil /></NIcon></template>
        编辑
      </NButton>
    </div>
  </div>
  <div class="info-grid">
    <div class="info-row"><span class="k">实际限速</span><span class="v">{{ detail.bandwidthLimitMbps ? `${detail.bandwidthLimitMbps} Mbps` : '不限' }}</span></div>
    <div class="info-row"><span class="k">月流量上限</span><span class="v">{{ detail.monthlyTrafficGb ? `${detail.monthlyTrafficGb} GB` : '不限' }}</span></div>
    <div class="info-row"><span class="k">本期已用</span>
      <span class="v">{{ formatBytes(detail.usedTrafficBytes) }}
        <NTag v-if="trafficUsagePercent != null" size="tiny" :type="trafficUsagePercent >= 90 ? 'error' : (trafficUsagePercent >= 70 ? 'warning' : 'success')" class="ml-1">
          {{ trafficUsagePercent }}%
        </NTag>
      </span>
    </div>
    <div class="info-row"><span class="k">流量状态</span>
      <NTag size="tiny" :type="detail.throttleState === 'THROTTLED' ? 'warning' : 'success'">
        {{ detail.throttleState === 'THROTTLED' ? '已触发限流' : '正常' }}
      </NTag>
    </div>
  </div>

  <!-- 账面 — 财务记录 -->
  <div class="section-header mt-4">
    <span class="section-title">账面 — 财务记录</span>
    <span class="section-hint">仅记录, 不参与实际控制</span>
    <div class="section-action">
      <NButton size="tiny" quaternary type="primary" @click="emit('edit-billing')">
        <template #icon><NIcon><Pencil /></NIcon></template>
        编辑
      </NButton>
    </div>
  </div>
  <div class="info-grid">
    <div class="info-row"><span class="k">月度成本</span><span class="v">{{ detail.costMonthlyUsd != null ? `${detail.costMonthlyUsd} USD` : '-' }}</span></div>
    <div class="info-row"><span class="k">账单日</span><span class="v">{{ detail.billingCycleDay != null ? `每月 ${detail.billingCycleDay} 号` : '-' }}</span></div>
    <div class="info-row info-row--full"><span class="k">到期日</span><span class="v">{{ detail.expiresAt || '-' }}</span></div>
  </div>
</template>

<style scoped src="./landing-tabs.scss"></style>
