<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Activity, Pencil, Plus, RefreshCcw, RotateCw, Search, Share2, Trash2, Zap } from 'lucide-vue-next'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import {
  CLIENT_STATUS_LABELS,
  pageClients,
  resetClientTraffic,
  revokeClient,
  rotateClient,
  type XrayClient,
  type XrayClientQuery
} from '@/api/xray/client'
import { pageServers, type ResourceServer } from '@/api/resource/server'
import { formatDateTime } from '@/utils/date'
import Select from '@/components/Select.vue'
import ClientEditDialog from './ClientEditDialog.vue'
import ClientProvisionDialog from './ClientProvisionDialog.vue'
import ClientShareDialog from './ClientShareDialog.vue'
import ClientTrafficDialog from './ClientTrafficDialog.vue'

const toast = useToast()
const { confirm } = useConfirm()

const STATUS_OPTIONS = [
  { label: '全部', value: undefined as number | undefined },
  { label: '运行', value: 1 },
  { label: '已停', value: 2 },
  { label: '待同步', value: 3 },
  { label: '远端缺失', value: 4 }
]
const PAGE_SIZE_OPTIONS = [
  { label: '10 条/页', value: 10 },
  { label: '20 条/页', value: 20 },
  { label: '50 条/页', value: 50 }
]

const query = reactive<Required<Pick<XrayClientQuery, 'pageNo' | 'pageSize'>> & XrayClientQuery>({
  pageNo: 1,
  pageSize: 10,
  keyword: '',
  serverId: '',
  memberUserId: '',
  ipId: '',
  status: undefined
})
const list = ref<XrayClient[]>([])
const total = ref(0)
const loading = ref(false)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / query.pageSize)))

// serverId → 服务器名/host 缓存；进入页面时拉一次，便于把 inbound 行里的裸 UUID 翻译成可读
const serverMap = ref<Record<string, ResourceServer>>({})
async function loadServerMap() {
  try {
    // 服务器总数有限，一把拉 200 条够用
    const res = await pageServers({ pageNo: 1, pageSize: 200 })
    serverMap.value = Object.fromEntries(res.records.map((s) => [s.id, s]))
  } catch {
    /* 拉不到不影响主流程，列表会回落到显示原始 ID */
  }
}
function serverLabel(id: string): string {
  const s = serverMap.value[id]
  return s ? s.name : id.slice(0, 8) + '…'
}
function serverHost(id: string): string {
  return serverMap.value[id]?.host ?? ''
}

async function loadList() {
  loading.value = true
  try {
    const res = await pageClients({
      pageNo: query.pageNo,
      pageSize: query.pageSize,
      keyword: query.keyword || undefined,
      serverId: query.serverId || undefined,
      memberUserId: query.memberUserId || undefined,
      ipId: query.ipId || undefined,
      status: query.status
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
    /* */
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.pageNo = 1
  query.keyword = ''
  query.serverId = ''
  query.memberUserId = ''
  query.ipId = ''
  query.status = undefined
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
  return s === 1 ? 'badge-success' : s === 2 ? 'badge-warning' : s === 4 ? 'badge-error' : 'badge-info'
}

// ===== Provision =====
const provisionOpen = ref(false)
async function onProvisioned() {
  await loadList()
}

// 行级操作 in-flight 标记；防止用户狂点同一行触发多次请求
const busy = ref<Record<string, boolean>>({})

// ===== 删除 (revoke) =====
async function onRevoke(e: XrayClient) {
  if (busy.value[e.id]) return
  const ok = await confirm({
    title: '吊销客户端',
    message: `确定吊销 ${e.clientEmail}？\n远端 client 会被删除，DB 软删，会员将立即断开。`,
    type: 'danger',
    confirmText: '吊销'
  })
  if (!ok) return
  busy.value[e.id] = true
  try {
    await revokeClient(e.id)
    toast.success('已吊销')
    loadList()
  } catch { /* */ } finally {
    busy.value[e.id] = false
  }
}

// ===== 轮换 UUID (rotate) =====
async function onRotate(e: XrayClient) {
  if (busy.value[e.id]) return
  const ok = await confirm({
    title: '轮换密钥',
    message: `重新生成 ${e.clientEmail} 的 UUID/密钥；旧客户端配置立即失效需要更新。`,
    type: 'warning',
    confirmText: '轮换'
  })
  if (!ok) return
  busy.value[e.id] = true
  try {
    await rotateClient(e.id)
    toast.success('已轮换')
    loadList()
  } catch { /* */ } finally {
    busy.value[e.id] = false
  }
}

// ===== 流量清零 =====
async function onResetTraffic(e: XrayClient) {
  if (busy.value[e.id]) return
  const ok = await confirm({
    title: '清零流量',
    message: `清零 ${e.clientEmail} 的累计上下行计数？`,
    type: 'warning',
    confirmText: '清零'
  })
  if (!ok) return
  busy.value[e.id] = true
  try {
    await resetClientTraffic(e.id)
    toast.success('已清零')
  } catch { /* */ } finally {
    busy.value[e.id] = false
  }
}

// ===== 看流量 =====
const trafficOpen = ref(false)
const trafficTarget = ref<XrayClient | null>(null)
function openTraffic(e: XrayClient) {
  trafficTarget.value = e
  trafficOpen.value = true
}

// ===== 分享 (生成订阅链接给会员客户端导入) =====
const shareOpen = ref(false)
const shareTarget = ref<XrayClient | null>(null)
function openShare(e: XrayClient) {
  shareTarget.value = e
  shareOpen.value = true
}

// ===== 编辑（本地元数据：listenIp/Port/transport/status） =====
const editOpen = ref(false)
const editTarget = ref<XrayClient | null>(null)
function openEdit(e: XrayClient) {
  editTarget.value = e
  editOpen.value = true
}
async function onEdited() {
  await loadList()
}

onMounted(() => {
  // 并发拉服务器映射 + inbound 列表，二者无依赖
  loadServerMap()
  loadList()
})
</script>

<template>
  <div class="space-y-4">
    <!-- 顶部搜索 -->
    <div class="card bg-base-100 shadow-sm">
      <div class="card-body py-4">
        <!-- 主筛选行：日常 90% 场景够用 -->
        <div class="flex flex-wrap gap-3 items-end">
          <div>
            <label class="label py-0"><span class="label-text">关键词</span></label>
            <input
              v-model="query.keyword"
              type="text"
              placeholder="client email"
              class="input input-bordered input-sm w-56"
              @keyup.enter="onSearch"
            />
          </div>
          <div>
            <label class="label py-0"><span class="label-text">状态</span></label>
            <Select v-model="query.status" :options="STATUS_OPTIONS" width="w-28" />
          </div>
          <button class="btn btn-primary btn-sm" @click="onSearch">
            <Search class="w-4 h-4" />搜索
          </button>
          <button class="btn btn-ghost btn-sm" @click="resetQuery">
            <RefreshCcw class="w-4 h-4" />重置
          </button>
          <div class="flex-1"></div>
          <button class="btn btn-primary btn-sm" @click="provisionOpen = true">
            <Plus class="w-4 h-4" />手动 Provision
          </button>
        </div>

        <!-- 高级筛选：默认折叠；按 ID 精确定位时才展开 -->
        <details class="collapse collapse-arrow border border-base-200 mt-3">
          <summary class="collapse-title text-sm py-2 min-h-0">
            高级筛选
            <span v-if="query.serverId || query.memberUserId || query.ipId" class="badge badge-primary badge-sm ml-2">
              已设
            </span>
          </summary>
          <div class="collapse-content">
            <div class="flex flex-wrap gap-3 items-end pt-2">
              <div>
                <label class="label py-0"><span class="label-text">服务器 ID</span></label>
                <input v-model="query.serverId" type="text" class="input input-bordered input-sm w-72 font-mono" @keyup.enter="onSearch" />
              </div>
              <div>
                <label class="label py-0"><span class="label-text">会员 ID</span></label>
                <input v-model="query.memberUserId" type="text" class="input input-bordered input-sm w-72 font-mono" @keyup.enter="onSearch" />
              </div>
              <div>
                <label class="label py-0"><span class="label-text">IP ID</span></label>
                <input v-model="query.ipId" type="text" class="input input-bordered input-sm w-72 font-mono" @keyup.enter="onSearch" />
              </div>
            </div>
          </div>
        </details>
      </div>
    </div>

    <!-- 表格 -->
    <div class="card bg-base-100 shadow-sm">
      <div class="card-body p-0">
        <div>
          <table class="table table-zebra">
            <thead>
              <tr>
                <th>Client Email</th>
                <th>协议</th>
                <th>服务器</th>
                <th>Inbound 引用</th>
                <th>会员 ID</th>
                <th>IP ID</th>
                <th>状态</th>
                <th>最近同步</th>
                <th>创建时间</th>
                <th class="text-right">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading">
                <td colspan="10" class="text-center py-12"><span class="loading loading-spinner"></span></td>
              </tr>
              <tr v-else-if="!list.length">
                <td colspan="10" class="text-center py-12">
                  <div class="flex flex-col items-center gap-3 text-base-content/50">
                    <Activity class="w-10 h-10 opacity-30" />
                    <div class="text-sm">还没有客户端配置</div>
                    <button class="btn btn-primary btn-sm" @click="provisionOpen = true">
                      <Plus class="w-4 h-4" />手动 Provision 第一条
                    </button>
                  </div>
                </td>
              </tr>
              <tr v-for="e in list" :key="e.id">
                <td class="font-mono text-xs">{{ e.clientEmail }}</td>
                <td>
                  <span class="badge badge-outline badge-sm">{{ e.protocol }}</span>
                </td>
                <td>
                  <div class="flex flex-col">
                    <span class="text-sm" :title="e.serverId">{{ serverLabel(e.serverId) }}</span>
                    <span v-if="serverHost(e.serverId)" class="font-mono text-xs text-base-content/50">{{ serverHost(e.serverId) }}</span>
                  </div>
                </td>
                <td class="font-mono text-xs">{{ e.externalInboundRef }}</td>
                <td class="font-mono text-xs">{{ e.memberUserId }}</td>
                <td class="font-mono text-xs">{{ e.ipId }}</td>
                <td>
                  <span :class="['badge badge-sm', statusBadge(e.status)]">
                    {{ CLIENT_STATUS_LABELS[e.status] || e.status }}
                  </span>
                </td>
                <td class="text-sm text-base-content/70 whitespace-nowrap">{{ formatDateTime(e.lastSyncedAt) }}</td>
                <td class="text-sm text-base-content/70 whitespace-nowrap">{{ formatDateTime(e.createdAt) }}</td>
                <td>
                  <div class="flex justify-end items-center gap-1 flex-wrap">
                    <button class="btn btn-ghost btn-xs gap-1" @click="openShare(e)" title="生成订阅链接给会员">
                      <Share2 class="w-3.5 h-3.5 text-primary" />
                      <span class="text-primary">分享</span>
                    </button>
                    <button class="btn btn-ghost btn-xs gap-1" @click="openTraffic(e)">
                      <Activity class="w-3.5 h-3.5 text-success" />
                      <span class="text-success">流量</span>
                    </button>
                    <button class="btn btn-ghost btn-xs gap-1" @click="openEdit(e)">
                      <Pencil class="w-3.5 h-3.5 text-info" />
                      <span class="text-info">编辑</span>
                    </button>
                    <button
                      class="btn btn-ghost btn-xs gap-1"
                      :disabled="busy[e.id]"
                      @click="onRotate(e)"
                    >
                      <span v-if="busy[e.id]" class="loading loading-spinner loading-xs"></span>
                      <RotateCw v-else class="w-3.5 h-3.5 text-warning" />
                      <span class="text-warning">轮换</span>
                    </button>
                    <button
                      class="btn btn-ghost btn-xs gap-1"
                      :disabled="busy[e.id]"
                      @click="onResetTraffic(e)"
                    >
                      <Zap class="w-3.5 h-3.5 text-accent" />
                      <span class="text-accent">清零</span>
                    </button>
                    <button
                      class="btn btn-ghost btn-xs gap-1"
                      :disabled="busy[e.id]"
                      @click="onRevoke(e)"
                    >
                      <Trash2 class="w-3.5 h-3.5 text-error" />
                      <span class="text-error">吊销</span>
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
              <button class="join-item btn btn-sm" :disabled="query.pageNo === 1" @click="goPage(query.pageNo - 1)">«</button>
              <button class="join-item btn btn-sm pointer-events-none">{{ query.pageNo }} / {{ totalPages }}</button>
              <button class="join-item btn btn-sm" :disabled="query.pageNo >= totalPages" @click="goPage(query.pageNo + 1)">»</button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <ClientProvisionDialog v-model="provisionOpen" @saved="onProvisioned" />
    <ClientTrafficDialog v-model="trafficOpen" :inbound="trafficTarget" />
    <ClientEditDialog
      v-model="editOpen"
      :inbound="editTarget"
      :server-map="serverMap"
      @saved="onEdited"
    />
    <!-- ShareDialog 自己拉 reveal 接口拿明文凭据, 不依赖父组件传 serverMap -->
    <ClientShareDialog v-model="shareOpen" :client="shareTarget" />
  </div>
</template>
