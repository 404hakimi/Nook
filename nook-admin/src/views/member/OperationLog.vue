<script setup lang="ts">
import { Construction } from 'lucide-vue-next'
</script>

<template>
  <div class="card bg-base-100 shadow-sm">
    <div class="card-body py-16 text-center">
      <Construction class="w-12 h-12 mx-auto text-base-content/30" />
      <h2 class="text-lg font-semibold mt-4">操作日志</h2>
      <p class="text-sm text-base-content/50 mt-1">
        待对接 <code class="bg-base-200 px-1.5 py-0.5 rounded text-xs">/admin/member/logs</code>
      </p>
    </div>
  </div>
</template>
