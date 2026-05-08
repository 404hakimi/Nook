<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { User, Lock, Sun, Moon } from 'lucide-vue-next'
import { NButton, NCard, NForm, NFormItem, NIcon, NInput, useMessage } from 'naive-ui'
import type { FormInst, FormRules } from 'naive-ui'
import { useUserStore } from '@/stores/user'
import { useTheme } from '@/composables/useTheme'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const message = useMessage()
const { theme, toggle: toggleTheme } = useTheme()

const formRef = ref<FormInst | null>(null)
const loading = ref(false)
const form = reactive({ username: '', password: '' })

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: ['blur', 'input'] }],
  password: [
    { required: true, message: '请输入密码', trigger: ['blur', 'input'] },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' }
  ]
}

async function onSubmit(e: Event) {
  e.preventDefault()
  if (loading.value) return
  try {
    await formRef.value?.validate()
  } catch {
    return // 校验未通过, 提示已由 NFormItem 显示
  }
  loading.value = true
  try {
    await userStore.login(form)
    message.success('登录成功')
    const redirect = (route.query.redirect as string) || '/dashboard'
    router.replace(redirect)
  } catch {
    // request.ts 拦截器已经 toast
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="min-h-screen relative flex items-center justify-center overflow-hidden bg-[var(--n-color-modal)]">
    <!-- 背景柔光球 -->
    <div class="absolute -top-32 -left-32 w-96 h-96 rounded-full blur-3xl bg-[var(--n-color-primary)]/20"></div>
    <div class="absolute -bottom-32 -right-32 w-96 h-96 rounded-full blur-3xl bg-[var(--n-color-info)]/30"></div>

    <!-- 主题切换 -->
    <NButton
      circle
      tertiary
      size="small"
      class="!absolute top-4 right-4 z-10"
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

    <NCard class="!w-[22rem] shadow-2xl relative z-0" :bordered="false">
      <div class="flex items-center justify-center gap-2 mb-6">
        <span class="inline-flex w-9 h-9 items-center justify-center bg-[var(--n-color-primary)] text-white rounded-lg text-lg font-bold">N</span>
        <h1 class="text-xl font-semibold m-0">Nook 管理端</h1>
      </div>

      <NForm
        ref="formRef"
        :model="form"
        :rules="rules"
        label-placement="left"
        :show-label="false"
        :show-require-mark="false"
        @submit="onSubmit"
      >
        <NFormItem path="username">
          <NInput
            v-model:value="form.username"
            placeholder="用户名"
            :input-props="{ autocomplete: 'username' }"
          >
            <template #prefix>
              <NIcon><User /></NIcon>
            </template>
          </NInput>
        </NFormItem>

        <NFormItem path="password">
          <NInput
            v-model:value="form.password"
            type="password"
            show-password-on="click"
            placeholder="密码"
            :input-props="{ autocomplete: 'current-password' }"
            @keyup.enter="onSubmit"
          >
            <template #prefix>
              <NIcon><Lock /></NIcon>
            </template>
          </NInput>
        </NFormItem>

        <NButton
          type="primary"
          attr-type="submit"
          block
          :loading="loading"
          @click="onSubmit"
        >
          {{ loading ? '登录中…' : '登 录' }}
        </NButton>
      </NForm>
    </NCard>
  </div>
</template>
