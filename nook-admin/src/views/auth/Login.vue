<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { User, Lock, Sun, Moon } from 'lucide-vue-next'
import { useUserStore } from '@/stores/user'
import { useToast } from '@/composables/useToast'
import { useTheme } from '@/composables/useTheme'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const toast = useToast()
const { theme, toggle: toggleTheme } = useTheme()

const loading = ref(false)
const errors = reactive<{ username?: string; password?: string }>({})
const form = reactive({ username: '', password: '' })

function validate() {
  errors.username = errors.password = undefined
  if (!form.username.trim()) errors.username = '请输入用户名'
  if (!form.password) errors.password = '请输入密码'
  else if (form.password.length < 6) errors.password = '密码至少 6 位'
  return !errors.username && !errors.password
}

async function onSubmit() {
  // 表单 type=submit 按钮已经处理了 Enter 触发，禁止给输入框再绑 keyup.enter
  if (loading.value) return
  if (!validate()) return
  loading.value = true
  try {
    await userStore.login(form)
    toast.success('登录成功')
    const redirect = (route.query.redirect as string) || '/dashboard'
    router.replace(redirect)
  } catch {
    // request.ts 拦截器已经 toast 了
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="min-h-screen relative flex items-center justify-center bg-base-200 overflow-hidden">
    <!-- 背景装饰：两个柔光球 -->
    <div class="absolute -top-32 -left-32 w-96 h-96 bg-primary/20 rounded-full blur-3xl"></div>
    <div class="absolute -bottom-32 -right-32 w-96 h-96 bg-secondary/20 rounded-full blur-3xl"></div>

    <!-- 主题切换：固定右上角 -->
    <button
      class="btn btn-ghost btn-circle btn-sm absolute top-4 right-4 z-10"
      :title="theme === 'dark' ? '切换为浅色' : '切换为深色'"
      @click="toggleTheme"
    >
      <Sun v-if="theme === 'dark'" class="w-5 h-5" />
      <Moon v-else class="w-5 h-5" />
    </button>

    <!-- 登录卡 -->
    <div class="card w-[22rem] bg-base-100 shadow-2xl relative z-0">
      <div class="card-body p-8">
        <div class="flex items-center justify-center gap-2 mb-6">
          <span class="inline-flex w-9 h-9 items-center justify-center bg-primary text-primary-content rounded-lg text-lg font-bold">N</span>
          <h1 class="text-xl font-semibold">Nook 管理端</h1>
        </div>

        <form @submit.prevent="onSubmit" class="space-y-4" novalidate>
          <div class="form-control">
            <label
              class="input input-bordered flex items-center gap-2"
              :class="{ 'input-error': errors.username }"
            >
              <User class="w-4 h-4 opacity-60" />
              <input
                v-model="form.username"
                type="text"
                class="grow"
                placeholder="用户名"
                autocomplete="username"
              />
            </label>
            <label v-if="errors.username" class="label py-1">
              <span class="label-text-alt text-error">{{ errors.username }}</span>
            </label>
          </div>

          <div class="form-control">
            <label
              class="input input-bordered flex items-center gap-2"
              :class="{ 'input-error': errors.password }"
            >
              <Lock class="w-4 h-4 opacity-60" />
              <input
                v-model="form.password"
                type="password"
                class="grow"
                placeholder="密码"
                autocomplete="current-password"
              />
            </label>
            <label v-if="errors.password" class="label py-1">
              <span class="label-text-alt text-error">{{ errors.password }}</span>
            </label>
          </div>

          <button
            type="submit"
            class="btn btn-primary w-full mt-2"
            :disabled="loading"
          >
            <span v-if="loading" class="loading loading-spinner loading-sm"></span>
            {{ loading ? '登录中…' : '登 录' }}
          </button>
        </form>
      </div>
    </div>
  </div>
</template>
