<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { Rocket, Shuffle } from 'lucide-vue-next'
import {
  NButton,
  NCard,
  NCheckbox,
  NForm,
  NFormItem,
  NIcon,
  NInput,
  NInputGroup,
  NInputNumber,
  NModal,
  NSelect,
  NSpace,
  useMessage
} from 'naive-ui'
import { useConfirm } from '@/composables/useConfirm'
import {
  DANTE_LOG_LEVEL_DEFAULT,
  DANTE_LOG_LEVEL_OPTIONS,
  createIpPool,
  type ResourceIpPoolSaveDTO
} from '@/api/resource/ip-pool'

/**
 * 新建 IP 池条目 (自部署 SOCKS5) — "配置 → 落库 (lifecycle=INSTALLING)" 模式, 装机不在这里跑.
 *
 * 跟 ServerCreateDialog 同节奏: 先建占位, 装机走详情页 / 列表"安装"入口.
 *
 * 字段分 5 张卡片:
 *   ① 资源归属 (主表)
 *   ② SSH 凭据 (credential 子表)
 *   ③ SOCKS5 服务参数 (socks5 子表)
 *   ④ dante 配置 + 装机产物 (install 子表; 默认值 / 高级展开)
 *   ⑤ 备注 (主表)
 *
 * 提交时 sync POST createIpPool, lifecycle=INSTALLING. 创建成功后弹"立即装机 / 稍后装机"二选一.
 */

interface IpTypeOption {
  id: string
  code: string
  name: string
}
interface RegionOption {
  code: string
  displayName?: string
}

interface Props {
  modelValue: boolean
  ipTypes: IpTypeOption[]
  regions: RegionOption[]
}
const props = defineProps<Props>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  /** 创建成功 → 推 ipId 给父组件 (列表刷新 + 提示用户去装机) */
  (e: 'created', ipId: string): void
  /** 创建成功 + 用户选 "立即装机" → 父组件打开 IpPoolDeployDialog */
  (e: 'install-now', ipId: string): void
}>()

const message = useMessage()
const { confirm } = useConfirm()
const submitting = ref(false)
const errors = reactive<Record<string, string>>({})

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
function randomSocksPass(): string {
  return randomAlnum(16, true)
}

const form = reactive({
  // ① 主表: 资源归属
  region: '',
  ipTypeId: '',
  ipAddress: '',
  remark: '',

  // ② SSH 凭据 (落 credential 子表)
  sshHost: '',
  sshPort: 22,
  sshUser: 'root',
  sshPassword: '',

  // ③ SOCKS5 服务参数 (落 socks5 子表)
  socksPort: randomSocksPort(),
  socksUser: '',
  socksPass: '',
  logLevel: DANTE_LOG_LEVEL_DEFAULT,

  // ④ dante / install 配置 (落 install 子表)
  installDir: '/home/socks5',
  logPath: '',
  autostartEnabled: true,
  installUfw: true,
  logRotate: true,
  // 高级路径 (默认 OK, 一般不动)
  confPath: '/home/socks5/etc/danted.conf',
  pamFile: '/etc/pam.d/sockd',
  pwdFile: '/home/socks5/etc/sockd.passwd'
})

const ipTypeOptions = computed(() =>
  props.ipTypes.map((t) => ({ label: `${t.name} (${t.code})`, value: t.id }))
)
const regionOptions = computed(() =>
  props.regions.map((r) => ({ label: `${r.code}${r.displayName ? ' ' + r.displayName : ''}`, value: r.code }))
)
// readonly tuple → mutable copy for NSelect typing
const logLevelOptions = DANTE_LOG_LEVEL_OPTIONS.map((o) => ({ label: o.label, value: o.value }))
const logPathPlaceholder = computed(() => `${form.installDir.trim() || '/home/socks5'}/logs/sockd.log`)

/** ipAddress 跟 sshHost 联动 — 大多场景两者一致, 用户改 SSH host 自动同步 IP. */
watch(() => form.sshHost, (v) => {
  if (!form.ipAddress.trim() || form.ipAddress === form.sshHost) {
    form.ipAddress = v
  }
})

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    Object.keys(errors).forEach((k) => delete errors[k])
    Object.assign(form, {
      region: '',
      ipTypeId: props.ipTypes[0]?.id ?? '',
      ipAddress: '',
      remark: '',
      sshHost: '',
      sshPort: 22,
      sshUser: 'root',
      sshPassword: '',
      socksPort: randomSocksPort(),
      socksUser: '',
      socksPass: '',
      logLevel: DANTE_LOG_LEVEL_DEFAULT,
      installDir: '/home/socks5',
      logPath: '',
      autostartEnabled: true,
      installUfw: true,
      logRotate: true,
      confPath: '/home/socks5/etc/danted.conf',
      pamFile: '/etc/pam.d/sockd',
      pwdFile: '/home/socks5/etc/sockd.passwd'
    })
  }
)

function validate(): boolean {
  Object.keys(errors).forEach((k) => delete errors[k])
  if (!form.region.trim()) errors.region = '请选区域'
  if (!form.ipTypeId) errors.ipTypeId = '请选 IP 类型'
  if (!form.ipAddress.trim()) errors.ipAddress = '请填 IP 地址'
  if (!form.sshHost.trim()) errors.sshHost = '请填 SSH 主机'
  if (form.sshPort < 1 || form.sshPort > 65535) errors.sshPort = '端口 1-65535'
  if (!form.sshUser.trim()) errors.sshUser = '请填 SSH 用户'
  if (!form.sshPassword) errors.sshPassword = '请填 SSH 密码'
  if (form.socksPort < 1 || form.socksPort > 65535) errors.socksPort = 'SOCKS5 端口 1-65535'
  if (!form.socksUser.trim()) errors.socksUser = '请填 SOCKS5 用户'
  if (!form.socksPass) errors.socksPass = '请填 SOCKS5 密码'
  if (!form.installDir.trim()) errors.installDir = '请填安装目录'
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
      lifecycleState: 'INSTALLING',
      provisionMode: 1, // SELF_DEPLOY; 第三方暂未实现
      remark: form.remark.trim() || undefined,
      sshHost: form.sshHost.trim(),
      sshPort: form.sshPort,
      sshUser: form.sshUser.trim(),
      sshPassword: form.sshPassword,
      socks5Port: form.socksPort,
      socks5Username: form.socksUser.trim(),
      socks5Password: form.socksPass,
      logLevel: form.logLevel.trim() || undefined,
      logPath: form.logPath.trim() || logPathPlaceholder.value,
      autostartEnabled: form.autostartEnabled ? 1 : 0,
      firewallEnabled: form.installUfw ? 1 : 0,
      logRotateEnabled: form.logRotate ? 1 : 0,
      installDir: form.installDir.trim(),
      confPath: form.confPath.trim(),
      pamFile: form.pamFile.trim(),
      pwdFile: form.pwdFile.trim(),
      systemdUnit: 'danted'
    }
    const created = await createIpPool(dto)
    message.success(`已创建 (lifecycle=INSTALLING); 接下来装机生效`)
    emit('created', created.id)

    // 询问是否立即装机
    const ok = await confirm({
      title: '是否立即装机?',
      message: `IP ${form.ipAddress} 已落库, 但 dante 服务还没起. 现在装机会 SSH 远端跑 install 脚本 + 切 lifecycle=LIVE.`,
      type: 'info',
      confirmText: '立即装机',
      cancelText: '稍后再装'
    })
    if (ok) {
      emit('install-now', created.id)
    }
    emit('update:modelValue', false)
  } catch { /* request 拦截器已 toast */ } finally {
    submitting.value = false
  }
}

function close() {
  if (submitting.value) return
  emit('update:modelValue', false)
}
</script>

<template>
  <NModal
    :show="modelValue"
    preset="card"
    style="max-width: 56rem; width: 92vw"
    :bordered="false"
    :mask-closable="false"
    :close-on-esc="!submitting"
    @update:show="(v: boolean) => emit('update:modelValue', v)"
  >
    <template #header>
      <div class="flex items-center gap-2">
        <NIcon :size="20" :depth="2"><Rocket /></NIcon>
        <span>新建 IP — 自部署 SOCKS5</span>
      </div>
    </template>

    <NForm
      :model="form"
      label-placement="top"
      require-mark-placement="right-hanging"
      size="small"
    >
      <!-- Card 1: 资源归属 -->
      <NCard size="small" :bordered="true" class="mb-3">
        <template #header><div class="text-sm font-semibold">① 资源归属</div></template>
        <template #header-extra>
          <span class="text-xs text-zinc-400">落 resource_ip_pool 主表</span>
        </template>
        <div class="grid grid-cols-1 sm:grid-cols-6 gap-x-4">
          <div class="sm:col-span-2">
            <NFormItem label="区域" required :validation-status="errors.region ? 'error' : undefined" :feedback="errors.region">
              <NSelect v-model:value="form.region" :options="regionOptions" filterable placeholder="选区域" />
            </NFormItem>
          </div>
          <div class="sm:col-span-2">
            <NFormItem label="IP 类型" required :validation-status="errors.ipTypeId ? 'error' : undefined" :feedback="errors.ipTypeId">
              <NSelect v-model:value="form.ipTypeId" :options="ipTypeOptions" placeholder="选 IP 类型" />
            </NFormItem>
          </div>
          <div class="sm:col-span-2">
            <NFormItem label="IP 地址" required :validation-status="errors.ipAddress ? 'error' : undefined" :feedback="errors.ipAddress">
              <NInput v-model:value="form.ipAddress" :input-props="{ style: 'font-family: monospace' }" placeholder="跟 SSH host 联动" />
            </NFormItem>
          </div>
        </div>
      </NCard>

      <!-- Card 2: SSH 凭据 -->
      <NCard size="small" :bordered="true" class="mb-3">
        <template #header><div class="text-sm font-semibold">② SSH 凭据</div></template>
        <template #header-extra>
          <span class="text-xs text-zinc-400">装机用 + 落 credential 子表</span>
        </template>
        <div class="grid grid-cols-1 sm:grid-cols-6 gap-x-4">
          <div class="sm:col-span-3">
            <NFormItem label="SSH 主机" required :validation-status="errors.sshHost ? 'error' : undefined" :feedback="errors.sshHost">
              <NInput v-model:value="form.sshHost" :input-props="{ style: 'font-family: monospace' }" placeholder="目标 VPS IP (= IP 地址)" />
            </NFormItem>
          </div>
          <NFormItem label="SSH 端口" :validation-status="errors.sshPort ? 'error' : undefined" :feedback="errors.sshPort">
            <NInputNumber v-model:value="form.sshPort" :min="1" :max="65535" style="width: 100%" />
          </NFormItem>
          <NFormItem label="SSH 用户" :validation-status="errors.sshUser ? 'error' : undefined" :feedback="errors.sshUser">
            <NInput v-model:value="form.sshUser" />
          </NFormItem>
          <NFormItem label="SSH 密码" required :validation-status="errors.sshPassword ? 'error' : undefined" :feedback="errors.sshPassword">
            <NInput v-model:value="form.sshPassword" type="password" show-password-on="click" :input-props="{ autocomplete: 'new-password' }" />
          </NFormItem>
        </div>
      </NCard>

      <!-- Card 3: SOCKS5 服务参数 -->
      <NCard size="small" :bordered="true" class="mb-3">
        <template #header><div class="text-sm font-semibold">③ SOCKS5 服务参数</div></template>
        <template #header-extra>
          <span class="text-xs text-zinc-400">dante 业务配置, 落 socks5 子表</span>
        </template>
        <div class="grid grid-cols-1 sm:grid-cols-6 gap-x-4">
          <NFormItem label="SOCKS5 端口" required :validation-status="errors.socksPort ? 'error' : undefined" :feedback="errors.socksPort">
            <NInputNumber v-model:value="form.socksPort" :min="1" :max="65535" style="width: 100%" />
          </NFormItem>
          <div class="sm:col-span-2">
            <NFormItem label="用户名" required :validation-status="errors.socksUser ? 'error' : undefined" :feedback="errors.socksUser">
              <NInputGroup>
                <NInput v-model:value="form.socksUser" />
                <NButton title="生成随机" @click="form.socksUser = randomSocksUser()">
                  <template #icon><NIcon><Shuffle /></NIcon></template>
                </NButton>
              </NInputGroup>
            </NFormItem>
          </div>
          <div class="sm:col-span-2">
            <NFormItem label="密码" required :validation-status="errors.socksPass ? 'error' : undefined" :feedback="errors.socksPass">
              <NInputGroup>
                <NInput v-model:value="form.socksPass" type="password" show-password-on="click" :input-props="{ autocomplete: 'new-password' }" />
                <NButton title="生成随机 16 位" @click="form.socksPass = randomSocksPass()">
                  <template #icon><NIcon><Shuffle /></NIcon></template>
                </NButton>
              </NInputGroup>
            </NFormItem>
          </div>
          <NFormItem label="UFW">
            <NCheckbox v-model:checked="form.installUfw">配置 UFW</NCheckbox>
          </NFormItem>
        </div>
      </NCard>

      <!-- Card 4: dante 配置 + 装机产物 -->
      <NCard size="small" :bordered="true" class="mb-3">
        <template #header><div class="text-sm font-semibold">④ dante 配置 + 装机产物</div></template>
        <template #header-extra>
          <span class="text-xs text-zinc-400">默认即可, 落 install 子表</span>
        </template>
        <div class="grid grid-cols-1 sm:grid-cols-4 gap-x-4">
          <div class="sm:col-span-2">
            <NFormItem>
              <template #label>
                <span>日志级别</span>
                <span class="text-xs text-zinc-400 ml-2">事件关键字组合</span>
              </template>
              <NSelect v-model:value="form.logLevel" :options="logLevelOptions" />
            </NFormItem>
          </div>
          <NFormItem label="systemd 自启">
            <NCheckbox v-model:checked="form.autostartEnabled">开机自启 dante</NCheckbox>
          </NFormItem>
          <NFormItem label="logrotate">
            <NCheckbox v-model:checked="form.logRotate">日志轮转 (50M)</NCheckbox>
          </NFormItem>
          <div class="sm:col-span-2">
            <NFormItem label="安装目录" :validation-status="errors.installDir ? 'error' : undefined" :feedback="errors.installDir">
              <NInput v-model:value="form.installDir" :input-props="{ style: 'font-family: monospace' }" placeholder="/home/socks5" />
            </NFormItem>
          </div>
          <div class="sm:col-span-2">
            <NFormItem>
              <template #label>
                <span>日志路径</span>
                <span class="text-xs text-zinc-400 ml-2">留空 = 安装目录/logs/sockd.log</span>
              </template>
              <NInput v-model:value="form.logPath" :placeholder="logPathPlaceholder" :input-props="{ style: 'font-family: monospace' }" />
            </NFormItem>
          </div>
        </div>

        <details class="mt-3 cursor-pointer">
          <summary class="text-sm font-semibold text-zinc-500 select-none">
            更多路径 (sockd.conf / PAM / htpasswd; 默认即可)
          </summary>
          <div class="grid grid-cols-1 sm:grid-cols-3 gap-x-4 mt-2">
            <NFormItem label="sockd.conf">
              <NInput v-model:value="form.confPath" :input-props="{ style: 'font-family: monospace' }" />
            </NFormItem>
            <NFormItem>
              <template #label>
                <span>PAM file</span>
                <span class="text-xs text-zinc-400 ml-2">OS 强制 /etc/pam.d/</span>
              </template>
              <NInput v-model:value="form.pamFile" :input-props="{ style: 'font-family: monospace' }" />
            </NFormItem>
            <NFormItem label="htpasswd file">
              <NInput v-model:value="form.pwdFile" :input-props="{ style: 'font-family: monospace' }" />
            </NFormItem>
          </div>
          <div class="text-xs text-zinc-500 mt-1">
            systemd unit 固定 <code class="font-mono">danted</code> (apt 包提供); 装机用 drop-in 覆盖
            <code class="font-mono">ExecStart=/usr/sbin/danted -f &lt;sockd.conf&gt;</code> 指到自定义路径
          </div>
        </details>
      </NCard>

      <!-- Card 5: 备注 -->
      <NCard size="small" :bordered="true">
        <template #header><div class="text-sm font-semibold">⑤ 备注 (可选)</div></template>
        <NFormItem :show-label="false">
          <NInput v-model:value="form.remark" type="textarea" :rows="2" placeholder="运营备注 (供应商 / 用途 / 套餐等)" />
        </NFormItem>
      </NCard>
    </NForm>

    <template #footer>
      <NSpace justify="end">
        <NButton size="small" :disabled="submitting" @click="close">取消</NButton>
        <NButton type="primary" size="small" :loading="submitting" @click="onSubmit">
          保存配置 (lifecycle=INSTALLING)
        </NButton>
      </NSpace>
    </template>
  </NModal>
</template>
