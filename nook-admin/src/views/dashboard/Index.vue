<script setup lang="ts">
import { Users, Globe, Ticket, AlertTriangle } from 'lucide-vue-next'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

interface StatItem {
  label: string
  value: string
  icon: typeof Users
  tone: string  // DaisyUI 颜色 token
}

// 占位数据。等监控接口接入后从 /admin/monitor/** 拉真实指标
const stats: StatItem[] = [
  { label: '在线会员', value: '—', icon: Users,          tone: 'text-primary bg-primary/10' },
  { label: '可用 IP',  value: '—', icon: Globe,          tone: 'text-success bg-success/10' },
  { label: '今日兑换', value: '—', icon: Ticket,         tone: 'text-info bg-info/10' },
  { label: '告警数',   value: '—', icon: AlertTriangle,  tone: 'text-warning bg-warning/10' }
]
</script>

<template>
  <div class="space-y-6">
    <!-- 欢迎条 -->
    <div class="card bg-base-100 shadow-sm">
      <div class="card-body py-5">
        <div class="flex items-center justify-between flex-wrap gap-2">
          <div>
            <h2 class="text-xl font-semibold">
              你好，{{ userStore.user?.realName || userStore.user?.username }}
            </h2>
            <p class="text-sm text-base-content/60 mt-1">欢迎回到 Nook 管理端</p>
          </div>
          <div class="badge badge-primary badge-outline">{{ userStore.user?.role || '-' }}</div>
        </div>
      </div>
    </div>

    <!-- 指标卡片 -->
    <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
      <div
        v-for="s in stats"
        :key="s.label"
        class="card bg-base-100 shadow-sm hover:shadow-md transition-shadow"
      >
        <div class="card-body">
          <div class="flex items-start justify-between">
            <div>
              <div class="text-sm text-base-content/60">{{ s.label }}</div>
              <div class="text-3xl font-bold mt-2">{{ s.value }}</div>
              <div class="text-xs text-base-content/40 mt-1">待对接</div>
            </div>
            <div :class="['w-10 h-10 rounded-lg flex items-center justify-center', s.tone]">
              <component :is="s.icon" class="w-5 h-5" />
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 占位区：后续放最近兑换 / 异常 IP / 流量趋势图 -->
    <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
      <div class="card bg-base-100 shadow-sm">
        <div class="card-body">
          <h3 class="font-semibold mb-2">最近兑换</h3>
          <div class="text-sm text-base-content/40 text-center py-12">待对接 /admin/business/order-log</div>
        </div>
      </div>
      <div class="card bg-base-100 shadow-sm">
        <div class="card-body">
          <h3 class="font-semibold mb-2">异常 IP</h3>
          <div class="text-sm text-base-content/40 text-center py-12">待对接 /admin/monitor/ip-health</div>
        </div>
      </div>
    </div>
  </div>
</template>
