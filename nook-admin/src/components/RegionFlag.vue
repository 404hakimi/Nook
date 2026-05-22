<script setup lang="ts">
/**
 * 国旗组件 - 包装 flag-icons CSS 类
 * - countryCode: ISO 3166-1 alpha-2 (JP / US / HK 等, 大小写均可)
 * - squared: true = 正方形 1:1; false = 矩形 4:3 (默认)
 * - size: 字号 (em-based, 跟随父级)
 * - fallback: countryCode 缺失时显示的 emoji (一般传 region.flagEmoji)
 *
 * 用法:
 *   <RegionFlag :code="region.countryCode" :size="18" />
 *   <RegionFlag :code="region.countryCode" squared :size="14" />
 */
import { computed } from 'vue'

const props = defineProps<{
  code?: string | null
  squared?: boolean
  /** CSS 字号 (像素). 默认跟随父级. */
  size?: number
  /** countryCode 缺失时的兜底文本 (一般是 emoji). */
  fallback?: string
}>()

const klass = computed(() => {
  if (!props.code) return null
  return ['fi', `fi-${props.code.toLowerCase()}`, props.squared ? 'fis' : null]
})

const style = computed(() => {
  if (props.size == null) return undefined
  // flag-icons 用 em 控制尺寸; 设 font-size 同步控制宽高
  return { fontSize: `${props.size}px`, lineHeight: 1 }
})
</script>

<template>
  <span v-if="klass" :class="klass" :style="style" class="region-flag"></span>
  <span v-else-if="fallback" :style="style" class="region-flag-fallback">{{ fallback }}</span>
</template>

<style scoped>
.region-flag {
  display: inline-block;
  vertical-align: middle;
  border-radius: 2px;
  box-shadow: 0 0 0 1px rgba(0, 0, 0, 0.06);
}
.region-flag-fallback {
  display: inline-block;
  vertical-align: middle;
  line-height: 1;
}
</style>
