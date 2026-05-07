<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { KeyRound, MoreVertical, Pencil, Plus, RefreshCcw, Search, Trash2 } from 'lucide-vue-next'
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
import { formatDateTime } from '@/utils/date'
import Select from '@/components/Select.vue'
import UserFormDialog from './UserFormDialog.vue'

// 筛选/分页选项常量；放到外面便于多页面复用，但这里业务集中、暂不抽
const STATUS_OPTIONS: { label: string; value: number | undefined }[] = [
  { label: '全部', value: undefined },
  { label: '正常', value: 1 },
  { label: '禁用', value: 2 }
]
const ROLE_OPTIONS: { label: string; value: string }[] = [
  { label: '全部', value: '' },
  { label: '超级管理员', value: 'super_admin' },
  { label: '运营', value: 'operator' },
  { label: '运维', value: 'devops' }
]
const PAGE_SIZE_OPTIONS: { label: string; value: number }[] = [
  { label: '10 条/页', value: 10 },
  { label: '20 条/页', value: 20 },
  { label: '50 条/页', value: 50 }
]

const toast = useToast()
const { confirm } = useConfirm()
const userStore = useUserStore()

// ===== 列表 + 查询 =====
const query = reactive<Required<Pick<SystemUserQuery, 'pageNo' | 'pageSize'>> & SystemUserQuery>({
  pageNo: 1,
  pageSize: 10,
  keyword: '',
  status: undefined,
  role: ''
})
const list = ref<SystemUser[]>([])
const total = ref(0)
const loading = ref(false)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / query.pageSize)))

async function loadList() {
  loading.value = true
  try {
    const res = await pageSystemUsers({
      pageNo: query.pageNo,
      pageSize: query.pageSize,
      keyword: query.keyword || undefined,
      status: query.status,
      role: query.role || undefined
    })
    // 删完最后一页的最后一条 → 当前 pageNo 已超出真实总页数；自动回退一页重拉
    const maxPage = res.total > 0 ? Math.ceil(res.total / query.pageSize) : 1
    if (query.pageNo > maxPage) {
      query.pageNo = maxPage
      loading.value = false
      await loadList()
      return
    }
    list.value = res.records
    total.value = res.total
  } catch {
    /* request 拦截器已 toast */
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.pageNo = 1
  query.keyword = ''
  query.status = undefined
  query.role = ''
  loadList()
}

function onSearch() {
  query.pageNo = 1
  loadList()
}

function goPage(p: number) {
  if (p < 1 || p > totalPages.value) return
  query.pageNo = p
  loadList()
}

function roleLabel(role: string): string {
  return ROLE_LABELS[role] || role
}

/** DaisyUI dropdown 用 :focus-within 保持展开，点选菜单项后 blur 当前焦点元素让它收起。 */
function runAndCloseDropdown(fn: () => void) {
  if (document.activeElement instanceof HTMLElement) {
    document.activeElement.blur()
  }
  fn()
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

async function onFormSaved() {
  // 等列表真的回来了再让用户操作，避免再次点编辑时拿到旧行
  await loadList()
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
            <Select v-model="query.status" :options="STATUS_OPTIONS" width="w-32" />
          </div>
          <div>
            <label class="label py-0"><span class="label-text">角色</span></label>
            <Select v-model="query.role" :options="ROLE_OPTIONS" width="w-40" />
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
      <!--
        说明：刻意不再外层包 overflow-x-auto，否则 CSS 规范会把 overflow-y 推断为 auto，
        导致行操作 DaisyUI Dropdown 的菜单被纵向裁剪。后台管理页只面向桌面端，
        极窄屏由页面级横向滚动兜底。
      -->
      <div class="card-body p-0">
        <div>
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
                <td class="text-sm text-base-content/70 whitespace-nowrap">{{ formatDateTime(u.lastLoginAt) }}</td>
                <td class="text-sm text-base-content/70 whitespace-nowrap">{{ formatDateTime(u.createdAt) }}</td>
                <td>
                  <div class="flex justify-end">
                    <div class="dropdown dropdown-end">
                      <div
                        tabindex="0"
                        role="button"
                        class="btn btn-ghost btn-xs btn-square"
                        aria-label="更多操作"
                      >
                        <MoreVertical class="w-4 h-4" />
                      </div>
                      <ul
                        tabindex="0"
                        class="dropdown-content menu menu-sm bg-base-100 rounded-box shadow-lg border border-base-200 z-20 w-32 p-1"
                      >
                        <li>
                          <a @click="runAndCloseDropdown(() => openEdit(u))">
                            <Pencil class="w-4 h-4" />编辑
                          </a>
                        </li>
                        <li>
                          <a @click="runAndCloseDropdown(() => openReset(u))">
                            <KeyRound class="w-4 h-4" />重置密码
                          </a>
                        </li>
                        <li>
                          <a class="text-error" @click="runAndCloseDropdown(() => onDelete(u))">
                            <Trash2 class="w-4 h-4" />删除
                          </a>
                        </li>
                      </ul>
                    </div>
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
            <Select
              v-model="query.pageSize"
              :options="PAGE_SIZE_OPTIONS"
              width="w-28"
              align="end"
              direction="top"
              @change="onSearch"
            />
            <div class="join">
              <button
                class="join-item btn btn-sm"
                :disabled="query.pageNo === 1"
                @click="goPage(query.pageNo - 1)"
              >«</button>
              <button class="join-item btn btn-sm pointer-events-none">
                {{ query.pageNo }} / {{ totalPages }}
              </button>
              <button
                class="join-item btn btn-sm"
                :disabled="query.pageNo >= totalPages"
                @click="goPage(query.pageNo + 1)"
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
