<script setup lang="ts">
import { computed } from 'vue'
import { AlertCircle, CheckCircle2, Copy, Pencil, Plug, XCircle } from 'lucide-vue-next'
import { NButton, NIcon, NTag, useMessage } from 'naive-ui'
import type { ServerLanding, Socks5ServiceStatus } from '@/api/resource/server-landing'

/**
 * 落地机详情 — SSH 凭据 tab.
 *
 * <p>展示 host/port/user/password (脱敏) + 连通性. 探测走父组件 loadStatus.
 */
const props = defineProps<{
  detail: ServerLanding
  canManage: boolean
  statusData: Socks5ServiceStatus | null
  statusLoading: boolean
  statusError: string
}>()
const emit = defineEmits<{
  'edit-credential': []
  'load-status': []
}>()

const message = useMessage()

const sshReachable = computed<'unknown' | 'open' | 'closed'>(() => {
  if (props.statusError) return 'closed'
  if (!props.statusData) return 'unknown'
  return props.statusData.hostInfo ? 'open' : 'unknown'
})

async function copyToClipboard(value: string | undefined, label: string) {
  if (!value) { message.warning(`${label} 为空`); return }
  try {
    await navigator.clipboard.writeText(value)
    message.success(`已复制 ${label}`)
  } catch { message.warning('复制失败') }
}
</script>

<template>
  <div class="tab-actions">
    <NButton size="small" type="primary" @click="emit('edit-credential')">
      <template #icon><NIcon><Pencil /></NIcon></template>
      编辑 SSH 凭据
    </NButton>
    <NButton size="small" quaternary :loading="statusLoading" :disabled="!canManage" @click="emit('load-status')">
      <template #icon><NIcon><Plug /></NIcon></template>
      测试连通性
    </NButton>
  </div>

  <div v-if="!detail.ipAddress" class="empty-hint">
    <NIcon :size="18"><AlertCircle /></NIcon>
    <div>
      <div class="font-semibold">尚未配置 SSH 凭据</div>
      <div class="text-xs text-zinc-500 mt-1">装机 / 装 Agent / 看日志都依赖 SSH</div>
    </div>
  </div>
  <div v-else class="info-grid">
    <div class="info-row"><span class="k">host</span><code class="v">{{ detail.ipAddress }}</code>
      <NButton quaternary size="tiny" circle @click="copyToClipboard(detail.ipAddress, 'SSH host')">
        <template #icon><NIcon><Copy /></NIcon></template>
      </NButton>
    </div>
    <div class="info-row"><span class="k">port</span><span class="v">{{ detail.sshPort ?? 22 }}</span></div>
    <div class="info-row"><span class="k">user</span><code class="v">{{ detail.sshUser || '-' }}</code></div>
    <div class="info-row"><span class="k">password</span>
      <NTag v-if="detail.sshPassword" size="tiny" type="success">已配置 (mask)</NTag>
      <NTag v-else size="tiny" type="warning">未配置</NTag>
    </div>
    <div class="info-row info-row--full"><span class="k">连通性</span>
      <NTag v-if="sshReachable === 'unknown'" size="tiny">未探测</NTag>
      <NTag v-else-if="sshReachable === 'open'" size="tiny" type="success">
        <template #icon><NIcon><CheckCircle2 /></NIcon></template> 可达
      </NTag>
      <NTag v-else size="tiny" type="error">
        <template #icon><NIcon><XCircle /></NIcon></template> 不可达
        <span v-if="statusError" class="ml-1">({{ statusError }})</span>
      </NTag>
    </div>
  </div>
</template>

<style scoped src="./landing-tabs.scss"></style>
