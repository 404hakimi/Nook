<script setup lang="ts">
import { computed, h, ref, watch } from 'vue'
import { RefreshCw } from 'lucide-vue-next'
import {
  NButton,
  NCard,
  NDataTable,
  NIcon,
  NModal,
  NSpace,
  NSpin,
  NTag,
  useMessage,
  type DataTableColumns
} from 'naive-ui'
import { getSlotPoolView, type XrayNode, type XraySlotItem } from '@/api/xray/node'

interface Props {
  modelValue: boolean
  node?: XrayNode | null
}
const props = defineProps<Props>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
}>()

const message = useMessage()

const slotsLoading = ref(false)
const slots = ref<XraySlotItem[]>([])

watch(
  () => [props.modelValue, props.node?.serverId],
  ([open]) => {
    if (open) {
      slots.value = []
      runSlots()
    }
  }
)

async function runSlots() {
  if (!props.node || slotsLoading.value) return
  slotsLoading.value = true
  try {
    slots.value = await getSlotPoolView(props.node.serverId)
  } catch (e) {
    slots.value = []
    message.error('拉 Slot 占用失败: ' + ((e as Error).message ?? ''))
  } finally {
    slotsLoading.value = false
  }
}

const CLIENT_STATUS_META: Record<number, { label: string; type: 'success' | 'warning' | 'error' | 'default' }> = {
  1: { label: '运行', type: 'success' },
  2: { label: '已停', type: 'default' },
  3: { label: '待同步', type: 'warning' },
  4: { label: '远端缺失', type: 'error' }
}

const slotColumns = computed<DataTableColumns<XraySlotItem>>(() => [
  {
    title: '槽位',
    key: 'slotIndex',
    width: 70,
    render: (r) => h('span', { class: 'font-mono text-xs' }, r.slotIndex)
  },
  {
    title: '端口',
    key: 'listenPort',
    width: 90,
    render: (r) => h('span', { class: 'font-mono text-xs' }, r.listenPort)
  },
  {
    title: '状态',
    key: 'used',
    width: 90,
    render: (r) =>
      r.used
        ? h(NTag, { size: 'small', type: 'info' }, { default: () => '已占用' })
        : h(NTag, { size: 'small', type: 'default' }, { default: () => '空闲' })
  },
  {
    title: '协议',
    key: 'protocol',
    width: 100,
    render: (r) =>
      r.protocol
        ? h(
            'span',
            { class: 'font-mono text-xs' },
            r.transport ? `${r.protocol}+${r.transport}` : r.protocol
          )
        : h('span', { class: 'text-zinc-400 text-xs' }, '-')
  },
  {
    title: '客户',
    key: 'client',
    render: (r) => {
      if (!r.used) return h('span', { class: 'text-zinc-400 text-xs' }, '-')
      const status = r.clientStatus != null ? CLIENT_STATUS_META[r.clientStatus] : null
      return h('div', { class: 'flex items-center gap-2' }, [
        h(
          'span',
          { class: 'text-xs', title: r.clientId || '' },
          r.clientEmail || r.clientId || '(已删)'
        ),
        status
          ? h(NTag, { size: 'small', type: status.type, bordered: false }, { default: () => status.label })
          : null
      ])
    }
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
    style="max-width: 56rem"
    :bordered="false"
    :mask-closable="true"
    @update:show="(v: boolean) => emit('update:modelValue', v)"
  >
    <template #header>
      <span>Slot 占用</span>
    </template>
    <template #header-extra>
      <span v-if="node" class="text-xs text-zinc-500">
        {{ node.serverName || node.serverId }} <span v-if="node.serverHost">({{ node.serverHost }})</span>
      </span>
    </template>

    <div class="flex justify-end mb-3">
      <NButton quaternary size="small" :disabled="slotsLoading" @click="runSlots">
        <template #icon><NIcon><RefreshCw /></NIcon></template>
        刷新 Slot
      </NButton>
    </div>

    <NSpin :show="slotsLoading && slots.length === 0">
      <NCard size="small" :content-style="{ padding: 0 }">
        <NDataTable
          :columns="slotColumns"
          :data="slots"
          :loading="slotsLoading"
          :bordered="false"
          :row-key="(r: XraySlotItem) => r.slotIndex"
          size="small"
          :max-height="480"
        />
      </NCard>
    </NSpin>

    <template #footer>
      <NSpace justify="end">
        <NButton size="small" @click="close">关闭</NButton>
      </NSpace>
    </template>
  </NModal>
</template>
