<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div class="min-h-screen flex items-center justify-center bg-base-200">
    <div class="text-center">
      <h1 class="text-9xl font-bold text-base-content/30">404</h1>
      <p class="text-xl mt-4">抱歉，访问的页面不存在</p>
      <button class="btn btn-primary mt-6" @click="router.replace('/')">回首页</button>
    </div>
  </div>
</template>
