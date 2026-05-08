<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import { KeyRound, MoreVertical, Pencil, Plus, RefreshCcw, Search, Trash2 } from 'lucide-vue-next'
import {
  NButton,
  NCard,
  NDataTable,
  NDropdown,
  NForm,
  NFormItem,
  NIcon,
  NInput,
  NModal,
  NSelect,
  NSpace,
  NTag,
  useMessage,
  type DataTableColumns,
  type DropdownOption
} from 'naive-ui'
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
import UserFormDialog from './UserFormDialog.vue'

// 筛选选项常量；放到外面便于多页面复用，但这里业务集中、暂不抽
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

const message = useMessage()
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

function roleLabel(role: string): string {
  return ROLE_LABELS[role] || role
}

// ===== 行操作菜单（NDropdown 选项 + 分发） =====
const ROW_ACTIONS: DropdownOption[] = [
  {
    label: '编辑',
    key: 'edit',
    icon: () => h(NIcon, null, { default: () => h(Pencil) })
  },
  {
    label: '重置密码',
    key: 'reset-pwd',
    icon: () => h(NIcon, null, { default: () => h(KeyRound) })
  },
  { type: 'divider', key: 'd1' },
  {
    label: '删除',
    key: 'delete',
    props: { style: 'color: var(--n-error-color)' },
    icon: () => h(NIcon, { color: 'var(--n-error-color)' }, { default: () => h(Trash2) })
  }
]

function onRowAction(key: string | number, u: SystemUser) {
  if (key === 'edit') openEdit(u)
  else if (key === 'reset-pwd') openReset(u)
  else if (key === 'delete') onDelete(u)
}

// ===== 表格列定义 =====
const columns = computed<DataTableColumns<SystemUser>>(() => [
  {
    title: '用户名',
    key: 'username',
    render: (row) => h('span', { class: 'font-mono text-sm' }, row.username)
  },
  { title: '姓名', key: 'realName', render: (row) => row.realName || '-' },
  {
    title: '角色',
    key: 'role',
    render: (row) =>
      h(NTag, { size: 'small', bordered: true }, { default: () => roleLabel(row.role) })
  },
  {
    title: '状态',
    key: 'status',
    render: (row) =>
      h(
        NTag,
        { size: 'small', type: row.status === 1 ? 'success' : 'error' },
        { default: () => (row.status === 1 ? '正常' : '禁用') }
      )
  },
  { title: '邮箱', key: 'email', render: (row) => row.email || '-' },
  {
    title: '最后登录',
    key: 'lastLoginAt',
    width: 170,
    render: (row) => formatDateTime(row.lastLoginAt)
  },
  {
    title: '创建时间',
    key: 'createdAt',
    width: 170,
    render: (row) => formatDateTime(row.createdAt)
  },
  {
    title: '操作',
    key: 'actions',
    align: 'right',
    width: 80,
    render: (row) =>
      h(
        NDropdown,
        {
          options: ROW_ACTIONS,
          trigger: 'click',
          onSelect: (key: string | number) => onRowAction(key, row)
        },
        {
          default: () =>
            h(
              NButton,
              { circle: true, quaternary: true, size: 'small' },
              { default: () => h(NIcon, null, { default: () => h(MoreVertical) }) }
            )
        }
      )
  }
])

const pagination = computed(() => ({
  page: query.pageNo,
  pageSize: query.pageSize,
  itemCount: total.value,
  pageSizes: [10, 20, 50],
  showSizePicker: true,
  prefix: ({ itemCount }: { itemCount: number }) => `共 ${itemCount} 条`,
  onUpdatePage: (p: number) => {
    query.pageNo = p
    loadList()
  },
  onUpdatePageSize: (s: number) => {
    query.pageSize = s
    query.pageNo = 1
    loadList()
  }
}))

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
    message.warning('不能删除当前登录账号')
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
    message.success('删除成功')
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
    message.success(`已重置 ${resetTarget.value!.username} 的密码`)
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
    <NCard size="small">
      <div class="flex flex-wrap gap-3 items-end">
        <div>
          <div class="text-xs text-zinc-500 mb-1">关键词</div>
          <NInput
            v-model:value="query.keyword"
            size="small"
            placeholder="用户名 / 姓名 / 邮箱"
            class="w-56"
            @keyup.enter="onSearch"
          />
        </div>
        <div>
          <div class="text-xs text-zinc-500 mb-1">状态</div>
          <NSelect
            v-model:value="query.status"
            :options="STATUS_OPTIONS"
            size="small"
            class="w-32"
          />
        </div>
        <div>
          <div class="text-xs text-zinc-500 mb-1">角色</div>
          <NSelect
            v-model:value="query.role"
            :options="ROLE_OPTIONS"
            size="small"
            class="w-40"
          />
        </div>
        <NButton type="primary" size="small" @click="onSearch">
          <template #icon><NIcon><Search /></NIcon></template>
          搜索
        </NButton>
        <NButton quaternary size="small" @click="resetQuery">
          <template #icon><NIcon><RefreshCcw /></NIcon></template>
          重置
        </NButton>
        <div class="flex-1"></div>
        <NButton type="primary" size="small" @click="openCreate">
          <template #icon><NIcon><Plus /></NIcon></template>
          新增用户
        </NButton>
      </div>
    </NCard>

    <!-- 表格 + 分页 -->
    <NCard size="small" :content-style="{ padding: 0 }">
      <NDataTable
        :columns="columns"
        :data="list"
        :loading="loading"
        :pagination="pagination"
        :remote="true"
        :bordered="false"
        :row-key="(row: SystemUser) => row.id"
        size="small"
      />
    </NCard>

    <!-- 新增/编辑 弹框 -->
    <UserFormDialog
      v-model="formOpen"
      :mode="formMode"
      :user="formUser"
      @saved="onFormSaved"
    />

    <!-- 重置密码 弹框 -->
    <NModal
      :show="resetOpen"
      preset="card"
      title="重置密码"
      style="max-width: 28rem"
      :bordered="false"
      :mask-closable="false"
      @update:show="(v: boolean) => (resetOpen = v)"
    >
      <NForm size="small" label-placement="top">
        <p class="text-sm mb-3">
          为用户
          <span class="font-mono font-semibold">{{ resetTarget?.username }}</span>
          设置新密码
        </p>
        <NFormItem
          label="新密码"
          required
          :validation-status="resetError ? 'error' : undefined"
          :feedback="resetError"
        >
          <NInput
            v-model:value="newPassword"
            type="password"
            show-password-on="click"
            placeholder="6-64 位"
            :input-props="{ autocomplete: 'new-password' }"
            @keyup.enter="confirmReset"
          />
        </NFormItem>
      </NForm>

      <template #footer>
        <NSpace justify="end">
          <NButton size="small" @click="closeReset">取消</NButton>
          <NButton
            type="primary"
            size="small"
            :loading="resetSubmitting"
            @click="confirmReset"
          >
            确定
          </NButton>
        </NSpace>
      </template>
    </NModal>
  </div>
</template>
