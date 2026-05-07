<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { KeyRound, Pencil, Plus, RefreshCcw, Search, Trash2 } from 'lucide-vue-next'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { useUserStore } from '@/stores/user'
import {
  deleteSystemUser,
  pageSystemUsers,
  resetSystemUserPassword,
  ROLE_LABELS,
  type SystemUser,
  type SystemUserQuery
} from '@/api/system/user'
import UserFormDialog from './UserFormDialog.vue'

const toast = useToast()
const { confirm } = useConfirm()
const userStore = useUserStore()

// ===== 列表 + 查询 =====
const query = reactive<Required<Pick<SystemUserQuery, 'page' | 'size'>> & SystemUserQuery>({
  page: 1,
  size: 20,
  keyword: '',
  status: undefined,
  role: ''
})
const list = ref<SystemUser[]>([])
const total = ref(0)
const loading = ref(false)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / query.size)))

async function loadList() {
  loading.value = true
  try {
    const res = await pageSystemUsers({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      status: query.status,
      role: query.role || undefined
    })
    list.value = res.records
    total.value = res.total
  } catch {
    /* request 拦截器已 toast */
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.page = 1
  query.keyword = ''
  query.status = undefined
  query.role = ''
  loadList()
}

function onSearch() {
  query.page = 1
  loadList()
}

function goPage(p: number) {
  if (p < 1 || p > totalPages.value) return
  query.page = p
  loadList()
}

function roleLabel(role: string): string {
  return ROLE_LABELS[role] || role
}

// ===== 新增/编辑 =====
const formOpen = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const formUser = ref<SystemUser | null>(null)

function openCreate() {
  formMode.value = 'create'
  formUser.value = null
  formOpen.value = true
}

function openEdit(u: SystemUser) {
  formMode.value = 'edit'
  formUser.value = u
  formOpen.value = true
}

function onFormSaved() {
  loadList()
}

// ===== 删除 =====
async function onDelete(u: SystemUser) {
  if (u.id === userStore.user?.id) {
    toast.warning('不能删除当前登录账号')
    return
  }
  const ok = await confirm({
    title: '删除用户',
    message: `确定删除用户 "${u.username}" 吗？该操作可被超管恢复。`,
    type: 'danger',
    confirmText: '删除'
  })
  if (!ok) return
  try {
    await deleteSystemUser(u.id)
    toast.success('删除成功')
    loadList()
  } catch {
    /* */
  }
}

// ===== 重置密码 =====
const resetOpen = ref(false)
const resetTarget = ref<SystemUser | null>(null)
const resetSubmitting = ref(false)
const newPassword = ref('')
const resetError = ref('')

function openReset(u: SystemUser) {
  resetTarget.value = u
  newPassword.value = ''
  resetError.value = ''
  resetOpen.value = true
}

function closeReset() {
  resetOpen.value = false
}

async function confirmReset() {
  if (!newPassword.value || newPassword.value.length < 6) {
    resetError.value = '密码至少 6 位'
    return
  }
  if (newPassword.value.length > 64) {
    resetError.value = '密码不能超过 64 位'
    return
  }
  resetSubmitting.value = true
  try {
    await resetSystemUserPassword(resetTarget.value!.id, newPassword.value)
    toast.success(`已重置 ${resetTarget.value!.username} 的密码`)
    closeReset()
  } catch {
    /* */
  } finally {
    resetSubmitting.value = false
  }
}

onMounted(loadList)
</script>

<template>
  <div class="space-y-4">
    <!-- 顶部搜索栏 -->
    <div class="card bg-base-100 shadow-sm">
      <div class="card-body py-4">
        <div class="flex flex-wrap gap-3 items-end">
          <div>
            <label class="label py-0"><span class="label-text">关键词</span></label>
            <input
              v-model="query.keyword"
              type="text"
              placeholder="用户名 / 姓名 / 邮箱"
              class="input input-bordered input-sm w-56"
              @keyup.enter="onSearch"
            />
          </div>
          <div>
            <label class="label py-0"><span class="label-text">状态</span></label>
            <select v-model="query.status" class="select select-bordered select-sm w-32">
              <option :value="undefined">全部</option>
              <option :value="1">正常</option>
              <option :value="2">禁用</option>
            </select>
          </div>
          <div>
            <label class="label py-0"><span class="label-text">角色</span></label>
            <select v-model="query.role" class="select select-bordered select-sm w-40">
              <option value="">全部</option>
              <option value="super_admin">超级管理员</option>
              <option value="operator">运营</option>
              <option value="devops">运维</option>
            </select>
          </div>
          <button class="btn btn-primary btn-sm" @click="onSearch">
            <Search class="w-4 h-4" />搜索
          </button>
          <button class="btn btn-ghost btn-sm" @click="resetQuery">
            <RefreshCcw class="w-4 h-4" />重置
          </button>
          <div class="flex-1"></div>
          <button class="btn btn-primary btn-sm" @click="openCreate">
            <Plus class="w-4 h-4" />新增用户
          </button>
        </div>
      </div>
    </div>

    <!-- 表格 -->
    <div class="card bg-base-100 shadow-sm">
      <div class="card-body p-0">
        <div class="overflow-x-auto">
          <table class="table table-zebra">
            <thead>
              <tr>
                <th>用户名</th>
                <th>姓名</th>
                <th>角色</th>
                <th>状态</th>
                <th>邮箱</th>
                <th>最后登录</th>
                <th>创建时间</th>
                <th class="text-right">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading">
                <td colspan="8" class="text-center py-12">
                  <span class="loading loading-spinner"></span>
                </td>
              </tr>
              <tr v-else-if="!list.length">
                <td colspan="8" class="text-center py-12 text-base-content/40">暂无数据</td>
              </tr>
              <tr v-for="u in list" :key="u.id">
                <td><span class="font-mono text-sm">{{ u.username }}</span></td>
                <td>{{ u.realName || '-' }}</td>
                <td>
                  <span class="badge badge-outline badge-sm">{{ roleLabel(u.role) }}</span>
                </td>
                <td>
                  <span
                    :class="[
                      'badge badge-sm',
                      u.status === 1 ? 'badge-success' : 'badge-error'
                    ]"
                  >
                    {{ u.status === 1 ? '正常' : '禁用' }}
                  </span>
                </td>
                <td class="text-sm">{{ u.email || '-' }}</td>
                <td class="text-sm text-base-content/70">{{ u.lastLoginAt || '-' }}</td>
                <td class="text-sm text-base-content/70">{{ u.createdAt || '-' }}</td>
                <td>
                  <div class="flex justify-end gap-1">
                    <button
                      class="btn btn-ghost btn-xs"
                      title="编辑"
                      @click="openEdit(u)"
                    >
                      <Pencil class="w-3.5 h-3.5" />
                    </button>
                    <button
                      class="btn btn-ghost btn-xs"
                      title="重置密码"
                      @click="openReset(u)"
                    >
                      <KeyRound class="w-3.5 h-3.5" />
                    </button>
                    <button
                      class="btn btn-ghost btn-xs text-error"
                      title="删除"
                      @click="onDelete(u)"
                    >
                      <Trash2 class="w-3.5 h-3.5" />
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- 分页 -->
        <div class="flex items-center justify-between p-4 border-t border-base-200">
          <div class="text-sm text-base-content/60">共 {{ total }} 条</div>
          <div class="flex items-center gap-2">
            <select
              v-model="query.size"
              class="select select-bordered select-sm"
              @change="onSearch"
            >
              <option :value="10">10 条/页</option>
              <option :value="20">20 条/页</option>
              <option :value="50">50 条/页</option>
            </select>
            <div class="join">
              <button
                class="join-item btn btn-sm"
                :disabled="query.page === 1"
                @click="goPage(query.page - 1)"
              >«</button>
              <button class="join-item btn btn-sm pointer-events-none">
                {{ query.page }} / {{ totalPages }}
              </button>
              <button
                class="join-item btn btn-sm"
                :disabled="query.page >= totalPages"
                @click="goPage(query.page + 1)"
              >»</button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 新增/编辑 弹框 -->
    <UserFormDialog
      v-model="formOpen"
      :mode="formMode"
      :user="formUser"
      @saved="onFormSaved"
    />

    <!-- 重置密码 弹框 -->
    <dialog class="modal" :class="{ 'modal-open': resetOpen }">
      <div class="modal-box max-w-md">
        <h3 class="text-lg font-semibold mb-4">重置密码</h3>
        <p class="text-sm text-base-content/70 mb-3">
          为用户 <span class="font-mono font-semibold">{{ resetTarget?.username }}</span> 设置新密码
        </p>
        <input
          v-model="newPassword"
          type="password"
          placeholder="6-64 位"
          class="input input-bordered input-sm w-full"
          :class="{ 'input-error': resetError }"
          @keyup.enter="confirmReset"
        />
        <div v-if="resetError" class="text-error text-xs mt-1">{{ resetError }}</div>
        <div class="modal-action mt-6">
          <button class="btn btn-ghost btn-sm" @click="closeReset">取消</button>
          <button
            class="btn btn-primary btn-sm"
            :disabled="resetSubmitting"
            @click="confirmReset"
          >
            <span v-if="resetSubmitting" class="loading loading-spinner loading-xs"></span>
            确定
          </button>
        </div>
      </div>
      <div class="modal-backdrop bg-black/40" @click="closeReset"></div>
    </dialog>
  </div>
</template>
