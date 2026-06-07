<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import {
  NAlert,
  NButton,
  NForm,
  NFormItem,
  NInputNumber,
  NModal,
  NSelect,
  NSpace,
  NSpin,
  NTag,
  useMessage
} from 'naive-ui'
import {
  getServerQuota,
  updateServerQuota,
  SERVER_QUOTA_RESET_POLICY_OPTIONS,
  SERVER_THROTTLE_STATE_LABELS,
  type ServerQuota
} from '@/api/resource/server'

/**
 * 编辑 server 容量阈值 — 业务阈值 (限速 / 月流量上限 / 重置策略).
 *
 * <p>throttleState 由后期业务流转 (90% 阈值 + agent push) 自动维护, 这里只读展示.
 * rxBytes/txBytes/usedBytes 由 agent push, 这里只读展示.
 */
const props = defineProps<{
  modelValue: boolean
  serverId: string
}>()
const emit = defineEmits<{
  'update:modelValue': [v: boolean]
  saved: []
}>()

const message = useMessage()
const submitting = ref(false)
const loading = ref(false)

const form = reactive({
  totalGb: null as number | null,
  bandwidthMbps: null as number | null,
  resetPolicy: 'MONTHLY',
  resetDay: 1 as number | null
})

// 远端 agent 上报的累计字段 + 状态机字段 (只读, 后期业务流转写)
const runtime = reactive({
  usedBytes: 0 as number | null,
  rxBytes: 0 as number | null,
  txBytes: 0 as number | null,
  throttleState: 'NORMAL' as string | null
})

function fill(c: ServerQuota | null) {
  form.totalGb = c?.totalGb ?? null
  form.bandwidthMbps = c?.bandwidthMbps ?? null
  form.resetPolicy = c?.resetPolicy ?? 'MONTHLY'
  form.resetDay = c?.resetDay ?? 1
  runtime.usedBytes = c?.usedBytes ?? 0
  runtime.rxBytes = c?.rxBytes ?? 0
  runtime.txBytes = c?.txBytes ?? 0
  runtime.throttleState = c?.throttleState ?? 'NORMAL'
}

watch(() => [props.modelValue, props.serverId], async ([open]) => {
  if (!open) return
  loading.value = true
  try {
    const c = await getServerQuota(props.serverId)
    fill(c)
  } catch { /* */ } finally {
    loading.value = false
  }
})

const usedTrafficLabel = computed(() => {
  const bytes = runtime.usedBytes ?? 0
  if (bytes === 0) return '0 B'
  const gb = bytes / 1024 / 1024 / 1024
  if (gb >= 1) return `${gb.toFixed(2)} GB`
  const mb = bytes / 1024 / 1024
  return `${mb.toFixed(1)} MB`
})

const trafficUsagePercent = computed(() => {
  if (!form.totalGb || form.totalGb <= 0) return null
  const usedGb = (runtime.usedBytes ?? 0) / 1024 / 1024 / 1024
  return Math.min(100, Math.round((usedGb / form.totalGb) * 100))
})

async function onSubmit() {
  submitting.value = true
  try {
    await updateServerQuota(props.serverId, {
      totalGb: form.totalGb ?? 0,
      bandwidthMbps: form.bandwidthMbps ?? 0,
      resetPolicy: form.resetPolicy,
      resetDay: form.resetPolicy === 'MONTHLY' ? (form.resetDay ?? undefined) : undefined
    })
    message.success('已保存')
    emit('saved')
    emit('update:modelValue', false)
  } catch { /* */ } finally {
    submitting.value = false
  }
}
</script>

<template>
  <NModal
    :show="modelValue"
    preset="card"
    title="编辑容量与流量阈值"
    style="max-width: 40rem; width: 92vw"
    :bordered="false"
    @update:show="(v: boolean) => emit('update:modelValue', v)"
  >
    <NSpin :show="loading">
      <!-- 当前限流状态 (read-only, 由后期业务状态机维护) -->
      <div class="section-header">
        <span class="section-title">运行控制</span>
        <NTag
          v-if="runtime.throttleState && runtime.throttleState !== 'NORMAL'"
          size="small"
          type="warning"
        >
          {{ SERVER_THROTTLE_STATE_LABELS[runtime.throttleState] || runtime.throttleState }}
        </NTag>
        <NTag v-else size="small" type="success">
          {{ SERVER_THROTTLE_STATE_LABELS.NORMAL }}
        </NTag>
      </div>
      <NAlert type="info" :show-icon="false" size="small" class="mb-3">
        <strong>带宽容量</strong>: 线路机出站带宽, 供套餐分配不超卖 (预留 ~10%), 不做真实限速 (限速在落地机 egress); <strong>月流量阈值</strong>: 月用量达 90% 触发限流的基数. 0 = 不限.
      </NAlert>
      <NForm :model="form" label-placement="top" size="small">
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-x-4">
          <NFormItem label="带宽容量 Mbps (供分配, 须 >0)">
            <NInputNumber v-model:value="form.bandwidthMbps" :min="0" :max="100000" class="w-full" />
          </NFormItem>
          <NFormItem label="月流量阈值 GB (0=不限)">
            <NInputNumber v-model:value="form.totalGb" :min="0" :max="1000000" class="w-full" />
          </NFormItem>
          <NFormItem label="周期重置策略">
            <NSelect v-model:value="form.resetPolicy" :options="SERVER_QUOTA_RESET_POLICY_OPTIONS as any" />
          </NFormItem>
          <NFormItem v-if="form.resetPolicy === 'MONTHLY'" label="每月重置日 (1-28)">
            <NInputNumber v-model:value="form.resetDay" :min="1" :max="28" class="w-full" />
          </NFormItem>
        </div>

        <!-- 当前周期使用情况 (只读, agent push) -->
        <div class="usage-block">
          <div class="usage-label">当前周期已用</div>
          <div class="usage-value">
            <span class="font-mono">{{ usedTrafficLabel }}</span>
            <template v-if="trafficUsagePercent != null">
              <span class="text-zinc-400 mx-1">/</span>
              <span class="font-mono">{{ form.totalGb }} GB</span>
              <NTag size="small" :type="trafficUsagePercent >= 90 ? 'error' : (trafficUsagePercent >= 70 ? 'warning' : 'success')" class="ml-2">
                {{ trafficUsagePercent }}%
              </NTag>
            </template>
            <span v-else class="text-zinc-400 ml-2 text-xs">月流量阈值未配置, 不触发限流</span>
          </div>
        </div>
      </NForm>
    </NSpin>
    <template #footer>
      <NSpace justify="end">
        <NButton size="small" @click="emit('update:modelValue', false)">取消</NButton>
        <NButton type="primary" size="small" :loading="submitting" :disabled="loading" @click="onSubmit">保存</NButton>
      </NSpace>
    </template>
  </NModal>
</template>

<style scoped>
.section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--n-text-color-2, #555);
}
.usage-block {
  margin-top: 4px;
  padding: 8px 12px;
  background: var(--n-action-color, #fafafa);
  border-radius: 4px;
  border: 1px solid var(--n-border-color, #efeff5);
}
.usage-label {
  font-size: 11px;
  color: var(--n-text-color-3, #999);
  margin-bottom: 2px;
}
.usage-value {
  font-size: 13px;
  color: var(--n-text-color-1, #222);
  display: flex;
  align-items: center;
}
</style>
