<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { MdCatalog, MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/preview.css'
import { getWebsiteShare, type WebsiteShareItem } from '@/apis/website-share'
import { sanitizeRenderedHtml } from '@/utils/sanitize-html'
import { setSeoMeta } from '@/utils/seo'

const route = useRoute()
const item = ref<WebsiteShareItem>()
const loading = ref(true)
const failed = ref(false)
const scrollElement = ref<HTMLElement>()
const hasCatalog = ref(false)
const catalogOpen = ref(false)
let requestVersion = 0
const siteUrl = computed(() => {
  try { const url = new URL(item.value?.siteUrl || ''); return ['http:', 'https:'].includes(url.protocol) ? url.href : '' } catch { return '' }
})
async function load() {
  const version = ++requestVersion
  loading.value = true
  failed.value = false
  hasCatalog.value = false
  catalogOpen.value = false
  item.value = undefined
  try {
    const result: any = await getWebsiteShare(String(route.params.id))
    if (version !== requestVersion) return
    if (result.code !== 200 || !result.data) throw new Error('not found')
    item.value = result.data
    setSeoMeta({ title: result.data.seoTitle || `${result.data.title} | 网站分享`, description: result.data.seoDescription || result.data.summary, keywords: result.data.seoKeywords || '网站分享,在线工具' })
  } catch { if (version === requestVersion) failed.value = true }
  finally { if (version === requestVersion) loading.value = false }
}
watch(() => route.params.id, load, { immediate: true })
onMounted(() => { scrollElement.value = document.documentElement })
onBeforeUnmount(() => { requestVersion++ })
</script>

<template>
  <main class="detail-page"><div class="detail-shell">
    <nav class="crumb" aria-label="面包屑"><router-link to="/website-shares">网站分享</router-link><span>/</span><span>{{ item?.title || '详情' }}</span></nav>
    <div v-if="loading" class="loading" aria-label="正在加载详情" aria-busy="true" />
    <section v-else-if="failed" class="state" role="alert"><h1>暂时无法查看这篇分享</h1><p>内容可能未发布，或网络暂时不可用。</p><button @click="load">重试</button></section>
    <template v-else-if="item">
      <header class="detail-heading"><h1>{{ item.title }}</h1><p>{{ item.summary }}</p><div class="metadata"><time>{{ item.createTime?.slice(0, 10) }}</time><a v-if="siteUrl" :href="siteUrl" target="_blank" rel="noopener noreferrer">访问网站 <span aria-hidden="true">↗</span></a></div></header>
      <img v-if="item.coverImage" class="detail-cover" :src="item.coverImage" :alt="`${item.title} 封面`" />
      <button v-if="hasCatalog" class="catalog-toggle" :aria-expanded="catalogOpen" aria-controls="share-catalog" @click="catalogOpen = !catalogOpen">{{ catalogOpen ? '收起目录' : '查看目录' }}</button>
      <div class="reading" :class="{ 'reading-single': !hasCatalog }"><aside v-if="hasCatalog" id="share-catalog" class="catalog" :class="{ open: catalogOpen }"><strong>目录</strong><MdCatalog editor-id="website-share-detail" :scroll-element="scrollElement" /></aside><article class="body"><MdPreview :key="item.id" editor-id="website-share-detail" :model-value="item.content || ''" :sanitize="sanitizeRenderedHtml" @on-get-catalog="(catalog: unknown[]) => hasCatalog = catalog.length > 0" /></article></div>
    </template>
  </div></main>
</template>

<style scoped>
.detail-page{min-height:100dvh;background:var(--brand-canvas);color:var(--brand-ink);padding:90px 0 70px}.detail-shell{width:min(1160px,calc(100% - 40px));margin:auto}.crumb{display:flex;flex-wrap:wrap;gap:12px;align-items:center;font-size:.85rem;color:var(--brand-ink-soft)}.crumb a{color:var(--brand-accent-strong);min-height:44px;display:inline-flex;align-items:center;text-decoration:none}.detail-heading{max-width:850px;padding:36px 0}.detail-heading h1{font-size:clamp(2rem,4vw,3.2rem);line-height:1.25;overflow-wrap:anywhere;margin:0 0 20px}.detail-heading p{line-height:1.9;color:var(--brand-ink-soft);font-size:1.05rem}.metadata{display:flex;align-items:center;gap:24px;margin-top:22px}.metadata time{color:var(--brand-ink-faint);font-size:.85rem}.metadata a{color:var(--brand-accent-strong);border:1px solid var(--brand-line);background:var(--brand-surface);border-radius:var(--brand-radius-sm);padding:12px 20px;text-decoration:none;white-space:nowrap}.detail-cover{width:100%;max-height:520px;aspect-ratio:16/10;object-fit:contain;background:var(--brand-canvas-soft);border-radius:var(--brand-radius-sm)}.reading{display:grid;grid-template-columns:220px minmax(0,760px);gap:48px;justify-content:center;padding:50px 0}.reading-single{grid-template-columns:minmax(0,760px)}.catalog{position:sticky;top:90px;align-self:start;max-height:calc(100dvh - 110px);overflow:auto;border-left:2px solid var(--brand-accent);padding-left:16px}.catalog strong{display:block;margin-bottom:15px}.body{min-width:0;overflow-wrap:anywhere}.body :deep(.md-editor){background:transparent;color:var(--brand-ink)}.body :deep(.md-editor-preview-wrapper){padding:0}.body :deep(.md-editor-preview){line-height:1.9;color:var(--brand-ink)}.body :deep(img){max-width:100%;height:auto}.body :deep(pre),.body :deep(table){max-width:100%;overflow:auto}.catalog-toggle{display:none}.state{padding:50px 0}.state p{color:var(--brand-ink-soft)}button{background:var(--brand-surface);color:var(--brand-accent-strong);border:1px solid var(--brand-line);padding:12px 18px;border-radius:var(--brand-radius-sm);cursor:pointer}.loading{height:400px;margin-top:32px;background:var(--brand-canvas-soft);border-radius:var(--brand-radius-sm)}a:focus-visible,button:focus-visible{outline:2px solid var(--brand-accent);outline-offset:4px}@media(max-width:899px){.detail-page{padding-top:70px}.detail-shell{width:calc(100% - 32px)}.detail-heading{padding:24px 0}.reading{display:block;padding:30px 0}.catalog-toggle{display:block;margin-top:24px}.catalog{display:none;position:static;max-height:350px;margin-bottom:30px}.catalog.open{display:block}}
</style>
