<script setup lang="ts">
import {User, Lock} from '@element-plus/icons-vue'
import {reactive, ref, onBeforeUnmount} from "vue";
import {login} from "@/apis/user";
import router from "@/router";
import {SET_TOKEN, REMOVE_TOKEN} from "@/utils/auth";
import http from '@/utils/http';
import {confirmEmailDelivery} from '@/apis/email';
import {ElMessage} from "element-plus";
import useUserStore from "@/store/modules/user.ts";


const formRef = ref();
const env = import.meta.env;

const form = reactive({
  username: '',
  password: '',
  remember: false
})

const rule = {
  username: [
    {required: true, message: '请输入用户名'}
  ],
  password: [
    {required: true, message: '请输入密码'}
  ]
}
const userStore = useUserStore()
const submitting = ref(false)
const challenge = ref<any>()
const verificationCode = ref('')
const resendAfter = ref(0)
const deliveryMessage = ref('')
let timer: ReturnType<typeof setInterval> | undefined
onBeforeUnmount(() => clearInterval(timer))

function setChallenge(data: any) {
  challenge.value = data
  verificationCode.value = ''
  form.password = ''
  resendAfter.value = data.resendAfter ?? 60
  clearInterval(timer)
  timer = setInterval(() => { if (resendAfter.value > 0) resendAfter.value-- }, 1000)
}

async function confirmDelivery(taskId?: string) {
  deliveryMessage.value = '验证码正在发送，请稍候'
  try {
    await confirmEmailDelivery(taskId)
    deliveryMessage.value = `验证码已发送至 ${challenge.value?.maskedEmail || '绑定邮箱'}`
  } catch {
    deliveryMessage.value = '暂未确认验证码送达，请检查邮箱，或稍后重新发送'
  }
}

async function completeLogin(res: any) {
  if (res.code !== 200) { ElMessage.error(res.msg || '登录失败'); return }
  if (res.data?.secondFactorRequired) {
    if (!res.data.challengeId) { ElMessage.error('登录验证未完成，请重新登录'); return }
    setChallenge(res.data)
    await confirmDelivery(res.data.taskId)
    return
  }
  if (!res.data?.token || !res.data?.expire) {
    ElMessage.error('登录未完成，请重新尝试')
    return
  }
  SET_TOKEN(res.data.token, res.data.expire, form.remember)
  try { await userStore.getInfo() }
  catch {
    REMOVE_TOKEN()
    userStore.token = ''
    userStore.userInfo = undefined
    ElMessage.error('无法获取登录信息，请重新登录')
    return
  }
  ElMessage.success('登录成功')
  await router.push('/')
}

async function userLogin() {
  if (submitting.value) return
  submitting.value = true
  try {
    if (challenge.value) {
      if (!/^\d{6}$/.test(verificationCode.value)) { ElMessage.warning('请输入6位邮箱验证码'); return }
      await completeLogin(await http.post('/user/admin-login/verify', {
        challengeId: challenge.value.challengeId, code: verificationCode.value,
      }))
      verificationCode.value = ''
    } else {
      const valid = await formRef.value.validate().catch(() => false)
      if (valid) await completeLogin(await login(form))
    }
  } catch { /* 请求错误由统一拦截器提示；允许重试。 */ }
  finally { submitting.value = false }
}

async function resendCode() {
  if (submitting.value || resendAfter.value > 0) return
  submitting.value = true
  try {
    const res: any = await http.post('/user/admin-login/resend', { challengeId: challenge.value.challengeId })
    if (res.code === 200 && res.data?.challengeId) { setChallenge(res.data); await confirmDelivery(res.data.taskId) }
    else ElMessage.error(res.msg || '验证码发送失败，请重新登录')
  } catch { /* 请求错误由统一拦截器提示。 */ }
  finally { submitting.value = false }
}
</script>

<template>
  <div style="text-align: center;margin: 0 20px">
    <div style="margin-top: 150px">
      <div style="font-size: 25px;font-weight: bold">登录</div>
      <div style="font-size: 14px;color: grey;margin-top: 1rem">用户密码使用键式哈希算法加密，请放心注册</div>
    </div>
    <div style="margin-top: 50px">
      <el-form v-if="!challenge" :model="form" :rules="rule" ref="formRef">
        <el-form-item prop="username">
          <el-input v-model="form.username" maxlength="20" type="text" placeholder="用户名/邮箱">
            <template #prefix>
              <el-icon>
                <User/>
              </el-icon>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" maxlength="20" placeholder="密码" @keyup.enter="userLogin()">
            <template #prefix>
              <el-icon>
                <Lock/>
              </el-icon>
            </template>
          </el-input>
        </el-form-item>
        <el-row>
          <el-col :span="12" style="text-align: left">
            <el-form-item prop="remember">
              <el-checkbox v-model="form.remember" label="记住我"></el-checkbox>
            </el-form-item>
          </el-col>
          <el-col :span="12" style="text-align: right">
            <el-link @click="$router.push('/auth/reset')">忘记密码</el-link>
          </el-col>
        </el-row>
      </el-form>
      <div v-else>
        <p>请完成邮箱验证</p>
        <p>{{ deliveryMessage }}</p>
        <el-input v-model="verificationCode" maxlength="6" inputmode="numeric" autocomplete="one-time-code" placeholder="请输入6位邮箱验证码" @keyup.enter="userLogin()" />
        <el-button :disabled="submitting || resendAfter > 0" @click="resendCode">{{ resendAfter > 0 ? `${resendAfter}秒后重发` : '重新发送' }}</el-button>
        <el-button :disabled="submitting" @click="challenge = undefined; verificationCode = ''; clearInterval(timer)">重新登录</el-button>
      </div>
    </div>
    <div style="margin-top: 30px">
      <el-button style="width: 270px" type="success" plain :loading="submitting" @click="userLogin()">{{ challenge ? '验证并登录' : '立即登录' }}</el-button>
    </div>
    <el-divider>
      <span style="font-size: 13px;color: grey">没有账号</span>
    </el-divider>
    <div>
      <el-button @click="$router.push('/auth/register')" style="width: 270px" type="danger" plain>立即注册</el-button>
    </div>
    <el-divider>
      <span style="font-size: 13px;color: grey">其他方式</span>
    </el-divider>
    <div>
      <div class="other_login">
        <div>
          <el-link :href="env.MODE === 'development' ? env.VITE_SERVE + '/oauth/gitee/render' : env.VITE_SERVE + '/api/oauth/gitee/render'">
            <svg-icon name="gitee" width="20px" height="20px" color="#4E86F1"/>
          </el-link>
        </div>
        <div style="margin-left: 1rem">
          <el-link :href="env.MODE === 'development' ? env.VITE_SERVE + '/oauth/github/render' : env.VITE_SERVE + '/api/oauth/github/render'">
            <svg-icon name="github" width="20px" height="20px" color="#4E86F1"/>
          </el-link>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.other_login{
  display: flex;
  justify-content: center;
  div:hover{
    cursor: pointer;
  }
}
</style>
