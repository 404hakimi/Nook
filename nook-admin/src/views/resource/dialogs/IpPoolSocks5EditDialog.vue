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
  NSelect,
  NSpace,
  NSpin,
  NSwitch,
  useMessage
} from 'naive-ui'
import {
  getIpPoolSocks5,
  updateIpPoolSocks5,
  type IpPoolSocks5,
  DANTE_LOG_LEVEL_OPTIONS,
  DANTE_LOG_LEVEL_DEFAULT
} from '@/api/resource/ip-pool'

const props = defineProps<{
  modelValue: boolean
  ipId: string
}>()
const emit = defineEmits<{
  'update:modelValue': [v: boolean]
  saved: []
}>()

const message = useMessage()
const submitting = ref(false)
const loading = ref(false)
const errors = reactive<Record<string, string>>({})

const form = reactive<IpPoolSocks5>({
  socks5Port: 1080,
  socks5Username: '',
  socks5Password: '',
  logLevel: DANTE_LOG_LEVEL_DEFAULT,
  logPath: '/home/socks5/logs/sockd.log',
  autostartEnabled: 1,
  firewallEnabled: 1,
  installDir: '/home/socks5'
})

function fill(s: IpPoolSocks5 | null) {
  if (!s) return
  form.socks5Port = s.socks5Port ?? 1080
  form.socks5Username = s.socks5Username ?? ''
  form.socks5Password = '' // 留空 = 保留原值
  form.logLevel = s.logLevel ?? DANTE_LOG_LEVEL_DEFAULT
  form.logPath = s.logPath ?? '/home/socks5/logs/sockd.log'
  form.autostartEnabled = s.autostartEnabled ?? 1
  form.firewallEnabled = s.firewallEnabled ?? 1
  form.installDir = s.installDir ?? '/home/socks5'
}

watch(() => [props.modelValue, props.ipId], async ([open]) => {
  if (!open) return
  Object.keys(errors).forEach((k) => delete errors[k])
  loading.value = true
  try {
    const s = await getIpPoolSocks5(props.ipId)
    fill(s)
  } catch { /* */ } finally {
    loading.value = false
  }
}, { immediate: true })

function validate(): boolean {
  Object.keys(errors).forEach((k) => delete errors[k])
  if (!form.socks5Port || form.socks5Port < 1 || form.socks5Port > 65535) errors.socks5Port = '端口 1-65535'
  if (!form.logPath?.trim()) errors.logPath = '日志路径必填'
  return Object.keys(errors).length === 0
}

async function onSubmit() {
  if (!validate()) return
  submitting.value = true
  try {
    await updateIpPoolSocks5(props.ipId, {
      socks5Port: form.socks5Port,
      socks5Username: form.socks5Username?.trim() || undefined,
      socks5Password: form.socks5Password?.trim() || undefined,
      logLevel: form.logLevel?.trim() || undefined,
      logPath: form.logPath.trim(),
      autostartEnabled: form.autostartEnabled ?? undefined,
      firewallEnabled: form.firewallEnabled ?? undefined,
      installDir: form.installDir?.trim() || undefined
    })
    message.success('已保存; 重装后远端生效')
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
    title="编辑 dante 配置"
    style="max-width: 44rem; width: 92vw"
    :bordered="false"
    @update:show="(v: boolean) => emit('update:modelValue', v)"
  >
    <NSpin :show="loading">
      <NAlert type="info" :show-icon="false" size="small" class="mb-3">
        配置保存后, 需走"重装"在远端生效 (装机脚本读 DB 重新生成 sockd.conf).
        密码留空 = 保留原值. 限速 / 流量上限在"容量"里改.
      </NAlert>
      <NForm :model="form" label-placement="top" size="small">
        <div class="text-xs text-zinc-500 mt-1 mb-1">SOCKS5 凭据</div>
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-x-4">
          <NFormItem label="SOCKS5 端口" required :feedback="errors.socks5Port" :validation-status="errors.socks5Port ? 'error' : undefined">
            <NInputNumber v-model:value="form.socks5Port" :min="1" :max="65535" class="w-full" />
          </NFormItem>
          <NFormItem label="SOCKS5 用户">
            <NInput v-model:value="form.socks5Username" />
          </NFormItem>
          <NFormItem label="SOCKS5 密码 (留空保留原值)">
            <NInput
              v-model:value="form.socks5Password"
              type="password"
              show-password-on="click"
              :input-props="{ autocomplete: 'new-password' }"
              placeholder="留空表示不修改"
            />
          </NFormItem>
        </div>

        <div class="text-xs text-zinc-500 mt-3 mb-1">日志 / 部署</div>
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-x-4">
          <NFormItem label="日志级别">
            <NSelect v-model:value="form.logLevel" :options="DANTE_LOG_LEVEL_OPTIONS as any" />
          </NFormItem>
          <NFormItem label="日志路径" required :feedback="errors.logPath" :validation-status="errors.logPath ? 'error' : undefined">
            <NInput v-model:value="form.logPath" :input-props="{ style: 'font-family: monospace' }" />
          </NFormItem>
          <NFormItem label="安装目录">
            <NInput v-model:value="form.installDir" :input-props="{ style: 'font-family: monospace' }" />
          </NFormItem>
          <NFormItem label="systemd 开机自启">
            <NSwitch :value="form.autostartEnabled === 1" @update:value="(v: boolean) => (form.autostartEnabled = v ? 1 : 0)" />
          </NFormItem>
          <NFormItem label="UFW 防火墙启用">
            <NSwitch :value="form.firewallEnabled === 1" @update:value="(v: boolean) => (form.firewallEnabled = v ? 1 : 0)" />
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
