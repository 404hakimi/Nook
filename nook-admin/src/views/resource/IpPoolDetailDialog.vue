<script setup lang="ts">
import { computed, h, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  Activity,
  Copy,
  ExternalLink,
  Eye,
  FileText,
  KeyRound,
  Pencil,
  Rocket,
  Server as ServerIcon,
  Trash2,
  Wrench,
  Zap
} from 'lucide-vue-next'
import {
  NButton,
  NDescriptions,
  NDescriptionsItem,
  NDropdown,
  NEmpty,
  NIcon,
  NModal,
  NSpin,
  NTabs,
  NTabPane,
  NTag,
  useMessage
} from 'naive-ui'
import {
  IP_POOL_LIFECYCLE_LABELS,
  IP_POOL_LIFECYCLE_TAG_TYPE,
  IP_POOL_STATUS_LABELS,
  getIpPoolDetail,
  type ResourceIpPool
} from '@/api/resource/ip-pool'
import { IP_TYPE_CODE_LABELS, listIpTypes, type ResourceIpType } from '@/api/resource/ip-type'
import { formatDateTime } from '@/utils/date'

/**
 * IP 池条目详情 — 4 tab 只读视图 + 顶部 Actions 工具栏 (所有 admin 操作集中入口).
 *
 * Tabs: 概览 / dante 服务 / 装机信息 / SSH 凭据
 * Toolbar: 装机·重装 | 编辑 ▾ | 运维 ▾ | 退役/启用 | 删除
 * 操作通过 emit 上抛父组件 (IpPoolList), 由父组件打开对应 sub-dialog 避免 modal-in-modal 嵌套.
 */
interface Props {
  modelValue: boolean
  ipId?: string | null
}
const props = defineProps<Props>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'edit-core', ip: ResourceIpPool): void
  (e: 'edit-credential', ip: ResourceIpPool): void
  (e: 'edit-billing', ip: ResourceIpPool): void
  (e: 'edit-socks5', ip: ResourceIpPool): void
  (e: 'deploy', ip: ResourceIpPool): void
  (e: 'test', ip: ResourceIpPool): void
  (e: 'view-status', ip: ResourceIpPool): void
  (e: 'view-log', ip: ResourceIpPool): void
  (e: 'provision-agent', ip: ResourceIpPool): void
  (e: 'lifecycle-retire', ip: ResourceIpPool): void
  (e: 'lifecycle-restore', ip: ResourceIpPool): void
  (e: 'delete', ip: ResourceIpPool): void
  /** detail 内任意操作触发后, 父组件刷新 detail (e.g. 编辑保存了字段, 应该重拉) */
  (e: 'refresh'): void
}>()

const message = useMessage()
const router = useRouter()
const loading = ref(false)
const detail = ref<ResourceIpPool | null>(null)
const error = ref<string>('')
const ipTypes = ref<ResourceIpType[]>([])
const activeTab = ref<'overview' | 'dante' | 'install' | 'ssh'>('overview')
let ipTypesLoaded = false

async function ensureIpTypes() {
  if (ipTypesLoaded) return
  try {
    ipTypes.value = await listIpTypes()
    ipTypesLoaded = true
  } catch { /* */ }
}

function ipTypeName(ipTypeId?: string): string {
  if (!ipTypeId) return '-'
  const t = ipTypes.value.find((x) => x.id === ipTypeId)
  if (!t) return ipTypeId
  return IP_TYPE_CODE_LABELS[t.code] || t.name || t.code
}

function statusTagType(status?: string) {
  switch (status) {
    case 'OCCUPIED': return 'warning'
    case 'AVAILABLE': return 'success'
    case 'COOLING': return 'info'
    case 'RESERVED': return 'default'
    default: return 'default'
  }
}

const socks5Endpoint = computed(() => {
  if (!detail.value?.ipAddress || !detail.value.socks5Port) return ''
  return `${detail.value.ipAddress}:${detail.value.socks5Port}`
})

/** SOCKS5 凭据齐才能 test / sync */
const canTest = computed(() =>
  !!detail.value?.ipAddress && !!detail.value?.socks5Port && !!detail.value?.socks5Username && !!detail.value?.socks5Password
)
/** 自部署 + SSH 密码齐才能拉运维 (dante 状态/日志) */
const canManage = computed(() => detail.value?.provisionMode === 1 && !!detail.value?.sshPassword)
const isSelfDeploy = computed(() => detail.value?.provisionMode === 1)
const isLive = computed(() => detail.value?.lifecycleState === 'LIVE')
const isInstalling = computed(() => detail.value?.lifecycleState === 'INSTALLING' || detail.value?.lifecycleState === 'READY')
const isRetired = computed(() => detail.value?.lifecycleState === 'RETIRED')

async function copyToClipboard(value: string | undefined, label: string) {
  if (!value) {
    message.warning(`${label} 为空`)
    return
  }
  try {
    await navigator.clipboard.writeText(value)
    message.success(`已复制 ${label}`)
  } catch {
    message.warning('复制失败')
  }
}

async function loadDetail(id: string) {
  loading.value = true
  error.value = ''
  detail.value = null
  try {
    detail.value = await getIpPoolDetail(id)
  } catch (e) {
    error.value = (e as Error).message || '加载失败'
  } finally {
    loading.value = false
  }
}

/** 父组件刷新事件时, 重拉 detail (e.g. 装机完成/编辑保存) */
async function refresh() {
  if (props.ipId) {
    await loadDetail(props.ipId)
    emit('refresh')
  }
}
defineExpose({ refresh })

watch(
  () => [props.modelValue, props.ipId],
  ([open, ipId]) => {
    if (!open) return
    activeTab.value = 'overview'
    void ensureIpTypes()
    if (typeof ipId === 'string' && ipId.length > 0) {
      void loadDetail(ipId)
    } else {
      detail.value = null
      error.value = 'ipId 缺失'
    }
  },
  { immediate: false }
)

function close() {
  emit('update:modelValue', false)
}

function openIpPoolPage() {
  const href = router.resolve({ name: 'resource-ip-pool' }).href
  window.open(href, '_blank')
}

// ===== Actions ===== (emit 上抛, 父组件统一开 sub-dialog)
const editDropdownOptions = [
  { label: '核心信息 (区域 / 类型 / IP / 部署模式)', key: 'core', icon: () => h(NIcon, null, { default: () => h(Pencil) }) },
  { label: 'SSH 凭据', key: 'credential', icon: () => h(NIcon, null, { default: () => h(KeyRound) }) },
  { label: '账面 (带宽 / 成本 / 到期)', key: 'billing' },
  { label: 'dante 配置 + 限速', key: 'socks5' }
]

function onEditSelect(key: string) {
  if (!detail.value) return
  const ip = detail.value
  switch (key) {
    case 'core': emit('edit-core', ip); break
    case 'credential': emit('edit-credential', ip); break
    case 'billing': emit('edit-billing', ip); break
    case 'socks5': emit('edit-socks5', ip); break
  }
}

const opsDropdownOptions = computed(() => [
  canManage.value
    ? { label: '查看 dante 状态', key: 'status', icon: () => h(NIcon, null, { default: () => h(Eye) }) }
    : null,
  canManage.value
    ? { label: '查看日志', key: 'log', icon: () => h(NIcon, null, { default: () => h(FileText) }) }
    : null,
  isSelfDeploy.value
    ? { label: '装 landing agent', key: 'provision', icon: () => h(NIcon, null, { default: () => h(ServerIcon) }) }
    : null
// eslint-disable-next-line @typescript-eslint/no-explicit-any
].filter(Boolean) as any[])

function onOpsSelect(key: string) {
  if (!detail.value) return
  const ip = detail.value
  switch (key) {
    case 'status': emit('view-status', ip); break
    case 'log': emit('view-log', ip); break
    case 'provision': emit('provision-agent', ip); break
  }
}
</script>

<template>
  <NModal
    :show="modelValue"
    preset="card"
    title="IP 详情"
    style="max-width: 56rem; width: 92vw"
    :bordered="false"
    @update:show="(v: boolean) => emit('update:modelValue', v)"
  >
    <NSpin :show="loading">
      <NEmpty v-if="!loading && !detail && error" :description="error" />

      <div v-else-if="detail">
        <!-- ===== Actions 工具栏: 所有 admin 操作集中入口 ===== -->
        <div class="actions-bar">
          <!-- 装机·重装: 自部署且 INSTALLING/READY/LIVE 时露 -->
          <NButton
            v-if="isSelfDeploy && isInstalling"
            size="small"
            type="primary"
            @click="emit('deploy', detail)"
          >
            <template #icon><NIcon><Rocket /></NIcon></template>
            装机
          </NButton>
          <NButton
            v-else-if="isSelfDeploy && isLive"
            size="small"
            quaternary
            type="info"
            @click="emit('deploy', detail)"
          >
            <template #icon><NIcon><Rocket /></NIcon></template>
            重装
          </NButton>

          <!-- 测试 -->
          <NButton
            v-if="canTest"
            size="small"
            quaternary
            type="warning"
            @click="emit('test', detail)"
          >
            <template #icon><NIcon><Zap /></NIcon></template>
            测试
          </NButton>

          <!-- 编辑 dropdown -->
          <NDropdown trigger="click" :options="editDropdownOptions" @select="onEditSelect">
            <NButton size="small" quaternary>
              <template #icon><NIcon><Pencil /></NIcon></template>
              编辑 ▾
            </NButton>
          </NDropdown>

          <!-- 运维 dropdown (canManage / canTest 决定可用项) -->
          <NDropdown
            v-if="opsDropdownOptions.length > 0"
            trigger="click"
            :options="opsDropdownOptions"
            @select="onOpsSelect"
          >
            <NButton size="small" quaternary>
              <template #icon><NIcon><Wrench /></NIcon></template>
              运维 ▾
            </NButton>
          </NDropdown>

          <div class="flex-1" />

          <!-- lifecycle 流转: 仅匹配 from 态时露 -->
          <NButton
            v-if="isLive"
            size="small"
            quaternary
            type="warning"
            @click="emit('lifecycle-retire', detail)"
          >
            <template #icon><NIcon><Activity /></NIcon></template>
            退役
          </NButton>
          <NButton
            v-else-if="isRetired"
            size="small"
            quaternary
            type="success"
            @click="emit('lifecycle-restore', detail)"
          >
            <template #icon><NIcon><Activity /></NIcon></template>
            重新启用
          </NButton>

          <!-- 删除 (danger) -->
          <NButton
            size="small"
            quaternary
            type="error"
            @click="emit('delete', detail)"
          >
            <template #icon><NIcon><Trash2 /></NIcon></template>
            删除
          </NButton>
        </div>

        <NTabs v-model:value="activeTab" type="line" size="small" animated>
          <!-- ===== 概览 ===== -->
          <NTabPane name="overview" tab="概览">
            <NDescriptions bordered size="small" label-placement="left" :column="1">
              <NDescriptionsItem label="IP 地址">
                <div class="flex items-center gap-2">
                  <span class="font-mono">{{ detail.ipAddress }}</span>
                  <NButton quaternary size="tiny" circle @click="copyToClipboard(detail.ipAddress, 'IP 地址')">
                    <template #icon><NIcon><Copy /></NIcon></template>
                  </NButton>
                </div>
              </NDescriptionsItem>
              <NDescriptionsItem label="区域">{{ detail.region || '-' }}</NDescriptionsItem>
              <NDescriptionsItem label="类型">{{ ipTypeName(detail.ipTypeId) }}</NDescriptionsItem>
              <NDescriptionsItem label="lifecycle">
                <NTag size="small" :type="IP_POOL_LIFECYCLE_TAG_TYPE[detail.lifecycleState] || 'default'">
                  {{ IP_POOL_LIFECYCLE_LABELS[detail.lifecycleState] || detail.lifecycleState || '-' }}
                </NTag>
              </NDescriptionsItem>
              <NDescriptionsItem label="占用状态">
                <NTag size="small" :type="statusTagType(detail.status)">
                  {{ IP_POOL_STATUS_LABELS[detail.status] || detail.status }}
                </NTag>
              </NDescriptionsItem>
              <NDescriptionsItem label="当前会员">
                <span class="font-mono text-xs">{{ detail.occupiedByMemberId || '-' }}</span>
              </NDescriptionsItem>
              <NDescriptionsItem label="占用时间">{{ formatDateTime(detail.occupiedAt) }}</NDescriptionsItem>
              <NDescriptionsItem v-if="detail.coolingUntil" label="冷却到期">
                {{ formatDateTime(detail.coolingUntil) }}
              </NDescriptionsItem>
              <NDescriptionsItem v-if="detail.lastHealthAt" label="最近健检">
                {{ formatDateTime(detail.lastHealthAt) }}
              </NDescriptionsItem>
              <NDescriptionsItem v-if="detail.remark" label="备注">{{ detail.remark }}</NDescriptionsItem>
              <NDescriptionsItem label="创建时间">{{ formatDateTime(detail.createdAt) }}</NDescriptionsItem>
            </NDescriptions>
          </NTabPane>

          <!-- ===== dante 服务 ===== -->
          <NTabPane name="dante" tab="dante 服务">
            <div v-if="!socks5Endpoint" class="text-zinc-500 text-sm py-6 text-center">
              该 IP 尚未部署 SOCKS5
            </div>
            <NDescriptions v-else bordered size="small" label-placement="left" :column="1">
              <NDescriptionsItem label="SOCKS5 端点">
                <div class="flex items-center gap-2">
                  <span class="font-mono">{{ socks5Endpoint }}</span>
                  <NButton quaternary size="tiny" circle @click="copyToClipboard(socks5Endpoint, 'SOCKS5 端点')">
                    <template #icon><NIcon><Copy /></NIcon></template>
                  </NButton>
                </div>
              </NDescriptionsItem>
              <NDescriptionsItem label="用户名">
                <span class="font-mono text-xs">{{ detail.socks5Username || '-' }}</span>
              </NDescriptionsItem>
              <NDescriptionsItem label="密码">
                <NTag v-if="detail.socks5Password" size="small" type="success">已配置 (走"编辑 → dante" 改)</NTag>
                <span v-else class="text-zinc-400 text-xs">-</span>
              </NDescriptionsItem>
              <NDescriptionsItem label="日志级别">
                <span class="font-mono text-xs">{{ detail.logLevel || '-' }}</span>
              </NDescriptionsItem>
              <NDescriptionsItem label="限速 (Mbps)">
                <NTag size="small" :type="detail.bandwidthLimitMbps ? 'warning' : 'default'">
                  {{ detail.bandwidthLimitMbps ? `${detail.bandwidthLimitMbps} Mbps` : '不限速' }}
                </NTag>
              </NDescriptionsItem>
            </NDescriptions>
          </NTabPane>

          <!-- ===== 装机信息 ===== -->
          <NTabPane name="install" tab="装机信息">
            <div v-if="!detail.installDir && !detail.logPath" class="text-zinc-500 text-sm py-6 text-center">
              未装机 / 未落 install 子表
            </div>
            <NDescriptions v-else bordered size="small" label-placement="left" :column="1">
              <NDescriptionsItem label="安装目录">
                <span class="font-mono text-xs">{{ detail.installDir || '-' }}</span>
              </NDescriptionsItem>
              <NDescriptionsItem label="日志路径">
                <span class="font-mono text-xs">{{ detail.logPath || '-' }}</span>
              </NDescriptionsItem>
              <NDescriptionsItem label="systemd 自启">
                <NTag size="small" :type="detail.autostartEnabled ? 'success' : 'default'">
                  {{ detail.autostartEnabled ? '已启用' : '未启用' }}
                </NTag>
              </NDescriptionsItem>
              <NDescriptionsItem label="UFW 防火墙">
                <NTag size="small" :type="detail.firewallEnabled ? 'success' : 'default'">
                  {{ detail.firewallEnabled ? '已配置' : '未配置' }}
                </NTag>
              </NDescriptionsItem>
              <NDescriptionsItem label="详细装机产物">
                <span class="text-xs text-zinc-500">
                  conf_path / pam_file / pwd_file / systemd_unit / dante_version
                  可通过 <code class="font-mono">/admin/resource/ip-pool/{id}/install</code> 接口取
                </span>
              </NDescriptionsItem>
            </NDescriptions>
          </NTabPane>

          <!-- ===== SSH 凭据 ===== -->
          <NTabPane name="ssh" tab="SSH 凭据">
            <div v-if="!detail.sshHost" class="text-zinc-500 text-sm py-6 text-center">
              未配置 SSH 凭据 (第三方 SOCKS5 无 SSH 凭据)
            </div>
            <NDescriptions v-else bordered size="small" label-placement="left" :column="1">
              <NDescriptionsItem label="SSH host">
                <span class="font-mono text-xs">{{ detail.sshHost || '-' }}</span>
              </NDescriptionsItem>
              <NDescriptionsItem label="SSH port">{{ detail.sshPort ?? '-' }}</NDescriptionsItem>
              <NDescriptionsItem label="SSH user">
                <span class="font-mono text-xs">{{ detail.sshUser || '-' }}</span>
              </NDescriptionsItem>
              <NDescriptionsItem label="SSH password">
                <NTag v-if="detail.sshPassword" size="small" type="success">已配置 (mask)</NTag>
                <span v-else class="text-zinc-400 text-xs">-</span>
              </NDescriptionsItem>
              <NDescriptionsItem label="备注">
                <span class="text-xs text-zinc-500">
                  改密码走顶部 "编辑 → SSH 凭据"
                </span>
              </NDescriptionsItem>
            </NDescriptions>
          </NTabPane>
        </NTabs>
      </div>
    </NSpin>

    <template #footer>
      <div class="flex justify-between items-center">
        <NButton quaternary size="small" :disabled="!detail" @click="openIpPoolPage">
          <template #icon><NIcon><ExternalLink /></NIcon></template>
          打开 IP 池管理
        </NButton>
        <NButton size="small" @click="close">关闭</NButton>
      </div>
    </template>
  </NModal>
</template>

<style scoped>
.actions-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 0 12px 0;
  margin-bottom: 8px;
  border-bottom: 1px solid var(--n-divider-color, #efeff5);
  flex-wrap: wrap;
}
</style>
