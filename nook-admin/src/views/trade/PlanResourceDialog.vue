<script setup lang="ts">
import { computed, h, ref, watch } from 'vue'
import {
  NButton,
  NDataTable,
  NModal,
  NSelect,
  NSpace,
  NTag,
  useMessage,
  type DataTableColumns
} from 'naive-ui'
import { useConfirm } from '@/composables/useConfirm'
import { pageServers } from '@/api/resource/server'
import { pageServerLanding } from '@/api/resource/server-landing'
import {
  bindTradePlanResource,
  listTradePlanResource,
  unbindTradePlanResource,
  type TradePlan,
  type TradePlanResource
} from '@/api/trade/plan'

const props = defineProps<{ modelValue: boolean; plan?: TradePlan | null }>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'changed'): void
}>()

const message = useMessage()
const { confirm } = useConfirm()

const resources = ref<TradePlanResource[]>([])
const loading = ref(false)
const resType = ref<'FRONTLINE' | 'LANDING'>('FRONTLINE')
const candidateId = ref<string | null>(null)
const candidates = ref<{ label: string; value: string }[]>([])
const loadingCand = ref(false)
const binding = ref(false)

const typeOptions = [
  { label: '门禁 (线路机)', value: 'FRONTLINE' },
  { label: '公寓 (落地机)', value: 'LANDING' }
]

async function loadResources() {
  if (!props.plan?.id) return
  loading.value = true
  try {
    resources.value = await listTradePlanResource(props.plan.id)
  } catch {
    /* */
  } finally {
    loading.value = false
  }
}

async function loadCandidates() {
  candidateId.value = null
  loadingCand.value = true
  try {
    if (resType.value === 'FRONTLINE') {
      const res = await pageServers({ pageNo: 1, pageSize: 100, lifecycleState: 'LIVE' })
      candidates.value = res.records.map((s) => ({ label: `${s.name} (${s.host ?? '-'})`, value: s.id }))
    } else {
      const res = await pageServerLanding({ pageNo: 1, pageSize: 100, lifecycleState: 'LIVE', status: 'AVAILABLE' })
      candidates.value = res.records.map((s) => ({ label: `${s.name} · ${s.ipAddress}`, value: s.id }))
    }
  } catch {
    /* */
  } finally {
    loadingCand.value = false
  }
}

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    resType.value = 'FRONTLINE'
    loadResources()
    loadCandidates()
  }
)
watch(resType, () => loadCandidates())

async function onBind() {
  if (!props.plan?.id || !candidateId.value) {
    message.warning('请选资源')
    return
  }
  binding.value = true
  try {
    await bindTradePlanResource(props.plan.id, resType.value, candidateId.value)
    message.success('已绑定')
    candidateId.value = null
    await Promise.all([loadResources(), loadCandidates()])
    emit('changed')
  } catch {
    /* */
  } finally {
    binding.value = false
  }
}

async function onUnbind(row: TradePlanResource) {
  const ok = await confirm({
    title: '解绑资源',
    message: `确定解绑 ${row.name ?? row.resourceId}? (已有订阅不受影响)`,
    type: 'warning',
    confirmText: '解绑'
  })
  if (!ok) return
  try {
    await unbindTradePlanResource(row.id)
    message.success('已解绑')
    await Promise.all([loadResources(), loadCandidates()])
    emit('changed')
  } catch {
    /* */
  }
}

const columns = computed<DataTableColumns<TradePlanResource>>(() => [
  {
    title: '类型',
    key: 'resourceType',
    width: 80,
    render: (r) => h(NTag, { size: 'small', type: r.resourceType === 'FRONTLINE' ? 'info' : 'success' },
      { default: () => (r.resourceType === 'FRONTLINE' ? '门禁' : '公寓') })
  },
  { title: '名称', key: 'name', render: (r) => r.name ?? '-' },
  { title: 'IP', key: 'ipAddress', render: (r) => r.ipAddress ?? '-' },
  {
    title: '生命周期',
    key: 'lifecycleState',
    width: 100,
    render: (r) => h(NTag, { size: 'small', type: r.lifecycleState === 'LIVE' ? 'success' : 'warning' },
      { default: () => r.lifecycleState ?? '-' })
  },
  { title: '占用', key: 'landingStatus', width: 90, render: (r) => r.landingStatus ?? '-' },
  {
    title: '操作',
    key: 'op',
    width: 70,
    render: (r) => h(NButton, { size: 'tiny', quaternary: true, type: 'error', onClick: () => onUnbind(r) },
      { default: () => '解绑' })
  }
])

function close() {
  emit('update:modelValue', false)
}
</script>

<template>
  <NModal
    :show="modelValue"
    preset="card"
    :title="`配置资源池 — ${plan?.name ?? ''}`"
    style="max-width: 56rem; width: 94vw"
    :bordered="false"
    @update:show="(v: boolean) => emit('update:modelValue', v)"
  >
    <div class="space-y-3">
      <div class="flex items-end gap-2">
        <div style="width: 160px">
          <div class="text-xs text-zinc-500 mb-1">资源类型</div>
          <NSelect v-model:value="resType" :options="typeOptions" size="small" />
        </div>
        <div class="flex-1">
          <div class="text-xs text-zinc-500 mb-1">
            选择资源 (仅 LIVE{{ resType === 'LANDING' ? ' + 可分配' : '' }})
          </div>
          <NSelect
            v-model:value="candidateId"
            :options="candidates"
            :loading="loadingCand"
            filterable
            clearable
            size="small"
            placeholder="选要绑定的服务器"
          />
        </div>
        <NButton type="primary" size="small" :loading="binding" @click="onBind">添加</NButton>
      </div>
      <NDataTable
        :columns="columns"
        :data="resources"
        :loading="loading"
        size="small"
        :bordered="false"
        :row-key="(r: TradePlanResource) => r.id"
      />
      <div class="text-xs text-zinc-400">
        门禁(线路机) ≥1 (建议 ≥2 便于切换); 公寓(落地机)数 = 套餐容量; 落地机 IP 类型须与套餐一致.
      </div>
    </div>
    <template #footer>
      <NSpace justify="end"><NButton size="small" @click="close">关闭</NButton></NSpace>
    </template>
  </NModal>
</template>
