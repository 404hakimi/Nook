<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  Globe2,
  Pencil,
  Plus,
  RefreshCcw,
  Rocket,
  Search,
  Trash2,
  Undo2
} from 'lucide-vue-next'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import {
  IP_POOL_STATUS_BADGE_CLASS,
  IP_POOL_STATUS_LABELS,
  deleteIpPool,
  pageIpPool,
  releaseIpPool,
  type ResourceIpPool,
  type ResourceIpPoolQuery
} from '@/api/resource/ip-pool'
import { IP_TYPE_CODE_LABELS, listIpTypes, type ResourceIpType } from '@/api/resource/ip-type'
import { formatDateTime } from '@/utils/date'
import Select from '@/components/Select.vue'
import IpPoolFormDialog from './IpPoolFormDialog.vue'
import IpPoolDeployDialog from './IpPoolDeployDialog.vue'

const toast = useToast()
const { confirm } = useConfirm()

// ===== 列表 + 查询 =====
const STATUS_OPTIONS = [
  { label: '全部', value: undefined as number | undefined },
  { label: '可分配', value: 1 },
  { label: '已占用', value: 2 },
  { label: '测试中', value: 3 },
  { label: '黑名单', value: 4 },
  { label: '冷却中', value: 5 },
  { label: '降级', value: 6 }
]
const PAGE_SIZE_OPTIONS = [
  { label: '10 条/页', value: 10 },
  { label: '20 条/页', value: 20 },
  { label: '50 条/页', value: 50 }
]

const ipTypes = ref<ResourceIpType[]>([])
const ipTypeOptions = computed(() => [
  { label: '全部类型', value: '' },
  ...ipTypes.value.map((t) => ({ label: t.name + (IP_TYPE_CODE_LABELS[t.code] ? ` (${IP_TYPE_CODE_LABELS[t.code]})` : ''), value: t.id }))
])

const query = reactive<Required<Pick<ResourceIpPoolQuery, 'pageNo' | 'pageSize'>> & ResourceIpPoolQuery>({
  pageNo: 1,
  pageSize: 10,
  keyword: '',
  status: undefined,
  region: '',
  ipTypeId: ''
})
const list = ref<ResourceIpPool[]>([])
const total = ref(0)
const loading = ref(false)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / query.pageSize)))

async function loadIpTypes() {
  // 加载失败不静默 — 错误已被 request 拦截器 toast, 这里再 console.error 帮助定位;
  // 拉到空数组也提醒一次, 通常是 99_seed.sql 没跑导致 resource_ip_type 表为空
  try {
    ipTypes.value = await listIpTypes()
    if (!ipTypes.value.length) {
      console.warn('[ip-pool] 未拉到任何 IP 类型. 请确认 sql/99_seed.sql 已执行 (resource_ip_type 表至少 3 条)')
      toast.warning('IP 类型为空, 请运营在数据库中执行 99_seed.sql 初始化')
    }
  } catch (e) {
    console.error('[ip-pool] 加载 IP 类型失败:', e)
  }
}

async function loadList() {
  loading.value = true
  try {
    const res = await pageIpPool({
      pageNo: query.pageNo,
      pageSize: query.pageSize,
      keyword: query.keyword || undefined,
      status: query.status,
      region: query.region || undefined,
      ipTypeId: query.ipTypeId || undefined
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
  query.status = undefined
  query.region = ''
  query.ipTypeId = ''
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

function ipTypeName(typeId: string) {
  const t = ipTypes.value.find((x) => x.id === typeId)
  if (!t) return typeId
  const label = IP_TYPE_CODE_LABELS[t.code] ?? t.code
  return `${t.name} · ${label}`
}

// ===== 新增 / 编辑 =====
const formOpen = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const formIp = ref<ResourceIpPool | null>(null)

function openCreate() {
  formMode.value = 'create'
  formIp.value = null
  formOpen.value = true
}

function openEdit(ip: ResourceIpPool) {
  formMode.value = 'edit'
  formIp.value = ip
  formOpen.value = true
}

function onFormSaved() {
  loadList()
}

// ===== 删除 =====
async function onDelete(ip: ResourceIpPool) {
  const ok = await confirm({
    title: '删除 IP',
    message: `确定从池中删除 ${ip.ipAddress} 吗？该 IP 当前状态为 "${IP_POOL_STATUS_LABELS[ip.status] ?? ip.status}"。`,
    type: 'danger',
    confirmText: '删除'
  })
  if (!ok) return
  try {
    await deleteIpPool(ip.id)
    toast.success('删除成功')
    loadList()
  } catch {
    /* */
  }
}

// ===== 退订 (occupied → cooling) =====
async function onRelease(ip: ResourceIpPool) {
  const ok = await confirm({
    title: '退订 IP',
    message: `把 ${ip.ipAddress} 从已占用置为冷却中？冷却到期后会自动回到可分配。`,
    type: 'warning',
    confirmText: '退订'
  })
  if (!ok) return
  try {
    await releaseIpPool(ip.id)
    toast.success('已置冷却')
    loadList()
  } catch {
    /* */
  }
}

// ===== 一键部署 SOCKS5 =====
const deployOpen = ref(false)
const deployTarget = ref<ResourceIpPool | null>(null)

function openDeploy(ip: ResourceIpPool) {
  deployTarget.value = ip
  deployOpen.value = true
}

function onDeployed() {
  // 部署成功后, 后端已回写 socks5 字段; 重新拉一次列表
  loadList()
}

onMounted(async () => {
  await loadIpTypes()
  await loadList()
})
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
              placeholder="IP 地址"
              class="input input-bordered input-sm w-48 font-mono"
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
          <div>
            <label class="label py-0"><span class="label-text">类型</span></label>
            <Select v-model="query.ipTypeId" :options="ipTypeOptions" width="w-44" />
          </div>
          <button class="btn btn-primary btn-sm" @click="onSearch">
            <Search class="w-4 h-4" />搜索
          </button>
          <button class="btn btn-ghost btn-sm" @click="resetQuery">
            <RefreshCcw class="w-4 h-4" />重置
          </button>
          <div class="flex-1"></div>
          <button class="btn btn-primary btn-sm" @click="openCreate">
            <Plus class="w-4 h-4" />新增 IP
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
                <th>IP 地址</th>
                <th>区域</th>
                <th>类型</th>
                <th>SOCKS5</th>
                <th>状态</th>
                <th>评分</th>
                <th>分配次数</th>
                <th>当前会员</th>
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
                <td colspan="10" class="text-center py-12">
                  <div class="flex flex-col items-center gap-3 text-base-content/50">
                    <Globe2 class="w-10 h-10 opacity-30" />
                    <div class="text-sm">还没有 IP 池条目</div>
                    <button class="btn btn-primary btn-sm" @click="openCreate">
                      <Plus class="w-4 h-4" />新增第一个 IP
                    </button>
                  </div>
                </td>
              </tr>
              <tr v-for="ip in list" :key="ip.id">
                <td class="whitespace-nowrap">
                  <div class="flex items-center gap-2">
                    <Globe2 class="w-4 h-4 text-base-content/50" />
                    <span class="font-mono">{{ ip.ipAddress }}</span>
                  </div>
                </td>
                <td class="text-sm">{{ ip.region }}</td>
                <td class="text-sm">{{ ipTypeName(ip.ipTypeId) }}</td>
                <td class="text-xs font-mono">
                  <div v-if="ip.socks5Host">
                    <span>{{ ip.socks5Host }}<span class="text-base-content/40">:{{ ip.socks5Port }}</span></span>
                    <span v-if="ip.socks5Username" class="ml-1 text-base-content/60">/ {{ ip.socks5Username }}</span>
                    <span
                      v-if="ip.socks5PasswordConfigured"
                      class="ml-1 badge badge-xs badge-success"
                      title="已配置密码"
                    >pass</span>
                    <span v-else class="ml-1 badge badge-xs badge-ghost" title="未配置密码">no-pass</span>
                  </div>
                  <span v-else class="text-base-content/40">未部署</span>
                </td>
                <td>
                  <span :class="['badge badge-sm', IP_POOL_STATUS_BADGE_CLASS[ip.status] || 'badge-ghost']">
                    {{ IP_POOL_STATUS_LABELS[ip.status] || ip.status }}
                  </span>
                </td>
                <td class="text-sm">{{ ip.score ?? '-' }}</td>
                <td class="text-sm">{{ ip.assignCount ?? 0 }}</td>
                <td class="text-xs font-mono text-base-content/70">{{ ip.assignedMemberId || '-' }}</td>
                <td class="text-sm text-base-content/70 whitespace-nowrap">{{ formatDateTime(ip.createdAt) }}</td>
                <td>
                  <div class="flex justify-end items-center gap-1 flex-wrap">
                    <button class="btn btn-ghost btn-xs gap-1" @click="openDeploy(ip)" title="一键部署 SOCKS5">
                      <Rocket class="w-3.5 h-3.5 text-warning" />
                      <span class="text-warning">部署</span>
                    </button>
                    <button class="btn btn-ghost btn-xs gap-1" @click="openEdit(ip)">
                      <Pencil class="w-3.5 h-3.5 text-info" />
                      <span class="text-info">编辑</span>
                    </button>
                    <button
                      v-if="ip.status === 2"
                      class="btn btn-ghost btn-xs gap-1"
                      @click="onRelease(ip)"
                      title="退订, 置为冷却"
                    >
                      <Undo2 class="w-3.5 h-3.5 text-primary" />
                      <span class="text-primary">退订</span>
                    </button>
                    <button class="btn btn-ghost btn-xs gap-1" @click="onDelete(ip)">
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
    <IpPoolFormDialog
      v-model="formOpen"
      :mode="formMode"
      :ip="formIp"
      :ip-types="ipTypes"
      @saved="onFormSaved"
    />

    <!-- 一键部署 SOCKS5 弹框 -->
    <IpPoolDeployDialog v-model="deployOpen" :ip="deployTarget" @deployed="onDeployed" />
  </div>
</template>
