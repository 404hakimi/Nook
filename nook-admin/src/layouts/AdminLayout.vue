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
  Cable,
  Bell,
  ChevronDown,
  Menu,
  Sun,
  Moon,
  LogOut,
  ScrollText,
  History,
  SlidersHorizontal
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
    key: 'member-group',
    label: '会员管理',
    icon: icon(Users),
    children: [
      { key: '/member/accounts', label: routerLabel('/member/accounts', '会员账户'), icon: icon(UserCircle) },
      { key: '/member/logs', label: routerLabel('/member/logs', '操作日志'), icon: icon(ScrollText) }
    ]
  },
  { key: '/business/plans', label: routerLabel('/business/plans', '套餐与 CDK'), icon: icon(Package) },
  {
    key: 'resource-group',
    label: '资源管理',
    icon: icon(Server),
    children: [
      { key: '/resource/servers', label: routerLabel('/resource/servers', '服务器'), icon: icon(Server) },
      { key: '/resource/ip-pool', label: routerLabel('/resource/ip-pool', 'IP代理池'), icon: icon(Globe2) }
    ]
  },
  {
    key: 'xray-group',
    label: 'Xray 管理',
    icon: icon(Cable),
    children: [
      { key: '/xray/nodes', label: routerLabel('/xray/nodes', 'Xray 节点'), icon: icon(Server) },
      { key: '/xray/clients', label: routerLabel('/xray/clients', 'Xray 客户端'), icon: icon(Cable) }
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

/** 当前激活菜单项 key; 命中规则: route.path 以菜单 key 为前缀则视为该菜单激活 (兼容 detail / edit 子路由)。 */
const activeKey = computed(() => {
  const leafKeys = menuOptions.flatMap((m) => (m.children ? m.children.map((c) => c.key as string) : [m.key as string]))
  return leafKeys.find((k) => route.path.startsWith(k)) || ''
})

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
