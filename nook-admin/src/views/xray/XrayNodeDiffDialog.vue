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

/** client UUID 32 hex 太长, 表里只显示前 8 位. */
const shortId = (id: string) => id.slice(0, 8)

/** 当前快照里需要修复的总条数 (三维度 staleDb 之和). */
const totalStale = computed(() => {
  const s = syncStatus.value
  if (!s) return 0
  return s.staleDbEmails.length + s.staleDbOutbounds.length + s.staleDbRules.length
})

/** 当前快照里所有孤儿条数 (三维度 orphan 之和), 仅用于摘要展示. */
const totalOrphan = computed(() => {
  const s = syncStatus.value
  if (!s) return 0
  return s.orphanRemoteEmails.length + s.orphanRemoteOutbounds.length + s.orphanRemoteRules.length
})

/**
 * 推送修复: 走 SERVER_REPLAY (后端会幂等推三段 user/rule/outbound).
 * 当前显示的 syncStatus 是入口前的快照, 实际推送以后端报告为准.
 */
async function onReplay() {
  if (!props.node || replayLoading.value || totalStale.value === 0) return
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

const replayButton = computed<{ label: string; disabled: boolean; tip: string }>(() => {
  if (!syncStatus.value) {
    return { label: '推送修复', disabled: true, tip: '查看差异加载中...' }
  }
  if (!syncStatus.value.reachable) {
    return { label: '推送修复', disabled: true, tip: '远端不可达, 无法推送' }
  }
  const n = totalStale.value
  if (n === 0) {
    return { label: '✓ 已对齐, 无需推送', disabled: true, tip: '远端与 DB 一致, 不需要推送' }
  }
  return {
    label: `立即推送修复 ${n} 项`,
    disabled: false,
    tip: `按 DB 状态推 user / rule / outbound 三段到远端 (幂等)`
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
    style="max-width: 52rem"
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
            三维度对账 (user / outbound / rule)
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
          拉远端 user / outbound / rule 跟 DB 比对 (只读, 不动远端).
        </div>
        <div v-else class="space-y-3 text-sm">
          <div v-if="!syncStatus.reachable">
            <NTag size="small" type="error">不可达</NTag>
            <span class="text-xs text-zinc-500 ml-2">SSH 不通或 xray 未起, 跳过本次查看</span>
          </div>
          <template v-else>
            <!-- 总览 -->
            <div class="flex items-center gap-4 text-xs">
              <div class="flex items-center gap-1">
                <NIcon :size="14" color="var(--n-success-color)"><CheckCircle2 /></NIcon>
                <span class="text-zinc-500">用户对齐</span>
                <span class="font-mono font-semibold">{{ syncStatus.okEmails.length }}</span>
              </div>
              <div class="flex items-center gap-1">
                <span class="text-zinc-500">待修复</span>
                <span
                  class="font-mono font-semibold"
                  :style="totalStale > 0 ? 'color: var(--n-warning-color)' : ''"
                >{{ totalStale }}</span>
              </div>
              <div class="flex items-center gap-1">
                <span class="text-zinc-500">孤儿</span>
                <span
                  class="font-mono font-semibold"
                  :style="totalOrphan > 0 ? 'color: var(--n-info-color)' : ''"
                >{{ totalOrphan }}</span>
              </div>
            </div>

            <!-- 用户维度 -->
            <div class="border-t pt-2">
              <div class="text-xs font-semibold text-zinc-500 mb-1">
                共享 inbound 上的 user
                <span class="text-zinc-400 font-normal ml-1">
                  (OK {{ syncStatus.okEmails.length }} · 缺 {{ syncStatus.staleDbEmails.length }} · 孤儿 {{ syncStatus.orphanRemoteEmails.length }})
                </span>
              </div>
              <div
                v-if="syncStatus.staleDbEmails.length > 0"
                class="text-xs"
                style="color: var(--n-warning-color)"
              >
                缺失: {{ syncStatus.staleDbEmails.join(', ') }}
                <span class="text-zinc-500 ml-1">— 客户连不上, 点下方"推送修复"</span>
              </div>
              <div
                v-if="syncStatus.orphanRemoteEmails.length > 0"
                class="text-xs text-zinc-500"
              >
                孤儿: {{ syncStatus.orphanRemoteEmails.join(', ') }}
                <span class="text-zinc-400 ml-1">— 不自动清</span>
              </div>
              <div
                v-if="syncStatus.staleDbEmails.length === 0 && syncStatus.orphanRemoteEmails.length === 0"
                class="text-xs"
                style="color: var(--n-success-color)"
              >
                ✓ 已对齐
              </div>
            </div>

            <!-- 出站维度 (tag = clientId) -->
            <div class="border-t pt-2">
              <div class="text-xs font-semibold text-zinc-500 mb-1">
                动态 socks 出站 (tag = clientId)
                <span class="text-zinc-400 font-normal ml-1">
                  (缺 {{ syncStatus.staleDbOutbounds.length }} · 孤儿 {{ syncStatus.orphanRemoteOutbounds.length }})
                </span>
              </div>
              <div
                v-if="syncStatus.staleDbOutbounds.length > 0"
                class="text-xs"
                style="color: var(--n-warning-color)"
              >
                缺失: {{ syncStatus.staleDbOutbounds.map(shortId).join(', ') }}
                <span class="text-zinc-500 ml-1">— 远端缺动态 socks, 流量进 blackhole 兜底被丢; 推送修复</span>
              </div>
              <div
                v-if="syncStatus.orphanRemoteOutbounds.length > 0"
                class="text-xs text-zinc-500"
              >
                孤儿: {{ syncStatus.orphanRemoteOutbounds.map(shortId).join(', ') }}
                <span class="text-zinc-400 ml-1">— DB 没对应 client (revoke 残留), 不自动清</span>
              </div>
              <div
                v-if="syncStatus.staleDbOutbounds.length === 0 && syncStatus.orphanRemoteOutbounds.length === 0"
                class="text-xs"
                style="color: var(--n-success-color)"
              >
                ✓ 已对齐
              </div>
            </div>

            <!-- 路由规则维度 (tag = rule_<clientId>) -->
            <div class="border-t pt-2">
              <div class="text-xs font-semibold text-zinc-500 mb-1">
                路由规则 (tag = rule_&lt;clientId&gt;)
                <span class="text-zinc-400 font-normal ml-1">
                  (缺 {{ syncStatus.staleDbRules.length }} · 孤儿 {{ syncStatus.orphanRemoteRules.length }})
                </span>
              </div>
              <div
                v-if="syncStatus.staleDbRules.length > 0"
                class="text-xs"
                style="color: var(--n-warning-color)"
              >
                缺失: {{ syncStatus.staleDbRules.map(shortId).join(', ') }}
                <span class="text-zinc-500 ml-1">— 流量进 blackhole 兜底被丢, 推送修复</span>
              </div>
              <div
                v-if="syncStatus.orphanRemoteRules.length > 0"
                class="text-xs text-zinc-500"
              >
                孤儿: {{ syncStatus.orphanRemoteRules.map(shortId).join(', ') }}
                <span class="text-zinc-400 ml-1">— DB 没对应 client, 不自动清</span>
              </div>
              <div
                v-if="syncStatus.staleDbRules.length === 0 && syncStatus.orphanRemoteRules.length === 0"
                class="text-xs"
                style="color: var(--n-success-color)"
              >
                ✓ 已对齐
              </div>
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
