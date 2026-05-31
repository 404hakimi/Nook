<script setup lang="ts">
import { computed, h, ref, type Component } from 'vue'
import { useRouter, useRoute, RouterLink } from 'vue-router'
import {
  LayoutDashboard,
  UserCog,
  UserCircle,
  Users,
  Settings,
  Package,
  Server,
  Globe2,
  Bell,
  ChevronDown,
  Menu,
  Sun,
  Moon,
  LogOut,
  ScrollText,
  History,
  SlidersHorizontal,
  ArrowLeftRight,
  Settings2,
  MapPin,
  Boxes
} from 'lucide-vue-next'
import {
  NAvatar,
  NButton,
  NDropdown,
  NIcon,
  NLayout,
  NLayoutContent,
  NLayoutHeader,
  NLayoutSider,
  NMenu,
  type MenuOption
} from 'naive-ui'
import { useUserStore } from '@/stores/user'
import { useTheme } from '@/composables/useTheme'
import { useConfirm } from '@/composables/useConfirm'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const { theme, toggle: toggleTheme } = useTheme()
const { confirm } = useConfirm()

/** 把 lucide 图标包成 NIcon 让 NMenu 能直接用 (Naive UI menu icon 期望 () => VNode)。 */
function icon(component: Component) {
  return () => h(NIcon, null, { default: () => h(component) })
}

/** RouterLink 包装: NMenu 默认 click 通过 onUpdateValue 走我们写的 handler, 但用 label slot 直接渲染 router-link 体验更好。 */
function routerLabel(to: string, text: string) {
  return () => h(RouterLink, { to }, { default: () => text })
}

const menuOptions: MenuOption[] = [
  { key: '/dashboard', label: routerLabel('/dashboard', '总览'), icon: icon(LayoutDashboard) },
  {
    key: 'system-group',
    label: '系统管理',
    icon: icon(Settings),
    children: [
      { key: '/system/users', label: routerLabel('/system/users', '系统用户'), icon: icon(UserCog) }
    ]
  },
  {
    key: 'config-group',
    label: '系统配置',
    icon: icon(Settings2),
    children: [
      { key: '/system/regions', label: routerLabel('/system/regions', '区域'), icon: icon(MapPin) }
    ]
  },
  {
    key: 'member-group',
    label: '会员管理',
    icon: icon(Users),
    children: [
      { key: '/member/accounts', label: routerLabel('/member/accounts', '会员账户'), icon: icon(UserCircle) },
      { key: '/member/logs', label: routerLabel('/member/logs', '操作日志'), icon: icon(ScrollText) }
    ]
  },
  {
    key: 'trade-group',
    label: '交易管理',
    icon: icon(Package),
    children: [
      { key: '/trade/plans', label: routerLabel('/trade/plans', '套餐'), icon: icon(Package) },
      { key: '/trade/subscriptions', label: routerLabel('/trade/subscriptions', '订阅'), icon: icon(ScrollText) },
      { key: '/trade/subscription-change-log', label: routerLabel('/trade/subscription-change-log', '换机日志'), icon: icon(ArrowLeftRight) }
    ]
  },
  {
    key: 'resource-group',
    label: '资源管理',
    icon: icon(Boxes),
    children: [
      // 线路机统一入口 (B 方案): 卡片总览, 点卡片进详情 tab (基本/Xray/Agent/任务)
      { key: '/servers', label: routerLabel('/servers', '线路机'), icon: icon(Server) },
      { key: '/resource/server-landing', label: routerLabel('/resource/server-landing', '落地机'), icon: icon(Globe2) }
    ]
  },
  {
    key: 'operation-group',
    label: '运维管理',
    icon: icon(History),
    children: [
      { key: '/operation/op-log', label: routerLabel('/operation/op-log', '操作流水'), icon: icon(History) },
      { key: '/operation/op-config', label: routerLabel('/operation/op-config', 'Op 调度配置'), icon: icon(SlidersHorizontal) }
    ]
  },
  { key: '/monitor/alerts', label: routerLabel('/monitor/alerts', '监控告警'), icon: icon(Bell) }
]

/** 当前激活菜单项 key; 命中规则: route.path 以菜单 key 为前缀则视为该菜单激活 (兼容 detail / edit 子路由)。
 *  递归扁平所有叶子节点 — 支持任意层级 (当前 Xray 管理嵌在资源管理下是三级). */
function collectLeafKeys(options: MenuOption[]): string[] {
  return options.flatMap((m) =>
    m.children && m.children.length > 0
      ? collectLeafKeys(m.children as MenuOption[])
      : [m.key as string]
  )
}
const activeKey = computed(() => {
  const leafKeys = collectLeafKeys(menuOptions)
  return leafKeys.find((k) => route.path.startsWith(k)) || ''
})

/** 默认展开的分组 key; 包含资源管理 + Xray 管理 (后者嵌在前者下作三级菜单), 让用户一进入就看到全部叶子. */
const defaultExpandedKeys = ['resource-group']

/** 移动端: <md 折叠侧栏, 点 toggle 临时展开; >=md 直接常驻 */
const collapsed = ref(false)

const userDropdownOptions = computed(() => [
  {
    key: 'role',
    type: 'render' as const,
    render: () =>
      h('div', { class: 'px-3 py-1.5 text-xs opacity-60 border-b' }, userStore.user?.role || '-')
  },
  { key: 'logout', label: '退出登录', icon: icon(LogOut) }
])

async function onUserDropdownSelect(key: string) {
  if (key !== 'logout') return
  const ok = await confirm({
    title: '退出登录',
    message: '退出后需重新登录, 是否继续?',
    type: 'warning',
    confirmText: '退出',
    cancelText: '取消'
  })
  if (!ok) return
  await userStore.logout()
  router.replace({ name: 'login' })
}
</script>

<template>
  <NLayout has-sider class="!h-screen">
    <NLayoutSider
      bordered
      collapse-mode="width"
      :collapsed-width="64"
      :width="240"
      :collapsed="collapsed"
      show-trigger="bar"
      @update:collapsed="collapsed = $event"
    >
      <div class="h-14 flex items-center px-4 text-base font-semibold border-b border-[var(--n-border-color)]">
        <span class="inline-flex w-7 h-7 items-center justify-center bg-[var(--n-color-primary)] text-white rounded mr-2 text-sm font-bold shrink-0">N</span>
        <span v-if="!collapsed">Nook 管理端</span>
      </div>
      <NMenu
        :collapsed="collapsed"
        :collapsed-width="64"
        :collapsed-icon-size="20"
        :options="menuOptions"
        :value="activeKey"
        :default-expanded-keys="defaultExpandedKeys"
        :indent="18"
      />
    </NLayoutSider>

    <NLayout>
      <NLayoutHeader bordered class="!flex !items-center h-14 px-4 gap-2">
        <NButton circle tertiary size="small" class="md:!hidden" @click="collapsed = !collapsed">
          <template #icon><NIcon><Menu /></NIcon></template>
        </NButton>

        <div class="flex-1 text-base font-medium">{{ route.meta.title }}</div>

        <NButton
          circle
          tertiary
          size="small"
          :title="theme === 'dark' ? '切换为浅色' : '切换为深色'"
          @click="toggleTheme"
        >
          <template #icon>
            <NIcon>
              <Sun v-if="theme === 'dark'" />
              <Moon v-else />
            </NIcon>
          </template>
        </NButton>

        <NDropdown
          trigger="click"
          :options="userDropdownOptions"
          @select="onUserDropdownSelect"
        >
          <NButton text class="!flex !items-center !gap-2">
            <NAvatar
              round
              :size="28"
              :style="{ backgroundColor: 'var(--n-color-primary)', color: '#fff' }"
            >
              {{ (userStore.user?.username || 'A')[0].toUpperCase() }}
            </NAvatar>
            <span class="hidden sm:inline">
              {{ userStore.user?.realName || userStore.user?.username || '未登录' }}
            </span>
            <NIcon size="14" class="opacity-60"><ChevronDown /></NIcon>
          </NButton>
        </NDropdown>
      </NLayoutHeader>

      <NLayoutContent class="p-6 !bg-[var(--n-color-modal)]" :native-scrollbar="false">
        <RouterView />
      </NLayoutContent>
    </NLayout>
  </NLayout>
</template>
