<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useToast } from '@/composables/useToast'
import { pageServers, type ResourceServer } from '@/api/resource/server'
import { listRemoteInbounds, type RemoteInbound } from '@/api/xray/server'
import { provisionInbound, type XrayInboundProvisionDTO } from '@/api/xray/inbound'
import Select from '@/components/Select.vue'

interface Props {
  modelValue: boolean
}
const props = defineProps<Props>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'saved'): void
}>()

const toast = useToast()
const submitting = ref(false)
const errors = reactive<Record<string, string>>({})

const servers = ref<ResourceServer[]>([])
const remoteInbounds = ref<RemoteInbound[]>([])
const loadingServers = ref(false)
const loadingInbounds = ref(false)

const form = reactive({
  serverId: '',
  ipId: '',
  memberUserId: '',
  externalInboundRef: '',
  protocol: '',
  transport: '',
  listenIp: '',
  listenPort: undefined as number | undefined,
  totalBytes: undefined as number | undefined,
  expiryEpochMillis: undefined as number | undefined,
  limitIp: undefined as number | undefined,
  flow: ''
})

const PROTOCOL_OPTIONS = [
  { label: 'vless', value: 'vless' },
  { label: 'vmess', value: 'vmess' },
  { label: 'trojan', value: 'trojan' },
  { label: 'shadowsocks', value: 'shadowsocks' }
]

const serverOptions = computed(() =>
  servers.value.map((s) => ({
    label: `${s.name} — ${s.host} (${s.backendType})`,
    value: s.id
  }))
)

const inboundOptions = computed(() =>
  remoteInbounds.value.map((ib) => ({
    label: `#${ib.externalInboundRef} ${ib.remark || ''} ${ib.protocol}:${ib.port}${ib.enabled ? '' : ' [禁用]'}`,
    value: ib.externalInboundRef
  }))
)

watch(
  () => props.modelValue,
  async (open) => {
    if (!open) return
    Object.keys(errors).forEach((k) => delete errors[k])
    Object.assign(form, {
      serverId: '',
      ipId: '',
      memberUserId: '',
      externalInboundRef: '',
      protocol: '',
      transport: '',
      listenIp: '',
      listenPort: undefined,
      totalBytes: undefined,
      expiryEpochMillis: undefined,
      limitIp: undefined,
      flow: ''
    })
    remoteInbounds.value = []
    await loadServers()
  }
)

async function loadServers() {
  loadingServers.value = true
  try {
    // 拉前 200 条够选了；正式环境服务器数量有限
    const res = await pageServers({ pageNo: 1, pageSize: 200 })
    servers.value = res.records
  } catch {
    /* */
  } finally {
    loadingServers.value = false
  }
}

watch(() => form.serverId, async (id) => {
  remoteInbounds.value = []
  form.externalInboundRef = ''
  form.protocol = ''
  form.listenPort = undefined
  if (!id) return
  loadingInbounds.value = true
  try {
    remoteInbounds.value = await listRemoteInbounds(id)
  } catch {
    /* */
  } finally {
    loadingInbounds.value = false
  }
})

watch(() => form.externalInboundRef, (ref) => {
  // 选了 inbound 自动回填 protocol + port
  const found = remoteInbounds.value.find((ib) => ib.externalInboundRef === ref)
  if (found) {
    if (found.protocol) form.protocol = found.protocol
    if (found.port) form.listenPort = found.port
  }
})

function validate(): boolean {
  Object.keys(errors).forEach((k) => delete errors[k])
  if (!form.serverId) errors.serverId = '请选服务器'
  if (!form.externalInboundRef) errors.externalInboundRef = '请选 inbound'
  if (!form.memberUserId.trim()) errors.memberUserId = '请输入会员 ID'
  if (!form.ipId.trim()) errors.ipId = '请输入 IP ID'
  if (!form.protocol) errors.protocol = '请选协议'
  return Object.keys(errors).length === 0
}

async function onSubmit() {
  if (!validate()) return
  submitting.value = true
  try {
    const dto: XrayInboundProvisionDTO = {
      serverId: form.serverId,
      ipId: form.ipId.trim(),
      memberUserId: form.memberUserId.trim(),
      externalInboundRef: form.externalInboundRef,
      protocol: form.protocol,
      transport: form.transport.trim() || undefined,
      listenIp: form.listenIp.trim() || undefined,
      listenPort: form.listenPort,
      totalBytes: form.totalBytes,
      expiryEpochMillis: form.expiryEpochMillis,
      limitIp: form.limitIp,
      flow: form.flow.trim() || undefined
    }
    await provisionInbound(dto)
    toast.success('开通成功')
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
    <div class="modal-box max-w-2xl">
      <h3 class="text-lg font-semibold mb-4">手动 Provision 客户端</h3>

      <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div class="sm:col-span-2">
          <label class="label py-1">
            <span class="label-text">服务器 <span class="text-error">*</span></span>
            <span v-if="loadingServers" class="label-text-alt"><span class="loading loading-spinner loading-xs"></span> 加载中</span>
          </label>
          <Select
            v-model="form.serverId"
            :options="serverOptions"
            :error="!!errors.serverId"
            placeholder="选服务器"
          />
          <div v-if="errors.serverId" class="text-error text-xs mt-1">{{ errors.serverId }}</div>
        </div>

        <div class="sm:col-span-2">
          <label class="label py-1">
            <span class="label-text">远端 Inbound <span class="text-error">*</span></span>
            <span v-if="loadingInbounds" class="label-text-alt"><span class="loading loading-spinner loading-xs"></span> 拉远端</span>
          </label>
          <Select
            v-model="form.externalInboundRef"
            :options="inboundOptions"
            :disabled="!form.serverId"
            :error="!!errors.externalInboundRef"
            placeholder="先选服务器"
          />
          <div v-if="errors.externalInboundRef" class="text-error text-xs mt-1">{{ errors.externalInboundRef }}</div>
        </div>

        <div>
          <label class="label py-1"><span class="label-text">会员 ID <span class="text-error">*</span></span></label>
          <input
            v-model="form.memberUserId"
            type="text"
            placeholder="member_user.id"
            class="input input-bordered input-sm w-full font-mono"
            :class="{ 'input-error': errors.memberUserId }"
          />
          <div v-if="errors.memberUserId" class="text-error text-xs mt-1">{{ errors.memberUserId }}</div>
        </div>
        <div>
          <label class="label py-1"><span class="label-text">IP ID <span class="text-error">*</span></span></label>
          <input
            v-model="form.ipId"
            type="text"
            placeholder="resource_ip_pool.id"
            class="input input-bordered input-sm w-full font-mono"
            :class="{ 'input-error': errors.ipId }"
          />
          <div v-if="errors.ipId" class="text-error text-xs mt-1">{{ errors.ipId }}</div>
        </div>

        <div>
          <label class="label py-1"><span class="label-text">协议 <span class="text-error">*</span></span></label>
          <Select v-model="form.protocol" :options="PROTOCOL_OPTIONS" :error="!!errors.protocol" />
        </div>
        <div>
          <label class="label py-1"><span class="label-text">监听端口</span></label>
          <input v-model.number="form.listenPort" type="number" class="input input-bordered input-sm w-full" />
        </div>

        <div>
          <label class="label py-1"><span class="label-text">流量上限 (字节, 0=不限)</span></label>
          <input v-model.number="form.totalBytes" type="number" min="0" class="input input-bordered input-sm w-full" />
        </div>
        <div>
          <label class="label py-1"><span class="label-text">到期 (epoch ms, 0=永久)</span></label>
          <input v-model.number="form.expiryEpochMillis" type="number" min="0" class="input input-bordered input-sm w-full" />
        </div>

        <div>
          <label class="label py-1"><span class="label-text">限 IP 数 (0=不限)</span></label>
          <input v-model.number="form.limitIp" type="number" min="0" class="input input-bordered input-sm w-full" />
        </div>
        <div>
          <label class="label py-1"><span class="label-text">flow (vless reality)</span></label>
          <input v-model="form.flow" type="text" placeholder="xtls-rprx-vision 或留空" class="input input-bordered input-sm w-full" />
        </div>
      </div>

      <div class="modal-action mt-6">
        <button class="btn btn-ghost btn-sm" @click="close">取消</button>
        <button class="btn btn-primary btn-sm" :disabled="submitting" @click="onSubmit">
          <span v-if="submitting" class="loading loading-spinner loading-xs"></span>
          确定
        </button>
      </div>
    </div>
    <div class="modal-backdrop bg-black/40" @click="close"></div>
  </dialog>
</template>
