<script setup lang="ts">
import { computed } from 'vue'
import { NDescriptions, NDescriptionsItem, NModal, NSpace, NButton, NTag } from 'naive-ui'
import type { XrayInstall } from '@/api/xray/xray-install'
import type { XrayInbound } from '@/api/xray/xray-inbound'
import { formatDateTime } from '@/utils/date'

interface Props {
  modelValue: boolean
  server?: XrayInstall | null
  config?: XrayInbound | null
}
const props = defineProps<Props>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
}>()

/**
 * 全部路径来自后端 RespVO 字段: xray_binary_path / xray_config_path / xray_share_dir / xray_log_dir
 * 由装机流程落库, systemd_unit_path 是后端常量回填.
 */
const installPaths = computed(() => {
  const s = props.server
  if (!s) return []
  const logSuffix = '{access,error}.log'
  const rows = [
    { label: '二进制包', value: s.xrayBinaryPath },
    { label: 'config', value: s.xrayConfigPath },
    { label: 'share', value: s.xrayShareDir },
    { label: 'log', value: s.xrayLogDir ? `${s.xrayLogDir.replace(/\/+$/, '')}/${logSuffix}` : '' },
    { label: 'systemd', value: s.xraySystemdUnitPath }
  ]
  return rows.filter((r) => !!r.value)
})

const hasTls = computed(() => !!props.config?.domain)

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
      <span>Xray 装机详情</span>
    </template>
    <template #header-extra>
      <span v-if="server" class="text-xs text-zinc-500">
        {{ server.serverName || server.serverId }}
        <span v-if="server.serverHost">({{ server.serverHost }})</span>
      </span>
    </template>

    <div v-if="server" class="space-y-4">
      <!-- 实例元数据 (xray_install) -->
      <NDescriptions
        :column="2"
        size="small"
        bordered
        label-placement="left"
        label-align="left"
      >
        <NDescriptionsItem label="Xray 版本">
          <NTag v-if="server.xrayVersion" size="small" type="info" :bordered="false">{{ server.xrayVersion }}</NTag>
          <span v-else class="text-zinc-400">-</span>
        </NDescriptionsItem>
        <NDescriptionsItem label="API 端口">
          <span class="font-mono text-xs">127.0.0.1:{{ server.xrayApiPort ?? '-' }}</span>
        </NDescriptionsItem>
        <NDescriptionsItem label="共享 inbound 端口">
          <span class="font-mono text-xs">{{ config?.sharedInboundPort ?? '-' }}</span>
        </NDescriptionsItem>
        <NDescriptionsItem label="协议 / 传输">
          <span class="font-mono text-xs">
            {{ config?.protocol ?? '-' }}<span v-if="config?.transport"> + {{ config.transport }}</span>
          </span>
        </NDescriptionsItem>
        <NDescriptionsItem label="WS Path" :span="2">
          <span class="font-mono text-xs">{{ config?.wsPath || '-' }}</span>
        </NDescriptionsItem>
      </NDescriptions>

      <!-- 安装路径 (xray_install) -->
      <div>
        <div class="text-xs font-semibold text-zinc-500 mb-2">安装路径</div>
        <div class="rounded border border-zinc-200 dark:border-zinc-700 bg-zinc-50 dark:bg-zinc-800/40 p-3 grid grid-cols-1 gap-y-1">
          <div
            v-for="p in installPaths"
            :key="p.label"
            class="flex items-baseline gap-3 text-xs"
          >
            <span class="text-zinc-500 w-20 flex-shrink-0">{{ p.label }}</span>
            <span class="font-mono text-zinc-700 dark:text-zinc-300 break-all">{{ p.value }}</span>
          </div>
          <div v-if="installPaths.length === 0" class="text-xs text-zinc-400">
            (xray 安装路径未落库; 走一次"强制重装"即可补齐)
          </div>
        </div>
      </div>

      <!-- TLS / 域名 (xray_inbound, 走域名时才显示) -->
      <div v-if="hasTls && config">
        <div class="text-xs font-semibold text-zinc-500 mb-2">TLS / 域名</div>
        <NDescriptions :column="1" size="small" bordered label-placement="left" label-align="left">
          <NDescriptionsItem label="域名">
            <span class="font-mono text-xs">{{ config.domain }}</span>
          </NDescriptionsItem>
          <NDescriptionsItem label="证书路径">
            <span class="font-mono text-xs">{{ config.tlsCertPath || '-' }}</span>
          </NDescriptionsItem>
          <NDescriptionsItem label="私钥路径">
            <span class="font-mono text-xs">{{ config.tlsKeyPath || '-' }}</span>
          </NDescriptionsItem>
        </NDescriptions>
      </div>

      <!-- 时间戳 (xray_install) -->
      <NDescriptions :column="1" size="small" bordered label-placement="left" label-align="left">
        <NDescriptionsItem label="最近一次部署">
          <span class="text-xs">{{ server.installedAt ? formatDateTime(server.installedAt) : '-' }}</span>
        </NDescriptionsItem>
        <NDescriptionsItem label="上次探测 xray 启动">
          <span class="text-xs">{{ server.lastXrayUptime ? formatDateTime(server.lastXrayUptime) : '-' }}</span>
        </NDescriptionsItem>
        <NDescriptionsItem label="记录更新于">
          <span class="text-xs">{{ server.updatedAt ? formatDateTime(server.updatedAt) : '-' }}</span>
        </NDescriptionsItem>
      </NDescriptions>
    </div>

    <template #footer>
      <NSpace justify="end">
        <NButton size="small" @click="close">关闭</NButton>
      </NSpace>
    </template>
  </NModal>
</template>
