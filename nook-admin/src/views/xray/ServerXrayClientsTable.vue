<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Activity, RefreshCcw, RotateCw, Share2, Zap } from 'lucide-vue-next'
import {
  NButton,
  NDataTable,
  NIcon,
  NTag,
  useMessage,
  type DataTableColumns
} from 'naive-ui'
import { useConfirm } from '@/composables/useConfirm'
import {
  CLIENT_STATUS_LABELS,
  pageClients,
  resetClientTraffic,
  rotateClient,
  type XrayClient
} from '@/api/xray/client'
import { formatDateTime } from '@/utils/date'
import ClientShareDialog from './ClientShareDialog.vue'
import ClientTrafficDialog from './ClientTrafficDialog.vue'

interface Props {
  serverId: string
}
const props = defineProps<Props>()

const router = useRouter()
const message = useMessage()
const { confirm } = useConfirm()

const list = ref<XrayClient[]>([])
const total = ref(0)
const loading = ref(false)
const busy = ref<Record<string, boolean>>({})

const PAGE_SIZE = 20
const pageNo = ref(1)

async function loadList() {
  loading.value = true
  try {
    const res = await pageClients({
      pageNo: pageNo.value,
      pageSize: PAGE_SIZE,
      serverId: props.serverId
    })
    list.value = res.records
    total.value = res.total
  } catch {
    /* request 拦截器 toast */
  } finally {
    loading.value = false
  }
}

function statusType(s: number): 'success' | 'warning' | 'error' | 'default' {
  if (s === 1) return 'success'
  if (s === 2) return 'default'
  if (s === 3) return 'warning'
  if (s === 4) return 'error'
  return 'default'
}

// ===== 行操作: 分享 / 流量 / 轮换 / 清流量 =====
async function onRotate(c: XrayClient) {
  if (busy.value[c.id]) return
  const ok = await confirm({
    title: '轮换密钥',
    message: `轮换 ${c.clientEmail} 的 UUID?`,
    type: 'warning',
    confirmText: '轮换'
  })
  if (!ok) return
  busy.value[c.id] = true
  try {
    await rotateClient(c.id)
    message.success('已轮换')
    loadList()
  } catch { /* */ } finally {
    busy.value[c.id] = false
  }
}

async function onResetTraffic(c: XrayClient) {
  if (busy.value[c.id]) return
  const ok = await confirm({
    title: '清零流量',
    message: `清零 ${c.clientEmail} 的流量计数?`,
    type: 'warning',
    confirmText: '清零'
  })
  if (!ok) return
  busy.value[c.id] = true
  try {
    await resetClientTraffic(c.id)
    message.success('已清零')
  } catch { /* */ } finally {
    busy.value[c.id] = false
  }
}

const shareOpen = ref(false)
const shareTarget = ref<XrayClient | null>(null)
function openShare(c: XrayClient) {
  shareTarget.value = c
  shareOpen.value = true
}

const trafficOpen = ref(false)
const trafficTarget = ref<XrayClient | null>(null)
function openTraffic(c: XrayClient) {
  trafficTarget.value = c
  trafficOpen.value = true
}

function openIpDetail(ipId: string) {
  if (!ipId) return
  router.push({ name: 'resource-server-landing-detail', params: { id: ipId } })
}

const columns = computed<DataTableColumns<XrayClient>>(() => [
  {
    title: 'Client Email',
    key: 'clientEmail',
    render: (row) => h('span', { class: 'font-mono text-xs' }, row.clientEmail)
  },
  {
    title: '会员 ID',
    key: 'memberUserId',
    width: 140,
    render: (row) => h('span', { class: 'font-mono text-xs' }, row.memberUserId)
  },
  {
    title: '落地 IP',
    key: 'ipAddress',
    width: 180,
    render: (row) => {
      const display = row.ipAddress || (row.ipId ? row.ipId.slice(0, 8) + '…' : '-')
      return h(
        NButton,
        {
          text: true,
          type: 'primary',
          size: 'small',
          disabled: !row.ipId,
          title: row.ipAddress ? row.ipId : '点击查看 IP 详情',
          onClick: () => openIpDetail(row.ipId)
        },
        { default: () => h('span', { class: 'font-mono text-xs' }, display) }
      )
    }
  },
  {
    title: '状态',
    key: 'status',
    width: 90,
    render: (row) =>
      h(
        NTag,
        { size: 'small', type: statusType(row.status) },
        { default: () => CLIENT_STATUS_LABELS[row.status] || row.status }
      )
  },
  {
    title: '最近同步',
    key: 'lastSyncedAt',
    width: 160,
    render: (row) =>
      h('span', { class: 'text-xs text-zinc-600 dark:text-zinc-400 whitespace-nowrap' },
        formatDateTime(row.lastSyncedAt))
  },
  {
    title: '创建时间',
    key: 'createdAt',
    width: 160,
    render: (row) =>
      h('span', { class: 'text-xs text-zinc-600 dark:text-zinc-400 whitespace-nowrap' },
        formatDateTime(row.createdAt))
  },
  {
    title: '操作',
    key: 'actions',
    align: 'right',
    width: 300,
    render: (row) => {
      const rowBusy = busy.value[row.id]
      return h('div', { class: 'flex gap-1 justify-end flex-nowrap' }, [
        h(NButton, {
          size: 'tiny', quaternary: true, disabled: rowBusy,
          onClick: () => openShare(row), title: '生成订阅 / 分享链接'
        }, { icon: () => h(NIcon, null, { default: () => h(Share2) }), default: () => '分享' }),
        h(NButton, {
          size: 'tiny', quaternary: true, disabled: rowBusy,
          onClick: () => openTraffic(row), title: '查看上下行流量统计'
        }, { icon: () => h(NIcon, null, { default: () => h(Activity) }), default: () => '流量' }),
        h(NButton, {
          size: 'tiny', quaternary: true, disabled: rowBusy,
          onClick: () => onRotate(row), title: '轮换 UUID / 密钥 (老凭据立即失效)'
        }, { icon: () => h(NIcon, null, { default: () => h(RotateCw) }), default: () => '轮换' }),
        h(NButton, {
          size: 'tiny', quaternary: true, disabled: rowBusy,
          onClick: () => onResetTraffic(row), title: '清零累计流量'
        }, { icon: () => h(NIcon, null, { default: () => h(Zap) }), default: () => '清流量' })
      ])
    }
  }
])

const pagination = computed(() => ({
  page: pageNo.value,
  pageSize: PAGE_SIZE,
  itemCount: total.value,
  prefix: ({ itemCount }: { itemCount?: number }) => `共 ${itemCount ?? 0} 条`,
  onUpdatePage: (p: number) => {
    pageNo.value = p
    loadList()
  }
}))

onMounted(loadList)
</script>

<template>
  <div>
    <div class="flex items-center gap-2 mb-2">
      <span class="text-xs font-semibold text-zinc-600 dark:text-zinc-300">Xray 客户端</span>
      <span class="text-xs text-zinc-400">共 {{ total }} 个</span>
      <div class="flex-1"></div>
      <NButton size="tiny" quaternary :loading="loading" @click="loadList">
        <template #icon><NIcon><RefreshCcw /></NIcon></template>
        刷新
      </NButton>
    </div>
    <NDataTable
      :columns="columns"
      :data="list"
      :loading="loading"
      :pagination="total > PAGE_SIZE ? pagination : false"
      :remote="true"
      :bordered="true"
      :row-key="(row: XrayClient) => row.id"
      size="small"
    />

    <ClientShareDialog v-model="shareOpen" :client="shareTarget" />
    <ClientTrafficDialog v-model="trafficOpen" :inbound="trafficTarget" />
  </div>
</template>
