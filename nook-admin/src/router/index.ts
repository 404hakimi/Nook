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
      {
        path: 'resource/servers',
        name: 'resource-servers',
        component: () => import('@/views/resource/ServerList.vue'),
        meta: { title: '服务器管理' }
      },
      {
        path: 'resource/ip-pool',
        name: 'resource-ip-pool',
        component: () => import('@/views/resource/IpPoolList.vue'),
        meta: { title: 'IP 池' }
      },
      {
        path: 'xray/inbounds',
        name: 'xray-inbounds',
        component: () => import('@/views/xray/InboundList.vue'),
        meta: { title: 'Xray 配置' }
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
