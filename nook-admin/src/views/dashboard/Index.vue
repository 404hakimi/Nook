<script setup lang="ts">
import { Users, Globe, Ticket, AlertTriangle } from 'lucide-vue-next'
import { NCard, NTag } from 'naive-ui'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

interface StatItem {
  label: string
  value: string
  icon: typeof Users
  /** 卡片右上角图标用的 tone class (Tailwind 仍可用, 与 Naive UI 共存) */
  tone: string
}

// 占位数据。等监控接口接入后从 /admin/monitor/** 拉真实指标
const stats: StatItem[] = [
  { label: '在线会员', value: '—', icon: Users, tone: 'text-blue-700 bg-blue-100 dark:text-blue-300 dark:bg-blue-950' },
  { label: '可用 IP', value: '—', icon: Globe, tone: 'text-emerald-700 bg-emerald-100 dark:text-emerald-300 dark:bg-emerald-950' },
  { label: '今日兑换', value: '—', icon: Ticket, tone: 'text-sky-700 bg-sky-100 dark:text-sky-300 dark:bg-sky-950' },
  { label: '告警数', value: '—', icon: AlertTriangle, tone: 'text-amber-700 bg-amber-100 dark:text-amber-300 dark:bg-amber-950' }
]
</script>

<template>
  <div class="space-y-6">
    <!-- 欢迎条 -->
    <NCard>
      <div class="flex items-center justify-between flex-wrap gap-2">
        <div>
          <h2 class="text-xl font-semibold m-0">
            你好, {{ userStore.user?.realName || userStore.user?.username }}
          </h2>
          <p class="text-sm opacity-60 mt-1 mb-0">欢迎回到 Nook 管理端</p>
        </div>
        <NTag :bordered="false" type="primary">{{ userStore.user?.role || '-' }}</NTag>
      </div>
    </NCard>

    <!-- 指标卡片 -->
    <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
      <NCard v-for="s in stats" :key="s.label" hoverable>
        <div class="flex items-start justify-between">
          <div>
            <div class="text-sm opacity-60">{{ s.label }}</div>
            <div class="text-3xl font-bold mt-2">{{ s.value }}</div>
            <div class="text-xs opacity-40 mt-1">待对接</div>
          </div>
          <div :class="['w-10 h-10 rounded-lg flex items-center justify-center', s.tone]">
            <component :is="s.icon" class="w-5 h-5" />
          </div>
        </div>
      </NCard>
    </div>

    <!-- 占位区: 最近兑换 / 异常 IP / 流量趋势图 -->
    <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
      <NCard title="最近兑换" :bordered="true">
        <div class="text-sm opacity-40 text-center py-12">待对接 /admin/business/order-log</div>
      </NCard>
      <NCard title="异常 IP" :bordered="true">
        <div class="text-sm opacity-40 text-center py-12">待对接 /admin/monitor/ip-health</div>
      </NCard>
    </div>
  </div>
</template>
