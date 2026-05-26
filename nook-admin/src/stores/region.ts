import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { listEnabledRegions, type SystemRegion } from '@/api/system/region'

/**
 * 区域字典 store (全局共用; 一次拉取后缓存).
 *
 * 服务器卡片 / 详情头部国旗 / 编辑 dialog 下拉 等都需要 region 字典,
 * 各组件各自 onMounted 拉会 N 次重复请求, 全部走该 store 去重.
 */
export const useRegionStore = defineStore('region', () => {
  const list = ref<SystemRegion[]>([])
  const loaded = ref(false)
  let inflight: Promise<void> | null = null

  /** code → SystemRegion 映射 */
  const map = computed(() => {
    const m: Record<string, SystemRegion> = {}
    for (const r of list.value) m[r.code] = r
    return m
  })

  /** 已加载就复用; 并发调用复用同一 inflight Promise. */
  async function ensureLoaded(): Promise<void> {
    if (loaded.value) return
    if (inflight) return inflight
    inflight = (async () => {
      try {
        list.value = await listEnabledRegions()
        loaded.value = true
      } finally {
        inflight = null
      }
    })()
    return inflight
  }

  /** admin 改完区域字典后调; 强制下次 access 重拉. */
  async function reload(): Promise<void> {
    loaded.value = false
    return ensureLoaded()
  }

  return { list, loaded, map, ensureLoaded, reload }
})
