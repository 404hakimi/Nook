<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  NButton,
  NForm,
  NFormItem,
  NInput,
  NInputNumber,
  NModal,
  NSelect,
  NSwitch,
  useMessage,
  type FormInst,
  type FormRules
} from 'naive-ui'
import {
  createOpConfig,
  listOpTypeOptions,
  updateOpConfig,
  type OpConfig,
  type OpTypeOption
} from '@/api/operation/op-config'
import { useOpConfigStore } from '@/stores/opConfig'

const props = defineProps<{
  modelValue: boolean
  /** 编辑 = 传入现有行; 创建 = null */
  record: OpConfig | null
}>()
const emit = defineEmits<{
  'update:modelValue': [boolean]
  saved: []
}>()

const message = useMessage()
const opConfigStore = useOpConfigStore()
const formRef = ref<FormInst | null>(null)
const submitting = ref(false)
const opTypeOptions = ref<OpTypeOption[]>([])

const isEdit = computed(() => !!props.record)
const title = computed(() => (isEdit.value ? '编辑 Op 配置' : '新建 Op 配置'))

interface FormState {
  opType: string
  name: string
  execTimeoutSeconds: number | null
  waitTimeoutSeconds: number | null
  maxRetry: number | null
  enabled: boolean
  description: string
}

function emptyForm(): FormState {
  return {
    opType: '',
    name: '',
    execTimeoutSeconds: 120,
    waitTimeoutSeconds: 150,
    maxRetry: 0,
    enabled: true,
    description: ''
  }
}

const form = ref<FormState>(emptyForm())

const rules: FormRules = {
  opType: [{ required: true, message: '请选择操作类型' }],
  name: [{ required: true, message: '请填写显示名称' }],
  execTimeoutSeconds: [
    { required: true, type: 'number', message: '请填写执行超时' },
    {
      validator: (_, v) =>
        v != null && v >= 1 && v <= 7200 ? true : new Error('1-7200 秒'),
      trigger: 'blur'
    }
  ],
  waitTimeoutSeconds: [
    { required: true, type: 'number', message: '请填写等待超时' },
    {
      validator: (_, v) =>
        v != null && v >= 1 && v <= 7200 ? true : new Error('1-7200 秒'),
      trigger: 'blur'
    }
  ]
}

// OpType 下拉: 编辑时锁死当前 opType; 创建时过滤掉已 configured 的
const opTypeSelectOptions = computed(() => {
  if (isEdit.value && props.record) {
    return [{ label: opTypeLabel(props.record.opType), value: props.record.opType }]
  }
  return opTypeOptions.value
    .filter((o) => !o.configured)
    .map((o) => ({ label: opTypeLabel(o.opType), value: o.opType }))
})

function opTypeLabel(opType: string): string {
  const cn = opConfigStore.getLabel(opType)
  return cn && cn !== opType ? `${cn} (${opType})` : opType
}

watch(
  () => props.modelValue,
  async (v) => {
    if (!v) return
    if (props.record) {
      const r = props.record
      form.value = {
        opType: r.opType,
        name: r.name,
        execTimeoutSeconds: r.execTimeoutSeconds,
        waitTimeoutSeconds: r.waitTimeoutSeconds,
        maxRetry: r.maxRetry,
        enabled: r.enabled,
        description: r.description ?? ''
      }
    } else {
      form.value = emptyForm()
      try {
        opTypeOptions.value = await listOpTypeOptions()
      } catch {
        opTypeOptions.value = []
      }
    }
  },
  { immediate: true }
)

function onClose() {
  emit('update:modelValue', false)
}

async function onSubmit() {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    if (isEdit.value && props.record) {
      await updateOpConfig(props.record.id, {
        name: form.value.name,
        execTimeoutSeconds: form.value.execTimeoutSeconds!,
        waitTimeoutSeconds: form.value.waitTimeoutSeconds!,
        maxRetry: form.value.maxRetry ?? 0,
        enabled: form.value.enabled,
        description: form.value.description || undefined
      })
      message.success('已保存')
    } else {
      await createOpConfig({
        opType: form.value.opType,
        name: form.value.name,
        execTimeoutSeconds: form.value.execTimeoutSeconds!,
        waitTimeoutSeconds: form.value.waitTimeoutSeconds!,
        maxRetry: form.value.maxRetry ?? 0,
        enabled: form.value.enabled,
        description: form.value.description || undefined
      })
      message.success('已创建')
    }
    emit('saved')
    onClose()
  } catch {
    /* request 拦截器已 toast */
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <NModal
    :show="modelValue"
    preset="card"
    :title="title"
    :style="{ width: '600px' }"
    @update:show="onClose"
  >
    <NForm ref="formRef" :model="form" :rules="rules" label-placement="left" label-width="110">
      <NFormItem label="操作类型" path="opType">
        <NSelect
          v-model:value="form.opType"
          :options="opTypeSelectOptions"
          :disabled="isEdit"
          placeholder="选择 OpType"
        />
      </NFormItem>
      <NFormItem label="显示名称" path="name">
        <NInput v-model:value="form.name" placeholder="中文名, admin 列表里展示" maxlength="64" />
      </NFormItem>
      <NFormItem label="执行超时" path="execTimeoutSeconds">
        <NInputNumber
          v-model:value="form.execTimeoutSeconds"
          :min="1"
          :max="7200"
          class="w-full"
        >
          <template #suffix>秒</template>
        </NInputNumber>
      </NFormItem>
      <NFormItem label="等待超时" path="waitTimeoutSeconds">
        <NInputNumber
          v-model:value="form.waitTimeoutSeconds"
          :min="1"
          :max="7200"
          class="w-full"
        >
          <template #suffix>秒</template>
        </NInputNumber>
      </NFormItem>
      <NFormItem label="失败重试" path="maxRetry">
        <NInputNumber v-model:value="form.maxRetry" :min="0" :max="10" class="w-full" />
      </NFormItem>
      <NFormItem label="是否启用">
        <NSwitch v-model:value="form.enabled" />
      </NFormItem>
      <NFormItem label="备注">
        <NInput
          v-model:value="form.description"
          type="textarea"
          :autosize="{ minRows: 2, maxRows: 4 }"
          maxlength="255"
          show-count
          placeholder="为何要这么调 (可选)"
        />
      </NFormItem>
    </NForm>
    <template #footer>
      <div class="flex justify-end gap-2">
        <NButton @click="onClose">取消</NButton>
        <NButton type="primary" :loading="submitting" @click="onSubmit">保存</NButton>
      </div>
    </template>
  </NModal>
</template>
