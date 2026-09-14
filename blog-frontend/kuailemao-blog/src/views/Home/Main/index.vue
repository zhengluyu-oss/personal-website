<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive } from 'vue'
import { getArticleList, getRecommendArticleList } from '@/apis/home'
import { experienceList, type WorkExperienceItem } from '@/apis/experience'
import useWebsiteStore from '@/store/modules/website'
import { PUBLIC_PATHS, routeWithId } from '@/router/paths'
import { articleItems, articlePage, createHomeResource, homeCount, homeExcerpt, loadHomeResource, selectHomeArticles, type HomeArticle, type HomeResource } from '@/utils/home-content'
import HomeMedia from '../HomeMedia.vue'

const website = useWebsiteStore()
const latest = reactive(createHomeResource<ReturnType<typeof articlePage>>())
const recommended = reactive(createHomeResource<HomeArticle[]>())
const work = reactive(createHomeResource<WorkExperienceItem[]>())
let active = true
const isActive = () => active
const loadArticles = () => loadHomeResource(latest, async () => articlePage((await getArticleList(1, 6)).data), isActive)
const loadRecommendations = () => loadHomeResource(recommended, async () => articleItems((await getRecommendArticleList()).data), isActive)
const loadWork = () => loadHomeResource(work, async () => {
  const data = (await experienceList()).data
  if (!Array.isArray(data)) throw new Error('Invalid experience response')
  return [...data].sort((a, b) => b.isCurrent - a.isCurrent || String(b.startDate).localeCompare(String(a.startDate)))
}, isActive)
const selection = computed(() => selectHomeArticles(latest.data?.articles || [], recommended.data || []))
const current = computed(() => work.data?.find(item => item.isCurrent === 1))
const pending = (resource: HomeResource<unknown>) => resource.status === 'idle' || resource.status === 'loading'
const lines = (value?: string) => value?.split(/\r?\n/).map(item => item.trim()).filter(Boolean) || []
const summary = (item: WorkExperienceItem) => item.projectSummary || lines(item.highlights)[0] || ''
const highlights = (item: WorkExperienceItem) => [...new Set(lines(item.highlights))].filter(line => line !== summary(item)).slice(0, 3)
const technologies = (item: WorkExperienceItem) => [...new Set((item.techStack || '').split(/[,，/|\n]/).map(value => value.trim()).filter(Boolean))].slice(0, 6)
const month = (value?: string) => value?.slice(0, 7).replace('-', '.') || ''
const period = (item: WorkExperienceItem) => [month(item.startDate), item.isCurrent === 1 ? '至今' : month(item.endDate)].filter(Boolean).join(' / ')
const articlePath = (id: string | number) => routeWithId(PUBLIC_PATHS.article, id)
const experiencePath = (id: string | number) => routeWithId(PUBLIC_PATHS.experienceDetail, id)
const githubUrl = computed(() => {
  const url = website.webInfo?.githubLink?.trim() || ''
  return /^https?:\/\//i.test(url) ? url : ''
})
onMounted(() => { void loadArticles(); void loadRecommendations(); void loadWork() })
onBeforeUnmount(() => { active = false })
</script>

<template>
  <div class="home-content">
    <section class="snapshot section-shell" aria-labelledby="snapshot-title">
      <h2 id="snapshot-title" class="sr-only">内容概览</h2>
      <dl>
        <div><dt>公开文章</dt><dd>{{ homeCount(latest.status, latest.data?.total) }}</dd></div>
        <div><dt>职业经历</dt><dd>{{ homeCount(work.status, work.data?.length) }}</dd></div>
        <div v-if="current" class="snapshot-current"><dt>当前岗位</dt><dd>{{ current.roleTitle }}</dd></div>
      </dl>
    </section>

    <section class="selected-work section-shell" aria-labelledby="work-title">
      <header class="section-header">
        <div><h2 id="work-title">工作经历</h2><p>在实际项目中积累开发与协作经验。</p></div>
        <RouterLink class="section-link" :to="PUBLIC_PATHS.experience">查看工作经历 <span aria-hidden="true">→</span></RouterLink>
      </header>
      <div v-if="work.data?.length" class="work-list">
        <article v-for="item in work.data.slice(0, 2)" :key="item.id" class="work-entry">
          <RouterLink :to="experiencePath(item.id)" class="work-link">
            <div class="work-role"><p class="company">{{ item.company }}</p><h3>{{ item.roleTitle }}</h3><p class="period">{{ period(item) }}</p></div>
            <div class="work-evidence">
              <p v-if="summary(item)" class="work-summary">{{ summary(item) }}</p>
              <ul v-if="highlights(item).length"><li v-for="line in highlights(item)" :key="line">{{ line }}</li></ul>
              <p v-if="technologies(item).length" class="work-tech">{{ technologies(item).join(' / ') }}</p>
              <span class="read-link">查看这段经历 <span aria-hidden="true">→</span></span>
            </div>
          </RouterLink>
        </article>
      </div>
      <div v-else-if="pending(work)" class="work-skeleton" role="status" aria-label="工作经历加载中"><span /><span /></div>
      <p v-else-if="work.status === 'success'" class="module-empty">工作经历正在整理中。</p>
      <p v-if="work.status === 'error'" class="module-message" role="status">工作经历暂未加载。<button type="button" @click="loadWork">重试经历</button></p>
    </section>

    <section class="writing section-shell" aria-labelledby="writing-title">
      <header class="section-header">
        <div><h2 id="writing-title">博客文章</h2><p>最近发布的技术笔记与项目复盘。</p></div>
        <RouterLink class="section-link" :to="PUBLIC_PATHS.blog">查看全部文章 <span aria-hidden="true">→</span></RouterLink>
      </header>
      <article v-if="selection.featured" class="featured-story">
        <RouterLink class="featured-link" :to="articlePath(selection.featured.id)">
          <HomeMedia :key="selection.featured.id" :src="selection.featured.articleCover" :alt="selection.featured.articleTitle + '封面'" />
          <div class="featured-body">
            <div class="article-meta">
              <span>{{ selection.featured.categoryName || '最近发布' }}</span>
              <time v-if="selection.featured.createTime">{{ selection.featured.createTime.slice(0, 10) }}</time>
            </div>
            <h3>{{ selection.featured.articleTitle }}</h3>
            <p v-if="selection.featured.articleContent">{{ homeExcerpt(selection.featured.articleContent, 140) }}</p>
            <span class="read-link">阅读全文 <span aria-hidden="true">→</span></span>
          </div>
        </RouterLink>
      </article>
      <div v-else-if="pending(latest) || pending(recommended)" class="article-skeleton" role="status" aria-label="博客文章加载中"><div /><span /><span /></div>
      <div v-else class="module-empty"><h3>这里将展示博客文章</h3><p>{{ latest.status === 'error' || recommended.status === 'error' ? '内容暂未加载，请稍后重试。' : '新的内容发布后，会出现在这里。' }}</p></div>
      <p v-if="recommended.status === 'error'" class="module-message" role="status">推荐暂未加载<span v-if="selection.featured">，先看看最近的文章。</span><button type="button" @click="loadRecommendations">重试推荐</button></p>
      <ol v-if="selection.articles.length" class="story-list">
        <li v-for="item in selection.articles" :key="item.id">
          <RouterLink class="story-link" :to="articlePath(item.id)">
            <div class="article-meta"><time v-if="item.createTime">{{ item.createTime.slice(0, 10) }}</time><span v-if="item.categoryName">{{ item.categoryName }}</span></div>
            <h4>{{ item.articleTitle }}<span aria-hidden="true">↗</span></h4>
            <p v-if="item.articleContent">{{ homeExcerpt(item.articleContent, 72) }}</p>
          </RouterLink>
        </li>
      </ol>
      <div v-else-if="pending(latest)" class="list-skeleton" role="status" aria-label="最新文章加载中"><span v-for="index in 4" :key="index" /></div>
      <p v-else-if="latest.status === 'success'" class="module-empty">{{ selection.featured ? '更多文章持续更新中。' : '暂时还没有公开文章。' }}</p>
      <p v-if="latest.status === 'error'" class="module-message" role="status">最新文章暂未加载。<button type="button" @click="loadArticles">重试文章</button></p>
    </section>

    <section class="closing section-shell" aria-labelledby="about-home-title">
      <div><h2 id="about-home-title">继续了解郑陆宇</h2><p>查看完整介绍、技术方向与可公开的联系信息。</p></div>
      <nav class="closing-links" aria-label="关于与联系入口">
        <RouterLink class="section-link" :to="PUBLIC_PATHS.about">关于我 <span aria-hidden="true">→</span></RouterLink>
        <a v-if="githubUrl" class="section-link" :href="githubUrl" target="_blank" rel="noopener noreferrer">GitHub <span aria-hidden="true">↗</span></a>
      </nav>
    </section>
  </div>
</template>

<style scoped lang="scss">
.home-content { background: var(--brand-canvas); color: var(--brand-ink); }
.section-shell { width: min(calc(100% - 4rem), 76rem); margin: auto; }
.snapshot { padding: 1.1rem 0; border-block: 1px solid var(--brand-line); }
.snapshot dl { display: flex; flex-wrap: wrap; align-items: baseline; gap: .85rem 3rem; margin: 0; }
.snapshot dl > div { display: flex; align-items: baseline; gap: .85rem; }
.snapshot dt { color: var(--brand-ink-soft); font-size: .9rem; }
.snapshot dd { margin: 0; color: var(--brand-ink); font-size: 1.1rem; font-weight: 650; }
.snapshot .snapshot-current { margin-left: auto; }
.writing, .selected-work { padding: 2.5rem 0; }
.section-header { display: flex; justify-content: space-between; align-items: center; gap: 1rem; margin-bottom: 1.5rem; }
.section-header h2 { margin: 0; font-size: clamp(1.6rem, 2.4vw, 2rem); font-weight: 680; line-height: 1.4; letter-spacing: -.02em; }
.section-header p { margin: .5rem 0 0; font-size: 1rem; color: var(--brand-ink-soft); line-height: 1.7; }
.section-link { flex-shrink: 0; color: var(--brand-accent-strong); font-size: .95rem; font-weight: 600; text-decoration: none; padding: .65rem 0; }
.section-link span { display: inline-block; margin-left: .5rem; }
.featured-story { overflow: hidden; border: 1px solid var(--brand-line); border-radius: var(--brand-radius-md); background: var(--brand-surface); }
.featured-link { display: grid; grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr); min-height: 22rem; color: inherit; text-decoration: none; overflow-wrap: anywhere; }
.featured-story :deep(.home-media) { height: 100%; min-height: 22rem; aspect-ratio: auto; border-radius: 0; }
.featured-story :deep(img) { object-fit: cover; }
.featured-body { display: flex; flex-direction: column; justify-content: center; min-width: 0; padding: 2rem 2.4rem; }
.article-meta { display: flex; flex-wrap: wrap; gap: .5rem 1rem; color: var(--brand-ink-soft); font-size: .85rem; line-height: 1.6; }
.featured-body .article-meta > span { color: var(--brand-accent-strong); font-weight: 600; }
.featured-link h3 { margin: .75rem 0 0; font-size: clamp(1.7rem, 2.8vw, 2.4rem); font-weight: 680; line-height: 1.28; letter-spacing: -.03em; }
.featured-link p { margin: .9rem 0 0; max-width: 38rem; font-size: 1rem; color: var(--brand-ink-soft); line-height: 1.8; }
.featured-link .read-link { margin-top: 1.35rem; }
.read-link { color: var(--brand-accent-strong); font-size: .9rem; font-weight: 600; }
.read-link span { margin-left: .5rem; }
.story-list { display: grid; grid-template-columns: 1fr 1fr; column-gap: 2.5rem; margin: 1.75rem 0 0; padding: 0; list-style: none; }
.story-list li { border-top: 1px solid var(--brand-line); }
.story-link { display: block; padding: 1.15rem 0; text-decoration: none; color: var(--brand-ink); overflow-wrap: anywhere; }
.story-link h4 { display: flex; align-items: start; justify-content: space-between; gap: 1rem; margin: .4rem 0; font-size: 1.18rem; font-weight: 620; line-height: 1.45; }
.story-link h4 span { flex-shrink: 0; color: var(--brand-accent-strong); font-size: 1rem; }
.story-link p { margin: .35rem 0 0; color: var(--brand-ink-soft); font-size: .9rem; line-height: 1.7; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.story-link:hover h4, .featured-link:hover h3, .work-link:hover h3 { color: var(--brand-accent-strong); }
.writing { border-top: 1px solid var(--brand-line); }
.work-list { display: grid; gap: 1rem; }
.work-entry { background: var(--brand-surface); border: 1px solid var(--brand-line); border-radius: var(--brand-radius-md); }
.work-link { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1.3fr); gap: 2rem; padding: 1.6rem 1.75rem; color: inherit; text-decoration: none; border-radius: inherit; overflow-wrap: anywhere; }
.company { margin: 0 0 .5rem; font-size: 1rem; color: var(--brand-ink-soft); line-height: 1.65; }
.work-role h3 { margin: 0; font-size: 1.4rem; font-weight: 650; line-height: 1.5; }
.period { margin: .65rem 0 0; font-size: .9rem; color: var(--brand-ink-soft); }
.work-summary { margin: 0 0 .6rem; font-size: 1rem; line-height: 1.75; }
.work-evidence ul { padding-left: 1.1rem; margin: .6rem 0; color: var(--brand-ink-soft); font-size: .95rem; line-height: 1.8; list-style: disc; }
.work-evidence .read-link { display: inline-block; margin-top: .6rem; }
.work-tech { margin: .75rem 0; color: var(--brand-ink-soft); font-size: .85rem; line-height: 1.75; }
.closing { display: flex; align-items: center; justify-content: space-between; gap: 1.5rem; border-top: 1px solid var(--brand-line); padding: 2rem 0 3rem; }
.closing h2 { margin: 0; font-size: 1.25rem; font-weight: 650; }
.closing p { margin: .5rem 0 0; color: var(--brand-ink-soft); font-size: .95rem; }
.closing-links { display: flex; flex-wrap: wrap; align-items: center; gap: 1rem 1.5rem; }
.module-message { display: flex; flex-wrap: wrap; align-items: center; gap: .5rem; margin: 1rem 0 0; padding: .8rem 1rem; background: var(--brand-canvas-soft); border-radius: var(--brand-radius-sm); font-size: .9rem; line-height: 1.6; color: var(--brand-ink-soft); }
.module-message button { padding: .45rem .75rem; border: 1px solid var(--brand-line); border-radius: var(--brand-radius-sm); color: var(--brand-accent-strong); background: var(--brand-surface); font-weight: 600; cursor: pointer; }
.module-empty { padding: 2rem 1rem; color: var(--brand-ink-soft); font-size: 1rem; line-height: 1.8; }
.module-empty h3 { margin: 0; color: var(--brand-ink); font-size: 1.15rem; }
.module-empty p { margin: .6rem 0 0; }
.article-skeleton { display: grid; grid-template-columns: 1.2fr 1fr; min-height: 22rem; background: var(--brand-surface); border: 1px solid var(--brand-line); border-radius: var(--brand-radius-md); overflow: hidden; }
.article-skeleton div { background: var(--brand-canvas-soft); }
.article-skeleton span { display: block; width: 70%; height: 1.25rem; margin: 2rem; background: var(--brand-canvas-soft); border-radius: .25rem; }
.list-skeleton { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem 2.5rem; padding-top: 1.75rem; }
.list-skeleton span { height: 5rem; background: var(--brand-canvas-soft); border-radius: var(--brand-radius-sm); }
.work-skeleton { display: grid; gap: 1rem; }
.work-skeleton span { height: 10rem; background: var(--brand-canvas-soft); border-radius: var(--brand-radius-md); }
a:focus-visible, button:focus-visible { outline: 2px solid var(--brand-accent); outline-offset: 4px; }
button:hover { background: var(--brand-accent-soft); }
@media (max-width: 767px) {
  .section-shell { width: calc(100% - 2rem); }
  .snapshot dl { gap: .75rem 1.5rem; }
  .snapshot .snapshot-current { width: 100%; margin-left: 0; }
  .snapshot dt { font-size: .85rem; }
  .snapshot dd { font-size: 1rem; }
  .writing, .selected-work { padding: 2rem 0; }
  .section-header { flex-wrap: wrap; align-items: start; gap: .35rem; }
  .section-header p { font-size: .95rem; }
  .featured-link, .article-skeleton { grid-template-columns: 1fr; min-height: 0; }
  .featured-story :deep(.home-media) { min-height: 0; aspect-ratio: 16 / 9; }
  .featured-body { padding: 1.25rem 1.15rem 1.4rem; }
  .featured-link h3 { font-size: 1.45rem; }
  .story-list, .list-skeleton { grid-template-columns: 1fr; column-gap: 0; }
  .work-link { grid-template-columns: 1fr; gap: .85rem; padding: 1.25rem; }
  .closing { flex-wrap: wrap; padding: 1.5rem 0 2rem; }
}
</style>
