<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import {
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
  getServerLandingBilling,
  updateServerLandingBilling,
  type ServerLandingBilling
} from '@/api/resource/server-landing'

/**
 * 编辑 账面 (billing 子表) — 月度成本 + 账单日 + 到期日.
 * 仅财务记录, 不参与实际控制 (实际带宽/流量在 quota 子表, 走 ServerLandingQuotaEditDialog).
 */
const props = defineProps<{
  modelValue: boolean
  serverId: string
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
  costMonthly: null as number | null,
  billingCycleDay: null as number | null,
  expiresAtTs: null as number | null
})

function fill(b: ServerLandingBilling | null) {
  form.costMonthly = b?.costMonthly ?? null
  form.billingCycleDay = b?.billingCycleDay ?? null
  form.expiresAtTs = b?.expiresAt ? new Date(b.expiresAt).getTime() : null
}

watch(() => [props.modelValue, props.serverId], async ([open]) => {
  if (!open) return
  Object.keys(errors).forEach((k) => delete errors[k])
  loading.value = true
  try {
    const b = await getServerLandingBilling(props.serverId).catch(() => null)
    fill(b)
  } finally {
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
  if (form.billingCycleDay != null
      && (form.billingCycleDay < 1 || form.billingCycleDay > 28)) {
    errors.billingCycleDay = '账单日 1-28'
  }
  return Object.keys(errors).length === 0
}

async function onSubmit() {
  if (!validate()) return
  submitting.value = true
  try {
    await updateServerLandingBilling(props.serverId, {
      costMonthly: form.costMonthly ?? undefined,
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
    title="编辑 账面 — 财务记录"
    style="max-width: 36rem; width: 92vw"
    :bordered="false"
    @update:show="(v: boolean) => emit('update:modelValue', v)"
  >
    <NSpin :show="loading">
      <div class="text-xs text-zinc-500 mb-3">
        仅记录, 不参与实际控制. 实际带宽 / 月流量在 "容量" 编辑.
      </div>
      <NForm :model="form" label-placement="top" size="small">
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-x-4">
          <NFormItem label="月成本 (¥)">
            <NInputNumber v-model:value="form.costMonthly" :min="0" :precision="2" class="w-full" />
          </NFormItem>
          <NFormItem
            label="账单日 (1-28)"
            :feedback="errors.billingCycleDay"
            :validation-status="errors.billingCycleDay ? 'error' : undefined"
          >
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
        <NButton type="primary" size="small" :loading="submitting" :disabled="loading" @click="onSubmit">
          保存
        </NButton>
      </NSpace>
    </template>
  </NModal>
</template>
