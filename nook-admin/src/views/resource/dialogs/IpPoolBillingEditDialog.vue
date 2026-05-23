<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import {
  NAlert,
  NButton,
  NDatePicker,
  NForm,
  NFormItem,
  NInputNumber,
  NModal,
  NSpace,
  NSpin,
  useMessage
} from 'naive-ui'
import {
  getIpPoolBilling,
  updateIpPoolBilling,
  type IpPoolBilling
} from '@/api/resource/ip-pool'

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

const form = reactive({
  bandwidthMbps: null as number | null,
  trafficQuotaGb: null as number | null,
  costMonthlyUsd: null as number | null,
  billingCycleDay: null as number | null,
  expiresAtTs: null as number | null
})

function fill(b: IpPoolBilling | null) {
  if (!b) {
    form.bandwidthMbps = null
    form.trafficQuotaGb = null
    form.costMonthlyUsd = null
    form.billingCycleDay = null
    form.expiresAtTs = null
    return
  }
  form.bandwidthMbps = b.bandwidthMbps ?? null
  form.trafficQuotaGb = b.trafficQuotaGb ?? null
  form.costMonthlyUsd = b.costMonthlyUsd ?? null
  form.billingCycleDay = b.billingCycleDay ?? null
  form.expiresAtTs = b.expiresAt ? new Date(b.expiresAt).getTime() : null
}

watch(() => [props.modelValue, props.ipId], async ([open]) => {
  if (!open) return
  Object.keys(errors).forEach((k) => delete errors[k])
  loading.value = true
  try {
    const b = await getIpPoolBilling(props.ipId)
    fill(b)
  } catch { /* */ } finally {
    loading.value = false
  }
}, { immediate: true })

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
  if (form.billingCycleDay != null && (form.billingCycleDay < 1 || form.billingCycleDay > 28)) {
    errors.billingCycleDay = '账单日 1-28'
  }
  if (form.bandwidthMbps != null && form.bandwidthMbps < 0) errors.bandwidthMbps = '带宽 ≥ 0'
  if (form.trafficQuotaGb != null && form.trafficQuotaGb < 0) errors.trafficQuotaGb = '流量 ≥ 0'
  return Object.keys(errors).length === 0
}

async function onSubmit() {
  if (!validate()) return
  submitting.value = true
  try {
    await updateIpPoolBilling(props.ipId, {
      bandwidthMbps: form.bandwidthMbps ?? undefined,
      trafficQuotaGb: form.trafficQuotaGb ?? undefined,
      costMonthlyUsd: form.costMonthlyUsd ?? undefined,
      billingCycleDay: form.billingCycleDay ?? undefined,
      expiresAt: tsToDateStr(form.expiresAtTs)
    })
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
    title="编辑账面信息"
    style="max-width: 36rem; width: 92vw"
    :bordered="false"
    @update:show="(v: boolean) => emit('update:modelValue', v)"
  >
    <NSpin :show="loading">
      <NAlert type="info" :show-icon="false" size="small" class="mb-3">
        账面记录, 仅展示用 (allocator 与 dante 限速不参考此字段); 实际限速在 dante 配置编辑.
      </NAlert>
      <NForm :model="form" label-placement="top" size="small">
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-x-4">
          <NFormItem label="采购带宽 (Mbps)" :feedback="errors.bandwidthMbps" :validation-status="errors.bandwidthMbps ? 'error' : undefined">
            <NInputNumber v-model:value="form.bandwidthMbps" :min="0" class="w-full" />
          </NFormItem>
          <NFormItem label="采购流量 (GB)" :feedback="errors.trafficQuotaGb" :validation-status="errors.trafficQuotaGb ? 'error' : undefined">
            <NInputNumber v-model:value="form.trafficQuotaGb" :min="0" class="w-full" />
          </NFormItem>
          <NFormItem label="月度成本 USD">
            <NInputNumber v-model:value="form.costMonthlyUsd" :min="0" :precision="2" class="w-full" />
          </NFormItem>
          <NFormItem label="账单日 (1-28)" :feedback="errors.billingCycleDay" :validation-status="errors.billingCycleDay ? 'error' : undefined">
            <NInputNumber v-model:value="form.billingCycleDay" :min="1" :max="28" class="w-full" />
          </NFormItem>
          <NFormItem label="到期日" :span="2">
            <NDatePicker v-model:value="form.expiresAtTs" type="date" clearable class="w-full" />
          </NFormItem>
        </div>
      </NForm>
    </NSpin>
    <template #footer>
      <NSpace justify="end">
        <NButton size="small" @click="emit('update:modelValue', false)">取消</NButton>
        <NButton type="primary" size="small" :loading="submitting" :disabled="loading" @click="onSubmit">保存</NButton>
      </NSpace>
    </template>
  </NModal>
</template>
