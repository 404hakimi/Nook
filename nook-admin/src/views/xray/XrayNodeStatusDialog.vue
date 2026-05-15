<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Activity, RefreshCw } from 'lucide-vue-next'
import {
  NButton,
  NCard,
  NIcon,
  NModal,
  NSpace,
  NSpin,
  NSwitch,
  NTag,
  useMessage
} from 'naive-ui'
import { useConfirm } from '@/composables/useConfirm'
import {
  getXrayServiceStatus,
  xrayAutostart,
  type XrayServiceStatus
} from '@/api/xray/server'
import type { XrayNode } from '@/api/xray/node'

interface Props {
  modelValue: boolean
  node?: XrayNode | null
}
const props = defineProps<Props>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
}>()

const message = useMessage()
const { confirm } = useConfirm()

const statusLoading = ref(false)
const autostartLoading = ref(false)
const serviceStatus = ref<XrayServiceStatus | null>(null)

watch(
  () => [props.modelValue, props.node?.serverId],
  ([open]) => {
    if (open) {
      serviceStatus.value = null
      runStatus()
    }
  }
)

async function runStatus() {
  if (!props.node || statusLoading.value) return
  statusLoading.value = true
  try {
    serviceStatus.value = await getXrayServiceStatus(props.node.serverId)
  } catch (e) {
    serviceStatus.value = null
    message.error('拉 Xray 服务状态失败: ' + ((e as Error).message ?? ''))
  } finally {
    statusLoading.value = false
  }
}

function close() {
  emit('update:modelValue', false)
}

const activeBadge = computed<{ text: string; type: 'success' | 'error' | 'warning' | 'default' }>(
  () => {
    const a = serviceStatus.value?.active?.trim() ?? ''
    if (a === 'active') return { text: '运行中', type: 'success' }
    if (a === 'inactive') return { text: '未运行', type: 'error' }
    if (a === 'failed') return { text: '失败', type: 'error' }
    if (!a) return { text: '未知', type: 'default' }
    return { text: a, type: 'warning' }
  }
)

/** 当前是否已开机自启 (基于 systemctl is-enabled 结果). serviceStatus 没拿到时返 null = 未知 */
const isAutostartEnabled = computed<boolean | null>(() => {
  const e = serviceStatus.value?.enabled?.trim() ?? ''
  if (!e) return null
  return e === 'enabled'
})

const autostartHint = computed(() => {
  const e = serviceStatus.value?.enabled?.trim() ?? ''
  if (e === 'static') return 'static (单元文件本身没 [Install] 段, 无法 enable/disable)'
  if (e === 'masked') return 'masked (被 mask, 需手动 unmask 后才能 enable)'
  if (e === 'enabled') return '开机会自动拉起'
  if (e === 'disabled') return '系统重启后 xray 不会自动起'
  return '未知'
})

/**
 * 切换自启: 走 confirm → API → 刷 status. NSwitch :value 受控, 拒绝时不动开关位置.
 * static / masked 不应该弹切换, NSwitch 在这种情况直接 disabled.
 */
async function onAutostartToggle(target: boolean) {
  if (!props.node || autostartLoading.value) return
  const label = props.node.serverName || props.node.serverId.slice(0, 12)
  const ok = await confirm({
    title: target ? '开启开机自启' : '关闭开机自启',
    message: target
      ? `开启 "${label}" 的 Xray 开机自启?`
      : `关闭 "${label}" 的 Xray 开机自启? 系统重启后 xray 将不会自动起`,
    type: target ? 'info' : 'warning',
    confirmText: target ? '开启' : '关闭'
  })
  if (!ok) return
  autostartLoading.value = true
  try {
    await xrayAutostart(props.node.serverId, target)
    message.success(`${label}: ${target ? '已开启自启' : '已关闭自启'}`)
    // 后端切完成后立即重拉 service status, 让 NSwitch 反映新状态
    await runStatus()
  } catch (e) {
    message.error('切换自启失败: ' + ((e as Error).message ?? ''))
  } finally {
    autostartLoading.value = false
  }
}

/** static / masked / 未拿到 status 时 NSwitch 禁用 */
const autostartSwitchDisabled = computed(() => {
  if (autostartLoading.value || statusLoading.value) return true
  if (!serviceStatus.value) return true
  const e = serviceStatus.value.enabled?.trim() ?? ''
  return e !== 'enabled' && e !== 'disabled'
})

/** NSwitch 自定义轨道色: 开 = 绿色 (success), 关 = 浅灰 (default-disabled 风格) */
function autostartRailStyle({ checked }: { checked: boolean }) {
  return {
    background: checked ? 'var(--n-success-color, #18a058)' : '#d0d0d6'
  }
}
</script>

<template>
  <NModal
    :show="modelValue"
    preset="card"
    style="max-width: 48rem"
    :bordered="false"
    :mask-closable="true"
    @update:show="(v: boolean) => emit('update:modelValue', v)"
  >
    <template #header>
      <span>Xray 服务状态</span>
    </template>
    <template #header-extra>
      <span v-if="node" class="text-xs text-zinc-500">
        {{ node.serverName || node.serverId }} <span v-if="node.serverHost">({{ node.serverHost }})</span>
      </span>
    </template>

    <div class="flex justify-end mb-3">
      <NButton quaternary size="small" :disabled="statusLoading" @click="runStatus">
        <template #icon><NIcon><RefreshCw /></NIcon></template>
        刷新状态
      </NButton>
    </div>

    <NSpin :show="statusLoading && !serviceStatus">
      <NCard size="small" class="min-h-[8rem]">
        <div class="text-xs font-semibold text-zinc-500 mb-2 flex items-center gap-1">
          <NIcon :size="14"><Activity /></NIcon>
          Xray 服务
        </div>
        <div v-if="serviceStatus" class="grid grid-cols-1 sm:grid-cols-2 gap-3 text-sm">
          <div>
            <div class="text-xs text-zinc-500">运行状态</div>
            <NTag size="small" :type="activeBadge.type">{{ activeBadge.text }}</NTag>
          </div>
          <div>
            <div class="text-xs text-zinc-500 mb-1">开机自启</div>
            <div class="flex items-center gap-3">
              <NSwitch
                :value="isAutostartEnabled === true"
                :loading="autostartLoading"
                :disabled="autostartSwitchDisabled"
                size="small"
                :rail-style="autostartRailStyle"
                @update:value="onAutostartToggle"
              />
              <span class="text-xs text-zinc-500">{{ autostartHint }}</span>
            </div>
          </div>
          <div>
            <div class="text-xs text-zinc-500">Xray 版本</div>
            <div class="font-mono text-xs">{{ serviceStatus.version || '-' }}</div>
          </div>
          <div class="sm:col-span-2">
            <div class="text-xs text-zinc-500">启动时间</div>
            <div class="text-xs">{{ serviceStatus.uptimeFrom || '-' }}</div>
          </div>
          <div class="sm:col-span-2">
            <div class="text-xs text-zinc-500">监听端口</div>
            <pre class="font-mono text-xs whitespace-pre-wrap break-all m-0">{{
              serviceStatus.listening || '(未捕获)'
            }}</pre>
          </div>
        </div>
        <div v-else class="text-xs text-zinc-400 py-2">(未获取到)</div>
      </NCard>
    </NSpin>

    <template #footer>
      <NSpace justify="end">
        <NButton size="small" @click="close">关闭</NButton>
      </NSpace>
    </template>
  </NModal>
</template>
