<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { Activity, Calendar, FileText, FolderOpen, Info, Lock, Network, Rocket, RotateCcw } from 'lucide-vue-next'
import { NAlert, NButton, NCard, NDescriptions, NDescriptionsItem, NIcon, NSpin, NTag, useDialog, useMessage } from 'naive-ui'
import { getXrayServer, type XrayServer } from '@/api/xray/xray-server'
import { getXrayConfig, type XrayConfig } from '@/api/xray/xray-config'
import { xrayRestart } from '@/api/xray/server'
import type { ServerFrontlineListItem } from '@/api/resource/server'
import { formatDateTime } from '@/utils/date'
import XrayServerInstallInfoDialog from '@/views/xray/XrayServerInstallInfoDialog.vue'
import XrayServerStatusDialog from '@/views/xray/XrayServerStatusDialog.vue'
import XrayServerLogDialog from '@/views/xray/XrayServerLogDialog.vue'
import ServerInstallDialog from '@/views/resource/ServerInstallDialog.vue'

const props = defineProps<{
  serverId: string
  /** 父组件已拿到的 server 运行时聚合 (id/name 给 install dialog 用). */
  agentInfo: ServerFrontlineListItem | null
}>()

const message = useMessage()
const dialog = useDialog()

const server = ref<XrayServer | null>(null)
const config = ref<XrayConfig | null>(null)
const loading = ref(false)

async function load() {
  if (!props.serverId) return
  loading.value = true
  try {
    // 并发拉 xray_server + xray_config; xray_server 不存在 → 进 "未装" 分支; xray_config 缺失独立兜 null.
    // resource_server (id/name) 给装机 dialog 用, 不再独立拉, 走父组件 agentInfo prop.
    const [srv, cfg] = await Promise.all([
      getXrayServer(props.serverId).catch(() => null),
      getXrayConfig(props.serverId).catch(() => null)
    ])
    server.value = srv
    config.value = cfg
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(() => props.serverId, load)

// ===== Dialogs =====
const installInfoOpen = ref(false)
const statusOpen = ref(false)
const logOpen = ref(false)
const installOpen = ref(false)

function onInstalled() {
  message.success('xray 装机成功')
  load()
}

function onRestart() {
  if (!server.value) return
  dialog.warning({
    title: '确认重启 xray',
    content: '会短暂中断现有客户连接 (~1-3s).',
    positiveText: '重启', negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await xrayRestart(props.serverId)
        message.success('已重启')
      } catch { /* */ }
    }
  })
}
</script>

<template>
  <NSpin :show="loading">
    <div v-if="!server && !loading" class="space-y-3">
      <NAlert type="warning" :show-icon="false" size="small">
        该 server 未装 xray. 点 "装 xray" SSH 一键部署 (含 binary 下载 + systemd unit + 证书申请).
      </NAlert>
      <div>
        <NButton type="primary" size="small" :disabled="!agentInfo" @click="installOpen = true">
          <template #icon><NIcon><Rocket /></NIcon></template>
          装 xray
        </NButton>
      </div>
    </div>

    <div v-else-if="server" class="space-y-3">
      <!-- 操作栏 -->
      <div class="flex items-center gap-2 flex-wrap">
        <NButton size="small" @click="installInfoOpen = true">
          <template #icon><NIcon><Info /></NIcon></template>
          装机信息
        </NButton>
        <NButton size="small" type="info" @click="statusOpen = true">
          <template #icon><NIcon><Activity /></NIcon></template>
          运行状态
        </NButton>
        <NButton size="small" quaternary @click="logOpen = true">
          <template #icon><NIcon><FileText /></NIcon></template>
          日志
        </NButton>
        <div class="flex-1"></div>
        <NButton size="small" type="warning" @click="onRestart">
          <template #icon><NIcon><RotateCcw /></NIcon></template>
          重启 xray
        </NButton>
      </div>

      <!-- === Section 1: 运行参数 (xray_server 元数据 + xray_config inbound) === -->
      <NCard size="small" :bordered="false" class="info-section">
        <template #header>
          <div class="section-header">
            <NIcon class="section-icon"><Network :size="14" /></NIcon>
            <span>运行参数</span>
          </div>
        </template>
        <NDescriptions bordered size="small" label-placement="left" :column="2" label-style="width: 6rem">
          <NDescriptionsItem label="xray 版本">
            <NTag size="small" type="info">{{ server.xrayVersion || '?' }}</NTag>
          </NDescriptionsItem>
          <NDescriptionsItem label="API 端口">
            <span v-if="server.xrayApiPort != null" class="num">{{ server.xrayApiPort }}</span>
            <span v-else class="muted">—</span>
          </NDescriptionsItem>
          <NDescriptionsItem label="监听端口">
            <span v-if="config?.sharedInboundPort != null" class="num">{{ config.sharedInboundPort }}</span>
            <span v-else class="muted">—</span>
          </NDescriptionsItem>
          <NDescriptionsItem label="domain">
            <code v-if="config?.domain" class="kbd">{{ config.domain }}</code>
            <span v-else class="muted">—</span>
          </NDescriptionsItem>
          <NDescriptionsItem label="协议 / 传输">
            <NTag v-if="config?.protocol" size="tiny">{{ config.protocol }}</NTag>
            <NTag v-if="config?.transport" size="tiny" type="info" class="ml-1">{{ config.transport }}</NTag>
            <span v-if="!config?.protocol && !config?.transport" class="muted">—</span>
          </NDescriptionsItem>
          <NDescriptionsItem label="ws path">
            <code v-if="config?.wsPath" class="kbd">{{ config.wsPath }}</code>
            <span v-else class="muted">—</span>
          </NDescriptionsItem>
        </NDescriptions>
      </NCard>

      <!-- === Section 2: TLS === -->
      <NCard size="small" :bordered="false" class="info-section">
        <template #header>
          <div class="section-header">
            <NIcon class="section-icon"><Lock :size="14" /></NIcon>
            <span>TLS 证书</span>
          </div>
        </template>
        <NDescriptions bordered size="small" label-placement="left" :column="1" label-style="width: 6rem">
          <NDescriptionsItem label="cert 路径">
            <code v-if="config?.tlsCertPath" class="kbd">{{ config.tlsCertPath }}</code>
            <span v-else class="muted">—</span>
          </NDescriptionsItem>
          <NDescriptionsItem label="key 路径">
            <code v-if="config?.tlsKeyPath" class="kbd">{{ config.tlsKeyPath }}</code>
            <span v-else class="muted">—</span>
          </NDescriptionsItem>
        </NDescriptions>
      </NCard>

      <!-- === Section 3: 文件路径 (xray_server) === -->
      <NCard size="small" :bordered="false" class="info-section">
        <template #header>
          <div class="section-header">
            <NIcon class="section-icon"><FolderOpen :size="14" /></NIcon>
            <span>文件路径</span>
          </div>
        </template>
        <NDescriptions bordered size="small" label-placement="left" :column="2" label-style="width: 6rem">
          <NDescriptionsItem label="binary">
            <code v-if="server.xrayBinaryPath" class="kbd">{{ server.xrayBinaryPath }}</code>
            <span v-else class="muted">—</span>
          </NDescriptionsItem>
          <NDescriptionsItem label="config">
            <code v-if="server.xrayConfigPath" class="kbd">{{ server.xrayConfigPath }}</code>
            <span v-else class="muted">—</span>
          </NDescriptionsItem>
          <NDescriptionsItem label="share 目录">
            <code v-if="server.xrayShareDir" class="kbd">{{ server.xrayShareDir }}</code>
            <span v-else class="muted">—</span>
          </NDescriptionsItem>
          <NDescriptionsItem label="log 目录">
            <code v-if="server.xrayLogDir" class="kbd">{{ server.xrayLogDir }}</code>
            <span v-else class="muted">—</span>
          </NDescriptionsItem>
        </NDescriptions>
      </NCard>

      <!-- === Section 4: 时间 === -->
      <NCard size="small" :bordered="false" class="info-section">
        <template #header>
          <div class="section-header">
            <NIcon class="section-icon"><Calendar :size="14" /></NIcon>
            <span>时间</span>
          </div>
        </template>
        <NDescriptions bordered size="small" label-placement="left" :column="2" label-style="width: 6rem">
          <NDescriptionsItem label="最近启动">
            <span v-if="server.lastXrayUptime">{{ formatDateTime(server.lastXrayUptime) }}</span>
            <span v-else class="muted">—</span>
          </NDescriptionsItem>
          <NDescriptionsItem label="部署完成">
            <span v-if="server.installedAt">{{ formatDateTime(server.installedAt) }}</span>
            <span v-else class="muted">—</span>
          </NDescriptionsItem>
        </NDescriptions>
      </NCard>

      <XrayServerInstallInfoDialog v-model="installInfoOpen" :server="server" :config="config" />
      <XrayServerStatusDialog v-model="statusOpen" :server="server" />
      <XrayServerLogDialog v-model="logOpen" :server="server" />
    </div>

    <ServerInstallDialog
      v-model="installOpen"
      :server="agentInfo"
      @installed="onInstalled"
    />
  </NSpin>
</template>

<style scoped>
/* info-section / section-header / section-icon / num / unit / muted / kbd 走 main.scss 全局 tokens */
</style>
