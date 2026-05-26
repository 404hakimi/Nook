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
  getServerLandingSocks5,
  updateServerLandingSocks5,
  type ServerLandingSocks5
} from '@/api/resource/server-landing'

/**
 * 编辑 SOCKS5 凭据 — 仅端口 + 用户 + 密码 3 项 (dante 运行期可热改, 客户端拨号用).
 *
 * 故意不编辑这些 (走"重装"流程, 而非这里):
 *   - 安装目录 / 日志路径 / sockd.conf 路径 等装机一次性路径
 *   - systemd 自启 / UFW 防火墙 (装机时的系统级开关, 改完不重装就漂移)
 *   - 日志级别 (dante log 关键字组合, admin 日常不动)
 */
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
const errors = reactive<Record<string, string>>({})

const form = reactive({
  socks5Port: 1080,
  socks5Username: '',
  socks5Password: ''
})

function fill(s: ServerLandingSocks5 | null) {
  form.socks5Port = s?.socks5Port ?? 1080
  form.socks5Username = s?.socks5Username ?? ''
  form.socks5Password = s?.socks5Password ?? ''
}

watch(() => [props.modelValue, props.serverId], async ([open]) => {
  if (!open) return
  Object.keys(errors).forEach((k) => delete errors[k])
  loading.value = true
  try {
    const s = await getServerLandingSocks5(props.serverId)
    fill(s)
  } catch { /* */ } finally {
    loading.value = false
  }
}, { immediate: true })

function validate(): boolean {
  Object.keys(errors).forEach((k) => delete errors[k])
  if (!form.socks5Port || form.socks5Port < 1 || form.socks5Port > 65535) {
    errors.socks5Port = '端口 1-65535'
  }
  if (!form.socks5Password?.trim()) {
    errors.socks5Password = '密码必填'
  }
  return Object.keys(errors).length === 0
}

async function onSubmit() {
  if (!validate()) return
  submitting.value = true
  try {
    // 仅传 3 个 socks5 字段; 后端 MyBatis-Plus update-strategy=NOT_NULL 会忽略未填字段,
    // 不影响 logLevel / logPath / autostartEnabled / firewallEnabled / installDir 原值
    await updateServerLandingSocks5(props.serverId, {
      socks5Port: form.socks5Port,
      socks5Username: form.socks5Username?.trim() || undefined,
      socks5Password: form.socks5Password.trim()
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
    title="编辑 SOCKS5 凭据"
    style="max-width: 32rem; width: 92vw"
    :bordered="false"
    @update:show="(v: boolean) => emit('update:modelValue', v)"
  >
    <NSpin :show="loading">
      <NAlert type="info" :show-icon="false" size="small" class="mb-3">
        保存后需走 "重装" 在远端生效.
      </NAlert>
      <NForm :model="form" label-placement="top" size="small">
        <NFormItem
          label="SOCKS5 端口"
          required
          :feedback="errors.socks5Port"
          :validation-status="errors.socks5Port ? 'error' : undefined"
        >
          <NInputNumber v-model:value="form.socks5Port" :min="1" :max="65535" class="w-full" />
        </NFormItem>
        <NFormItem label="SOCKS5 用户">
          <NInput v-model:value="form.socks5Username" />
        </NFormItem>
        <NFormItem
          label="SOCKS5 密码"
          required
          :feedback="errors.socks5Password"
          :validation-status="errors.socks5Password ? 'error' : undefined"
        >
          <NInput
            v-model:value="form.socks5Password"
            :input-props="{ style: 'font-family: monospace', autocomplete: 'off' }"
          />
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
