<script setup lang="ts">
import { Construction } from 'lucide-vue-next'
import { NCard } from 'naive-ui'
</script>

<template>
  <NCard size="small">
    <div class="py-16 text-center">
      <Construction class="w-12 h-12 mx-auto text-zinc-400" />
      <h2 class="text-lg font-semibold mt-4">操作日志</h2>
      <p class="text-sm text-zinc-500 mt-1">
        待对接 <code class="bg-zinc-100 dark:bg-zinc-800 px-1.5 py-0.5 rounded text-xs">/admin/member/logs</code>
      </p>
    </div>
  </NCard>
</template>
