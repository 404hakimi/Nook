<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  Pencil,
  Plus,
  RefreshCcw,
  Search,
  Server as ServerIcon,
  Terminal,
  Trash2,
  Users,
  Zap
} from 'lucide-vue-next'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import {
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
import ServerOpsDialog from './ServerOpsDialog.vue'
import { pageInbounds } from '@/api/xray/inbound'

const toast = useToast()
const { confirm } = useConfirm()

// ===== 列表 + 查询 =====
const STATUS_OPTIONS = [
  { label: '全部', value: undefined as number | undefined },
  { label: '运行', value: 1 },
  { label: '维护', value: 2 },
  { label: '下线', value: 3 }
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
    // 刷完列表后异步拉每台 server 的活跃用户数
    loadActiveUserCounts()
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
      toast.success(`✔ Xray gRPC 连通 (${res.elapsedMs}ms)`)
    } else {
      toast.error(`✘ ${res.error || '探活失败'}`)
    }
  } catch {
    /* */
  } finally {
    testing.value[s.id] = false
  }
}

// ===== Xray 运维台 =====
const opsOpen = ref(false)
const opsTarget = ref<ResourceServer | null>(null)

function openOps(s: ResourceServer) {
  opsTarget.value = s
  opsOpen.value = true
}

// ===== 当前活跃用户数(每个 server 一个数字) =====
// 进列表后并发拉,失败不影响主流程,仅显示 "?" / "-"
const activeUserCount = ref<Record<string, number>>({})
async function loadActiveUserCounts() {
  if (!list.value.length) return
  const tasks = list.value.map(async (s) => {
    try {
      const res = await pageInbounds({ pageNo: 1, pageSize: 1, serverId: s.id, status: 1 })
      activeUserCount.value[s.id] = res.total
    } catch {
      // 忽略个别失败,UI 显示 -
    }
  })
  await Promise.allSettled(tasks)
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
                <th>区域</th>
                <th>带宽</th>
                <th>月流量</th>
                <th>IP 数</th>
                <th>状态</th>
                <th>SSH</th>
                <th class="whitespace-nowrap">活跃用户</th>
                <th>创建时间</th>
                <th class="text-right">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading">
                <td colspan="11" class="text-center py-12">
                  <span class="loading loading-spinner"></span>
                </td>
              </tr>
              <tr v-else-if="!list.length">
                <td colspan="11" class="text-center py-12">
                  <div class="flex flex-col items-center gap-3 text-base-content/50">
                    <ServerIcon class="w-10 h-10 opacity-30" />
                    <div class="text-sm">还没有服务器</div>
                    <button class="btn btn-primary btn-sm" @click="openCreate">
                      <Plus class="w-4 h-4" />新增第一台服务器
                    </button>
                  </div>
                </td>
              </tr>
              <tr v-for="s in list" :key="s.id">
                <td class="whitespace-nowrap">
                  <div class="flex items-center gap-2">
                    <ServerIcon class="w-4 h-4 text-base-content/50" />
                    <span class="font-medium">{{ s.name }}</span>
                  </div>
                </td>
                <td class="font-mono text-xs">{{ s.host }}<span class="text-base-content/40">:{{ s.sshPort || 22 }}</span></td>
                <td class="text-sm">{{ s.region || '-' }}</td>
                <td class="text-sm whitespace-nowrap">{{ s.totalBandwidth ? s.totalBandwidth + ' Mbps' : '-' }}</td>
                <td class="text-sm whitespace-nowrap">
                  <span v-if="s.monthlyTrafficGb && s.monthlyTrafficGb > 0">
                    {{ s.monthlyTrafficGb >= 1000 ? (s.monthlyTrafficGb / 1000).toFixed(1) + ' TB' : s.monthlyTrafficGb + ' GB' }}
                  </span>
                  <span v-else class="text-base-content/40">不限</span>
                </td>
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
                      class="badge badge-xs"
                      :class="s.xrayGrpcHost && s.xrayGrpcPort ? 'badge-success' : 'badge-ghost'"
                      :title="s.xrayGrpcHost && s.xrayGrpcPort ? 'gRPC 已配置' : 'gRPC 未配置'"
                    >gRPC</span>
                  </div>
                </td>
                <td class="whitespace-nowrap">
                  <div class="flex items-center gap-1">
                    <Users class="w-3.5 h-3.5 text-base-content/40" />
                    <span class="font-mono text-sm">
                      {{ activeUserCount[s.id] !== undefined ? activeUserCount[s.id] : '-' }}
                    </span>
                  </div>
                </td>
                <td class="text-sm text-base-content/70 whitespace-nowrap">{{ formatDateTime(s.createdAt) }}</td>
                <td>
                  <div class="flex justify-end items-center gap-1 flex-wrap">
                    <button
                      class="btn btn-ghost btn-xs gap-1"
                      :disabled="testing[s.id]"
                      title="测试连通性"
                      @click="onTest(s)"
                    >
                      <span v-if="testing[s.id]" class="loading loading-spinner loading-xs"></span>
                      <Zap v-else class="w-3.5 h-3.5 text-warning" />
                      <span class="text-warning">测速</span>
                    </button>
                    <button class="btn btn-ghost btn-xs gap-1" @click="openEdit(s)">
                      <Pencil class="w-3.5 h-3.5 text-info" />
                      <span class="text-info">编辑</span>
                    </button>
                    <button class="btn btn-ghost btn-xs gap-1" @click="openOps(s)">
                      <Terminal class="w-3.5 h-3.5 text-primary" />
                      <span class="text-primary">运维</span>
                    </button>
                    <button class="btn btn-ghost btn-xs gap-1" @click="onDelete(s)">
                      <Trash2 class="w-3.5 h-3.5 text-error" />
                      <span class="text-error">删除</span>
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

    <!-- Xray 运维台 -->
    <ServerOpsDialog v-model="opsOpen" :server="opsTarget" />
  </div>
</template>
