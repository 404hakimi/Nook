<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { CheckCircle2, Dices, Rocket, Settings2, Terminal } from 'lucide-vue-next'
import {
  NAlert,
  NButton,
  NCheckbox,
  NCheckboxGroup,
  NCollapse,
  NCollapseItem,
  NDescriptions,
  NDescriptionsItem,
  NEmpty,
  NForm,
  NFormItem,
  NIcon,
  NInput,
  NInputGroup,
  NInputNumber,
  NModal,
  NSelect,
  NSpace,
  NSpin,
  NTag,
  NTooltip,
  useMessage
} from 'naive-ui'
import { useConfirm } from '@/composables/useConfirm'
import {
  DANTE_LOG_LEVEL_OPTIONS,
  LANDING_DEPLOY_DEFAULTS,
  SERVER_LANDING_LIFECYCLE_LABELS,
  SERVER_LANDING_LIFECYCLE_TAG_TYPE,
  getServerLandingDetail,
  installServerLandingSocks5Stream,
  type ServerLanding,
  type ServerLandingDeployDTO
} from '@/api/resource/server-landing'
import { IP_TYPE_CODE_LABELS } from '@/api/system/ip-type'
import { useIpTypeStore } from '@/stores/ipType'
import { storeToRefs } from 'pinia'

/**
 * 针对已存在落地机条目的 SOCKS5 装机 (流式).
 *
 * <p>首次装机: SOCKS5 端口/账号/密码字段可编辑 (前端随机生成 + 可手动调整).
 * <p>重装 (已有 socks5Port): 这三字段置灰; 修改请走"编辑 SOCKS5 凭据" dialog.
 * <p>install 路径 + dante 开关: 前端默认值, 用户可改.
 * <p>装机后后端写回 landing DO + lifecycle → LIVE.
 */

interface Props {
  modelValue: boolean
  serverId?: string | null
}
const props = defineProps<Props>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'installed', serverId: string): void
}>()

const message = useMessage()
const { confirm } = useConfirm()

const loadingDetail = ref(false)
const installing = ref(false)
const deployed = ref(false)
const detail = ref<ServerLanding | null>(null)
const error = ref('')
const output = ref('')
const outputRef = ref<HTMLPreElement | null>(null)
let abortCtrl: AbortController | null = null

/** 是否重装 — 已经有 socks5Port 视为重装, 凭据字段置灰. */
const isReinstall = computed(() => !!detail.value?.socks5Port)

const ipTypeStore = useIpTypeStore()
const { list: ipTypes } = storeToRefs(ipTypeStore)
const ipTypeName = computed(() => {
  const id = detail.value?.ipTypeId
  if (!id) return '—'
  const t = ipTypes.value.find((x) => x.id === id)
  if (!t) return id
  return IP_TYPE_CODE_LABELS[t.code] || t.name || t.code
})

// ===== 随机生成 (跟编辑 SOCKS5 dialog 同算法) =====
function randomPort(): number {
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
function randomUsername(): string {
  const lower = 'abcdefghijklmnopqrstuvwxyz'
  const buf = new Uint8Array(1)
  crypto.getRandomValues(buf)
  return lower[buf[0] % lower.length] + randomAlnum(7, false)
}

const form = reactive<ServerLandingDeployDTO>({
  socks5Port: randomPort(),
  socks5Username: randomUsername(),
  socks5Password: randomAlnum(16, true),
  ...LANDING_DEPLOY_DEFAULTS
})

function fillFromDetail(d: ServerLanding) {
  // SOCKS5 凭据: 重装走 DO 已有值; 首次装机用前端随机 (form 初始化时就有)
  if (d.socks5Port) form.socks5Port = d.socks5Port
  if (d.socks5Username) form.socks5Username = d.socks5Username
  if (d.socks5Password) form.socks5Password = d.socks5Password
  // install 路径 / 开关: 重装时优先 DO 已有值, 否则前端默认
  form.logLevel = d.logLevel || LANDING_DEPLOY_DEFAULTS.logLevel
  form.logPath = d.logPath || LANDING_DEPLOY_DEFAULTS.logPath
  form.installDir = d.installDir || LANDING_DEPLOY_DEFAULTS.installDir
  form.confPath = LANDING_DEPLOY_DEFAULTS.confPath
  form.pamFile = LANDING_DEPLOY_DEFAULTS.pamFile
  form.pwdFile = LANDING_DEPLOY_DEFAULTS.pwdFile
  form.systemdUnit = LANDING_DEPLOY_DEFAULTS.systemdUnit
  form.autostartEnabled = d.autostartEnabled ?? LANDING_DEPLOY_DEFAULTS.autostartEnabled
  form.firewallEnabled = d.firewallEnabled ?? LANDING_DEPLOY_DEFAULTS.firewallEnabled
  form.logRotateEnabled = LANDING_DEPLOY_DEFAULTS.logRotateEnabled
}

async function loadDetail(id: string) {
  loadingDetail.value = true
  error.value = ''
  detail.value = null
  try {
    const [d] = await Promise.all([
      getServerLandingDetail(id),
      ipTypeStore.ensureLoaded()
    ])
    detail.value = d
    fillFromDetail(d)
  } catch (e) {
    error.value = (e as Error).message || '加载失败'
  } finally {
    loadingDetail.value = false
  }
}

watch(
  () => [props.modelValue, props.serverId],
  ([open, id]) => {
    if (!open) {
      if (abortCtrl) {
        abortCtrl.abort()
        abortCtrl = null
      }
      return
    }
    output.value = ''
    deployed.value = false
    // 重置为新一轮随机凭据 + 默认 install 配置 (loadDetail 后再用 DO 覆盖)
    Object.assign(form, {
      socks5Port: randomPort(),
      socks5Username: randomUsername(),
      socks5Password: randomAlnum(16, true),
      ...LANDING_DEPLOY_DEFAULTS
    })
    if (typeof id === 'string' && id) {
      void loadDetail(id)
    } else {
      error.value = 'id 缺失, 无法装机'
    }
  },
  { immediate: false }
)

const ANSI_RE = /\x1b\[[0-9;?]*[A-Za-z]/g
function appendOutput(chunk: string) {
  output.value += chunk.replace(ANSI_RE, '')
  nextTick(() => {
    if (outputRef.value) outputRef.value.scrollTop = outputRef.value.scrollHeight
  })
}

/** 3 个部署开关合并为 checkbox group 值; getter/setter 在 number(0/1) 与 string[] 之间映射 */
const switchValues = computed<string[]>({
  get: () => {
    const arr: string[] = []
    if (form.autostartEnabled === 1) arr.push('autostart')
    if (form.firewallEnabled === 1) arr.push('firewall')
    if (form.logRotateEnabled === 1) arr.push('logrotate')
    return arr
  },
  set: (v) => {
    form.autostartEnabled = v.includes('autostart') ? 1 : 0
    form.firewallEnabled = v.includes('firewall') ? 1 : 0
    form.logRotateEnabled = v.includes('logrotate') ? 1 : 0
  }
})

function onRandomPort() { form.socks5Port = randomPort() }
function onRandomUsername() { form.socks5Username = randomUsername() }
function onRandomPassword() { form.socks5Password = randomAlnum(16, true) }

async function onStartInstall() {
  if (!props.serverId || !detail.value) return
  const ok = await confirm({
    title: isReinstall.value ? '重装 SOCKS5?' : '开始装机 SOCKS5?',
    message: isReinstall.value
      ? `落地机 ${detail.value.ipAddress} 当前已 LIVE; 重装会重新跑 install 脚本, 期间客户端会断流几秒.`
      : `在 ${detail.value.ipAddress}:${detail.value.sshPort} 跑 dante 装机脚本; 成功后 lifecycle 切到 LIVE.`,
    type: isReinstall.value ? 'warning' : 'info',
    confirmText: isReinstall.value ? '继续重装' : '开始装机'
  })
  if (!ok) return

  installing.value = true
  deployed.value = false
  output.value = ''
  abortCtrl = new AbortController()
  try {
    await installServerLandingSocks5Stream(props.serverId, { ...form }, appendOutput, abortCtrl.signal)
    deployed.value = true
    message.success('装机完成, lifecycle 已切到 LIVE')
    emit('installed', props.serverId)
  } catch (e) {
    if ((e as Error).name === 'AbortError') {
      appendOutput('\n[nook] 用户已取消, 远端脚本可能仍在执行\n')
      message.warning('已取消, 远端可能仍在执行')
    } else {
      appendOutput(`\n[error] ${(e as Error).message || ''}\n`)
      message.error('装机失败, 看输出日志定位')
    }
  } finally {
    installing.value = false
    abortCtrl = null
  }
}

function close() {
  if (abortCtrl) {
    abortCtrl.abort()
    abortCtrl = null
  }
  emit('update:modelValue', false)
}
</script>

<template>
  <NModal
    :show="modelValue"
    preset="card"
    style="max-width: 56rem; width: 92vw"
    :bordered="false"
    :mask-closable="!installing"
    :close-on-esc="!installing"
    @update:show="(v: boolean) => emit('update:modelValue', v)"
  >
    <template #header>
      <div class="flex items-center gap-2">
        <NIcon :size="20" :depth="2"><Rocket /></NIcon>
        <span>装机 SOCKS5</span>
        <NTag v-if="detail" size="small" type="info">{{ detail.ipAddress }}</NTag>
      </div>
    </template>

    <NSpin :show="loadingDetail">
      <NEmpty v-if="!loadingDetail && !detail && error" :description="error" />

      <div v-else-if="detail">
        <!-- 节点信息 -->
        <NDescriptions bordered size="small" label-placement="left" :column="2" class="mb-3">
          <NDescriptionsItem label="名称" :span="2">{{ detail.name || '—' }}</NDescriptionsItem>
          <NDescriptionsItem label="IP 地址"><span class="font-mono">{{ detail.ipAddress }}</span></NDescriptionsItem>
          <NDescriptionsItem label="IP 类型">{{ ipTypeName }}</NDescriptionsItem>
          <NDescriptionsItem label="地区" :span="2">
            <NTag v-if="detail.region" size="small">{{ detail.region }}</NTag>
            <span v-else>—</span>
          </NDescriptionsItem>
          <NDescriptionsItem label="生命周期" :span="2">
            <NTag size="small" :type="SERVER_LANDING_LIFECYCLE_TAG_TYPE[detail.lifecycleState] || 'default'">
              {{ SERVER_LANDING_LIFECYCLE_LABELS[detail.lifecycleState] || detail.lifecycleState }}
            </NTag>
          </NDescriptionsItem>
        </NDescriptions>

        <!-- 装机表单 (装机前) -->
        <NForm v-if="!installing && !deployed" :model="form" label-placement="top" size="small">
          <!-- SOCKS5 凭据 (首次可编辑 + 随机, 重装置灰) -->
          <div class="section-title">SOCKS5 凭据</div>
          <NAlert v-if="isReinstall" type="warning" :show-icon="false" size="small" class="mb-2">
            重装路径下 SOCKS5 端口 / 用户 / 密码已锁定; 如需修改请走"编辑 SOCKS5 凭据" dialog 后再重装.
          </NAlert>

          <div class="grid grid-cols-3 gap-3">
            <NFormItem label="SOCKS5 端口" required>
              <NInputGroup>
                <NInputNumber
                  v-model:value="form.socks5Port"
                  :min="1" :max="65535"
                  :disabled="isReinstall"
                  class="flex-1"
                />
                <NTooltip v-if="!isReinstall">
                  <template #trigger>
                    <NButton @click="onRandomPort">
                      <NIcon><Dices /></NIcon>
                    </NButton>
                  </template>
                  随机 20000-60000
                </NTooltip>
              </NInputGroup>
            </NFormItem>
            <NFormItem label="SOCKS5 用户" required>
              <NInputGroup>
                <NInput
                  v-model:value="form.socks5Username"
                  :disabled="isReinstall"
                  :input-props="{ style: 'font-family: monospace' }"
                />
                <NTooltip v-if="!isReinstall">
                  <template #trigger>
                    <NButton @click="onRandomUsername">
                      <NIcon><Dices /></NIcon>
                    </NButton>
                  </template>
                  随机 (8 字符 a-z0-9)
                </NTooltip>
              </NInputGroup>
            </NFormItem>
            <NFormItem label="SOCKS5 密码" required>
              <NInputGroup>
                <NInput
                  v-model:value="form.socks5Password"
                  :disabled="isReinstall"
                  :input-props="{ style: 'font-family: monospace', autocomplete: 'off' }"
                />
                <NTooltip v-if="!isReinstall">
                  <template #trigger>
                    <NButton @click="onRandomPassword">
                      <NIcon><Dices /></NIcon>
                    </NButton>
                  </template>
                  随机 (16 字符 大小写+数字)
                </NTooltip>
              </NInputGroup>
            </NFormItem>
          </div>

          <!-- dante 配置: 安装目录 / 日志路径 / 日志级别 三列均分 -->
          <div class="section-title mt-3">dante 配置</div>
          <div class="grid grid-cols-3 gap-3">
            <NFormItem label="安装目录" required>
              <NInput v-model:value="form.installDir" :input-props="{ style: 'font-family: monospace' }" />
            </NFormItem>
            <NFormItem label="日志路径" required>
              <NInput v-model:value="form.logPath" :input-props="{ style: 'font-family: monospace' }" />
            </NFormItem>
            <NFormItem label="dante 日志级别" required>
              <NSelect v-model:value="form.logLevel" :options="DANTE_LOG_LEVEL_OPTIONS as any" />
            </NFormItem>
          </div>

          <!-- 部署开关: 并排勾选 -->
          <NFormItem label="部署开关">
            <NCheckboxGroup v-model:value="switchValues">
              <NSpace :size="20">
                <NCheckbox value="autostart">systemd 开机自启</NCheckbox>
                <NCheckbox value="firewall">UFW 防火墙</NCheckbox>
                <NCheckbox value="logrotate">logrotate</NCheckbox>
              </NSpace>
            </NCheckboxGroup>
          </NFormItem>

          <!-- 高级: 用 NCollapse 替代裸文字按钮, 视觉权重更显眼 -->
          <NCollapse class="advanced-collapse mt-2" :default-expanded-names="[]">
            <NCollapseItem name="advanced">
              <template #header>
                <div class="flex items-center gap-1 text-sm">
                  <NIcon :size="14"><Settings2 /></NIcon>
                  <span>高级配置</span>
                  <span class="text-xs text-zinc-400 ml-1">sockd.conf / pam / pwd / systemd unit</span>
                </div>
              </template>
              <div class="grid grid-cols-2 gap-3">
                <NFormItem label="sockd.conf 路径">
                  <NInput v-model:value="form.confPath" :input-props="{ style: 'font-family: monospace' }" />
                </NFormItem>
                <NFormItem label="PAM 文件">
                  <NInput v-model:value="form.pamFile" :input-props="{ style: 'font-family: monospace' }" />
                </NFormItem>
                <NFormItem label="htpasswd 文件">
                  <NInput v-model:value="form.pwdFile" :input-props="{ style: 'font-family: monospace' }" />
                </NFormItem>
                <NFormItem label="systemd unit">
                  <NInput v-model:value="form.systemdUnit" :input-props="{ style: 'font-family: monospace' }" />
                </NFormItem>
              </div>
            </NCollapseItem>
          </NCollapse>
        </NForm>

        <!-- 流式日志输出 -->
        <div class="flex items-center justify-between mb-2 mt-3">
          <div class="text-sm font-semibold flex items-center gap-2">
            <NIcon :size="16"><Terminal /></NIcon>
            远程 stdout (实时)
          </div>
          <div v-if="installing" class="flex items-center gap-2 text-xs text-zinc-500">
            <NSpin :size="14" /><span>装机中...</span>
          </div>
          <div v-else-if="deployed" class="flex items-center gap-1 text-xs" style="color: var(--n-success-color, #18a058)">
            <NIcon :size="16"><CheckCircle2 /></NIcon><span>装机完成</span>
          </div>
        </div>
        <pre
          ref="outputRef"
          class="text-xs max-h-72 min-h-32 overflow-auto bg-zinc-900 text-zinc-100 px-4 py-3 rounded whitespace-pre-wrap break-all font-mono leading-relaxed"
        ><code v-if="output">{{ output }}</code><span v-else class="text-zinc-500">{{ installing ? '准备中...' : '点"开始装机"启动远端脚本' }}</span></pre>
      </div>
    </NSpin>

    <template #footer>
      <NSpace justify="end">
        <NButton size="small" :disabled="installing" @click="close">关闭</NButton>
        <NButton
          v-if="!deployed"
          type="primary"
          size="small"
          :loading="installing"
          :disabled="installing || !detail"
          @click="onStartInstall"
        >
          <template #icon><NIcon><Rocket /></NIcon></template>
          {{ isReinstall ? '重装' : '开始装机' }}
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
.advanced-collapse :deep(.n-collapse-item) {
  border: 1px dashed rgba(127, 127, 127, 0.25);
  border-radius: 6px;
  margin-top: 0 !important;
}
/* 覆盖 naive-ui 默认 padding-top:0 + 让 arrow 跟 main 严格垂直居中 */
.advanced-collapse :deep(.n-collapse-item__header) {
  padding: 10px 12px !important;
  background: rgba(99, 102, 241, 0.04);
  border-radius: 6px;
  align-items: center;
  min-height: 0;
}
.advanced-collapse :deep(.n-collapse-item-arrow) {
  display: inline-flex;
  align-items: center;
  line-height: 1;
}
.advanced-collapse :deep(.n-collapse-item__content-wrapper) {
  padding: 10px 12px 0;
}
</style>
