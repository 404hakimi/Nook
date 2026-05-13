import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { simpleListOpConfig, type OpConfigSimple } from '@/api/operation/op-config'

/**
 * Op 配置精简数据 store (仅 opType + name)
 *
 * 给跨页面共用的"opType → 中文名"需求服务: OpLog 下拉 / OpLog 行 label / OpLogDetailDialog / OpConfigEditDialog.
 * 全字段编辑列表在 OpConfigList 自己拉, 不走这个 store.
 */
export const useOpConfigStore = defineStore('opConfig', () => {
  const list = ref<OpConfigSimple[]>([])
  const loaded = ref(false)
  let inflight: Promise<void> | null = null

  /** opType → name 映射 */
  const labelMap = computed(() => {
    const m = new Map<string, string>()
    for (const row of list.value) m.set(row.opType, row.name)
    return m
  })

  function getLabel(opType: string): string {
    return labelMap.value.get(opType) || opType
  }

  /** 已加载就直接复用; 并发调用复用同一 inflight Promise */
  async function ensureLoaded(): Promise<void> {
    if (loaded.value) return
    if (inflight) return inflight
    inflight = (async () => {
      try {
        list.value = await simpleListOpConfig()
        loaded.value = true
      } finally {
        inflight = null
      }
    })()
    return inflight
  }

  /** admin 改完 op_config 后调; 强制下次访问重新拉 */
  async function reload(): Promise<void> {
    loaded.value = false
    return ensureLoaded()
  }

  return { list, loaded, labelMap, getLabel, ensureLoaded, reload }
})
