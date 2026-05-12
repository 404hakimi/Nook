<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ServerCog, ArrowRight } from 'lucide-vue-next'
import { NButton, NCard, NEmpty, NIcon } from 'naive-ui'

const router = useRouter()

function goServers() {
  router.push('/resource/servers')
}
</script>

<template>
  <div class="space-y-4">
    <NCard size="small">
      <NEmpty description="Xray 节点列表功能开发中">
        <template #icon>
          <NIcon size="48" depth="3"><ServerCog /></NIcon>
        </template>
        <template #extra>
          <div class="space-y-3 text-center">
            <p class="text-sm text-zinc-500 max-w-md mx-auto">
              本页面将展示每台服务器上跑的 Xray 实例配置 (版本 / API 端口 / Slot 池 / 上次启动时间).
              当前可在「资源管理 → 服务器」点击行操作中的「Xray 管理」查看节点状态、日志、重启.
            </p>
            <NButton type="primary" @click="goServers">
              前往服务器列表
              <template #icon><NIcon><ArrowRight /></NIcon></template>
            </NButton>
          </div>
        </template>
      </NEmpty>
    </NCard>
  </div>
</template>
