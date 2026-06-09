<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { NButton, NCard, NEmpty, NForm, NFormItem, NIcon, NInput, NModal, NSpin, useMessage } from 'naive-ui'
import { Globe2, Plus, RotateCcw, Search } from 'lucide-vue-next'
import { useConfirm } from '@/composables/useConfirm'
import {
  createSystemDomain,
  deleteSystemDomain,
  listSystemDomain,
  updateSystemDomain,
  type SystemDomain,
  type SystemDomainSaveDTO
} from '@/api/system/domain'

const message = useMessage()
const { confirm } = useConfirm()

const list = ref<SystemDomain[]>([])
const loading = ref(false)
const keyword = ref('')

async function load() {
  loading.value = true
  try {
    const all = await listSystemDomain()
    const kw = keyword.value.trim().toLowerCase()
    list.value = kw ? all.filter((d) => d.domain.toLowerCase().includes(kw)) : all
  } catch {
    /* */
  } finally {
    loading.value = false
  }
}
onMounted(load)
function doReset() {
  keyword.value = ''
  load()
}

// ===== 新建 / 编辑 =====
const dialogOpen = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const form = reactive<SystemDomainSaveDTO>({ id: undefined, domain: '', cfZoneId: '', cfApiToken: '', remark: '' })

function resetForm() {
  Object.assign(form, { id: undefined, domain: '', cfZoneId: '', cfApiToken: '', remark: '' })
}
function openCreate() {
  isEdit.value = false
  resetForm()
  dialogOpen.value = true
}
function openEdit(d: SystemDomain) {
  isEdit.value = true
  Object.assign(form, {
    id: d.id, domain: d.domain, cfZoneId: d.cfZoneId ?? '',
    cfApiToken: d.cfApiToken ?? '', remark: d.remark ?? ''
  })
  dialogOpen.value = true
}

async function onSave() {
  if (!form.domain.trim()) {
    message.warning('请填写根域名')
    return
  }
  if (!isApexDomain(form.domain)) {
    message.warning('请填一级域名 (根域如 karsu.cc), 不要带子域名')
    return
  }
  saving.value = true
  try {
    const dto: SystemDomainSaveDTO = {
      id: form.id,
      domain: form.domain.trim(),
      cfZoneId: form.cfZoneId?.trim() || undefined,
      cfApiToken: form.cfApiToken?.trim() || undefined,
      remark: form.remark?.trim() || undefined
    }
    if (isEdit.value) {
      await updateSystemDomain(dto)
      message.success('已保存')
    } else {
      await createSystemDomain(dto)
      message.success('已新建')
    }
    dialogOpen.value = false
    await load()
  } catch {
    /* */
  } finally {
    saving.value = false
  }
}

async function onDelete(d: SystemDomain) {
  const ok = await confirm({
    title: '删除域名',
    message: `删除域名 ${d.domain}? 若已被线路机绑定 (xray_install.domain_id), 删除后该机装机 / 续期会失去域名。`,
    type: 'warning',
    confirmText: '删除'
  })
  if (!ok) return
  try {
    await deleteSystemDomain(d.id)
    message.success('已删除')
    await load()
  } catch {
    /* */
  }
}

/** token 脱敏展示. */
function maskToken(t?: string): string {
  if (!t) return '—'
  return t.length <= 8 ? '••••' : t.slice(0, 4) + '••••' + t.slice(-4)
}

/** 常见复合后缀; 末两段命中时 3 段也算一级域. */
const TWO_PART_TLDS = new Set([
  'com.cn', 'net.cn', 'org.cn', 'gov.cn', 'edu.cn',
  'co.uk', 'org.uk', 'me.uk', 'com.hk', 'com.tw', 'com.au', 'co.jp', 'co.kr'
])
/** 是否一级域名 (apex): 恰 2 段, 或 3 段且末两段是常见复合后缀; 跟后端校验对齐. */
function isApexDomain(d: string): boolean {
  const s = d.trim().replace(/\.+$/, '').toLowerCase()
  if (!/^([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,}$/.test(s)) return false
  const parts = s.split('.')
  if (parts.length === 2) return true
  return parts.length === 3 && TWO_PART_TLDS.has(parts.slice(-2).join('.'))
}
</script>

<template>
  <div class="space-y-3">
    <NCard size="small" :bordered="false">
      <div class="flex items-center gap-2 flex-wrap">
        <NInput v-model:value="keyword" placeholder="域名" size="small" style="width: 220px" clearable @keyup.enter="load" />
        <NButton type="primary" size="small" @click="load">
          <template #icon><NIcon><Search :size="14" /></NIcon></template>
          搜索
        </NButton>
        <NButton size="small" @click="doReset">
          <template #icon><NIcon><RotateCcw :size="14" /></NIcon></template>
          重置
        </NButton>
        <div class="flex-1"></div>
        <NButton type="primary" size="small" @click="openCreate">
          <template #icon><NIcon><Plus :size="14" /></NIcon></template>
          新建域名
        </NButton>
      </div>
    </NCard>

    <NCard size="small" :bordered="false">
      <NSpin :show="loading">
        <NEmpty v-if="!loading && list.length === 0" description="暂无域名, 点右上角新建" class="py-10">
          <template #icon><NIcon :size="28"><Globe2 /></NIcon></template>
        </NEmpty>
        <div v-else class="dl">
          <div v-for="d in list" :key="d.id" class="dl-row">
            <span class="dl-domain font-mono">{{ d.domain }}</span>
            <span class="dl-meta">Zone: {{ d.cfZoneId || '—' }}</span>
            <span class="dl-meta">Token: {{ maskToken(d.cfApiToken) }}</span>
            <span class="dl-remark">{{ d.remark || '' }}</span>
            <span class="flex-1"></span>
            <NButton size="tiny" quaternary @click="openEdit(d)">编辑</NButton>
            <NButton size="tiny" quaternary type="error" @click="onDelete(d)">删除</NButton>
          </div>
        </div>
      </NSpin>
    </NCard>

    <NModal v-model:show="dialogOpen" preset="card" :title="isEdit ? '编辑域名' : '新建域名'" style="width: 480px; max-width: 92vw">
      <NForm label-placement="top" size="small">
        <NFormItem label="根域名 (一级域名)" required>
          <NInput v-model:value="form.domain" placeholder="如 karsu.cc (二级域名在装机时填)" :input-props="{ style: 'font-family: monospace' }" />
        </NFormItem>
        <NFormItem label="Cloudflare Zone ID">
          <NInput v-model:value="form.cfZoneId" placeholder="可空 (装机按域名自动推导)" :input-props="{ style: 'font-family: monospace' }" />
        </NFormItem>
        <NFormItem label="Cloudflare API Token">
          <NInput v-model:value="form.cfApiToken" type="password" show-password-on="click" placeholder="DNS-01 签发/续期用 (Zone:Read + DNS:Edit)" />
        </NFormItem>
        <NFormItem label="备注">
          <NInput v-model:value="form.remark" placeholder="可空" />
        </NFormItem>
      </NForm>
      <template #footer>
        <div class="flex justify-end gap-2">
          <NButton size="small" @click="dialogOpen = false">取消</NButton>
          <NButton type="primary" size="small" :loading="saving" @click="onSave">保存</NButton>
        </div>
      </template>
    </NModal>
  </div>
</template>

<style scoped>
.dl { display: flex; flex-direction: column; gap: 1px; }
.dl-row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 9px 10px;
  border-radius: 6px;
  transition: background 0.12s ease;
}
.dl-row:hover { background: rgba(127, 127, 127, 0.05); }
.dl-row + .dl-row { border-top: 1px solid rgba(127, 127, 127, 0.07); }
.dl-domain { font-size: 13px; font-weight: 600; color: var(--n-text-color-1, #222); min-width: 200px; }
.dl-meta { font-size: 11.5px; color: var(--n-text-color-3, #9ca3af); }
.dl-remark { font-size: 12px; color: var(--n-text-color-3, #9ca3af); }
</style>
