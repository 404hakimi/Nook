<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import {
  NAlert,
  NButton,
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
  getIpPoolCredential,
  updateIpPoolCredential,
  type IpPoolCredential
} from '@/api/resource/ip-pool'

const props = defineProps<{
  modelValue: boolean
  ipId: string
  /** ip_pool.ip_address (空 sshHost 时的兜底显示用). */
  ipAddress?: string
}>()
const emit = defineEmits<{
  'update:modelValue': [v: boolean]
  saved: []
}>()

const message = useMessage()
const submitting = ref(false)
const loading = ref(false)
const errors = reactive<Record<string, string>>({})

const form = reactive<IpPoolCredential>({
  sshHost: '',
  sshPort: 22,
  sshUser: 'root',
  sshPassword: ''
})

function fill(c: IpPoolCredential | null) {
  if (!c) return
  form.sshHost = c.sshHost ?? ''
  form.sshPort = c.sshPort ?? 22
  form.sshUser = c.sshUser ?? 'root'
  form.sshPassword = '' // 留空表示不修改
}

watch(() => [props.modelValue, props.ipId], async ([open]) => {
  if (!open) return
  Object.keys(errors).forEach((k) => delete errors[k])
  loading.value = true
  try {
    const c = await getIpPoolCredential(props.ipId)
    fill(c)
  } catch { /* */ } finally {
    loading.value = false
  }
}, { immediate: true })

function validate(): boolean {
  Object.keys(errors).forEach((k) => delete errors[k])
  if (form.sshPort != null && (form.sshPort < 1 || form.sshPort > 65535)) errors.sshPort = '端口 1-65535'
  return Object.keys(errors).length === 0
}

async function onSubmit() {
  if (!validate()) return
  submitting.value = true
  try {
    await updateIpPoolCredential(props.ipId, {
      sshHost: form.sshHost?.trim() || undefined,
      sshPort: form.sshPort ?? undefined,
      sshUser: form.sshUser?.trim() || undefined,
      sshPassword: form.sshPassword?.trim() || undefined
    })
    message.success('已保存; 下次 SSH 走新凭据')
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
    title="编辑 SSH 凭据"
    style="max-width: 40rem; width: 92vw"
    :bordered="false"
    @update:show="(v: boolean) => emit('update:modelValue', v)"
  >
    <NSpin :show="loading">
      <NAlert type="info" :show-icon="false" size="small" class="mb-3">
        SSH 凭据用于落地机 dante 装机 + 后续运维 (改配置 / 切自启 / 看日志). 密码留空 = 保留原值.
        sshHost 留空时, 走 ip_address ({{ ipAddress || '-' }}) 兜底.
      </NAlert>
      <NForm :model="form" label-placement="top" size="small">
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-x-4">
          <NFormItem label="SSH 主机 (留空 = ip_address)">
            <NInput v-model:value="form.sshHost" :input-props="{ style: 'font-family: monospace' }" />
          </NFormItem>
          <NFormItem label="SSH 端口 (留空 = 22)" :feedback="errors.sshPort" :validation-status="errors.sshPort ? 'error' : undefined">
            <NInputNumber v-model:value="form.sshPort" :min="1" :max="65535" class="w-full" />
          </NFormItem>
          <NFormItem label="SSH 用户 (留空 = root)">
            <NInput v-model:value="form.sshUser" />
          </NFormItem>
          <NFormItem label="SSH 密码 (留空保留原值)">
            <NInput
              v-model:value="form.sshPassword"
              type="password"
              show-password-on="click"
              :input-props="{ autocomplete: 'new-password' }"
              placeholder="留空表示不修改"
            />
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
