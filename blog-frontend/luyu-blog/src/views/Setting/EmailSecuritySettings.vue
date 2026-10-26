<script setup lang="ts">
import { ElMessage } from 'element-plus'
import http from '@/utils/http'
import useUserStore from '@/store/modules/user'
import { confirmEmailDelivery, EmailDeliveryError } from '@/apis/email'
import { REMOVE_TOKEN } from '@/utils/auth'

const userStore = useUserStore()
const form = reactive({ email: '', password: '', code: '', oldCode: '' })
const busy = ref(false)
const deliveryController = new AbortController()
const deliveryMessage = ref('')
const savedKey = 'email-change-challenge-v1'
type Challenge = { challengeId: string; email: string; expiresAt: number; oldEmailRequired: boolean; maskedOldEmail: string; provider: number; userId: string }
const challenge = ref<Challenge>()
const now = ref(Date.now())
const remaining = computed(() => Math.max(0, Math.ceil(((challenge.value?.expiresAt || 0) - now.value) / 1000)))
let timer: ReturnType<typeof setInterval> | undefined
function reset() {
  challenge.value = undefined
  form.password = ''; form.code = ''; form.oldCode = ''
  sessionStorage.removeItem(savedKey)
}
onMounted(() => {
  try {
    const saved = JSON.parse(sessionStorage.getItem(savedKey) || 'null')
    if (saved && /^[0-9a-f]{64}$/.test(saved.challengeId) && saved.expiresAt > Date.now()) {
      challenge.value = saved; form.email = saved.email
    } else reset()
  } catch { reset() }
  timer = setInterval(() => { now.value = Date.now(); if (challenge.value && remaining.value === 0) reset() }, 1000)
})
onBeforeUnmount(() => { deliveryController.abort(); if (timer) clearInterval(timer) })
watch(() => userStore.userInfo?.username, id => {
  if (id && challenge.value && String(id) !== challenge.value.userId) reset()
})
async function start() {
  busy.value = true
  try {
    const result: any = await http.post('/user/auth/email-change/start', { email: form.email, password: form.password })
    if (result.code !== 200) { ElMessage.error(result.msg || '无法发起安全验证'); return }
    challenge.value = { ...result.data, email: form.email, expiresAt: Date.now() + result.data.expiresIn * 1000, userId: String(userStore.userInfo?.username) }
    sessionStorage.setItem(savedKey, JSON.stringify(challenge.value))
    deliveryMessage.value = '验证码正在发送，请稍候'
    await confirmEmailDelivery(result.data?.taskId, deliveryController.signal)
    deliveryMessage.value = '验证码已发送，请查收邮件'
    ElMessage.success('验证码已发送，请在五分钟内完成全部验证')
  } catch (error) {
    if (deliveryController.signal.aborted) return
    if (error instanceof EmailDeliveryError && ['FAILED', 'EXPIRED'].includes(error.status)) reset()
    deliveryMessage.value = error instanceof Error ? error.message : '暂时无法发起验证，请稍后重试'
    ElMessage.error(deliveryMessage.value)
  }
  finally { form.password = ''; busy.value = false }
}
async function reauthenticate() {
  if (!challenge.value) return
  busy.value = true
  try {
    const result: any = await http.post('/oauth/reauth/start', { challengeId: challenge.value.challengeId })
    if (result.code !== 200) { ElMessage.error(result.msg || '身份验证无法发起'); return }
    const url = new URL(result.data)
    if (url.protocol !== 'https:' || !['github.com', 'gitee.com'].includes(url.hostname)) throw new Error('Invalid provider')
    window.location.assign(url.href)
  } catch { ElMessage.error('身份验证无法发起，请重新尝试') }
  finally { busy.value = false }
}
async function complete() {
  if (!challenge.value) return
  busy.value = true
  try {
    const result: any = await http.post('/user/auth/update/email', { challengeId: challenge.value.challengeId, email: challenge.value.email, code: form.code, oldCode: form.oldCode })
    if (result.code !== 200) { ElMessage.error(result.msg || '验证失败，请检查验证码或重新发起'); return }
    reset(); REMOVE_TOKEN(); userStore.userInfo = undefined; userStore.token = ''
    ElMessage.success('邮箱已更新，原登录状态已失效，请重新登录')
    window.location.assign('/auth/login')
  } catch { ElMessage.error('验证未完成；若登录已失效，请重新登录后再发起') }
  finally { busy.value = false; form.code = ''; form.oldCode = '' }
}
</script>

<template>
  <el-form label-position="top" class="mx-6 mt-5" @submit.prevent>
    <el-alert type="info" :closable="false" class="mb-4" title="修改邮箱需要验证原有身份。管理员及已绑定邮箱的第三方账号还需验证原邮箱；原邮箱不可用时，请联系站点管理员恢复。" />
    <el-form-item label="新邮箱">
      <el-input v-model="form.email" type="email" :disabled="!!challenge" maxlength="254" autocomplete="email" />
    </el-form-item>
    <template v-if="!challenge">
      <el-form-item v-if="userStore.userInfo?.registerType === 0" label="当前密码">
        <el-input v-model="form.password" type="password" show-password autocomplete="current-password" maxlength="128" />
      </el-form-item>
      <el-button type="success" :loading="busy" :disabled="!userStore.userInfo || !form.email" @click="start">发起安全验证</el-button>
    </template>
    <template v-else>
      <p v-if="deliveryMessage" class="mb-4" role="status">{{ deliveryMessage }}</p>
      <p class="mb-4">验证剩余 {{ remaining }} 秒。最多允许 5 次失败，过期后请重新发起。</p>
      <el-form-item v-if="challenge.provider !== 0" label="原第三方身份">
        <el-button :loading="busy" @click="reauthenticate">重新验证 {{ challenge.provider === 1 ? 'Gitee' : 'GitHub' }} 账号</el-button>
      </el-form-item>
      <el-form-item v-if="challenge.oldEmailRequired" :label="`原邮箱验证码（${challenge.maskedOldEmail}）`">
        <el-input v-model="form.oldCode" maxlength="6" inputmode="numeric" autocomplete="off" />
      </el-form-item>
      <el-form-item label="新邮箱验证码">
        <el-input v-model="form.code" maxlength="6" inputmode="numeric" autocomplete="one-time-code" />
      </el-form-item>
      <el-button type="success" :loading="busy" @click="complete">完成验证并更新邮箱</el-button>
      <el-button :disabled="busy" @click="reset">取消 / 重新发起</el-button>
    </template>
  </el-form>
</template>
