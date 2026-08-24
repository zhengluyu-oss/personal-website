<script setup lang="ts">
import { PUBLIC_PATHS } from '@/router/paths'
</script>

<template>
  <main class="not-found">
    <p>404 · PAGE NOT FOUND</p>
    <h1>这里没有你要找的页面</h1>
    <span>地址可能已经失效，请从首页重新开始浏览。</span>
    <router-link :to="PUBLIC_PATHS.home">返回首页</router-link>
  </main>
</template>

<style scoped>
.not-found { min-height: 100vh; display: grid; place-content: center; gap: 1rem; padding: 2rem; text-align: center; color: #172033; background: #f6f7fb; }
.not-found p { margin: 0; color: #4f6ef7; font: 700 .75rem/1.4 ui-monospace, monospace; letter-spacing: .18em; }
.not-found h1 { margin: 0; font-size: clamp(2rem, 7vw, 4.5rem); }
.not-found span { color: #687086; }
.not-found a { justify-self: center; margin-top: .75rem; padding: .75rem 1.25rem; border-radius: 999px; color: white; background: #172033; }
</style>
