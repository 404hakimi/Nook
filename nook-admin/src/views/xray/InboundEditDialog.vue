<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useToast } from '@/composables/useToast'
import {
  INBOUND_STATUS_LABELS,
  getInboundDetail,
  updateInbound,
  type XrayInbound,
  type XrayInboundUpdateDTO
} from '@/api/xray/inbound'
import type { ResourceServer } from '@/api/resource/server'
import { formatDateTime } from '@/utils/date'
import Select from '@/components/Select.vue'

interface Props {
  modelValue: boolean
  inbound?: XrayInbound | null
  /** 父级传入的 serverId → 服务器映射，用于显示服务器名 */
  serverMap?: Record<string, ResourceServer>
}
const props = withDefaults(defineProps<Props>(), { serverMap: () => ({}) })
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'saved'): void
}>()

const toast = useToast()
const submitting = ref(false)
const loadingDetail = ref(false)
const detail = ref<XrayInbound | null>(null)
const errors = reactive<Record<string, string>>({})

const STATUS_OPTIONS = [
  { label: '运行', value: 1 },
  { label: '已停', value: 2 },
  { label: '待同步', value: 3 },
  { label: '远端缺失', value: 4 }
]

const form = reactive({
  listenIp: '',
  listenPort: undefined as number | undefined,
  transport: '',
  status: 1
})

watch(
  () => [props.modelValue, props.inbound?.id],
  async ([open]) => {
    if (!open || !props.inbound) {
      detail.value = null
      return
    }
    Object.keys(errors).forEach((k) => delete errors[k])
    // 用列表行先填一遍
    fillFromInbound(props.inbound)
    detail.value = props.inbound
    // 异步拉详情覆盖（同 UserFormDialog 同样防缓存的套路）
    const id = props.inbound.id
    loadingDetail.value = true
    try {
      const fresh = await getInboundDetail(id)
      if (props.modelValue && props.inbound?.id === id) {
        detail.value = fresh
        fillFromInbound(fresh)
      }
    } catch {
      /* 拉不到不影响，继续编辑列表行的快照 */
    } finally {
      loadingDetail.value = false
    }
  }
)

function fillFromInbound(e: XrayInbound) {
  form.listenIp = e.listenIp ?? ''
  form.listenPort = e.listenPort
  form.transport = e.transport ?? ''
  form.status = e.status
}

const serverName = computed(() => {
  if (!detail.value) return ''
  return props.serverMap[detail.value.serverId]?.name ?? detail.value.serverId
})

function validate(): boolean {
  Object.keys(errors).forEach((k) => delete errors[k])
  if (form.listenPort != null && (form.listenPort < 1 || form.listenPort > 65535)) {
    errors.listenPort = '端口范围 1-65535'
  }
  return Object.keys(errors).length === 0
}

async function onSubmit() {
  if (!validate() || !detail.value) return
  submitting.value = true
  try {
    const dto: XrayInboundUpdateDTO = {
      listenIp: form.listenIp.trim() || undefined,
      listenPort: form.listenPort,
      transport: form.transport.trim() || undefined,
      status: form.status
    }
    await updateInbound(detail.value.id, dto)
    toast.success('保存成功')
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
    <div class="modal-box max-w-2xl relative">
      <h3 class="text-lg font-semibold mb-1">编辑 Inbound 元数据</h3>
      <p class="text-xs text-base-content/50 mb-4">
        本对话框只改本地 DB 字段(listen IP/端口、transport、状态)，不触达远端 3x-ui 或 Xray。
        UUID/email/server/IP/protocol 是开通时定的契约，要换请走"轮换 / 吊销重开"。
      </p>

      <div
        v-if="loadingDetail"
        class="absolute inset-0 bg-base-100/70 flex items-center justify-center z-20 rounded-2xl"
      >
        <span class="loading loading-spinner loading-md text-primary"></span>
      </div>

      <div v-if="detail" class="space-y-4">
        <!-- 只读身份信息 -->
        <div class="text-sm font-semibold text-base-content/70">身份信息（只读）</div>
        <div class="grid grid-cols-2 gap-3 text-sm bg-base-200 rounded p-3">
          <div>
            <div class="text-xs text-base-content/50">服务器</div>
            <div>{{ serverName }}</div>
          </div>
          <div>
            <div class="text-xs text-base-content/50">远端 inbound 引用</div>
            <div class="font-mono">{{ detail.externalInboundRef }}</div>
          </div>
          <div>
            <div class="text-xs text-base-content/50">会员 ID</div>
            <div class="font-mono text-xs">{{ detail.memberUserId }}</div>
          </div>
          <div>
            <div class="text-xs text-base-content/50">IP ID</div>
            <div class="font-mono text-xs">{{ detail.ipId }}</div>
          </div>
          <div>
            <div class="text-xs text-base-content/50">协议</div>
            <div>{{ detail.protocol }}</div>
          </div>
          <div class="col-span-2">
            <div class="text-xs text-base-content/50">Client Email</div>
            <div class="font-mono text-xs">{{ detail.clientEmail }}</div>
          </div>
          <div class="col-span-2">
            <div class="text-xs text-base-content/50">Client UUID（已脱敏）</div>
            <div class="font-mono text-xs">{{ detail.clientUuid }}</div>
          </div>
          <div>
            <div class="text-xs text-base-content/50">创建时间</div>
            <div>{{ formatDateTime(detail.createdAt) }}</div>
          </div>
          <div>
            <div class="text-xs text-base-content/50">最近同步</div>
            <div>{{ formatDateTime(detail.lastSyncedAt) }}</div>
          </div>
        </div>

        <!-- 可编辑字段 -->
        <div class="text-sm font-semibold text-base-content/70">本地元数据（可改）</div>
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div>
            <label class="label py-1"><span class="label-text">监听 IP</span></label>
            <input
              v-model="form.listenIp"
              type="text"
              placeholder="0.0.0.0 / 127.0.0.1"
              class="input input-bordered input-sm w-full font-mono"
            />
          </div>
          <div>
            <label class="label py-1"><span class="label-text">监听端口</span></label>
            <input
              v-model.number="form.listenPort"
              type="number"
              min="1"
              max="65535"
              class="input input-bordered input-sm w-full"
              :class="{ 'input-error': errors.listenPort }"
            />
            <div v-if="errors.listenPort" class="text-error text-xs mt-1">{{ errors.listenPort }}</div>
          </div>
          <div>
            <label class="label py-1"><span class="label-text">Transport</span></label>
            <input
              v-model="form.transport"
              type="text"
              placeholder="reality / ws / tcp / grpc / xhttp"
              class="input input-bordered input-sm w-full"
            />
          </div>
          <div>
            <label class="label py-1">
              <span class="label-text">状态</span>
              <span class="label-text-alt text-base-content/50">手动覆写当前 {{ INBOUND_STATUS_LABELS[detail.status] }}</span>
            </label>
            <Select v-model="form.status" :options="STATUS_OPTIONS" />
          </div>
        </div>
      </div>

      <div class="modal-action mt-6">
        <button class="btn btn-ghost btn-sm" @click="close">取消</button>
        <button
          class="btn btn-primary btn-sm"
          :disabled="submitting || loadingDetail || !detail"
          @click="onSubmit"
        >
          <span v-if="submitting" class="loading loading-spinner loading-xs"></span>
          保存
        </button>
      </div>
    </div>
    <div class="modal-backdrop bg-black/40" @click="close"></div>
  </dialog>
</template>
