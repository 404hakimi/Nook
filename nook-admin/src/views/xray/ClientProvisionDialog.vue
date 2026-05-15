<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { CheckCircle2 } from 'lucide-vue-next'
import {
  NButton,
  NForm,
  NFormItem,
  NIcon,
  NInput,
  NInputNumber,
  NModal,
  NSelect,
  NSpace,
  NSpin,
  useMessage
} from 'naive-ui'
import { pageServers, type ResourceServer } from '@/api/resource/server'
import { pageClients, provisionClient, type XrayClientProvisionDTO } from '@/api/xray/client'
import {
  IP_POOL_STATUS_LABELS,
  pageIpPool,
  type ResourceIpPool
} from '@/api/resource/ip-pool'
import { IP_TYPE_CODE_LABELS, listIpTypes, type ResourceIpType } from '@/api/resource/ip-type'

interface Props {
  modelValue: boolean
}
const props = defineProps<Props>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'saved'): void
}>()

const message = useMessage()
const submitting = ref(false)
const errors = reactive<Record<string, string>>({})

const servers = ref<ResourceServer[]>([])
const loadingServers = ref(false)

const ipPool = ref<ResourceIpPool[]>([])
const ipTypes = ref<ResourceIpType[]>([])
const loadingIpPool = ref(false)

/** memberUserId 唯一性预校验状态; 'idle' 表示未校验, 'ok' 通过, 'dup' 已重复, 'checking' 正在请求. */
const memberCheck = ref<'idle' | 'checking' | 'ok' | 'dup'>('idle')

/**
 * 1:1 + slot 模型: inbound tag / 监听端口由 nook 自动决定 (in_slot_NN, port = slot_port_base + slot_index);
 * transport / listenIp 后端不再 fallback 默认值, 必须前端传 (有合理默认值, 运维可改).
 */
const form = reactive({
  serverId: '',
  ipId: '',
  memberUserId: '',
  protocol: '' as '' | 'vless' | 'vmess' | 'trojan',
  transport: 'tcp' as 'tcp' | 'ws' | 'grpc' | 'h2' | 'quic',
  listenIp: '0.0.0.0',
  totalBytes: undefined as number | undefined,
  expiryEpochMillis: undefined as number | undefined,
  limitIp: undefined as number | undefined,
  flow: ''
})

const PROTOCOL_OPTIONS = [
  { label: 'vless', value: 'vless' as const },
  { label: 'vmess', value: 'vmess' as const },
  { label: 'trojan', value: 'trojan' as const }
]

const TRANSPORT_OPTIONS = [
  { label: 'tcp (默认)', value: 'tcp' as const },
  { label: 'ws', value: 'ws' as const },
  { label: 'grpc', value: 'grpc' as const },
  { label: 'h2', value: 'h2' as const },
  { label: 'quic', value: 'quic' as const }
]

const serverOptions = computed(() =>
  servers.value.map((s) => ({
    label: `${s.name} — ${s.host}`,
    value: s.id
  }))
)

/** 仅展示 status=1(可分配) 的 IP, 避免误把已占用的 IP 二次派发. */
const ipPoolOptions = computed(() =>
  ipPool.value.map((ip) => {
    const t = ipTypes.value.find((x) => x.id === ip.ipTypeId)
    const typeLabel = t ? IP_TYPE_CODE_LABELS[t.code] ?? t.code : ip.ipTypeId
    const statusLabel = IP_POOL_STATUS_LABELS[ip.status] ?? ip.status
    return {
      label: `${ip.ipAddress} · ${ip.region} · ${typeLabel} · ${statusLabel}`,
      value: ip.id
    }
  })
)

watch(
  () => props.modelValue,
  async (open) => {
    if (!open) return
    Object.keys(errors).forEach((k) => delete errors[k])
    memberCheck.value = 'idle'
    Object.assign(form, {
      serverId: '',
      ipId: '',
      memberUserId: '',
      protocol: '',
      transport: 'tcp',
      listenIp: '0.0.0.0',
      totalBytes: undefined,
      expiryEpochMillis: undefined,
      limitIp: undefined,
      flow: ''
    })
    await Promise.all([loadServers(), loadIpPool(), loadIpTypesOnce()])
  }
)

async function loadIpPool() {
  loadingIpPool.value = true
  try {
    // 仅拉 status=1 (available) 的 IP, 防止误派已占用的; 取 200 条够下拉用
    const res = await pageIpPool({ pageNo: 1, pageSize: 200, status: 1 })
    ipPool.value = res.records
  } catch {
    /* */
  } finally {
    loadingIpPool.value = false
  }
}

async function loadIpTypesOnce() {
  if (ipTypes.value.length) return
  try {
    ipTypes.value = await listIpTypes()
  } catch {
    /* */
  }
}

async function loadServers() {
  loadingServers.value = true
  try {
    // 拉前 200 条够选了; 正式环境服务器数量有限
    const res = await pageServers({ pageNo: 1, pageSize: 200 })
    servers.value = res.records
  } catch {
    /* */
  } finally {
    loadingServers.value = false
  }
}

/**
 * 会员 ID 唯一性预校验:
 * - 同一 memberUserId 在 xray_client 表里只能存在一条 (后端会用 unique 约束兜底).
 * - 这里前端做一次 pageClients(memberUserId=...) 查询给运营即时反馈;
 *   blur 触发 + 提交前最终复核.
 * - 注意 pageClients 默认过滤软删, 已 revoke 的旧客户端不会被算进重复.
 */
async function checkMemberUnique(): Promise<boolean> {
  const id = form.memberUserId.trim()
  if (!id) {
    memberCheck.value = 'idle'
    return false
  }
  memberCheck.value = 'checking'
  try {
    const res = await pageClients({ memberUserId: id, pageNo: 1, pageSize: 1 })
    if (res.total > 0) {
      errors.memberUserId = '该会员已有客户端, 请先吊销旧的再 Provision'
      memberCheck.value = 'dup'
      return false
    }
    delete errors.memberUserId
    memberCheck.value = 'ok'
    return true
  } catch {
    // 拉不到不阻塞; 后端 unique 约束兜底
    memberCheck.value = 'idle'
    return true
  }
}

function onMemberInput() {
  // 输入过程中清状态, 避免拿旧结论卡用户
  if (memberCheck.value === 'dup') delete errors.memberUserId
  memberCheck.value = 'idle'
}

function validate(): boolean {
  Object.keys(errors).forEach((k) => delete errors[k])
  if (!form.serverId) errors.serverId = '请选服务器'
  if (!form.memberUserId.trim()) errors.memberUserId = '请输入会员 ID'
  if (!form.ipId.trim()) errors.ipId = '请选 IP'
  if (!form.protocol) errors.protocol = '请选协议'
  if (!form.transport) errors.transport = '请选传输层'
  if (!form.listenIp.trim()) errors.listenIp = '请输入监听 IP'
  // flow 仅 vless 协议可填; 后端 Validator 会复核, 这里前置一道避免无效请求
  if (form.flow.trim() && form.protocol !== 'vless') {
    errors.flow = 'flow 仅 vless 协议支持'
  }
  return Object.keys(errors).length === 0
}

async function onSubmit() {
  if (!validate()) return
  // 提交前再确认一次 memberUserId 唯一性 (防止用户绕过 blur 直接点确定)
  if (!(await checkMemberUnique())) return
  submitting.value = true
  try {
    // inbound tag / 监听端口由 nook 自动分配; transport / listenIp 由前端传
    const dto: XrayClientProvisionDTO = {
      serverId: form.serverId,
      ipId: form.ipId.trim(),
      memberUserId: form.memberUserId.trim(),
      protocol: form.protocol as 'vless' | 'vmess' | 'trojan',
      transport: form.transport,
      listenIp: form.listenIp.trim(),
      totalBytes: form.totalBytes,
      expiryEpochMillis: form.expiryEpochMillis,
      limitIp: form.limitIp,
      flow: form.flow.trim() || undefined
    }
    await provisionClient(dto)
    message.success('开通成功')
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
    title="手动 Provision 客户端"
    style="max-width: 56rem; width: 92vw"
    :bordered="false"
    :mask-closable="false"
    :close-on-esc="false"
    @update:show="(v: boolean) => emit('update:modelValue', v)"
  >
    <NForm
      :model="form"
      label-placement="top"
      require-mark-placement="right-hanging"
      size="small"
    >
      <div class="grid grid-cols-1 sm:grid-cols-2 gap-x-4">
        <div class="sm:col-span-2">
          <NFormItem
            required
            :validation-status="errors.serverId ? 'error' : undefined"
            :feedback="errors.serverId"
          >
            <template #label>
              <span>服务器</span>
              <span v-if="loadingServers" class="text-xs text-zinc-400 ml-2">
                <NSpin :size="12" /> 加载中
              </span>
            </template>
            <NSelect
              v-model:value="form.serverId"
              :options="serverOptions"
              :status="errors.serverId ? 'error' : undefined"
              placeholder="选服务器"
            />
          </NFormItem>
        </div>

        <div class="sm:col-span-2">
          <NFormItem
            required
            :validation-status="errors.memberUserId ? 'error' : undefined"
            :feedback="errors.memberUserId"
          >
            <template #label>
              <span>会员 ID</span>
              <span v-if="memberCheck === 'checking'" class="text-xs text-zinc-400 ml-2">
                <NSpin :size="12" /> 校验唯一性
              </span>
              <span
                v-else-if="memberCheck === 'ok'"
                class="text-xs ml-2 inline-flex items-center gap-1"
                style="color: var(--n-success-color)"
              >
                <NIcon :size="12"><CheckCircle2 /></NIcon>
                可用
              </span>
            </template>
            <NInput
              v-model:value="form.memberUserId"
              placeholder="member_user.id (一名会员仅能持有一个客户端)"
              :status="errors.memberUserId ? 'error' : undefined"
              :input-props="{ style: 'font-family: monospace' }"
              @input="onMemberInput"
              @blur="checkMemberUnique"
            />
          </NFormItem>
        </div>

        <div class="sm:col-span-2">
          <NFormItem
            required
            :validation-status="errors.ipId ? 'error' : undefined"
            :feedback="errors.ipId"
          >
            <template #label>
              <span>IP</span>
              <span v-if="loadingIpPool" class="text-xs text-zinc-400 ml-2">
                <NSpin :size="12" /> 加载中
              </span>
              <span v-else class="text-xs text-zinc-400 ml-2">仅显示可分配</span>
            </template>
            <NSelect
              v-model:value="form.ipId"
              :options="ipPoolOptions"
              :status="errors.ipId ? 'error' : undefined"
              placeholder="选 IP"
            />
          </NFormItem>
        </div>

        <NFormItem
          label="协议"
          required
          :validation-status="errors.protocol ? 'error' : undefined"
          :feedback="errors.protocol"
        >
          <NSelect
            v-model:value="form.protocol"
            :options="PROTOCOL_OPTIONS"
            :status="errors.protocol ? 'error' : undefined"
            placeholder="vless / vmess / trojan"
          />
        </NFormItem>

        <NFormItem
          label="传输层"
          required
          :validation-status="errors.transport ? 'error' : undefined"
          :feedback="errors.transport"
        >
          <NSelect
            v-model:value="form.transport"
            :options="TRANSPORT_OPTIONS"
            :status="errors.transport ? 'error' : undefined"
          />
        </NFormItem>

        <NFormItem
          label="监听 IP"
          required
          :validation-status="errors.listenIp ? 'error' : undefined"
          :feedback="errors.listenIp"
        >
          <NInput
            v-model:value="form.listenIp"
            :status="errors.listenIp ? 'error' : undefined"
            placeholder="0.0.0.0 / ::"
            :input-props="{ style: 'font-family: monospace' }"
          />
        </NFormItem>

        <NFormItem
          label="flow"
          :validation-status="errors.flow ? 'error' : undefined"
          :feedback="errors.flow || (form.protocol === 'vless' ? 'vless reality 用, 例 xtls-rprx-vision' : '仅 vless 协议可填')"
        >
          <NInput
            v-model:value="form.flow"
            :disabled="form.protocol !== 'vless'"
            placeholder="xtls-rprx-vision 或留空"
          />
        </NFormItem>

        <NFormItem label="流量上限 (字节, 0=不限)">
          <NInputNumber v-model:value="form.totalBytes" :min="0" class="w-full" />
        </NFormItem>

        <NFormItem label="到期 (epoch ms, 0=永久)">
          <NInputNumber v-model:value="form.expiryEpochMillis" :min="0" class="w-full" />
        </NFormItem>

        <NFormItem label="限 IP 数 (0=不限, 上限 100)">
          <NInputNumber v-model:value="form.limitIp" :min="0" :max="100" class="w-full" />
        </NFormItem>
      </div>
    </NForm>

    <template #footer>
      <NSpace justify="end">
        <NButton size="small" @click="close">取消</NButton>
        <NButton
          type="primary"
          size="small"
          :loading="submitting"
          :disabled="memberCheck === 'checking'"
          @click="onSubmit"
        >
          确定
        </NButton>
      </NSpace>
    </template>
  </NModal>
</template>
