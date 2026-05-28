<script setup lang="ts">
import { computed } from 'vue'
import {
  Activity,
  AlertCircle,
  Calendar,
  Copy,
  FileText,
  FolderOpen,
  KeyRound,
  Pencil,
  RefreshCcw,
  Rocket,
  ServerCog,
  Zap
} from 'lucide-vue-next'
import {
  NAlert,
  NButton,
  NCard,
  NDescriptions,
  NDescriptionsItem,
  NIcon,
  NTag,
  useMessage
} from 'naive-ui'
import type {
  ServerLanding,
  ServerLandingInstall
} from '@/api/resource/server-landing'
import type { SystemdStatus } from '@/api/xray/server'
import { formatDateTime } from '@/utils/date'

/**
 * 落地机详情 — SOCKS5 服务 tab. 结构对齐线路机 XrayTab: NCard + NDescriptions.
 *
 * 3 段: 凭据 / 部署配置 (install 子表) / 远端 dante 状态 (SSH 探测, 懒加载).
 */
const props = defineProps<{
  detail: ServerLanding
  installInfo: ServerLandingInstall | null
  canManage: boolean
  canTest: boolean
  isSelfDeploy: boolean
  isInstalling: boolean
  isLive: boolean
  statusData: SystemdStatus | null
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
  <div class="space-y-3">
    <!-- 操作栏 -->
    <div class="action-bar">
      <NButton size="small" type="primary" @click="emit('edit-socks5')">
        <template #icon><NIcon><Pencil :size="14" /></NIcon></template>
        编辑 dante 配置
      </NButton>
      <NButton
        v-if="isSelfDeploy && (isInstalling || isLive)"
        size="small"
        :type="isLive ? 'default' : 'primary'"
        :quaternary="isLive"
        @click="emit('open-deploy')"
      >
        <template #icon><NIcon><Rocket :size="14" /></NIcon></template>
        {{ isLive ? '重装' : '装机' }}
      </NButton>
      <NButton v-if="canTest" size="small" quaternary type="warning" @click="emit('open-test')">
        <template #icon><NIcon><Zap :size="14" /></NIcon></template>
        拨号测试
      </NButton>
      <NButton size="small" quaternary :disabled="!canManage" @click="emit('open-log')">
        <template #icon><NIcon><FileText :size="14" /></NIcon></template>
        查看日志
      </NButton>
    </div>

    <!-- 未配置 SOCKS5 提示 -->
    <NAlert v-if="!socks5Endpoint" type="info" :show-icon="false" size="small">
      <span class="flex items-center gap-2">
        <NIcon><AlertCircle :size="14" /></NIcon>
        <span>尚未配置 SOCKS5; 点 "编辑 dante 配置" 填端口/用户/密码, 或点 "装机" 一次性填齐并部署</span>
      </span>
    </NAlert>

    <template v-else>
      <!-- === Section 1: 凭据 === -->
      <NCard size="small" :bordered="false" class="info-section">
        <template #header>
          <div class="section-header">
            <NIcon class="section-icon"><KeyRound :size="14" /></NIcon>
            <span>凭据</span>
            <NTag size="tiny" type="warning" class="ml-1">敏感信息</NTag>
          </div>
        </template>
        <NDescriptions bordered size="small" label-placement="left" :column="1" label-style="width: 6rem">
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
      </NCard>

      <!-- === Section 2: 部署配置 (install 子表) === -->
      <NCard size="small" :bordered="false" class="info-section">
        <template #header>
          <div class="section-header">
            <NIcon class="section-icon"><ServerCog :size="14" /></NIcon>
            <span>部署配置</span>
            <span v-if="!installInfo" class="text-xs text-zinc-400 ml-2">尚未装机</span>
          </div>
        </template>
        <NDescriptions bordered size="small" label-placement="left" :column="2" label-style="width: 7rem">
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
        </NDescriptions>
      </NCard>

      <!-- === Section 3: 文件路径 === -->
      <NCard size="small" :bordered="false" class="info-section">
        <template #header>
          <div class="section-header">
            <NIcon class="section-icon"><FolderOpen :size="14" /></NIcon>
            <span>文件路径</span>
          </div>
        </template>
        <NDescriptions bordered size="small" label-placement="left" :column="2" label-style="width: 7rem">
          <NDescriptionsItem label="安装目录">
            <code class="kbd">{{ installInfo?.installDir || detail.installDir || '—' }}</code>
          </NDescriptionsItem>
          <NDescriptionsItem label="日志路径">
            <code class="kbd">{{ installInfo?.logPath || detail.logPath || '—' }}</code>
          </NDescriptionsItem>
          <NDescriptionsItem label="sockd.conf">
            <code class="kbd">{{ installInfo?.confPath || '—' }}</code>
          </NDescriptionsItem>
          <NDescriptionsItem label="PAM 配置">
            <code class="kbd">{{ installInfo?.pamFile || '—' }}</code>
          </NDescriptionsItem>
          <NDescriptionsItem label="密码文件" :span="2">
            <code class="kbd">{{ installInfo?.pwdFile || '—' }}</code>
          </NDescriptionsItem>
        </NDescriptions>
      </NCard>

      <!-- === Section 4: 远端 dante 状态 (SSH 探测, 懒加载) === -->
      <NCard size="small" :bordered="false" class="info-section">
        <template #header>
          <div class="section-header">
            <NIcon class="section-icon"><Activity :size="14" /></NIcon>
            <span>远端 dante 状态</span>
            <NTag size="tiny" type="default" class="ml-1">SSH 探测</NTag>
            <span class="flex-1"></span>
            <NButton
              size="tiny"
              type="primary"
              :loading="statusLoading"
              :disabled="!canManage"
              @click="emit('load-status')"
            >
              <template #icon><NIcon><RefreshCcw :size="12" /></NIcon></template>
              {{ statusData ? '刷新' : '加载' }}
            </NButton>
          </div>
        </template>

        <NAlert v-if="!canManage" type="warning" :show-icon="false" size="small">
          <span class="flex items-center gap-1">
            <NIcon><AlertCircle :size="14" /></NIcon>
            需要 SSH 凭据齐才能探测; 去 SSH 凭据 tab 补全
          </span>
        </NAlert>
        <NAlert v-else-if="!statusData && !statusLoading && !statusError" type="info" :show-icon="false" size="small">
          点上方 "加载" 拉远端 systemd / 监听端口状态 (单次约 1-2s).
        </NAlert>
        <NAlert v-else-if="statusError" type="error" :show-icon="false" size="small">{{ statusError }}</NAlert>

        <NDescriptions
          v-else-if="statusData"
          bordered size="small" label-placement="left" :column="2" label-style="width: 7rem"
        >
          <NDescriptionsItem label="systemd">
            <NTag size="tiny" :type="statusData.active === 'active' ? 'success' : 'error'">
              {{ statusData.active || 'unknown' }}
            </NTag>
          </NDescriptionsItem>
          <NDescriptionsItem label="开机自启">
            <code class="kbd">{{ statusData.enabled || '—' }}</code>
          </NDescriptionsItem>
          <NDescriptionsItem label="进程启动">
            <code class="kbd">{{ statusData.uptimeFrom || '—' }}</code>
          </NDescriptionsItem>
        </NDescriptions>
      </NCard>

      <!-- === Section 5: 时间 (如果有 dante 上次启动) === -->
      <NCard v-if="installInfo?.lastDanteUptime" size="small" :bordered="false" class="info-section">
        <template #header>
          <div class="section-header">
            <NIcon class="section-icon"><Calendar :size="14" /></NIcon>
            <span>时间</span>
          </div>
        </template>
        <NDescriptions bordered size="small" label-placement="left" :column="1" label-style="width: 7rem">
          <NDescriptionsItem label="dante 上次启动">
            <code class="kbd">{{ formatDateTime(installInfo.lastDanteUptime) }}</code>
          </NDescriptionsItem>
        </NDescriptions>
      </NCard>
    </template>
  </div>
</template>

<style scoped>
.action-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.cred-row {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
</style>
