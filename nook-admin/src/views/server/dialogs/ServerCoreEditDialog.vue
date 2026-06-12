<script setup lang="ts">
import { computed, reactive, ref, watch, h } from 'vue'
import {
  NButton,
  NForm,
  NFormItem,
  NIcon,
  NInput,
  NModal,
  NSelect,
  NSpace,
  NTooltip,
  useMessage
} from 'naive-ui'
import { HelpCircle } from 'lucide-vue-next'
import {
  updateServerCore,
  type ResourceServer,
  type ResourceServerCoreUpdateDTO
} from '@/api/resource/server'
import { useRegionStore } from '@/stores/region'
import { storeToRefs } from 'pinia'
import RegionFlag from '@/components/RegionFlag.vue'

const props = defineProps<{
  modelValue: boolean
  server: ResourceServer
}>()
const emit = defineEmits<{
  'update:modelValue': [v: boolean]
  saved: []
}>()

const message = useMessage()
const submitting = ref(false)
const errors = reactive<Record<string, string>>({})

// 仅装机中 / 待上线可改区域; 上线后区域是套餐与机器的匹配依据, 锁定
const regionLocked = computed(() =>
  props.server?.lifecycleState !== 'INSTALLING' && props.server?.lifecycleState !== 'READY'
)

const regionStore = useRegionStore()
const { list: regions } = storeToRefs(regionStore)
const regionOptions = computed(() => regions.value.map((r) => ({
  label: r.displayName,
  value: r.code,
  countryCode: r.countryCode,
  flagEmoji: r.flagEmoji
})))
function renderRegionLabel(o: { label: string; countryCode?: string; flagEmoji?: string }) {
  if (!o.countryCode) return o.label
  return h('span', { style: 'display:flex; align-items:center; gap:6px;' }, [
    h(RegionFlag, { code: o.countryCode, fallback: o.flagEmoji, size: 14 }),
    o.label
  ])
}

const form = reactive<ResourceServerCoreUpdateDTO>({
  name: '',
  region: '',
  remark: ''
})

function fill(s: ResourceServer) {
  form.name = s.name
  form.region = s.region ?? ''
  form.remark = s.remark ?? ''
}

// 仅在 dialog 打开时拉字典 (走 store, 全局去重); 关闭状态 mount 不触发请求
watch(() => [props.modelValue, props.server?.id], async ([open]) => {
  if (!open) return
  Object.keys(errors).forEach((k) => delete errors[k])
  await regionStore.ensureLoaded()
  fill(props.server)
})

function validate(): boolean {
  Object.keys(errors).forEach((k) => delete errors[k])
  if (!form.name.trim()) errors.name = '请输入别名'
  if (!form.region) errors.region = '请选择区域'
  return Object.keys(errors).length === 0
}

async function onSubmit() {
  if (!validate()) return
  submitting.value = true
  try {
    await updateServerCore(props.server.id, {
      name: form.name.trim(),
      region: form.region,
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
      <NFormItem label="别名" required :feedback="errors.name" :validation-status="errors.name ? 'error' : undefined">
        <NInput v-model:value="form.name" />
      </NFormItem>
      <NFormItem required :feedback="errors.region" :validation-status="errors.region ? 'error' : undefined">
        <template #label>
          <span style="display:inline-flex;align-items:center;gap:4px">
            区域
            <NTooltip>
              <template #trigger>
                <NIcon :size="15" style="cursor:help;color:#999"><HelpCircle /></NIcon>
              </template>
              区域是套餐与线路机 / 落地机的匹配依据; 仅装机中 / 待上线可改, 服务器上线后锁定
            </NTooltip>
          </span>
        </template>
        <NSelect
          v-model:value="form.region"
          :options="regionOptions"
          :render-label="renderRegionLabel as any"
          :disabled="regionLocked"
          filterable
        />
      </NFormItem>
      <NFormItem label="备注">
        <NInput v-model:value="form.remark" type="textarea" :autosize="{ minRows: 2, maxRows: 4 }" />
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
