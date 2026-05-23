<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { Activity, Calendar, FileText, FolderOpen, Info, Lock, Network, RotateCcw, ServerCog } from 'lucide-vue-next'
import { NAlert, NButton, NCard, NDescriptions, NDescriptionsItem, NIcon, NSpin, NTag, useDialog, useMessage } from 'naive-ui'
import { pageXrayNode, type XrayNode } from '@/api/xray/node'
import { xrayRestart } from '@/api/xray/server'
import { formatDateTime } from '@/utils/date'
import XrayNodeInstallInfoDialog from '@/views/xray/XrayNodeInstallInfoDialog.vue'
import XrayNodeStatusDialog from '@/views/xray/XrayNodeStatusDialog.vue'
import XrayNodeDiffDialog from '@/views/xray/XrayNodeDiffDialog.vue'
import XrayNodeLogDialog from '@/views/xray/XrayNodeLogDialog.vue'

const props = defineProps<{ serverId: string }>()

const message = useMessage()
const dialog = useDialog()

const node = ref<XrayNode | null>(null)
const loading = ref(false)

async function load() {
  if (!props.serverId) return
  loading.value = true
  try {
    const page = await pageXrayNode({ serverId: props.serverId, pageNo: 1, pageSize: 1 })
    node.value = page.records?.[0] ?? null
  } catch {
    node.value = null
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(() => props.serverId, load)

// ===== Dialogs =====
const installInfoOpen = ref(false)
const statusOpen = ref(false)
const diffOpen = ref(false)
const logOpen = ref(false)

function onRestart() {
  if (!node.value) return
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
    <div v-if="!node && !loading">
      <NAlert type="warning" :show-icon="false" size="small">
        该 server 未装 xray. 装 xray 流程暂未在新 UI 集成, 请到 <a href="/xray/nodes" class="text-blue-500">Xray 节点管理</a> 走一键部署 (后续会迁过来).
      </NAlert>
    </div>

    <div v-else-if="node" class="space-y-3">
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
        <NButton size="small" quaternary @click="diffOpen = true">
          <template #icon><NIcon><ServerCog /></NIcon></template>
          配置 Diff
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

      <!-- === Section 1: 运行参数 === -->
      <NCard size="small" :bordered="false" class="info-section">
        <template #header>
          <div class="section-header">
            <NIcon class="section-icon"><Network :size="14" /></NIcon>
            <span>运行参数</span>
          </div>
        </template>
        <NDescriptions bordered size="small" label-placement="left" :column="2" label-style="width: 6rem">
          <NDescriptionsItem label="xray 版本">
            <NTag size="small" type="info">{{ node.xrayVersion || '?' }}</NTag>
          </NDescriptionsItem>
          <NDescriptionsItem label="监听端口">
            <span v-if="node.sharedInboundPort != null" class="num">{{ node.sharedInboundPort }}</span>
            <span v-else class="muted">—</span>
          </NDescriptionsItem>
          <NDescriptionsItem label="domain">
            <code v-if="node.domain" class="kbd">{{ node.domain }}</code>
            <span v-else class="muted">—</span>
          </NDescriptionsItem>
          <NDescriptionsItem label="API 端口">
            <span v-if="node.xrayApiPort != null" class="num">{{ node.xrayApiPort }}</span>
            <span v-else class="muted">—</span>
          </NDescriptionsItem>
          <NDescriptionsItem label="ws path">
            <code v-if="node.wsPath" class="kbd">{{ node.wsPath }}</code>
            <span v-else class="muted">—</span>
          </NDescriptionsItem>
          <NDescriptionsItem label="touchdownSize">
            <span v-if="node.touchdownSize != null" class="num">{{ node.touchdownSize }}</span>
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
            <code v-if="node.tlsCertPath" class="kbd">{{ node.tlsCertPath }}</code>
            <span v-else class="muted">—</span>
          </NDescriptionsItem>
          <NDescriptionsItem label="key 路径">
            <code v-if="node.tlsKeyPath" class="kbd">{{ node.tlsKeyPath }}</code>
            <span v-else class="muted">—</span>
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
        <NDescriptions bordered size="small" label-placement="left" :column="2" label-style="width: 6rem">
          <NDescriptionsItem label="binary">
            <code v-if="node.xrayBinaryPath" class="kbd">{{ node.xrayBinaryPath }}</code>
            <span v-else class="muted">—</span>
          </NDescriptionsItem>
          <NDescriptionsItem label="config">
            <code v-if="node.xrayConfigPath" class="kbd">{{ node.xrayConfigPath }}</code>
            <span v-else class="muted">—</span>
          </NDescriptionsItem>
          <NDescriptionsItem label="share 目录">
            <code v-if="node.xrayShareDir" class="kbd">{{ node.xrayShareDir }}</code>
            <span v-else class="muted">—</span>
          </NDescriptionsItem>
          <NDescriptionsItem label="log 目录">
            <code v-if="node.xrayLogDir" class="kbd">{{ node.xrayLogDir }}</code>
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
            <span v-if="node.lastXrayUptime">{{ formatDateTime(node.lastXrayUptime) }}</span>
            <span v-else class="muted">—</span>
          </NDescriptionsItem>
          <NDescriptionsItem label="部署完成">
            <span v-if="node.installedAt">{{ formatDateTime(node.installedAt) }}</span>
            <span v-else class="muted">—</span>
          </NDescriptionsItem>
        </NDescriptions>
      </NCard>

      <XrayNodeInstallInfoDialog v-model="installInfoOpen" :node="node" />
      <XrayNodeStatusDialog v-model="statusOpen" :node="node" />
      <XrayNodeDiffDialog v-model="diffOpen" :node="node" />
      <XrayNodeLogDialog v-model="logOpen" :node="node" />
    </div>
  </NSpin>
</template>

<style scoped>
/* info-section / section-header / section-icon / num / unit / muted / kbd 走 main.scss 全局 tokens */
</style>
