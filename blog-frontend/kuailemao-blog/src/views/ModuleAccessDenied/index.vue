<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { moduleForPath } from '@/utils/site-module-access'
import { GET_TOKEN } from '@/utils/auth'

const route = useRoute()
const router = useRouter()
const unavailable = computed(() => route.query.unavailable === '1')
const signedIn = computed(() => Boolean(GET_TOKEN()))
const target = computed(() => {
  const value = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
  return value.startsWith('/') && !value.startsWith('//') && !/[\\\u0000-\u001f]/.test(value) && moduleForPath(value.split(/[?#]/)[0]) ? value : '/'
})
const retry = () => router.replace(target.value)
</script>

<template>
  <main class="module-denied">
    <h1>{{ unavailable ? '暂时无法确认访问权限' : '暂无查看权限' }}</h1>
    <p>{{ unavailable ? '权限服务暂不可用，请稍后重试。' : signedIn ? '你的账号尚未获准查看此模块，请联系管理员分配角色。' : '此模块仅向获授权的角色开放，请先登录。' }}</p>
    <div>
      <RouterLink v-if="!signedIn" to="/auth/login">登录账号</RouterLink>
      <button type="button" @click="retry">重新检查权限</button>
    </div>
    <p class="hint">你也可以通过上方导航浏览其他模块。</p>
  </main>
</template>

<style scoped>
.module-denied { min-height: 60vh; max-width: 42rem; margin: auto; padding: 9rem 1.5rem 4rem; color: var(--brand-ink); }
h1 { font-size: clamp(1.8rem, 4vw, 2.8rem); } p { line-height: 1.8; color: var(--brand-ink-soft); }
.module-denied > div { display: flex; flex-wrap: wrap; gap: 1rem; margin: 2rem 0; }
a, button { display: inline-flex; align-items: center; min-height: 44px; padding: .7rem 1.2rem; border: 1px solid var(--brand-line); border-radius: var(--brand-radius-sm); background: var(--brand-surface); color: var(--brand-accent-strong); font: inherit; cursor: pointer; text-decoration: none; }
a:focus-visible, button:focus-visible { outline: 2px solid var(--brand-accent); outline-offset: 4px; }.hint { font-size: .9rem; }
</style>
