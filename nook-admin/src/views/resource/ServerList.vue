<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  MoreVertical,
  Pencil,
  Plus,
  RefreshCcw,
  Search,
  Server as ServerIcon,
  TerminalSquare,
  Trash2,
  Zap
} from 'lucide-vue-next'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import {
  BACKEND_TYPE_LABELS,
  SERVER_STATUS_LABELS,
  deleteServer,
  pageServers,
  type ResourceServer,
  type ResourceServerQuery
} from '@/api/resource/server'
import { testServerConnectivity } from '@/api/xray/server'
import { formatDateTime } from '@/utils/date'
import Select from '@/components/Select.vue'
import ServerFormDialog from './ServerFormDialog.vue'
import ServerSshDialog from './ServerSshDialog.vue'

const toast = useToast()
const { confirm } = useConfirm()

// ===== 列表 + 查询 =====
const STATUS_OPTIONS = [
  { label: '全部', value: undefined as number | undefined },
  { label: '运行', value: 1 },
  { label: '维护', value: 2 },
  { label: '下线', value: 3 }
]
const BACKEND_OPTIONS = [
  { label: '全部', value: '' },
  { label: '3x-ui 面板', value: 'threexui' },
  { label: 'Xray gRPC', value: 'xray-grpc' }
]
const PAGE_SIZE_OPTIONS = [
  { label: '10 条/页', value: 10 },
  { label: '20 条/页', value: 20 },
  { label: '50 条/页', value: 50 }
]

const query = reactive<Required<Pick<ResourceServerQuery, 'pageNo' | 'pageSize'>> & ResourceServerQuery>({
  pageNo: 1,
  pageSize: 10,
  keyword: '',
  status: undefined,
  backendType: '',
  region: ''
})
const list = ref<ResourceServer[]>([])
const total = ref(0)
const loading = ref(false)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / query.pageSize)))

async function loadList() {
  loading.value = true
  try {
    const res = await pageServers({
      pageNo: query.pageNo,
      pageSize: query.pageSize,
      keyword: query.keyword || undefined,
      status: query.status,
      backendType: query.backendType || undefined,
      region: query.region || undefined
    })
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
  query.backendType = ''
  query.region = ''
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

function backendLabel(t: string) {
  return BACKEND_TYPE_LABELS[t] || t
}

function statusBadge(s: number) {
  return s === 1 ? 'badge-success' : s === 2 ? 'badge-warning' : 'badge-error'
}

// ===== 新增 / 编辑 =====
const formOpen = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const formServer = ref<ResourceServer | null>(null)

function openCreate() {
  formMode.value = 'create'
  formServer.value = null
  formOpen.value = true
}

function openEdit(s: ResourceServer) {
  formMode.value = 'edit'
  formServer.value = s
  formOpen.value = true
}

async function onFormSaved() {
  await loadList()
}

// ===== 删除 =====
async function onDelete(s: ResourceServer) {
  const ok = await confirm({
    title: '删除服务器',
    message: `确定删除服务器 "${s.name}" 吗？已关联的 IP / inbound 不会自动清理，请先妥善处理。`,
    type: 'danger',
    confirmText: '删除'
  })
  if (!ok) return
  try {
    await deleteServer(s.id)
    toast.success('删除成功')
    loadList()
  } catch {
    /* */
  }
}

// ===== 测试连通性 =====
const testing = ref<Record<string, boolean>>({})

async function onTest(s: ResourceServer) {
  testing.value[s.id] = true
  try {
    const res = await testServerConnectivity(s.id)
    if (res.success) {
      toast.success(`✔ ${backendLabel(res.backendType || s.backendType)} 连通 (${res.elapsedMs}ms)`)
    } else {
      toast.error(`✘ ${res.error || '探活失败'}`)
    }
  } catch {
    /* */
  } finally {
    testing.value[s.id] = false
  }
}

// ===== SSH 控制台 =====
const sshOpen = ref(false)
const sshTarget = ref<ResourceServer | null>(null)

function openSsh(s: ResourceServer) {
  sshTarget.value = s
  sshOpen.value = true
}

// ===== Dropdown 收起 =====
function runAndCloseDropdown(fn: () => void) {
  if (document.activeElement instanceof HTMLElement) document.activeElement.blur()
  fn()
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
              placeholder="别名 / 主机"
              class="input input-bordered input-sm w-56"
              @keyup.enter="onSearch"
            />
          </div>
          <div>
            <label class="label py-0"><span class="label-text">状态</span></label>
            <Select v-model="query.status" :options="STATUS_OPTIONS" width="w-28" />
          </div>
          <div>
            <label class="label py-0"><span class="label-text">Backend</span></label>
            <Select v-model="query.backendType" :options="BACKEND_OPTIONS" width="w-36" />
          </div>
          <div>
            <label class="label py-0"><span class="label-text">区域</span></label>
            <input
              v-model="query.region"
              type="text"
              placeholder="us-west / jp / ..."
              class="input input-bordered input-sm w-32"
              @keyup.enter="onSearch"
            />
          </div>
          <button class="btn btn-primary btn-sm" @click="onSearch">
            <Search class="w-4 h-4" />搜索
          </button>
          <button class="btn btn-ghost btn-sm" @click="resetQuery">
            <RefreshCcw class="w-4 h-4" />重置
          </button>
          <div class="flex-1"></div>
          <button class="btn btn-primary btn-sm" @click="openCreate">
            <Plus class="w-4 h-4" />新增服务器
          </button>
        </div>
      </div>
    </div>

    <!-- 表格 -->
    <div class="card bg-base-100 shadow-sm">
      <div class="card-body p-0">
        <div>
          <table class="table table-zebra">
            <thead>
              <tr>
                <th>别名</th>
                <th>主机</th>
                <th>Backend</th>
                <th>区域</th>
                <th>带宽</th>
                <th>IP 数</th>
                <th>状态</th>
                <th>凭据</th>
                <th>创建时间</th>
                <th class="text-right">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading">
                <td colspan="10" class="text-center py-12">
                  <span class="loading loading-spinner"></span>
                </td>
              </tr>
              <tr v-else-if="!list.length">
                <td colspan="10" class="text-center py-12 text-base-content/40">暂无数据</td>
              </tr>
              <tr v-for="s in list" :key="s.id">
                <td class="whitespace-nowrap">
                  <div class="flex items-center gap-2">
                    <ServerIcon class="w-4 h-4 text-base-content/50" />
                    <span class="font-medium">{{ s.name }}</span>
                  </div>
                </td>
                <td class="font-mono text-xs">{{ s.host }}<span class="text-base-content/40">:{{ s.sshPort || 22 }}</span></td>
                <td>
                  <span class="badge badge-outline badge-sm">{{ backendLabel(s.backendType) }}</span>
                </td>
                <td class="text-sm">{{ s.region || '-' }}</td>
                <td class="text-sm">{{ s.totalBandwidth ? s.totalBandwidth + ' Mbps' : '-' }}</td>
                <td class="text-sm">{{ s.totalIpCount ?? 0 }}</td>
                <td>
                  <span :class="['badge badge-sm', statusBadge(s.status)]">
                    {{ SERVER_STATUS_LABELS[s.status] || s.status }}
                  </span>
                </td>
                <td>
                  <div class="flex gap-1">
                    <span
                      class="badge badge-xs"
                      :class="s.sshAuthConfigured ? 'badge-success' : 'badge-ghost'"
                      :title="s.sshAuthConfigured ? 'SSH 已配置' : 'SSH 未配置'"
                    >SSH</span>
                    <span
                      v-if="s.backendType === 'threexui'"
                      class="badge badge-xs"
                      :class="s.panelPasswordConfigured ? 'badge-success' : 'badge-ghost'"
                      :title="s.panelPasswordConfigured ? '面板凭据已配置' : '面板凭据未配置'"
                    >面板</span>
                    <span
                      v-if="s.backendType === 'xray-grpc'"
                      class="badge badge-xs"
                      :class="s.xrayGrpcHost && s.xrayGrpcPort ? 'badge-success' : 'badge-ghost'"
                    >gRPC</span>
                  </div>
                </td>
                <td class="text-sm text-base-content/70 whitespace-nowrap">{{ formatDateTime(s.createdAt) }}</td>
                <td>
                  <div class="flex justify-end items-center gap-1">
                    <button
                      class="btn btn-ghost btn-xs"
                      :disabled="testing[s.id]"
                      title="测试连通性"
                      @click="onTest(s)"
                    >
                      <span v-if="testing[s.id]" class="loading loading-spinner loading-xs"></span>
                      <Zap v-else class="w-3.5 h-3.5" />
                    </button>
                    <div class="dropdown dropdown-end">
                      <div tabindex="0" role="button" class="btn btn-ghost btn-xs btn-square" aria-label="更多操作">
                        <MoreVertical class="w-4 h-4" />
                      </div>
                      <ul
                        tabindex="0"
                        class="dropdown-content menu menu-sm bg-base-100 rounded-box shadow-lg border border-base-200 z-20 w-36 p-1"
                      >
                        <li>
                          <a @click="runAndCloseDropdown(() => openEdit(s))">
                            <Pencil class="w-4 h-4" />编辑
                          </a>
                        </li>
                        <li>
                          <a @click="runAndCloseDropdown(() => openSsh(s))">
                            <TerminalSquare class="w-4 h-4" />SSH 控制台
                          </a>
                        </li>
                        <li>
                          <a class="text-error" @click="runAndCloseDropdown(() => onDelete(s))">
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
    <ServerFormDialog
      v-model="formOpen"
      :mode="formMode"
      :server="formServer"
      @saved="onFormSaved"
    />

    <!-- SSH 控制台 -->
    <ServerSshDialog v-model="sshOpen" :server="sshTarget" />
  </div>
</template>
