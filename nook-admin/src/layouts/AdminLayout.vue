<script setup lang="ts">
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import {
  LayoutDashboard,
  UserCog,
  UserCircle,
  Users,
  Settings,
  Package,
  Server,
  Cable,
  Bell,
  ChevronDown,
  Menu,
  Sun,
  Moon,
  LogOut,
  ScrollText
} from 'lucide-vue-next'
import { useUserStore } from '@/stores/user'
import { useTheme } from '@/composables/useTheme'
import { useConfirm } from '@/composables/useConfirm'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const { theme, toggle: toggleTheme } = useTheme()
const { confirm } = useConfirm()

interface MenuItem {
  /** 叶子菜单的路径；分组菜单为 undefined */
  path?: string
  title: string
  icon: typeof LayoutDashboard
  /** 分组菜单的子项；叶子菜单无 */
  children?: MenuItem[]
}

const menus: MenuItem[] = [
  { path: '/dashboard', title: '总览', icon: LayoutDashboard },
  {
    title: '系统管理',
    icon: Settings,
    children: [
      { path: '/system/users', title: '系统用户', icon: UserCog }
    ]
  },
  {
    title: '会员管理',
    icon: Users,
    children: [
      { path: '/member/accounts', title: '会员账户', icon: UserCircle },
      { path: '/member/logs', title: '操作日志', icon: ScrollText }
    ]
  },
  { path: '/business/plans', title: '套餐与 CDK', icon: Package },
  { path: '/resource/servers', title: '服务器管理', icon: Server },
  { path: '/xray/inbounds', title: 'Xray 配置', icon: Cable },
  { path: '/monitor/alerts', title: '监控告警', icon: Bell }
]

const activePath = computed(() => route.path)

/** 判断分组是否包含当前激活路由(用于 details 自动展开)。 */
function groupActive(group: MenuItem): boolean {
  return group.children?.some((c) => c.path && activePath.value.startsWith(c.path)) ?? false
}

/** 判断单条菜单是否激活(高亮)。 */
function leafActive(path?: string): boolean {
  return !!path && activePath.value.startsWith(path)
}

async function onLogout() {
  const ok = await confirm({
    title: '退出登录',
    message: '退出后需重新登录，是否继续？',
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
  <div class="drawer lg:drawer-open">
    <input id="nook-drawer" type="checkbox" class="drawer-toggle" />

    <!-- 主区 -->
    <div class="drawer-content flex flex-col min-h-screen bg-base-200">
      <!-- 顶栏 -->
      <header class="navbar bg-base-100 shadow-sm px-4 sticky top-0 z-30 min-h-14">
        <div class="flex-none lg:hidden">
          <label for="nook-drawer" class="btn btn-square btn-ghost btn-sm">
            <Menu class="w-5 h-5" />
          </label>
        </div>
        <div class="flex-1 text-base font-medium">{{ route.meta.title }}</div>
        <div class="flex-none flex items-center gap-1">
          <button
            class="btn btn-ghost btn-circle btn-sm"
            :title="theme === 'dark' ? '切换为浅色' : '切换为深色'"
            @click="toggleTheme"
          >
            <Sun v-if="theme === 'dark'" class="w-5 h-5" />
            <Moon v-else class="w-5 h-5" />
          </button>

          <div class="dropdown dropdown-end">
            <div tabindex="0" role="button" class="btn btn-ghost btn-sm gap-1">
              <div class="avatar placeholder">
                <div class="bg-primary text-primary-content rounded-full w-7">
                  <span class="text-sm">{{ (userStore.user?.username || 'A')[0].toUpperCase() }}</span>
                </div>
              </div>
              <span class="hidden sm:inline">
                {{ userStore.user?.realName || userStore.user?.username || '未登录' }}
              </span>
              <ChevronDown class="w-4 h-4 opacity-60" />
            </div>
            <ul tabindex="0" class="dropdown-content menu bg-base-100 rounded-box w-44 mt-2 shadow-lg border border-base-300 z-40">
              <li class="menu-title text-xs"><span>{{ userStore.user?.role || '-' }}</span></li>
              <li>
                <a @click="onLogout">
                  <LogOut class="w-4 h-4" />退出登录
                </a>
              </li>
            </ul>
          </div>
        </div>
      </header>

      <!-- 内容 -->
      <main class="flex-1 p-6">
        <RouterView />
      </main>
    </div>

    <!-- 侧边抽屉 -->
    <aside class="drawer-side z-40">
      <label for="nook-drawer" class="drawer-overlay"></label>
      <div class="w-60 min-h-full bg-base-100 border-r border-base-300 flex flex-col">
        <div class="h-14 flex items-center px-4 text-base font-semibold border-b border-base-300">
          <span class="inline-block w-7 h-7 bg-primary text-primary-content rounded mr-2 leading-7 text-center text-sm">N</span>
          Nook 管理端
        </div>

        <ul class="menu menu-md p-2 gap-0.5 flex-1">
          <template v-for="m in menus" :key="m.title">
            <!-- 叶子菜单 -->
            <li v-if="m.path">
              <RouterLink :to="m.path" :class="{ active: leafActive(m.path) }" class="rounded-md">
                <component :is="m.icon" class="w-4 h-4" />
                <span>{{ m.title }}</span>
              </RouterLink>
            </li>
            <!-- 分组菜单：含当前激活子项时自动展开 -->
            <li v-else>
              <details :open="groupActive(m)">
                <summary class="rounded-md">
                  <component :is="m.icon" class="w-4 h-4" />
                  <span>{{ m.title }}</span>
                </summary>
                <ul>
                  <li v-for="c in m.children" :key="c.path">
                    <RouterLink
                      :to="c.path!"
                      :class="{ active: leafActive(c.path) }"
                      class="rounded-md"
                    >
                      <component :is="c.icon" class="w-4 h-4" />
                      <span>{{ c.title }}</span>
                    </RouterLink>
                  </li>
                </ul>
              </details>
            </li>
          </template>
        </ul>

        <div class="p-3 text-xs text-base-content/40 border-t border-base-300">
          v1.0.0-SNAPSHOT
        </div>
      </div>
    </aside>
  </div>
</template>
