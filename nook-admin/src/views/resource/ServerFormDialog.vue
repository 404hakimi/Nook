<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import {
  NButton,
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
  updateServer,
  type ResourceServer,
  type ResourceServerSaveDTO
} from '@/api/resource/server'

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

const STATUS_OPTIONS = [
  { label: '运行', value: 1 },
  { label: '维护', value: 2 },
  { label: '下线', value: 3 }
]

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
  totalBandwidth: 1000 as number | null,
  monthlyTrafficGb: null as number | null,
  idcProvider: '',
  region: '',
  status: 1,
  remark: ''
})

const isEdit = computed(() => props.mode === 'edit')

function fill(s: ResourceServer) {
  form.name = s.name
  form.host = s.host
  form.sshPort = s.sshPort ?? 22
  form.sshUser = s.sshUser ?? 'root'
  // 接口下发明文凭据, 直接 fill 进密码框 (UI 遮盖); 不改就保留, 改了就覆盖
  form.sshPassword = s.sshPassword ?? ''
  form.sshTimeoutSeconds = s.sshTimeoutSeconds ?? 30
  form.sshOpTimeoutSeconds = s.sshOpTimeoutSeconds ?? 30
  form.sshUploadTimeoutSeconds = s.sshUploadTimeoutSeconds ?? 30
  form.installTimeoutSeconds = s.installTimeoutSeconds ?? 600
  form.totalBandwidth = s.totalBandwidth ?? 1000
  form.monthlyTrafficGb = s.monthlyTrafficGb ?? null
  form.idcProvider = s.idcProvider ?? ''
  form.region = s.region ?? ''
  form.status = s.status
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
  form.totalBandwidth = 1000
  form.monthlyTrafficGb = null
  form.idcProvider = ''
  form.region = ''
  form.status = 1
  form.remark = ''
}

watch(
  () => [props.modelValue, props.server, props.mode],
  async ([open]) => {
    if (!open) return
    Object.keys(errors).forEach((k) => delete errors[k])
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

  validateRange('sshTimeoutSeconds', 'SSH 握手超时', 5, 300)
  validateRange('sshOpTimeoutSeconds', 'SSH 单条命令超时', 5, 300)
  validateRange('sshUploadTimeoutSeconds', 'SCP 上传超时', 5, 600)
  validateRange('installTimeoutSeconds', '安装超时', 60, 3600)

  if (props.mode === 'create') {
    if (!form.sshPassword) errors.sshPassword = '请填 SSH 密码'
  }
  return Object.keys(errors).length === 0
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
      totalBandwidth: form.totalBandwidth ?? undefined,
      monthlyTrafficGb: form.monthlyTrafficGb ?? undefined,
      idcProvider: form.idcProvider.trim() || undefined,
      region: form.region.trim() || undefined,
      status: form.status,
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
    style="max-width: 48rem"
    :bordered="false"
    :mask-closable="false"
    @update:show="(v: boolean) => emit('update:modelValue', v)"
  >
    <NSpin :show="loadingDetail">
      <NForm
        :model="form"
        label-placement="top"
        require-mark-placement="right-hanging"
        size="small"
      >
        <!-- 基本信息 -->
        <div class="text-sm font-semibold text-zinc-500 mb-2">基本信息</div>
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-x-4">
          <NFormItem
            label="别名"
            required
            :validation-status="errors.name ? 'error' : undefined"
            :feedback="errors.name"
          >
            <NInput v-model:value="form.name" placeholder="如 us-west-rn-01" />
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
          <NFormItem label="区域">
            <NInput v-model:value="form.region" placeholder="us-west / jp / ..." />
          </NFormItem>
          <NFormItem label="IDC 供应商">
            <NInput
              v-model:value="form.idcProvider"
              placeholder="racknerd / hosthatch / dmit"
            />
          </NFormItem>
          <NFormItem>
            <template #label>
              <span>带宽峰值 (Mbps)</span>
              <span class="text-xs text-zinc-400 ml-2">速率</span>
            </template>
            <NInputNumber
              v-model:value="form.totalBandwidth"
              :min="0"
              class="w-full"
            />
          </NFormItem>
          <NFormItem>
            <template #label>
              <span>月流量额度 (GB)</span>
              <span class="text-xs text-zinc-400 ml-2">不限留空</span>
            </template>
            <NInputNumber
              v-model:value="form.monthlyTrafficGb"
              :min="0"
              placeholder="例 1000 = 1TB/月"
              class="w-full"
            />
          </NFormItem>
          <NFormItem label="状态">
            <NSelect v-model:value="form.status" :options="STATUS_OPTIONS" />
          </NFormItem>
        </div>

        <!-- SSH -->
        <div class="text-sm font-semibold text-zinc-500 mt-4 mb-2">SSH 凭据</div>
        <div class="grid grid-cols-1 sm:grid-cols-3 gap-x-4">
          <NFormItem label="SSH 端口">
            <NInputNumber
              v-model:value="form.sshPort"
              :min="1"
              :max="65535"
              class="w-full"
            />
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

        <div class="text-sm font-semibold text-zinc-500 mt-4 mb-2">
          超时配置
        </div>
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
            <NInputNumber
              v-model:value="form.sshTimeoutSeconds"
              :min="5"
              :max="300"
              class="w-full"
            />
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
            <NInputNumber
              v-model:value="form.sshOpTimeoutSeconds"
              :min="5"
              :max="300"
              class="w-full"
            />
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
            <NInputNumber
              v-model:value="form.sshUploadTimeoutSeconds"
              :min="5"
              :max="600"
              class="w-full"
            />
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
            <NInputNumber
              v-model:value="form.installTimeoutSeconds"
              :min="60"
              :max="3600"
              class="w-full"
            />
          </NFormItem>
        </div>

        <NFormItem label="备注">
          <NInput
            v-model:value="form.remark"
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 4 }"
          />
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
