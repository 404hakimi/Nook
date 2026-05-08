<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Activity, MoreVertical, Plus, RefreshCcw, RotateCw, Search, Trash2, Zap } from 'lucide-vue-next'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import {
  INBOUND_STATUS_LABELS,
  pageInbounds,
  resetInboundTraffic,
  revokeInbound,
  rotateInbound,
  type XrayInbound,
  type XrayInboundQuery
} from '@/api/xray/inbound'
import { pageServers, type ResourceServer } from '@/api/resource/server'
import { formatDateTime } from '@/utils/date'
import Select from '@/components/Select.vue'
import InboundProvisionDialog from './InboundProvisionDialog.vue'
import InboundTrafficDialog from './InboundTrafficDialog.vue'

const toast = useToast()
const { confirm } = useConfirm()

const STATUS_OPTIONS = [
  { label: '全部', value: undefined as number | undefined },
  { label: '运行', value: 1 },
  { label: '已停', value: 2 },
  { label: '待同步', value: 3 },
  { label: '远端缺失', value: 4 }
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

const query = reactive<Required<Pick<XrayInboundQuery, 'pageNo' | 'pageSize'>> & XrayInboundQuery>({
  pageNo: 1,
  pageSize: 10,
  keyword: '',
  serverId: '',
  memberUserId: '',
  ipId: '',
  backendType: '',
  status: undefined
})
const list = ref<XrayInbound[]>([])
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
    const res = await pageInbounds({
      pageNo: query.pageNo,
      pageSize: query.pageSize,
      keyword: query.keyword || undefined,
      serverId: query.serverId || undefined,
      memberUserId: query.memberUserId || undefined,
      ipId: query.ipId || undefined,
      backendType: query.backendType || undefined,
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
  query.backendType = ''
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

// ===== 删除 (revoke) =====
async function onRevoke(e: XrayInbound) {
  const ok = await confirm({
    title: '吊销客户端',
    message: `确定吊销 ${e.clientEmail}？\n远端 client 会被删除，DB 软删，会员将立即断开。`,
    type: 'danger',
    confirmText: '吊销'
  })
  if (!ok) return
  try {
    await revokeInbound(e.id)
    toast.success('已吊销')
    loadList()
  } catch { /* */ }
}

// ===== 轮换 UUID (rotate) =====
async function onRotate(e: XrayInbound) {
  const ok = await confirm({
    title: '轮换密钥',
    message: `重新生成 ${e.clientEmail} 的 UUID/密钥；旧客户端配置立即失效需要更新。`,
    type: 'warning',
    confirmText: '轮换'
  })
  if (!ok) return
  try {
    await rotateInbound(e.id)
    toast.success('已轮换')
    loadList()
  } catch { /* */ }
}

// ===== 流量清零 =====
async function onResetTraffic(e: XrayInbound) {
  const ok = await confirm({
    title: '清零流量',
    message: `清零 ${e.clientEmail} 的累计上下行计数？`,
    type: 'warning',
    confirmText: '清零'
  })
  if (!ok) return
  try {
    await resetInboundTraffic(e.id)
    toast.success('已清零')
  } catch { /* */ }
}

// ===== 看流量 =====
const trafficOpen = ref(false)
const trafficTarget = ref<XrayInbound | null>(null)
function openTraffic(e: XrayInbound) {
  trafficTarget.value = e
  trafficOpen.value = true
}

function runAndCloseDropdown(fn: () => void) {
  if (document.activeElement instanceof HTMLElement) document.activeElement.blur()
  fn()
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
            <label class="label py-0"><span class="label-text">服务器 ID</span></label>
            <input v-model="query.serverId" type="text" class="input input-bordered input-sm w-32 font-mono" @keyup.enter="onSearch" />
          </div>
          <div>
            <label class="label py-0"><span class="label-text">会员 ID</span></label>
            <input v-model="query.memberUserId" type="text" class="input input-bordered input-sm w-32 font-mono" @keyup.enter="onSearch" />
          </div>
          <div>
            <label class="label py-0"><span class="label-text">IP ID</span></label>
            <input v-model="query.ipId" type="text" class="input input-bordered input-sm w-32 font-mono" @keyup.enter="onSearch" />
          </div>
          <div>
            <label class="label py-0"><span class="label-text">状态</span></label>
            <Select v-model="query.status" :options="STATUS_OPTIONS" width="w-28" />
          </div>
          <div>
            <label class="label py-0"><span class="label-text">Backend</span></label>
            <Select v-model="query.backendType" :options="BACKEND_OPTIONS" width="w-36" />
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
                <td colspan="10" class="text-center py-12 text-base-content/40">暂无数据</td>
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
                    {{ INBOUND_STATUS_LABELS[e.status] || e.status }}
                  </span>
                </td>
                <td class="text-sm text-base-content/70 whitespace-nowrap">{{ formatDateTime(e.lastSyncedAt) }}</td>
                <td class="text-sm text-base-content/70 whitespace-nowrap">{{ formatDateTime(e.createdAt) }}</td>
                <td>
                  <div class="flex justify-end items-center gap-1">
                    <button class="btn btn-ghost btn-xs" title="查看流量" @click="openTraffic(e)">
                      <Activity class="w-3.5 h-3.5" />
                    </button>
                    <div class="dropdown dropdown-end">
                      <div tabindex="0" role="button" class="btn btn-ghost btn-xs btn-square">
                        <MoreVertical class="w-4 h-4" />
                      </div>
                      <ul tabindex="0" class="dropdown-content menu menu-sm bg-base-100 rounded-box shadow-lg border border-base-200 z-20 w-36 p-1">
                        <li>
                          <a @click="runAndCloseDropdown(() => onRotate(e))">
                            <RotateCw class="w-4 h-4" />轮换 UUID
                          </a>
                        </li>
                        <li>
                          <a @click="runAndCloseDropdown(() => onResetTraffic(e))">
                            <Zap class="w-4 h-4" />清零流量
                          </a>
                        </li>
                        <li>
                          <a class="text-error" @click="runAndCloseDropdown(() => onRevoke(e))">
                            <Trash2 class="w-4 h-4" />吊销
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
              <button class="join-item btn btn-sm" :disabled="query.pageNo === 1" @click="goPage(query.pageNo - 1)">«</button>
              <button class="join-item btn btn-sm pointer-events-none">{{ query.pageNo }} / {{ totalPages }}</button>
              <button class="join-item btn btn-sm" :disabled="query.pageNo >= totalPages" @click="goPage(query.pageNo + 1)">»</button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <InboundProvisionDialog v-model="provisionOpen" @saved="onProvisioned" />
    <InboundTrafficDialog v-model="trafficOpen" :inbound="trafficTarget" />
  </div>
</template>
