<script setup lang="ts">
import { computed, h, reactive, ref, watch } from 'vue'
import {
  NButton,
  NCollapseTransition,
  NForm,
  NFormItem,
  NIcon,
  NInput,
  NInputNumber,
  NModal,
  NSelect,
  NSpace,
  useMessage
} from 'naive-ui'
import { ChevronDown, ChevronRight, Rocket } from 'lucide-vue-next'
import { useConfirm } from '@/composables/useConfirm'
import { createServer, type ResourceServerCreateDTO } from '@/api/resource/server'
import {
  DANTE_LOG_LEVEL_DEFAULT,
  createServerLanding,
  type ServerLandingSaveDTO
} from '@/api/resource/server-landing'
import { useRegionStore } from '@/stores/region'
import { useIpTypeStore } from '@/stores/ipType'
import { storeToRefs } from 'pinia'
import RegionFlag from '@/components/RegionFlag.vue'

/**
 * 服务器创建 dialog — frontline / landing 共用一套表单 + 逻辑, 按 serverType 分发.
 *
 * <p>共用字段: 区域 + SSH 凭据 (host/port/user/password) + 备注.
 * <p>frontline 独有: 别名 (name) + 高级超时配置.
 * <p>landing 独有: IP 类型 (ipTypeId) + IP 地址 (ipAddress) + SOCKS5/dante 默认值落库.
 *
 * <p>都走"配置 → 落库 (lifecycle=INSTALLING)"模式; 装机走详情页 → 各自的"装机"入口.
 * landing 创建成功后弹"立即装机 / 稍后"二选一 (frontline 不需要, 装 xray 是后续独立操作).
 */
const props = defineProps<{
  modelValue: boolean
  /** server_type, 默认 frontline 兼容老用法. */
  serverType?: 'frontline' | 'landing'
}>()
const emit = defineEmits<{
  'update:modelValue': [v: boolean]
  /** 创建成功; 父组件可刷新列表 */
  created: [serverId: string]
  /** landing 创建后用户选"立即装机"; 父组件打开 ServerLandingDeployDialog */
  'install-now': [serverId: string]
}>()

const message = useMessage()
const { confirm } = useConfirm()
const submitting = ref(false)
const errors = reactive<Record<string, string>>({})

const isLanding = computed(() => props.serverType === 'landing')

// ===== 区域 / IP 类型 字典 走 store =====
const regionStore = useRegionStore()
const ipTypeStore = useIpTypeStore()
const { list: regions } = storeToRefs(regionStore)
const { list: ipTypes } = storeToRefs(ipTypeStore)

const regionOptions = computed(() => regions.value.map((r) => ({
  label: r.displayName,
  value: r.code,
  countryCode: r.countryCode,
  flagEmoji: r.flagEmoji
})))
function renderRegionLabel(o: { label: string; countryCode?: string; flagEmoji?: string }) {
  if (!o.countryCode) return o.label
  return h('span', { style: 'display:flex; align-items:center; gap:6px;' }, [
    h(RegionFlag, { code: o.countryCode, fallback: o.flagEmoji, size: 14 }),
    o.label
  ])
}
const ipTypeOptions = computed(() =>
  ipTypes.value.map((t) => ({ label: `${t.name} (${t.code})`, value: t.id }))
)

const SSH_DEFAULTS = {
  sshPort: 22,
  sshUser: 'root',
  sshTimeoutSeconds: 30,
  sshOpTimeoutSeconds: 60,
  sshUploadTimeoutSeconds: 300,
  installTimeoutSeconds: 900
}

const form = reactive({
  // frontline-only
  name: '',
  // landing-only
  ipTypeId: '',
  // shared: ipAddress = SSH 主机 (canonical, Option 2)
  ipAddress: '',
  region: '',
  remark: '',
  // SSH credential (扁平, submit 时按 serverType 分别构造 nested / flat); host = ipAddress
  sshPort: SSH_DEFAULTS.sshPort,
  sshUser: SSH_DEFAULTS.sshUser,
  sshPassword: '',
  sshTimeoutSeconds: SSH_DEFAULTS.sshTimeoutSeconds,
  sshOpTimeoutSeconds: SSH_DEFAULTS.sshOpTimeoutSeconds,
  sshUploadTimeoutSeconds: SSH_DEFAULTS.sshUploadTimeoutSeconds,
  installTimeoutSeconds: SSH_DEFAULTS.installTimeoutSeconds
})
const advancedOpen = ref(false)

watch(() => props.modelValue, async (open) => {
  if (!open) return
  Object.keys(errors).forEach((k) => delete errors[k])
  Object.assign(form, {
    name: '',
    ipTypeId: '',
    ipAddress: '',
    region: '',
    remark: '',
    sshPort: SSH_DEFAULTS.sshPort,
    sshUser: SSH_DEFAULTS.sshUser,
    sshPassword: '',
    sshTimeoutSeconds: SSH_DEFAULTS.sshTimeoutSeconds,
    sshOpTimeoutSeconds: SSH_DEFAULTS.sshOpTimeoutSeconds,
    sshUploadTimeoutSeconds: SSH_DEFAULTS.sshUploadTimeoutSeconds,
    installTimeoutSeconds: SSH_DEFAULTS.installTimeoutSeconds
  })
  advancedOpen.value = false
  // 字典走 store 全局去重 (region 两边都用; ipType 仅 landing 用)
  const tasks: Promise<unknown>[] = [regionStore.ensureLoaded()]
  if (isLanding.value) tasks.push(ipTypeStore.ensureLoaded())
  await Promise.all(tasks)
  // landing 默认选第一个 IP 类型
  if (isLanding.value && !form.ipTypeId && ipTypes.value[0]) {
    form.ipTypeId = ipTypes.value[0].id
  }
})

function validate(): boolean {
  Object.keys(errors).forEach((k) => delete errors[k])
  if (isLanding.value) {
    if (!form.ipTypeId) errors.ipTypeId = '请选 IP 类型'
  } else {
    if (!form.name.trim()) errors.name = '请输入别名'
  }
  // ipAddress 两边都必填 (= SSH 主机)
  if (!form.ipAddress.trim()) errors.ipAddress = '请填 IP 地址'
  if (!form.region) errors.region = '请选择区域'
  if (form.sshPort == null || form.sshPort < 1 || form.sshPort > 65535) errors.sshPort = '端口 1-65535'
  if (!form.sshUser.trim()) errors.sshUser = '请输入 SSH 用户'
  if (!form.sshPassword.trim()) errors.sshPassword = '请输入 SSH 密码 (装机需要)'
  return Object.keys(errors).length === 0
}

// ===== landing 装机随机默认值 (用户装机前可去详情页调整) =====
function randomSocksPort(): number {
  return Math.floor(Math.random() * (60000 - 20000 + 1)) + 20000
}
function randomAlnum(len: number, mixedCase: boolean): string {
  const lower = 'abcdefghijklmnopqrstuvwxyz'
  const upper = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'
  const digit = '0123456789'
  const charset = (mixedCase ? lower + upper : lower) + digit
  const buf = new Uint32Array(len)
  crypto.getRandomValues(buf)
  let out = ''
  for (let i = 0; i < len; i++) out += charset[buf[i] % charset.length]
  return out
}
function randomSocksUser(): string {
  const lower = 'abcdefghijklmnopqrstuvwxyz'
  const buf = new Uint8Array(1)
  crypto.getRandomValues(buf)
  return lower[buf[0] % lower.length] + randomAlnum(7, false)
}
const LANDING_INSTALL_DIR = '/home/socks5'

async function onSubmit() {
  if (!validate()) return
  submitting.value = true
  try {
    if (isLanding.value) {
      const dto: ServerLandingSaveDTO = {
        region: form.region,
        ipTypeId: form.ipTypeId,
        ipAddress: form.ipAddress.trim(),
        lifecycleState: 'INSTALLING',
        provisionMode: 1, // SELF_DEPLOY
        remark: form.remark.trim() || undefined,
        sshPort: form.sshPort,
        sshUser: form.sshUser.trim(),
        sshPassword: form.sshPassword,
        sshTimeoutSeconds: form.sshTimeoutSeconds,
        sshOpTimeoutSeconds: form.sshOpTimeoutSeconds,
        sshUploadTimeoutSeconds: form.sshUploadTimeoutSeconds,
        installTimeoutSeconds: form.installTimeoutSeconds,
        // SOCKS5 默认值
        socks5Port: randomSocksPort(),
        socks5Username: randomSocksUser(),
        socks5Password: randomAlnum(16, true),
        // dante / install 默认值
        logLevel: DANTE_LOG_LEVEL_DEFAULT,
        logPath: `${LANDING_INSTALL_DIR}/logs/sockd.log`,
        autostartEnabled: 1,
        firewallEnabled: 1,
        logRotateEnabled: 1,
        installDir: LANDING_INSTALL_DIR,
        confPath: `${LANDING_INSTALL_DIR}/etc/danted.conf`,
        pamFile: '/etc/pam.d/sockd',
        pwdFile: `${LANDING_INSTALL_DIR}/etc/sockd.passwd`,
        systemdUnit: 'danted'
      }
      const created = await createServerLanding(dto)
      emit('created', created.id)
      const ok = await confirm({
        title: '落地机已落库, 现在装机?',
        message: `落地机 ${form.ipAddress} 已创建. 装机会 SSH 到 ${form.ipAddress} 跑 dante install + 自动切 LIVE. SOCKS5 端口/账号已用随机默认值, 装机前可去详情页 "SOCKS5 服务" tab 调整.`,
        type: 'info',
        confirmText: '立即装机',
        cancelText: '稍后再装'
      })
      if (ok) emit('install-now', created.id)
    } else {
      const dto: ResourceServerCreateDTO = {
        name: form.name.trim(),
        ipAddress: form.ipAddress.trim(),
        region: form.region,
        remark: form.remark.trim() || undefined,
        lifecycleState: 'INSTALLING',
        credential: {
          sshPort: form.sshPort,
          sshUser: form.sshUser.trim(),
          sshPassword: form.sshPassword.trim(),
          sshTimeoutSeconds: form.sshTimeoutSeconds,
          sshOpTimeoutSeconds: form.sshOpTimeoutSeconds,
          sshUploadTimeoutSeconds: form.sshUploadTimeoutSeconds,
          installTimeoutSeconds: form.installTimeoutSeconds
        }
      }
      const created = await createServer(dto)
      message.success(`已创建 (id: ${created.id.slice(0, 8)}...) — 进详情页继续装 Agent / Xray`)
      emit('created', created.id)
    }
    emit('update:modelValue', false)
  } catch { /* request 拦截器已 toast */ } finally {
    submitting.value = false
  }
}

const dialogTitle = computed(() => isLanding.value ? '新建落地机' : '新建线路机')
</script>

<template>
  <NModal
    :show="modelValue"
    preset="card"
    :title="dialogTitle"
    style="max-width: 42rem; width: 92vw"
    :bordered="false"
    :mask-closable="false"
    :close-on-esc="!submitting"
    @update:show="(v: boolean) => emit('update:modelValue', v)"
  >
    <template #header>
      <div class="flex items-center gap-2">
        <NIcon :size="18"><Rocket /></NIcon>
        <span>{{ dialogTitle }}</span>
      </div>
    </template>

    <NForm :model="form" label-placement="top" size="small">
      <div class="section-title">基础信息</div>

      <!-- frontline: 别名 -->
      <NFormItem
        v-if="!isLanding"
        label="别名"
        required
        :feedback="errors.name"
        :validation-status="errors.name ? 'error' : undefined"
      >
        <NInput v-model:value="form.name" placeholder="fra-test-001" />
      </NFormItem>

      <!-- landing 独有: IP 类型 -->
      <NFormItem
        v-if="isLanding"
        label="IP 类型"
        required
        :feedback="errors.ipTypeId"
        :validation-status="errors.ipTypeId ? 'error' : undefined"
      >
        <NSelect v-model:value="form.ipTypeId" :options="ipTypeOptions" placeholder="选 IP 类型" />
      </NFormItem>

      <!-- IP 地址 = SSH 主机 (canonical, 两边都用) -->
      <div class="grid grid-cols-3 gap-3">
        <NFormItem
          label="IP 地址 / SSH 主机"
          required
          :feedback="errors.ipAddress"
          :validation-status="errors.ipAddress ? 'error' : undefined"
          class="col-span-2"
        >
          <NInput
            v-model:value="form.ipAddress"
            placeholder="64.118.158.12 或 host.example.com"
            :input-props="{ style: 'font-family: monospace' }"
          />
        </NFormItem>

        <NFormItem label="SSH 端口" required :feedback="errors.sshPort" :validation-status="errors.sshPort ? 'error' : undefined">
          <NInputNumber v-model:value="form.sshPort" :min="1" :max="65535" class="w-full" />
        </NFormItem>
      </div>

      <NFormItem label="区域" required :feedback="errors.region" :validation-status="errors.region ? 'error' : undefined">
        <NSelect
          v-model:value="form.region"
          :options="regionOptions"
          :render-label="renderRegionLabel as any"
          placeholder="选择部署区域"
          filterable
        />
      </NFormItem>

      <NFormItem label="备注">
        <NInput
          v-model:value="form.remark"
          type="textarea"
          placeholder="可空"
          :autosize="{ minRows: 1, maxRows: 3 }"
        />
      </NFormItem>

      <div class="section-title mt-3">SSH 凭据 <span class="hint">(装机需要)</span></div>

      <div class="grid grid-cols-2 gap-3">
        <NFormItem label="SSH 用户" required :feedback="errors.sshUser" :validation-status="errors.sshUser ? 'error' : undefined">
          <NInput v-model:value="form.sshUser" :input-props="{ style: 'font-family: monospace' }" />
        </NFormItem>

        <NFormItem
          label="SSH 密码"
          required
          :feedback="errors.sshPassword"
          :validation-status="errors.sshPassword ? 'error' : undefined"
        >
          <NInput
            v-model:value="form.sshPassword"
            type="password"
            show-password-on="click"
            :input-props="{ style: 'font-family: monospace', autocomplete: 'new-password' }"
          />
        </NFormItem>
      </div>

      <!-- SSH 参数 (frontline + landing 共用) -->
      <div class="advanced-toggle" @click="advancedOpen = !advancedOpen">
        <NIcon :size="14"><ChevronDown v-if="advancedOpen" /><ChevronRight v-else /></NIcon>
        <span>高级 (超时配置)</span>
      </div>

      <NCollapseTransition :show="advancedOpen">
        <div class="grid grid-cols-2 gap-3 mt-2">
          <NFormItem label="SSH 握手超时 (秒)">
            <NInputNumber v-model:value="form.sshTimeoutSeconds" :min="5" :max="300" class="w-full" />
          </NFormItem>
          <NFormItem label="SSH 命令超时 (秒)">
            <NInputNumber v-model:value="form.sshOpTimeoutSeconds" :min="5" :max="300" class="w-full" />
          </NFormItem>
          <NFormItem label="SCP 上传超时 (秒)">
            <NInputNumber v-model:value="form.sshUploadTimeoutSeconds" :min="5" :max="600" class="w-full" />
          </NFormItem>
          <NFormItem label="安装超时 (秒)">
            <NInputNumber v-model:value="form.installTimeoutSeconds" :min="60" :max="3600" class="w-full" />
          </NFormItem>
        </div>
      </NCollapseTransition>

      <div v-if="isLanding" class="text-xs text-zinc-500 mt-3">
        💡 SOCKS5 端口 / 账号密码 / dante 配置 在装机时使用默认值; 装机前可去详情页 "SOCKS5 服务" tab 调整.
      </div>
    </NForm>

    <template #footer>
      <NSpace justify="end">
        <NButton size="small" :disabled="submitting" @click="emit('update:modelValue', false)">取消</NButton>
        <NButton type="primary" size="small" :loading="submitting" @click="onSubmit">
          {{ isLanding ? '保存配置' : '创建' }}
        </NButton>
      </NSpace>
    </template>
  </NModal>
</template>

<style scoped>
.section-title {
  font-size: 12px;
  font-weight: 600;
  color: #71717a;
  margin-bottom: 8px;
  padding-bottom: 4px;
  border-bottom: 1px dashed rgba(127, 127, 127, 0.18);
}
.section-title .hint {
  font-weight: 400;
  color: #a1a1aa;
  margin-left: 4px;
}
.advanced-toggle {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #71717a;
  cursor: pointer;
  user-select: none;
  margin-top: 4px;
}
.advanced-toggle:hover { color: #6366f1; }
</style>
