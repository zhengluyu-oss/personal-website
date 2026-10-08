<script setup lang="ts">
import { ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { useGet, usePut } from '~/utils/request'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ (event: 'close'): void }>()
interface Policy { key: string; name: string; publicAccess: boolean; roleIds: string[]; revision: number }
interface Role { id: string | number; roleName: string; status: number }
const policies = ref<Policy[]>([])
const options = ref<{ value: string; label: string }[]>([])
const loading = ref(false)
const saving = ref('')
const failed = ref(false)

async function load() {
  loading.value = true
  failed.value = false
  policies.value = []
  try {
    const [policyResponse, roleResponse] = await Promise.all([
      useGet<Policy[]>('/site-modules/back/policies'), useGet<Role[]>('/role/list'),
    ])
    if (policyResponse.code !== 200 || roleResponse.code !== 200 || !policyResponse.data || !roleResponse.data) throw new Error('读取失败')
    policies.value = policyResponse.data.map(item => ({ ...item, roleIds: item.roleIds.map(String) }))
    options.value = roleResponse.data.filter(role => role.status === 0).map(role => ({ value: String(role.id), label: role.roleName }))
  }
  catch { failed.value = true; message.error('模块权限读取失败，请重试') }
  finally { loading.value = false }
}
watch(() => props.open, open => { if (open) void load() })

async function save(policy: Policy) {
  if (!policy.publicAccess && !policy.roleIds.length) { message.warning('请选择至少一个角色'); return }
  saving.value = policy.key
  try {
    const result = await usePut(`/site-modules/back/policies/${policy.key}`, {
      publicAccess: policy.publicAccess,
      roleIds: policy.publicAccess ? [] : policy.roleIds,
      revision: policy.revision,
    })
    if (result.code !== 200) throw new Error(result.msg)
    policy.revision += 1
    if (policy.publicAccess) policy.roleIds = []
    message.success(`${policy.name}的访问权限已保存`)
  }
  catch { message.error('保存失败，配置可能已被修改。请重新打开窗口核对后重试。') }
  finally { saving.value = '' }
}
</script>

<template>
  <a-modal :open="open" title="前台模块访问权限" :footer="null" :width="760" :mask-closable="false" @cancel="emit('close')">
    <p>公开模块允许游客查看；指定角色模块要求登录，拥有任一所选角色即可查看。超级管理员始终可查看。</p>
    <p>用户角色沿用角色管理中的“授权用户”。无权限的用户仍能看到导航入口，但无法读取内容。</p>
    <a-spin :spinning="loading">
      <div v-if="failed"><a-button @click="load">重新读取</a-button></div>
      <div v-for="policy in policies" :key="policy.key" class="module-row">
        <h3>{{ policy.name }}</h3>
        <a-radio-group v-model:value="policy.publicAccess" :disabled="!!saving">
          <a-radio :value="true">公开访问</a-radio>
          <a-radio :value="false">指定角色可见</a-radio>
        </a-radio-group>
        <a-select v-if="!policy.publicAccess" v-model:value="policy.roleIds" mode="multiple" :options="options" :disabled="!!saving" placeholder="选择可查看的角色" class="role-select" />
        <a-button type="primary" :loading="saving === policy.key" :disabled="!!saving && saving !== policy.key" @click="save(policy)">保存</a-button>
      </div>
    </a-spin>
  </a-modal>
</template>

<style scoped>
p { color: #64748b; line-height: 1.7; }
.module-row { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; padding: 18px 0; border-bottom: 1px solid #e8e8e8; }
.module-row h3 { width: 80px; margin: 0; font-size: 14px; }.role-select { flex: 1; min-width: 180px; }
</style>
