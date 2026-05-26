<script setup lang="ts">
import { computed, reactive, ref, watch, h } from 'vue'
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
  totalIpCount: 0,
  remark: ''
})

function fill(s: ResourceServer) {
  form.name = s.name
  form.region = s.region ?? ''
  form.totalIpCount = s.totalIpCount ?? 0
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
      totalIpCount: form.totalIpCount ?? 0,
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
      <NFormItem label="区域" required :feedback="errors.region" :validation-status="errors.region ? 'error' : undefined">
        <NSelect
          v-model:value="form.region"
          :options="regionOptions"
          :render-label="renderRegionLabel as any"
          filterable
        />
      </NFormItem>
      <NFormItem label="IP 总数">
        <NInputNumber :value="form.totalIpCount" disabled class="w-full" />
        <template #feedback>
          <span class="text-zinc-400 text-xs">由 xray 落地 IP 统计带回 (待接入), 编辑不可改</span>
        </template>
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
