<script setup lang="ts">
import { computed, h, onMounted, reactive, ref, watch } from 'vue'
import {
  NButton,
  NForm,
  NFormItem,
  NInput,
  NModal,
  NRadio,
  NRadioGroup,
  NSelect,
  NSpace,
  useMessage
} from 'naive-ui'
import {
  updateIpPoolCore,
  type IpPoolCoreUpdateDTO,
  type ResourceIpPool
} from '@/api/resource/ip-pool'
import { listIpTypes, type ResourceIpType, IP_TYPE_CODE_LABELS } from '@/api/resource/ip-type'
import { listEnabledRegions, type ResourceRegion } from '@/api/resource/region'
import RegionFlag from '@/components/RegionFlag.vue'

const props = defineProps<{
  modelValue: boolean
  ipPool: ResourceIpPool
}>()
const emit = defineEmits<{
  'update:modelValue': [v: boolean]
  saved: []
}>()

const message = useMessage()
const submitting = ref(false)
const errors = reactive<Record<string, string>>({})

const regions = ref<ResourceRegion[]>([])
const ipTypes = ref<ResourceIpType[]>([])

const regionOptions = computed(() =>
  regions.value.map((r) => ({
    label: r.displayName,
    value: r.code,
    countryCode: r.countryCode,
    flagEmoji: r.flagEmoji
  }))
)
function renderRegionLabel(o: any) {
  return h('div', { class: 'flex items-center gap-2' }, [
    h(RegionFlag, { code: o.countryCode, fallback: o.flagEmoji, size: 14 }),
    o.label
  ])
}

const ipTypeOptions = computed(() =>
  ipTypes.value.map((t) => ({
    label: `${IP_TYPE_CODE_LABELS[t.code] || t.name} (冷却 ${t.coolingMinutes}min)`,
    value: t.id
  }))
)

const form = reactive<IpPoolCoreUpdateDTO>({
  region: '',
  ipTypeId: '',
  ipAddress: '',
  provisionMode: 2,
  remark: ''
})

function fill(p: ResourceIpPool) {
  form.region = p.region ?? ''
  form.ipTypeId = p.ipTypeId ?? ''
  form.ipAddress = p.ipAddress ?? ''
  form.provisionMode = p.provisionMode ?? 2
  form.remark = p.remark ?? ''
}

onMounted(async () => {
  try { regions.value = await listEnabledRegions() } catch { /* */ }
  try { ipTypes.value = await listIpTypes() } catch { /* */ }
})

watch(() => [props.modelValue, props.ipPool?.id], async ([open]) => {
  if (!open) return
  Object.keys(errors).forEach((k) => delete errors[k])
  if (regions.value.length === 0) {
    try { regions.value = await listEnabledRegions() } catch { /* */ }
  }
  if (ipTypes.value.length === 0) {
    try { ipTypes.value = await listIpTypes() } catch { /* */ }
  }
  fill(props.ipPool)
}, { immediate: true })

function validate(): boolean {
  Object.keys(errors).forEach((k) => delete errors[k])
  if (!form.region) errors.region = '请选择区域'
  if (!form.ipTypeId) errors.ipTypeId = '请选择 IP 类型'
  if (!form.ipAddress?.trim()) errors.ipAddress = '请输入 IP 地址'
  if (form.provisionMode !== 1 && form.provisionMode !== 2) errors.provisionMode = '请选择部署模式'
  return Object.keys(errors).length === 0
}

async function onSubmit() {
  if (!validate()) return
  submitting.value = true
  try {
    await updateIpPoolCore(props.ipPool.id, {
      region: form.region,
      ipTypeId: form.ipTypeId,
      ipAddress: form.ipAddress.trim(),
      provisionMode: form.provisionMode,
      remark: form.remark?.trim() || undefined
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
    title="编辑核心信息"
    style="max-width: 36rem; width: 92vw"
    :bordered="false"
    @update:show="(v: boolean) => emit('update:modelValue', v)"
  >
    <NForm :model="form" label-placement="top" size="small">
      <NFormItem label="区域" required :feedback="errors.region" :validation-status="errors.region ? 'error' : undefined">
        <NSelect
          v-model:value="form.region"
          :options="regionOptions"
          :render-label="renderRegionLabel as any"
          filterable
        />
      </NFormItem>
      <NFormItem label="IP 类型" required :feedback="errors.ipTypeId" :validation-status="errors.ipTypeId ? 'error' : undefined">
        <NSelect v-model:value="form.ipTypeId" :options="ipTypeOptions" />
      </NFormItem>
      <NFormItem label="IP 地址 / 入网域名" required :feedback="errors.ipAddress" :validation-status="errors.ipAddress ? 'error' : undefined">
        <NInput v-model:value="form.ipAddress" :input-props="{ style: 'font-family: monospace' }" />
      </NFormItem>
      <NFormItem label="部署模式" required :feedback="errors.provisionMode" :validation-status="errors.provisionMode ? 'error' : undefined">
        <NRadioGroup v-model:value="form.provisionMode">
          <NRadio :value="1">自部署 (SSH + 一键装 dante)</NRadio>
          <NRadio :value="2">第三方 (仅记录现成 SOCKS5)</NRadio>
        </NRadioGroup>
      </NFormItem>
      <NFormItem label="备注">
        <NInput v-model:value="form.remark" type="textarea" :rows="2" />
      </NFormItem>
    </NForm>
    <template #footer>
      <NSpace justify="end">
        <NButton size="small" @click="emit('update:modelValue', false)">取消</NButton>
        <NButton type="primary" size="small" :loading="submitting" @click="onSubmit">保存</NButton>
      </NSpace>
    </template>
  </NModal>
</template>
