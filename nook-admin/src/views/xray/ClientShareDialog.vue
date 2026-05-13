<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Copy, Share2, ShieldAlert } from 'lucide-vue-next'
import {
  NAlert,
  NButton,
  NDescriptions,
  NDescriptionsItem,
  NIcon,
  NInput,
  NModal,
  NSpin,
  useMessage
} from 'naive-ui'
import QrcodeVue from 'qrcode.vue'
import { getClientCredential, type XrayClient, type XrayClientCredential } from '@/api/xray/client'
import { buildClientUri } from '@/utils/xrayUri'

interface Props {
  modelValue: boolean
  client?: XrayClient | null
}
const props = defineProps<Props>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
}>()

const message = useMessage()
const copied = ref(false)
/**
 * 列表接口下发的 clientUuid 是 mask 形式 (xxx***xxxx), 不能直接用来拼 URI;
 * 打开 dialog 时按 id 拉一次专用 reveal 接口拿明文凭据.
 */
const credential = ref<XrayClientCredential | null>(null)
const loading = ref(false)

const uri = computed(() => {
  const c = credential.value
  if (!c || !c.serverHost || !c.listenPort) return ''
  try {
    return buildClientUri({
      protocol: c.protocol,
      host: c.serverHost,
      port: c.listenPort,
      uuid: c.clientUuid,
      email: c.clientEmail,
      transport: c.transport
    })
  } catch (e) {
    console.warn('[share] 拼 URI 失败:', e)
    return ''
  }
})

watch(
  () => [props.modelValue, props.client?.id],
  async ([open, id]) => {
    if (!open || !id) {
      // 关 dialog 即清掉凭据 + URI, 不在内存里留着
      credential.value = null
      return
    }
    copied.value = false
    loading.value = true
    try {
      credential.value = await getClientCredential(id as string)
    } catch (e) {
      console.error('[share] 加载凭据失败:', e)
      credential.value = null
    } finally {
      loading.value = false
    }
  }
)

async function onCopy() {
  if (!uri.value) return
  try {
    await navigator.clipboard.writeText(uri.value)
    copied.value = true
    message.success('已复制订阅链接')
    setTimeout(() => (copied.value = false), 2000)
  } catch (e) {
    console.warn('[share] clipboard 写入失败, 请手动复制', e)
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
    style="max-width: 46rem"
    :bordered="false"
    :mask-closable="true"
    @update:show="(v: boolean) => emit('update:modelValue', v)"
  >
    <template #header>
      <div class="flex items-center gap-2">
        <NIcon :size="20" :depth="2"><Share2 /></NIcon>
        <span>分享给客户端</span>
      </div>
    </template>
    <template #header-extra>
      <span v-if="client" class="text-xs text-zinc-500">
        {{ client.clientEmail }} · {{ client.protocol }}
      </span>
    </template>

    <NSpin :show="loading">
      <div class="min-h-[8rem]">
        <div v-if="!client" class="text-sm text-zinc-500 py-8 text-center">
          未指定 client
        </div>

        <NAlert v-else-if="!loading && !credential" type="error" :show-icon="true">
          加载凭据失败, 请关闭重试
        </NAlert>

        <NAlert v-else-if="credential && !credential.serverHost" type="warning" :show-icon="true">
          关联的 resource_server 不存在或已被删, 无法生成订阅链接
        </NAlert>

        <NAlert v-else-if="credential && !uri" type="error" :show-icon="true">
          无法生成订阅链接 — 协议 "{{ credential.protocol }}" 暂不支持, 或 listenPort 缺失
        </NAlert>

        <template v-else-if="credential && uri">
          <!-- 安全提示: 二维码 = 凭据明文图像, 截图泄露即等于送密钥 -->
          <NAlert type="warning" :show-icon="true" class="!mb-3">
            <template #icon>
              <NIcon><ShieldAlert /></NIcon>
            </template>
            <div class="text-xs">
              二维码与订阅链接包含完整凭据 (UUID / 密码), 请仅向本人会员展示, 勿截图发群.
            </div>
          </NAlert>

          <!-- 订阅链接 + 二维码: 宽屏左右分栏, 窄屏竖排 -->
          <div class="flex flex-col sm:flex-row gap-4">
            <div class="flex-1 min-w-0">
              <div class="text-sm font-semibold mb-2">订阅链接</div>
              <NInput
                type="textarea"
                :value="uri"
                readonly
                :autosize="{ minRows: 5, maxRows: 7 }"
                :input-props="{ style: 'font-family: monospace; font-size: 12px' }"
              />
              <div class="flex justify-end mt-2">
                <NButton type="primary" size="small" @click="onCopy">
                  <template #icon>
                    <NIcon><Copy /></NIcon>
                  </template>
                  {{ copied ? '已复制' : '复制' }}
                </NButton>
              </div>
            </div>

            <div class="flex flex-col items-center gap-2 sm:shrink-0">
              <div class="text-sm font-semibold mb-1 self-start sm:self-center">二维码</div>
              <!--
                level=M 错误纠正适合大多数客户端扫码; render-as=svg 保持矢量清晰,
                background=#fff 确保深色模式下也是白底 (黑底 QR 部分客户端识别有问题).
              -->
              <div class="bg-white p-2 rounded border border-zinc-200">
                <QrcodeVue :value="uri" :size="180" level="M" render-as="svg" background="#ffffff" />
              </div>
              <div class="text-xs text-zinc-500">扫码导入</div>
            </div>
          </div>

          <!-- 使用说明 -->
          <div class="text-sm font-semibold mt-6 mb-2">使用说明</div>
          <ul class="text-xs text-zinc-500 list-disc pl-5 space-y-1">
            <li>v2rayN / NekoBox / V2RayNG: 复制链接后从剪贴板导入, 或扫描二维码</li>
            <li>Shadowrocket / ClashX: 同上, 或用客户端的"添加节点"扫描</li>
            <li>会员退订或运营吊销后此链接与二维码立即失效</li>
          </ul>

          <!-- 协议级凭据 (备用) -->
          <div class="text-sm font-semibold mt-6 mb-2">协议级凭据 (备用)</div>
          <NDescriptions :column="2" size="small" label-placement="left" bordered>
            <NDescriptionsItem label="协议">{{ credential.protocol }}</NDescriptionsItem>
            <NDescriptionsItem label="传输">{{ credential.transport || 'tcp' }}</NDescriptionsItem>
            <NDescriptionsItem label="主机" :span="2">
              <span class="font-mono">{{ credential.serverHost }}:{{ credential.listenPort }}</span>
            </NDescriptionsItem>
            <NDescriptionsItem label="UUID/密码" :span="2">
              <span class="font-mono break-all">{{ credential.clientUuid }}</span>
            </NDescriptionsItem>
            <NDescriptionsItem label="Email" :span="2">
              <span class="font-mono break-all">{{ credential.clientEmail }}</span>
            </NDescriptionsItem>
          </NDescriptions>
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
