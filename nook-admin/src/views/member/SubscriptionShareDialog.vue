<script setup lang="ts">
import { ref, watch } from 'vue'
import { Copy, Share2, ShieldAlert } from 'lucide-vue-next'
import { NAlert, NButton, NIcon, NInput, NModal, NSpin, useMessage } from 'naive-ui'
import QrcodeVue from 'qrcode.vue'
import { getMemberSubUrl } from '@/api/member/user'

interface Props {
  modelValue: boolean
  member?: { id: string; email: string } | null
}
const props = defineProps<Props>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void }>()

const message = useMessage()
const loading = ref(false)
const subUrl = ref('')
const copied = ref(false)

watch(
  () => [props.modelValue, props.member?.id],
  async ([open, id]) => {
    if (!open || !id) {
      subUrl.value = ''
      return
    }
    copied.value = false
    loading.value = true
    try {
      subUrl.value = await getMemberSubUrl(id as string)
    } catch {
      subUrl.value = ''
    } finally {
      loading.value = false
    }
  }
)

async function onCopy() {
  if (!subUrl.value) return
  try {
    await navigator.clipboard.writeText(subUrl.value)
    copied.value = true
    message.success('已复制订阅链接')
    setTimeout(() => (copied.value = false), 2000)
  } catch {
    message.warning('浏览器拒绝写剪贴板, 请手动选中复制')
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
    style="max-width: 52rem; width: 92vw"
    :bordered="false"
    :mask-closable="true"
    @update:show="(v: boolean) => emit('update:modelValue', v)"
  >
    <template #header>
      <div class="flex items-center gap-2">
        <NIcon :size="20" :depth="2"><Share2 /></NIcon>
        <span>分享订阅链接</span>
      </div>
    </template>
    <template #header-extra>
      <span v-if="member" class="text-xs text-zinc-500">{{ member.email }}</span>
    </template>

    <NSpin :show="loading">
      <div class="min-h-[8rem]">
        <NAlert v-if="!loading && !subUrl" type="error" :show-icon="true">
          生成订阅链接失败, 请关闭重试 (会员可能无 sub_token 或后端未配置公网地址)
        </NAlert>

        <template v-else-if="subUrl">
          <!-- 安全提示: 订阅链接 = 该会员全部节点凭据 -->
          <NAlert type="warning" :show-icon="true" class="!mb-3">
            <template #icon><NIcon><ShieldAlert /></NIcon></template>
            <div class="text-xs">
              订阅链接含该会员全部节点凭据, 仅向本人会员展示, 勿截图发群。会员退订 / 被禁用后链接立即失效。
            </div>
          </NAlert>

          <!-- 订阅链接 + 二维码: 宽屏左右分栏, 窄屏竖排 -->
          <div class="flex flex-col sm:flex-row gap-4">
            <div class="flex-1 min-w-0">
              <div class="text-sm font-semibold mb-2">订阅链接 (客户端导入)</div>
              <NInput
                type="textarea"
                :value="subUrl"
                readonly
                :autosize="{ minRows: 3, maxRows: 5 }"
                :input-props="{ style: 'font-family: monospace; font-size: 12px' }"
              />
              <div class="flex justify-end mt-2">
                <NButton type="primary" size="small" @click="onCopy">
                  <template #icon><NIcon><Copy /></NIcon></template>
                  {{ copied ? '已复制' : '复制' }}
                </NButton>
              </div>
            </div>

            <div class="flex flex-col items-center gap-2 sm:shrink-0">
              <div class="text-sm font-semibold mb-1 self-start sm:self-center">二维码</div>
              <div class="bg-white p-2 rounded border border-zinc-200">
                <QrcodeVue :value="subUrl" :size="180" level="M" render-as="svg" background="#ffffff" />
              </div>
              <div class="text-xs text-zinc-500">扫码导入</div>
            </div>
          </div>

          <!-- 使用说明 -->
          <div class="text-sm font-semibold mt-6 mb-2">使用说明</div>
          <ul class="text-xs text-zinc-500 list-disc pl-5 space-y-1">
            <li>v2rayN / NekoBox / V2RayNG: 「订阅」→ 添加订阅 → 粘贴链接 → 更新</li>
            <li>Shadowrocket: 右上 + → 类型选「订阅」→ 粘贴链接</li>
            <li>订阅会自动拉取该会员当前生效的全部节点 (含故障切换后的新线路机)</li>
            <li>会员退订 / 被禁用后此链接立即失效</li>
          </ul>
        </template>
      </div>
    </NSpin>

    <template #footer>
      <div class="flex justify-end">
        <NButton size="small" @click="close">关闭</NButton>
      </div>
    </template>
  </NModal>
</template>
