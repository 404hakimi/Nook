<script setup lang="ts">
import { Info } from 'lucide-vue-next'
import { NIcon, NTooltip } from 'naive-ui'

/**
 * 标签旁的「ⓘ」小图标 + hover tooltip; 用于装载详细说明 / 技术细节, 不挤占主界面.
 *
 * <p>用法:
 * <pre>
 *   <span>Xray API 端口 <TipIcon>仅 127.0.0.1 监听, xray api adi/rmi 调用用</TipIcon></span>
 * </pre>
 */
defineProps<{
  /** tooltip 位置, 默认 top */
  placement?: 'top' | 'bottom' | 'left' | 'right'
  /** 图标大小, 默认 13px */
  size?: number
}>()
</script>

<template>
  <NTooltip
    :placement="placement || 'top'"
    trigger="hover"
    :style="{ maxWidth: '320px', whiteSpace: 'pre-line' }"
  >
    <template #trigger>
      <NIcon
        :size="size || 13"
        class="text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-300 align-middle ml-0.5 cursor-help inline-block"
      >
        <Info />
      </NIcon>
    </template>
    <div class="text-xs leading-relaxed">
      <slot />
    </div>
  </NTooltip>
</template>
