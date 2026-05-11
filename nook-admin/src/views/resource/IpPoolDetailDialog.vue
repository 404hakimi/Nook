<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Copy, ExternalLink } from 'lucide-vue-next'
import {
  NButton,
  NDescriptions,
  NDescriptionsItem,
  NEmpty,
  NIcon,
  NModal,
  NSpin,
  NTag,
  useMessage
} from 'naive-ui'
import {
  IP_POOL_STATUS_LABELS,
  getIpPoolDetail,
  type ResourceIpPool
} from '@/api/resource/ip-pool'
import { IP_TYPE_CODE_LABELS, listIpTypes, type ResourceIpType } from '@/api/resource/ip-type'
import { formatDateTime } from '@/utils/date'

/**
 * IP 池条目只读详情弹框 — 给 ClientList 等"知道 ipId 但只想看一眼信息"的入口用.
 *
 * 不复用 IpPoolFormDialog 是因为：
 * 1. 表单组件有大量 disabled 分支会污染逻辑;
 * 2. 详情场景明确不展示 SOCKS5 密码 (form 走的是带明文密码的编辑路径).
 *
 * 每次打开按 ipId 重新拉一次接口, 不缓存; IP 池总条目有限, 不担心放大.
 */
interface Props {
  modelValue: boolean
  ipId?: string | null
}
const props = defineProps<Props>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
}>()

const message = useMessage()
const router = useRouter()
const loading = ref(false)
const detail = ref<ResourceIpPool | null>(null)
const error = ref<string>('')
const ipTypes = ref<ResourceIpType[]>([])
let ipTypesLoaded = false

async function ensureIpTypes() {
  // 一次会话只拉一次; 失败时降级展示原 typeId, 不阻塞详情显示
  if (ipTypesLoaded) return
  try {
    ipTypes.value = await listIpTypes()
  } catch {
    /* 静默 */
  } finally {
    ipTypesLoaded = true
  }
}

function ipTypeName(typeId?: string): string {
  if (!typeId) return '-'
  const t = ipTypes.value.find((x) => x.id === typeId)
  if (!t) return typeId
  const label = IP_TYPE_CODE_LABELS[t.code] ?? t.code
  return `${t.name} · ${label}`
}

function statusTagType(status?: number): 'success' | 'info' | 'warning' | 'error' | 'default' {
  switch (status) {
    case 1: return 'success'
    case 2: return 'info'
    case 3: return 'warning'
    case 4: return 'error'
    case 5: return 'warning'
    case 6: return 'default'
    default: return 'default'
  }
}

const socks5Endpoint = computed(() => {
  const d = detail.value
  if (!d || !d.ipAddress || !d.socks5Port) return ''
  return `${d.ipAddress}:${d.socks5Port}`
})

async function copyToClipboard(text: string, label: string) {
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    message.success(`${label} 已复制`)
  } catch {
    message.error('复制失败')
  }
}

watch(
  () => [props.modelValue, props.ipId],
  async ([open, id]) => {
    if (!open) return
    detail.value = null
    error.value = ''
    if (!id || typeof id !== 'string') {
      error.value = '缺少 IP ID'
      return
    }
    loading.value = true
    try {
      // 并发: 详情 + ipTypes 字典
      const [d] = await Promise.all([getIpPoolDetail(id), ensureIpTypes()])
      // 防止用户快速切换条目时, 旧请求回来覆盖新数据
      if (props.modelValue && props.ipId === id) {
        detail.value = d
      }
    } catch (e: unknown) {
      // request 拦截器已经 toast, 这里只置错避免空白
      error.value = e instanceof Error ? e.message : '加载失败'
    } finally {
      loading.value = false
    }
  },
  { immediate: false }
)

function close() {
  emit('update:modelValue', false)
}

function openIpPoolPage() {
  // 走 router.resolve 兼容 base / history 部署前缀; 新 tab 打开避免打断当前列表操作
  const href = router.resolve({ name: 'resource-ip-pool' }).href
  window.open(href, '_blank')
}
</script>

<template>
  <NModal
    :show="modelValue"
    preset="card"
    title="IP 详情"
    style="max-width: 40rem"
    :bordered="false"
    @update:show="(v: boolean) => emit('update:modelValue', v)"
  >
    <NSpin :show="loading">
      <NEmpty v-if="!loading && !detail && error" :description="error" />
      <NDescriptions
        v-else-if="detail"
        bordered
        size="small"
        label-placement="left"
        :column="1"
      >
        <NDescriptionsItem label="IP 地址">
          <div class="flex items-center gap-2">
            <span class="font-mono">{{ detail.ipAddress }}</span>
            <NButton
              quaternary
              size="tiny"
              circle
              @click="copyToClipboard(detail.ipAddress, 'IP 地址')"
            >
              <template #icon><NIcon><Copy /></NIcon></template>
            </NButton>
          </div>
        </NDescriptionsItem>
        <NDescriptionsItem label="区域">{{ detail.region || '-' }}</NDescriptionsItem>
        <NDescriptionsItem label="类型">{{ ipTypeName(detail.ipTypeId) }}</NDescriptionsItem>
        <NDescriptionsItem label="状态">
          <NTag size="small" :type="statusTagType(detail.status)">
            {{ IP_POOL_STATUS_LABELS[detail.status] || detail.status }}
          </NTag>
        </NDescriptionsItem>
        <NDescriptionsItem label="SOCKS5">
          <span v-if="!socks5Endpoint" class="text-xs text-zinc-400">未部署</span>
          <div v-else class="flex items-center gap-2">
            <span class="font-mono text-xs">{{ socks5Endpoint }}</span>
            <span v-if="detail.socks5Username" class="font-mono text-xs text-zinc-500">
              / {{ detail.socks5Username }}
            </span>
            <!-- 不展示明文密码; 编辑入口才有, 避免详情这种"路过"场景泄露 -->
            <NTag
              v-if="detail.socks5Password"
              size="small"
              type="success"
            >
              pass 已配置
            </NTag>
          </div>
        </NDescriptionsItem>
        <NDescriptionsItem label="分配次数">{{ detail.assignCount ?? 0 }}</NDescriptionsItem>
        <NDescriptionsItem label="当前会员">
          <span class="font-mono text-xs">{{ detail.assignedMemberId || '-' }}</span>
        </NDescriptionsItem>
        <NDescriptionsItem label="分配时间">{{ formatDateTime(detail.assignedAt) }}</NDescriptionsItem>
        <NDescriptionsItem v-if="detail.coolingUntil" label="冷却到期">
          {{ formatDateTime(detail.coolingUntil) }}
        </NDescriptionsItem>
        <NDescriptionsItem v-if="detail.lastHealthAt" label="最近健检">
          {{ formatDateTime(detail.lastHealthAt) }}
        </NDescriptionsItem>
        <NDescriptionsItem v-if="detail.remark" label="备注">{{ detail.remark }}</NDescriptionsItem>
        <NDescriptionsItem label="创建时间">{{ formatDateTime(detail.createdAt) }}</NDescriptionsItem>
      </NDescriptions>
    </NSpin>

    <template #footer>
      <div class="flex justify-between items-center">
        <NButton
          quaternary
          size="small"
          :disabled="!detail"
          @click="openIpPoolPage"
        >
          <template #icon><NIcon><ExternalLink /></NIcon></template>
          打开 IP 池管理
        </NButton>
        <NButton size="small" @click="close">关闭</NButton>
      </div>
    </template>
  </NModal>
</template>
