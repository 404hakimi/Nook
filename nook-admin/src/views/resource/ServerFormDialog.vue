<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  NButton,
  NDatePicker,
  NForm,
  NFormItem,
  NInput,
  NInputNumber,
  NModal,
  NSelect,
  NSpace,
  NSpin,
  useMessage
} from 'naive-ui'
import {
  createServer,
  getServerDetail,
  SERVER_LIFECYCLE_LABELS,
  updateServer,
  type ResourceServer,
  type ResourceServerSaveDTO
} from '@/api/resource/server'
import { listEnabledRegions, type ResourceRegion } from '@/api/resource/region'

interface Props {
  modelValue: boolean
  mode: 'create' | 'edit'
  server?: ResourceServer | null
}
const props = defineProps<Props>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'saved'): void
}>()

const message = useMessage()
const submitting = ref(false)
const loadingDetail = ref(false)
const errors = reactive<Record<string, string>>({})

const LIFECYCLE_OPTIONS = (['INSTALLING', 'READY', 'LIVE', 'RETIRED'] as const).map((v) => ({
  label: SERVER_LIFECYCLE_LABELS[v],
  value: v
}))

const regions = ref<ResourceRegion[]>([])
const regionOptions = computed(() =>
  regions.value.map((r) => ({ label: `${r.flagEmoji || ''} ${r.displayName} (${r.code})`, value: r.code }))
)

// expiresAt 跟 NDatePicker (timestamp ms) 双向绑定; 提交前转 'yyyy-MM-dd'
const form = reactive({
  name: '',
  host: '',
  sshPort: 22 as number | null,
  sshUser: 'root',
  sshPassword: '',
  sshTimeoutSeconds: 30 as number | null,
  sshOpTimeoutSeconds: 30 as number | null,
  sshUploadTimeoutSeconds: 30 as number | null,
  installTimeoutSeconds: 600 as number | null,
  bandwidthMbps: 1000 as number | null,
  domain: '',
  cfZoneId: '',
  cfRecordId: '',
  costMonthlyUsd: null as number | null,
  billingCycleDay: null as number | null,
  expiresAtTs: null as number | null,
  maxConcurrentClients: 50 as number | null,
  idcProvider: '',
  region: '',
  lifecycleState: 'INSTALLING',
  remark: ''
})

const isEdit = computed(() => props.mode === 'edit')

function fill(s: ResourceServer) {
  form.name = s.name
  form.host = s.host
  form.sshPort = s.sshPort ?? 22
  form.sshUser = s.sshUser ?? 'root'
  form.sshPassword = s.sshPassword ?? ''
  form.sshTimeoutSeconds = s.sshTimeoutSeconds ?? 30
  form.sshOpTimeoutSeconds = s.sshOpTimeoutSeconds ?? 30
  form.sshUploadTimeoutSeconds = s.sshUploadTimeoutSeconds ?? 30
  form.installTimeoutSeconds = s.installTimeoutSeconds ?? 600
  form.bandwidthMbps = s.bandwidthMbps ?? 1000
  form.domain = s.domain ?? ''
  form.cfZoneId = s.cfZoneId ?? ''
  form.cfRecordId = s.cfRecordId ?? ''
  form.costMonthlyUsd = s.costMonthlyUsd ?? null
  form.billingCycleDay = s.billingCycleDay ?? null
  form.expiresAtTs = s.expiresAt ? new Date(s.expiresAt).getTime() : null
  form.maxConcurrentClients = s.maxConcurrentClients ?? 50
  form.idcProvider = s.idcProvider ?? ''
  form.region = s.region ?? ''
  form.lifecycleState = s.lifecycleState ?? 'INSTALLING'
  form.remark = s.remark ?? ''
}

function reset() {
  form.name = ''
  form.host = ''
  form.sshPort = 22
  form.sshUser = 'root'
  form.sshPassword = ''
  form.sshTimeoutSeconds = 30
  form.sshOpTimeoutSeconds = 30
  form.sshUploadTimeoutSeconds = 30
  form.installTimeoutSeconds = 600
  form.bandwidthMbps = 1000
  form.domain = ''
  form.cfZoneId = ''
  form.cfRecordId = ''
  form.costMonthlyUsd = null
  form.billingCycleDay = null
  form.expiresAtTs = null
  form.maxConcurrentClients = 50
  form.idcProvider = ''
  form.region = ''
  form.lifecycleState = 'INSTALLING'
  form.remark = ''
}

async function loadRegions() {
  try {
    regions.value = await listEnabledRegions()
  } catch {
    /* */
  }
}

onMounted(loadRegions)

watch(
  () => [props.modelValue, props.server, props.mode],
  async ([open]) => {
    if (!open) return
    Object.keys(errors).forEach((k) => delete errors[k])
    if (regions.value.length === 0) {
      await loadRegions()
    }
    if (props.mode === 'edit' && props.server) {
      const id = props.server.id
      fill(props.server)
      loadingDetail.value = true
      try {
        const fresh = await getServerDetail(id)
        if (props.modelValue && props.mode === 'edit' && props.server?.id === id) {
          fill(fresh)
        }
      } catch {
        /* */
      } finally {
        loadingDetail.value = false
      }
    } else {
      reset()
    }
  }
)

function validateRange(field: keyof typeof form, label: string, min: number, max: number) {
  const v = form[field] as number | null
  if (v == null || isNaN(v)) {
    errors[field as string] = `${label}不能为空`
    return
  }
  if (v < min || v > max) {
    errors[field as string] = `${label}需在 ${min}-${max} 之间`
  }
}

function validate(): boolean {
  Object.keys(errors).forEach((k) => delete errors[k])
  if (!form.name.trim()) errors.name = '请输入别名'
  if (!form.host.trim()) errors.host = '请输入主机'
  if (!form.region.trim()) errors.region = '请选择区域'

  validateRange('sshTimeoutSeconds', 'SSH 握手超时', 5, 300)
  validateRange('sshOpTimeoutSeconds', 'SSH 单条命令超时', 5, 300)
  validateRange('sshUploadTimeoutSeconds', 'SCP 上传超时', 5, 600)
  validateRange('installTimeoutSeconds', '安装超时', 60, 3600)

  if (form.maxConcurrentClients == null || form.maxConcurrentClients < 1 || form.maxConcurrentClients > 10000) {
    errors.maxConcurrentClients = '客户数上限 1-10000'
  }
  if (form.bandwidthMbps == null) errors.bandwidthMbps = '请填带宽峰值'
  if (form.billingCycleDay != null && (form.billingCycleDay < 1 || form.billingCycleDay > 28)) {
    errors.billingCycleDay = '账单日 1-28'
  }

  if (props.mode === 'create') {
    if (!form.sshPassword) errors.sshPassword = '请填 SSH 密码'
  }
  // LIVE 前置: domain 必填
  if (form.lifecycleState === 'LIVE' && !form.domain.trim()) {
    errors.domain = 'LIVE 状态要求 domain 必填'
  }
  return Object.keys(errors).length === 0
}

function tsToDateStr(ts: number | null): string | undefined {
  if (ts == null) return undefined
  const d = new Date(ts)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${dd}`
}

async function onSubmit() {
  if (!validate()) return
  submitting.value = true
  try {
    const dto: ResourceServerSaveDTO = {
      name: form.name.trim(),
      host: form.host.trim(),
      sshPort: form.sshPort ?? undefined,
      sshUser: form.sshUser.trim(),
      sshPassword: form.sshPassword || undefined,
      sshTimeoutSeconds: form.sshTimeoutSeconds ?? undefined,
      sshOpTimeoutSeconds: form.sshOpTimeoutSeconds ?? undefined,
      sshUploadTimeoutSeconds: form.sshUploadTimeoutSeconds ?? undefined,
      installTimeoutSeconds: form.installTimeoutSeconds ?? undefined,
      bandwidthMbps: form.bandwidthMbps ?? undefined,
      domain: form.domain.trim() || undefined,
      cfZoneId: form.cfZoneId.trim() || undefined,
      cfRecordId: form.cfRecordId.trim() || undefined,
      costMonthlyUsd: form.costMonthlyUsd ?? undefined,
      billingCycleDay: form.billingCycleDay ?? undefined,
      expiresAt: tsToDateStr(form.expiresAtTs),
      maxConcurrentClients: form.maxConcurrentClients ?? undefined,
      idcProvider: form.idcProvider.trim() || undefined,
      region: form.region.trim() || undefined,
      lifecycleState: form.lifecycleState,
      remark: form.remark.trim() || undefined
    }
    if (props.mode === 'create') {
      await createServer(dto)
      message.success('创建成功')
    } else {
      await updateServer(props.server!.id, dto)
      message.success('更新成功')
    }
    emit('saved')
    emit('update:modelValue', false)
  } catch {
    /* */
  } finally {
    submitting.value = false
  }
}

function close() {
  emit('update:modelValue', false)
}
</script>

<template>
  <NModal
    :show="modelValue"
    preset="card"
    :title="mode === 'create' ? '新增服务器' : '编辑服务器'"
    style="max-width: 64rem; width: 92vw"
    :bordered="false"
    :mask-closable="false"
    :close-on-esc="false"
    @update:show="(v: boolean) => emit('update:modelValue', v)"
  >
    <NSpin :show="loadingDetail">
      <NForm
        :model="form"
        label-placement="top"
        require-mark-placement="right-hanging"
        size="small"
      >
        <div class="text-sm font-semibold text-zinc-500 mb-2">基本信息</div>
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-x-4">
          <NFormItem
            label="别名"
            required
            :validation-status="errors.name ? 'error' : undefined"
            :feedback="errors.name"
          >
            <NInput v-model:value="form.name" placeholder="如 jp-tyo-rn-01" />
          </NFormItem>
          <NFormItem
            label="主机/管理 IP"
            required
            :validation-status="errors.host ? 'error' : undefined"
            :feedback="errors.host"
          >
            <NInput
              v-model:value="form.host"
              placeholder="x.x.x.x 或 host.example.com"
              :input-props="{ style: 'font-family: monospace' }"
            />
          </NFormItem>
          <NFormItem
            label="区域"
            required
            :validation-status="errors.region ? 'error' : undefined"
            :feedback="errors.region"
          >
            <NSelect
              v-model:value="form.region"
              :options="regionOptions"
              placeholder="选择区域字典"
              filterable
            />
          </NFormItem>
          <NFormItem label="IDC 供应商">
            <NInput
              v-model:value="form.idcProvider"
              placeholder="racknerd / hosthatch / dmit"
            />
          </NFormItem>
          <NFormItem
            label="生命周期"
            required
          >
            <NSelect v-model:value="form.lifecycleState" :options="LIFECYCLE_OPTIONS" />
          </NFormItem>
          <NFormItem
            label="线路机域名 (LIVE 前置必填)"
            :validation-status="errors.domain ? 'error' : undefined"
            :feedback="errors.domain"
          >
            <NInput
              v-model:value="form.domain"
              placeholder="jp-01.nook.com"
              :input-props="{ style: 'font-family: monospace' }"
            />
          </NFormItem>
        </div>

        <div class="text-sm font-semibold text-zinc-500 mt-4 mb-2">容量 / 账面</div>
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-x-4">
          <NFormItem
            label="带宽峰值 (Mbps)"
            required
            :validation-status="errors.bandwidthMbps ? 'error' : undefined"
            :feedback="errors.bandwidthMbps"
          >
            <NInputNumber v-model:value="form.bandwidthMbps" :min="0" class="w-full" />
          </NFormItem>
          <NFormItem
            label="客户数上限"
            required
            :validation-status="errors.maxConcurrentClients ? 'error' : undefined"
            :feedback="errors.maxConcurrentClients"
          >
            <template #label>
              <span>客户数上限</span>
              <span class="text-xs text-zinc-400 ml-2">1C1G=50-100 / 2C2G=200 / 4C4G=500</span>
            </template>
            <NInputNumber
              v-model:value="form.maxConcurrentClients"
              :min="1"
              :max="10000"
              class="w-full"
            />
          </NFormItem>
          <NFormItem label="月度成本 USD">
            <NInputNumber v-model:value="form.costMonthlyUsd" :min="0" :precision="2" class="w-full" />
          </NFormItem>
          <NFormItem
            label="账单日 (1-28)"
            :validation-status="errors.billingCycleDay ? 'error' : undefined"
            :feedback="errors.billingCycleDay"
          >
            <NInputNumber v-model:value="form.billingCycleDay" :min="1" :max="28" class="w-full" />
          </NFormItem>
          <NFormItem label="服务器到期日">
            <NDatePicker
              v-model:value="form.expiresAtTs"
              type="date"
              clearable
              class="w-full"
            />
          </NFormItem>
        </div>

        <div class="text-sm font-semibold text-zinc-500 mt-4 mb-2">Cloudflare</div>
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-x-4">
          <NFormItem label="Cloudflare Zone ID">
            <NInput v-model:value="form.cfZoneId" :input-props="{ style: 'font-family: monospace' }" />
          </NFormItem>
          <NFormItem label="Cloudflare DNS Record ID">
            <NInput v-model:value="form.cfRecordId" :input-props="{ style: 'font-family: monospace' }" />
          </NFormItem>
        </div>

        <div class="text-sm font-semibold text-zinc-500 mt-4 mb-2">SSH 凭据</div>
        <div class="grid grid-cols-1 sm:grid-cols-3 gap-x-4">
          <NFormItem label="SSH 端口">
            <NInputNumber v-model:value="form.sshPort" :min="1" :max="65535" class="w-full" />
          </NFormItem>
          <NFormItem label="SSH 用户">
            <NInput v-model:value="form.sshUser" />
          </NFormItem>
          <div class="hidden sm:block"></div>
          <div class="sm:col-span-3">
            <NFormItem
              label="SSH 密码"
              :validation-status="errors.sshPassword ? 'error' : undefined"
              :feedback="errors.sshPassword"
            >
              <NInput
                v-model:value="form.sshPassword"
                type="password"
                show-password-on="click"
                :input-props="{ autocomplete: 'new-password' }"
                :placeholder="isEdit ? '留空表示不修改' : '必填'"
              />
            </NFormItem>
          </div>
        </div>

        <div class="text-sm font-semibold text-zinc-500 mt-4 mb-2">超时配置</div>
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-x-4">
          <NFormItem
            required
            :validation-status="errors.sshTimeoutSeconds ? 'error' : undefined"
            :feedback="errors.sshTimeoutSeconds"
          >
            <template #label>
              <span>SSH 握手超时 (秒)</span>
              <span class="text-xs text-zinc-400 ml-2">5-300, 默认 30</span>
            </template>
            <NInputNumber v-model:value="form.sshTimeoutSeconds" :min="5" :max="300" class="w-full" />
          </NFormItem>
          <NFormItem
            required
            :validation-status="errors.sshOpTimeoutSeconds ? 'error' : undefined"
            :feedback="errors.sshOpTimeoutSeconds"
          >
            <template #label>
              <span>SSH 单条命令超时 (秒)</span>
              <span class="text-xs text-zinc-400 ml-2">xray api / journalctl</span>
            </template>
            <NInputNumber v-model:value="form.sshOpTimeoutSeconds" :min="5" :max="300" class="w-full" />
          </NFormItem>
          <NFormItem
            required
            :validation-status="errors.sshUploadTimeoutSeconds ? 'error' : undefined"
            :feedback="errors.sshUploadTimeoutSeconds"
          >
            <template #label>
              <span>SCP 上传超时 (秒)</span>
              <span class="text-xs text-zinc-400 ml-2">脚本/模板上传, 5-600</span>
            </template>
            <NInputNumber v-model:value="form.sshUploadTimeoutSeconds" :min="5" :max="600" class="w-full" />
          </NFormItem>
          <NFormItem
            required
            :validation-status="errors.installTimeoutSeconds ? 'error' : undefined"
            :feedback="errors.installTimeoutSeconds"
          >
            <template #label>
              <span>安装超时 (秒)</span>
              <span class="text-xs text-zinc-400 ml-2">一次部署上限, 60-3600</span>
            </template>
            <NInputNumber v-model:value="form.installTimeoutSeconds" :min="60" :max="3600" class="w-full" />
          </NFormItem>
        </div>

        <NFormItem label="备注">
          <NInput v-model:value="form.remark" type="textarea" :autosize="{ minRows: 2, maxRows: 4 }" />
        </NFormItem>
      </NForm>
    </NSpin>

    <template #footer>
      <NSpace justify="end">
        <NButton size="small" @click="close">取消</NButton>
        <NButton
          type="primary"
          size="small"
          :loading="submitting"
          :disabled="loadingDetail"
          @click="onSubmit"
        >
          确定
        </NButton>
      </NSpace>
    </template>
  </NModal>
</template>
