<script setup lang="ts">
import {
  LockOutlined,
  MailOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import { delayTimer } from '@v-c/utils'
import { AxiosError } from 'axios'
import { loginApi, resendAdminLoginApi, verifyAdminLoginApi } from '~/api/common/login'
import { getQueryParam } from '~/utils/tools'
import type { LoginMobileParams, LoginParams } from '~@/api/common/login'
import pageBubble from '@/utils/page-bubble'

const message = useMessage()
const notification = useNotification()
const appStore = useAppStore()
const { layoutSetting } = storeToRefs(appStore)
const router = useRouter()
const token = useAuthorization()
const loginModel = reactive({
  username: '',
  password: undefined,
  mobile: undefined,
  code: undefined,
  type: 'account',
  remember: true,
})
const { t } = useI18nLocale()
const formRef = shallowRef()
const resetCounter = 60
const submitLoading = shallowRef(false)
const errorAlert = shallowRef(false)
const challenge = reactive({ id: '', maskedEmail: '', code: '', expiresIn: 0, resendAfter: 0 })
const awaitingSecondFactor = computed(() => Boolean(challenge.id))
let challengeTimer: ReturnType<typeof setInterval> | undefined
const bubbleCanvas = ref<HTMLCanvasElement>()
const { pause } = useInterval(1000, {
  controls: true,
  immediate: false,
  callback(count) {
    if (count) {
      if (count === resetCounter)
        pause()
    }
  },
})
const userStore = useUserStore()
async function submit() {
  submitLoading.value = true
  try {
    await formRef.value?.validate()
    let params: LoginParams | LoginMobileParams

    if (loginModel.type === 'account') {
      params = {
        username: loginModel.username,
        password: loginModel.password,
      } as unknown as LoginParams
    }
    else {
      params = {
        mobile: loginModel.mobile,
        code: loginModel.code,
        type: 'mobile',
      } as unknown as LoginMobileParams
    }
    const { data } = await loginApi(params)
    if (data?.secondFactorRequired && data.challengeId) {
      challenge.id = data.challengeId
      challenge.maskedEmail = data.maskedEmail || ''
      challenge.expiresIn = data.expiresIn || 300
      challenge.resendAfter = data.resendAfter || 60
      startChallengeTimer()
      loginModel.password = undefined
      submitLoading.value = false
      notification.info({ message: '需要邮箱验证', description: `验证码已发送至 ${challenge.maskedEmail}` })
      return
    }
    await finishLogin(data?.token, data?.expire)
  }
  catch (e) {
    handleLoginError(e)
  }
}

function startChallengeTimer() {
  if (challengeTimer) clearInterval(challengeTimer)
  challengeTimer = setInterval(() => {
    if (challenge.resendAfter > 0) challenge.resendAfter--
    if (challenge.expiresIn > 0) challenge.expiresIn--
    if (challenge.expiresIn === 0) {
      clearChallenge()
      message.warning('验证码已过期，请重新登录')
    }
  }, 1000)
}

async function resendSecondFactor() {
  if (!challenge.id || challenge.resendAfter > 0) return
  submitLoading.value = true
  try {
    const { data } = await resendAdminLoginApi({ challengeId: challenge.id })
    if (!data?.challengeId) throw new Error('服务器未返回有效验证请求')
    challenge.id = data.challengeId
    challenge.maskedEmail = data.maskedEmail || challenge.maskedEmail
    challenge.expiresIn = data.expiresIn || 300
    challenge.resendAfter = data.resendAfter || 60
    challenge.code = ''
    startChallengeTimer()
    message.success('验证码已重新发送')
  }
  catch (e) {
    handleLoginError(e)
  }
  finally {
    submitLoading.value = false
  }
}

async function verifySecondFactor() {
  if (!/^\d{6}$/.test(challenge.code)) {
    message.warning('请输入 6 位邮箱验证码')
    return
  }
  submitLoading.value = true
  try {
    const { data } = await verifyAdminLoginApi({ challengeId: challenge.id, code: challenge.code })
    await finishLogin(data?.token, data?.expire)
    clearChallenge()
  }
  catch (e) {
    challenge.code = ''
    handleLoginError(e)
  }
}

async function finishLogin(loginToken?: string, expire?: string) {
    if (!loginToken || !expire) throw new Error('服务器未返回有效登录凭证')
    token.value = JSON.stringify({ token: loginToken, expires: expire })
    notification.success({
      message: '登录成功',
      description: '欢迎回来！',
      duration: 3,
    })
    // 获取当前是否存在重定向的链接，如果存在就走重定向的地址
    const redirect = getQueryParam('redirect', '/')
    // 获取用户信息
    await userStore.getUserInfo()
    // 获取路由菜单的信息
    const currentRoute = await userStore.generateDynamicRoutes()
    router.addRoute(currentRoute)
    await router.push({
      path: redirect,
      replace: true,
    })
    submitLoading.value = false
}

function handleLoginError(e: unknown) {
    notification.error({
      message: `登录失败${e}`,
      description: e instanceof Error ? e.message : '请联系管理员',
      duration: 3,
    })
    if (e instanceof AxiosError)
      errorAlert.value = true

  submitLoading.value = false
}

function clearChallenge() {
  if (challengeTimer) {
    clearInterval(challengeTimer)
    challengeTimer = undefined
  }
  challenge.id = ''
  challenge.maskedEmail = ''
  challenge.code = ''
  challenge.expiresIn = 0
  challenge.resendAfter = 0
}

onMounted(async () => {
  await delayTimer(300)
  pageBubble.init(unref(bubbleCanvas)!)
})

onBeforeUnmount(() => {
  clearChallenge()
  pageBubble.removeListeners()
})
</script>

<template>
  <div class="login-container">
    <div h-screen w-screen absolute z-10>
      <canvas ref="bubbleCanvas" />
    </div>
    <div class="login-content flex-center">
      <div class="ant-pro-form-login-main rounded">
        <!-- 登录头部 -->
        <div
          class="flex-between h-15 px-4 mb-[2px]"
        >
          <div class="flex-end">
            <span class="ant-pro-form-login-title">
              ruyu-blog
            </span>
            <span class="ant-pro-form-login-desc">
              欢迎进入个人博客后台
            </span>
          </div>
          <div class="login-lang flex-center relative z-11">
            <span
              class="flex-center cursor-pointer text-16px"
              @click="appStore.toggleTheme(layoutSetting.theme === 'dark' ? 'light' : 'dark')"
            >
              <!-- 亮色和暗黑模式切换按钮 -->
              <template v-if="layoutSetting.theme === 'light'">
                <carbon-moon />
              </template>
              <template v-else>
                <carbon-sun />
              </template>
            </span>
          </div>
        </div>
        <a-divider m-0 />
        <!-- 登录主体 -->
        <div class="box-border flex min-h-[520px]">
          <!-- 登录框左侧 -->
          <div class="ant-pro-form-login-main-left min-h-[520px] flex-center  bg-[var(--bg-color-container)]">
            <img src="@/assets/images/login-left.png" class="h-5/6 w-5/6">
          </div>
          <a-divider m-0 type="vertical" class="ant-pro-login-divider  min-h-[520px]" />
          <!-- 登录框右侧 -->
          <div class="ant-pro-form-login-main-right px-5 w-[335px] flex-center flex-col relative z-11">
            <div class="text-center py-6 text-2xl">
              {{ awaitingSecondFactor ? '验证管理员邮箱' : '欢迎登录后台系统' }}
            </div>
            <a-form ref="formRef" :model="loginModel">
              <a-tabs v-model:activeKey="loginModel.type" centered>
                <a-tab-pane key="account" :tab="t('pages.login.accountLogin.tab')" />
              </a-tabs>
              <!-- 判断是否存在error -->
              <a-alert
                v-if="errorAlert && loginModel.type === 'account'" mb-24px
                :message="t('pages.login.accountLogin.errorMessage')" type="error" show-icon
              />
              <template v-if="loginModel.type === 'account' && !awaitingSecondFactor">
                <a-form-item name="username" :rules="[{ required: true, message: t('pages.login.username.required') }]">
                  <a-input
                    v-model:value="loginModel.username" allow-clear
                    autocomplete="off"
                    :placeholder="t('pages.login.username.placeholder')" size="large" @press-enter="submit"
                  >
                    <template #prefix>
                      <UserOutlined />
                    </template>
                  </a-input>
                </a-form-item>
                <a-form-item name="password" :rules="[{ required: true, message: t('pages.login.password.required') }]">
                  <a-input-password
                    v-model:value="loginModel.password" allow-clear
                    :placeholder="t('pages.login.password.placeholder')" size="large" autocomplete="current-password"
                    @press-enter="submit"
                  >
                    <template #prefix>
                      <LockOutlined />
                    </template>
                  </a-input-password>
                </a-form-item>
              </template>
              <template v-else-if="awaitingSecondFactor">
                <a-alert class="mb-4" type="info" show-icon :message="`验证码已发送至 ${challenge.maskedEmail}`" />
                <a-form-item>
                  <a-input
                    v-model:value="challenge.code" inputmode="numeric" :maxlength="6" autocomplete="one-time-code"
                    placeholder="请输入 6 位邮箱验证码" size="large" aria-label="邮箱验证码" @press-enter="verifySecondFactor"
                  >
                    <template #prefix><MailOutlined /></template>
                  </a-input>
                </a-form-item>
                <div class="mb-4 flex items-center justify-between text-sm text-[var(--text-color-2)]">
                  <span>{{ Math.ceil(challenge.expiresIn / 60) }} 分钟内有效</span>
                  <a-button type="link" size="small" :disabled="challenge.resendAfter > 0" @click="resendSecondFactor">
                    {{ challenge.resendAfter > 0 ? `${challenge.resendAfter} 秒后重发` : '重新发送' }}
                  </a-button>
                </div>
              </template>
              <a-button type="primary" block :loading="submitLoading" size="large" @click="awaitingSecondFactor ? verifySecondFactor() : submit()">
                {{ awaitingSecondFactor ? '验证并登录' : t('pages.login.submit') }}
              </a-button>
              <a-button v-if="awaitingSecondFactor" class="mt-3" type="link" block @click="clearChallenge">返回重新登录</a-button>
            </a-form>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="less" scoped>
.login-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: auto;
  background: var(--bg-color-container);
}

.login-lang {
  height: 40px;
  line-height: 44px;
}

.login-content {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
}

.ant-pro-form-login-container {
  display: flex;
  flex: 1 1;
  flex-direction: column;
  height: 100%;
  padding: 32px 0;
  overflow: auto;
  background: inherit
}

.ant-pro-form-login-header a {
  text-decoration: none
}

.ant-pro-form-login-title {
  color: var(--text-color);
  font-weight: 600;
  font-size: 33px;
  line-height: 1;
}

.ant-pro-form-login-logo {
  width: 44px;
  height: 44px;
  margin-right: 16px;
  vertical-align: top
}

.ant-pro-form-login-desc {
  color: var(--text-color-1);
  font-size: 14px;
  margin-left: 16px
}

.ant-pro-form-login-main-right {
  .ant-tabs-nav-list {
    margin: 0 auto;
    font-size: 16px;
  }

  .ant-pro-form-login-other {
    line-height: 22px;
    text-align: center
  }

}

.ant-pro-form-login-main {
  box-shadow: var(--c-shadow);
}

.icon {
  margin-left: 8px;
  color: var(--text-color-2);
  font-size: 24px;
  vertical-align: middle;
  cursor: pointer;
  transition: color .3s;

  &:hover {
    color: var(--pro-ant-color-primary);
  }
}

.login-media(@width:100%) {
  .ant-pro-form-login-main {
    width: @width;
  }
  .ant-pro-form-login-main-left {
    display: none;
  }
  .ant-pro-form-login-main-right {
    width: 100%;
  }
  .ant-pro-form-login-desc {
    display: none;
  }
}

@media (min-width: 992px) {
  .ant-pro-form-login-main-left {
    width: 700px;
  }
}

@media (min-width: 768px) and (max-width: 991px) {
  .ant-pro-login-divider {
    display: none;
  }

  .login-media(400px)
}

@media screen and (max-width: 767px) {
  .login-media(350px);

  .ant-pro-login-divider {
    display: none;
  }
}
</style>
