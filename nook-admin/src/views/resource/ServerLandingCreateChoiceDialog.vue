<script setup lang="ts">
import { NModal, NCard, NIcon, NTag } from 'naive-ui'
import { Rocket, Server, Clock } from 'lucide-vue-next'

/**
 * 落地机新增 — 方式选择对话框
 *
 * 两种方式 (跟 ServerLandingProvisionModeEnum 对齐):
 *   - 自部署 (provisionMode=1): backend 跑装机脚本起 dante SOCKS5 + 落 install 子表
 *   - 第三方 (provisionMode=2): 已有 socks5 服务录入凭据; 当前未实现, 标 Coming Soon
 */

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'choose-self-deploy'): void
}>()

function pickSelfDeploy() {
  emit('update:modelValue', false)
  emit('choose-self-deploy')
}
</script>

<template>
  <NModal
    :show="props.modelValue"
    preset="card"
    title="新建落地机 — 选择方式"
    :style="{ width: '720px' }"
    :mask-closable="true"
    :on-close="() => emit('update:modelValue', false)"
    @update:show="(v: boolean) => emit('update:modelValue', v)"
  >
    <div class="text-sm text-zinc-500 mb-4">
      根据落地机来源选对应方式; 自部署会跑装机脚本搭一套 dante SOCKS5; 第三方录入已有 socks5 凭据.
    </div>

    <div class="grid grid-cols-2 gap-4">
      <!-- 自部署卡片 -->
      <NCard
        hoverable
        :bordered="true"
        size="small"
        class="cursor-pointer transition-shadow hover:shadow-md"
        @click="pickSelfDeploy"
      >
        <div class="flex items-start gap-3">
          <NIcon size="28" color="#2080f0">
            <Rocket />
          </NIcon>
          <div class="flex-1">
            <div class="font-medium flex items-center gap-2">
              自部署 SOCKS5
              <NTag size="small" type="success" :bordered="false">推荐</NTag>
            </div>
            <div class="text-xs text-zinc-500 mt-1">
              我有一台空 VPS, 后台 SSH 跑装机脚本 + dante + UFW 一键起服务, 装机产物落表持久化.
            </div>
            <ul class="text-xs text-zinc-400 mt-2 list-disc pl-4 space-y-0.5">
              <li>backend SSH 远端跑 install-dante-landing.sh</li>
              <li>装机事实 (install_dir/log_path/...) 自动落库</li>
              <li>支持 status / 日志 / 重启 / 改限速 一系列运维</li>
            </ul>
          </div>
        </div>
      </NCard>

      <!-- 第三方卡片 (Coming Soon, 禁用) -->
      <NCard
        :bordered="true"
        size="small"
        class="cursor-not-allowed opacity-60"
      >
        <div class="flex items-start gap-3">
          <NIcon size="28" color="#909399">
            <Server />
          </NIcon>
          <div class="flex-1">
            <div class="font-medium flex items-center gap-2">
              第三方 SOCKS5
              <NTag size="small" type="warning" :bordered="false">
                <template #icon><NIcon><Clock /></NIcon></template>
                Coming Soon
              </NTag>
            </div>
            <div class="text-xs text-zinc-500 mt-1">
              我已有第三方 socks5 服务, 只录凭据进池. (功能后期实现)
            </div>
            <ul class="text-xs text-zinc-400 mt-2 list-disc pl-4 space-y-0.5">
              <li>不跑装机脚本</li>
              <li>仅录 host/port/user/pass</li>
              <li>无装机记录 (运维操作受限)</li>
            </ul>
          </div>
        </div>
      </NCard>
    </div>
  </NModal>
</template>
