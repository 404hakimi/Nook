<script setup lang="ts">
import { useDialog, useMessage } from 'naive-ui'
import { setDialogApi, setMessageApi } from '@/utils/discreteApi'

/**
 * 启动时把 useMessage / useDialog 实例注册到全局 discreteApi, 让 axios 拦截器等
 * 模块级代码也能通过 import { message } from '@/utils/discreteApi' 调用。
 *
 * <p>必须挂在 App.vue 的 NMessageProvider + NDialogProvider 子树内, 不渲染任何 UI。
 */
setMessageApi(useMessage())
setDialogApi(useDialog())
</script>

<template>
  <slot />
</template>
