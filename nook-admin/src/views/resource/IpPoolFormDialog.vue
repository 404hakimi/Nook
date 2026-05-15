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
  DANTE_LOG_LEVEL_DEFAULT,
  DANTE_LOG_LEVEL_OPTIONS,
  getIpPoolDetail,
  IP_POOL_PROVISION_MODE_LABELS,
  updateIpPool,
  type ResourceIpPool,
  type ResourceIpPoolSaveDTO
} from '@/api/resource/ip-pool'
import type { ResourceIpType } from '@/api/resource/ip-type'

interface SocksPrefill {
  ipAddress: string
  socks5Port: number
  socks5Username: string
  socks5Password: string
  /** 由部署窗口接力带过来的 dante 高级配置, 落库时跟远端实际状态对齐. */
  logLevel?: string
  logPath?: string
  autostartEnabled?: number
  firewallEnabled?: number
  firewallAllowFrom?: string
  installDir?: string
  /** SSH 凭据接力: 部署成功必填, 后续 详情/日志/切自启 运维操作要用. */
  sshHost?: string
  sshPort?: number
  sshUser?: string
  sshPassword?: string
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

const PROVISION_MODE_OPTIONS = Object.entries(IP_POOL_PROVISION_MODE_LABELS).map(
  ([value, label]) => ({ label, value: Number(value) })
)

const ipTypeOptions = computed(() =>
  props.ipTypes.map((t) => ({ label: t.name, value: t.id }))
)

/** 表单字段; SOCKS5 主机 = ipAddress, 不再单独存. */
const form = reactive({
  region: '',
  ipTypeId: '',
  ipAddress: '',
  socks5Port: undefined as number | undefined,
  socks5Username: '',
  socks5Password: '',
  status: 1,
  /** 部署模式 1=自部署 2=第三方; 后端必填, 由用户选 (部署接力进入时预填 1). */
  provisionMode: undefined as number | undefined,
  /** dante 日志级别预设值 (实际是事件关键字组合, 见 DANTE_LOG_LEVEL_OPTIONS). */
  logLevel: DANTE_LOG_LEVEL_DEFAULT,
  /** dante logoutput 路径. */
  logPath: '/var/log/sockd.log',
  /** systemd 开机自启 (1=enable 0=disable). */
  autostartEnabled: 1,
  /** UFW 是否配 (1=配 0=跳过). */
  firewallEnabled: 1,
  /** UFW allow 来源 CIDR; 空 = 0.0.0.0/0. */
  firewallAllowFrom: '',
  /** SOCKS5 安装目录; logs / info.txt 等运维资产放这里. */
  installDir: '/home/socks5',
  /** SSH 主机; 留空 = 用 ipAddress 作为兜底 (后端处理). */
  sshHost: '',
  sshPort: 22,
  sshUser: 'root',
  /** SSH 密码; edit 时留空保留原值, create 必填; 跟 SOCKS5 密码同口径明文存储. */
  sshPassword: '',
  /** 采购带宽上限 (Mbps); undefined = 不限/未填; 仅账面记录, 后续套餐侧消费. */
  bandwidthMbps: undefined as number | undefined,
  /** 采购流量上限 (GB); undefined = 不限/未填. */
  trafficQuotaGb: undefined as number | undefined,
  remark: ''
})

/** logPath 占位符随 installDir 联动: 用户留空时 DB 也存空, 实际跑脚本时后端按 installDir/logs/sockd.log 兜底. */
const logPathPlaceholder = computed(
  () => `${(form.installDir || '').trim() || '/home/socks5'}/logs/sockd.log`
)

const isEdit = computed(() => props.mode === 'edit')

function fill(ip: ResourceIpPool) {
  form.region = ip.region
  form.ipTypeId = ip.ipTypeId
  form.ipAddress = ip.ipAddress
  form.socks5Port = ip.socks5Port
  form.socks5Username = ip.socks5Username ?? ''
  // 接口下发明文密码, 直接 fill 进密码框 (UI 自然遮盖); 用户改一字会立即覆盖, 不改就保持
  form.socks5Password = ip.socks5Password ?? ''
  form.status = ip.status
  form.provisionMode = ip.provisionMode
  form.logLevel = ip.logLevel ?? DANTE_LOG_LEVEL_DEFAULT
  form.logPath = ip.logPath ?? ''
  form.autostartEnabled = ip.autostartEnabled ?? 1
  form.firewallEnabled = ip.firewallEnabled ?? 1
  form.firewallAllowFrom = ip.firewallAllowFrom ?? ''
  form.installDir = ip.installDir ?? '/home/socks5'
  form.sshHost = ip.sshHost ?? ''
  form.sshPort = ip.sshPort ?? 22
  form.sshUser = ip.sshUser ?? 'root'
  form.sshPassword = ip.sshPassword ?? ''
  form.bandwidthMbps = ip.bandwidthMbps ?? undefined
  form.trafficQuotaGb = ip.trafficQuotaGb ?? undefined
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
  form.provisionMode = undefined
  form.logLevel = DANTE_LOG_LEVEL_DEFAULT
  form.logPath = ''
  form.autostartEnabled = 1
  form.firewallEnabled = 1
  form.firewallAllowFrom = ''
  form.installDir = '/home/socks5'
  form.sshHost = ''
  form.sshPort = 22
  form.sshUser = 'root'
  form.sshPassword = ''
  form.bandwidthMbps = undefined
  form.trafficQuotaGb = undefined
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
      // create 且父组件传了 socksPrefill (部署成功接力场景), 预填 IP + SOCKS5 端口/账号
      // 部署接力场景必然是自部署, provisionMode 直接预填 1, 用户无需再选
      // 高级配置 (log / 自启 / UFW / 安装目录) 也接力过来, 否则 DB 记录跟远端实际状态会对不上
      const p = props.socksPrefill
      if (p) {
        form.ipAddress = p.ipAddress
        form.socks5Port = p.socks5Port
        form.socks5Username = p.socks5Username
        form.socks5Password = p.socks5Password
        form.provisionMode = 1
        if (p.logLevel != null) form.logLevel = p.logLevel
        if (p.logPath != null) form.logPath = p.logPath
        if (p.autostartEnabled != null) form.autostartEnabled = p.autostartEnabled
        if (p.firewallEnabled != null) form.firewallEnabled = p.firewallEnabled
        if (p.firewallAllowFrom != null) form.firewallAllowFrom = p.firewallAllowFrom
        if (p.installDir != null) form.installDir = p.installDir
        if (p.sshHost != null) form.sshHost = p.sshHost
        if (p.sshPort != null) form.sshPort = p.sshPort
        if (p.sshUser != null) form.sshUser = p.sshUser
        if (p.sshPassword != null) form.sshPassword = p.sshPassword
      }
    }
  }
)

function validate(): boolean {
  Object.keys(errors).forEach((k) => delete errors[k])
  if (!form.region.trim()) errors.region = '请输入区域'
  if (!form.ipTypeId) errors.ipTypeId = '请选择类型'
  if (!form.ipAddress.trim()) errors.ipAddress = '请输入 IP 地址'
  if (form.provisionMode == null) errors.provisionMode = '请选择部署模式'

  // 创建时 SOCKS5 端口/账号/密码必填; 编辑时未填的密码不会被覆盖
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
      socks5Port: form.socks5Port,
      socks5Username: form.socks5Username.trim() || undefined,
      socks5Password: form.socks5Password || undefined,
      status: form.status,
      provisionMode: form.provisionMode,
      logLevel: form.logLevel.trim() || undefined,
      logPath: form.logPath.trim() || undefined,
      autostartEnabled: form.autostartEnabled,
      firewallEnabled: form.firewallEnabled,
      firewallAllowFrom: form.firewallAllowFrom.trim() || undefined,
      installDir: form.installDir.trim() || undefined,
      sshHost: form.sshHost.trim() || undefined,
      sshPort: form.sshPort || undefined,
      sshUser: form.sshUser.trim() || undefined,
      // edit 时留空 = 保留 DB 现值; create 时表单已要求必填
      sshPassword: form.sshPassword || undefined,
      // bandwidth/quota: 后端 NULL 表示不限/未填, 前端 undefined 直接透传
      bandwidthMbps: form.bandwidthMbps ?? undefined,
      trafficQuotaGb: form.trafficQuotaGb ?? undefined,
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
            :feedback="errors.ipTypeId || (!ipTypeOptions.length ? '未找到 IP 类型, 请检查 resource_ip_type 是否已初始化' : undefined)"
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
              :feedback="errors.ipAddress || (isEdit ? '换 IP 等于换机器, 请新建条目, 不要在原行改' : undefined)"
            >
              <NInput
                v-model:value="form.ipAddress"
                placeholder="例 1.2.3.4"
                :disabled="isEdit"
                :input-props="{ style: 'font-family: monospace' }"
              />
            </NFormItem>
          </div>

          <NFormItem label="状态">
            <NSelect v-model:value="form.status" :options="STATUS_OPTIONS" />
          </NFormItem>

          <NFormItem
            label="部署模式"
            required
            :validation-status="errors.provisionMode ? 'error' : undefined"
            :feedback="errors.provisionMode || '自部署 = 后台一键装的 SOCKS5; 第三方 = 现成供应商'"
          >
            <NSelect
              v-model:value="form.provisionMode"
              :options="PROVISION_MODE_OPTIONS"
              :status="errors.provisionMode ? 'error' : undefined"
              placeholder="请选择"
            />
          </NFormItem>
        </div>

        <NDivider title-placement="left">SOCKS5 凭据</NDivider>
        <div class="grid grid-cols-1 sm:grid-cols-3 gap-x-4">
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

        <NDivider title-placement="left">dante 高级配置</NDivider>
        <div class="grid grid-cols-1 sm:grid-cols-3 gap-x-4">
          <div class="sm:col-span-2">
            <NFormItem>
              <template #label>
                <span>日志级别</span>
                <span class="text-xs text-zinc-400 ml-2">仅错误 / 警告 / 详细; 实际是 dante log 事件关键字组合</span>
              </template>
              <NSelect
                v-model:value="form.logLevel"
                :options="[...DANTE_LOG_LEVEL_OPTIONS]"
              />
            </NFormItem>
          </div>

          <NFormItem label="开机自启">
            <NSelect
              :value="form.autostartEnabled"
              :options="[{label: '启用', value: 1}, {label: '禁用', value: 0}]"
              @update:value="(v: number) => (form.autostartEnabled = v)"
            />
          </NFormItem>

          <div class="sm:col-span-2">
            <NFormItem>
              <template #label>
                <span>日志路径</span>
                <span class="text-xs text-zinc-400 ml-2">留空 = 安装目录/logs/sockd.log</span>
              </template>
              <NInput
                v-model:value="form.logPath"
                :placeholder="logPathPlaceholder"
                :input-props="{ style: 'font-family: monospace' }"
              />
            </NFormItem>
          </div>

          <NFormItem label="UFW 防火墙">
            <NSelect
              :value="form.firewallEnabled"
              :options="[{label: '启用', value: 1}, {label: '禁用', value: 0}]"
              @update:value="(v: number) => (form.firewallEnabled = v)"
            />
          </NFormItem>

          <div class="sm:col-span-3">
            <NFormItem>
              <template #label>
                <span>UFW 允许来源</span>
                <span class="text-xs text-zinc-400 ml-2">空 = 0.0.0.0/0 (全网开放); 推荐填中转线路公网 IP</span>
              </template>
              <NInput
                v-model:value="form.firewallAllowFrom"
                placeholder="留空 = 0.0.0.0/0"
                :disabled="form.firewallEnabled === 0"
                :input-props="{ style: 'font-family: monospace' }"
              />
            </NFormItem>
          </div>

          <div class="sm:col-span-3">
            <NFormItem>
              <template #label>
                <span>安装目录</span>
                <span class="text-xs text-zinc-400 ml-2">logs / info.txt 等运维资产存放; 默认 /home/socks5</span>
              </template>
              <NInput
                v-model:value="form.installDir"
                placeholder="/home/socks5"
                :input-props="{ style: 'font-family: monospace' }"
              />
            </NFormItem>
          </div>
        </div>

        <NDivider title-placement="left">
          SSH 凭据
          <span class="text-xs text-zinc-400 ml-2 font-normal">(后续 详情 / 日志 / 切自启 运维操作免输密码)</span>
        </NDivider>
        <div class="grid grid-cols-1 sm:grid-cols-3 gap-x-4">
          <div class="sm:col-span-2">
            <NFormItem>
              <template #label>
                <span>SSH 主机</span>
                <span class="text-xs text-zinc-400 ml-2">留空 = 用 IP 地址</span>
              </template>
              <NInput
                v-model:value="form.sshHost"
                :placeholder="form.ipAddress || '同 IP 地址'"
                :input-props="{ style: 'font-family: monospace' }"
              />
            </NFormItem>
          </div>

          <NFormItem label="SSH 端口">
            <NInputNumber
              v-model:value="form.sshPort"
              :min="1"
              :max="65535"
              style="width: 100%"
            />
          </NFormItem>

          <NFormItem label="SSH 用户">
            <NInput v-model:value="form.sshUser" placeholder="root" />
          </NFormItem>

          <div class="sm:col-span-2">
            <NFormItem>
              <template #label>
                <span>SSH 密码</span>
                <span class="text-xs text-zinc-400 ml-2">编辑时留空 = 保留 DB 原值</span>
              </template>
              <NInput
                v-model:value="form.sshPassword"
                type="password"
                show-password-on="click"
                :input-props="{ autocomplete: 'new-password' }"
                :placeholder="isEdit ? '留空保留原值' : '必填'"
              />
            </NFormItem>
          </div>
        </div>

        <NDivider title-placement="left">
          采购规格
          <span class="text-xs text-zinc-400 ml-2 font-normal">(账面记录, 后续套餐侧消费; 留空 = 不限/未填)</span>
        </NDivider>
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-x-4">
          <NFormItem>
            <template #label>
              <span>带宽</span>
              <span class="text-xs text-zinc-400 ml-2">Mbps; 留空 = 不限</span>
            </template>
            <NInputNumber
              v-model:value="form.bandwidthMbps"
              :min="1"
              :max="1000000"
              placeholder="例 100"
              clearable
              style="width: 100%"
            >
              <template #suffix>Mbps</template>
            </NInputNumber>
          </NFormItem>

          <NFormItem>
            <template #label>
              <span>流量</span>
              <span class="text-xs text-zinc-400 ml-2">GB; 留空 = 不限</span>
            </template>
            <NInputNumber
              v-model:value="form.trafficQuotaGb"
              :min="1"
              :max="10000000"
              placeholder="例 1000"
              clearable
              style="width: 100%"
            >
              <template #suffix>GB</template>
            </NInputNumber>
          </NFormItem>
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
