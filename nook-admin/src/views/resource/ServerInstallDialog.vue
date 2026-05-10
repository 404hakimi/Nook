<script setup lang="ts">
import { nextTick, reactive, ref, watch } from 'vue'
import { Rocket } from 'lucide-vue-next'
import {
  NButton,
  NCheckbox,
  NForm,
  NFormItem,
  NIcon,
  NInputNumber,
  NInput,
  NModal,
  NSelect,
  NSpace,
  NSpin,
  useMessage
} from 'naive-ui'
import { useConfirm } from '@/composables/useConfirm'
import { xrayInstallStream, type LineServerInstallDTO } from '@/api/xray/server'
import type { ResourceServer } from '@/api/resource/server'

interface Props {
  modelValue: boolean
  server?: ResourceServer | null
}
const props = defineProps<Props>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'installed'): void
}>()

const message = useMessage()
const { confirm } = useConfirm()

const installing = ref(false)
const output = ref('')
const errors = reactive<Record<string, string>>({})
const outputRef = ref<HTMLPreElement | null>(null)
let abortCtrl: AbortController | null = null

const TIMEZONE_OPTIONS = [
  { label: '不修改 (skip)', value: 'skip' },
  { label: 'Asia/Shanghai (北京)', value: 'Asia/Shanghai' },
  { label: 'Asia/Hong_Kong', value: 'Asia/Hong_Kong' },
  { label: 'Asia/Tokyo', value: 'Asia/Tokyo' },
  { label: 'Asia/Singapore', value: 'Asia/Singapore' },
  { label: 'UTC', value: 'UTC' },
  { label: 'America/Los_Angeles', value: 'America/Los_Angeles' },
  { label: 'America/New_York', value: 'America/New_York' },
  { label: 'Europe/London', value: 'Europe/London' },
  { label: 'Europe/Frankfurt', value: 'Europe/Frankfurt' }
]

/** 项目认可的 Xray 稳定版; 升级时改这里. (后端不再有 fallback, 必须前端传值) */
const XRAY_DEFAULT_VERSION = 'v1.8.23'

const XRAY_VERSION_OPTIONS = [
  { label: `${XRAY_DEFAULT_VERSION} (稳定版, 推荐)`, value: XRAY_DEFAULT_VERSION },
  { label: 'latest (最新, 风险自负)', value: 'latest' }
]

const form = reactive<LineServerInstallDTO>({
  xrayVersion: XRAY_DEFAULT_VERSION,
  slotPortBase: 30000,
  slotPoolSize: 50,
  xrayApiPort: 8080,
  logDir: '/var/log/xray',
  installUfw: true,
  timezone: 'Asia/Shanghai'
})

watch(
  () => [props.modelValue, props.server?.id],
  ([open]) => {
    if (!open) return
    Object.keys(errors).forEach((k) => delete errors[k])
    output.value = ''
    // xray 配置默认值不再从 resource_server 取 (那里没了); 如需读已部署节点的端口, 后续可调 xray_node 接口
  }
)

function validate() {
  Object.keys(errors).forEach((k) => delete errors[k])
  if (form.xrayApiPort < 1 || form.xrayApiPort > 65535) errors.xrayApiPort = '端口范围 1-65535'
  if (form.slotPortBase < 1024 || form.slotPortBase > 60000) errors.slotPortBase = '端口范围 1024-60000'
  if (form.slotPoolSize < 1 || form.slotPoolSize > 200) errors.slotPoolSize = '槽位数 1-200'
  // slot 端口段不能覆盖 xray api 端口
  const slotEnd = form.slotPortBase + form.slotPoolSize
  if (form.xrayApiPort >= form.slotPortBase && form.xrayApiPort <= slotEnd) {
    errors.xrayApiPort = `不能落在 slot 端口段 ${form.slotPortBase}-${slotEnd} 内`
  }
  return Object.keys(errors).length === 0
}

async function onSubmit() {
  if (!validate() || !props.server) return
  const slotEnd = form.slotPortBase + form.slotPoolSize
  const ok = await confirm({
    title: '一键部署 Xray (1:1 + slot 模型)',
    message: `将在 ${props.server.name} 上部署 Xray ${form.xrayVersion} + 预置 ${form.slotPoolSize} 个 slot (端口段 ${form.slotPortBase}-${slotEnd}).\n\n约 1-5 分钟。如已存在 Xray 配置会先备份再覆盖。`,
    type: 'warning',
    confirmText: '开始部署'
  })
  if (!ok) return
  installing.value = true
  output.value = ''
  abortCtrl = new AbortController()
  try {
    const dto: LineServerInstallDTO = {
      xrayVersion: form.xrayVersion,
      slotPortBase: form.slotPortBase,
      slotPoolSize: form.slotPoolSize,
      xrayApiPort: form.xrayApiPort,
      logDir: form.logDir,
      installUfw: form.installUfw,
      timezone: form.timezone || 'skip'
    }
    await xrayInstallStream(props.server.id, dto, appendOutput, abortCtrl.signal)
    message.success('部署完成')
    emit('installed')
  } catch (e) {
    if ((e as Error).name === 'AbortError') {
      appendOutput('\n[nook] 用户已取消, 远端脚本可能已经在跑(无法终止)\n')
      message.warning('已取消, 但远端可能仍在执行')
    } else {
      appendOutput(`\n[error] ${(e as Error).message || ''}\n`)
      message.error('部署失败, 看输出日志定位')
    }
  } finally {
    installing.value = false
    abortCtrl = null
  }
}

// 剥 ANSI 颜色码 (\x1b[0;32m 等), 远端 apt/Xray 安装等命令可能带颜色
const ANSI_RE = /\x1b\[[0-9;?]*[A-Za-z]/g

function appendOutput(chunk: string) {
  output.value += chunk.replace(ANSI_RE, '')
  // 下一帧滚到底, 让用户始终看到最新一行
  nextTick(() => {
    if (outputRef.value) {
      outputRef.value.scrollTop = outputRef.value.scrollHeight
    }
  })
}

function close() {
  if (installing.value) {
    // 关弹框时主动 abort, 但 SSH 命令在远端继续跑(无法 kill)
    abortCtrl?.abort()
    message.warning('已断开输出流, 远端脚本可能仍在后台跑')
  }
  emit('update:modelValue', false)
}
</script>

<template>
  <NModal
    :show="modelValue"
    preset="card"
    style="max-width: 52rem"
    :bordered="false"
    :mask-closable="false"
    @update:show="(v: boolean) => emit('update:modelValue', v)"
  >
    <template #header>
      <div class="flex items-center gap-2">
        <NIcon :size="20" :depth="2"><Rocket /></NIcon>
        <span>一键部署 Xray (1:1 + slot 模型)</span>
      </div>
    </template>
    <template #header-extra>
      <span v-if="server" class="text-xs text-zinc-500">
        {{ server.name }} ({{ server.host }})
      </span>
    </template>

    <p class="text-xs text-zinc-500 mb-4">
      将远程执行 nook 模块化部署脚本 (仅支持 Ubuntu 22.04+), 装 Xray 内核 + 预置 slot
      placeholder; 客户开通时通过 SSH + xray api CLI 动态加 inbound/outbound, 不重启 xray 不影响其他客户。
    </p>

    <NForm
      :model="form"
      label-placement="top"
      require-mark-placement="right-hanging"
      size="small"
    >
      <!-- ===== 核心参数 ===== -->
      <div class="grid grid-cols-1 sm:grid-cols-2 gap-x-4">
        <NFormItem required>
          <template #label>
            <span>Xray 版本</span>
            <span class="text-xs text-zinc-400 ml-2">推荐稳定版</span>
          </template>
          <NSelect
            v-model:value="form.xrayVersion"
            :options="XRAY_VERSION_OPTIONS"
            :disabled="installing"
          />
        </NFormItem>
        <NFormItem
          required
          :validation-status="errors.xrayApiPort ? 'error' : undefined"
          :feedback="errors.xrayApiPort"
        >
          <template #label>
            <span>Xray API 端口</span>
            <span class="text-xs text-zinc-400 ml-2">仅 127.0.0.1 监听; xray api adi/rmi 用</span>
          </template>
          <NInputNumber
            v-model:value="form.xrayApiPort"
            :min="1"
            :max="65535"
            :disabled="installing"
            class="w-full"
          />
        </NFormItem>

        <NFormItem
          required
          :validation-status="errors.slotPortBase ? 'error' : undefined"
          :feedback="errors.slotPortBase"
        >
          <template #label>
            <span>Slot 端口段起点</span>
            <span class="text-xs text-zinc-400 ml-2">每客户独享一个端口</span>
          </template>
          <NInputNumber
            v-model:value="form.slotPortBase"
            :min="1024"
            :max="60000"
            :disabled="installing"
            class="w-full"
          />
        </NFormItem>
        <NFormItem
          required
          :validation-status="errors.slotPoolSize ? 'error' : undefined"
          :feedback="errors.slotPoolSize"
        >
          <template #label>
            <span>Slot 池大小</span>
            <span class="text-xs text-zinc-400 ml-2">该 server 客户上限 (含冗余)</span>
          </template>
          <NInputNumber
            v-model:value="form.slotPoolSize"
            :min="1"
            :max="200"
            :disabled="installing"
            class="w-full"
          />
        </NFormItem>

        <div class="sm:col-span-2">
          <NFormItem label="日志目录">
            <NInput
              v-model:value="form.logDir"
              :disabled="installing"
              :input-props="{ style: 'font-family: monospace' }"
            />
          </NFormItem>
        </div>
      </div>

      <!-- ===== 可选模块勾选 ===== -->
      <div class="mt-2 pt-3 border-t border-zinc-200 dark:border-zinc-700">
        <div class="text-sm font-semibold text-zinc-500 mb-2">可选模块</div>
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-x-4 gap-y-3">
          <div>
            <NCheckbox v-model:checked="form.installUfw" :disabled="installing">
              配置 UFW 防火墙
            </NCheckbox>
            <p class="text-xs text-zinc-500 ml-6 mt-1">
              放 22 + slot 端口段 ({{ form.slotPortBase }}-{{ form.slotPortBase + form.slotPoolSize }})
            </p>
          </div>
        </div>
        <p class="text-xs text-zinc-500 mt-3">
          BBR / swap 等通用 OS 调优在服务器列表的"运维"菜单按需独立触发, 不混进部署链路.
        </p>

        <div class="mt-3">
          <NFormItem>
            <template #label>
              <span>时区</span>
              <span class="text-xs text-zinc-400 ml-2">影响日志/到期判定; 选 skip 不修改</span>
            </template>
            <NSelect
              v-model:value="form.timezone"
              :options="TIMEZONE_OPTIONS"
              :disabled="installing"
            />
          </NFormItem>
        </div>
      </div>
    </NForm>

    <!-- 输出区: 流式追加, 自动滚到最底 -->
    <div class="mt-4">
      <div class="flex items-center justify-between mb-2">
        <div class="text-sm font-semibold text-zinc-500">远程输出 (实时)</div>
        <div v-if="installing" class="flex items-center gap-2 text-xs text-zinc-500">
          <NSpin :size="14" />
          <span>实时回传中...</span>
        </div>
      </div>
      <pre
        ref="outputRef"
        class="text-xs max-h-72 overflow-auto bg-zinc-900 text-zinc-100 min-h-32 px-4 py-3 rounded whitespace-pre-wrap break-all font-mono leading-relaxed"
      ><code v-if="output">{{ output }}</code><span v-else class="text-zinc-500">{{ installing ? '准备中...' : '点"开始部署"触发, 远端 stdout 会逐行回传到这里' }}</span></pre>
    </div>

    <template #footer>
      <NSpace justify="end">
        <NButton size="small" :disabled="installing" @click="close">关闭</NButton>
        <NButton
          type="primary"
          size="small"
          :loading="installing"
          :disabled="installing"
          @click="onSubmit"
        >
          <template #icon>
            <NIcon><Rocket /></NIcon>
          </template>
          {{ installing ? '部署中...' : '开始部署' }}
        </NButton>
      </NSpace>
    </template>
  </NModal>
</template>
