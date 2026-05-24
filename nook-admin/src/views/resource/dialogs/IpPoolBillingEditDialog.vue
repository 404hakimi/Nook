<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import {
  NAlert,
  NButton,
  NDatePicker,
  NDivider,
  NForm,
  NFormItem,
  NInputNumber,
  NModal,
  NSpace,
  NSpin,
  NTag,
  useMessage
} from 'naive-ui'
import {
  THROTTLE_STATE_LABELS,
  getIpPoolBilling,
  getIpPoolCapacity,
  updateIpPoolBilling,
  updateIpPoolCapacity,
  type IpPoolBilling,
  type IpPoolCapacity
} from '@/api/resource/ip-pool'

/**
 * 编辑 容量与账面 — 两个 section 一个 dialog:
 *  ① 容量 (实际控制) — 实际限速 / 月流量上限 / 重置策略 → updateIpPoolCapacity
 *  ② 账面 (财务记录) — 采购带宽 / 采购流量 / 月费 / 账单日 / 到期 → updateIpPoolBilling
 *
 * 保存时按需 dispatch 两个 PUT (各自字段未改则跳过).
 */
const props = defineProps<{
  modelValue: boolean
  ipId: string
}>()
const emit = defineEmits<{
  'update:modelValue': [v: boolean]
  saved: []
}>()

const message = useMessage()
const submitting = ref(false)
const loading = ref(false)
const errors = reactive<Record<string, string>>({})

// ===== 容量 (capacity 子表; quota_reset_policy 由后端默认填 CALENDAR_MONTH, 这里不暴露给 admin) =====
const capacityForm = reactive({
  bandwidthLimitMbps: 0,
  monthlyTrafficGb: null as number | null
})
// 远端 agent 上报的累计字段 (只读展示)
const capacityRuntime = reactive({
  usedTrafficBytes: 0 as number | null,
  rxBytes: 0 as number | null,
  txBytes: 0 as number | null,
  throttleState: 'NORMAL' as string | null
})

// ===== 账面 (billing 子表) =====
const billingForm = reactive({
  bandwidthMbps: null as number | null,
  trafficQuotaGb: null as number | null,
  costMonthlyUsd: null as number | null,
  billingCycleDay: null as number | null,
  expiresAtTs: null as number | null
})

function fillCapacity(c: IpPoolCapacity | null) {
  capacityForm.bandwidthLimitMbps = c?.bandwidthLimitMbps ?? 0
  capacityForm.monthlyTrafficGb = c?.monthlyTrafficGb ?? null
  capacityRuntime.usedTrafficBytes = c?.usedTrafficBytes ?? 0
  capacityRuntime.rxBytes = c?.rxBytes ?? 0
  capacityRuntime.txBytes = c?.txBytes ?? 0
  capacityRuntime.throttleState = c?.throttleState ?? 'NORMAL'
}

function fillBilling(b: IpPoolBilling | null) {
  billingForm.bandwidthMbps = b?.bandwidthMbps ?? null
  billingForm.trafficQuotaGb = b?.trafficQuotaGb ?? null
  billingForm.costMonthlyUsd = b?.costMonthlyUsd ?? null
  billingForm.billingCycleDay = b?.billingCycleDay ?? null
  billingForm.expiresAtTs = b?.expiresAt ? new Date(b.expiresAt).getTime() : null
}

watch(() => [props.modelValue, props.ipId], async ([open]) => {
  if (!open) return
  Object.keys(errors).forEach((k) => delete errors[k])
  loading.value = true
  try {
    const [c, b] = await Promise.all([
      getIpPoolCapacity(props.ipId).catch(() => null),
      getIpPoolBilling(props.ipId).catch(() => null)
    ])
    fillCapacity(c)
    fillBilling(b)
  } finally {
    loading.value = false
  }
}, { immediate: true })

const usedTrafficLabel = computed(() => {
  const bytes = capacityRuntime.usedTrafficBytes ?? 0
  if (bytes === 0) return '0 B'
  const gb = bytes / 1024 / 1024 / 1024
  if (gb >= 1) return `${gb.toFixed(2)} GB`
  const mb = bytes / 1024 / 1024
  return `${mb.toFixed(1)} MB`
})

const trafficUsagePercent = computed(() => {
  if (!capacityForm.monthlyTrafficGb || capacityForm.monthlyTrafficGb <= 0) return null
  const usedGb = (capacityRuntime.usedTrafficBytes ?? 0) / 1024 / 1024 / 1024
  return Math.min(100, Math.round((usedGb / capacityForm.monthlyTrafficGb) * 100))
})

function tsToDateStr(ts: number | null): string | undefined {
  if (ts == null) return undefined
  const d = new Date(ts)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${dd}`
}

function validate(): boolean {
  Object.keys(errors).forEach((k) => delete errors[k])
  if (capacityForm.bandwidthLimitMbps == null || capacityForm.bandwidthLimitMbps < 0) {
    errors.bandwidthLimitMbps = '实际限速 ≥ 0 (0 = 不限)'
  }
  if (capacityForm.monthlyTrafficGb != null && capacityForm.monthlyTrafficGb < 0) {
    errors.monthlyTrafficGb = '月流量上限 ≥ 0'
  }
  if (billingForm.billingCycleDay != null
      && (billingForm.billingCycleDay < 1 || billingForm.billingCycleDay > 28)) {
    errors.billingCycleDay = '账单日 1-28'
  }
  if (billingForm.bandwidthMbps != null && billingForm.bandwidthMbps < 0) {
    errors.bandwidthMbps = '采购带宽 ≥ 0'
  }
  if (billingForm.trafficQuotaGb != null && billingForm.trafficQuotaGb < 0) {
    errors.trafficQuotaGb = '采购流量 ≥ 0'
  }
  return Object.keys(errors).length === 0
}

async function onSubmit() {
  if (!validate()) return
  submitting.value = true
  try {
    // 并行 2 个 PUT (各管各子表)
    await Promise.all([
      updateIpPoolCapacity(props.ipId, {
        bandwidthLimitMbps: capacityForm.bandwidthLimitMbps,
        monthlyTrafficGb: capacityForm.monthlyTrafficGb ?? undefined
      }),
      updateIpPoolBilling(props.ipId, {
        bandwidthMbps: billingForm.bandwidthMbps ?? undefined,
        trafficQuotaGb: billingForm.trafficQuotaGb ?? undefined,
        costMonthlyUsd: billingForm.costMonthlyUsd ?? undefined,
        billingCycleDay: billingForm.billingCycleDay ?? undefined,
        expiresAt: tsToDateStr(billingForm.expiresAtTs)
      })
    ])
    message.success('已保存')
    emit('saved')
    emit('update:modelValue', false)
  } catch { /* */ } finally {
    submitting.value = false
  }
}
</script>

<template>
  <NModal
    :show="modelValue"
    preset="card"
    title="编辑 容量与账面"
    style="max-width: 44rem; width: 92vw"
    :bordered="false"
    @update:show="(v: boolean) => emit('update:modelValue', v)"
  >
    <NSpin :show="loading">
      <!-- ============ 容量 (实际控制) ============ -->
      <div class="section-title">
        <span>容量 — 实际控制</span>
        <NTag
          v-if="capacityRuntime.throttleState && capacityRuntime.throttleState !== 'NORMAL'"
          size="small"
          type="warning"
        >
          {{ THROTTLE_STATE_LABELS[capacityRuntime.throttleState] || capacityRuntime.throttleState }}
        </NTag>
        <NTag v-else size="small" type="success">
          {{ THROTTLE_STATE_LABELS.NORMAL }}
        </NTag>
      </div>
      <NAlert type="info" :show-icon="false" size="small" class="mb-3">
        实际限速由远端 SOCKS5 服务落实, 改完后需走"重装" 在远端生效.
        月流量上限触发后系统自动停止把该 IP 分配给新订阅.
      </NAlert>
      <NForm :model="capacityForm" label-placement="top" size="small">
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-x-4">
          <NFormItem
            label="实际限速 Mbps (0=不限)"
            :feedback="errors.bandwidthLimitMbps"
            :validation-status="errors.bandwidthLimitMbps ? 'error' : undefined"
          >
            <NInputNumber v-model:value="capacityForm.bandwidthLimitMbps" :min="0" :max="1000000" class="w-full" />
          </NFormItem>
          <NFormItem
            label="月流量上限 GB (留空=不限)"
            :feedback="errors.monthlyTrafficGb"
            :validation-status="errors.monthlyTrafficGb ? 'error' : undefined"
          >
            <NInputNumber v-model:value="capacityForm.monthlyTrafficGb" :min="0" :max="10000000" class="w-full" />
          </NFormItem>
        </div>

        <!-- 当前周期使用情况 (只读) -->
        <div class="usage-block">
          <div class="usage-label">当前周期已用</div>
          <div class="usage-value">
            <span class="font-mono">{{ usedTrafficLabel }}</span>
            <template v-if="trafficUsagePercent != null">
              <span class="text-zinc-400 mx-1">/</span>
              <span class="font-mono">{{ capacityForm.monthlyTrafficGb }} GB</span>
              <NTag size="small" :type="trafficUsagePercent >= 90 ? 'error' : (trafficUsagePercent >= 70 ? 'warning' : 'success')" class="ml-2">
                {{ trafficUsagePercent }}%
              </NTag>
            </template>
            <span v-else class="text-zinc-400 ml-2 text-xs">月流量上限未配置, 不触发限流</span>
          </div>
        </div>
      </NForm>

      <NDivider style="margin: 16px 0" />

      <!-- ============ 账面 (财务记录) ============ -->
      <div class="section-title">
        <span>账面 — 财务记录</span>
        <span class="text-xs text-zinc-400 ml-2">仅记录, 不参与实际控制</span>
      </div>
      <NForm :model="billingForm" label-placement="top" size="small">
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-x-4">
          <NFormItem
            label="采购带宽 Mbps"
            :feedback="errors.bandwidthMbps"
            :validation-status="errors.bandwidthMbps ? 'error' : undefined"
          >
            <NInputNumber v-model:value="billingForm.bandwidthMbps" :min="0" class="w-full" />
          </NFormItem>
          <NFormItem
            label="采购流量 GB"
            :feedback="errors.trafficQuotaGb"
            :validation-status="errors.trafficQuotaGb ? 'error' : undefined"
          >
            <NInputNumber v-model:value="billingForm.trafficQuotaGb" :min="0" class="w-full" />
          </NFormItem>
          <NFormItem label="月度成本 USD">
            <NInputNumber v-model:value="billingForm.costMonthlyUsd" :min="0" :precision="2" class="w-full" />
          </NFormItem>
          <NFormItem
            label="账单日 (1-28)"
            :feedback="errors.billingCycleDay"
            :validation-status="errors.billingCycleDay ? 'error' : undefined"
          >
            <NInputNumber v-model:value="billingForm.billingCycleDay" :min="1" :max="28" class="w-full" />
          </NFormItem>
          <NFormItem label="到期日" :span="2">
            <NDatePicker v-model:value="billingForm.expiresAtTs" type="date" clearable class="w-full" />
          </NFormItem>
        </div>
      </NForm>
    </NSpin>
    <template #footer>
      <NSpace justify="end">
        <NButton size="small" @click="emit('update:modelValue', false)">取消</NButton>
        <NButton type="primary" size="small" :loading="submitting" :disabled="loading" @click="onSubmit">
          保存
        </NButton>
      </NSpace>
    </template>
  </NModal>
</template>

<style scoped>
.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--n-text-color-2, #555);
  margin-bottom: 8px;
}
.usage-block {
  margin-top: 4px;
  padding: 8px 12px;
  background: var(--n-action-color, #fafafa);
  border-radius: 4px;
  border: 1px solid var(--n-border-color, #efeff5);
}
.usage-label {
  font-size: 11px;
  color: var(--n-text-color-3, #999);
  margin-bottom: 2px;
}
.usage-value {
  font-size: 13px;
  color: var(--n-text-color-1, #222);
  display: flex;
  align-items: center;
}
</style>
