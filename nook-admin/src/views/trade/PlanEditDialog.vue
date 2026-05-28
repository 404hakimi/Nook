<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import {
  NButton,
  NForm,
  NFormItem,
  NInput,
  NInputNumber,
  NModal,
  NSelect,
  NSpace,
  useMessage
} from 'naive-ui'
import { storeToRefs } from 'pinia'
import { useRegionStore } from '@/stores/region'
import { useIpTypeStore } from '@/stores/ipType'
import { createTradePlan, updateTradePlan, type TradePlan, type TradePlanSaveDTO } from '@/api/trade/plan'

const props = defineProps<{ modelValue: boolean; plan?: TradePlan | null }>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'saved'): void
}>()

const message = useMessage()
const regionStore = useRegionStore()
const ipTypeStore = useIpTypeStore()
const { list: regions } = storeToRefs(regionStore)
const { list: ipTypes } = storeToRefs(ipTypeStore)
const submitting = ref(false)
const errors = reactive<Record<string, string>>({})

const isEdit = computed(() => !!props.plan?.id)

const form = reactive<TradePlanSaveDTO>({
  code: '', name: '', regionCode: '', ipTypeId: undefined,
  trafficGb: 100, bandwidthMbps: undefined, periodDays: 30, limitIp: 0,
  priceCny: 0, costBasisCny: undefined, remark: undefined
})

const regionOptions = computed(() =>
  regions.value.map((r) => ({ label: `${r.flagEmoji ?? ''} ${r.displayName}`.trim(), value: r.code }))
)
const ipTypeOptions = computed(() => ipTypes.value.map((t) => ({ label: t.name, value: t.id })))

watch(
  () => props.modelValue,
  async (open) => {
    if (!open) return
    Object.keys(errors).forEach((k) => delete errors[k])
    await Promise.all([regionStore.ensureLoaded(), ipTypeStore.ensureLoaded()])
    if (props.plan) {
      Object.assign(form, {
        id: props.plan.id, code: props.plan.code, name: props.plan.name,
        regionCode: props.plan.regionCode, ipTypeId: props.plan.ipTypeId,
        trafficGb: props.plan.trafficGb, bandwidthMbps: props.plan.bandwidthMbps,
        periodDays: props.plan.periodDays, limitIp: props.plan.limitIp,
        priceCny: props.plan.priceCny, costBasisCny: props.plan.costBasisCny, remark: props.plan.remark
      })
    } else {
      Object.assign(form, {
        id: undefined, code: '', name: '', regionCode: '', ipTypeId: undefined,
        trafficGb: 100, bandwidthMbps: undefined, periodDays: 30, limitIp: 0,
        priceCny: 0, costBasisCny: undefined, remark: undefined
      })
    }
  }
)

function validate(): boolean {
  Object.keys(errors).forEach((k) => delete errors[k])
  if (!form.code.trim()) errors.code = '套餐码必填'
  if (!form.name.trim()) errors.name = '套餐名必填'
  if (!form.regionCode) errors.regionCode = '区域必填'
  if (!form.trafficGb || form.trafficGb < 1) errors.trafficGb = '流量至少 1GB'
  if (!form.periodDays || form.periodDays < 1) errors.periodDays = '周期至少 1 天'
  if (form.priceCny == null || form.priceCny < 0) errors.priceCny = '价格必填'
  return Object.keys(errors).length === 0
}

async function onSubmit() {
  if (!validate()) return
  submitting.value = true
  try {
    if (isEdit.value) {
      await updateTradePlan(form)
      message.success('已保存')
    } else {
      await createTradePlan(form)
      message.success('已创建 (默认下架, 绑定资源后再上架)')
    }
    emit('saved')
    emit('update:modelValue', false)
  } catch {
    /* request 拦截器 toast */
  } finally {
    submitting.value = false
  }
}

function close() {
  emit('update:modelValue', false)
}
</script>

<template>
  <NModal
    :show="modelValue"
    preset="card"
    :title="isEdit ? '编辑套餐' : '新建套餐'"
    style="max-width: 44rem; width: 92vw"
    :bordered="false"
    :mask-closable="false"
    @update:show="(v: boolean) => emit('update:modelValue', v)"
  >
    <NForm label-placement="top" size="small">
      <div class="grid grid-cols-2 gap-x-4">
        <NFormItem label="套餐码" required :validation-status="errors.code ? 'error' : undefined" :feedback="errors.code">
          <NInput v-model:value="form.code" :disabled="isEdit" placeholder="jp_tyo_isp_100gb_monthly" />
        </NFormItem>
        <NFormItem label="套餐名" required :validation-status="errors.name ? 'error' : undefined" :feedback="errors.name">
          <NInput v-model:value="form.name" placeholder="日本东京 ISP 100GB 月付" />
        </NFormItem>
        <NFormItem label="区域" required :validation-status="errors.regionCode ? 'error' : undefined" :feedback="errors.regionCode">
          <NSelect v-model:value="form.regionCode" :options="regionOptions" :disabled="isEdit" filterable placeholder="选区域" />
        </NFormItem>
        <NFormItem label="IP 类型 (展示分类)">
          <NSelect v-model:value="form.ipTypeId" :options="ipTypeOptions" :disabled="isEdit" clearable placeholder="选 IP 类型" />
        </NFormItem>
        <NFormItem label="月流量 (GB)" required :validation-status="errors.trafficGb ? 'error' : undefined" :feedback="errors.trafficGb">
          <NInputNumber v-model:value="form.trafficGb" :min="1" :disabled="isEdit" class="w-full" />
        </NFormItem>
        <NFormItem label="周期 (天)" required :validation-status="errors.periodDays ? 'error' : undefined" :feedback="errors.periodDays">
          <NInputNumber v-model:value="form.periodDays" :min="1" :disabled="isEdit" class="w-full" />
        </NFormItem>
        <NFormItem label="同时连接 IP 数 (0=不限)">
          <NInputNumber v-model:value="form.limitIp" :min="0" :disabled="isEdit" class="w-full" />
        </NFormItem>
        <NFormItem label="售价 (CNY)" required :validation-status="errors.priceCny ? 'error' : undefined" :feedback="errors.priceCny">
          <NInputNumber v-model:value="form.priceCny" :min="0" :precision="2" :disabled="isEdit" class="w-full" />
        </NFormItem>
        <NFormItem label="账面带宽 (Mbps, 仅展示)">
          <NInputNumber v-model:value="form.bandwidthMbps" :min="0" class="w-full" />
        </NFormItem>
        <NFormItem label="成本 (CNY, 内部)">
          <NInputNumber v-model:value="form.costBasisCny" :min="0" :precision="2" class="w-full" />
        </NFormItem>
        <div class="col-span-2">
          <NFormItem label="备注">
            <NInput v-model:value="form.remark" type="textarea" :rows="2" />
          </NFormItem>
        </div>
      </div>
      <div v-if="isEdit" class="text-xs text-zinc-400">灰色为已售卖不可变字段; 需调整请建新套餐 + 旧的下架</div>
    </NForm>
    <template #footer>
      <NSpace justify="end">
        <NButton size="small" @click="close">取消</NButton>
        <NButton type="primary" size="small" :loading="submitting" @click="onSubmit">
          {{ isEdit ? '保存' : '创建' }}
        </NButton>
      </NSpace>
    </template>
  </NModal>
</template>
