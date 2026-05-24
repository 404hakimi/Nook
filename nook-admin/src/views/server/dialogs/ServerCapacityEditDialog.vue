<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import {
  NAlert,
  NButton,
  NForm,
  NFormItem,
  NInputNumber,
  NModal,
  NSpace,
  NSpin,
  useMessage
} from 'naive-ui'
import {
  getServerCapacity,
  updateServerCapacity,
  type ServerCapacity
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

const form = reactive({
  monthlyTrafficGb: null as number | null,
  bandwidthLimitMbps: null as number | null,
  clientMaxCount: null as number | null
})

function fill(c: ServerCapacity | null) {
  form.monthlyTrafficGb = c?.monthlyTrafficGb ?? null
  form.bandwidthLimitMbps = c?.bandwidthLimitMbps ?? null
  form.clientMaxCount = c?.clientMaxCount ?? null
}

watch(() => [props.modelValue, props.serverId], async ([open]) => {
  if (!open) return
  loading.value = true
  try {
    const c = await getServerCapacity(props.serverId)
    fill(c)
  } catch { /* */ } finally {
    loading.value = false
  }
})

async function onSubmit() {
  submitting.value = true
  try {
    await updateServerCapacity(props.serverId, {
      monthlyTrafficGb: form.monthlyTrafficGb ?? 0,
      bandwidthLimitMbps: form.bandwidthLimitMbps ?? 0,
      clientMaxCount: form.clientMaxCount ?? 0
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
    title="编辑容量与流量阈值"
    style="max-width: 36rem; width: 92vw"
    :bordered="false"
    @update:show="(v: boolean) => emit('update:modelValue', v)"
  >
    <NSpin :show="loading">
      <NAlert type="info" :show-icon="false" size="small" class="mb-3">
        业务阈值: <strong>限定带宽</strong> 由 agent tc qdisc 真实 enforce; <strong>月流量阈值</strong> 是 throttle 状态机 90% 触发的基数; <strong>客户数上限</strong> 是 allocator 选 server 时的硬上限. 0 = 不限.
      </NAlert>
      <NForm :model="form" label-placement="top" size="small">
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-x-4">
          <NFormItem label="限定带宽 Mbps (0=不限)">
            <NInputNumber v-model:value="form.bandwidthLimitMbps" :min="0" :max="100000" class="w-full" />
          </NFormItem>
          <NFormItem label="月流量阈值 GB (0=不限)">
            <NInputNumber v-model:value="form.monthlyTrafficGb" :min="0" :max="1000000" class="w-full" />
          </NFormItem>
          <NFormItem label="客户数上限 (0=不限)">
            <NInputNumber v-model:value="form.clientMaxCount" :min="0" :max="100000" class="w-full" />
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
