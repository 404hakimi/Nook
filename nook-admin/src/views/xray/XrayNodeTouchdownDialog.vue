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
import { getTouchdownList, type XrayNode, type XrayTouchdownItem } from '@/api/xray/node'

interface Props {
  modelValue: boolean
  node?: XrayNode | null
}
const props = defineProps<Props>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
}>()

const message = useMessage()

const loading = ref(false)
const items = ref<XrayTouchdownItem[]>([])

watch(
  () => [props.modelValue, props.node?.serverId],
  ([open]) => {
    if (open) {
      items.value = []
      load()
    }
  }
)

async function load() {
  if (!props.node || loading.value) return
  loading.value = true
  try {
    items.value = await getTouchdownList(props.node.serverId)
  } catch (e) {
    items.value = []
    message.error('拉落地占用失败: ' + ((e as Error).message ?? ''))
  } finally {
    loading.value = false
  }
}

const CLIENT_STATUS_META: Record<number, { label: string; type: 'success' | 'warning' | 'error' | 'default' }> = {
  1: { label: '运行', type: 'success' },
  2: { label: '已停', type: 'default' },
  3: { label: '待同步', type: 'warning' },
  4: { label: '远端缺失', type: 'error' }
}

/** clientId 32 字符 hex 太长, 表格里只显示前 8 位; hover title 全长. */
const shortId = (id?: string | null) => (id ? id.slice(0, 8) : '-')

const columns = computed<DataTableColumns<XrayTouchdownItem>>(() => [
  {
    title: '客户 ID',
    key: 'clientId',
    width: 110,
    render: (r) =>
      h(
        'span',
        { class: 'font-mono text-xs', title: r.clientId || '' },
        shortId(r.clientId)
      )
  },
  {
    title: '落地 IP',
    key: 'ipAddress',
    width: 150,
    render: (r) =>
      r.ipAddress
        ? h('span', { class: 'font-mono text-xs' }, r.ipAddress)
        : h(
            'span',
            { class: 'text-zinc-400 text-xs', title: r.ipId || '' },
            r.ipId ? `(已删 ip=${r.ipId.slice(0, 8)})` : '-'
          )
  },
  {
    title: '邮箱',
    key: 'clientEmail',
    render: (r) =>
      r.clientEmail
        ? h('span', { class: 'text-xs', title: r.clientEmail }, r.clientEmail)
        : h('span', { class: 'text-zinc-400 text-xs' }, '-')
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
    title: '状态',
    key: 'clientStatus',
    width: 90,
    render: (r) => {
      const status = r.clientStatus != null ? CLIENT_STATUS_META[r.clientStatus] : null
      return status
        ? h(NTag, { size: 'small', type: status.type, bordered: false }, { default: () => status.label })
        : h('span', { class: 'text-zinc-400 text-xs' }, '-')
    }
  }
])

const summary = computed(() => {
  const total = items.value.length
  const cap = props.node?.touchdownSize ?? null
  return cap != null ? `${total} / ${cap}` : `${total}`
})

function close() {
  emit('update:modelValue', false)
}
</script>

<template>
  <NModal
    :show="modelValue"
    preset="card"
    style="max-width: 64rem"
    :bordered="false"
    :mask-closable="true"
    @update:show="(v: boolean) => emit('update:modelValue', v)"
  >
    <template #header>
      <span>落地占用 <span class="text-zinc-500 text-xs ml-2">{{ summary }}</span></span>
    </template>
    <template #header-extra>
      <span v-if="node" class="text-xs text-zinc-500">
        {{ node.serverName || node.serverId }} <span v-if="node.serverHost">({{ node.serverHost }})</span>
      </span>
    </template>

    <div class="flex justify-end mb-3">
      <NButton quaternary size="small" :disabled="loading" @click="load">
        <template #icon><NIcon><RefreshCw /></NIcon></template>
        刷新
      </NButton>
    </div>

    <NSpin :show="loading && items.length === 0">
      <NCard size="small" :content-style="{ padding: 0 }">
        <NDataTable
          :columns="columns"
          :data="items"
          :loading="loading"
          :bordered="false"
          :row-key="(r: XrayTouchdownItem) => r.clientId"
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
