<script setup lang="ts">
import useUserStore from '@/store/modules/user'
import { useRoute, useRouter } from 'vue-router'
const userStore = useUserStore()
const route = useRoute()
const router = useRouter()
async function retry() {
  try {
    await userStore.getInfo()
    if (userStore.userInfo && route.path === '/auth/login') await router.push('/')
  } catch { /* 保留可重试状态；请求客户端显示具体错误。 */ }
}
</script>

<template>
  <el-button size="small" :loading="userStore.sessionState === 'loading'" @click="retry">
    {{ userStore.sessionState === 'loading' ? '恢复登录中' : '重试登录状态' }}
  </el-button>
</template>
