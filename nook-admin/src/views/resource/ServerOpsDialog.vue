<script setup lang="ts">
import { ref, watch } from 'vue'
import {
  Cpu,
  HardDrive,
  MemoryStick,
  RefreshCw,
  Server,
  Timer
} from 'lucide-vue-next'
import {
  NButton,
  NCard,
  NIcon,
  NModal,
  NSpace,
  NSpin,
  useMessage
} from 'naive-ui'
import { getServerSystemInfo, type ServerSystemInfo } from '@/api/xray/server'
import type { ResourceServer } from '@/api/resource/server'

interface Props {
  modelValue: boolean
  server?: ResourceServer | null
}
const props = defineProps<Props>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
}>()

const message = useMessage()

const loading = ref(false)
const systemInfo = ref<ServerSystemInfo | null>(null)

watch(
  () => [props.modelValue, props.server?.id],
  ([open]) => {
    if (open) {
      systemInfo.value = null
      runRefresh()
    }
  }
)

/** 拉系统信息; 失败仅 toast 提示, 字段保持 null 让模板回落到 "-". */
async function runRefresh() {
  if (!props.server || loading.value) return
  loading.value = true
  try {
    systemInfo.value = await getServerSystemInfo(props.server.id)
  } catch (e) {
    systemInfo.value = null
    message.error('拉系统信息失败: ' + ((e as Error).message ?? ''))
  } finally {
    loading.value = false
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
    style="max-width: 48rem"
    :bordered="false"
    :mask-closable="true"
    @update:show="(v: boolean) => emit('update:modelValue', v)"
  >
    <template #header>
      <span>服务器信息</span>
    </template>
    <template #header-extra>
      <span v-if="server" class="text-xs text-zinc-500">
        {{ server.name }} ({{ server.host }})
      </span>
    </template>

    <div class="flex items-center justify-end mb-3">
      <NButton quaternary size="small" :disabled="loading" @click="runRefresh">
        <template #icon><NIcon><RefreshCw /></NIcon></template>
        刷新
      </NButton>
    </div>

    <NSpin :show="loading && !systemInfo">
      <NCard size="small">
        <div class="text-xs font-semibold text-zinc-500 mb-2 flex items-center gap-1">
          <NIcon :size="14"><Server /></NIcon>
          系统信息
        </div>
        <div v-if="systemInfo" class="grid grid-cols-2 sm:grid-cols-4 gap-3 text-sm">
          <div>
            <div class="text-xs text-zinc-500">主机名</div>
            <div class="font-mono text-xs truncate" :title="systemInfo.hostname">
              {{ systemInfo.hostname || '-' }}
            </div>
          </div>
          <div>
            <div class="text-xs text-zinc-500">时区</div>
            <div class="font-mono text-xs">{{ systemInfo.timezone || '-' }}</div>
          </div>
          <div class="col-span-2">
            <div class="text-xs text-zinc-500">操作系统 / 内核</div>
            <div class="text-xs">
              {{ systemInfo.osRelease || '-' }}
              <span class="text-zinc-400 font-mono">{{ systemInfo.kernel }}</span>
            </div>
          </div>
          <div class="col-span-2">
            <div class="text-xs text-zinc-500 flex items-center gap-1">
              <NIcon :size="12"><Timer /></NIcon> 系统已运行
            </div>
            <div class="text-xs">{{ systemInfo.systemUptime || '-' }}</div>
          </div>
          <div class="col-span-2">
            <div class="text-xs text-zinc-500 flex items-center gap-1">
              <NIcon :size="12"><Cpu /></NIcon> 负载均值 (1/5/15min)
            </div>
            <div class="font-mono text-xs">{{ systemInfo.loadAvg || '-' }}</div>
          </div>
          <div>
            <div class="text-xs text-zinc-500 flex items-center gap-1">
              <NIcon :size="12"><MemoryStick /></NIcon> 内存
            </div>
            <div class="font-mono text-xs">{{ systemInfo.memory || '-' }}</div>
          </div>
          <div>
            <div class="text-xs text-zinc-500 flex items-center gap-1">
              <NIcon :size="12"><HardDrive /></NIcon> 磁盘 (/)
            </div>
            <div class="font-mono text-xs">{{ systemInfo.disk || '-' }}</div>
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
