<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import {
  NButton,
  NDivider,
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
  createIpPool,
  getIpPoolDetail,
  updateIpPool,
  type ResourceIpPool,
  type ResourceIpPoolSaveDTO
} from '@/api/resource/ip-pool'
import type { ResourceIpType } from '@/api/resource/ip-type'

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
  /** 由 DeployDialog 部署成功后接力传入, 自动填 SOCKS5 字段, 用户只需补 region/类型/IP 即可落库。 */
  socksPrefill?: SocksPrefill | null
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
      remark: form.remark.trim() || undefined
    }
    if (props.mode === 'create') {
      await createIpPool(dto)
      message.success('创建成功')
    } else {
      await updateIpPool(props.ip!.id, dto)
      message.success('更新成功')
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
  <NModal
    :show="modelValue"
    preset="card"
    :title="mode === 'create' ? '新增 IP' : '编辑 IP'"
    style="max-width: 48rem"
    :bordered="false"
    :mask-closable="false"
    @update:show="(v: boolean) => emit('update:modelValue', v)"
  >
    <p class="text-xs text-zinc-500 mb-4">
      本表单仅保存 IP 池条目元数据。<strong>一键部署 SOCKS5</strong> 在配置完成后, 通过列表行的 "部署" 按钮触发。
    </p>

    <NSpin :show="loadingDetail">
      <NForm
        :model="form"
        label-placement="top"
        require-mark-placement="right-hanging"
        size="small"
      >
        <NDivider title-placement="left" style="margin-top: 0">基本信息</NDivider>
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-x-4">
          <NFormItem
            label="区域"
            required
            :validation-status="errors.region ? 'error' : undefined"
            :feedback="errors.region"
          >
            <NInput
              v-model:value="form.region"
              placeholder="us-west / jp / hk / sg"
            />
          </NFormItem>

          <NFormItem
            label="类型"
            required
            :validation-status="errors.ipTypeId ? 'error' : undefined"
            :feedback="errors.ipTypeId || (!ipTypeOptions.length ? '未找到 IP 类型 — 请先在数据库执行 sql/99_seed.sql 初始化 resource_ip_type' : undefined)"
          >
            <NSelect
              v-model:value="form.ipTypeId"
              :options="ipTypeOptions"
              :status="errors.ipTypeId ? 'error' : undefined"
              placeholder="请选择"
            />
          </NFormItem>

          <div class="sm:col-span-2">
            <NFormItem
              label="IP 地址"
              required
              :validation-status="errors.ipAddress ? 'error' : undefined"
              :feedback="errors.ipAddress"
            >
              <NInput
                v-model:value="form.ipAddress"
                placeholder="例 1.2.3.4"
                :input-props="{ style: 'font-family: monospace' }"
              />
            </NFormItem>
          </div>

          <NFormItem label="状态">
            <NSelect v-model:value="form.status" :options="STATUS_OPTIONS" />
          </NFormItem>
        </div>

        <NDivider title-placement="left">SOCKS5 凭据</NDivider>
        <p class="text-xs text-zinc-500 mb-2 -mt-2">
          SOCKS5 主机自动跟随 IP 地址 (后续支持 HTTP / 其它代理协议时仍按此入口); 端口 / 用户名 / 密码由部署或外部 SOCKS5 服务决定。
        </p>
        <div class="grid grid-cols-1 sm:grid-cols-3 gap-x-4">
          <div class="sm:col-span-2">
            <NFormItem>
              <template #label>
                <span>SOCKS5 主机</span>
                <span class="text-xs text-zinc-400 ml-2">= IP 地址</span>
              </template>
              <NInput
                :value="form.ipAddress"
                readonly
                disabled
                :input-props="{ style: 'font-family: monospace' }"
              />
            </NFormItem>
          </div>

          <NFormItem
            :label="'SOCKS5 端口'"
            :required="!isEdit"
            :validation-status="errors.socks5Port ? 'error' : undefined"
            :feedback="errors.socks5Port"
          >
            <NInputNumber
              v-model:value="form.socks5Port"
              :min="1"
              :max="65535"
              style="width: 100%"
            />
          </NFormItem>

          <NFormItem
            label="用户名"
            :required="!isEdit"
            :validation-status="errors.socks5Username ? 'error' : undefined"
            :feedback="errors.socks5Username"
          >
            <NInput v-model:value="form.socks5Username" />
          </NFormItem>

          <div class="sm:col-span-2">
            <NFormItem
              label="密码"
              :required="!isEdit"
              :validation-status="errors.socks5Password ? 'error' : undefined"
              :feedback="errors.socks5Password"
            >
              <NInput
                v-model:value="form.socks5Password"
                type="password"
                show-password-on="click"
                :status="errors.socks5Password ? 'error' : undefined"
                :input-props="{ autocomplete: 'new-password' }"
              />
            </NFormItem>
          </div>
        </div>

        <NFormItem label="备注">
          <NInput
            v-model:value="form.remark"
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 4 }"
            placeholder="选填"
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
