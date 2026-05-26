import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/auth/Login.vue'),
    meta: { public: true, title: '登录' }
  },
  {
    path: '/',
    component: () => import('@/layouts/AdminLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('@/views/dashboard/Index.vue'),
        meta: { title: '总览' }
      },
      {
        path: 'system/users',
        name: 'system-users',
        component: () => import('@/views/system/UserList.vue'),
        meta: { title: '系统用户' }
      },
      {
        path: 'member/accounts',
        name: 'member-accounts',
        component: () => import('@/views/member/AccountList.vue'),
        meta: { title: '会员账户' }
      },
      {
        path: 'member/logs',
        name: 'member-logs',
        component: () => import('@/views/member/OperationLog.vue'),
        meta: { title: '操作日志' }
      },
      // ===== 服务器统一入口 (B 方案 P1): 卡片总览 + 详情 tab =====
      {
        path: 'servers',
        name: 'server-overview',
        component: () => import('@/views/server/ServerOverview.vue'),
        meta: { title: '服务器' }
      },
      {
        path: 'servers/:id',
        name: 'server-detail',
        component: () => import('@/views/server/ServerDetail.vue'),
        meta: { title: '服务器详情', serverType: 'frontline' }
      },
      // 老路由保留 redirect, 避免外部书签 / 历史链接 404
      { path: 'resource/servers', redirect: '/servers' },
      { path: 'agent/list', redirect: '/servers' },
      { path: 'xray/nodes', redirect: '/servers' },

      {
        path: 'resource/server-landing',
        name: 'resource-server-landing',
        component: () => import('@/views/resource/ServerLandingList.vue'),
        meta: { title: '落地机' }
      },
      {
        // 落地机详情走统一 ServerDetail.vue, 通过 meta.serverType 分发
        path: 'resource/server-landing/:id',
        name: 'resource-server-landing-detail',
        component: () => import('@/views/server/ServerDetail.vue'),
        meta: { title: '落地机详情', serverType: 'landing' }
      },
      {
        path: 'xray/clients',
        name: 'xray-clients',
        component: () => import('@/views/xray/ClientList.vue'),
        meta: { title: 'Xray 客户端' }
      },
      {
        path: 'operation/op-log',
        name: 'operation-op-log',
        component: () => import('@/views/operation/OpLogList.vue'),
        meta: { title: '操作流水' }
      },
      {
        path: 'operation/op-config',
        name: 'operation-op-config',
        component: () => import('@/views/operation/OpConfigList.vue'),
        meta: { title: 'Op 调度配置' }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/error/NotFound.vue'),
    meta: { public: true }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫：未登录访问受保护路由 → 跳登录；带 token 但 user 未填充 → 拉一次 me
router.beforeEach(async (to) => {
  const userStore = useUserStore()
  if (to.meta.public) {
    return true
  }
  if (!userStore.token) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (!userStore.user) {
    try {
      await userStore.fetchCurrentUser()
    } catch {
      // me 接口失败(token 失效)，回登录
      userStore.clear()
      return { name: 'login', query: { redirect: to.fullPath } }
    }
  }
  return true
})

router.afterEach((to) => {
  if (to.meta.title) {
    document.title = `${to.meta.title} - Nook 管理端`
  }
})

export default router
