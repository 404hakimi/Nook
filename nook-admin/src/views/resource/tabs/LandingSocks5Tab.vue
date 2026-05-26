<script setup lang="ts">
import { computed } from 'vue'
import {
  AlertCircle,
  Copy,
  FileText,
  Pencil,
  RefreshCcw,
  Rocket,
  Zap
} from 'lucide-vue-next'
import {
  NButton,
  NDescriptions,
  NDescriptionsItem,
  NIcon,
  NTag,
  useMessage
} from 'naive-ui'
import type {
  ServerLanding,
  ServerLandingInstall,
  Socks5ServiceStatus
} from '@/api/resource/server-landing'
import { formatDateTime } from '@/utils/date'

/**
 * 落地机详情 — SOCKS5 服务 tab.
 *
 * <p>展示凭据 (端点/用户/密码) + 部署配置 (install 子表全字段) + 远端 dante 状态 (SSH 探测).
 */
const props = defineProps<{
  detail: ServerLanding
  installInfo: ServerLandingInstall | null
  canManage: boolean
  canTest: boolean
  isSelfDeploy: boolean
  isInstalling: boolean
  isLive: boolean
  statusData: Socks5ServiceStatus | null
  statusLoading: boolean
  statusError: string
}>()
const emit = defineEmits<{
  'edit-socks5': []
  'open-deploy': []
  'open-test': []
  'open-log': []
  'load-status': []
}>()

const message = useMessage()

const socks5Endpoint = computed(() => {
  if (!props.detail.ipAddress || !props.detail.socks5Port) return ''
  return `${props.detail.ipAddress}:${props.detail.socks5Port}`
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
    <NButton size="small" type="primary" @click="emit('edit-socks5')">
      <template #icon><NIcon><Pencil /></NIcon></template>
      编辑 dante 配置
    </NButton>
    <NButton
      v-if="isSelfDeploy && (isInstalling || isLive)"
      size="small"
      :type="isLive ? 'default' : 'primary'"
      :quaternary="isLive"
      @click="emit('open-deploy')"
    >
      <template #icon><NIcon><Rocket /></NIcon></template>
      {{ isLive ? '重装' : '装机' }}
    </NButton>
    <NButton v-if="canTest" size="small" quaternary type="warning" @click="emit('open-test')">
      <template #icon><NIcon><Zap /></NIcon></template>
      拨号测试
    </NButton>
    <NButton size="small" quaternary :disabled="!canManage" @click="emit('open-log')">
      <template #icon><NIcon><FileText /></NIcon></template>
      查看日志
    </NButton>
  </div>

  <div v-if="!socks5Endpoint" class="empty-hint">
    <NIcon :size="18"><AlertCircle /></NIcon>
    <div>
      <div class="font-semibold">尚未配置 SOCKS5</div>
      <div class="text-xs text-zinc-500 mt-1">点 "编辑 dante 配置" 填端口/用户/密码</div>
    </div>
  </div>
  <div v-else class="space-y-3">
    <!-- ===== Section 1: 凭据 ===== -->
    <div class="socks5-section">
      <div class="section-header">
        <span class="section-title">凭据</span>
        <NTag size="tiny" type="warning" class="ml-1">敏感信息</NTag>
      </div>
      <NDescriptions bordered size="small" label-placement="left" :column="1" label-style="width: 7.5rem; white-space: nowrap">
        <NDescriptionsItem label="端点">
          <div class="cred-row">
            <code class="kbd">{{ socks5Endpoint }}</code>
            <NButton text size="tiny" @click="copyToClipboard(socks5Endpoint, 'SOCKS5 端点')" title="复制">
              <template #icon><NIcon><Copy :size="12" /></NIcon></template>
            </NButton>
          </div>
        </NDescriptionsItem>
        <NDescriptionsItem label="用户">
          <div class="cred-row">
            <code class="kbd">{{ detail.socks5Username || '—' }}</code>
            <NButton v-if="detail.socks5Username" text size="tiny" @click="copyToClipboard(detail.socks5Username, '用户名')" title="复制">
              <template #icon><NIcon><Copy :size="12" /></NIcon></template>
            </NButton>
          </div>
        </NDescriptionsItem>
        <NDescriptionsItem label="密码">
          <div class="cred-row">
            <code v-if="detail.socks5Password" class="kbd">{{ detail.socks5Password }}</code>
            <NTag v-else size="tiny" type="warning">未配置</NTag>
            <NButton v-if="detail.socks5Password" text size="tiny" @click="copyToClipboard(detail.socks5Password, '密码')" title="复制">
              <template #icon><NIcon><Copy :size="12" /></NIcon></template>
            </NButton>
          </div>
        </NDescriptionsItem>
      </NDescriptions>
    </div>

    <!-- ===== Section 2: 部署配置 ===== -->
    <div class="socks5-section">
      <div class="section-header">
        <span class="section-title">部署配置</span>
        <span v-if="!installInfo" class="section-hint">尚未装机</span>
      </div>
      <NDescriptions bordered size="small" label-placement="left" :column="2" label-style="width: 8rem; white-space: nowrap">
        <NDescriptionsItem label="dante 版本">
          <code class="kbd">{{ installInfo?.danteVersion || '—' }}</code>
        </NDescriptionsItem>
        <NDescriptionsItem label="systemd 服务名">
          <code class="kbd">{{ installInfo?.systemdUnit || '—' }}</code>
        </NDescriptionsItem>
        <NDescriptionsItem label="systemd 自启">
          <NTag size="tiny" :type="(installInfo?.autostartEnabled ?? detail.autostartEnabled) ? 'success' : 'default'">
            {{ (installInfo?.autostartEnabled ?? detail.autostartEnabled) ? '已启用' : '未启用' }}
          </NTag>
        </NDescriptionsItem>
        <NDescriptionsItem label="UFW 防火墙">
          <NTag size="tiny" :type="(installInfo?.firewallEnabled ?? detail.firewallEnabled) ? 'success' : 'default'">
            {{ (installInfo?.firewallEnabled ?? detail.firewallEnabled) ? '已配置' : '未配置' }}
          </NTag>
        </NDescriptionsItem>
        <NDescriptionsItem label="日志轮转">
          <NTag size="tiny" :type="installInfo?.logRotateEnabled ? 'success' : 'default'">
            {{ installInfo?.logRotateEnabled ? '已配置' : '未配置' }}
          </NTag>
        </NDescriptionsItem>
        <NDescriptionsItem label="装机时间">
          <code class="kbd">{{ formatDateTime(installInfo?.installedAt || detail.installedAt) || '—' }}</code>
        </NDescriptionsItem>
        <NDescriptionsItem label="安装目录" :span="2">
          <code class="kbd">{{ installInfo?.installDir || detail.installDir || '—' }}</code>
        </NDescriptionsItem>
        <NDescriptionsItem label="日志路径" :span="2">
          <code class="kbd">{{ installInfo?.logPath || detail.logPath || '—' }}</code>
        </NDescriptionsItem>
        <NDescriptionsItem label="sockd.conf" :span="2">
          <code class="kbd">{{ installInfo?.confPath || '—' }}</code>
        </NDescriptionsItem>
        <NDescriptionsItem label="PAM 配置" :span="2">
          <code class="kbd">{{ installInfo?.pamFile || '—' }}</code>
        </NDescriptionsItem>
        <NDescriptionsItem label="密码文件" :span="2">
          <code class="kbd">{{ installInfo?.pwdFile || '—' }}</code>
        </NDescriptionsItem>
        <NDescriptionsItem v-if="installInfo?.lastDanteUptime" label="上次启动" :span="2">
          <code class="kbd">{{ formatDateTime(installInfo.lastDanteUptime) }}</code>
        </NDescriptionsItem>
      </NDescriptions>
    </div>

    <!-- ===== Section 3: 远端 dante 状态 (按需手动拉取) ===== -->
    <template v-if="canManage">
      <div class="socks5-section">
        <div class="section-header">
          <span class="section-title">远端 dante 状态</span>
          <span class="section-hint">SSH 探测 systemd / 端口</span>
          <div class="section-action">
            <NButton size="tiny" quaternary :loading="statusLoading" @click="emit('load-status')">
              <template #icon><NIcon><RefreshCcw :size="12" /></NIcon></template>
              刷新远端
            </NButton>
          </div>
        </div>
        <div v-if="!statusData && !statusLoading && !statusError" class="hint">
          未拉取; 点右上 "刷新远端" 探测
        </div>
        <div v-else-if="statusError" class="hint text-error">{{ statusError }}</div>
        <NDescriptions
          v-else-if="statusData"
          bordered size="small" label-placement="left" :column="2" label-style="width: 7.5rem; white-space: nowrap"
        >
          <NDescriptionsItem label="systemd">
            <NTag size="tiny" :type="statusData.active === 'active' ? 'success' : 'error'">
              {{ statusData.active || 'unknown' }}
            </NTag>
          </NDescriptionsItem>
          <NDescriptionsItem label="开机自启">
            <code class="kbd">{{ statusData.enabled || '—' }}</code>
          </NDescriptionsItem>
          <NDescriptionsItem label="版本">
            <code class="kbd">{{ statusData.version || '—' }}</code>
          </NDescriptionsItem>
          <NDescriptionsItem label="进程启动">
            <code class="kbd">{{ statusData.uptimeFrom || '—' }}</code>
          </NDescriptionsItem>
          <NDescriptionsItem v-if="statusData.listening" label="监听" :span="2">
            <pre class="status-pre">{{ statusData.listening }}</pre>
          </NDescriptionsItem>
        </NDescriptions>
      </div>
    </template>
  </div>
</template>

<style scoped src="./landing-tabs.scss"></style>
