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

interface Props {
  modelValue: boolean
  mode: 'create' | 'edit'
  ip?: ResourceIpPool | null
  ipTypes: ResourceIpType[]
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

const form = reactive({
  region: '',
  ipTypeId: '',
  ipAddress: '',
  socks5Host: '',
  socks5Port: 1080 as number | undefined,
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
  form.socks5Host = ip.socks5Host ?? ''
  form.socks5Port = ip.socks5Port
  form.socks5Username = ip.socks5Username ?? ''
  form.socks5Password = '' // 编辑时强制空, 留空 = 保留原值
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
  form.socks5Host = ''
  form.socks5Port = 1080
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
  if (!form.region.trim()) errors.region = '请输入区域'
  if (!form.ipTypeId) errors.ipTypeId = '请选择类型'
  if (!form.ipAddress.trim()) errors.ipAddress = '请输入 IP 地址'

  if (props.mode === 'create') {
    if (!form.socks5Host.trim()) errors.socks5Host = 'SOCKS5 主机必填'
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
    const dto: ResourceIpPoolSaveDTO = {
      region: form.region.trim(),
      ipTypeId: form.ipTypeId,
      ipAddress: form.ipAddress.trim(),
      socks5Host: form.socks5Host.trim() || undefined,
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
  } catch {
    /* */
  } finally {
    submitting.value = false
  }
}

function close() {
  emit('update:modelValue', false)
}

// IP 录入: 自动同步 socks5Host = ipAddress (用户可后续修改)
function onIpAddressBlur() {
  if (props.mode === 'create' && !form.socks5Host) {
    form.socks5Host = form.ipAddress
  }
}
</script>

<template>
  <dialog class="modal" :class="{ 'modal-open': modelValue }">
    <div class="modal-box max-w-3xl relative">
      <h3 class="text-lg font-semibold mb-4">
        {{ mode === 'create' ? '新增 IP' : '编辑 IP' }}
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
          />
          <div v-if="errors.ipTypeId" class="text-error text-xs mt-1">{{ errors.ipTypeId }}</div>
        </div>
        <div class="sm:col-span-2">
          <label class="label py-1"><span class="label-text">IP 地址 <span class="text-error">*</span></span></label>
          <input
            v-model="form.ipAddress"
            type="text"
            placeholder="例 1.2.3.4"
            class="input input-bordered input-sm w-full font-mono"
            :class="{ 'input-error': errors.ipAddress }"
            @blur="onIpAddressBlur"
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
            placeholder="默认 100"
            class="input input-bordered input-sm w-full"
          />
        </div>
      </div>

      <!-- SOCKS5 凭据 -->
      <div class="text-sm font-semibold text-base-content/70 mt-6 mb-2">SOCKS5 凭据</div>
      <p class="text-xs text-base-content/50 mb-2">
        nook 在中转线路上配 outbound 时会用这套凭据连这台落地 SOCKS5; 通常 host 等于 IP 地址.
      </p>
      <div class="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div class="sm:col-span-2">
          <label class="label py-1">
            <span class="label-text">SOCKS5 主机 <span class="text-error">*</span></span>
          </label>
          <input
            v-model="form.socks5Host"
            type="text"
            placeholder="通常 = IP 地址"
            class="input input-bordered input-sm w-full font-mono"
            :class="{ 'input-error': errors.socks5Host }"
          />
          <div v-if="errors.socks5Host" class="text-error text-xs mt-1">{{ errors.socks5Host }}</div>
        </div>
        <div>
          <label class="label py-1"><span class="label-text">SOCKS5 端口 <span class="text-error">*</span></span></label>
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
            <span class="label-text">SOCKS5 用户名 <span v-if="!isEdit" class="text-error">*</span></span>
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
            <span class="label-text">SOCKS5 密码 <span v-if="!isEdit" class="text-error">*</span></span>
            <span v-if="isEdit" class="label-text-alt text-base-content/50">留空保留原值</span>
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
