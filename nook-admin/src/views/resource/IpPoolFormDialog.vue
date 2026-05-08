<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useToast } from '@/composables/useToast'
import {
  createIpPool,
  getIpPoolDetail,
  updateIpPool,
  type ResourceIpPool,
  type ResourceIpPoolSaveDTO
} from '@/api/resource/ip-pool'
import type { ResourceIpType } from '@/api/resource/ip-type'
import Select from '@/components/Select.vue'

interface SocksPrefill {
  socks5Host: string
  socks5Port: number
  socks5Username: string
  socks5Password: string
}

interface Props {
  modelValue: boolean
  mode: 'create' | 'edit'
  ip?: ResourceIpPool | null
  ipTypes: ResourceIpType[]
  /** 由 DeployDialog 部署成功后接力传入, 自动填 SOCKS5 字段, 用户只需补 region/类型/IP/score 即可落库。 */
  socksPrefill?: SocksPrefill | null
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
  { label: '可分配', value: 1 },
  { label: '已占用', value: 2 },
  { label: '测试中', value: 3 },
  { label: '黑名单', value: 4 },
  { label: '冷却中', value: 5 },
  { label: '降级', value: 6 }
]

const ipTypeOptions = computed(() =>
  props.ipTypes.map((t) => ({ label: t.name, value: t.id }))
)

/**
 * 表单字段; 不包含 socks5Host —— 主机始终 = ipAddress, 提交时由 buildSaveDto 自动同步。
 * 表上 socks5_host 列保留是为后续兼容更多代理协议 (HTTP / HTTPS / TROJAN-SOCKS 等),
 * 也方便运营在表里直接看到入口地址; 但对当前 SOCKS5 场景与 IP 地址等价, 不让用户重复填。
 */
const form = reactive({
  region: '',
  ipTypeId: '',
  ipAddress: '',
  socks5Port: undefined as number | undefined,
  socks5Username: '',
  socks5Password: '',
  status: 1,
  score: undefined as number | undefined,
  scamalyticsScore: undefined as number | undefined,
  ipqsScore: undefined as number | undefined,
  remark: ''
})

const isEdit = computed(() => props.mode === 'edit')

function fill(ip: ResourceIpPool) {
  form.region = ip.region
  form.ipTypeId = ip.ipTypeId
  form.ipAddress = ip.ipAddress
  // socks5Host 不在 form 里, 由模板只读展示 form.ipAddress
  form.socks5Port = ip.socks5Port
  form.socks5Username = ip.socks5Username ?? ''
  // 接口下发明文密码, 直接 fill 进密码框 (UI 自然遮盖); 用户改一字会立即覆盖, 不改就保持
  form.socks5Password = ip.socks5Password ?? ''
  form.status = ip.status
  form.score = typeof ip.score === 'string' ? Number(ip.score) : ip.score
  form.scamalyticsScore = ip.scamalyticsScore
  form.ipqsScore = ip.ipqsScore
  form.remark = ip.remark ?? ''
}

function reset() {
  form.region = ''
  form.ipTypeId = props.ipTypes[0]?.id ?? ''
  form.ipAddress = ''
  form.socks5Port = undefined
  form.socks5Username = ''
  form.socks5Password = ''
  form.status = 1
  form.score = undefined
  form.scamalyticsScore = undefined
  form.ipqsScore = undefined
  form.remark = ''
}

watch(
  () => [props.modelValue, props.ip, props.mode],
  async ([open]) => {
    if (!open) return
    Object.keys(errors).forEach((k) => delete errors[k])
    if (props.mode === 'edit' && props.ip) {
      const id = props.ip.id
      fill(props.ip)
      loadingDetail.value = true
      try {
        const fresh = await getIpPoolDetail(id)
        if (props.modelValue && props.mode === 'edit' && props.ip?.id === id) fill(fresh)
      } finally {
        loadingDetail.value = false
      }
    } else {
      reset()
      // 模式 = create 且父组件传了 socksPrefill (部署成功接力场景), 预填 SOCKS5 + IP 地址。
      // socksPrefill.socks5Host 来自部署 dialog 里用户填的 sshHost, 这里同时作为 ipAddress 的初值;
      // socks5Host 不在 form 里, 由模板自动跟随 form.ipAddress
      if (props.socksPrefill) {
        form.ipAddress = props.socksPrefill.socks5Host
        form.socks5Port = props.socksPrefill.socks5Port
        form.socks5Username = props.socksPrefill.socks5Username
        form.socks5Password = props.socksPrefill.socks5Password
      }
    }
  }
)

function validate(): boolean {
  Object.keys(errors).forEach((k) => delete errors[k])
  if (!form.region.trim()) errors.region = '请输入区域'
  if (!form.ipTypeId) errors.ipTypeId = '请选择类型'
  if (!form.ipAddress.trim()) errors.ipAddress = '请输入 IP 地址'

  // 创建时 SOCKS5 端口/账号/密码必填; 编辑时未填的密码不会被覆盖, 端口与用户名按表单值更新。
  // socks5Host 不再校验 — 由 form.ipAddress 自动同步, 已在前面校验 ipAddress 必填。
  if (props.mode === 'create') {
    if (!form.socks5Port) errors.socks5Port = 'SOCKS5 端口必填'
    if (!form.socks5Username.trim()) errors.socks5Username = 'SOCKS5 用户名必填'
    if (!form.socks5Password) errors.socks5Password = 'SOCKS5 密码必填'
  }
  if (form.socks5Port != null && (form.socks5Port < 1 || form.socks5Port > 65535)) {
    errors.socks5Port = '端口范围 1-65535'
  }
  return Object.keys(errors).length === 0
}

async function onSubmit() {
  if (!validate()) return
  submitting.value = true
  try {
    const ip = form.ipAddress.trim()
    const dto: ResourceIpPoolSaveDTO = {
      region: form.region.trim(),
      ipTypeId: form.ipTypeId,
      ipAddress: ip,
      // socks5Host 始终 = ipAddress; 表单不让用户独立改, 避免不一致
      socks5Host: ip,
      socks5Port: form.socks5Port,
      socks5Username: form.socks5Username.trim() || undefined,
      socks5Password: form.socks5Password || undefined,
      status: form.status,
      score: form.score,
      scamalyticsScore: form.scamalyticsScore,
      ipqsScore: form.ipqsScore,
      remark: form.remark.trim() || undefined
    }
    if (props.mode === 'create') {
      await createIpPool(dto)
      toast.success('创建成功')
    } else {
      await updateIpPool(props.ip!.id, dto)
      toast.success('更新成功')
    }
    emit('saved')
    emit('update:modelValue', false)
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
        {{ mode === 'create' ? '新增 IP' : '编辑 IP' }}
      </h3>
      <p class="text-xs text-base-content/50 mb-4">
        本表单仅保存 IP 池条目元数据。<strong>一键部署 SOCKS5</strong> 在配置完成后, 通过列表行的 "部署" 按钮触发。
      </p>

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
          <label class="label py-1"><span class="label-text">区域 <span class="text-error">*</span></span></label>
          <input
            v-model="form.region"
            type="text"
            placeholder="us-west / jp / hk / sg"
            class="input input-bordered input-sm w-full"
            :class="{ 'input-error': errors.region }"
          />
          <div v-if="errors.region" class="text-error text-xs mt-1">{{ errors.region }}</div>
        </div>
        <div>
          <label class="label py-1"><span class="label-text">类型 <span class="text-error">*</span></span></label>
          <Select
            v-model="form.ipTypeId"
            :options="ipTypeOptions"
            :class="{ 'select-error': errors.ipTypeId }"
            placeholder="请选择"
          />
          <div v-if="!ipTypeOptions.length" class="text-warning text-xs mt-1">
            未找到 IP 类型 — 请先在数据库执行 sql/99_seed.sql 初始化 resource_ip_type
          </div>
          <div v-else-if="errors.ipTypeId" class="text-error text-xs mt-1">{{ errors.ipTypeId }}</div>
        </div>
        <div class="sm:col-span-2">
          <label class="label py-1"><span class="label-text">IP 地址 <span class="text-error">*</span></span></label>
          <input
            v-model="form.ipAddress"
            type="text"
            placeholder="例 1.2.3.4"
            class="input input-bordered input-sm w-full font-mono"
            :class="{ 'input-error': errors.ipAddress }"
          />
          <div v-if="errors.ipAddress" class="text-error text-xs mt-1">{{ errors.ipAddress }}</div>
        </div>
        <div>
          <label class="label py-1"><span class="label-text">状态</span></label>
          <Select v-model="form.status" :options="STATUS_OPTIONS" />
        </div>
        <div>
          <label class="label py-1">
            <span class="label-text">综合评分</span>
            <span class="label-text-alt text-base-content/50">0-100, 越高越优先派发</span>
          </label>
          <input
            v-model.number="form.score"
            type="number"
            step="0.01"
            min="0"
            max="100"
            placeholder="例 100"
            class="input input-bordered input-sm w-full"
          />
        </div>
      </div>

      <!-- SOCKS5 凭据 -->
      <div class="text-sm font-semibold text-base-content/70 mt-6 mb-2">SOCKS5 凭据</div>
      <p class="text-xs text-base-content/50 mb-2">
        SOCKS5 主机自动跟随 IP 地址 (后续支持 HTTP / 其它代理协议时仍按此入口); 端口 / 用户名 / 密码由部署或外部 SOCKS5 服务决定。
      </p>
      <div class="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div class="sm:col-span-2">
          <label class="label py-1">
            <span class="label-text">SOCKS5 主机</span>
            <span class="label-text-alt text-base-content/50">= IP 地址</span>
          </label>
          <input
            :value="form.ipAddress"
            type="text"
            readonly
            tabindex="-1"
            class="input input-bordered input-sm w-full font-mono bg-base-200 text-base-content/60 cursor-not-allowed"
          />
        </div>
        <div>
          <label class="label py-1">
            <span class="label-text">SOCKS5 端口 <span v-if="!isEdit" class="text-error">*</span></span>
          </label>
          <input
            v-model.number="form.socks5Port"
            type="number"
            min="1"
            max="65535"
            class="input input-bordered input-sm w-full"
            :class="{ 'input-error': errors.socks5Port }"
          />
          <div v-if="errors.socks5Port" class="text-error text-xs mt-1">{{ errors.socks5Port }}</div>
        </div>
        <div>
          <label class="label py-1">
            <span class="label-text">用户名 <span v-if="!isEdit" class="text-error">*</span></span>
          </label>
          <input
            v-model="form.socks5Username"
            type="text"
            class="input input-bordered input-sm w-full"
            :class="{ 'input-error': errors.socks5Username }"
          />
          <div v-if="errors.socks5Username" class="text-error text-xs mt-1">{{ errors.socks5Username }}</div>
        </div>
        <div class="sm:col-span-2">
          <label class="label py-1">
            <span class="label-text">密码 <span v-if="!isEdit" class="text-error">*</span></span>
          </label>
          <input
            v-model="form.socks5Password"
            type="password"
            autocomplete="new-password"
            class="input input-bordered input-sm w-full"
            :class="{ 'input-error': errors.socks5Password }"
          />
          <div v-if="errors.socks5Password" class="text-error text-xs mt-1">{{ errors.socks5Password }}</div>
        </div>
      </div>

      <!-- 风险评分 + 备注 -->
      <div class="text-sm font-semibold text-base-content/70 mt-6 mb-2">风险评分 (可选)</div>
      <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div>
          <label class="label py-1">
            <span class="label-text">Scamalytics</span>
            <span class="label-text-alt text-base-content/50">0-100, 越低越好</span>
          </label>
          <input v-model.number="form.scamalyticsScore" type="number" min="0" max="100" class="input input-bordered input-sm w-full" />
        </div>
        <div>
          <label class="label py-1">
            <span class="label-text">IPQualityScore</span>
            <span class="label-text-alt text-base-content/50">0-100, 越低越好</span>
          </label>
          <input v-model.number="form.ipqsScore" type="number" min="0" max="100" class="input input-bordered input-sm w-full" />
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
