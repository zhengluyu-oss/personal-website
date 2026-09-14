<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive } from 'vue'
import { backGetBanners } from '@/apis/website'
import { createHomeResource, firstBanner, loadHomeResource } from '@/utils/home-content'
import HomeMedia from '../HomeMedia.vue'

const banner = reactive(createHomeResource<string>())
let active = true
const load = () => loadHomeResource(banner, async () => firstBanner((await backGetBanners()).data), () => active)
onMounted(load)
onBeforeUnmount(() => { active = false })
</script>

<template>
  <div class="hero-image">
    <HomeMedia :src="banner.data || ''" alt="郑陆宇个人主页介绍图片" eager
      :empty-text="banner.status === 'loading' || banner.status === 'idle' ? '首页图片加载中' : '首页图片暂未显示'" />
    <button v-if="banner.status === 'error'" class="banner-retry" type="button" @click="load">重新获取首页图片</button>
  </div>
</template>

<style scoped lang="scss">
.hero-image { min-width: 0; }
.banner-retry { margin-top: .75rem; padding: .5rem .75rem; border: 1px solid var(--brand-line); border-radius: var(--brand-radius-sm); background: var(--brand-surface); color: var(--brand-accent-strong); cursor: pointer; }
.banner-retry:focus-visible { outline: 2px solid var(--brand-accent); outline-offset: 3px; }
</style>
