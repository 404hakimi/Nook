import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { listIpTypes, type SystemIpType } from '@/api/system/ip-type'

/**
 * IP 类型字典 store (全局共用; 一次拉取后缓存).
 *
 * 落地机详情 + 编辑 dialog + 创建 dialog 等都需要 ip-type 字典,
 * 各组件各自 onMounted 拉会 N 次重复请求, 全部走该 store 去重.
 */
export const useIpTypeStore = defineStore('ipType', () => {
  const list = ref<SystemIpType[]>([])
  const loaded = ref(false)
  let inflight: Promise<void> | null = null

  /** id → SystemIpType 映射 */
  const map = computed(() => {
    const m: Record<string, SystemIpType> = {}
    for (const t of list.value) m[t.id] = t
    return m
  })

  /** 已加载就复用; 并发调用复用同一 inflight Promise. */
  async function ensureLoaded(): Promise<void> {
    if (loaded.value) return
    if (inflight) return inflight
    inflight = (async () => {
      try {
        list.value = await listIpTypes()
        loaded.value = true
      } finally {
        inflight = null
      }
    })()
    return inflight
  }

  /** admin 改完 ip-type 字典后调; 强制下次 access 重拉. */
  async function reload(): Promise<void> {
    loaded.value = false
    return ensureLoaded()
  }

  return { list, loaded, map, ensureLoaded, reload }
})
