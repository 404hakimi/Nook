<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { CheckCircle2, Rocket, Terminal } from 'lucide-vue-next'
import {
  NButton,
  NDescriptions,
  NDescriptionsItem,
  NEmpty,
  NIcon,
  NModal,
  NSpace,
  NSpin,
  NTag,
  useMessage
} from 'naive-ui'
import { useConfirm } from '@/composables/useConfirm'
import {
  getServerLandingDetail,
  installServerLandingSocks5Stream,
  type ServerLanding
} from '@/api/resource/server-landing'

/**
 * 针对已存在落地机条目的 SOCKS5 装机 (流式) — 跟 ServerInstallDialog 同模式.
 *
 * - props.serverId: 待装机的落地机条目 (lifecycle=INSTALLING 或 LIVE 重装)
 * - 装机前先 GET 详情展示给用户确认 (IP/SOCKS5 端口/SSH host 等)
 * - 用户点"开始装机" → 调 /install-socks5?id=xxx → 流式 stdout + lifecycle 自动 LIVE
 * - 装机成功 → emit installed → 父组件刷新列表
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

async function loadDetail(id: string) {
  loadingDetail.value = true
  error.value = ''
  detail.value = null
  try {
    detail.value = await getServerLandingDetail(id)
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
      // 关闭时主动 abort 流, 防"已取消但远端还在跑"两边状态不一致
      if (abortCtrl) {
        abortCtrl.abort()
        abortCtrl = null
      }
      return
    }
    output.value = ''
    deployed.value = false
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

async function onStartInstall() {
  if (!props.serverId || !detail.value) return
  const isReinstall = detail.value.lifecycleState === 'LIVE'
  const ok = await confirm({
    title: isReinstall ? '重装 SOCKS5?' : '开始装机 SOCKS5?',
    message: isReinstall
      ? `落地机 ${detail.value.ipAddress} 当前已 LIVE; 重装会重新跑 install 脚本, 期间客户端会断流几秒.`
      : `在 ${detail.value.ipAddress}:${detail.value.sshPort} 跑 dante 装机脚本; 成功后 lifecycle 切到 LIVE.`,
    type: isReinstall ? 'warning' : 'info',
    confirmText: isReinstall ? '继续重装' : '开始装机'
  })
  if (!ok) return

  installing.value = true
  deployed.value = false
  output.value = ''
  abortCtrl = new AbortController()
  try {
    await installServerLandingSocks5Stream(props.serverId, appendOutput, abortCtrl.signal)
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
        <!-- 装机前: 配置摘要给用户确认 -->
        <NDescriptions bordered size="small" label-placement="left" :column="2" class="mb-3">
          <NDescriptionsItem label="IP"><span class="font-mono">{{ detail.ipAddress }}</span></NDescriptionsItem>
          <NDescriptionsItem label="lifecycle"><NTag size="small">{{ detail.lifecycleState }}</NTag></NDescriptionsItem>
          <NDescriptionsItem label="SSH host"><span class="font-mono text-xs">{{ detail.ipAddress }}:{{ detail.sshPort }}</span></NDescriptionsItem>
          <NDescriptionsItem label="SSH user">{{ detail.sshUser }}</NDescriptionsItem>
          <NDescriptionsItem label="SOCKS5 端口"><span class="font-mono">{{ detail.socks5Port }}</span></NDescriptionsItem>
          <NDescriptionsItem label="SOCKS5 用户"><span class="font-mono text-xs">{{ detail.socks5Username }}</span></NDescriptionsItem>
          <NDescriptionsItem label="安装目录"><span class="font-mono text-xs">{{ detail.installDir }}</span></NDescriptionsItem>
          <NDescriptionsItem label="日志路径"><span class="font-mono text-xs">{{ detail.logPath }}</span></NDescriptionsItem>
        </NDescriptions>

        <!-- 流式日志输出 -->
        <div class="flex items-center justify-between mb-2">
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
          {{ detail?.lifecycleState === 'LIVE' ? '重装' : '开始装机' }}
        </NButton>
      </NSpace>
    </template>
  </NModal>
</template>
