<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  NButton,
  NCard,
  NEmpty,
  NForm,
  NFormItem,
  NIcon,
  NInput,
  NModal,
  NSelect,
  NSpin,
  NSwitch,
  useMessage
} from 'naive-ui'
import { ChevronDown, ListTree, Package, Plus, RotateCcw, Search, Server } from 'lucide-vue-next'
import { useRegionStore } from '@/stores/region'
import {
  createRegion,
  listRegions,
  recodeRegion,
  toggleRegionEnabled,
  updateRegion,
  type SystemRegion,
  type SystemRegionRecodeDTO,
  type SystemRegionSaveDTO
} from '@/api/system/region'
import { getServerRegionUsage } from '@/api/resource/server'
import { getPlanRegionUsage } from '@/api/trade/plan'
import { COUNTRIES, countryFlagEmoji } from '@/constants/countries'
import RegionFlag from '@/components/RegionFlag.vue'
import { useConfirm } from '@/composables/useConfirm'

const message = useMessage()
const regionStore = useRegionStore()
const { confirm } = useConfirm()

const list = ref<SystemRegion[]>([])
const loading = ref(false)
// 区域引用数: code → { machines, plans } (前端拉机器+套餐自行统计, 不依赖后端跨模块)
const usage = ref<Record<string, { machines: number; plans: number }>>({})

async function loadUsage() {
  try {
    // 各模块各提供按区域聚合的统计接口 (GROUP BY), 不再拉分页列表前端数
    const [machines, plans] = await Promise.all([getServerRegionUsage(), getPlanRegionUsage()])
    const map: Record<string, { machines: number; plans: number }> = {}
    for (const [code, n] of Object.entries(machines || {})) {
      if (!map[code]) map[code] = { machines: 0, plans: 0 }
      map[code].machines = n
    }
    for (const [code, n] of Object.entries(plans || {})) {
      if (!map[code]) map[code] = { machines: 0, plans: 0 }
      map[code].plans = n
    }
    usage.value = map
  } catch {
    /* */
  }
}
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
onMounted(() => { void load(); void loadUsage() })
function onSearch() {
  load()
}
function doReset() {
  keyword.value = ''
  filterEnabled.value = undefined
  load()
}

// ===== 二级树: 国家 → 城市 =====
interface CountryGroup {
  code: string
  name: string
  flag: string
  cities: SystemRegion[]
}
const grouped = computed<CountryGroup[]>(() => {
  const m = new Map<string, CountryGroup>()
  for (const r of list.value) {
    let g = m.get(r.countryCode)
    if (!g) {
      g = { code: r.countryCode, name: r.countryName, flag: r.flagEmoji || countryFlagEmoji(r.countryCode), cities: [] }
      m.set(r.countryCode, g)
    }
    g.cities.push(r)
  }
  return [...m.values()]
})
const collapsed = ref<Record<string, boolean>>({}) // 默认空 = 全部展开
function toggle(code: string) {
  collapsed.value[code] = !collapsed.value[code]
}
function setAllCollapsed(v: boolean) {
  const next: Record<string, boolean> = {}
  for (const g of grouped.value) next[g.code] = v
  collapsed.value = next
}

// ===== 新建 / 编辑弹窗 =====
const dialogOpen = ref(false)
const isEdit = ref(false)
const saving = ref(false)
/** 更正区域码: 原码 + 是否处于更正模式 (解锁区域码/城市码编辑). */
const originalCode = ref('')
const recoding = ref(false)
const form = reactive<SystemRegionSaveDTO>({
  code: '', countryCode: '', countryName: '', city: '', displayName: '', flagEmoji: ''
})
/** 城市码: 仅用于拼区域码后缀 (JP-TYO 的 TYO); 不单独入库. */
const cityCode = ref('')

// 国家下拉: 预设表 + (编辑历史数据时) 补当前国家码, 免得选框空白
const countryOptions = computed(() => {
  const opts = COUNTRIES.map((c) => ({ label: `${countryFlagEmoji(c.code)} ${c.name} (${c.code})`, value: c.code }))
  if (form.countryCode && !COUNTRIES.some((c) => c.code === form.countryCode)) {
    opts.unshift({ label: `${countryFlagEmoji(form.countryCode)} ${form.countryName || form.countryCode} (${form.countryCode})`, value: form.countryCode })
  }
  return opts
})

/** 区域码 = 国家码[-城市码]; 编辑时区域码是主键不可改, 跳过. */
function recomputeCode() {
  if (isEdit.value && !recoding.value) return
  const cc = (form.countryCode || '').toUpperCase()
  const sub = cityCode.value.trim().toUpperCase()
  form.code = cc ? (sub ? `${cc}-${sub}` : cc) : ''
}
/** 展示名 = 国家名[ · 城市]. */
function recomputeDisplayName() {
  const cn = form.countryName || ''
  const ct = (form.city || '').trim()
  form.displayName = ct ? `${cn} · ${ct}` : cn
}
/** 选国家: 带出 countryCode / countryName / 国旗, 并刷新区域码 + 展示名. */
function onCountrySelect(code: string | number | null) {
  if (typeof code !== 'string' || !code) return
  const c = COUNTRIES.find((x) => x.code === code)
  form.countryCode = code
  form.countryName = c ? c.name : form.countryName
  form.flagEmoji = countryFlagEmoji(code)
  recomputeCode()
  recomputeDisplayName()
}

function resetForm() {
  Object.assign(form, { code: '', countryCode: '', countryName: '', city: '', displayName: '', flagEmoji: '' })
  cityCode.value = ''
  recoding.value = false
  originalCode.value = ''
}
function openCreate() {
  isEdit.value = false
  resetForm()
  dialogOpen.value = true
}
/** 在某国家下加城市: 预选国家, 用户只需填城市 + 城市码. */
function openCreateForCountry(g: CountryGroup) {
  isEdit.value = false
  resetForm()
  form.countryCode = g.code
  form.countryName = g.name
  form.flagEmoji = g.flag || countryFlagEmoji(g.code)
  recomputeCode()
  recomputeDisplayName()
  dialogOpen.value = true
}
function openEdit(r: SystemRegion) {
  isEdit.value = true
  Object.assign(form, {
    code: r.code, countryCode: r.countryCode, countryName: r.countryName,
    city: r.city ?? '', displayName: r.displayName, flagEmoji: r.flagEmoji ?? ''
  })
  // 从区域码拆出城市码后缀 (JP-TYO → TYO) 仅供展示
  cityCode.value = r.code.includes('-') ? r.code.slice(r.code.indexOf('-') + 1) : ''
  originalCode.value = r.code
  recoding.value = false
  dialogOpen.value = true
}

async function onSave() {
  if (!form.countryCode.trim() || !form.code.trim() || !form.displayName.trim()) {
    message.warning('请先选择国家 / 地区, 并确认区域码与展示名')
    return
  }
  // 编辑态且处于更正模式且区域码真的改了 → 走级联更正
  const codeChanged = isEdit.value && recoding.value && form.code.trim() !== originalCode.value
  if (codeChanged) {
    const u = usage.value[originalCode.value]
    const ok = await confirm({
      title: '更正区域码',
      message: u && u.machines + u.plans > 0
        ? `区域码 ${originalCode.value} → ${form.code}; 将同步迁移 ${u.machines} 台机器 + ${u.plans} 个套餐的引用。是否继续?`
        : `区域码 ${originalCode.value} → ${form.code}; 当前无机器 / 套餐引用。是否继续?`,
      type: 'warning',
      confirmText: '更正并迁移'
    })
    if (!ok) return
  }
  saving.value = true
  try {
    const dto: SystemRegionSaveDTO = {
      ...form,
      city: form.city?.trim() || undefined,
      flagEmoji: form.flagEmoji?.trim() || undefined
    }
    if (codeChanged) {
      await recodeRegion({ ...dto, oldCode: originalCode.value } as SystemRegionRecodeDTO)
      message.success('已更正并迁移引用')
    } else if (isEdit.value) {
      await updateRegion(form.code, dto)
      message.success('已保存')
    } else {
      await createRegion(dto)
      message.success('已新建')
    }
    dialogOpen.value = false
    await Promise.all([load(), loadUsage()])
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
        <NButton size="small" quaternary @click="setAllCollapsed(false)">展开全部</NButton>
        <NButton size="small" quaternary @click="setAllCollapsed(true)">收起全部</NButton>
        <NButton type="primary" size="small" @click="openCreate">
          <template #icon><NIcon><Plus :size="14" /></NIcon></template>
          新建区域
        </NButton>
      </div>
    </NCard>

    <NCard size="small" :bordered="false">
      <NSpin :show="loading">
        <NEmpty v-if="!loading && grouped.length === 0" description="暂无区域, 点右上角新建" class="py-10">
          <template #icon><NIcon :size="28"><ListTree /></NIcon></template>
        </NEmpty>
        <div v-else class="rt">
          <div v-for="g in grouped" :key="g.code" class="rt-group">
            <!-- 国家行 -->
            <div class="rt-country" @click="toggle(g.code)">
              <span class="rt-chevron" :class="{ 'rt-collapsed': collapsed[g.code] }">
                <NIcon :size="15"><ChevronDown /></NIcon>
              </span>
              <RegionFlag :code="g.code" :fallback="g.flag" squared :size="18" />
              <span class="rt-cname">{{ g.name }}</span>
              <span class="rt-ccode">{{ g.code }}</span>
              <span class="rt-count">{{ g.cities.length }} 个城市</span>
              <span class="flex-1"></span>
              <NButton size="tiny" quaternary type="primary" title="在该国家下新增城市" @click.stop="openCreateForCountry(g)">
                <template #icon><NIcon><Plus :size="13" /></NIcon></template>
                城市
              </NButton>
            </div>
            <!-- 城市行 -->
            <div v-show="!collapsed[g.code]" class="rt-cities">
              <div v-for="r in g.cities" :key="r.code" class="rt-city">
                <span class="rt-citycode font-mono">{{ r.code }}</span>
                <span class="rt-cityname">{{ r.city || '未命名城市' }}</span>
                <span class="rt-display">{{ r.displayName }}</span>
                <span class="flex-1"></span>
                <span
                  class="rt-usage"
                  :title="`${usage[r.code]?.machines || 0} 台机器 / ${usage[r.code]?.plans || 0} 个套餐在用`"
                >
                  <NIcon :size="12"><Server /></NIcon>{{ usage[r.code]?.machines || 0 }}
                  <NIcon :size="12"><Package /></NIcon>{{ usage[r.code]?.plans || 0 }}
                </span>
                <NSwitch :value="r.enabled === 1" size="small" @update:value="(v: boolean) => onToggle(r, v)" />
                <NButton size="tiny" quaternary @click="openEdit(r)">编辑</NButton>
              </div>
            </div>
          </div>
        </div>
      </NSpin>
    </NCard>

    <NModal
      v-model:show="dialogOpen"
      preset="card"
      :title="isEdit ? '编辑区域' : '新建区域'"
      style="width: 460px; max-width: 92vw"
    >
      <NForm label-placement="top" size="small">
        <NFormItem label="国家 / 地区" required>
          <NSelect
            :value="form.countryCode || null"
            :options="countryOptions"
            filterable
            placeholder="选择国家 / 地区 (自动带出国家码与国旗)"
            @update:value="onCountrySelect"
          />
        </NFormItem>
        <div class="grid grid-cols-2 gap-x-3">
          <NFormItem label="城市">
            <NInput v-model:value="form.city" placeholder="东京 (可空)" @update:value="recomputeDisplayName" />
          </NFormItem>
          <NFormItem label="城市码">
            <NInput v-model:value="cityCode" :disabled="isEdit && !recoding" placeholder="TYO (可空)" @update:value="recomputeCode" />
          </NFormItem>
        </div>
        <div class="grid grid-cols-2 gap-x-3">
          <NFormItem label="区域码" required>
            <NInput v-model:value="form.code" :disabled="isEdit && !recoding" placeholder="自动生成, 如 JP-TYO">
              <template v-if="isEdit && !recoding" #suffix>
                <NButton text size="tiny" type="primary" @click="recoding = true">更正</NButton>
              </template>
            </NInput>
          </NFormItem>
          <NFormItem label="展示名" required>
            <NInput v-model:value="form.displayName" placeholder="自动生成, 如 日本 · 东京" />
          </NFormItem>
        </div>
        <div v-if="recoding" class="text-xs leading-relaxed recode-warn">
          ⚠ 更正区域码会改主键, 并把引用该码的线路机 / 落地机和套餐一起迁到新码 (单事务原子)。
          <template v-if="usage[originalCode]">当前 {{ usage[originalCode].machines }} 台机器 + {{ usage[originalCode].plans }} 个套餐在用。</template>
        </div>
        <div v-else class="text-xs text-zinc-400 leading-relaxed">
          选国家后自动带出国家码 / 国旗; 区域码、展示名按"国家 + 城市"自动生成, 可手动微调。<template v-if="isEdit">区域码默认锁定, 需更正点右侧“更正”。</template>
        </div>
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

<style scoped>
.rt {
  display: flex;
  flex-direction: column;
}
.rt-group {
  padding: 2px 0;
}
.rt-group + .rt-group {
  border-top: 1px solid rgba(127, 127, 127, 0.07);
}

/* 国家行 */
.rt-country {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 9px 10px;
  border-radius: 8px;
  cursor: pointer;
  user-select: none;
  transition: background 0.12s ease;
}
.rt-country:hover {
  background: rgba(127, 127, 127, 0.06);
}
.rt-chevron {
  display: inline-flex;
  color: var(--nook-fg-faint, #a1a1aa);
  transition: transform 0.15s ease;
}
.rt-chevron.rt-collapsed {
  transform: rotate(-90deg);
}
.rt-cname {
  font-size: 14px;
  font-weight: 600;
  color: var(--n-text-color-1, #222);
}
.rt-ccode {
  font-size: 11px;
  font-family: 'JetBrains Mono', monospace;
  color: var(--nook-fg-faint, #a1a1aa);
  background: rgba(127, 127, 127, 0.1);
  border-radius: 4px;
  padding: 1px 6px;
}
.rt-count {
  font-size: 12px;
  color: var(--n-text-color-3, #9ca3af);
}

/* 城市行 (缩进到国旗下) */
.rt-cities {
  display: flex;
  flex-direction: column;
  gap: 1px;
  padding-bottom: 4px;
}
.rt-city {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 7px 10px 7px 36px;
  border-radius: 6px;
  transition: background 0.12s ease;
}
.rt-city:hover {
  background: rgba(127, 127, 127, 0.05);
}
.rt-citycode {
  font-size: 12px;
  color: var(--nook-accent, #6366f1);
  background: rgba(99, 102, 241, 0.08);
  border: 1px solid rgba(99, 102, 241, 0.16);
  border-radius: 5px;
  padding: 1px 8px;
  min-width: 92px;
  text-align: center;
}
.rt-cityname {
  font-size: 13px;
  font-weight: 500;
  color: var(--n-text-color-1, #222);
}
.rt-display {
  font-size: 12px;
  color: var(--n-text-color-3, #9ca3af);
}
.rt-usage {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 11.5px;
  color: var(--n-text-color-3, #9ca3af);
  margin-right: 6px;
}
.rt-usage :deep(svg) { opacity: 0.6; }
.rt-usage :deep(svg):not(:first-child) { margin-left: 7px; }
.recode-warn { color: #d97706; }
html[data-theme='dark'] .recode-warn { color: #fbbf24; }
</style>
