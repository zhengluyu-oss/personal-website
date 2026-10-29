<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { getArticleList, getRecommendArticleList } from '@/apis/home'
import { experienceList, experienceOverview, type ExperienceOverview, type WorkExperienceItem } from '@/apis/experience'
import { PUBLIC_PATHS, routeWithId } from '@/router/paths'
import { articleItems, articlePage, createHomeResource, homeCount, homeExcerpt, loadHomeResource, selectHomeArticles, type HomeArticle, type HomeResource } from '@/utils/home-content'
import HomeMedia from '../HomeMedia.vue'
import { canViewModule } from '@/composables/useSiteModuleAccess'
import { websiteShareList, type WebsiteShareItem } from '@/apis/website-share'
import { knowledgeRequest, type KnowledgeCapability, type KnowledgeEntry, type KnowledgePage } from '@/utils/knowledge-api'
import { diaryReadRequest, type DiaryPage } from '@/utils/diary-api'
import useUserStore from '@/store/modules/user'

const latest = reactive(createHomeResource<ReturnType<typeof articlePage>>())
const recommended = reactive(createHomeResource<HomeArticle[]>())
const work = reactive(createHomeResource<WorkExperienceItem[]>())
const overview = reactive(createHomeResource<ExperienceOverview>())
const shares = reactive(createHomeResource<WebsiteShareItem[]>())
type KnowledgePreview = { entry: KnowledgeEntry; excerpt: string }
const knowledge = reactive(createHomeResource<KnowledgePreview[]>())
type DiaryPreview = { id: string; title: string; createdAt: string }
const diary = reactive(createHomeResource<DiaryPreview[]>())
const user = useUserStore()
const diaryAllowed = computed(() => canViewModule('diary'))
const diaryDenied = ref(false)
const diaryVisible = computed(() => diaryAllowed.value && !diaryDenied.value)
let diarySequence = 0
let diaryTimer: ReturnType<typeof setInterval> | undefined
let active = true
const isActive = () => active
const loadArticles = () => loadHomeResource(latest, async () => articlePage((await getArticleList(1, 6)).data), isActive)
const loadRecommendations = () => loadHomeResource(recommended, async () => articleItems((await getRecommendArticleList()).data), isActive)
const loadWork = () => loadHomeResource(work, async () => {
  const data = (await experienceList()).data
  if (!Array.isArray(data)) throw new Error('Invalid experience response')
  return [...data].sort((a, b) => b.isCurrent - a.isCurrent || String(b.startDate).localeCompare(String(a.startDate)))
}, isActive)
const loadOverview = () => loadHomeResource(overview, async () => {
  const response: any = await experienceOverview()
  const data = response.data
  if (response.code !== 200 || !Number.isInteger(data?.total) || data.total < 0
    || !Array.isArray(data.projectCounts) || data.projectCounts.length !== data.total
    || data.projectCounts.some((count: unknown) => !Number.isInteger(count) || (count as number) < 0)) {
    throw new Error('Invalid experience overview response')
  }
  return data as ExperienceOverview
}, isActive)
const loadShares = () => loadHomeResource(shares, async () => {
  const response: any = await websiteShareList()
  if (response.code !== 200 || !Array.isArray(response.data)) throw new Error('Invalid website shares response')
  return response.data as WebsiteShareItem[]
}, isActive)
const loadKnowledge = () => loadHomeResource(knowledge, async () => {
  const capability = await knowledgeRequest<KnowledgeCapability>('GET', '/view/capabilities')
  if (!capability.allowed || !capability.available) throw new Error(capability.message || '知识库暂不可用')
  const page = await knowledgeRequest<KnowledgePage<KnowledgePreview>>('POST', '/view/search', {
    keyword: null, kind: null, categoryId: null, tagId: null, page: 1, pageSize: 4, deleted: false,
  })
  if (!Array.isArray(page.records)) throw new Error('Invalid knowledge response')
  return page.records
}, isActive)
function clearDiary() {
  diarySequence++
  diary.data = null
  diary.status = 'idle'
  diaryDenied.value = false
}
async function loadDiary(refresh = false) {
  if (!active || !diaryAllowed.value) return
  const sequence = ++diarySequence
  const token = user.token
  if (!refresh) { diary.data = null; diary.status = 'loading' }
  const current = () => active && sequence === diarySequence && diaryAllowed.value && user.token === token
  try {
    const capability = await diaryReadRequest<{ allowed: boolean; available: boolean }>('GET', '/capabilities')
    if (!current()) return
    if (!capability.allowed) {
      diary.data = null; diary.status = 'idle'; diaryDenied.value = true
      return
    }
    diaryDenied.value = false
    if (!capability.available) { diary.data = null; diary.status = 'error'; return }
    const page = await diaryReadRequest<DiaryPage<DiaryPreview>>('POST', '/entries/search', {
      axis: 'created', from: null, to: null, keyword: '', page: 1, pageSize: 3, deleted: false,
    })
    if (!current()) return
    if (!Array.isArray(page.records)) throw new Error('Invalid diary response')
    diary.data = page.records.slice(0, 3).map(item => ({ id: item.id, title: item.title || '无标题日记', createdAt: item.createdAt }))
    diary.status = 'success'
  } catch (error: any) {
    if (!current()) return
    diary.data = null
    if (Number(error?.status) === 401 || Number(error?.status) === 403) diaryDenied.value = true
    diary.status = 'error'
  }
}
watch([diaryAllowed, () => user.token], () => {
  clearDiary()
  if (diaryAllowed.value) void loadDiary()
}, { immediate: true, flush: 'sync' })
const onDiarySessionInvalid = () => { clearDiary(); diaryDenied.value = true }
const refreshDiary = () => { if (diaryAllowed.value) void loadDiary(true) }
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
const knowledgeKind = { NOTE: '笔记', DOCUMENT: '文件资料', CONCEPT: '概念' }
onMounted(() => {
  window.addEventListener('diary-session-invalid', onDiarySessionInvalid)
  window.addEventListener('focus', refreshDiary)
  diaryTimer = setInterval(refreshDiary, 30_000)
  void loadOverview()
  if (canViewModule('blog')) { void loadArticles(); void loadRecommendations() }
  if (canViewModule('experience')) void loadWork()
  if (canViewModule('shares')) void loadShares()
  if (canViewModule('knowledge')) void loadKnowledge()
})
onBeforeUnmount(() => {
  active = false
  clearDiary()
  clearInterval(diaryTimer)
  window.removeEventListener('diary-session-invalid', onDiarySessionInvalid)
  window.removeEventListener('focus', refreshDiary)
})
</script>

<template>
  <div class="home-content">
    <section class="snapshot section-shell" aria-labelledby="snapshot-title">
      <h2 id="snapshot-title" class="sr-only">内容概览</h2>
      <dl>
        <div><dt>公开文章</dt><dd>{{ canViewModule('blog') ? homeCount(latest.status, latest.data?.total) : '需授权' }}</dd></div>
        <div><dt>职业经历</dt><dd>{{ overview.data ? `${overview.data.total} 段` : canViewModule('experience') ? homeCount(work.status, work.data?.length) : '暂不可用' }}</dd></div>
        <div v-if="canViewModule('experience') && current" class="snapshot-current"><dt>当前岗位</dt><dd>{{ current.roleTitle }}</dd></div>
        <div v-else-if="!canViewModule('experience')" class="snapshot-current"><dt>当前岗位</dt><dd>需授权</dd></div>
      </dl>
    </section>

    <section class="selected-work section-shell" aria-labelledby="work-title">
      <header class="section-header">
        <div><h2 id="work-title">工作经历</h2><p>在实际项目中积累开发与协作经验。</p></div>
        <RouterLink class="section-link" :to="PUBLIC_PATHS.experience">查看工作经历 <span aria-hidden="true">→</span></RouterLink>
      </header>
      <div v-if="!canViewModule('experience')" class="restricted-work" role="status">
        <div class="restricted-work__intro">
          <span class="restricted-work__mark">经历目录</span>
          <strong>{{ overview.data ? `${overview.data.total} 段工作经历` : overview.status === 'error' ? '经历数量暂不可用' : '正在读取经历数量' }}</strong>
          <p>这里展示已发布经历的数量。公司、岗位和项目详情需要授权后查看。</p>
        </div>
        <ol v-if="overview.data?.projectCounts.length" class="restricted-work__list">
          <li v-for="(count, index) in overview.data.projectCounts" :key="index">
            <span class="restricted-work__index">{{ String(index + 1).padStart(2, '0') }}</span>
            <span class="restricted-work__label">第 {{ index + 1 }} 段经历<small>内容需授权</small></span>
            <strong>{{ count }} <small>个项目</small></strong>
          </li>
        </ol>
        <p v-else-if="overview.data" class="restricted-work__empty">目前还没有已发布的经历。</p>
        <p v-else-if="overview.status === 'error'" class="restricted-work__empty">数量暂时无法读取。<button type="button" @click="loadOverview">重新加载</button></p>
        <div v-else class="restricted-work__loading" aria-hidden="true"><span /><span /></div>
        <RouterLink class="restricted-work__link" :to="PUBLIC_PATHS.experience">查看访问说明 <span aria-hidden="true">→</span></RouterLink>
      </div>
      <div v-else-if="work.data?.length" class="work-list">
        <article v-for="(item, index) in work.data.slice(0, 2)" :key="item.id" class="work-entry">
          <RouterLink :to="experiencePath(item.id)" class="work-link" :class="{ 'work-link--compact': !summary(item) && !highlights(item).length }">
            <div class="work-role"><h3>{{ item.company }}</h3><p class="company">{{ item.roleTitle }}</p><p class="period">{{ period(item) }}</p><p v-if="overview.data" class="work-project-count">{{ overview.data.projectCounts[index] }} 个项目</p></div>
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
      <template v-if="canViewModule('blog')">
      <div v-if="selection.featuredItems.length" class="featured-grid">
      <article v-for="featured in selection.featuredItems" :key="featured.id" class="featured-story">
        <RouterLink class="featured-link" :to="articlePath(featured.id)">
          <HomeMedia :src="featured.articleCover" :alt="featured.articleTitle + '封面'" />
          <div class="featured-body">
            <div class="article-meta">
              <span>{{ recommended.data?.length ? '精选文章' : '最近发布' }}</span>
              <span v-if="featured.categoryName">{{ featured.categoryName }}</span>
              <time v-if="featured.createTime">{{ featured.createTime.slice(0, 10) }}</time>
            </div>
            <h3>{{ featured.articleTitle }}</h3>
            <p v-if="homeExcerpt(featured.articleContent)">{{ homeExcerpt(featured.articleContent, 140) }}</p>
            <span class="read-link">阅读全文 <span aria-hidden="true">→</span></span>
          </div>
        </RouterLink>
      </article>
      </div>
      <div v-else-if="pending(latest) || pending(recommended)" class="article-skeleton" role="status" aria-label="博客文章加载中"><div /><span /><span /></div>
      <div v-else class="module-empty"><h3>这里将展示博客文章</h3><p>{{ latest.status === 'error' || recommended.status === 'error' ? '内容暂未加载，请稍后重试。' : '新的内容发布后，会出现在这里。' }}</p></div>
      <p v-if="recommended.status === 'error'" class="module-message" role="status">推荐暂未加载<span v-if="selection.featured">，先看看最近的文章。</span><button type="button" @click="loadRecommendations">重试推荐</button></p>
      <h3 v-if="selection.articles.length" class="recent-heading">近期文章</h3>
      <ol v-if="selection.articles.length" class="story-list">
        <li v-for="item in selection.articles" :key="item.id">
          <RouterLink class="story-link" :to="articlePath(item.id)">
            <HomeMedia :src="item.articleCover" :alt="item.articleTitle + '封面'" />
            <div class="story-copy">
              <div class="article-meta"><time v-if="item.createTime">{{ item.createTime.slice(0, 10) }}</time><span v-if="item.categoryName">{{ item.categoryName }}</span></div>
              <h4>{{ item.articleTitle }}<span aria-hidden="true">↗</span></h4>
              <p v-if="item.articleContent">{{ homeExcerpt(item.articleContent, 72) }}</p>
            </div>
          </RouterLink>
        </li>
      </ol>
      <div v-else-if="pending(latest)" class="list-skeleton" role="status" aria-label="最新文章加载中"><span v-for="index in 4" :key="index" /></div>
      <p v-else-if="latest.status === 'success'" class="module-empty">{{ selection.featured ? '更多文章持续更新中。' : '暂时还没有公开文章。' }}</p>
      <p v-if="latest.status === 'error'" class="module-message" role="status">最新文章暂未加载。<button type="button" @click="loadArticles">重试文章</button></p>
      </template>
      <div v-else class="restricted-preview" role="status">
        <div class="article-skeleton" aria-hidden="true"><div /><span /><span /></div>
        <div class="list-skeleton" aria-hidden="true"><span v-for="index in 4" :key="index" /></div>
        <p>博客文章仅向获授权的账号开放。<RouterLink :to="PUBLIC_PATHS.blog">查看访问说明 →</RouterLink></p>
      </div>
    </section>

    <section v-if="diaryVisible" class="home-collection section-shell" aria-labelledby="diary-home-title">
      <header class="section-header">
        <div><h2 id="diary-home-title">我的日记</h2><p>最近写下的生活记录与思考。</p></div>
        <RouterLink class="section-link" to="/diary">查看全部日记 <span aria-hidden="true">→</span></RouterLink>
      </header>
      <div v-if="pending(diary)" class="diary-preview-grid" role="status" aria-label="日记加载中">
        <span v-for="index in 3" :key="index" class="diary-preview-skeleton" aria-hidden="true" />
      </div>
      <ol v-else-if="diary.data?.length" class="diary-preview-grid diary-preview-list">
        <li v-for="item in diary.data" :key="item.id">
          <RouterLink class="diary-preview" to="/diary">
            <time v-if="item.createdAt" :datetime="item.createdAt">{{ item.createdAt.slice(0, 10) }}</time>
            <h3>{{ item.title }} <span aria-hidden="true">↗</span></h3>
            <span class="read-link">阅读日记 <span aria-hidden="true">→</span></span>
          </RouterLink>
        </li>
      </ol>
      <div v-else-if="diary.status === 'success'" class="module-empty">还没有可查看的日记。</div>
      <p v-else-if="diary.status === 'error'" class="module-message" role="status">日记暂未加载。<button type="button" @click="loadDiary()">重新加载</button></p>
    </section>

    <section class="home-collection section-shell" aria-labelledby="shares-home-title">
      <header class="section-header">
        <div><h2 id="shares-home-title">网站分享</h2><p>值得收藏的网站与实用工具。</p></div>
        <RouterLink class="section-link" :to="PUBLIC_PATHS.websiteShares">查看全部网站 <span aria-hidden="true">→</span></RouterLink>
      </header>
      <div v-if="!canViewModule('shares')" class="module-empty">网站分享仅向获授权的账号开放。<RouterLink :to="PUBLIC_PATHS.websiteShares">查看访问说明 →</RouterLink></div>
      <div v-else-if="pending(shares)" class="collection-skeleton" role="status" aria-label="网站分享加载中"><span /><span /></div>
      <div v-else-if="shares.data?.length" class="share-preview-grid">
        <article v-for="item in shares.data.slice(0, 2)" :key="item.id" class="share-preview">
          <RouterLink :to="routeWithId(PUBLIC_PATHS.websiteShareDetail, item.id)" class="share-preview__link">
            <HomeMedia :src="item.coverImage" :alt="item.title + '封面'" empty-text="暂无网站封面" />
            <div class="share-preview__copy"><time v-if="item.createTime">{{ item.createTime.slice(0, 10) }}</time><h3>{{ item.title }} <span aria-hidden="true">↗</span></h3><p>{{ item.summary }}</p></div>
          </RouterLink>
        </article>
      </div>
      <div v-else-if="shares.status === 'success'" class="module-empty"><h3>网站分享正在整理中</h3><p>发布后会在这里展示。</p></div>
      <p v-if="shares.status === 'error'" class="module-message" role="status">网站分享暂未加载。<button type="button" @click="loadShares">重新加载</button></p>
    </section>

    <section class="home-collection home-collection--knowledge section-shell" aria-labelledby="knowledge-home-title">
      <header class="section-header">
        <div><h2 id="knowledge-home-title">知识库</h2><p>按主题整理的笔记、资料和概念。</p></div>
        <RouterLink class="section-link" to="/knowledge">查看知识库 <span aria-hidden="true">→</span></RouterLink>
      </header>
      <div v-if="!canViewModule('knowledge')" class="restricted-work restricted-knowledge" role="status">
        <div class="restricted-work__intro">
          <span class="restricted-work__mark">知识目录</span>
          <strong>笔记、资料与概念</strong>
          <p>知识库仅向获授权的账号开放。获得访问权限后，可以查看条目标题和内容。</p>
        </div>
        <ol class="restricted-work__list">
          <li v-for="(kind, index) in ['笔记', '文件资料', '概念']" :key="kind">
            <span class="restricted-work__index">{{ String(index + 1).padStart(2, '0') }}</span>
            <span class="restricted-work__label">{{ kind }}<small>内容需授权</small></span>
          </li>
        </ol>
        <RouterLink class="restricted-work__link" to="/knowledge">查看访问说明 <span aria-hidden="true">→</span></RouterLink>
      </div>
      <div v-else-if="pending(knowledge)" class="collection-skeleton collection-skeleton--knowledge" role="status" aria-label="知识库加载中"><span v-for="index in 4" :key="index" /></div>
      <div v-else-if="knowledge.data?.length" class="knowledge-preview-grid">
        <RouterLink v-for="item in knowledge.data" :key="item.entry.id" class="knowledge-preview" :to="{ path: '/knowledge', query: { entry: item.entry.id } }">
          <span class="knowledge-preview__kind">{{ knowledgeKind[item.entry.content.kind] }}</span>
          <h3>{{ item.entry.content.title }} <span aria-hidden="true">↗</span></h3>
          <p v-if="homeExcerpt(item.excerpt, 130)">{{ homeExcerpt(item.excerpt, 130) }}</p>
          <time>{{ new Date(item.entry.updatedAt).toLocaleDateString('zh-CN') }}</time>
        </RouterLink>
      </div>
      <div v-else-if="knowledge.status === 'success'" class="module-empty"><h3>知识库正在整理中</h3><p>新增条目后会在这里展示。</p></div>
      <p v-if="knowledge.status === 'error'" class="module-message" role="status">知识库暂未加载。<button type="button" @click="loadKnowledge">重新加载</button></p>
    </section>
  </div>
</template>

<style scoped lang="scss">
.home-content { background: var(--brand-canvas); color: var(--brand-ink); }
.section-shell { width: min(calc(100% - 4rem), 76rem); margin: auto; }
.snapshot {
  position: relative;
  z-index: 2;
  padding: 1.35rem 0 1.1rem;
  border-block: 1px solid var(--brand-line);
  background: var(--brand-canvas);
}
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
.featured-story :deep(img) { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; object-position: center; }
.featured-grid { display: grid; gap: 1.25rem; }
.recent-heading { margin: 2rem 0 -.6rem; font-size: 1rem; color: var(--brand-ink-soft); font-weight: 600; }
.featured-body { display: flex; flex-direction: column; justify-content: center; min-width: 0; padding: 2rem 2.4rem; }
.article-meta { display: flex; flex-wrap: wrap; gap: .5rem 1rem; color: var(--brand-ink-soft); font-size: .85rem; line-height: 1.6; }
.featured-body .article-meta > span { color: var(--brand-accent-strong); font-weight: 600; }
.featured-link h3 { margin: .75rem 0 0; font-size: clamp(1.5rem, 2.2vw, 2rem); font-weight: 680; line-height: 1.4; letter-spacing: -.02em; }
.featured-link p { margin: .9rem 0 0; max-width: 38rem; font-size: 1rem; color: var(--brand-ink-soft); line-height: 1.8; display: -webkit-box; -webkit-line-clamp: 4; -webkit-box-orient: vertical; overflow: hidden; }
.featured-link .read-link { margin-top: 1.35rem; }
.read-link { color: var(--brand-accent-strong); font-size: .9rem; font-weight: 600; }
.read-link span { margin-left: .5rem; }
.story-list { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 2.25rem 1.5rem; margin: 1.75rem 0 0; padding: 0; list-style: none; }
.story-list li { min-width: 0; }
.story-link { display: block; height: 100%; overflow: hidden; border: 1px solid var(--brand-line); border-radius: var(--brand-radius-md); background: var(--brand-surface); text-decoration: none; color: var(--brand-ink); overflow-wrap: anywhere; }
.story-link :deep(.home-media) { aspect-ratio: 16 / 9; border-radius: 0; }
.story-link :deep(.home-media img) { object-fit: cover; }
.story-copy { padding: 1.3rem 1.5rem 1.5rem; }
.story-link h4 { display: flex; align-items: start; justify-content: space-between; gap: 1rem; margin: .55rem 0; font-size: clamp(1.18rem, 1.6vw, 1.4rem); font-weight: 650; line-height: 1.45; }
.story-link h4 span { flex-shrink: 0; color: var(--brand-accent-strong); font-size: 1rem; }
.story-link p { margin: .5rem 0 0; color: var(--brand-ink-soft); font-size: .92rem; line-height: 1.7; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.story-list li:last-child:nth-child(odd) { grid-column: 1 / -1; }
.story-list li:last-child:nth-child(odd) .story-link { display: grid; grid-template-columns: minmax(0, .9fr) minmax(0, 1.1fr); }
.story-list li:last-child:nth-child(odd) .story-link :deep(.home-media) { height: 100%; aspect-ratio: auto; }
.story-list li:last-child:nth-child(odd) .story-copy { align-self: center; padding: 1.7rem 2rem; }
.story-link:hover h4, .featured-link:hover h3, .work-link:hover h3 { color: var(--brand-accent-strong); }
.writing { border-top: 1px solid var(--brand-line); }
.work-list { display: grid; gap: 1rem; }
.work-entry { background: var(--brand-surface); border: 1px solid var(--brand-line); border-radius: var(--brand-radius-md); }
.work-link { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1.3fr); gap: 2rem; padding: 1.6rem 1.75rem; color: inherit; text-decoration: none; border-radius: inherit; overflow-wrap: anywhere; }
.company { margin: 0 0 .5rem; font-size: 1rem; color: var(--brand-ink-soft); line-height: 1.65; }
.work-role h3 { margin: 0; font-size: 1.4rem; font-weight: 650; line-height: 1.5; }
.work-role .company { margin-top: .4rem; }
.work-link--compact { grid-template-columns: 1fr; gap: .3rem; }
.period { margin: .65rem 0 0; font-size: .9rem; color: var(--brand-ink-soft); }
.work-project-count { display: inline-block; margin: .8rem 0 0; padding: .28rem .7rem; border: 1px solid var(--brand-line); border-radius: 999px; color: var(--brand-accent-strong); font-size: .84rem; font-weight: 650; }
.work-summary { margin: 0 0 .6rem; font-size: 1rem; line-height: 1.75; }
.work-evidence ul { padding-left: 1.1rem; margin: .6rem 0; color: var(--brand-ink-soft); font-size: .95rem; line-height: 1.8; list-style: disc; }
.work-evidence .read-link { display: inline-block; margin-top: .6rem; }
.work-tech { margin: .75rem 0; color: var(--brand-ink-soft); font-size: .85rem; line-height: 1.75; }
.home-collection { padding: 2.5rem 0; border-top: 1px solid var(--brand-line); }
.home-collection--knowledge { padding-bottom: 3.5rem; }
.share-preview-grid, .knowledge-preview-grid, .collection-skeleton { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 1.5rem; }
.diary-preview-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 1rem; }
.diary-preview-list { margin: 0; padding: 0; list-style: none; }
.diary-preview-list li { min-width: 0; }
.diary-preview { display: flex; flex-direction: column; min-height: 11rem; height: 100%; padding: 1.35rem 1.5rem; border: 1px solid var(--brand-line); border-radius: var(--brand-radius-md); background: var(--brand-surface); color: var(--brand-ink); text-decoration: none; overflow-wrap: anywhere; }
.diary-preview time { color: var(--brand-ink-soft); font-size: .85rem; }
.diary-preview h3 { display: flex; justify-content: space-between; gap: 1rem; margin: .7rem 0 1rem; font-size: 1.2rem; line-height: 1.5; }
.diary-preview h3 span { flex-shrink: 0; color: var(--brand-accent-strong); font-size: 1rem; }
.diary-preview .read-link { margin-top: auto; }
.diary-preview:hover h3 { color: var(--brand-accent-strong); }
.diary-preview-skeleton { height: 11rem; border-radius: var(--brand-radius-md); background: var(--brand-canvas-soft); }
.share-preview, .knowledge-preview { min-width: 0; overflow: hidden; border: 1px solid var(--brand-line); border-radius: var(--brand-radius-md); background: var(--brand-surface); }
.share-preview__link, .knowledge-preview { display: block; height: 100%; color: var(--brand-ink); text-decoration: none; }
.share-preview__link :deep(.home-media) { border-radius: 0; aspect-ratio: 16 / 9; }
.share-preview__link :deep(.home-media img) { object-fit: cover; }
.share-preview__copy { padding: 1.3rem 1.5rem 1.5rem; }
.share-preview time, .knowledge-preview time { color: var(--brand-ink-soft); font-size: .82rem; }
.share-preview h3, .knowledge-preview h3 { display: flex; justify-content: space-between; gap: 1rem; margin: .5rem 0; font-size: 1.3rem; line-height: 1.45; }
.share-preview h3 span, .knowledge-preview h3 span { color: var(--brand-accent-strong); font-size: 1rem; }
.share-preview p, .knowledge-preview p { margin: .5rem 0 0; color: var(--brand-ink-soft); line-height: 1.75; display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; overflow: hidden; }
.knowledge-preview { display: flex; flex-direction: column; min-height: 12rem; padding: 1.5rem; }
.knowledge-preview__kind { color: var(--brand-accent-strong); font-size: .82rem; font-weight: 650; }
.knowledge-preview p { -webkit-line-clamp: 3; }
.knowledge-preview time { display: block; margin-top: auto; padding-top: 1.2rem; }
.share-preview__link:hover h3, .knowledge-preview:hover h3 { color: var(--brand-accent-strong); }
.collection-skeleton span { height: 16rem; border-radius: var(--brand-radius-md); background: var(--brand-canvas-soft); }
.collection-skeleton--knowledge span { height: 10rem; }
.module-empty a { color: var(--brand-accent-strong); font-weight: 600; }
.module-message { display: flex; flex-wrap: wrap; align-items: center; gap: .5rem; margin: 1rem 0 0; padding: .8rem 1rem; background: var(--brand-canvas-soft); border-radius: var(--brand-radius-sm); font-size: .9rem; line-height: 1.6; color: var(--brand-ink-soft); }
.module-message button { padding: .45rem .75rem; border: 1px solid var(--brand-line); border-radius: var(--brand-radius-sm); color: var(--brand-accent-strong); background: var(--brand-surface); font-weight: 600; cursor: pointer; }
.module-empty { padding: 2rem 1rem; color: var(--brand-ink-soft); font-size: 1rem; line-height: 1.8; }
.module-empty h3 { margin: 0; color: var(--brand-ink); font-size: 1.15rem; }
.module-empty p { margin: .6rem 0 0; }
.restricted-preview > p { margin: 1rem 0 0; color: var(--brand-ink-soft); font-size: .95rem; line-height: 1.7; }
.restricted-preview > p a { margin-left: .35rem; color: var(--brand-accent-strong); font-weight: 600; text-decoration: none; }
.restricted-preview > p a:focus-visible { outline: 2px solid var(--brand-accent); outline-offset: 3px; }
.article-skeleton { display: grid; grid-template-columns: 1.2fr 1fr; min-height: 22rem; background: var(--brand-surface); border: 1px solid var(--brand-line); border-radius: var(--brand-radius-md); overflow: hidden; }
.article-skeleton div { background: var(--brand-canvas-soft); }
.article-skeleton span { display: block; width: 70%; height: 1.25rem; margin: 2rem; background: var(--brand-canvas-soft); border-radius: .25rem; }
.list-skeleton { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem 2.5rem; padding-top: 1.75rem; }
.list-skeleton span { height: 5rem; background: var(--brand-canvas-soft); border-radius: var(--brand-radius-sm); }
.work-skeleton { display: grid; gap: 1rem; }
.work-skeleton span { height: 10rem; background: var(--brand-canvas-soft); border-radius: var(--brand-radius-md); }
.restricted-work { overflow: hidden; border: 1px solid var(--brand-line); border-radius: var(--brand-radius-md); background: var(--brand-surface); }
.restricted-work__intro { padding: 1.75rem 2rem 1.5rem; border-bottom: 1px solid var(--brand-line); background: linear-gradient(110deg, var(--brand-canvas-soft), var(--brand-surface) 75%); }
.restricted-work__mark { display: block; margin-bottom: .65rem; color: var(--brand-accent-strong); font-size: .78rem; font-weight: 700; letter-spacing: .12em; }
.restricted-work__intro strong { display: block; font-size: clamp(1.45rem, 2.5vw, 2rem); line-height: 1.3; }
.restricted-work__intro p { max-width: 40rem; margin: .6rem 0 0; color: var(--brand-ink-soft); line-height: 1.7; }
.restricted-work__list { margin: 0; padding: 0 2rem; list-style: none; }
.restricted-work__list li { display: grid; grid-template-columns: 3.5rem minmax(0, 1fr) auto; align-items: center; gap: 1rem; min-height: 5.75rem; border-bottom: 1px solid var(--brand-line); }
.restricted-work__list li:last-child { border-bottom: 0; }
.restricted-work__index { color: var(--brand-accent-strong); font-family: 'Share TechMono', Consolas, monospace; font-size: 1.25rem; }
.restricted-work__label { font-weight: 650; }
.restricted-work__label small { display: block; margin-top: .3rem; color: var(--brand-ink-soft); font-size: .8rem; font-weight: 400; }
.restricted-work__list strong { color: var(--brand-accent-strong); font-size: 1.3rem; white-space: nowrap; }
.restricted-work__list strong small { color: var(--brand-ink-soft); font-size: .78rem; font-weight: 500; }
.restricted-work__empty { margin: 0; padding: 1.5rem 2rem; color: var(--brand-ink-soft); }
.restricted-work__empty button { border: 0; background: transparent; color: var(--brand-accent-strong); font: inherit; font-weight: 650; cursor: pointer; }
.restricted-work__loading { display: grid; gap: .75rem; padding: 1.5rem 2rem; }
.restricted-work__loading span { width: 100%; height: 3rem; border-radius: var(--brand-radius-sm); background: var(--brand-canvas-soft); }
.restricted-work__link { display: inline-flex; align-items: center; gap: .4rem; margin: .5rem 2rem 1.5rem; color: var(--brand-accent-strong); font-size: .9rem; font-weight: 650; text-decoration: none; }
.restricted-knowledge .restricted-work__list li { grid-template-columns: 3.5rem minmax(0, 1fr); min-height: 4.75rem; }
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
  .featured-story :deep(.home-media) { height: auto; min-height: 0; aspect-ratio: 16 / 9; }
  .featured-body { padding: 1.25rem 1.15rem 1.4rem; }
  .featured-link h3 { font-size: 1.45rem; }
  .story-list, .list-skeleton { grid-template-columns: 1fr; column-gap: 0; }
  .story-list { gap: 1.25rem; }
  .story-list li:last-child:nth-child(odd) { grid-column: auto; }
  .story-list li:last-child:nth-child(odd) .story-link { display: block; }
  .story-list li:last-child:nth-child(odd) .story-link :deep(.home-media) { height: auto; aspect-ratio: 16 / 9; }
  .story-copy, .story-list li:last-child:nth-child(odd) .story-copy { padding: 1.1rem 1.15rem 1.25rem; }
  .work-link { grid-template-columns: 1fr; gap: .85rem; padding: 1.25rem; }
  .restricted-work__intro { padding: 1.4rem 1.2rem; }
  .restricted-work__list { padding: 0 1.2rem; }
  .restricted-work__list li { grid-template-columns: 2.5rem minmax(0, 1fr) auto; gap: .45rem; }
  .restricted-work__index { font-size: 1rem; }
  .restricted-knowledge .restricted-work__list li { grid-template-columns: 2.5rem minmax(0, 1fr); }
  .restricted-work__list strong { font-size: 1.1rem; }
  .restricted-work__link { margin-left: 1.2rem; }
  .home-collection { padding: 2rem 0; }
  .home-collection--knowledge { padding-bottom: 2.5rem; }
  .share-preview-grid, .knowledge-preview-grid, .collection-skeleton { grid-template-columns: 1fr; gap: 1rem; }
  .diary-preview-grid { grid-template-columns: 1fr; }
  .diary-preview { min-height: 0; padding: 1.15rem; }
  .share-preview__copy, .knowledge-preview { padding: 1.15rem; }
}
</style>
