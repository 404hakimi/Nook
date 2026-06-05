<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { NButton, NForm, NFormItem, NModal, NSelect, NSpace, useMessage } from 'naive-ui'
import { pageMemberAccounts } from '@/api/member/user'
import { pageTradePlan, type TradePlan } from '@/api/trade/plan'
import { adminCreateSubscription } from '@/api/trade/subscription'

// presetPlan: 套餐详情页代客下单时直接传入当前套餐, 避免再拉全量套餐列表; 不传则为全局下单, 自行加载可选套餐
const props = defineProps<{ modelValue: boolean; presetPlan?: TradePlan | null }>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'created'): void
}>()

const message = useMessage()
const memberId = ref<string | null>(null)
const planId = ref<string | null>(null)
const plans = ref<TradePlan[]>([])
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

// ===== 会员下拉: 后端分页滚动 + 关键字搜索 =====
const MEMBER_PAGE_SIZE = 10
const memberOptions = ref<{ label: string; value: string }[]>([])
const selectedMember = ref<{ label: string; value: string } | null>(null)
const memberKeyword = ref('')
const memberPageNo = ref(1)
const memberHasMore = ref(true)
const loadingMembers = ref(false)
let memberSearchTimer: ReturnType<typeof setTimeout> | undefined

// 已选会员可能不在当前页, 合并进选项避免回显成 id
const memberSelectOptions = computed(() => {
  const sel = selectedMember.value
  if (sel && !memberOptions.value.some((o) => o.value === sel.value)) {
    return [sel, ...memberOptions.value]
  }
  return memberOptions.value
})

async function fetchMembers(reset: boolean) {
  if (loadingMembers.value) return
  if (!reset && !memberHasMore.value) return
  loadingMembers.value = true
  try {
    const pageNo = reset ? 1 : memberPageNo.value
    const res = await pageMemberAccounts({
      pageNo,
      pageSize: MEMBER_PAGE_SIZE,
      status: 1,
      keyword: memberKeyword.value.trim() || undefined
    })
    const opts = res.records.map((m) => ({ label: m.email, value: m.id }))
    memberOptions.value = reset ? opts : [...memberOptions.value, ...opts]
    memberPageNo.value = pageNo + 1
    memberHasMore.value = memberOptions.value.length < res.total
  } catch {
    /* */
  } finally {
    loadingMembers.value = false
  }
}

function onMemberSearch(q: string) {
  memberKeyword.value = q
  if (memberSearchTimer) clearTimeout(memberSearchTimer)
  memberSearchTimer = setTimeout(() => fetchMembers(true), 300)
}

function onMemberScroll(e: Event) {
  const el = e.currentTarget as HTMLElement
  if (el.scrollTop + el.offsetHeight >= el.scrollHeight - 12) {
    fetchMembers(false)
  }
}

function onMemberChange(val: string | null, opt: { label: string; value: string } | null) {
  memberId.value = val
  selectedMember.value = opt
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
    // 会员选择态重置 + 拉首页
    memberId.value = null
    selectedMember.value = null
    memberKeyword.value = ''
    memberPageNo.value = 1
    memberHasMore.value = true
    memberOptions.value = []
    fetchMembers(true)
    // 套餐: 详情页已带入当前套餐则直接用, 否则加载可选套餐
    if (props.presetPlan) {
      plans.value = [props.presetPlan]
      planId.value = props.presetPlan.id
    } else {
      planId.value = null
      loadPlans()
    }
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
          :value="memberId"
          :options="memberSelectOptions"
          :loading="loadingMembers"
          remote
          filterable
          clearable
          :reset-menu-on-options-change="false"
          placeholder="按邮箱搜索 (滚动加载更多)"
          @update:value="onMemberChange"
          @search="onMemberSearch"
          @scroll="onMemberScroll"
        />
      </NFormItem>
      <NFormItem label="套餐" required>
        <NSelect
          v-model:value="planId"
          :options="planOptions"
          :loading="loadingPlans"
          :disabled="!!presetPlan"
          filterable
          placeholder="选套餐 (仅上架)"
        />
      </NFormItem>
      <div v-if="noStock" class="text-xs text-red-500">该套餐无可分配落地机 (售罄)</div>
      <div class="text-xs text-zinc-400">下单将自动选线路机 + 落地机, 开通凭证并生成订阅.</div>
    </NForm>
    <template #footer>
      <NSpace justify="end">
        <NButton size="small" @click="close">取消</NButton>
        <NButton type="primary" size="small" :loading="submitting" :disabled="noStock" @click="onSubmit">开通</NButton>
      </NSpace>
    </template>
  </NModal>
</template>
