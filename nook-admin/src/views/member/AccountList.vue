<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import { Ban, CheckCircle2, Copy, Eye, Pencil, RefreshCcw, Search } from 'lucide-vue-next'
import {
  NButton,
  NCard,
  NDataTable,
  NDescriptions,
  NDescriptionsItem,
  NForm,
  NFormItem,
  NIcon,
  NInput,
  NModal,
  NSelect,
  NSpace,
  NTag,
  useMessage,
  type DataTableColumns
} from 'naive-ui'
import { useConfirm } from '@/composables/useConfirm'
import {
  disableMemberAccount,
  enableMemberAccount,
  getMemberAccountDetail,
  pageMemberAccounts,
  updateMemberAccountRemark,
  type MemberAccount,
  type MemberAccountQuery
} from '@/api/member/user'
import { formatDateTime } from '@/utils/date'

const STATUS_OPTIONS: { label: string; value: number | undefined }[] = [
  { label: '全部', value: undefined },
  { label: '正常', value: 1 },
  { label: '禁用', value: 2 }
]

const message = useMessage()
const { confirm } = useConfirm()

// ===== 列表 + 查询 =====
const query = reactive<Required<Pick<MemberAccountQuery, 'pageNo' | 'pageSize'>> & MemberAccountQuery>({
  pageNo: 1,
  pageSize: 10,
  keyword: '',
  status: undefined
})
const list = ref<MemberAccount[]>([])
const total = ref(0)
const loading = ref(false)

async function loadList() {
  loading.value = true
  try {
    const res = await pageMemberAccounts({
      pageNo: query.pageNo,
      pageSize: query.pageSize,
      keyword: query.keyword || undefined,
      status: query.status
    })
    // 当前页超总页 → 回退一页重拉, 跟 system UserList 一致
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
  loadList()
}

function onSearch() {
  query.pageNo = 1
  loadList()
}

// sub_token 较长, 列表里脱敏展示前 8 字符
function maskedSubToken(token: string): string {
  if (!token) return '-'
  return `${token.slice(0, 8)}…`
}

async function copyToClipboard(text: string, hint: string) {
  try {
    await navigator.clipboard.writeText(text)
    message.success(`${hint} 已复制`)
  } catch {
    message.error('复制失败, 请手动选中')
  }
}

// ===== 表格列定义 =====
const columns = computed<DataTableColumns<MemberAccount>>(() => [
  {
    title: '邮箱',
    key: 'email',
    render: (row) => h('span', { class: 'font-mono text-sm' }, row.email)
  },
  {
    title: 'sub_token',
    key: 'subToken',
    width: 160,
    render: (row) =>
      h(
        'span',
        {
          class: 'font-mono text-xs cursor-pointer hover:underline',
          title: '点击复制完整 sub_token',
          onClick: () => copyToClipboard(row.subToken, 'sub_token')
        },
        maskedSubToken(row.subToken)
      )
  },
  {
    title: '状态',
    key: 'status',
    width: 90,
    render: (row) =>
      h(
        NTag,
        { size: 'small', type: row.status === 1 ? 'success' : 'error' },
        { default: () => (row.status === 1 ? '正常' : '禁用') }
      )
  },
  {
    title: '最后登录',
    key: 'lastLoginAt',
    width: 170,
    render: (row) => formatDateTime(row.lastLoginAt)
  },
  {
    title: '最后登录 IP',
    key: 'lastLoginIp',
    width: 140,
    render: (row) => row.lastLoginIp || '-'
  },
  {
    title: '备注',
    key: 'remark',
    render: (row) => row.remark || '-'
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
    width: 260,
    render: (row) =>
      h('div', { class: 'flex gap-1 justify-end flex-nowrap' }, [
        h(
          NButton,
          { size: 'tiny', quaternary: true, onClick: () => openDetail(row), title: '查看详情' },
          { icon: () => h(NIcon, null, { default: () => h(Eye) }), default: () => '详情' }
        ),
        h(
          NButton,
          { size: 'tiny', quaternary: true, onClick: () => openRemark(row), title: '编辑备注' },
          { icon: () => h(NIcon, null, { default: () => h(Pencil) }), default: () => '备注' }
        ),
        row.status === 1
          ? h(
              NButton,
              {
                size: 'tiny',
                quaternary: true,
                type: 'warning',
                onClick: () => onDisable(row),
                title: '禁用账号 (踢出所有 token)'
              },
              { icon: () => h(NIcon, null, { default: () => h(Ban) }), default: () => '禁用' }
            )
          : h(
              NButton,
              {
                size: 'tiny',
                quaternary: true,
                type: 'success',
                onClick: () => onEnable(row),
                title: '恢复登录'
              },
              { icon: () => h(NIcon, null, { default: () => h(CheckCircle2) }), default: () => '启用' }
            )
      ])
  }
])

const pagination = computed(() => ({
  page: query.pageNo,
  pageSize: query.pageSize,
  itemCount: total.value,
  pageSizes: [10, 20, 50],
  showSizePicker: true,
  prefix: ({ itemCount }: { itemCount?: number }) => `共 ${itemCount ?? 0} 条`,
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

// ===== 详情抽屉 =====
const detailOpen = ref(false)
const detailLoading = ref(false)
const detailMember = ref<MemberAccount | null>(null)

async function openDetail(row: MemberAccount) {
  detailMember.value = row
  detailOpen.value = true
  // 列表数据可能略陈旧, 再拉一次详情
  detailLoading.value = true
  try {
    detailMember.value = await getMemberAccountDetail(row.id)
  } catch {
    /* */
  } finally {
    detailLoading.value = false
  }
}

// ===== 禁用 / 启用 =====
async function onDisable(row: MemberAccount) {
  const ok = await confirm({
    title: '禁用会员',
    message: `禁用会员 "${row.email}"? 会同时踢出该会员的所有在线 token, 直到重新启用前无法登录.`,
    type: 'danger',
    confirmText: '禁用'
  })
  if (!ok) return
  try {
    await disableMemberAccount(row.id)
    message.success('已禁用')
    loadList()
  } catch {
    /* */
  }
}

async function onEnable(row: MemberAccount) {
  try {
    await enableMemberAccount(row.id)
    message.success('已启用')
    loadList()
  } catch {
    /* */
  }
}

// ===== 备注编辑 =====
const remarkOpen = ref(false)
const remarkTarget = ref<MemberAccount | null>(null)
const remarkValue = ref('')
const remarkSubmitting = ref(false)

function openRemark(row: MemberAccount) {
  remarkTarget.value = row
  remarkValue.value = row.remark || ''
  remarkOpen.value = true
}

async function confirmRemark() {
  if (remarkValue.value.length > 255) {
    message.error('备注长度不能超过 255')
    return
  }
  remarkSubmitting.value = true
  try {
    await updateMemberAccountRemark(remarkTarget.value!.id, remarkValue.value)
    message.success('已保存')
    remarkOpen.value = false
    loadList()
  } catch {
    /* */
  } finally {
    remarkSubmitting.value = false
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
          <div class="text-xs text-zinc-500 mb-1">邮箱关键词</div>
          <NInput
            v-model:value="query.keyword"
            size="small"
            placeholder="支持模糊匹配"
            class="w-64"
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
        <NButton type="primary" size="small" @click="onSearch">
          <template #icon><NIcon><Search /></NIcon></template>
          搜索
        </NButton>
        <NButton quaternary size="small" @click="resetQuery">
          <template #icon><NIcon><RefreshCcw /></NIcon></template>
          重置
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
        :row-key="(row: MemberAccount) => row.id"
        size="small"
      />
    </NCard>

    <!-- 详情抽屉 (用 NModal preset=card, 跟项目其他详情风格一致) -->
    <NModal
      :show="detailOpen"
      preset="card"
      title="会员详情"
      style="max-width: 40rem"
      :bordered="false"
      @update:show="(v: boolean) => (detailOpen = v)"
    >
      <NDescriptions
        v-if="detailMember"
        :column="1"
        size="small"
        bordered
        :label-style="{ width: '110px' }"
      >
        <NDescriptionsItem label="ID">
          <span class="font-mono text-xs">{{ detailMember.id }}</span>
        </NDescriptionsItem>
        <NDescriptionsItem label="邮箱">
          <span class="font-mono">{{ detailMember.email }}</span>
        </NDescriptionsItem>
        <NDescriptionsItem label="sub_token">
          <div class="flex items-center gap-2">
            <span class="font-mono text-xs break-all">{{ detailMember.subToken }}</span>
            <NButton
              size="tiny"
              quaternary
              :title="'复制 sub_token'"
              @click="copyToClipboard(detailMember.subToken, 'sub_token')"
            >
              <template #icon><NIcon><Copy /></NIcon></template>
            </NButton>
          </div>
        </NDescriptionsItem>
        <NDescriptionsItem label="状态">
          <NTag size="small" :type="detailMember.status === 1 ? 'success' : 'error'">
            {{ detailMember.status === 1 ? '正常' : '禁用' }}
          </NTag>
        </NDescriptionsItem>
        <NDescriptionsItem label="最后登录">
          {{ formatDateTime(detailMember.lastLoginAt) }}
        </NDescriptionsItem>
        <NDescriptionsItem label="最后登录 IP">
          {{ detailMember.lastLoginIp || '-' }}
        </NDescriptionsItem>
        <NDescriptionsItem label="备注">
          {{ detailMember.remark || '-' }}
        </NDescriptionsItem>
        <NDescriptionsItem label="创建时间">
          {{ formatDateTime(detailMember.createdAt) }}
        </NDescriptionsItem>
        <NDescriptionsItem label="更新时间">
          {{ formatDateTime(detailMember.updatedAt) }}
        </NDescriptionsItem>
      </NDescriptions>
    </NModal>

    <!-- 编辑备注 -->
    <NModal
      :show="remarkOpen"
      preset="card"
      title="编辑备注"
      style="max-width: 30rem"
      :bordered="false"
      :mask-closable="false"
      @update:show="(v: boolean) => (remarkOpen = v)"
    >
      <NForm size="small" label-placement="top">
        <p class="text-sm mb-3">
          为会员
          <span class="font-mono font-semibold">{{ remarkTarget?.email }}</span>
          设置备注 (仅 admin 可见)
        </p>
        <NFormItem label="备注">
          <NInput
            v-model:value="remarkValue"
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 5 }"
            placeholder="最多 255 字"
            maxlength="255"
            show-count
          />
        </NFormItem>
      </NForm>
      <template #footer>
        <NSpace justify="end">
          <NButton size="small" @click="remarkOpen = false">取消</NButton>
          <NButton
            type="primary"
            size="small"
            :loading="remarkSubmitting"
            @click="confirmRemark"
          >
            保存
          </NButton>
        </NSpace>
      </template>
    </NModal>
  </div>
</template>
