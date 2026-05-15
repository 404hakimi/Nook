<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { CheckCircle2, RefreshCw, ShieldAlert } from 'lucide-vue-next'
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
import { getSyncStatus, replayServer, type ReplayReport, type SyncStatus } from '@/api/xray/client'
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

const syncStatusLoading = ref(false)
const replayLoading = ref(false)
const syncStatus = ref<SyncStatus | null>(null)

watch(
  () => [props.modelValue, props.node?.serverId],
  ([open]) => {
    if (open) {
      syncStatus.value = null
      runSyncStatus()
    }
  }
)

async function runSyncStatus() {
  if (!props.node || syncStatusLoading.value) return
  syncStatusLoading.value = true
  try {
    syncStatus.value = await getSyncStatus(props.node.serverId)
  } catch (e) {
    syncStatus.value = null
    message.error('查看差异失败: ' + ((e as Error).message ?? ''))
  } finally {
    syncStatusLoading.value = false
  }
}

/**
 * 推送修复: 走 SERVER_REPLAY (后端内部仍会 lsi 二次校验, 只推未对齐).
 * 当前显示的 syncStatus 是入口前的快照, 实际推送数以后端报告为准.
 */
async function onReplay() {
  if (!props.node || replayLoading.value) return
  const tags = syncStatus.value?.staleDbTags ?? []
  if (tags.length === 0) return
  const label = props.node.serverName || props.node.serverId.slice(0, 12)
  replayLoading.value = true
  try {
    const report: ReplayReport = await replayServer(props.node.serverId)
    const tip = `总 ${report.totalCount} · 已对齐 ${report.alreadyOkCount} · 推送 ${report.successCount}`
    if (report.failedClientIds.length === 0) {
      message.success(`${label}: 推送完成 (${tip})`)
    } else {
      message.warning(
        `${label}: 推送部分失败 ${tip} · 失败 ${report.failedClientIds.length} (已标 status=3 等下轮自动重试)`
      )
    }
    await runSyncStatus()
  } catch (e) {
    message.error('推送失败: ' + ((e as Error).message ?? ''))
  } finally {
    replayLoading.value = false
  }
}

/** 推送按钮文案动态: 已对齐时禁用, 不可达时禁用, 否则显示缺失数 */
const replayButton = computed<{ label: string; disabled: boolean; tip: string }>(() => {
  if (!syncStatus.value) {
    return { label: '推送修复', disabled: true, tip: '查看差异加载中...' }
  }
  if (!syncStatus.value.reachable) {
    return { label: '推送修复', disabled: true, tip: '远端不可达, 无法推送' }
  }
  const n = syncStatus.value.staleDbTags.length
  if (n === 0) {
    return { label: '✓ 已对齐, 无需推送', disabled: true, tip: '远端与 DB 一致, 不需要推送' }
  }
  return {
    label: `立即推送修复 ${n} 个`,
    disabled: false,
    tip: `按 DB 状态把 ${n} 个缺失的 inbound 重建到远端 (其余 ${syncStatus.value.okTags.length} 个已对齐, 不动)`
  }
})

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
      <span>查看差异 (远端 vs DB, 只读)</span>
    </template>
    <template #header-extra>
      <span v-if="node" class="text-xs text-zinc-500">
        {{ node.serverName || node.serverId }} <span v-if="node.serverHost">({{ node.serverHost }})</span>
      </span>
    </template>

    <NSpin :show="syncStatusLoading && !syncStatus">
      <NCard size="small" class="min-h-[6rem]">
        <div class="flex items-center justify-between mb-2">
          <div class="text-xs font-semibold text-zinc-500 flex items-center gap-1">
            <NIcon :size="14"><ShieldAlert /></NIcon>
            查看差异
          </div>
          <NButton
            quaternary
            size="tiny"
            :loading="syncStatusLoading"
            @click="runSyncStatus"
          >
            <template #icon><NIcon><RefreshCw /></NIcon></template>
            重新查看
          </NButton>
        </div>

        <div v-if="!syncStatus" class="text-xs text-zinc-400 py-2">
          拉远端 inbound list 跟 DB 比对 (只读, 不动远端).
        </div>
        <div v-else class="space-y-2 text-sm">
          <div v-if="!syncStatus.reachable">
            <NTag size="small" type="error">不可达</NTag>
            <span class="text-xs text-zinc-500 ml-2">SSH 不通或 xray 未起, 跳过本次查看</span>
          </div>
          <template v-else>
            <div class="grid grid-cols-3 gap-2">
              <div class="flex items-center gap-1">
                <NIcon :size="14" color="var(--n-success-color)"><CheckCircle2 /></NIcon>
                <span class="text-xs text-zinc-500">OK</span>
                <span class="font-mono font-semibold">{{ syncStatus.okTags.length }}</span>
              </div>
              <div class="flex items-center gap-1">
                <span class="text-xs text-zinc-500">DB 有远端无</span>
                <span
                  class="font-mono font-semibold"
                  :style="syncStatus.staleDbTags.length > 0 ? 'color: var(--n-warning-color)' : ''"
                >{{ syncStatus.staleDbTags.length }}</span>
              </div>
              <div class="flex items-center gap-1">
                <span class="text-xs text-zinc-500">远端孤儿</span>
                <span
                  class="font-mono font-semibold"
                  :style="syncStatus.orphanRemoteTags.length > 0 ? 'color: var(--n-info-color)' : ''"
                >{{ syncStatus.orphanRemoteTags.length }}</span>
              </div>
            </div>

            <div
              v-if="syncStatus.staleDbTags.length > 0"
              class="text-xs"
              style="color: var(--n-warning-color)"
            >
              缺失: {{ syncStatus.staleDbTags.join(', ') }}
              <span class="text-zinc-500 ml-1">- 点下方"推送修复"按钮恢复</span>
            </div>
            <div
              v-if="syncStatus.orphanRemoteTags.length > 0"
              class="text-xs text-zinc-500"
            >
              孤儿: {{ syncStatus.orphanRemoteTags.join(', ') }}
              <span class="text-zinc-400 ml-1">- 当前不自动清理</span>
            </div>
            <div
              v-if="syncStatus.staleDbTags.length === 0 && syncStatus.orphanRemoteTags.length === 0"
              class="text-xs"
              style="color: var(--n-success-color)"
            >
              ✓ 已对齐
            </div>
          </template>
        </div>
      </NCard>
    </NSpin>

    <template #footer>
      <NSpace justify="end">
        <NButton size="small" @click="close">关闭</NButton>
        <NButton
          type="warning"
          size="small"
          :loading="replayLoading"
          :disabled="replayButton.disabled"
          :title="replayButton.tip"
          @click="onReplay"
        >
          {{ replayButton.label }}
        </NButton>
      </NSpace>
    </template>
  </NModal>
</template>
