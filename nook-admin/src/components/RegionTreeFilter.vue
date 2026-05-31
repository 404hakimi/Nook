<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { NIcon } from 'naive-ui'
import { ChevronDown, Globe } from 'lucide-vue-next'
import { storeToRefs } from 'pinia'
import { useRegionStore } from '@/stores/region'

/** 选中变化时抛出当前区域码集合 (空数组=全部); 父组件据此筛列表. */
const emit = defineEmits<{ change: [codes: string[]] }>()

interface RegionCountry {
  code: string
  name: string
  flag?: string
  cities: { code: string; label: string }[]
}

const regionStore = useRegionStore()
const { list: regionList } = storeToRefs(regionStore)

const activeKey = ref('all') // 'all' | country:XX | region:XX, 仅用于高亮
const collapsed = ref<Record<string, boolean>>({}) // 默认空 = 全部展开

const groups = computed<RegionCountry[]>(() => {
  const m = new Map<string, RegionCountry>()
  for (const r of regionList.value) {
    if (!m.has(r.countryCode)) {
      m.set(r.countryCode, { code: r.countryCode, name: r.countryName, flag: r.flagEmoji, cities: [] })
    }
    m.get(r.countryCode)!.cities.push({ code: r.code, label: r.city || r.displayName || r.code })
  }
  return [...m.values()]
})

function apply(key: string, codes: string[]) {
  activeKey.value = key
  emit('change', codes)
}
function selectAll() {
  apply('all', [])
}
function onCountry(c: RegionCountry) {
  collapsed.value[c.code] = false // 点国家即展开
  apply(`country:${c.code}`, c.cities.map((x) => x.code))
}
function onCity(code: string) {
  apply(`region:${code}`, [code])
}
function toggle(code: string) {
  collapsed.value[code] = !collapsed.value[code]
}

/** 父组件"重置"时调用: 回到全部 (不 emit, 父组件自行清自己的筛选). */
defineExpose({ reset: () => { activeKey.value = 'all' } })

onMounted(() => { void regionStore.ensureLoaded() })
</script>

<template>
  <aside class="region-aside">
    <div class="region-head">
      <NIcon :size="14"><Globe /></NIcon>
      <span>区域</span>
    </div>
    <div class="region-list">
      <div class="r-row r-all" :class="{ 'r-active': activeKey === 'all' }" @click="selectAll">
        全部区域
      </div>
      <div v-for="c in groups" :key="c.code" class="r-group">
        <div
          class="r-row r-country"
          :class="{ 'r-active': activeKey === `country:${c.code}` }"
          @click="onCountry(c)"
        >
          <span
            class="r-chevron"
            :class="{ 'r-collapsed': collapsed[c.code] }"
            @click.stop="toggle(c.code)"
          >
            <NIcon :size="13"><ChevronDown /></NIcon>
          </span>
          <span class="r-flag">{{ c.flag || '🏳️' }}</span>
          <span class="r-name">{{ c.name }}</span>
          <span class="r-count">{{ c.cities.length }}</span>
        </div>
        <div v-show="!collapsed[c.code]" class="r-cities">
          <div
            v-for="city in c.cities"
            :key="city.code"
            class="r-row r-city"
            :class="{ 'r-active': activeKey === `region:${city.code}` }"
            @click="onCity(city.code)"
          >
            {{ city.label }}
          </div>
        </div>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.region-aside {
  width: 216px;
  flex-shrink: 0;
  align-self: stretch;
  overflow-y: auto;
  scrollbar-gutter: stable;
  padding: 8px;
  background: var(--card-color, #fff);
  border: 1px solid rgba(127, 127, 127, 0.12);
  border-radius: 10px;
}
html[data-theme='dark'] .region-aside {
  background: #1f1f23;
}
.region-head {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--nook-fg-muted);
  padding: 4px 8px 8px;
}
.region-head :deep(svg) {
  color: var(--nook-fg-faint);
}
.region-list {
  display: flex;
  flex-direction: column;
  gap: 1px;
}
.r-row {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 6px 8px;
  border-radius: 7px;
  cursor: pointer;
  font-size: 13px;
  line-height: 1.2;
  color: var(--nook-fg);
  user-select: none;
  transition: background 0.12s ease;
}
.r-row:hover {
  background: rgba(127, 127, 127, 0.08);
}
.r-active,
.r-active:hover {
  background: rgba(99, 102, 241, 0.12);
  color: var(--nook-accent);
  font-weight: 600;
}
.r-all,
.r-country {
  font-weight: 500;
}
.r-chevron {
  display: inline-flex;
  color: var(--nook-fg-faint);
  border-radius: 4px;
  transition: transform 0.15s ease;
}
.r-chevron:hover {
  background: rgba(127, 127, 127, 0.15);
}
.r-chevron.r-collapsed {
  transform: rotate(-90deg);
}
.r-flag {
  font-size: 14px;
  line-height: 1;
}
.r-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.r-count {
  font-size: 11px;
  color: var(--nook-fg-faint);
  background: rgba(127, 127, 127, 0.1);
  border-radius: 999px;
  padding: 0 6px;
  min-width: 18px;
  text-align: center;
}
.r-active .r-count {
  background: rgba(99, 102, 241, 0.18);
  color: var(--nook-accent);
}
.r-cities {
  display: flex;
  flex-direction: column;
  gap: 1px;
}
.r-city {
  padding-left: 30px;
  font-size: 12.5px;
  color: var(--nook-fg-muted);
}
.r-city.r-active {
  color: var(--nook-accent);
}
</style>
