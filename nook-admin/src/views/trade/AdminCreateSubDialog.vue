<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { NButton, NForm, NFormItem, NModal, NSelect, NSpace, useMessage } from 'naive-ui'
import { pageMemberAccounts } from '@/api/member/user'
import { pageTradePlan, type TradePlan } from '@/api/trade/plan'
import { adminCreateSubscription } from '@/api/trade/subscription'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'created'): void
}>()

const message = useMessage()
const memberId = ref<string | null>(null)
const planId = ref<string | null>(null)
const memberOptions = ref<{ label: string; value: string }[]>([])
const plans = ref<TradePlan[]>([])
const loadingMembers = ref(false)
const loadingPlans = ref(false)
const submitting = ref(false)

const planOptions = computed(() =>
  plans.value.map((p) => ({
    label: `${p.name} · 剩${p.capacityAvailable ?? 0}/${p.capacityTotal ?? 0} · ¥${p.price}`,
    value: p.id
  }))
)
const selectedPlan = computed(() => plans.value.find((p) => p.id === planId.value))
const noStock = computed(() => !!selectedPlan.value && (selectedPlan.value.capacityAvailable ?? 0) <= 0)

async function loadMembers() {
  loadingMembers.value = true
  try {
    const res = await pageMemberAccounts({ pageNo: 1, pageSize: 100, status: 1 })
    memberOptions.value = res.records.map((m) => ({ label: m.email, value: m.id }))
  } catch {
    /* */
  } finally {
    loadingMembers.value = false
  }
}
async function loadPlans() {
  loadingPlans.value = true
  try {
    const res = await pageTradePlan({ pageNo: 1, pageSize: 100, enabled: 1 })
    plans.value = res.records
  } catch {
    /* */
  } finally {
    loadingPlans.value = false
  }
}

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    memberId.value = null
    planId.value = null
    loadMembers()
    loadPlans()
  }
)

async function onSubmit() {
  if (!memberId.value) {
    message.warning('请选会员')
    return
  }
  if (!planId.value) {
    message.warning('请选套餐')
    return
  }
  if (noStock.value) {
    message.warning('该套餐已售罄')
    return
  }
  submitting.value = true
  try {
    await adminCreateSubscription(memberId.value, planId.value)
    message.success('开通成功')
    emit('created')
    emit('update:modelValue', false)
  } catch {
    /* */
  } finally {
    submitting.value = false
  }
}

function close() {
  emit('update:modelValue', false)
}
</script>

<template>
  <NModal
    :show="modelValue"
    preset="card"
    title="代客下单"
    style="max-width: 40rem; width: 92vw"
    :bordered="false"
    :mask-closable="false"
    @update:show="(v: boolean) => emit('update:modelValue', v)"
  >
    <NForm label-placement="top" size="small">
      <NFormItem label="会员" required>
        <NSelect
          v-model:value="memberId"
          :options="memberOptions"
          :loading="loadingMembers"
          filterable
          placeholder="按邮箱选会员"
        />
      </NFormItem>
      <NFormItem label="套餐" required>
        <NSelect
          v-model:value="planId"
          :options="planOptions"
          :loading="loadingPlans"
          filterable
          placeholder="选套餐 (仅上架)"
        />
      </NFormItem>
      <div v-if="noStock" class="text-xs text-red-500">该套餐无可分配落地机 (售罄)</div>
      <div class="text-xs text-zinc-400">下单将自动选线路机 + 落地机, 开通 xray 客户端并生成订阅.</div>
    </NForm>
    <template #footer>
      <NSpace justify="end">
        <NButton size="small" @click="close">取消</NButton>
        <NButton type="primary" size="small" :loading="submitting" :disabled="noStock" @click="onSubmit">开通</NButton>
      </NSpace>
    </template>
  </NModal>
</template>
