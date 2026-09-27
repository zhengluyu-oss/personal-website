<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { websiteShareList, type WebsiteShareItem } from '@/apis/website-share'
import { setSeoMeta } from '@/utils/seo'

const items = ref<WebsiteShareItem[]>([])
const loading = ref(true)
const failed = ref(false)
async function load() {
  loading.value = true
  failed.value = false
  try {
    const result: any = await websiteShareList()
    if (result.code !== 200 || !Array.isArray(result.data)) throw new Error('load failed')
    items.value = result.data
  } catch { failed.value = true }
  finally { loading.value = false }
}
onMounted(() => {
  setSeoMeta({ title: '网站分享 | 陆屿', description: '记录值得收藏的网站，分享具体用途、使用方法和体验。', keywords: '网站分享,实用网站,在线工具' })
  void load()
})
</script>

<template>
  <main class="share-page"><div class="share-shell">
    <header class="share-heading"><h1>网站分享</h1><p>值得收藏的网站，以及它们能帮你做什么。</p></header>
    <div v-if="loading" class="loading" aria-label="正在加载网站分享" aria-busy="true"><div/><div/></div>
    <section v-else-if="failed" class="state" role="alert"><h2>暂时无法加载</h2><p>请稍后重试。</p><button @click="load">重新加载</button></section>
    <section v-else-if="!items.length" class="state"><h2>正在整理值得分享的网站</h2><p>发布后会显示在这里。</p></section>
    <div v-else class="shares">
      <article v-for="item in items" :key="item.id" class="share">
        <router-link v-if="item.coverImage" :to="`/website-shares/${item.id}`" class="cover" tabindex="-1" aria-hidden="true"><img :src="item.coverImage" alt="" loading="lazy" /></router-link>
        <div class="share-copy"><time>{{ item.createTime?.slice(0, 10) }}</time><h2><router-link :to="`/website-shares/${item.id}`">{{ item.title }}</router-link></h2><p>{{ item.summary }}</p><router-link class="read" :to="`/website-shares/${item.id}`">阅读详细介绍 <span aria-hidden="true">→</span></router-link></div>
      </article>
    </div>
  </div></main>
</template>

<style scoped>
.share-page{min-height:100dvh;background:var(--brand-canvas);color:var(--brand-ink);padding:100px 0 80px}.share-shell{width:min(1100px,calc(100% - 40px));margin:auto}.share-heading{padding:32px 0 48px}.share-heading h1{font-size:clamp(2rem,4vw,3rem);letter-spacing:-.04em;margin:0 0 18px}.share-heading p,.share-copy p,.state p{color:var(--brand-ink-soft);line-height:1.9}.shares{display:grid;gap:40px}.share{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:40px;padding-bottom:40px;border-bottom:1px solid var(--brand-line)}.cover{display:block;aspect-ratio:16/10;overflow:hidden;border-radius:var(--brand-radius-sm);background:var(--brand-canvas-soft)}.cover img{width:100%;height:100%;object-fit:cover}.share-copy{align-self:center}.share-copy time{font-size:.8rem;color:var(--brand-ink-faint)}.share-copy h2{font-size:1.8rem;margin:14px 0}.share-copy h2 a{color:inherit;text-decoration:none}.read{display:inline-flex;align-items:center;gap:14px;color:var(--brand-accent-strong);min-height:44px;text-decoration:none;font-weight:600}.share-copy p{margin-bottom:18px}.state{padding:40px 0}.state button{background:var(--brand-surface);color:var(--brand-accent-strong);border:1px solid var(--brand-line);padding:12px 20px;border-radius:var(--brand-radius-sm);cursor:pointer}.loading{display:grid;grid-template-columns:1fr 1fr;gap:40px}.loading div{height:280px;border-radius:var(--brand-radius-sm);background:var(--brand-canvas-soft)}a:focus-visible,button:focus-visible{outline:2px solid var(--brand-accent);outline-offset:4px}@media(max-width:767px){.share-page{padding-top:70px}.share-shell{width:calc(100% - 32px)}.share-heading{padding:26px 0 30px}.share{grid-template-columns:1fr;gap:20px}.share-copy h2{font-size:1.5rem}.loading{grid-template-columns:1fr}.loading div+div{height:120px}}
</style>
