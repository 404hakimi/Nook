<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { useToast } from '@/composables/useToast'
import {
  createSystemUser,
  updateSystemUser,
  type CreateSystemUserDTO,
  type SystemUser,
  type UpdateSystemUserDTO
} from '@/api/system/user'

interface Props {
  modelValue: boolean
  mode: 'create' | 'edit'
  user?: SystemUser | null
}
const props = defineProps<Props>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'saved'): void
}>()

const toast = useToast()
const submitting = ref(false)
const errors = reactive<Record<string, string>>({})

const form = reactive({
  username: '',
  password: '',
  realName: '',
  email: '',
  role: 'operator',
  status: 1,
  remark: ''
})

// 弹框打开时根据 mode + user 重置表单
watch(
  () => [props.modelValue, props.user, props.mode],
  ([open]) => {
    if (!open) return
    Object.keys(errors).forEach((k) => delete errors[k])
    if (props.mode === 'edit' && props.user) {
      form.username = props.user.username
      form.password = ''
      form.realName = props.user.realName ?? ''
      form.email = props.user.email ?? ''
      form.role = props.user.role
      form.status = props.user.status
      form.remark = props.user.remark ?? ''
    } else {
      form.username = ''
      form.password = ''
      form.realName = ''
      form.email = ''
      form.role = 'operator'
      form.status = 1
      form.remark = ''
    }
  }
)

function validate(): boolean {
  Object.keys(errors).forEach((k) => delete errors[k])
  if (props.mode === 'create') {
    if (!form.username.trim()) errors.username = '请输入用户名'
    else if (!/^[a-zA-Z0-9_]{3,32}$/.test(form.username))
      errors.username = '用户名 3-32 位字母数字下划线'
    if (!form.password) errors.password = '请输入密码'
    else if (form.password.length < 6) errors.password = '密码至少 6 位'
  }
  if (form.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email))
    errors.email = '邮箱格式不正确'
  if (!form.role) errors.role = '请选择角色'
  return Object.keys(errors).length === 0
}

async function onSubmit() {
  if (!validate()) return
  submitting.value = true
  try {
    if (props.mode === 'create') {
      const dto: CreateSystemUserDTO = {
        username: form.username,
        password: form.password,
        realName: form.realName || undefined,
        email: form.email || undefined,
        role: form.role,
        remark: form.remark || undefined
      }
      await createSystemUser(dto)
      toast.success('创建成功')
    } else {
      const dto: UpdateSystemUserDTO = {
        realName: form.realName || undefined,
        email: form.email || undefined,
        role: form.role,
        status: form.status,
        remark: form.remark || undefined
      }
      await updateSystemUser(props.user!.id, dto)
      toast.success('更新成功')
    }
    emit('saved')
    emit('update:modelValue', false)
  } catch {
    // request 拦截器已 toast
  } finally {
    submitting.value = false
  }
}

function close() {
  emit('update:modelValue', false)
}
</script>

<template>
  <dialog class="modal" :class="{ 'modal-open': modelValue }">
    <div class="modal-box max-w-2xl">
      <h3 class="text-lg font-semibold mb-4">
        {{ mode === 'create' ? '新增后台用户' : '编辑后台用户' }}
      </h3>

      <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div>
          <label class="label py-1"><span class="label-text">用户名 <span class="text-error">*</span></span></label>
          <input
            v-model="form.username"
            type="text"
            :disabled="mode === 'edit'"
            class="input input-bordered input-sm w-full"
            :class="{ 'input-error': errors.username }"
          />
          <div v-if="errors.username" class="text-error text-xs mt-1">{{ errors.username }}</div>
        </div>

        <div v-if="mode === 'create'">
          <label class="label py-1"><span class="label-text">密码 <span class="text-error">*</span></span></label>
          <input
            v-model="form.password"
            type="password"
            class="input input-bordered input-sm w-full"
            :class="{ 'input-error': errors.password }"
            placeholder="6-64 位"
          />
          <div v-if="errors.password" class="text-error text-xs mt-1">{{ errors.password }}</div>
        </div>
        <div v-else>
          <label class="label py-1"><span class="label-text">状态</span></label>
          <select v-model="form.status" class="select select-bordered select-sm w-full">
            <option :value="1">正常</option>
            <option :value="2">禁用</option>
          </select>
        </div>

        <div>
          <label class="label py-1"><span class="label-text">真实姓名</span></label>
          <input v-model="form.realName" type="text" class="input input-bordered input-sm w-full" />
        </div>

        <div>
          <label class="label py-1"><span class="label-text">角色 <span class="text-error">*</span></span></label>
          <select
            v-model="form.role"
            class="select select-bordered select-sm w-full"
            :class="{ 'select-error': errors.role }"
          >
            <option value="super_admin">超级管理员</option>
            <option value="operator">运营</option>
            <option value="devops">运维</option>
          </select>
        </div>

        <div>
          <label class="label py-1"><span class="label-text">邮箱</span></label>
          <input
            v-model="form.email"
            type="email"
            class="input input-bordered input-sm w-full"
            :class="{ 'input-error': errors.email }"
          />
          <div v-if="errors.email" class="text-error text-xs mt-1">{{ errors.email }}</div>
        </div>

        <div class="sm:col-span-2">
          <label class="label py-1"><span class="label-text">备注</span></label>
          <textarea
            v-model="form.remark"
            rows="2"
            class="textarea textarea-bordered w-full text-sm"
          ></textarea>
        </div>
      </div>

      <div class="modal-action mt-6">
        <button class="btn btn-ghost btn-sm" @click="close">取消</button>
        <button class="btn btn-primary btn-sm" :disabled="submitting" @click="onSubmit">
          <span v-if="submitting" class="loading loading-spinner loading-xs"></span>
          确定
        </button>
      </div>
    </div>
    <div class="modal-backdrop bg-black/40" @click="close"></div>
  </dialog>
</template>
