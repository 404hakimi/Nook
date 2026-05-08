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

const STATUS_OPTIONS = [
  { label: '运行', value: 1 },
  { label: '维护', value: 2 },
  { label: '下线', value: 3 }
]

const form = reactive({
  name: '',
  host: '',
  sshPort: 22,
  sshUser: 'root',
  sshPassword: '',
  sshPrivateKey: '',
  sshTimeoutSeconds: 30,
  backendTimeoutSeconds: 20,
  xrayGrpcHost: '127.0.0.1',
  xrayGrpcPort: 62789 as number | undefined,
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
  form.backendTimeoutSeconds = s.backendTimeoutSeconds ?? 20
  form.xrayGrpcHost = s.xrayGrpcHost ?? '127.0.0.1'
  form.xrayGrpcPort = s.xrayGrpcPort ?? 62789
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
  form.backendTimeoutSeconds = 20
  form.xrayGrpcHost = '127.0.0.1'
  form.xrayGrpcPort = 62789
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
      sshPort: form.sshPort,
      sshUser: form.sshUser.trim(),
      sshPassword: form.sshPassword || undefined,
      sshPrivateKey: form.sshPrivateKey || undefined,
      sshTimeoutSeconds: form.sshTimeoutSeconds,
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

      <!-- Xray gRPC -->
      <div class="text-sm font-semibold text-base-content/70 mt-6 mb-2">Xray gRPC 配置</div>
      <p class="text-xs text-base-content/50 mb-2">
        nook 通过 SSH 隧道转发 gRPC, 所以 host 通常填 <code>127.0.0.1</code>(Xray 监听本地).
        端口由部署脚本决定, 默认 62789.
      </p>
      <div class="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div>
          <label class="label py-1"><span class="label-text">gRPC 主机 <span class="text-error">*</span></span></label>
          <input
            v-model="form.xrayGrpcHost"
            type="text"
            class="input input-bordered input-sm w-full font-mono"
            :class="{ 'input-error': errors.xrayGrpcHost }"
          />
          <div v-if="errors.xrayGrpcHost" class="text-error text-xs mt-1">{{ errors.xrayGrpcHost }}</div>
        </div>
        <div>
          <label class="label py-1"><span class="label-text">gRPC 端口 <span class="text-error">*</span></span></label>
          <input
            v-model.number="form.xrayGrpcPort"
            type="number"
            min="1"
            max="65535"
            class="input input-bordered input-sm w-full"
            :class="{ 'input-error': errors.xrayGrpcPort }"
          />
          <div v-if="errors.xrayGrpcPort" class="text-error text-xs mt-1">{{ errors.xrayGrpcPort }}</div>
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
