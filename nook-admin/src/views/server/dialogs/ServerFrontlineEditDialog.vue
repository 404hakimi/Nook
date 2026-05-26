<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import {
  NAlert,
  NButton,
  NForm,
  NFormItem,
  NInput,
  NModal,
  NSpace,
  NSpin,
  useMessage
} from 'naive-ui'
import {
  getServerFrontline,
  updateServerFrontline,
  type ServerFrontline
} from '@/api/resource/server'

const props = defineProps<{
  modelValue: boolean
  serverId: string
  lifecycleState?: string
}>()
const emit = defineEmits<{
  'update:modelValue': [v: boolean]
  saved: []
}>()

const message = useMessage()
const submitting = ref(false)
const loading = ref(false)
const errors = reactive<Record<string, string>>({})

const form = reactive<ServerFrontline>({
  domain: '',
  cfZoneId: '',
  cfRecordId: ''
})

function fill(d: ServerFrontline | null) {
  form.domain = d?.domain ?? ''
  form.cfZoneId = d?.cfZoneId ?? ''
  form.cfRecordId = d?.cfRecordId ?? ''
}

watch(() => [props.modelValue, props.serverId], async ([open]) => {
  if (!open) return
  Object.keys(errors).forEach((k) => delete errors[k])
  loading.value = true
  try {
    const d = await getServerFrontline(props.serverId)
    fill(d)
  } catch { /* */ } finally {
    loading.value = false
  }
})

async function onSubmit() {
  submitting.value = true
  try {
    await updateServerFrontline(props.serverId, {
      domain: form.domain?.trim() || undefined,
      cfZoneId: form.cfZoneId?.trim() || undefined,
      cfRecordId: form.cfRecordId?.trim() || undefined
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
    title="编辑线路机扩展"
    style="max-width: 36rem; width: 92vw"
    :bordered="false"
    @update:show="(v: boolean) => emit('update:modelValue', v)"
  >
    <NSpin :show="loading">
      <NAlert v-if="lifecycleState === 'LIVE'" type="warning" :show-icon="false" size="small" class="mb-3">
        服务器处于 LIVE 状态, 修改 domain 会导致用户连接断开; 改前请确认。
      </NAlert>
      <NAlert v-else-if="!form.domain" type="info" :show-icon="false" size="small" class="mb-3">
        LIVE 上线前需填 domain (用户连接的子域名).
      </NAlert>
      <NForm :model="form" label-placement="top" size="small">
        <NFormItem label="线路机域名">
          <NInput v-model:value="form.domain" placeholder="jp-01.nook.com" :input-props="{ style: 'font-family: monospace' }" />
        </NFormItem>
        <NFormItem label="Cloudflare Zone ID">
          <NInput v-model:value="form.cfZoneId" :input-props="{ style: 'font-family: monospace' }" />
        </NFormItem>
        <NFormItem label="Cloudflare Record ID">
          <NInput v-model:value="form.cfRecordId" :input-props="{ style: 'font-family: monospace' }" />
        </NFormItem>
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
