<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import {
  NButton,
  NForm,
  NFormItem,
  NInput,
  NModal,
  NSelect,
  NSpace,
  NSpin,
  useMessage
} from 'naive-ui'
import { useUserStore } from '@/stores/user'
import {
  createSystemUser,
  getSystemUserDetail,
  updateSystemUser,
  type SystemUser,
  type SystemUserSaveDTO
} from '@/api/system/user'

// 角色 / 状态枚举常量；与 UserList 一致的取值
const ROLE_OPTIONS: { label: string; value: string }[] = [
  { label: '超级管理员', value: 'super_admin' },
  { label: '运营', value: 'operator' },
  { label: '运维', value: 'devops' }
]
const STATUS_OPTIONS: { label: string; value: number }[] = [
  { label: '正常', value: 1 },
  { label: '禁用', value: 2 }
]

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

const message = useMessage()
const userStore = useUserStore()
const submitting = ref(false)
/** 编辑模式打开时调详情接口期间的 loading；保证用户看到的是最新数据，不是行缓存。 */
const loadingDetail = ref(false)
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

/** 编辑的是当前登录账号——禁止改自己的角色/状态，避免自我降权或自禁用导致掉线。 */
const isSelf = computed(
  () => props.mode === 'edit' && !!props.user && props.user.id === userStore.user?.id
)

function fill(u: SystemUser) {
  form.username = u.username
  form.password = ''
  form.realName = u.realName ?? ''
  form.email = u.email ?? ''
  form.role = u.role
  form.status = u.status
  form.remark = u.remark ?? ''
}

function reset() {
  form.username = ''
  form.password = ''
  form.realName = ''
  form.email = ''
  form.role = 'operator'
  form.status = 1
  form.remark = ''
}

// 弹框打开时根据 mode + user 重置表单；编辑模式必走详情接口拿最新数据
watch(
  () => [props.modelValue, props.user, props.mode],
  async ([open]) => {
    if (!open) return
    Object.keys(errors).forEach((k) => delete errors[k])
    if (props.mode === 'edit' && props.user) {
      const id = props.user.id
      // 用列表行数据先填一次，避免空白闪烁
      fill(props.user)
      loadingDetail.value = true
      try {
        const fresh = await getSystemUserDetail(id)
        // 异步返回时弹框可能已关闭/切换目标，必须复核
        if (props.modelValue && props.mode === 'edit' && props.user?.id === id) {
          fill(fresh)
        }
      } catch {
        /* request 拦截器已 toast */
      } finally {
        loadingDetail.value = false
      }
    } else {
      reset()
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
      const dto: SystemUserSaveDTO = {
        username: form.username,
        password: form.password,
        realName: form.realName || undefined,
        email: form.email || undefined,
        role: form.role,
        remark: form.remark || undefined
      }
      await createSystemUser(dto)
      message.success('创建成功')
    } else {
      // 编辑场景：可空字段一律 trim 后原样上送(包括空串)，让后端识别"清空"语义；
      // 不要 `|| undefined`——那会把"清空"折成"未变更"，字段写不回 DB。
      const dto: SystemUserSaveDTO = {
        realName: form.realName.trim(),
        email: form.email.trim(),
        // 自己编辑自己时不允许提交 role/status，避免自降权或自禁用
        role: isSelf.value ? undefined : form.role,
        status: isSelf.value ? undefined : form.status,
        remark: form.remark.trim()
      }
      await updateSystemUser(props.user!.id, dto)
      message.success('更新成功')
      // 改的是自己——同步 userStore，让顶栏 / 路由守卫能拿到新角色与新名字
      if (isSelf.value) {
        await userStore.fetchCurrentUser().catch(() => {})
      }
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
  <NModal
    :show="modelValue"
    preset="card"
    :title="mode === 'create' ? '新增后台用户' : '编辑后台用户'"
    style="max-width: 56rem; width: 92vw"
    :bordered="false"
    :mask-closable="false"
    :close-on-esc="false"
    @update:show="(v: boolean) => emit('update:modelValue', v)"
  >
    <NSpin :show="loadingDetail">
      <NForm
        :model="form"
        label-placement="top"
        require-mark-placement="right-hanging"
        size="small"
      >
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-x-4">
          <NFormItem
            label="用户名"
            :required="mode === 'create'"
            :validation-status="errors.username ? 'error' : undefined"
            :feedback="errors.username"
          >
            <NInput
              v-model:value="form.username"
              :disabled="mode === 'edit'"
              placeholder="3-32 位字母/数字/下划线"
            />
          </NFormItem>

          <NFormItem
            v-if="mode === 'create'"
            label="密码"
            required
            :validation-status="errors.password ? 'error' : undefined"
            :feedback="errors.password"
          >
            <NInput
              v-model:value="form.password"
              type="password"
              show-password-on="click"
              placeholder="6-64 位"
              :input-props="{ autocomplete: 'new-password' }"
            />
          </NFormItem>
          <NFormItem v-else>
            <template #label>
              <span>状态</span>
              <span v-if="isSelf" class="text-xs text-zinc-400 ml-2">不可改自身</span>
            </template>
            <NSelect v-model:value="form.status" :options="STATUS_OPTIONS" :disabled="isSelf" />
          </NFormItem>

          <NFormItem label="真实姓名">
            <NInput v-model:value="form.realName" placeholder="选填" />
          </NFormItem>

          <NFormItem
            required
            :validation-status="errors.role ? 'error' : undefined"
            :feedback="errors.role"
          >
            <template #label>
              <span>角色</span>
              <span v-if="isSelf" class="text-xs text-zinc-400 ml-2">不可改自身</span>
            </template>
            <NSelect
              v-model:value="form.role"
              :options="ROLE_OPTIONS"
              :disabled="isSelf"
              :status="errors.role ? 'error' : undefined"
            />
          </NFormItem>

          <NFormItem
            label="邮箱"
            :validation-status="errors.email ? 'error' : undefined"
            :feedback="errors.email"
          >
            <NInput v-model:value="form.email" placeholder="选填" />
          </NFormItem>

          <div class="sm:col-span-2">
            <NFormItem label="备注">
              <NInput
                v-model:value="form.remark"
                type="textarea"
                :autosize="{ minRows: 2, maxRows: 4 }"
                placeholder="选填"
              />
            </NFormItem>
          </div>
        </div>
      </NForm>
    </NSpin>

    <template #footer>
      <NSpace justify="end">
        <NButton size="small" @click="close">取消</NButton>
        <NButton
          type="primary"
          size="small"
          :loading="submitting"
          :disabled="loadingDetail"
          @click="onSubmit"
        >
          确定
        </NButton>
      </NSpace>
    </template>
  </NModal>
</template>
