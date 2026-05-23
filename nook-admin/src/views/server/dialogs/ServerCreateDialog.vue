<script setup lang="ts">
import { computed, h, onMounted, reactive, ref, watch } from 'vue'
import {
  NButton,
  NCollapseTransition,
  NForm,
  NFormItem,
  NIcon,
  NInput,
  NInputNumber,
  NModal,
  NSelect,
  NSpace,
  useMessage
} from 'naive-ui'
import { ChevronDown, ChevronRight } from 'lucide-vue-next'
import { createServer, type ResourceServerCreateDTO } from '@/api/resource/server'
import { listEnabledRegions, type ResourceRegion } from '@/api/resource/region'
import RegionFlag from '@/components/RegionFlag.vue'

const props = defineProps<{
  modelValue: boolean
}>()
const emit = defineEmits<{
  'update:modelValue': [v: boolean]
  created: [serverId: string]
}>()

const message = useMessage()
const submitting = ref(false)
const errors = reactive<Record<string, string>>({})

const regions = ref<ResourceRegion[]>([])
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

const DEFAULTS = {
  sshPort: 22,
  sshUser: 'root',
  sshTimeoutSeconds: 30,
  sshOpTimeoutSeconds: 60,
  sshUploadTimeoutSeconds: 120,
  installTimeoutSeconds: 900
}

function freshForm(): ResourceServerCreateDTO {
  return {
    name: '',
    region: '',
    remark: '',
    lifecycleState: 'INSTALLING',
    credential: {
      host: '',
      sshPort: DEFAULTS.sshPort,
      sshUser: DEFAULTS.sshUser,
      sshPassword: '',
      sshTimeoutSeconds: DEFAULTS.sshTimeoutSeconds,
      sshOpTimeoutSeconds: DEFAULTS.sshOpTimeoutSeconds,
      sshUploadTimeoutSeconds: DEFAULTS.sshUploadTimeoutSeconds,
      installTimeoutSeconds: DEFAULTS.installTimeoutSeconds
    }
  }
}
const form = reactive<ResourceServerCreateDTO>(freshForm())
const advancedOpen = ref(false)

onMounted(async () => {
  try { regions.value = await listEnabledRegions() } catch { /* */ }
})

watch(() => props.modelValue, async (open) => {
  if (!open) return
  Object.keys(errors).forEach((k) => delete errors[k])
  Object.assign(form, freshForm())
  advancedOpen.value = false
  if (regions.value.length === 0) {
    try { regions.value = await listEnabledRegions() } catch { /* */ }
  }
})

function validate(): boolean {
  Object.keys(errors).forEach((k) => delete errors[k])
  if (!form.name.trim()) errors.name = '请输入别名'
  if (!form.region) errors.region = '请选择区域'
  const c = form.credential
  if (!c.host?.trim()) errors.host = '请输入 IP / 主机'
  if (c.sshPort == null || c.sshPort < 1 || c.sshPort > 65535) errors.sshPort = '端口 1-65535'
  if (!c.sshUser?.trim()) errors.sshUser = '请输入 SSH 用户'
  if (!c.sshPassword?.trim()) errors.sshPassword = '请输入 SSH 密码 (装机需要)'
  return Object.keys(errors).length === 0
}

async function onSubmit() {
  if (!validate()) return
  submitting.value = true
  try {
    const created = await createServer({
      name: form.name.trim(),
      region: form.region,
      remark: form.remark?.trim() || undefined,
      lifecycleState: 'INSTALLING',
      credential: {
        host: form.credential.host.trim(),
        sshPort: form.credential.sshPort,
        sshUser: form.credential.sshUser?.trim(),
        sshPassword: form.credential.sshPassword?.trim(),
        sshTimeoutSeconds: form.credential.sshTimeoutSeconds,
        sshOpTimeoutSeconds: form.credential.sshOpTimeoutSeconds,
        sshUploadTimeoutSeconds: form.credential.sshUploadTimeoutSeconds,
        installTimeoutSeconds: form.credential.installTimeoutSeconds
      }
    })
    message.success(`已创建 (id: ${created.id.slice(0, 8)}...) — 进详情页继续装 Agent / Xray`)
    emit('created', created.id)
    emit('update:modelValue', false)
  } catch { /* request 拦截器已 toast */ } finally {
    submitting.value = false
  }
}
</script>

<template>
  <NModal
    :show="modelValue"
    preset="card"
    title="新建 Server"
    style="max-width: 40rem; width: 92vw"
    :bordered="false"
    @update:show="(v: boolean) => emit('update:modelValue', v)"
  >
    <NForm :model="form" label-placement="top" size="small">
      <div class="section-title">基础信息</div>

      <NFormItem label="别名" required :feedback="errors.name" :validation-status="errors.name ? 'error' : undefined">
        <NInput v-model:value="form.name" placeholder="fra-test-001" />
      </NFormItem>

      <NFormItem label="区域" required :feedback="errors.region" :validation-status="errors.region ? 'error' : undefined">
        <NSelect
          v-model:value="form.region"
          :options="regionOptions"
          :render-label="renderRegionLabel as any"
          placeholder="选择部署区域"
          filterable
        />
      </NFormItem>

      <NFormItem label="备注">
        <NInput
          v-model:value="form.remark"
          type="textarea"
          placeholder="可空"
          :autosize="{ minRows: 1, maxRows: 3 }"
        />
      </NFormItem>

      <div class="section-title mt-3">SSH 凭据 <span class="hint">(装机需要)</span></div>

      <div class="grid grid-cols-3 gap-3">
        <NFormItem
          label="IP / 主机"
          required
          :feedback="errors.host"
          :validation-status="errors.host ? 'error' : undefined"
          class="col-span-2"
        >
          <NInput
            v-model:value="form.credential.host"
            placeholder="64.118.158.12 或 host.example.com"
            :input-props="{ style: 'font-family: monospace' }"
          />
        </NFormItem>

        <NFormItem label="端口" required :feedback="errors.sshPort" :validation-status="errors.sshPort ? 'error' : undefined">
          <NInputNumber v-model:value="form.credential.sshPort" :min="1" :max="65535" class="w-full" />
        </NFormItem>
      </div>

      <div class="grid grid-cols-2 gap-3">
        <NFormItem label="SSH 用户" required :feedback="errors.sshUser" :validation-status="errors.sshUser ? 'error' : undefined">
          <NInput v-model:value="form.credential.sshUser" :input-props="{ style: 'font-family: monospace' }" />
        </NFormItem>

        <NFormItem
          label="SSH 密码"
          required
          :feedback="errors.sshPassword"
          :validation-status="errors.sshPassword ? 'error' : undefined"
        >
          <NInput
            v-model:value="form.credential.sshPassword"
            type="password"
            show-password-on="click"
            :input-props="{ style: 'font-family: monospace', autocomplete: 'new-password' }"
          />
        </NFormItem>
      </div>

      <div class="advanced-toggle" @click="advancedOpen = !advancedOpen">
        <NIcon :size="14"><ChevronDown v-if="advancedOpen" /><ChevronRight v-else /></NIcon>
        <span>高级 (超时配置)</span>
      </div>

      <NCollapseTransition :show="advancedOpen">
        <div class="grid grid-cols-2 gap-3 mt-2">
          <NFormItem label="SSH 握手超时 (秒)">
            <NInputNumber v-model:value="form.credential.sshTimeoutSeconds" :min="5" :max="300" class="w-full" />
          </NFormItem>
          <NFormItem label="SSH 命令超时 (秒)">
            <NInputNumber v-model:value="form.credential.sshOpTimeoutSeconds" :min="5" :max="300" class="w-full" />
          </NFormItem>
          <NFormItem label="SCP 上传超时 (秒)">
            <NInputNumber v-model:value="form.credential.sshUploadTimeoutSeconds" :min="5" :max="600" class="w-full" />
          </NFormItem>
          <NFormItem label="安装超时 (秒)">
            <NInputNumber v-model:value="form.credential.installTimeoutSeconds" :min="60" :max="3600" class="w-full" />
          </NFormItem>
        </div>
      </NCollapseTransition>
    </NForm>

    <template #footer>
      <NSpace justify="end">
        <NButton size="small" @click="emit('update:modelValue', false)">取消</NButton>
        <NButton type="primary" size="small" :loading="submitting" @click="onSubmit">创建</NButton>
      </NSpace>
    </template>
  </NModal>
</template>

<style scoped>
.section-title {
  font-size: 12px;
  font-weight: 600;
  color: #71717a;
  margin-bottom: 8px;
  padding-bottom: 4px;
  border-bottom: 1px dashed rgba(127, 127, 127, 0.18);
}
.section-title .hint {
  font-weight: 400;
  color: #a1a1aa;
  margin-left: 4px;
}
.advanced-toggle {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #71717a;
  cursor: pointer;
  user-select: none;
  margin-top: 4px;
}
.advanced-toggle:hover { color: #6366f1; }
</style>
