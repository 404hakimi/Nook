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
  sshPrivateKey: '',
  sshTimeoutSeconds: 30 as number | null,
  backendTimeoutSeconds: 20 as number | null,
  xrayGrpcHost: '127.0.0.1',
  xrayGrpcPort: 62789 as number | null,
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
  form.sshPrivateKey = s.sshPrivateKey ?? ''
  form.sshTimeoutSeconds = s.sshTimeoutSeconds ?? 30
  form.backendTimeoutSeconds = s.backendTimeoutSeconds ?? 20
  form.xrayGrpcHost = s.xrayGrpcHost ?? '127.0.0.1'
  form.xrayGrpcPort = s.xrayGrpcPort ?? 62789
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
  form.sshPrivateKey = ''
  form.sshTimeoutSeconds = 30
  form.backendTimeoutSeconds = 20
  form.xrayGrpcHost = '127.0.0.1'
  form.xrayGrpcPort = 62789
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

function validate(): boolean {
  Object.keys(errors).forEach((k) => delete errors[k])
  if (!form.name.trim()) errors.name = '请输入别名'
  if (!form.host.trim()) errors.host = '请输入主机'

  if (form.sshTimeoutSeconds == null || isNaN(form.sshTimeoutSeconds as number)) {
    errors.sshTimeoutSeconds = 'SSH 超时不能为空'
  } else if ((form.sshTimeoutSeconds as number) < 5 || (form.sshTimeoutSeconds as number) > 300) {
    errors.sshTimeoutSeconds = 'SSH 超时需在 5-300 秒之间'
  }
  if (form.backendTimeoutSeconds == null || isNaN(form.backendTimeoutSeconds as number)) {
    errors.backendTimeoutSeconds = 'Backend 超时不能为空'
  } else if (
    (form.backendTimeoutSeconds as number) < 5 ||
    (form.backendTimeoutSeconds as number) > 120
  ) {
    errors.backendTimeoutSeconds = 'Backend 超时需在 5-120 秒之间'
  }

  if (props.mode === 'create') {
    if (!form.sshPassword && !form.sshPrivateKey) {
      errors.sshAuth = '请填 SSH 密码或私钥之一'
    }
    if (!form.xrayGrpcHost.trim()) errors.xrayGrpcHost = 'gRPC 主机必填'
    if (!form.xrayGrpcPort) errors.xrayGrpcPort = 'gRPC 端口必填'
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
      sshPrivateKey: form.sshPrivateKey || undefined,
      sshTimeoutSeconds: form.sshTimeoutSeconds ?? undefined,
      backendTimeoutSeconds: form.backendTimeoutSeconds ?? undefined,
      xrayGrpcHost: form.xrayGrpcHost.trim() || undefined,
      xrayGrpcPort: form.xrayGrpcPort ?? undefined,
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
          <NFormItem
            required
            :validation-status="errors.sshTimeoutSeconds ? 'error' : undefined"
            :feedback="errors.sshTimeoutSeconds"
          >
            <template #label>
              <span>SSH 超时 (秒)</span>
              <span class="text-xs text-zinc-400 ml-2">5-300, 建议 30</span>
            </template>
            <NInputNumber
              v-model:value="form.sshTimeoutSeconds"
              :min="5"
              :max="300"
              class="w-full"
            />
          </NFormItem>
          <div class="sm:col-span-3">
            <NFormItem label="SSH 密码">
              <NInput
                v-model:value="form.sshPassword"
                type="password"
                show-password-on="click"
                :input-props="{ autocomplete: 'new-password' }"
              />
            </NFormItem>
          </div>
          <div class="sm:col-span-3">
            <NFormItem label="SSH 私钥 (PEM)">
              <NInput
                v-model:value="form.sshPrivateKey"
                type="password"
                show-password-on="click"
                placeholder="-----BEGIN OPENSSH PRIVATE KEY-----..."
                :input-props="{
                  autocomplete: 'new-password',
                  style: 'font-family: monospace'
                }"
              />
            </NFormItem>
          </div>
          <div v-if="errors.sshAuth" class="sm:col-span-3 text-xs mb-3" style="color: var(--n-error-color)">
            {{ errors.sshAuth }}
          </div>
        </div>

        <!-- Xray gRPC -->
        <div class="text-sm font-semibold text-zinc-500 mt-4 mb-2">Xray gRPC 配置</div>
        <p class="text-xs text-zinc-500 mb-2">
          nook 通过 SSH 隧道转发 gRPC, 所以 host 通常填 <code>127.0.0.1</code>(Xray
          监听本地). 端口由部署脚本决定, 默认 62789.
        </p>
        <div class="grid grid-cols-1 sm:grid-cols-3 gap-x-4">
          <NFormItem
            label="gRPC 主机"
            required
            :validation-status="errors.xrayGrpcHost ? 'error' : undefined"
            :feedback="errors.xrayGrpcHost"
          >
            <NInput
              v-model:value="form.xrayGrpcHost"
              :input-props="{ style: 'font-family: monospace' }"
            />
          </NFormItem>
          <NFormItem
            label="gRPC 端口"
            required
            :validation-status="errors.xrayGrpcPort ? 'error' : undefined"
            :feedback="errors.xrayGrpcPort"
          >
            <NInputNumber
              v-model:value="form.xrayGrpcPort"
              :min="1"
              :max="65535"
              class="w-full"
            />
          </NFormItem>
          <NFormItem
            required
            :validation-status="errors.backendTimeoutSeconds ? 'error' : undefined"
            :feedback="errors.backendTimeoutSeconds"
          >
            <template #label>
              <span>Backend 超时 (秒)</span>
              <span class="text-xs text-zinc-400 ml-2">5-120, 建议 20</span>
            </template>
            <NInputNumber
              v-model:value="form.backendTimeoutSeconds"
              :min="5"
              :max="120"
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
