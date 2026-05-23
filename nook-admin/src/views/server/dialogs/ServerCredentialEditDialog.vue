<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
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
  getServerCredential,
  updateServerCredential,
  type ServerCredential
} from '@/api/resource/server'

const props = defineProps<{
  modelValue: boolean
  serverId: string
  /** 用于判 LIVE 后 host/port 锁定. */
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
const liveLocked = computed(() => props.lifecycleState === 'LIVE')

const form = reactive<ServerCredential>({
  host: '',
  sshPort: 22,
  sshUser: 'root',
  sshPassword: '',
  sshTimeoutSeconds: 30,
  sshOpTimeoutSeconds: 30,
  sshUploadTimeoutSeconds: 60,
  installTimeoutSeconds: 600
})

function fill(c: ServerCredential | null) {
  if (!c) return
  form.host = c.host ?? ''
  form.sshPort = c.sshPort ?? 22
  form.sshUser = c.sshUser ?? 'root'
  form.sshPassword = '' // 留空表示不修改
  form.sshTimeoutSeconds = c.sshTimeoutSeconds ?? 30
  form.sshOpTimeoutSeconds = c.sshOpTimeoutSeconds ?? 30
  form.sshUploadTimeoutSeconds = c.sshUploadTimeoutSeconds ?? 60
  form.installTimeoutSeconds = c.installTimeoutSeconds ?? 600
}

watch(() => [props.modelValue, props.serverId], async ([open]) => {
  if (!open) return
  Object.keys(errors).forEach((k) => delete errors[k])
  loading.value = true
  try {
    const c = await getServerCredential(props.serverId)
    fill(c)
  } catch { /* */ } finally {
    loading.value = false
  }
})

function validate(): boolean {
  Object.keys(errors).forEach((k) => delete errors[k])
  if (!form.host?.trim()) errors.host = '主机不能空'
  if (!form.sshPort || form.sshPort < 1 || form.sshPort > 65535) errors.sshPort = '端口 1-65535'
  if (!form.sshUser?.trim()) errors.sshUser = 'SSH 用户不能空'
  if (!form.sshTimeoutSeconds || form.sshTimeoutSeconds < 5 || form.sshTimeoutSeconds > 300) errors.sshTimeoutSeconds = '握手超时 5-300'
  if (!form.sshOpTimeoutSeconds || form.sshOpTimeoutSeconds < 5 || form.sshOpTimeoutSeconds > 300) errors.sshOpTimeoutSeconds = '单命令超时 5-300'
  if (!form.sshUploadTimeoutSeconds || form.sshUploadTimeoutSeconds < 5 || form.sshUploadTimeoutSeconds > 600) errors.sshUploadTimeoutSeconds = 'SCP 超时 5-600'
  if (!form.installTimeoutSeconds || form.installTimeoutSeconds < 60 || form.installTimeoutSeconds > 3600) errors.installTimeoutSeconds = '装机超时 60-3600'
  return Object.keys(errors).length === 0
}

async function onSubmit() {
  if (!validate()) return
  submitting.value = true
  try {
    await updateServerCredential(props.serverId, {
      ...form,
      sshPassword: form.sshPassword?.trim() || undefined,
      host: form.host.trim(),
      sshUser: form.sshUser?.trim()
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
      <NAlert v-if="liveLocked" type="warning" :show-icon="false" size="small" class="mb-3">
        服务器处于 LIVE 状态, host / SSH 端口已硬锁; 如需修改请先退到 READY (会断 agent 心跳).
      </NAlert>
      <NAlert v-else type="info" :show-icon="false" size="small" class="mb-3">
        修改 SSH 凭据会使现有 agent 连接断开, 下次握手走新凭据. 密码留空 = 保留原值.
      </NAlert>
      <NForm :model="form" label-placement="top" size="small">
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-x-4">
          <NFormItem label="Host / IP" required :feedback="errors.host" :validation-status="errors.host ? 'error' : undefined">
            <NInput v-model:value="form.host" :disabled="liveLocked" :input-props="{ style: 'font-family: monospace' }" />
          </NFormItem>
          <NFormItem label="SSH 端口" required :feedback="errors.sshPort" :validation-status="errors.sshPort ? 'error' : undefined">
            <NInputNumber v-model:value="form.sshPort" :min="1" :max="65535" :disabled="liveLocked" class="w-full" />
          </NFormItem>
          <NFormItem label="SSH 用户" required :feedback="errors.sshUser" :validation-status="errors.sshUser ? 'error' : undefined">
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
        <div class="text-xs text-zinc-500 mt-2 mb-1">超时参数</div>
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-x-4">
          <NFormItem label="握手超时 (秒)" required :feedback="errors.sshTimeoutSeconds" :validation-status="errors.sshTimeoutSeconds ? 'error' : undefined">
            <NInputNumber v-model:value="form.sshTimeoutSeconds" :min="5" :max="300" class="w-full" />
          </NFormItem>
          <NFormItem label="单命令超时 (秒)" required :feedback="errors.sshOpTimeoutSeconds" :validation-status="errors.sshOpTimeoutSeconds ? 'error' : undefined">
            <NInputNumber v-model:value="form.sshOpTimeoutSeconds" :min="5" :max="300" class="w-full" />
          </NFormItem>
          <NFormItem label="SCP 上传超时 (秒)" required :feedback="errors.sshUploadTimeoutSeconds" :validation-status="errors.sshUploadTimeoutSeconds ? 'error' : undefined">
            <NInputNumber v-model:value="form.sshUploadTimeoutSeconds" :min="5" :max="600" class="w-full" />
          </NFormItem>
          <NFormItem label="装机整体超时 (秒)" required :feedback="errors.installTimeoutSeconds" :validation-status="errors.installTimeoutSeconds ? 'error' : undefined">
            <NInputNumber v-model:value="form.installTimeoutSeconds" :min="60" :max="3600" class="w-full" />
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
