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
  NTabs,
  NTabPane,
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
 * IP 池条目只读详情 — tab 化展示, 运营 + 技术双视角:
 *   - 概览:    region / ipType / lifecycle / 占用状态 / 会员 / 备注
 *   - dante 服务: socks5 端口 / 用户 / 日志级别 / 限速 (业务配置)
 *   - 装机信息: install_dir / log_path / conf_path / systemd unit / dante_version / installed_at (装机产物, 运维迁移用)
 *   - SSH 凭据: host / port / user (密码 mask; 编辑走 IpPoolCredentialEditDialog)
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
const activeTab = ref<'overview' | 'dante' | 'install' | 'ssh'>('overview')
let ipTypesLoaded = false

async function ensureIpTypes() {
  if (ipTypesLoaded) return
  try {
    ipTypes.value = await listIpTypes()
    ipTypesLoaded = true
  } catch {
    /* 类型查询失败不影响详情查询 */
  }
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

async function copyToClipboard(value: string | undefined, label: string) {
  if (!value) {
    message.warning(`${label} 为空, 无法复制`)
    return
  }
  try {
    await navigator.clipboard.writeText(value)
    message.success(`${label} 已复制`)
  } catch {
    message.warning('复制失败 (可能浏览器不允许), 手动选取吧')
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
    style="max-width: 52rem"
    :bordered="false"
    @update:show="(v: boolean) => emit('update:modelValue', v)"
  >
    <NSpin :show="loading">
      <NEmpty v-if="!loading && !detail && error" :description="error" />
      <NTabs
        v-else-if="detail"
        v-model:value="activeTab"
        type="line"
        size="small"
        animated
      >
        <!-- ===== 概览: 资源归属 + lifecycle + 占用 ===== -->
        <NTabPane name="overview" tab="概览">
          <NDescriptions bordered size="small" label-placement="left" :column="1">
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
            <NDescriptionsItem label="lifecycle">
              <NTag size="small">{{ detail.lifecycleState || '-' }}</NTag>
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

        <!-- ===== dante 服务: socks5 业务配置 (落 resource_ip_pool_socks5) ===== -->
        <NTabPane name="dante" tab="dante 服务">
          <div v-if="!socks5Endpoint" class="text-zinc-500 text-sm py-6 text-center">
            该 IP 尚未部署 SOCKS5 (装机后这里展示业务配置)
          </div>
          <NDescriptions v-else bordered size="small" label-placement="left" :column="1">
            <NDescriptionsItem label="SOCKS5 端点">
              <div class="flex items-center gap-2">
                <span class="font-mono">{{ socks5Endpoint }}</span>
                <NButton
                  quaternary
                  size="tiny"
                  circle
                  @click="copyToClipboard(socks5Endpoint, 'SOCKS5 端点')"
                >
                  <template #icon><NIcon><Copy /></NIcon></template>
                </NButton>
              </div>
            </NDescriptionsItem>
            <NDescriptionsItem label="用户名">
              <span class="font-mono text-xs">{{ detail.socks5Username || '-' }}</span>
            </NDescriptionsItem>
            <NDescriptionsItem label="密码">
              <NTag v-if="detail.socks5Password" size="small" type="success">已配置 (编辑入口可改)</NTag>
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

        <!-- ===== 装机信息: install 子表; 给运维迁移 / 重建服务用 ===== -->
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

        <!-- ===== SSH 凭据: credential 子表; 密码 mask 安全展示 ===== -->
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
                明文凭据请走 IP 池列表行的「编辑 SSH」入口操作
              </span>
            </NDescriptionsItem>
          </NDescriptions>
        </NTabPane>
      </NTabs>
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
