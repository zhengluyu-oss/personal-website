<script lang="ts" setup>
import {onBeforeUnmount, ref} from 'vue'
import {
  Setting, Promotion, Close
} from '@element-plus/icons-vue'
import SvgIcon from '@/components/SvgIcon/index.vue'
import useUserStore from "@/store/modules/user.ts"
import {logout, oauthExchange} from "@/apis/user"
import {GET_TOKEN, REMOVE_TOKEN, SET_TOKEN} from "@/utils/auth.ts"
import {ElMessage} from "element-plus"
import router from "@/router"

const userStore = useUserStore()
const route = useRoute()
const dialogVisible = ref(false)

onMounted(async () => {
  if (route.query.oauth_code || route.query.oauth_error || route.query.access_token) return
  try {
    await userStore.getInfo();
  } catch (error) {
    console.error("Error defining custom element or getting user info:", error);
  }
})

void thirdLogin()

// 第三方登录
async function thirdLogin() {
  const emailReauth = route.query.email_reauth
  if (emailReauth) {
    const cleanUrl = new URL(window.location.href)
    cleanUrl.searchParams.delete('email_reauth')
    window.history.replaceState(window.history.state, '', cleanUrl.pathname + cleanUrl.search + cleanUrl.hash)
    if (emailReauth === 'complete') ElMessage.success('第三方身份已验证，请回到邮箱设置完成验证码验证')
    else ElMessage.error('第三方身份验证失败，请使用原来绑定的第三方账号重试')
    await router.replace('/account')
    return
  }
  const code = typeof route.query.oauth_code === 'string' ? route.query.oauth_code : undefined
  const error = typeof route.query.oauth_error === 'string' ? route.query.oauth_error : undefined
  const legacyToken = route.query.access_token
  if (!code && !error && !legacyToken) return

  // Remove even an expired/legacy credential before starting any network request.
  const cleanUrl = new URL(window.location.href)
  for (const key of ['oauth_code', 'oauth_error', 'access_token', 'login_type', 'user_name']) {
    cleanUrl.searchParams.delete(key)
  }
  window.history.replaceState(window.history.state, '', cleanUrl.pathname + cleanUrl.search + cleanUrl.hash)

  if (error || legacyToken || !code) {
    ElMessage.error(error === 'account_conflict' ? '该第三方账号与现有账号冲突，请联系管理员' : '第三方登录失败，请重新尝试')
    return
  }
  try {
    if (GET_TOKEN()) {
      ElMessage.warning('当前已登录；如需切换账号，请先退出后重新发起第三方登录')
      await userStore.getInfo()
      return
    }
    const res: any = await oauthExchange(code)
    if (res.code !== 200) {
      ElMessage.error(res.msg || '第三方登录失败，请重新尝试')
      return
    }
    if (res.data?.secondFactorRequired) {
      ElMessage.warning('管理员账号请通过后台登录并完成邮箱验证')
      return
    }
    if (!res.data?.token || !res.data?.expire) {
      ElMessage.error('第三方登录未完成，请重新尝试')
      return
    }
    SET_TOKEN(res.data.token, res.data.expire, true)
    await userStore.getInfo()
    ElMessage.success('登录成功')
    await router.push('/')
  } catch {
    ElMessage.error('第三方登录失败，请重新尝试')
  }
}

const logoutSub = () => {
  logout().then((res: any) => {
    if (res.code === 200) {
      REMOVE_TOKEN()
      userStore.userInfo = undefined
      ElMessage.success('退出登录成功')
      router.push('/')
    }
  })
}

const drawer = ref(false)
const menuTrigger = ref<HTMLButtonElement>()
let removeBreakpointListener = () => {}
onMounted(() => {
  const desktop = window.matchMedia('(min-width: 911px)')
  const closeOnDesktop = () => { if (desktop.matches) drawer.value = false }
  desktop.addEventListener('change', closeOnDesktop)
  removeBreakpointListener = () => desktop.removeEventListener('change', closeOnDesktop)
})
onBeforeUnmount(() => removeBreakpointListener())
function restoreMenuFocus(event?: Event) {
  event?.preventDefault()
  const target = window.matchMedia('(min-width: 911px)').matches ? document.querySelector<HTMLElement>('.menu a') : menuTrigger.value
  target?.focus()
}

</script>
<template>
  <div class="search_dialog_container">
    <!-- 搜索内容 -->
    <el-dialog
        v-model="dialogVisible"
        :show-close="false"
        :close-on-click-modal="false"
        :lock-scroll="true"
    >
      <template #header>
        <div style="display: flex;justify-content: space-between;align-items: center">
          <span style="font-size: 1.2rem">搜索</span>
          <el-button :icon="Close" style="background: none;font-size: 1.5rem;width: 30px;border: none"
                     @click="dialogVisible = false"/>
        </div>
      </template>
      <Search @isShowSearch="dialogVisible = false"/>
    </el-dialog>
  </div>
  <div class="menu">
    <Menu/>
  </div>
  <!-- 移动端 -->
  <div class="move_nav">
    <div class="move_nav__left">
      <button ref="menuTrigger" class="mobile-action" type="button" aria-label="打开导航" :aria-expanded="drawer" aria-controls="mobile-navigation" @click="drawer = true">
        <SvgIcon name="directory_icon" width="30" height="30" color="#409EFF" class="icon"/>
      </button>
      <router-link class="mobile-brand" to="/">陆屿</router-link>
    </div>

    <!-- 搜索按钮 -->
    <div class="right_nav">
      <button type="button" aria-label="搜索" class="search mobile-action" @click="dialogVisible = true">
        <SvgIcon name="search" width="30" height="30" color="#409EFF" class="icon"/>
      </button>
      <div class="user-info">
        <div v-if="userStore.userInfo == undefined">
          <el-tooltip
              class="box-item"
              effect="light"
              content="点击去登录"
              placement="right"
          >
            <RouterLink to="/auth/login" aria-label="登录"><el-avatar>登录</el-avatar></RouterLink>
          </el-tooltip>
        </div>
        <div v-else style="display: flex">
          <el-tooltip
              class="box-item"
              effect="light"
              placement="right"
          >
            <template #content>
              <div class="profile">
                <div style="font-size: 15px;font-weight: bold;color: black">{{
                    userStore.userInfo?.username
                  }}
                </div>
                <div style="font-size: 14px;color: #363636;margin-top: 3px"
                     v-if="userStore.userInfo?.registerType === 0">{{ userStore.userInfo?.email }}
                </div>
                <div style="font-size: 14px;color: #363636;margin-top: 3px" v-else>
                  {{ userStore.userInfo?.registerType === 1 ? 'Gitee登录' : 'Github登录' }}
                </div>
              </div>
            </template>
            <el-dropdown>
              <el-avatar style="margin-right: 3rem"
                         :src="userStore.userInfo?.avatar"></el-avatar>
              <template #dropdown>
                <el-dropdown-item @click="router.push('/account')">
                  <template #default>
                    <el-icon>
                      <Setting/>
                    </el-icon>
                    个人设置
                  </template>
                </el-dropdown-item>
                <!--                  <el-dropdown-item>-->
                <!--                    <template #default>-->
                <!--                      <el-icon>-->
                <!--                        <Collection/>-->
                <!--                      </el-icon>-->
                <!--                      我的收藏-->
                <!--                    </template>-->
                <!--                  </el-dropdown-item>-->
                <el-dropdown-item @click="logoutSub">
                  <template #default>
                    <el-icon>
                      <Promotion/>
                    </el-icon>
                    退出登录
                  </template>
                </el-dropdown-item>
              </template>
            </el-dropdown>
          </el-tooltip>
        </div>
      </div>
    </div>
  </div>
  <div>
    <el-drawer v-model="drawer" :with-header="true" size="min(22rem, calc(100vw - 2rem))" direction="ltr" :show-close="false" @close-auto-focus="restoreMenuFocus">
      <template #header>
        <span style="font-size: 1.2rem">导航</span>
        <el-button aria-label="关闭导航" :icon="Close" style="background: none;font-size: 1.5rem;width: 44px;height: 44px;border: none"
                   @click="drawer = false"/>
      </template>
      <template #default>
        <MoveMenu id="mobile-navigation" @update:closeDrawer="drawer = false"/>
      </template>
    </el-drawer>
  </div>
</template>

<style lang="scss" scoped>

.menu {
  @media screen and (max-width: 910px) {
    display: none;
  }
}

.move_nav {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: calc(58px + env(safe-area-inset-top));
  padding: env(safe-area-inset-top) .75rem 0;
  box-sizing: border-box;
  position: fixed;
  top: 0;
  z-index: 999;
  width: 100vw;
  background: rgba(255, 255, 255, .9);
  border-bottom: 1px solid var(--brand-line);
  backdrop-filter: blur(18px);
  -webkit-backdrop-filter: blur(18px);
  @media screen and (min-width: 911px) {
    display: none;
  }

  .right_nav {
    display: flex;
    align-items: center;
  }
}

.move_nav__left { display:flex; align-items:center; gap:.65rem; }
.mobile-action { display:grid; width:44px; height:44px; place-items:center; padding:0; border:1px solid var(--brand-line); border-radius:.7rem; background:var(--brand-surface-solid); cursor:pointer; }
.mobile-action:focus-visible, .mobile-brand:focus-visible { outline:2px solid var(--brand-accent);outline-offset:3px; }
.move_nav .search { margin-right:.75rem; }
.move_nav :deep(.el-avatar) { margin-right:0!important; }
.mobile-brand { color:var(--brand-ink); font-size:1rem; font-weight:750; text-decoration:none; }

.search {
  display: flex;
  justify-content: center;
  align-items: center;
  margin-right: 20px;
  transition: transform 0.3s linear;
  cursor: pointer;

  &:hover {
    transform: scale(1.1);
  }
}

.search_dialog_container {
  :deep(.el-dialog) {
    overflow: auto;
    border-radius: 10px;
    height: 70%;
  }

  @media screen and (max-width: 650px) {
    :deep(.el-dialog) {
      border-radius: 0;
      margin-top: 0;
      margin-bottom: 0;
      width: 100vw;
      height: 100%;
    }
  }
}

:deep(.el-dialog) {
  // 过渡效果
  transition: all .3s;
  @media (max-width: 1400px) {
    width: 45%;
  }
  @media (max-width: 1000px) {
    width: 60%;
  }
  @media (max-width: 760px) {
    width: 70%;
  }
  @media (max-width: 600px) {
    width: 90%;
  }
}
</style>
