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
  THROTTLE_STATE_LABELS,
  getServerLandingQuota,
  updateServerLandingQuota,
  type ServerLandingQuota
} from '@/api/resource/server-landing'

/**
 * 编辑 容量 (quota 子表) — 实际限速 + 月流量上限 + 重置策略.
 * rx/tx/used/throttle 由 agent push, 这里只读展示.
 */
const props = defineProps<{
  modelValue: boolean
  serverId: string
}>()
const emit = defineEmits<{
  'update:modelValue': [v: boolean]
  saved: []
}>()

const QUOTA_RESET_OPTIONS = [
  { label: '按月重置 (MONTHLY, 默认)', value: 'MONTHLY' },
  { label: '永不重置 (FIXED)', value: 'FIXED' }
]

const message = useMessage()
const submitting = ref(false)
const loading = ref(false)
const errors = reactive<Record<string, string>>({})

const form = reactive({
  bandwidthMbps: 0,
  totalGb: null as number | null,
  usablePercent: 90 as number | null,
  resetPolicy: 'MONTHLY',
  resetDay: 1 as number | null
})
// 远端 agent 上报的累计字段 (只读)
const runtime = reactive({
  usedBytes: 0 as number | null,
  rxBytes: 0 as number | null,
  txBytes: 0 as number | null,
  throttleState: 'NORMAL' as string | null
})

function fill(c: ServerLandingQuota | null) {
  form.bandwidthMbps = c?.bandwidthMbps ?? 0
  form.totalGb = c?.totalGb ?? null
  form.usablePercent = c?.usablePercent ?? 90
  form.resetPolicy = c?.resetPolicy ?? 'MONTHLY'
  form.resetDay = c?.resetDay ?? 1
  runtime.usedBytes = c?.usedBytes ?? 0
  runtime.rxBytes = c?.rxBytes ?? 0
  runtime.txBytes = c?.txBytes ?? 0
  runtime.throttleState = c?.throttleState ?? 'NORMAL'
}

watch(() => [props.modelValue, props.serverId], async ([open]) => {
  if (!open) return
  Object.keys(errors).forEach((k) => delete errors[k])
  loading.value = true
  try {
    const c = await getServerLandingQuota(props.serverId).catch(() => null)
    fill(c)
  } finally {
    loading.value = false
  }
}, { immediate: true })

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

function validate(): boolean {
  Object.keys(errors).forEach((k) => delete errors[k])
  if (form.bandwidthMbps == null || form.bandwidthMbps < 0) {
    errors.bandwidthMbps = '实际限速 ≥ 0 (0 = 不限)'
  }
  if (form.totalGb != null && form.totalGb < 0) {
    errors.totalGb = '月流量上限 ≥ 0'
  }
  if (form.usablePercent != null && (form.usablePercent < 1 || form.usablePercent > 100)) {
    errors.usablePercent = '可用比例 1-100'
  }
  return Object.keys(errors).length === 0
}

async function onSubmit() {
  if (!validate()) return
  submitting.value = true
  try {
    await updateServerLandingQuota(props.serverId, {
      bandwidthMbps: form.bandwidthMbps,
      totalGb: form.totalGb ?? undefined,
      usablePercent: form.usablePercent ?? undefined,
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
    title="编辑 容量 — 实际控制"
    style="max-width: 40rem; width: 92vw"
    :bordered="false"
    @update:show="(v: boolean) => emit('update:modelValue', v)"
  >
    <NSpin :show="loading">
      <div class="section-header">
        <span class="section-title">运行控制</span>
        <NTag
          v-if="runtime.throttleState && runtime.throttleState !== 'NORMAL'"
          size="small"
          type="warning"
        >
          {{ THROTTLE_STATE_LABELS[runtime.throttleState] || runtime.throttleState }}
        </NTag>
        <NTag v-else size="small" type="success">
          {{ THROTTLE_STATE_LABELS.NORMAL }}
        </NTag>
      </div>
      <NAlert type="info" :show-icon="false" size="small" class="mb-3">
        <strong>实际限速</strong>: 落地机出口带宽限速值, 修改后需重装才在远端生效;
        <strong>月流量上限</strong>: 照抄厂商面板原值 (单向计费厂商 ×2), 0 = 不限; 月用量达 上限 × 可用比例 即限流并停止参与新订阅分配.
      </NAlert>
      <NForm :model="form" label-placement="top" size="small">
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-x-4">
          <NFormItem
            label="实际限速 Mbps (0=不限)"
            :feedback="errors.bandwidthMbps"
            :validation-status="errors.bandwidthMbps ? 'error' : undefined"
          >
            <NInputNumber v-model:value="form.bandwidthMbps" :min="0" :max="1000000" class="w-full" />
          </NFormItem>
          <NFormItem
            label="月流量上限 GB (留空=不限)"
            :feedback="errors.totalGb"
            :validation-status="errors.totalGb ? 'error' : undefined"
          >
            <NInputNumber v-model:value="form.totalGb" :min="0" :max="10000000" class="w-full" />
          </NFormItem>
          <NFormItem
            label="可用比例 % (留冗余给换机延迟/装机流量)"
            :feedback="errors.usablePercent"
            :validation-status="errors.usablePercent ? 'error' : undefined"
          >
            <NInputNumber v-model:value="form.usablePercent" :min="1" :max="100" class="w-full" />
          </NFormItem>
          <NFormItem label="周期重置策略">
            <NSelect v-model:value="form.resetPolicy" :options="QUOTA_RESET_OPTIONS" />
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
            <span v-else class="text-zinc-400 ml-2 text-xs">月流量上限未配置, 不触发限流</span>
          </div>
        </div>
      </NForm>
    </NSpin>
    <template #footer>
      <NSpace justify="end">
        <NButton size="small" @click="emit('update:modelValue', false)">取消</NButton>
        <NButton type="primary" size="small" :loading="submitting" :disabled="loading" @click="onSubmit">
          保存
        </NButton>
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
