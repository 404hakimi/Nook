<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { Activity, FileText, Info, Rocket, RotateCcw, ServerCog } from 'lucide-vue-next'
import { NAlert, NButton, NIcon, NSpin, NTag, useDialog, useMessage } from 'naive-ui'
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

      <!-- 字段表 -->
      <div class="grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
        <div class="info-row"><span class="k">xray 版本</span>
          <NTag size="tiny" type="info">{{ node.xrayVersion || '?' }}</NTag>
        </div>
        <div class="info-row"><span class="k">API 端口</span><code class="v">{{ node.xrayApiPort ?? '—' }}</code></div>
        <div class="info-row"><span class="k">监听端口</span><code class="v">{{ node.sharedInboundPort ?? '—' }}</code></div>
        <div class="info-row"><span class="k">domain</span><code class="v">{{ node.domain || '—' }}</code></div>
        <div class="info-row"><span class="k">ws path</span><code class="v">{{ node.wsPath || '—' }}</code></div>
        <div class="info-row"><span class="k">touchdownSize</span><span class="v">{{ node.touchdownSize ?? '—' }}</span></div>
        <div class="info-row"><span class="k">binary 路径</span><code class="v">{{ node.xrayBinaryPath || '—' }}</code></div>
        <div class="info-row"><span class="k">config 路径</span><code class="v">{{ node.xrayConfigPath || '—' }}</code></div>
        <div class="info-row"><span class="k">share 目录</span><code class="v">{{ node.xrayShareDir || '—' }}</code></div>
        <div class="info-row"><span class="k">log 目录</span><code class="v">{{ node.xrayLogDir || '—' }}</code></div>
        <div class="info-row"><span class="k">TLS cert</span><code class="v">{{ node.tlsCertPath || '—' }}</code></div>
        <div class="info-row"><span class="k">TLS key</span><code class="v">{{ node.tlsKeyPath || '—' }}</code></div>
        <div class="info-row"><span class="k">最近启动</span><span class="v">{{ formatDateTime(node.lastXrayUptime) || '—' }}</span></div>
        <div class="info-row"><span class="k">部署完成</span><span class="v">{{ formatDateTime(node.installedAt) || '—' }}</span></div>
      </div>

      <XrayNodeInstallInfoDialog v-model="installInfoOpen" :node="node" />
      <XrayNodeStatusDialog v-model="statusOpen" :node="node" />
      <XrayNodeDiffDialog v-model="diffOpen" :node="node" />
      <XrayNodeLogDialog v-model="logOpen" :node="node" />
    </div>
  </NSpin>
</template>

<style scoped>
/* info-row / k / v 走 main.scss 全局 tokens */
</style>
