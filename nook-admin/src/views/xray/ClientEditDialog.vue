<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import {
  NButton,
  NDescriptions,
  NDescriptionsItem,
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
  CLIENT_STATUS_LABELS,
  getClientDetail,
  updateClient,
  type XrayClient,
  type XrayClientUpdateDTO
} from '@/api/xray/client'
import type { ResourceServer } from '@/api/resource/server'
import { formatDateTime } from '@/utils/date'

interface Props {
  modelValue: boolean
  inbound?: XrayClient | null
  /** 父级传入的 serverId → 服务器映射，用于显示服务器名 */
  serverMap?: Record<string, ResourceServer>
}
const props = withDefaults(defineProps<Props>(), { serverMap: () => ({}) })
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'saved'): void
}>()

const message = useMessage()
const submitting = ref(false)
const loadingDetail = ref(false)
const detail = ref<XrayClient | null>(null)
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
      const fresh = await getClientDetail(id)
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

function fillFromInbound(e: XrayClient) {
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
    const dto: XrayClientUpdateDTO = {
      listenIp: form.listenIp.trim() || undefined,
      listenPort: form.listenPort,
      transport: form.transport.trim() || undefined,
      status: form.status
    }
    await updateClient(detail.value.id, dto)
    message.success('保存成功')
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
    title="编辑 Inbound 元数据"
    style="max-width: 42rem"
    :bordered="false"
    :mask-closable="false"
    @update:show="(v: boolean) => emit('update:modelValue', v)"
  >
    <NSpin :show="loadingDetail">
      <div v-if="detail" class="space-y-4">
        <!-- 只读身份信息 -->
        <div class="text-sm font-semibold text-zinc-600 dark:text-zinc-400">身份信息（只读）</div>
        <NDescriptions :column="2" size="small" label-placement="left" bordered>
          <NDescriptionsItem label="服务器">{{ serverName }}</NDescriptionsItem>
          <NDescriptionsItem label="远端 inbound 引用">
            <span class="font-mono text-xs">{{ detail.externalInboundRef }}</span>
          </NDescriptionsItem>
          <NDescriptionsItem label="会员 ID">
            <span class="font-mono text-xs">{{ detail.memberUserId }}</span>
          </NDescriptionsItem>
          <NDescriptionsItem label="IP ID">
            <span class="font-mono text-xs">{{ detail.ipId }}</span>
          </NDescriptionsItem>
          <NDescriptionsItem label="协议">{{ detail.protocol }}</NDescriptionsItem>
          <NDescriptionsItem label="Client Email" :span="2">
            <span class="font-mono text-xs break-all">{{ detail.clientEmail }}</span>
          </NDescriptionsItem>
          <NDescriptionsItem label="Client UUID（已脱敏）" :span="2">
            <span class="font-mono text-xs break-all">{{ detail.clientUuid }}</span>
          </NDescriptionsItem>
          <NDescriptionsItem label="创建时间">{{ formatDateTime(detail.createdAt) }}</NDescriptionsItem>
          <NDescriptionsItem label="最近同步">{{ formatDateTime(detail.lastSyncedAt) }}</NDescriptionsItem>
        </NDescriptions>

        <!-- 可编辑字段 -->
        <div class="text-sm font-semibold text-zinc-600 dark:text-zinc-400">本地元数据（可改）</div>
        <NForm
          :model="form"
          label-placement="top"
          require-mark-placement="right-hanging"
          size="small"
        >
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-x-4">
            <NFormItem label="监听 IP">
              <NInput
                v-model:value="form.listenIp"
                placeholder="0.0.0.0 / 127.0.0.1"
                :input-props="{ style: 'font-family: monospace' }"
              />
            </NFormItem>

            <NFormItem
              label="监听端口"
              :validation-status="errors.listenPort ? 'error' : undefined"
              :feedback="errors.listenPort"
            >
              <NInputNumber
                v-model:value="form.listenPort"
                :min="1"
                :max="65535"
                class="w-full"
              />
            </NFormItem>

            <NFormItem label="Transport">
              <NInput
                v-model:value="form.transport"
                placeholder="reality / ws / tcp / grpc / xhttp"
              />
            </NFormItem>

            <NFormItem>
              <template #label>
                <span>状态</span>
                <span class="text-xs text-zinc-400 ml-2">
                  手动覆写当前 {{ CLIENT_STATUS_LABELS[detail.status] }}
                </span>
              </template>
              <NSelect v-model:value="form.status" :options="STATUS_OPTIONS" />
            </NFormItem>
          </div>
        </NForm>
      </div>
    </NSpin>

    <template #footer>
      <NSpace justify="end">
        <NButton size="small" @click="close">取消</NButton>
        <NButton
          type="primary"
          size="small"
          :loading="submitting"
          :disabled="loadingDetail || !detail"
          @click="onSubmit"
        >
          保存
        </NButton>
      </NSpace>
    </template>
  </NModal>
</template>
