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
  NTag,
  useMessage
} from 'naive-ui'
import { getServerSystemdStatus, type SystemdStatus } from '@/api/resource/server-ops'
import { type XrayInstall } from '@/api/xray/xray-install'

/** Xray 走公共 systemd 接口的固定 unit 名. */
const XRAY_UNIT = 'xray'

interface Props {
  modelValue: boolean
  server?: XrayInstall | null
}
const props = defineProps<Props>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
}>()

const message = useMessage()

const statusLoading = ref(false)
const serviceStatus = ref<SystemdStatus | null>(null)

watch(
  () => [props.modelValue, props.server?.serverId],
  ([open]) => {
    if (open) {
      serviceStatus.value = null
      runStatus()
    }
  }
)

async function runStatus() {
  if (!props.server || statusLoading.value) return
  statusLoading.value = true
  try {
    serviceStatus.value = await getServerSystemdStatus(props.server.serverId, XRAY_UNIT)
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
      <span v-if="server" class="text-xs text-zinc-500">
        {{ server.serverName || server.serverId }} <span v-if="server.serverHost">({{ server.serverHost }})</span>
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
            <div class="text-xs text-zinc-500">Xray 版本</div>
            <div class="font-mono text-xs">{{ server?.xrayVersion || '-' }}</div>
          </div>
          <div class="sm:col-span-2">
            <div class="text-xs text-zinc-500">启动时间</div>
            <div class="text-xs">{{ serviceStatus.uptimeFrom || '-' }}</div>
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
