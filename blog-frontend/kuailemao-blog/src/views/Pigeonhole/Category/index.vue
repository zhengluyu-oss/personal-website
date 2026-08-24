<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { categoryList } from '@/apis/category'
import { whereArticleList } from '@/apis/article'
import { getArticleList } from '@/apis/home'
import type { WritingArticleItem } from '@/components/Writing/WritingArticleList.vue'
import NotFound from '@/views/NotFound/index.vue'
import { buildCategorySlugEntries, resolveCategorySlug } from '@/utils/category-slug'
import { setSeoMeta } from '@/utils/seo'

interface CategoryItem { id: number; categoryName: string; articleCount?: number }
type BlogArticle = WritingArticleItem

const route = useRoute()
const router = useRouter()
const categories = ref<CategoryItem[]>([])
const articles = ref<BlogArticle[]>([])
const loading = ref(true)
const loadError = ref(false)
const notFound = ref(false)
let requestVersion = 0

const activeSlug = computed(() => String(route.params.slug || ''))
const categoryEntries = computed(() => buildCategorySlugEntries(categories.value))
const activeCategoryEntry = computed(() => categoryEntries.value.find(entry => entry.slug === activeSlug.value))
const activeCategory = computed(() => activeCategoryEntry.value?.category)
const featuredArticle = computed(() => articles.value[0])
const remainingArticles = computed(() => articles.value.slice(1))
const totalArticles = computed(() => categories.value.reduce((sum, item) => sum + Number(item.articleCount || 0), 0))
const activeCategories = computed(() => categories.value.filter(item => Number(item.articleCount) > 0))

function normalizeArticle(item: any): BlogArticle {
  return {
    ...item,
    tags: Array.isArray(item.tags)
      ? item.tags.map((tag: string | { id?: number; tagName?: string }, index: number) =>
          typeof tag === 'string' ? { id: index, tagName: tag } : { id: tag.id ?? index, tagName: tag.tagName || '' },
        ).filter((tag: { tagName: string }) => tag.tagName)
      : [],
  }
}

function excerpt(value?: string, limit = 110) {
  const text = (value || '').replace(/```[\s\S]*?```/g, ' ').replace(/[#>*`~\[\]()_-]/g, ' ').replace(/\s+/g, ' ').trim()
  return text.length > limit ? `${text.slice(0, limit)}…` : text
}
const displayDate = (value?: string) => value?.slice(0, 10).replace(/-/g, '.') || ''
const openArticle = (id: number | string) => router.push(`/blog/articles/${id}`)

async function bootstrap() {
  const version = ++requestVersion
  loading.value = true
  loadError.value = false
  notFound.value = false
  try {
    if (!categories.value.length) {
      const categoryRes = await categoryList()
      if (version !== requestVersion) return
      if (categoryRes.code === 200) categories.value = categoryRes.data || []
    }
    if (activeSlug.value) {
      const entry = resolveCategorySlug(categories.value, activeSlug.value)
      if (!entry) {
        notFound.value = true
        return
      }
      setSeoMeta({
        title: `${entry.category.categoryName} | 陆屿的个人博客`,
        description: `浏览“${entry.category.categoryName}”主题下的技术记录、实践经验与项目复盘。`,
        keywords: `${entry.category.categoryName},技术博客,项目实践,郑陆宇`,
      })
      const res = await whereArticleList(1, String(entry.categoryId))
      if (version !== requestVersion) return
      articles.value = res.code === 200 && Array.isArray(res.data) ? res.data.map(normalizeArticle) : []
    } else {
      const res = await getArticleList(1, 100)
      if (version !== requestVersion) return
      articles.value = (res?.data?.page || []).map(normalizeArticle)
    }
  } catch {
    if (version !== requestVersion) return
    loadError.value = true
  } finally {
    if (version === requestVersion) loading.value = false
  }
}

onMounted(bootstrap)
watch(() => route.params.slug, bootstrap)
</script>

<template>
  <NotFound v-if="notFound" />
  <Main v-else only-father-container>
    <template #content>
      <main class="blog-journal">
          <header class="blog-hero">
            <div class="blog-hero__copy">
              <p class="blog-hero__eyebrow">CODE · NOTES · PRACTICE</p>
              <h1><span>在代码之外，</span>记录思考发生的地方。</h1>
              <p class="blog-hero__intro">技术实践、工具研究与项目复盘。这里收录我在构建产品、解决问题和持续学习过程中留下的完整记录。</p>
            </div>
            <div class="blog-hero__stats" aria-label="博客数据">
              <strong>{{ totalArticles }}</strong><span>篇公开文章</span><small>{{ activeCategories.length }} 个持续更新的主题</small>
            </div>
          </header>

          <nav v-if="categories.length" class="topic-nav" aria-label="文章分类">
            <router-link to="/blog" :class="{ 'is-active': !activeSlug }">全部文章</router-link>
            <router-link v-for="entry in categoryEntries" :key="entry.categoryId" :to="entry.path" :class="{ 'is-active': entry.slug === activeSlug }">
              {{ entry.category.categoryName }}<sup>{{ entry.category.articleCount || 0 }}</sup>
            </router-link>
          </nav>

          <section v-if="loading" class="blog-skeleton" aria-label="文章正在加载"><div /><div /><div /></section>
          <section v-else-if="loadError" class="blog-state">
            <strong>内容暂时没有加载成功</strong><p>请检查网络后重新尝试。</p><button type="button" @click="bootstrap">重新加载</button>
          </section>
          <section v-else-if="featuredArticle" class="journal-content">
            <article class="featured-story" tabindex="0" @click="openArticle(featuredArticle.id)" @keydown.enter="openArticle(featuredArticle.id)">
              <div class="featured-story__cover"><img v-if="featuredArticle.articleCover" :src="featuredArticle.articleCover" :alt="featuredArticle.articleTitle"></div>
              <div class="featured-story__content">
                <div class="story-meta"><span>{{ featuredArticle.categoryName || '近期写作' }}</span><time>{{ displayDate(featuredArticle.createTime) }}</time></div>
                <h2>{{ featuredArticle.articleTitle }}</h2><p>{{ excerpt(featuredArticle.articleContent, 145) }}</p>
                <span class="story-link">阅读全文 <b aria-hidden="true">→</b></span>
              </div>
            </article>

            <div class="section-heading">
              <div><p>LATEST NOTES</p><h2>最近更新</h2></div><router-link to="/blog/archive">按时间浏览全部文章 →</router-link>
            </div>
            <div class="article-grid">
              <article v-for="article in remainingArticles" :key="article.id" class="article-card" tabindex="0" @click="openArticle(article.id)" @keydown.enter="openArticle(article.id)">
                <div class="article-card__cover"><img v-if="article.articleCover" :src="article.articleCover" :alt="article.articleTitle" loading="lazy"></div>
                <div class="article-card__body">
                  <div class="story-meta"><span>{{ article.categoryName || '技术笔记' }}</span><time>{{ displayDate(article.createTime) }}</time></div>
                  <h3>{{ article.articleTitle }}</h3><p>{{ excerpt(article.articleContent) }}</p>
                  <footer><span>{{ article.visitCount || 0 }} 次阅读</span><b aria-hidden="true">↗</b></footer>
                </div>
              </article>
            </div>
          </section>
          <section v-else class="blog-state"><strong>{{ activeCategory ? `“${activeCategory.categoryName}”下暂时没有文章` : '第一篇文章正在路上' }}</strong><p>{{ activeCategory ? '可以浏览其他主题，或稍后再回来看看。' : '这里将用于记录技术实践、项目复盘与持续学习。' }}</p></section>
      </main>
    </template>
  </Main>
</template>

<style scoped lang="scss">
.blog-journal { --journal-blue: #112a4a; --journal-accent: var(--brand-accent); min-height: 72vh; color: var(--brand-ink); }
.blog-hero { position: relative; isolation: isolate; display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: clamp(2.5rem, 7vw, 7.5rem); align-items: center; width: min(calc(100vw - 2rem), 112rem); margin-inline: 50%; padding: clamp(3rem, 5vw, 4.75rem) clamp(2rem, 7vw, 8rem); overflow: hidden; border-radius: 0 0 var(--brand-radius-lg) var(--brand-radius-lg); background: radial-gradient(circle at 86% 8%, rgba(79,135,195,.2), transparent 25rem), linear-gradient(122deg, #0b1c35 0%, #112a4a 72%, #17375d 100%); color: #fff; transform: translateX(-50%); }
.blog-hero::after { content: ""; position: absolute; right: clamp(1.5rem, 5vw, 5rem); bottom: -5rem; z-index: -1; width: 20rem; aspect-ratio: 1; border: 1px solid rgba(255,255,255,.09); border-radius: 50%; box-shadow: 0 0 0 4rem rgba(255,255,255,.018), 0 0 0 8rem rgba(255,255,255,.012); }
.blog-hero__eyebrow { margin: 0 0 1rem; color: #a9bed8; font-family: "Share TechMono", monospace; font-size: .68rem; letter-spacing: .18em; }
.blog-hero h1 { display: flex; flex-wrap: wrap; gap: 0 .22em; margin: 0; max-width: 18ch; color: #c9d8e9; font-size: clamp(2.7rem, 4.35vw, 4.8rem); font-weight: 780; line-height: 1.04; letter-spacing: -.06em; }
.blog-hero h1 span { color: #fff; }
.blog-hero__intro { max-width: 47rem; margin: 1.35rem 0 0; color: #b8c8dc; font-size: clamp(.9rem, 1.15vw, 1.02rem); line-height: 1.75; }
.blog-hero__stats { min-width: 11rem; padding: .4rem 0 .4rem 1.75rem; border-left: 1px solid rgba(255,255,255,.24); }
.blog-hero__stats strong { display: block; font-family: "Share TechMono", monospace; font-size: clamp(3rem, 4.5vw, 4.5rem); font-weight: 500; line-height: .9; }
.blog-hero__stats span, .blog-hero__stats small { display: block; }
.blog-hero__stats span { margin-top: .85rem; font-weight: 700; }
.blog-hero__stats small { margin-top: .4rem; color: #9eb2cb; font-size: .78rem; }
.topic-nav { display: flex; gap: .55rem; overflow-x: auto; padding: 1.35rem max(1.25rem, calc((100vw - 72rem) / 2)); border-bottom: 1px solid var(--brand-line); scrollbar-width: none; }
.topic-nav a { flex: 0 0 auto; padding: .55rem .85rem; border-radius: var(--brand-radius-sm); color: var(--brand-ink-soft); font-size: .82rem; font-weight: 650; text-decoration: none; transition: background .2s ease, color .2s ease; }
.topic-nav a:hover, .topic-nav a.is-active { background: var(--brand-accent-soft); color: var(--brand-accent-strong); }
.topic-nav sup { margin-left: .25rem; color: var(--brand-ink-faint); font-family: "Share TechMono", monospace; }
.journal-content, .blog-state, .blog-skeleton { width: min(calc(100% - 2rem), 72rem); margin-inline: auto; }
.journal-content { padding: clamp(2rem, 5vw, 4.5rem) 0 5rem; }
.featured-story { display: grid; grid-template-columns: minmax(0, 1.15fr) minmax(18rem, .85fr); min-height: 25rem; overflow: hidden; border-radius: var(--brand-radius-lg); background: var(--brand-surface); box-shadow: 0 22px 70px rgba(24,55,91,.12); cursor: pointer; }
.featured-story:focus-visible { outline: 3px solid var(--journal-accent); outline-offset: 4px; }
.featured-story__cover { min-height: 20rem; overflow: hidden; background: var(--brand-canvas-soft); }
.featured-story__cover img { width: 100%; height: 100%; object-fit: cover; transition: transform .6s cubic-bezier(.2,.65,.3,1); }
.featured-story:hover .featured-story__cover img { transform: scale(1.035); }
.featured-story__content { display: flex; flex-direction: column; justify-content: center; padding: clamp(1.5rem, 4vw, 3.25rem); }
.featured-story h2 { margin: 1rem 0 0; font-size: clamp(1.8rem, 3.5vw, 3rem); line-height: 1.16; letter-spacing: -.045em; }
.featured-story p { margin: 1rem 0 0; color: var(--brand-ink-soft); line-height: 1.75; }
.story-meta { display: flex; flex-wrap: wrap; gap: .8rem; color: var(--brand-ink-faint); font-family: "Share TechMono", monospace; font-size: .7rem; }
.story-meta span { color: var(--journal-accent); font-weight: 700; }
.story-link { margin-top: 1.5rem; color: var(--brand-ink); font-weight: 750; }
.story-link b { margin-left: .35rem; color: var(--journal-accent); }
.section-heading { display: flex; align-items: end; justify-content: space-between; gap: 1.5rem; margin: clamp(3.5rem, 7vw, 6rem) 0 1.4rem; }
.section-heading p { margin: 0 0 .4rem; color: var(--brand-ink-faint); font-family: "Share TechMono", monospace; font-size: .68rem; letter-spacing: .13em; }
.section-heading h2 { margin: 0; font-size: clamp(1.8rem, 4vw, 2.8rem); letter-spacing: -.045em; }
.section-heading a { color: var(--brand-ink-soft); font-size: .8rem; font-weight: 700; text-decoration: none; }
.section-heading a:hover { color: var(--journal-accent); }
.article-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 1.25rem; }
.article-card { overflow: hidden; border: 1px solid var(--brand-line); border-radius: var(--brand-radius-lg); background: var(--brand-surface); cursor: pointer; transition: transform .24s ease, border-color .24s ease, box-shadow .24s ease; }
.article-card:hover { transform: translateY(-4px); border-color: rgba(38,94,154,.35); box-shadow: 0 18px 45px rgba(24,55,91,.1); }
.article-card:focus-visible { outline: 3px solid var(--journal-accent); outline-offset: 3px; }
.article-card__cover { aspect-ratio: 16 / 10; overflow: hidden; background: linear-gradient(135deg, #dce7f2, #eef3f8); }
.article-card__cover img { width: 100%; height: 100%; object-fit: cover; transition: transform .45s ease; }
.article-card:hover img { transform: scale(1.04); }
.article-card__body { padding: 1.15rem; }
.article-card h3 { min-height: 2.8em; margin: .8rem 0 0; font-size: 1.08rem; line-height: 1.4; letter-spacing: -.02em; }
.article-card p { min-height: 4.8em; margin: .7rem 0 0; color: var(--brand-ink-soft); font-size: .82rem; line-height: 1.6; }
.article-card footer { display: flex; justify-content: space-between; margin-top: 1.1rem; color: var(--brand-ink-faint); font-size: .72rem; }
.article-card footer b { color: var(--journal-accent); }
.blog-state { margin-block: 4rem; padding: 4rem 1.5rem; text-align: center; border: 1px solid var(--brand-line); border-radius: var(--brand-radius-lg); background: var(--brand-surface); }
.blog-state strong { font-size: 1.3rem; }
.blog-state p { color: var(--brand-ink-soft); }
.blog-state button { margin-top: .5rem; padding: .7rem 1rem; border: 0; border-radius: var(--brand-radius-sm); background: var(--journal-blue); color: #fff; cursor: pointer; }
.blog-skeleton { display: grid; grid-template-columns: repeat(3, 1fr); gap: 1rem; padding-block: 4rem; }
.blog-skeleton div { height: 20rem; border-radius: var(--brand-radius-lg); background: linear-gradient(100deg, var(--brand-canvas-soft) 30%, var(--brand-surface) 50%, var(--brand-canvas-soft) 70%); background-size: 300% 100%; animation: shimmer 1.3s infinite; }
@keyframes shimmer { to { background-position-x: -200%; } }
@media (max-width: 900px) { .article-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } .featured-story { grid-template-columns: 1fr; } .featured-story__cover { aspect-ratio: 16 / 8; min-height: 0; } }
@media (max-width: 640px) { .blog-hero { grid-template-columns: 1fr; gap: 2rem; width: calc(100vw - .75rem); padding: 2.75rem 1.35rem; border-radius: 0 0 var(--brand-radius-lg) var(--brand-radius-lg); } .blog-hero h1 { display: block; max-width: 10ch; font-size: clamp(2.45rem, 12vw, 3.5rem); line-height: 1.06; } .blog-hero h1 span { display: block; } .blog-hero__intro { margin-top: 1.1rem; font-size: .9rem; line-height: 1.7; } .blog-hero__stats { display: grid; grid-template-columns: auto 1fr; column-gap: 1rem; align-items: end; min-width: 0; padding: 1.15rem 0 0; border-top: 1px solid rgba(255,255,255,.2); border-left: 0; } .blog-hero__stats strong { font-size: 2.8rem; } .blog-hero__stats small { grid-column: 2; } .article-grid, .blog-skeleton { grid-template-columns: 1fr; } .section-heading { align-items: flex-start; flex-direction: column; } .featured-story__cover { aspect-ratio: 16 / 10; } .article-card h3, .article-card p { min-height: auto; } }
@media (prefers-reduced-motion: reduce) { .featured-story__cover img, .article-card, .article-card__cover img { transition: none; } .blog-skeleton div { animation: none; } }
</style>
