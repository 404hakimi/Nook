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
