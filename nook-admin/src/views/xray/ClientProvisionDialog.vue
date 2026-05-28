<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { CheckCircle2 } from 'lucide-vue-next'
import {
  NButton,
  NForm,
  NFormItem,
  NIcon,
  NInputNumber,
  NModal,
  NSelect,
  NSpace,
  NSpin,
  useMessage
} from 'naive-ui'
import { pageServers, type ResourceServer } from '@/api/resource/server'
import { pageClients, provisionClient, type XrayClientProvisionDTO } from '@/api/xray/client'
import {
  SERVER_LANDING_STATUS_LABELS,
  pageServerLanding,
  type ServerLanding
} from '@/api/resource/server-landing'
import { pageMemberAccounts, type MemberAccount } from '@/api/member/user'
import { IP_TYPE_CODE_LABELS } from '@/api/system/ip-type'
import { useIpTypeStore } from '@/stores/ipType'
import { storeToRefs } from 'pinia'

interface Props {
  modelValue: boolean
  /** 上层 (server 详情页内) 锁死的 serverId; 传了就不再显示服务器下拉, 也不拉 pageServers. */
  serverId?: string
}
const props = defineProps<Props>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'saved'): void
}>()

const message = useMessage()
const submitting = ref(false)
const errors = reactive<Record<string, string>>({})

const servers = ref<ResourceServer[]>([])
const loadingServers = ref(false)

const landings = ref<ServerLanding[]>([])
const ipTypeStore = useIpTypeStore()
const { list: ipTypes } = storeToRefs(ipTypeStore)
const loadingLandings = ref(false)

// 会员下拉: 远程分页 + 邮箱模糊搜索 + 滚动到底加载下一页
const MEMBER_PAGE_SIZE = 10
const members = ref<MemberAccount[]>([])
const loadingMembers = ref(false)
const memberKeyword = ref('')
const memberPageNo = ref(1)
const memberTotal = ref(0)
/** 防抖句柄, 输入快速变化时只发最后一次. */
let memberSearchTimer: number | null = null

/** memberUserId 唯一性预校验状态; 'idle' 表示未校验, 'ok' 通过, 'dup' 已重复, 'checking' 正在请求. */
const memberCheck = ref<'idle' | 'checking' | 'ok' | 'dup'>('idle')

/** 共享 inbound 模型下, 协议 / 传输 / listen IP 由 server 上的 xray 配置决定, 不在这里采集. */
const form = reactive({
  serverId: '',
  ipId: '',
  memberUserId: '',
  totalBytes: undefined as number | undefined,
  expiryEpochMillis: undefined as number | undefined,
  limitIp: undefined as number | undefined
})

const serverOptions = computed(() =>
  servers.value.map((s) => ({
    label: s.name,
    value: s.id
  }))
)

/** 仅展示 status=AVAILABLE 的落地机, 避免误把已占用的二次派发. */
const landingOptions = computed(() =>
  landings.value.map((ip) => {
    const t = ipTypes.value.find((x) => x.id === ip.ipTypeId)
    const typeLabel = t ? IP_TYPE_CODE_LABELS[t.code] ?? t.code : ip.ipTypeId
    const statusLabel = SERVER_LANDING_STATUS_LABELS[ip.status] ?? ip.status
    return {
      label: `${ip.ipAddress} · ${ip.region} · ${typeLabel} · ${statusLabel}`,
      value: ip.id
    }
  })
)

/** 启用状态会员下拉; 禁用账户不参与分配. */
const memberOptions = computed(() =>
  members.value.map((m) => ({
    label: m.email,
    value: m.id
  }))
)

watch(
  () => props.modelValue,
  async (open) => {
    if (!open) return
    Object.keys(errors).forEach((k) => delete errors[k])
    memberCheck.value = 'idle'
    Object.assign(form, {
      serverId: props.serverId ?? '',
      ipId: '',
      memberUserId: '',
      totalBytes: undefined,
      expiryEpochMillis: undefined,
      limitIp: undefined
    })
    // 会员: 重置搜索 + 拉首页
    memberKeyword.value = ''
    memberPageNo.value = 1
    members.value = []
    memberTotal.value = 0
    // 在 server 详情页打开时 serverId 已固定, 不再拉 server 列表
    const tasks: Promise<unknown>[] = [loadLandings(), loadMembers(true), ipTypeStore.ensureLoaded()]
    if (!props.serverId) tasks.push(loadServers())
    await Promise.all(tasks)
  }
)

/**
 * 远程分页加载会员.
 * @param reset true = 替换列表 (重新搜索 / 初次打开); false = 追加到尾部 (滚动加载下一页)
 */
async function loadMembers(reset: boolean) {
  if (loadingMembers.value) return
  loadingMembers.value = true
  try {
    const res = await pageMemberAccounts({
      pageNo: memberPageNo.value,
      pageSize: MEMBER_PAGE_SIZE,
      status: 1,
      keyword: memberKeyword.value.trim() || undefined
    })
    members.value = reset ? res.records : [...members.value, ...res.records]
    memberTotal.value = res.total
  } catch {
    /* */
  } finally {
    loadingMembers.value = false
  }
}

/** 用户在下拉里输入邮箱关键字; 300ms debounce, 重置到 page1. */
function onMemberSearch(value: string) {
  memberKeyword.value = value
  if (memberSearchTimer != null) {
    window.clearTimeout(memberSearchTimer)
  }
  memberSearchTimer = window.setTimeout(() => {
    memberPageNo.value = 1
    void loadMembers(true)
  }, 300)
}

/** 下拉列表滚动到底部时拉下一页 (未到 total 才拉). */
function onMemberScroll(e: Event) {
  if (loadingMembers.value) return
  if (members.value.length >= memberTotal.value) return
  const el = e.target as HTMLElement
  if (el.scrollHeight - el.scrollTop - el.clientHeight < 20) {
    memberPageNo.value += 1
    void loadMembers(false)
  }
}

async function loadLandings() {
  loadingLandings.value = true
  try {
    // 仅拉 AVAILABLE 的落地机, 防止误派已占用的; 后端单页上限 100
    const res = await pageServerLanding({ pageNo: 1, pageSize: 100, status: 'AVAILABLE' })
    landings.value = res.records
  } catch {
    /* */
  } finally {
    loadingLandings.value = false
  }
}

async function loadServers() {
  loadingServers.value = true
  try {
    // 拉前 100 条够选; Nook 个位数集群规模
    const res = await pageServers({ pageNo: 1, pageSize: 100 })
    servers.value = res.records
  } catch {
    /* */
  } finally {
    loadingServers.value = false
  }
}

/**
 * 会员 ID 唯一性预校验:
 * - 同一 memberUserId 在 xray_client 表里只能存在一条 (后端会用 unique 约束兜底).
 * - 这里前端做一次 pageClients(memberUserId=...) 查询给运营即时反馈;
 *   blur 触发 + 提交前最终复核.
 * - 注意 pageClients 默认过滤软删, 已 revoke 的旧客户端不会被算进重复.
 */
async function checkMemberUnique(): Promise<boolean> {
  const id = form.memberUserId
  if (!id) {
    memberCheck.value = 'idle'
    return false
  }
  memberCheck.value = 'checking'
  try {
    const res = await pageClients({ memberUserId: id, pageNo: 1, pageSize: 1 })
    if (res.total > 0) {
      errors.memberUserId = '该会员已有客户端, 请先吊销旧的再 Provision'
      memberCheck.value = 'dup'
      return false
    }
    delete errors.memberUserId
    memberCheck.value = 'ok'
    return true
  } catch {
    // 拉不到不阻塞; 后端 unique 约束兜底
    memberCheck.value = 'idle'
    return true
  }
}

/** 切换会员后清旧的校验状态, 并对新选项即时跑唯一性检查. */
function onMemberChange() {
  if (memberCheck.value === 'dup') delete errors.memberUserId
  memberCheck.value = 'idle'
  if (form.memberUserId) {
    void checkMemberUnique()
  }
}

function validate(): boolean {
  Object.keys(errors).forEach((k) => delete errors[k])
  if (!form.serverId) errors.serverId = '请选服务器'
  if (!form.memberUserId) errors.memberUserId = '请选会员'
  if (!form.ipId.trim()) errors.ipId = '请选 IP'
  return Object.keys(errors).length === 0
}

async function onSubmit() {
  if (!validate()) return
  // 提交前再确认一次 memberUserId 唯一性 (防止用户绕过 blur 直接点确定)
  if (!(await checkMemberUnique())) return
  submitting.value = true
  try {
    // inbound / 协议 / 传输 / listen IP 都由 server 端 xray 配置决定, 前端只传 server + IP + 会员 + 配额
    const dto: XrayClientProvisionDTO = {
      serverId: form.serverId,
      ipId: form.ipId.trim(),
      memberUserId: form.memberUserId,
      totalBytes: form.totalBytes,
      expiryEpochMillis: form.expiryEpochMillis,
      limitIp: form.limitIp
    }
    await provisionClient(dto)
    message.success('开通成功')
    emit('saved')
    emit('update:modelValue', false)
  } catch {
    /* */
  } finally {
    submitting.value = false
  }
}

function close() {
  emit('update:modelValue', false)
}
</script>

<template>
  <NModal
    :show="modelValue"
    preset="card"
    title="手动 Provision 客户端"
    style="max-width: 56rem; width: 92vw"
    :bordered="false"
    :mask-closable="false"
    :close-on-esc="false"
    @update:show="(v: boolean) => emit('update:modelValue', v)"
  >
    <NForm
      :model="form"
      label-placement="top"
      require-mark-placement="right-hanging"
      size="small"
    >
      <div class="grid grid-cols-1 sm:grid-cols-2 gap-x-4">
        <!-- 服务器字段: 从全局 ClientList 入口才显示; 从某台 server 详情页入口已锁定不再选 -->
        <div v-if="!serverId" class="sm:col-span-2">
          <NFormItem
            required
            :validation-status="errors.serverId ? 'error' : undefined"
            :feedback="errors.serverId"
          >
            <template #label>
              <span>服务器</span>
              <span v-if="loadingServers" class="text-xs text-zinc-400 ml-2">
                <NSpin :size="12" /> 加载中
              </span>
            </template>
            <NSelect
              v-model:value="form.serverId"
              :options="serverOptions"
              :status="errors.serverId ? 'error' : undefined"
              placeholder="选服务器"
            />
          </NFormItem>
        </div>

        <div class="sm:col-span-2">
          <NFormItem
            required
            :validation-status="errors.memberUserId ? 'error' : undefined"
            :feedback="errors.memberUserId"
          >
            <template #label>
              <span>会员</span>
              <span v-if="loadingMembers" class="text-xs text-zinc-400 ml-2">
                <NSpin :size="12" /> 加载中
              </span>
              <span v-else-if="memberCheck === 'checking'" class="text-xs text-zinc-400 ml-2">
                <NSpin :size="12" /> 校验唯一性
              </span>
              <span
                v-else-if="memberCheck === 'ok'"
                class="text-xs ml-2 inline-flex items-center gap-1"
                style="color: var(--n-success-color)"
              >
                <NIcon :size="12"><CheckCircle2 /></NIcon>
                可用
              </span>
              <span v-else class="text-xs text-zinc-400 ml-2">
                已加载 {{ members.length }} / 共 {{ memberTotal }} (仅启用)
              </span>
            </template>
            <NSelect
              v-model:value="form.memberUserId"
              :options="memberOptions"
              :status="errors.memberUserId ? 'error' : undefined"
              :loading="loadingMembers"
              filterable
              remote
              clear-filter-after-select
              placeholder="按邮箱搜索 (滚动加载更多; 一名会员仅能持有一个客户端)"
              @search="onMemberSearch"
              @scroll="onMemberScroll"
              @update:value="onMemberChange"
            />
          </NFormItem>
        </div>

        <div class="sm:col-span-2">
          <NFormItem
            required
            :validation-status="errors.ipId ? 'error' : undefined"
            :feedback="errors.ipId"
          >
            <template #label>
              <span>落地机</span>
              <span v-if="loadingLandings" class="text-xs text-zinc-400 ml-2">
                <NSpin :size="12" /> 加载中
              </span>
              <span v-else class="text-xs text-zinc-400 ml-2">仅显示可分配</span>
            </template>
            <NSelect
              v-model:value="form.ipId"
              :options="landingOptions"
              :status="errors.ipId ? 'error' : undefined"
              placeholder="选落地机"
            />
          </NFormItem>
        </div>

        <NFormItem label="流量上限 (字节, 0=不限)">
          <NInputNumber v-model:value="form.totalBytes" :min="0" class="w-full" />
        </NFormItem>

        <NFormItem label="到期 (epoch ms, 0=永久)">
          <NInputNumber v-model:value="form.expiryEpochMillis" :min="0" class="w-full" />
        </NFormItem>

        <NFormItem label="限 IP 数 (0=不限, 上限 100)">
          <NInputNumber v-model:value="form.limitIp" :min="0" :max="100" class="w-full" />
        </NFormItem>
      </div>
    </NForm>

    <template #footer>
      <NSpace justify="end">
        <NButton size="small" @click="close">取消</NButton>
        <NButton
          type="primary"
          size="small"
          :loading="submitting"
          :disabled="memberCheck === 'checking'"
          @click="onSubmit"
        >
          确定
        </NButton>
      </NSpace>
    </template>
  </NModal>
</template>
