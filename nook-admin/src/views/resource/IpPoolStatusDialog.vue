<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Activity, ChevronDown, ChevronRight, RefreshCw, ShieldCheck, Server } from 'lucide-vue-next'
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
  getSocks5Status,
  setSocks5Autostart,
  type ResourceIpPool,
  type Socks5ServiceStatus
} from '@/api/resource/ip-pool'

interface Props {
  modelValue: boolean
  ip?: ResourceIpPool | null
}
const props = defineProps<Props>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  /** 状态/自启变化后通知父组件刷新列表 (DB.autostart_enabled 可能更新). */
  (e: 'changed'): void
}>()

const message = useMessage()
const { confirm } = useConfirm()

const statusLoading = ref(false)
const autostartLoading = ref(false)
const serviceStatus = ref<Socks5ServiceStatus | null>(null)

watch(
  () => [props.modelValue, props.ip?.id],
  ([open]) => {
    if (open) {
      serviceStatus.value = null
      runStatus()
    }
  }
)

async function runStatus() {
  if (!props.ip || statusLoading.value) return
  statusLoading.value = true
  try {
    serviceStatus.value = await getSocks5Status(props.ip.id)
  } catch (e) {
    serviceStatus.value = null
    message.error('拉 SOCKS5 服务状态失败: ' + ((e as Error).message ?? ''))
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
  if (e === 'disabled') return '系统重启后 dante 不会自动起'
  return '未知'
})

async function onAutostartToggle(target: boolean) {
  if (!props.ip || autostartLoading.value) return
  const label = props.ip.ipAddress
  const ok = await confirm({
    title: target ? '开启开机自启' : '关闭开机自启',
    message: target
      ? `开启 "${label}" 的 SOCKS5 (dante) 开机自启?`
      : `关闭 "${label}" 的 SOCKS5 (dante) 开机自启? 系统重启后 dante 将不会自动起`,
    type: target ? 'info' : 'warning',
    confirmText: target ? '开启' : '关闭'
  })
  if (!ok) return
  autostartLoading.value = true
  try {
    await setSocks5Autostart(props.ip.id, target)
    message.success(`${label}: ${target ? '已开启自启' : '已关闭自启'}`)
    await runStatus()
    emit('changed')
  } catch (e) {
    message.error('切换自启失败: ' + ((e as Error).message ?? ''))
  } finally {
    autostartLoading.value = false
  }
}

const autostartSwitchDisabled = computed(() => {
  if (autostartLoading.value || statusLoading.value) return true
  if (!serviceStatus.value) return true
  const e = serviceStatus.value.enabled?.trim() ?? ''
  return e !== 'enabled' && e !== 'disabled'
})

function autostartRailStyle({ checked }: { checked: boolean }) {
  return {
    background: checked ? 'var(--n-success-color, #18a058)' : '#d0d0d6'
  }
}

/** 主机信息折叠状态: 默认收起. */
const hostInfoExpanded = ref(false)
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
      <span>SOCKS5 服务状态</span>
    </template>
    <template #header-extra>
      <span v-if="ip" class="text-xs text-zinc-500">
        {{ ip.ipAddress }}<span v-if="ip.socks5Port" class="text-zinc-400">:{{ ip.socks5Port }}</span>
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
          dante 服务
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
            <div class="text-xs text-zinc-500">dante 版本</div>
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

      <!-- UFW 防火墙 (始终展示) -->
      <NCard v-if="serviceStatus" size="small" class="mt-3">
        <div class="text-xs font-semibold text-zinc-500 mb-2 flex items-center gap-1">
          <NIcon :size="14"><ShieldCheck /></NIcon>
          UFW 防火墙
        </div>
        <pre class="font-mono text-xs whitespace-pre-wrap break-all m-0 max-h-48 overflow-auto bg-zinc-50 dark:bg-zinc-900 px-3 py-2 rounded">{{
          serviceStatus.ufwStatus || '(未获取到)'
        }}</pre>
      </NCard>

      <!-- 主机基本信息 (默认折叠) -->
      <NCard v-if="serviceStatus?.hostInfo" size="small" class="mt-3">
        <div
          class="text-xs font-semibold text-zinc-500 flex items-center gap-1 cursor-pointer select-none"
          @click="hostInfoExpanded = !hostInfoExpanded"
        >
          <NIcon :size="14">
            <ChevronDown v-if="hostInfoExpanded" />
            <ChevronRight v-else />
          </NIcon>
          <NIcon :size="14"><Server /></NIcon>
          主机信息
          <span class="text-zinc-400 font-normal ml-1">({{ hostInfoExpanded ? '点击收起' : '点击展开' }})</span>
        </div>
        <div v-if="hostInfoExpanded" class="grid grid-cols-1 sm:grid-cols-2 gap-x-4 gap-y-2 text-sm mt-3">
          <div>
            <div class="text-xs text-zinc-500">主机名</div>
            <div class="font-mono text-xs">{{ serviceStatus.hostInfo.hostname || '-' }}</div>
          </div>
          <div>
            <div class="text-xs text-zinc-500">系统</div>
            <div class="text-xs">{{ serviceStatus.hostInfo.osRelease || '-' }}</div>
          </div>
          <div>
            <div class="text-xs text-zinc-500">内核</div>
            <div class="font-mono text-xs">{{ serviceStatus.hostInfo.kernel || '-' }}</div>
          </div>
          <div>
            <div class="text-xs text-zinc-500">时区</div>
            <div class="font-mono text-xs">{{ serviceStatus.hostInfo.timezone || '-' }}</div>
          </div>
          <div>
            <div class="text-xs text-zinc-500">运行时间</div>
            <div class="text-xs">{{ serviceStatus.hostInfo.systemUptime || '-' }}</div>
          </div>
          <div>
            <div class="text-xs text-zinc-500">负载 (1/5/15 分钟)</div>
            <div class="font-mono text-xs">{{ serviceStatus.hostInfo.loadAvg || '-' }}</div>
          </div>
          <div>
            <div class="text-xs text-zinc-500">内存</div>
            <div class="font-mono text-xs">{{ serviceStatus.hostInfo.memory || '-' }}</div>
          </div>
          <div>
            <div class="text-xs text-zinc-500">磁盘 (/)</div>
            <div class="font-mono text-xs">{{ serviceStatus.hostInfo.disk || '-' }}</div>
          </div>
        </div>
      </NCard>
    </NSpin>

    <template #footer>
      <NSpace justify="end">
        <NButton size="small" @click="close">关闭</NButton>
      </NSpace>
    </template>
  </NModal>
</template>
