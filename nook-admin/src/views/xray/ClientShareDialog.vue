<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Copy, Share2 } from 'lucide-vue-next'
import { useToast } from '@/composables/useToast'
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

const toast = useToast()
const copied = ref(false)
/**
 * 列表接口下发的 clientUuid 是 mask 形式 (xxx***xxxx), 不能直接用来拼 URI;
 * 打开 dialog 时按 id 拉一次专用 reveal 接口拿明文凭据。
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
      credential.value = null
      return
    }
    copied.value = false
    loading.value = true
    try {
      credential.value = await getClientCredential(id as string)
    } catch (e) {
      // request 拦截器已 toast; 这里再 console 帮助定位
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
    toast.success('已复制订阅链接')
    setTimeout(() => (copied.value = false), 2000)
  } catch (e) {
    console.warn('[share] clipboard 写入失败, 请手动复制', e)
    toast.warning('浏览器拒绝写剪贴板, 请手动选中复制')
  }
}

function close() {
  emit('update:modelValue', false)
}
</script>

<template>
  <dialog class="modal" :class="{ 'modal-open': modelValue }">
    <div class="modal-box max-w-2xl">
      <h3 class="text-lg font-semibold flex items-center gap-2 mb-1">
        <Share2 class="w-5 h-5 text-primary" />
        分享给客户端
      </h3>
      <p v-if="client" class="text-xs text-base-content/50 mb-4">
        {{ client.clientEmail }} · {{ client.protocol }}
      </p>

      <div v-if="loading" class="flex items-center gap-2 text-sm text-base-content/60 py-12 justify-center">
        <span class="loading loading-spinner loading-sm"></span>
        加载凭据中...
      </div>
      <div v-else-if="!client" class="text-sm text-base-content/60 py-12 text-center">未指定 client</div>
      <div v-else-if="!credential" class="alert alert-error text-sm">加载凭据失败, 请关闭重试</div>
      <div v-else-if="!credential.serverHost" class="alert alert-warning text-sm">
        关联的 resource_server 不存在或已被删, 无法生成订阅链接
      </div>
      <div v-else-if="!uri" class="alert alert-error text-sm">
        无法生成订阅链接 — 协议 "{{ credential.protocol }}" 暂不支持, 或 listenPort 缺失
      </div>
      <template v-else>
        <!-- 订阅链接文本框 + 复制 -->
        <div class="text-sm font-semibold text-base-content/70 mb-2">订阅链接</div>
        <div class="join w-full">
          <textarea
            class="textarea textarea-bordered font-mono text-xs w-full join-item resize-none"
            rows="3"
            :value="uri"
            readonly
            @click="($event.target as HTMLTextAreaElement).select()"
          />
        </div>
        <div class="mt-2 flex justify-end">
          <button class="btn btn-primary btn-sm gap-1" @click="onCopy">
            <Copy class="w-4 h-4" />
            {{ copied ? '已复制' : '复制' }}
          </button>
        </div>

        <!-- 使用说明 -->
        <div class="text-sm font-semibold text-base-content/70 mt-6 mb-2">使用说明</div>
        <ul class="text-xs text-base-content/70 list-disc pl-5 space-y-1">
          <li>v2rayN / NekoBox / V2RayNG: 复制后从剪贴板导入</li>
          <li>Shadowrocket / ClashX: 同上, 或用客户端的"添加节点"扫描</li>
          <li>会员退订或运营吊销后此链接立即失效</li>
        </ul>

        <!-- 协议级凭据展示 (备用, 让运营在客户端弹错时人肉对账; 这里是明文 UUID) -->
        <div class="text-sm font-semibold text-base-content/70 mt-6 mb-2">协议级凭据 (备用)</div>
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-2 text-xs">
          <div><span class="text-base-content/50">协议:</span> {{ credential.protocol }}</div>
          <div><span class="text-base-content/50">传输:</span> {{ credential.transport || 'tcp' }}</div>
          <div class="sm:col-span-2"><span class="text-base-content/50">主机:</span>
            <span class="font-mono">{{ credential.serverHost }}:{{ credential.listenPort }}</span>
          </div>
          <div class="sm:col-span-2 break-all">
            <span class="text-base-content/50">UUID/密码:</span>
            <span class="font-mono">{{ credential.clientUuid }}</span>
          </div>
          <div class="sm:col-span-2 break-all">
            <span class="text-base-content/50">Email:</span>
            <span class="font-mono">{{ credential.clientEmail }}</span>
          </div>
        </div>
      </template>

      <div class="modal-action mt-6">
        <button class="btn btn-ghost btn-sm" @click="close">关闭</button>
      </div>
    </div>
    <div class="modal-backdrop bg-black/40" @click="close"></div>
  </dialog>
</template>
