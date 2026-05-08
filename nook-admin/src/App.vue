<script setup lang="ts">
import { computed } from 'vue'
import {
  NConfigProvider,
  NDialogProvider,
  NLoadingBarProvider,
  NMessageProvider,
  NNotificationProvider,
  darkTheme,
  dateZhCN,
  zhCN,
  type GlobalThemeOverrides
} from 'naive-ui'
import { useTheme } from '@/composables/useTheme'
import DiscreteApiProvider from '@/components/DiscreteApiProvider.vue'

const { theme } = useTheme()
const naiveTheme = computed(() => (theme.value === 'dark' ? darkTheme : null))

/** 全局主题微调 — 中性灰系, 后台风格简洁; 改这里一处即可全站生效。 */
const themeOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: '#18181b',
    primaryColorHover: '#27272a',
    primaryColorPressed: '#09090b',
    primaryColorSuppl: '#27272a',
    borderRadius: '6px',
    borderRadiusSmall: '4px'
  }
}
</script>

<template>
  <NConfigProvider
    :theme="naiveTheme"
    :theme-overrides="themeOverrides"
    :locale="zhCN"
    :date-locale="dateZhCN"
  >
    <NLoadingBarProvider>
      <NDialogProvider>
        <NNotificationProvider>
          <NMessageProvider>
            <!-- 把 useMessage / useDialog 实例注册到全局 discreteApi, 模块级代码 (axios 拦截器等) 才能用 -->
            <DiscreteApiProvider>
              <RouterView />
            </DiscreteApiProvider>
          </NMessageProvider>
        </NNotificationProvider>
      </NDialogProvider>
    </NLoadingBarProvider>
  </NConfigProvider>
</template>
