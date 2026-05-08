<script setup lang="ts">
import { useRouter } from 'vue-router'
import { NButton } from 'naive-ui'

const router = useRouter()
</script>

<template>
  <div class="min-h-screen flex items-center justify-center bg-[var(--n-color)]">
    <div class="text-center">
      <h1 class="text-9xl font-bold opacity-20">404</h1>
      <p class="text-xl mt-4">抱歉, 访问的页面不存在</p>
      <NButton type="primary" class="mt-6" @click="router.replace('/')">回首页</NButton>
    </div>
  </div>
</template>
