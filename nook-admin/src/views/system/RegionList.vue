<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue'
import {
  NButton,
  NCard,
  NDataTable,
  NForm,
  NFormItem,
  NIcon,
  NInput,
  NModal,
  NSelect,
  NSwitch,
  useMessage,
  type DataTableColumns
} from 'naive-ui'
import { Plus, RotateCcw, Search } from 'lucide-vue-next'
import { useRegionStore } from '@/stores/region'
import {
  createRegion,
  listRegions,
  toggleRegionEnabled,
  updateRegion,
  type SystemRegion,
  type SystemRegionSaveDTO
} from '@/api/system/region'

const message = useMessage()
const regionStore = useRegionStore()

const list = ref<SystemRegion[]>([])
const loading = ref(false)
const keyword = ref('')
const filterEnabled = ref<number | undefined>(undefined)

const enabledOptions = [
  { label: '全部', value: undefined as number | undefined },
  { label: '启用', value: 1 },
  { label: '停用', value: 0 }
]

async function load() {
  loading.value = true
  try {
    list.value = await listRegions({ keyword: keyword.value.trim() || undefined, enabled: filterEnabled.value })
  } catch {
    /* */
  } finally {
    loading.value = false
  }
}
onMounted(load)
function onSearch() {
  load()
}
function doReset() {
  keyword.value = ''
  filterEnabled.value = undefined
  load()
}

// ===== 新建 / 编辑弹窗 =====
const dialogOpen = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const form = reactive<SystemRegionSaveDTO>({
  code: '', countryCode: '', countryName: '', city: '', displayName: '', flagEmoji: ''
})

function openCreate() {
  isEdit.value = false
  Object.assign(form, { code: '', countryCode: '', countryName: '', city: '', displayName: '', flagEmoji: '' })
  dialogOpen.value = true
}
function openEdit(r: SystemRegion) {
  isEdit.value = true
  Object.assign(form, {
    code: r.code, countryCode: r.countryCode, countryName: r.countryName,
    city: r.city ?? '', displayName: r.displayName, flagEmoji: r.flagEmoji ?? ''
  })
  dialogOpen.value = true
}

async function onSave() {
  if (!form.code.trim() || !form.countryCode.trim() || !form.countryName.trim() || !form.displayName.trim()) {
    message.warning('区域码 / 国家码 / 国家名 / 展示名必填')
    return
  }
  saving.value = true
  try {
    const dto: SystemRegionSaveDTO = {
      ...form,
      city: form.city?.trim() || undefined,
      flagEmoji: form.flagEmoji?.trim() || undefined
    }
    if (isEdit.value) {
      await updateRegion(dto)
      message.success('已保存')
    } else {
      await createRegion(dto)
      message.success('已新建')
    }
    dialogOpen.value = false
    await load()
    await regionStore.reload()
  } catch {
    /* */
  } finally {
    saving.value = false
  }
}

async function onToggle(r: SystemRegion, val: boolean) {
  try {
    await toggleRegionEnabled(r.code, val)
    r.enabled = val ? 1 : 0
    await regionStore.reload()
  } catch {
    /* */
  }
}

const columns: DataTableColumns<SystemRegion> = [
  { title: '区域码', key: 'code', width: 120, render: (r) => h('span', { class: 'font-mono text-xs' }, r.code) },
  { title: '国家', key: 'country', render: (r) => `${r.flagEmoji ?? ''} ${r.countryName} (${r.countryCode})` },
  { title: '城市', key: 'city', render: (r) => r.city || '—' },
  { title: '展示名', key: 'displayName' },
  {
    title: '状态',
    key: 'enabled',
    width: 80,
    render: (r) => h(NSwitch, { value: r.enabled === 1, size: 'small', 'onUpdate:value': (v: boolean) => onToggle(r, v) })
  },
  {
    title: '操作',
    key: 'op',
    width: 70,
    render: (r) => h(NButton, { size: 'tiny', quaternary: true, onClick: () => openEdit(r) }, { default: () => '编辑' })
  }
]
</script>

<template>
  <div class="space-y-3">
    <NCard size="small" :bordered="false">
      <div class="flex items-center gap-2 flex-wrap">
        <NInput
          v-model:value="keyword"
          placeholder="区域码 / 国家 / 城市 / 展示名"
          size="small"
          style="width: 220px"
          clearable
          @keyup.enter="onSearch"
        />
        <NSelect v-model:value="filterEnabled" :options="enabledOptions" size="small" style="width: 110px" />
        <NButton type="primary" size="small" @click="onSearch">
          <template #icon><NIcon><Search :size="14" /></NIcon></template>
          搜索
        </NButton>
        <NButton size="small" @click="doReset">
          <template #icon><NIcon><RotateCcw :size="14" /></NIcon></template>
          重置
        </NButton>
        <div class="flex-1"></div>
        <NButton type="primary" size="small" @click="openCreate">
          <template #icon><NIcon><Plus :size="14" /></NIcon></template>
          新建区域
        </NButton>
      </div>
    </NCard>

    <NCard size="small" :bordered="false">
      <NDataTable
        :columns="columns"
        :data="list"
        :loading="loading"
        size="small"
        :bordered="false"
        :row-key="(r: SystemRegion) => r.code"
      />
    </NCard>

    <NModal
      v-model:show="dialogOpen"
      preset="card"
      :title="isEdit ? '编辑区域' : '新建区域'"
      style="width: 460px; max-width: 92vw"
    >
      <NForm label-placement="top" size="small">
        <NFormItem label="区域码" required>
          <NInput v-model:value="form.code" :disabled="isEdit" placeholder="JP-TYO / US-LAX / HK" />
        </NFormItem>
        <div class="grid grid-cols-2 gap-x-3">
          <NFormItem label="国家码" required>
            <NInput v-model:value="form.countryCode" placeholder="JP" />
          </NFormItem>
          <NFormItem label="国家名" required>
            <NInput v-model:value="form.countryName" placeholder="日本" />
          </NFormItem>
          <NFormItem label="城市">
            <NInput v-model:value="form.city" placeholder="东京 (可空)" />
          </NFormItem>
          <NFormItem label="国旗 emoji">
            <NInput v-model:value="form.flagEmoji" placeholder="🇯🇵" />
          </NFormItem>
        </div>
        <NFormItem label="展示名" required>
          <NInput v-model:value="form.displayName" placeholder="日本 · 东京" />
        </NFormItem>
      </NForm>
      <template #footer>
        <div class="flex justify-end gap-2">
          <NButton size="small" @click="dialogOpen = false">取消</NButton>
          <NButton type="primary" size="small" :loading="saving" @click="onSave">保存</NButton>
        </div>
      </template>
    </NModal>
  </div>
</template>
