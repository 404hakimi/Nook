<script setup lang="ts">
import NodeClientsSubTable from '@/views/xray/NodeClientsSubTable.vue'

defineProps<{ serverId: string }>()
</script>

<template>
  <NodeClientsSubTable :server-id="serverId" :embedded="false" />
</template>
