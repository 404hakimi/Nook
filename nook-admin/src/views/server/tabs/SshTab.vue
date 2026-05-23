<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import {
  Copy,
  Edit3,
  Eye,
  EyeOff,
  KeyRound,
  Timer,
  Wifi
} from 'lucide-vue-next'
import {
  NButton,
  NCard,
  NDescriptions,
  NDescriptionsItem,
  NIcon,
  NSpin,
  NTag,
  useMessage
} from 'naive-ui'
import {
  getServerCredential,
  getServerDetail,
  type ResourceServer,
  type ServerCredential
} from '@/api/resource/server'
import { testServerConnectivity, type ConnectivityTestResult } from '@/api/xray/server'
import ServerCredentialEditDialog from '@/views/server/dialogs/ServerCredentialEditDialog.vue'

const props = defineProps<{ serverId: string }>()

const message = useMessage()

const detail = ref<ResourceServer | null>(null)
const credential = ref<ServerCredential | null>(null)
const loading = ref(false)
const showPassword = ref(false)
const editOpen = ref(false)

const testing = ref(false)
const testResult = ref<ConnectivityTestResult | null>(null)

async function load() {
  if (!props.serverId) return
  loading.value = true
  try {
    const [d, c] = await Promise.all([
      getServerDetail(props.serverId),
      getServerCredential(props.serverId)
    ])
    detail.value = d
    credential.value = c
  } catch { /* */ } finally {
    loading.value = false
  }
}

onMounted(load)
watch(() => props.serverId, load)

async function onTest() {
  testing.value = true
  testResult.value = null
  try {
    testResult.value = await testServerConnectivity(props.serverId)
    if (testResult.value.success) {
      message.success(`连通 ✓ (${testResult.value.elapsedMs}ms)`)
    } else {
      message.error(`连通失败: ${testResult.value.error || '未知'}`)
    }
  } catch { /* */ } finally {
    testing.value = false
  }
}

function maskedPassword(pw?: string) {
  if (!pw) return '—'
  return showPassword.value ? pw : '•'.repeat(Math.min(pw.length, 16))
}

async function copyText(text: string | undefined, label: string) {
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    message.success(`${label} 已复制`)
  } catch {
    message.error('复制失败')
  }
}

function afterEdit() { load() }
</script>

<template>
  <NSpin :show="loading">
    <div v-if="credential" class="space-y-3">

      <!-- 操作栏 -->
      <div class="action-bar">
        <NButton size="small" type="primary" @click="editOpen = true">
          <template #icon><NIcon><Edit3 :size="14" /></NIcon></template>
          编辑 SSH 凭据
        </NButton>
        <NButton size="small" type="info" :loading="testing" @click="onTest">
          <template #icon><NIcon><Wifi :size="14" /></NIcon></template>
          测试 SSH 连通性
        </NButton>
        <NTag v-if="testResult" size="small" :type="testResult.success ? 'success' : 'error'">
          {{ testResult.success ? `✓ 通 (${testResult.elapsedMs}ms)` : `✗ ${testResult.error || '失败'}` }}
        </NTag>
      </div>

      <!-- === Section 1: 登录凭据 (单列, 便于复制) === -->
      <NCard size="small" :bordered="false" class="info-section">
        <template #header>
          <div class="section-header">
            <NIcon class="section-icon"><KeyRound :size="14" /></NIcon>
            <span>登录凭据</span>
            <NTag size="tiny" type="warning" class="ml-1">敏感信息</NTag>
            <span v-if="detail?.lifecycleState === 'LIVE'" class="text-xs text-orange-500 ml-2">⚠ LIVE 后 host/port 已硬锁</span>
          </div>
        </template>
        <NDescriptions bordered size="small" label-placement="left" :column="1" label-style="width: 6rem">
          <NDescriptionsItem label="Host">
            <div class="cred-row">
              <code class="kbd">{{ credential.host }}</code>
              <NButton text size="tiny" @click="copyText(credential.host, 'Host')" title="复制">
                <template #icon><NIcon><Copy :size="12" /></NIcon></template>
              </NButton>
            </div>
          </NDescriptionsItem>
          <NDescriptionsItem label="端口">
            <div class="cred-row">
              <code class="kbd">{{ credential.sshPort ?? 22 }}</code>
              <NButton text size="tiny" @click="copyText(String(credential.sshPort ?? 22), '端口')" title="复制">
                <template #icon><NIcon><Copy :size="12" /></NIcon></template>
              </NButton>
            </div>
          </NDescriptionsItem>
          <NDescriptionsItem label="用户名">
            <div class="cred-row">
              <code class="kbd">{{ credential.sshUser || '—' }}</code>
              <NButton v-if="credential.sshUser" text size="tiny" @click="copyText(credential.sshUser, '用户名')" title="复制">
                <template #icon><NIcon><Copy :size="12" /></NIcon></template>
              </NButton>
            </div>
          </NDescriptionsItem>
          <NDescriptionsItem label="密码">
            <div class="cred-row">
              <code class="kbd password-mask">{{ maskedPassword(credential.sshPassword) }}</code>
              <NButton v-if="credential.sshPassword" text size="tiny" @click="showPassword = !showPassword">
                <template #icon>
                  <NIcon><EyeOff v-if="showPassword" :size="12" /><Eye v-else :size="12" /></NIcon>
                </template>
                {{ showPassword ? '隐藏' : '显示' }}
              </NButton>
              <NButton v-if="credential.sshPassword" text size="tiny" @click="copyText(credential.sshPassword, '密码')" title="复制">
                <template #icon><NIcon><Copy :size="12" /></NIcon></template>
              </NButton>
            </div>
          </NDescriptionsItem>
          <NDescriptionsItem label="SSH 命令">
            <code class="kbd full-cmd">ssh {{ credential.sshUser || 'root' }}@{{ credential.host }}{{ (credential.sshPort && credential.sshPort !== 22) ? ` -p ${credential.sshPort}` : '' }}</code>
          </NDescriptionsItem>
        </NDescriptions>
      </NCard>

      <!-- === Section 2: 超时参数 === -->
      <NCard size="small" :bordered="false" class="info-section">
        <template #header>
          <div class="section-header">
            <NIcon class="section-icon"><Timer :size="14" /></NIcon>
            <span>SSH 超时参数</span>
            <span class="text-xs text-zinc-400 ml-1">默认值, 部署/升级 dialog 可一次性 override</span>
          </div>
        </template>
        <NDescriptions bordered size="small" label-placement="left" :column="2" label-style="width: 10rem">
          <NDescriptionsItem label="握手超时">
            <span class="num">{{ credential.sshTimeoutSeconds ?? '—' }}</span> <span class="unit">秒</span>
          </NDescriptionsItem>
          <NDescriptionsItem label="单条命令超时">
            <span class="num">{{ credential.sshOpTimeoutSeconds ?? '—' }}</span> <span class="unit">秒</span>
          </NDescriptionsItem>
          <NDescriptionsItem label="SCP 上传超时">
            <span class="num">{{ credential.sshUploadTimeoutSeconds ?? '—' }}</span> <span class="unit">秒</span>
          </NDescriptionsItem>
          <NDescriptionsItem label="装机整体超时">
            <span class="num">{{ credential.installTimeoutSeconds ?? '—' }}</span> <span class="unit">秒</span>
          </NDescriptionsItem>
        </NDescriptions>
      </NCard>

    </div>

    <ServerCredentialEditDialog
      v-model="editOpen"
      :server-id="serverId"
      :lifecycle-state="detail?.lifecycleState"
      @saved="afterEdit"
    />
  </NSpin>
</template>

<style scoped>
/* 字体 / 数值 / 段头 走 main.scss 全局 tokens */

.cred-row {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.password-mask {
  letter-spacing: 1px;
}
.full-cmd {
  display: inline-block;
  word-break: break-all;
}
</style>
