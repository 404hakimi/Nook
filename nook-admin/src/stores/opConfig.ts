import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { listOpConfig, type OpConfig } from '@/api/operation/op-config'

/**
 * Op 配置全局共享 store
 *
 * 一次拉取 op_config 全表, 给 OpLogList 下拉 / OpConfigList 表格 / Dialog 显示统一用.
 * label = op_config.name (admin 改完立刻在 reload 后生效); 缺失时 fallback 到 opType 字面值.
 */
export const useOpConfigStore = defineStore('opConfig', () => {
  const list = ref<OpConfig[]>([])
  const loaded = ref(false)
  let inflight: Promise<void> | null = null

  /** opType → name 映射, 给行渲染 / dropdown label 用 */
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
        list.value = await listOpConfig()
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
