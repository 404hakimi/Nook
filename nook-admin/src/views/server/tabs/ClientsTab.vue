<script setup lang="ts">
import ServerXrayClientsTable from '@/views/xray/ServerXrayClientsTable.vue'

defineProps<{ serverId: string }>()  // ServerDetail tab 容器, 内层是 Xray 客户端表格
</script>

<template>
  <ServerXrayClientsTable :server-id="serverId" />
</template>
