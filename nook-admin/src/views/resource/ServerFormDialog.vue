<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useToast } from '@/composables/useToast'
import {
  createServer,
  getServerDetail,
  updateServer,
  type ResourceServer,
  type ResourceServerSaveDTO
} from '@/api/resource/server'
import Select from '@/components/Select.vue'

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

const toast = useToast()
const submitting = ref(false)
const loadingDetail = ref(false)
const errors = reactive<Record<string, string>>({})

const BACKEND_OPTIONS = [
  { label: '3x-ui 面板', value: 'threexui' },
  { label: 'Xray gRPC', value: 'xray-grpc' }
]
const STATUS_OPTIONS = [
  { label: '运行', value: 1 },
  { label: '维护', value: 2 },
  { label: '下线', value: 3 }
]
const TLS_OPTIONS = [
  { label: '正常校验', value: 0 },
  { label: '跳过校验(自签证书)', value: 1 }
]

const form = reactive({
  name: '',
  host: '',
  sshPort: 22,
  sshUser: 'root',
  sshPassword: '',
  sshPrivateKey: '',
  sshTimeoutSeconds: 30,
  backendType: 'threexui',
  panelBaseUrl: '',
  panelUsername: 'admin',
  panelPassword: '',
  panelIgnoreTls: 0,
  backendTimeoutSeconds: 20,
  xrayGrpcHost: '',
  xrayGrpcPort: undefined as number | undefined,
  totalBandwidth: 1000,
  monthlyTrafficGb: undefined as number | undefined,
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
  form.sshPassword = ''
  form.sshPrivateKey = ''
  form.sshTimeoutSeconds = s.sshTimeoutSeconds ?? 30
  form.backendType = s.backendType
  form.panelBaseUrl = s.panelBaseUrl ?? ''
  form.panelUsername = s.panelUsername ?? 'admin'
  form.panelPassword = ''
  form.panelIgnoreTls = s.panelIgnoreTls ?? 0
  form.backendTimeoutSeconds = s.backendTimeoutSeconds ?? 20
  form.xrayGrpcHost = s.xrayGrpcHost ?? ''
  form.xrayGrpcPort = s.xrayGrpcPort
  form.totalBandwidth = s.totalBandwidth ?? 1000
  form.monthlyTrafficGb = s.monthlyTrafficGb
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
  form.backendType = 'threexui'
  form.panelBaseUrl = ''
  form.panelUsername = 'admin'
  form.panelPassword = ''
  form.panelIgnoreTls = 0
  form.backendTimeoutSeconds = 20
  form.xrayGrpcHost = ''
  form.xrayGrpcPort = undefined
  form.totalBandwidth = 1000
  form.monthlyTrafficGb = undefined
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
  if (!form.backendType) errors.backendType = '请选择 backend 类型'

  // 超时校验对所有模式都执行（编辑时也不能传非法值）
  if (form.sshTimeoutSeconds == null || isNaN(form.sshTimeoutSeconds)) {
    errors.sshTimeoutSeconds = 'SSH 超时不能为空'
  } else if (form.sshTimeoutSeconds < 5 || form.sshTimeoutSeconds > 300) {
    errors.sshTimeoutSeconds = 'SSH 超时需在 5-300 秒之间'
  }
  if (form.backendTimeoutSeconds == null || isNaN(form.backendTimeoutSeconds)) {
    errors.backendTimeoutSeconds = 'Backend 超时不能为空'
  } else if (form.backendTimeoutSeconds < 5 || form.backendTimeoutSeconds > 120) {
    errors.backendTimeoutSeconds = 'Backend 超时需在 5-120 秒之间'
  }

  if (props.mode === 'create') {
    if (!form.sshPassword && !form.sshPrivateKey) {
      errors.sshAuth = '请填 SSH 密码或私钥之一'
    }
    if (form.backendType === 'threexui') {
      if (!form.panelBaseUrl.trim()) errors.panelBaseUrl = '面板入口必填'
      if (!form.panelUsername.trim()) errors.panelUsername = '面板用户名必填'
      if (!form.panelPassword) errors.panelPassword = '面板密码必填'
    } else if (form.backendType === 'xray-grpc') {
      if (!form.xrayGrpcHost.trim()) errors.xrayGrpcHost = 'gRPC 主机必填'
      if (!form.xrayGrpcPort) errors.xrayGrpcPort = 'gRPC 端口必填'
    }
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
      sshPort: form.sshPort,
      sshUser: form.sshUser.trim(),
      // 密码字段：留空表示保留旧值；非空则覆盖
      sshPassword: form.sshPassword || undefined,
      sshPrivateKey: form.sshPrivateKey || undefined,
      sshTimeoutSeconds: form.sshTimeoutSeconds,
      backendType: form.backendType,
      panelBaseUrl: form.panelBaseUrl.trim() || undefined,
      panelUsername: form.panelUsername.trim() || undefined,
      panelPassword: form.panelPassword || undefined,
      panelIgnoreTls: form.panelIgnoreTls,
      backendTimeoutSeconds: form.backendTimeoutSeconds,
      xrayGrpcHost: form.xrayGrpcHost.trim() || undefined,
      xrayGrpcPort: form.xrayGrpcPort,
      totalBandwidth: form.totalBandwidth,
      monthlyTrafficGb: form.monthlyTrafficGb,
      idcProvider: form.idcProvider.trim() || undefined,
      region: form.region.trim() || undefined,
      status: form.status,
      remark: form.remark.trim() || undefined
    }
    if (props.mode === 'create') {
      await createServer(dto)
      toast.success('创建成功')
    } else {
      await updateServer(props.server!.id, dto)
      toast.success('更新成功')
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
  <dialog class="modal" :class="{ 'modal-open': modelValue }">
    <div class="modal-box max-w-3xl relative">
      <h3 class="text-lg font-semibold mb-4">
        {{ mode === 'create' ? '新增服务器' : '编辑服务器' }}
      </h3>

      <div
        v-if="loadingDetail"
        class="absolute inset-0 bg-base-100/70 flex items-center justify-center z-20 rounded-2xl"
      >
        <span class="loading loading-spinner loading-md text-primary"></span>
      </div>

      <!-- 基本信息 -->
      <div class="text-sm font-semibold text-base-content/70 mb-2">基本信息</div>
      <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div>
          <label class="label py-1"><span class="label-text">别名 <span class="text-error">*</span></span></label>
          <input
            v-model="form.name"
            type="text"
            placeholder="如 us-west-rn-01"
            class="input input-bordered input-sm w-full"
            :class="{ 'input-error': errors.name }"
          />
          <div v-if="errors.name" class="text-error text-xs mt-1">{{ errors.name }}</div>
        </div>
        <div>
          <label class="label py-1"><span class="label-text">主机/管理 IP <span class="text-error">*</span></span></label>
          <input
            v-model="form.host"
            type="text"
            placeholder="x.x.x.x 或 host.example.com"
            class="input input-bordered input-sm w-full font-mono"
            :class="{ 'input-error': errors.host }"
          />
          <div v-if="errors.host" class="text-error text-xs mt-1">{{ errors.host }}</div>
        </div>
        <div>
          <label class="label py-1"><span class="label-text">区域</span></label>
          <input v-model="form.region" type="text" placeholder="us-west / jp / ..." class="input input-bordered input-sm w-full" />
        </div>
        <div>
          <label class="label py-1"><span class="label-text">IDC 供应商</span></label>
          <input v-model="form.idcProvider" type="text" placeholder="racknerd / hosthatch / dmit" class="input input-bordered input-sm w-full" />
        </div>
        <div>
          <label class="label py-1">
            <span class="label-text">带宽峰值 (Mbps)</span>
            <span class="label-text-alt text-base-content/50">速率</span>
          </label>
          <input v-model.number="form.totalBandwidth" type="number" min="0" class="input input-bordered input-sm w-full" />
        </div>
        <div>
          <label class="label py-1">
            <span class="label-text">月流量额度 (GB)</span>
            <span class="label-text-alt text-base-content/50">不限留空</span>
          </label>
          <input
            v-model.number="form.monthlyTrafficGb"
            type="number"
            min="0"
            placeholder="例 1000 = 1TB/月"
            class="input input-bordered input-sm w-full"
          />
        </div>
        <div>
          <label class="label py-1"><span class="label-text">状态</span></label>
          <Select v-model="form.status" :options="STATUS_OPTIONS" />
        </div>
      </div>

      <!-- SSH -->
      <div class="text-sm font-semibold text-base-content/70 mt-6 mb-2">SSH 凭据</div>
      <div class="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div>
          <label class="label py-1"><span class="label-text">SSH 端口</span></label>
          <input v-model.number="form.sshPort" type="number" min="1" max="65535" class="input input-bordered input-sm w-full" />
        </div>
        <div>
          <label class="label py-1"><span class="label-text">SSH 用户</span></label>
          <input v-model="form.sshUser" type="text" class="input input-bordered input-sm w-full" />
        </div>
        <div>
          <label class="label py-1">
            <span class="label-text">SSH 超时 (秒) <span class="text-error">*</span></span>
            <span class="label-text-alt text-base-content/50">5-300, 建议 30</span>
          </label>
          <input
            v-model.number="form.sshTimeoutSeconds"
            type="number"
            min="5"
            max="300"
            class="input input-bordered input-sm w-full"
            :class="{ 'input-error': errors.sshTimeoutSeconds }"
          />
          <div v-if="errors.sshTimeoutSeconds" class="text-error text-xs mt-1">{{ errors.sshTimeoutSeconds }}</div>
        </div>
        <div class="sm:col-span-3">
          <label class="label py-1">
            <span class="label-text">SSH 密码</span>
            <span v-if="isEdit" class="label-text-alt text-base-content/50">留空保留原值</span>
          </label>
          <input
            v-model="form.sshPassword"
            type="password"
            autocomplete="new-password"
            class="input input-bordered input-sm w-full"
          />
        </div>
        <div class="sm:col-span-3">
          <label class="label py-1">
            <span class="label-text">SSH 私钥 (PEM)</span>
            <span v-if="isEdit" class="label-text-alt text-base-content/50">留空保留原值</span>
          </label>
          <textarea
            v-model="form.sshPrivateKey"
            rows="3"
            placeholder="-----BEGIN OPENSSH PRIVATE KEY-----..."
            class="textarea textarea-bordered w-full text-xs font-mono"
          ></textarea>
        </div>
        <div v-if="errors.sshAuth" class="sm:col-span-3 text-error text-xs">{{ errors.sshAuth }}</div>
      </div>

      <!-- Backend -->
      <div class="text-sm font-semibold text-base-content/70 mt-6 mb-2">Backend 配置</div>
      <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div>
          <label class="label py-1"><span class="label-text">Backend 类型 <span class="text-error">*</span></span></label>
          <Select
            v-model="form.backendType"
            :options="BACKEND_OPTIONS"
            :disabled="isEdit"
          />
          <div v-if="isEdit" class="text-xs text-base-content/50 mt-1">backend 类型不可变更（重建一台新服务器）</div>
        </div>
        <div>
          <label class="label py-1">
            <span class="label-text">Backend 超时 (秒) <span class="text-error">*</span></span>
            <span class="label-text-alt text-base-content/50">5-120, 建议 20</span>
          </label>
          <input
            v-model.number="form.backendTimeoutSeconds"
            type="number"
            min="5"
            max="120"
            class="input input-bordered input-sm w-full"
            :class="{ 'input-error': errors.backendTimeoutSeconds }"
          />
          <div v-if="errors.backendTimeoutSeconds" class="text-error text-xs mt-1">{{ errors.backendTimeoutSeconds }}</div>
        </div>
        <!-- 3x-ui 字段 -->
        <template v-if="form.backendType === 'threexui'">
          <div class="sm:col-span-2">
            <label class="label py-1">
              <span class="label-text">面板入口 URL <span class="text-error">*</span></span>
            </label>
            <input
              v-model="form.panelBaseUrl"
              type="text"
              placeholder="https://x.com:2053/abc  (含 webBasePath)"
              class="input input-bordered input-sm w-full font-mono"
              :class="{ 'input-error': errors.panelBaseUrl }"
            />
            <div v-if="errors.panelBaseUrl" class="text-error text-xs mt-1">{{ errors.panelBaseUrl }}</div>
          </div>
          <div>
            <label class="label py-1"><span class="label-text">面板用户名 <span class="text-error">*</span></span></label>
            <input
              v-model="form.panelUsername"
              type="text"
              class="input input-bordered input-sm w-full"
              :class="{ 'input-error': errors.panelUsername }"
            />
          </div>
          <div>
            <label class="label py-1">
              <span class="label-text">面板密码 <span class="text-error">*</span></span>
              <span v-if="isEdit" class="label-text-alt text-base-content/50">留空保留原值</span>
            </label>
            <input
              v-model="form.panelPassword"
              type="password"
              autocomplete="new-password"
              class="input input-bordered input-sm w-full"
              :class="{ 'input-error': errors.panelPassword }"
            />
          </div>
          <div class="sm:col-span-2">
            <label class="label py-1"><span class="label-text">HTTPS 证书</span></label>
            <Select v-model="form.panelIgnoreTls" :options="TLS_OPTIONS" />
          </div>
        </template>
        <!-- gRPC 字段 -->
        <template v-else-if="form.backendType === 'xray-grpc'">
          <div>
            <label class="label py-1"><span class="label-text">gRPC 主机 <span class="text-error">*</span></span></label>
            <input
              v-model="form.xrayGrpcHost"
              type="text"
              placeholder="127.0.0.1 (通常本机)"
              class="input input-bordered input-sm w-full font-mono"
              :class="{ 'input-error': errors.xrayGrpcHost }"
            />
          </div>
          <div>
            <label class="label py-1"><span class="label-text">gRPC 端口 <span class="text-error">*</span></span></label>
            <input
              v-model.number="form.xrayGrpcPort"
              type="number"
              placeholder="10085"
              class="input input-bordered input-sm w-full"
              :class="{ 'input-error': errors.xrayGrpcPort }"
            />
          </div>
        </template>
      </div>

      <div class="mt-4">
        <label class="label py-1"><span class="label-text">备注</span></label>
        <textarea
          v-model="form.remark"
          rows="2"
          class="textarea textarea-bordered w-full text-sm"
        ></textarea>
      </div>

      <div class="modal-action mt-6">
        <button class="btn btn-ghost btn-sm" @click="close">取消</button>
        <button
          class="btn btn-primary btn-sm"
          :disabled="submitting || loadingDetail"
          @click="onSubmit"
        >
          <span v-if="submitting" class="loading loading-spinner loading-xs"></span>
          确定
        </button>
      </div>
    </div>
    <div class="modal-backdrop bg-black/40" @click="close"></div>
  </dialog>
</template>
