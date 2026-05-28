<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import {
  NButton,
  NDatePicker,
  NForm,
  NFormItem,
  NInput,
  NInputNumber,
  NModal,
  NSpace,
  NSpin,
  useMessage
} from 'naive-ui'
import {
  getServerBilling,
  updateServerBilling,
  type ServerBilling
} from '@/api/resource/server'

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
  idcProvider: '',
  costMonthly: null as number | null,
  billingCycleDay: null as number | null,
  expiresAtTs: null as number | null
})

function fill(b: ServerBilling | null) {
  if (!b) {
    form.idcProvider = ''
    form.costMonthly = null
    form.billingCycleDay = null
    form.expiresAtTs = null
    return
  }
  form.idcProvider = b.idcProvider ?? ''
  form.costMonthly = b.costMonthly ?? null
  form.billingCycleDay = b.billingCycleDay ?? null
  form.expiresAtTs = b.expiresAt ? new Date(b.expiresAt).getTime() : null
}

watch(() => [props.modelValue, props.serverId], async ([open]) => {
  if (!open) return
  Object.keys(errors).forEach((k) => delete errors[k])
  loading.value = true
  try {
    const b = await getServerBilling(props.serverId)
    fill(b)
  } catch { /* */ } finally {
    loading.value = false
  }
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
  if (form.billingCycleDay != null && (form.billingCycleDay < 1 || form.billingCycleDay > 28)) {
    errors.billingCycleDay = '账单日 1-28'
  }
  return Object.keys(errors).length === 0
}

async function onSubmit() {
  if (!validate()) return
  submitting.value = true
  try {
    await updateServerBilling(props.serverId, {
      idcProvider: form.idcProvider.trim() || undefined,
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
    title="编辑账面信息"
    style="max-width: 36rem; width: 92vw"
    :bordered="false"
    @update:show="(v: boolean) => emit('update:modelValue', v)"
  >
    <NSpin :show="loading">
      <NForm :model="form" label-placement="top" size="small">
        <NFormItem label="云厂商">
          <NInput v-model:value="form.idcProvider" placeholder="如 Vultr / Hetzner / dmit" />
        </NFormItem>
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-x-4">
          <NFormItem label="月成本 (¥)">
            <NInputNumber v-model:value="form.costMonthly" :min="0" :precision="2" class="w-full" />
          </NFormItem>
          <NFormItem label="账单日 (1-28)" :feedback="errors.billingCycleDay" :validation-status="errors.billingCycleDay ? 'error' : undefined">
            <NInputNumber v-model:value="form.billingCycleDay" :min="1" :max="28" class="w-full" />
          </NFormItem>
          <NFormItem label="到期日">
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
