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
const regionOptions = computed(() =>
  regions.value.map((r) => ({ label: `${r.flagEmoji ?? ''} ${r.displayName}`.trim(), value: r.code }))
)
const ipTypeOptions = computed(() => ipTypes.value.map((t) => ({ label: t.name, value: t.id })))

const form = reactive<TradePlanSaveDTO>({
  code: '', name: '', regionCode: '', ipTypeId: '',
  trafficGb: 100, bandwidthMbps: 50, periodDays: 30,
  price: 0, remark: undefined
})

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
        periodDays: props.plan.periodDays, price: props.plan.price, remark: props.plan.remark
      })
    } else {
      Object.assign(form, {
        id: undefined, code: '', name: '', regionCode: '', ipTypeId: '',
        trafficGb: 100, bandwidthMbps: 50, periodDays: 30, price: 0, remark: undefined
      })
    }
  }
)

function validate(): boolean {
  Object.keys(errors).forEach((k) => delete errors[k])
  if (!form.code.trim()) errors.code = '套餐码必填'
  if (!form.name.trim()) errors.name = '套餐名必填'
  if (!form.regionCode) errors.regionCode = '区域必填'
  if (!form.ipTypeId) errors.ipTypeId = 'IP 类型必填'
  if (!form.trafficGb || form.trafficGb < 1) errors.trafficGb = '流量至少 1GB'
  if (!form.bandwidthMbps || form.bandwidthMbps < 1) errors.bandwidthMbps = '带宽至少 1Mbps'
  if (!form.periodDays || form.periodDays < 1) errors.periodDays = '周期至少 1 天'
  if (form.price == null || form.price < 0) errors.price = '价格必填'
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
      message.success('已创建 (默认下架; 上架后下单自动匹配同区域可用落地机)')
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
    style="max-width: 33rem; width: 92vw"
    :bordered="false"
    :mask-closable="false"
    @update:show="(v: boolean) => emit('update:modelValue', v)"
  >
    <NForm label-placement="top" size="small">
      <!-- 基本信息: 标识字段整行 -->
      <div class="form-section">基本信息</div>
      <NFormItem label="套餐码" required :validation-status="errors.code ? 'error' : undefined" :feedback="errors.code">
        <NInput v-model:value="form.code" :disabled="isEdit" placeholder="jp_tyo_isp_100gb_monthly" />
      </NFormItem>
      <NFormItem label="套餐名" required :validation-status="errors.name ? 'error' : undefined" :feedback="errors.name">
        <NInput v-model:value="form.name" placeholder="日本东京 ISP 100GB 月付" />
      </NFormItem>

      <!-- 产品规格: 选择项两列, 数值规格三列 -->
      <div class="form-section">
        产品规格
        <span class="form-section-hint">区域 + IP 类型决定下单时匹配哪些落地机</span>
      </div>
      <div class="grid grid-cols-2 gap-x-3">
        <NFormItem label="区域" required :validation-status="errors.regionCode ? 'error' : undefined" :feedback="errors.regionCode">
          <NSelect v-model:value="form.regionCode" :options="regionOptions" :disabled="isEdit" filterable placeholder="选区域" />
        </NFormItem>
        <NFormItem label="IP 类型" required :validation-status="errors.ipTypeId ? 'error' : undefined" :feedback="errors.ipTypeId">
          <NSelect v-model:value="form.ipTypeId" :options="ipTypeOptions" :disabled="isEdit" placeholder="选 IP 类型" />
        </NFormItem>
      </div>
      <div class="grid grid-cols-3 gap-x-3">
        <NFormItem label="月流量" required :validation-status="errors.trafficGb ? 'error' : undefined" :feedback="errors.trafficGb">
          <NInputNumber v-model:value="form.trafficGb" :min="1" :show-button="false" :disabled="isEdit" class="w-full">
            <template #suffix>GB</template>
          </NInputNumber>
        </NFormItem>
        <NFormItem label="带宽" required :validation-status="errors.bandwidthMbps ? 'error' : undefined" :feedback="errors.bandwidthMbps">
          <NInputNumber v-model:value="form.bandwidthMbps" :min="1" :show-button="false" :disabled="isEdit" class="w-full">
            <template #suffix>Mbps</template>
          </NInputNumber>
        </NFormItem>
        <NFormItem label="周期" required :validation-status="errors.periodDays ? 'error' : undefined" :feedback="errors.periodDays">
          <NInputNumber v-model:value="form.periodDays" :min="1" :show-button="false" class="w-full">
            <template #suffix>天</template>
          </NInputNumber>
        </NFormItem>
      </div>

      <!-- 定价 + 备注: 同一行左右分布 -->
      <div class="form-section">定价</div>
      <div class="grid grid-cols-2 gap-x-3">
        <NFormItem label="售价" required :validation-status="errors.price ? 'error' : undefined" :feedback="errors.price">
          <NInputNumber v-model:value="form.price" :min="0" :precision="2" :show-button="false" class="w-full">
            <template #prefix>¥</template>
          </NInputNumber>
        </NFormItem>
      </div>

      <NFormItem label="备注">
        <NInput v-model:value="form.remark" type="textarea" :rows="2" placeholder="选填, 仅内部备注" />
      </NFormItem>

      <div v-if="isEdit" class="text-xs text-zinc-400 -mt-1">售价 / 周期可改 (仅影响展示与之后下单); 灰色为已售卖锁定字段 (区域/规格), 需调整请建新套餐 + 旧的下架</div>
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

<style scoped>
/* 分区标题: 单列表单的视觉分组, 比纯堆叠更像 VPS 面板配置 */
.form-section {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--nook-fg);
  margin: 4px 0 6px;
  padding-bottom: 5px;
  border-bottom: 1px solid rgba(127, 127, 127, 0.12);
}
.form-section:not(:first-child) {
  margin-top: 14px;
}
.form-section-hint {
  font-size: 11px;
  font-weight: 400;
  color: var(--nook-fg-faint);
}
</style>
