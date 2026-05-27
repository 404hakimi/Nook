<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import {
  NAlert,
  NButton,
  NForm,
  NFormItem,
  NIcon,
  NInput,
  NInputGroup,
  NInputNumber,
  NModal,
  NSpace,
  NSpin,
  NTooltip,
  useMessage
} from 'naive-ui'
import { Dices } from 'lucide-vue-next'
import {
  getServerLandingSocks5,
  updateServerLandingSocks5,
  type ServerLandingSocks5
} from '@/api/resource/server-landing'

/**
 * 编辑 SOCKS5 凭据 — 仅端口 + 用户 + 密码 3 项 (dante 运行期可热改, 客户端拨号用).
 *
 * 端口 / 用户名 / 密码 三处提供"随机"按钮 (crypto.getRandomValues 本地生成).
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

function randomPort(): number {
  return Math.floor(Math.random() * (60000 - 20000 + 1)) + 20000
}
function randomAlnum(len: number, mixedCase: boolean): string {
  const lower = 'abcdefghijklmnopqrstuvwxyz'
  const upper = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'
  const digit = '0123456789'
  const charset = (mixedCase ? lower + upper : lower) + digit
  const buf = new Uint32Array(len)
  crypto.getRandomValues(buf)
  let out = ''
  for (let i = 0; i < len; i++) out += charset[buf[i] % charset.length]
  return out
}
function randomUsername(): string {
  const lower = 'abcdefghijklmnopqrstuvwxyz'
  const buf = new Uint8Array(1)
  crypto.getRandomValues(buf)
  return lower[buf[0] % lower.length] + randomAlnum(7, false)
}

function onRandomPort() { form.socks5Port = randomPort() }
function onRandomUsername() { form.socks5Username = randomUsername() }
function onRandomPassword() { form.socks5Password = randomAlnum(16, true) }

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
          <NInputGroup>
            <NInputNumber v-model:value="form.socks5Port" :min="1" :max="65535" class="flex-1" />
            <NTooltip>
              <template #trigger>
                <NButton @click="onRandomPort" :title="'随机 20000-60000'">
                  <NIcon><Dices /></NIcon>
                </NButton>
              </template>
              随机生成 (20000-60000)
            </NTooltip>
          </NInputGroup>
        </NFormItem>
        <NFormItem label="SOCKS5 用户">
          <NInputGroup>
            <NInput v-model:value="form.socks5Username" />
            <NTooltip>
              <template #trigger>
                <NButton @click="onRandomUsername">
                  <NIcon><Dices /></NIcon>
                </NButton>
              </template>
              随机生成 (8 字符 a-z0-9)
            </NTooltip>
          </NInputGroup>
        </NFormItem>
        <NFormItem
          label="SOCKS5 密码"
          required
          :feedback="errors.socks5Password"
          :validation-status="errors.socks5Password ? 'error' : undefined"
        >
          <NInputGroup>
            <NInput
              v-model:value="form.socks5Password"
              :input-props="{ style: 'font-family: monospace', autocomplete: 'off' }"
            />
            <NTooltip>
              <template #trigger>
                <NButton @click="onRandomPassword">
                  <NIcon><Dices /></NIcon>
                </NButton>
              </template>
              随机生成 (16 字符 大小写+数字)
            </NTooltip>
          </NInputGroup>
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
