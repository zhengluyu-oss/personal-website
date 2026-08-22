<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getArticleList, getRecommendArticleList } from '@/apis/home'
import { experienceList, type WorkExperienceItem } from '@/apis/experience'

interface ArticleItem { id:number; articleTitle:string; articleContent?:string; articleCover?:string; categoryName?:string; createTime?:string; tags?:string[] }
const router = useRouter()
const loading = ref(true)
const failed = ref(false)
const articles = ref<ArticleItem[]>([])
const recommended = ref<ArticleItem[]>([])
const experiences = ref<WorkExperienceItem[]>([])
const totalArticles = ref(0)

const stripMarkdown = (value?: string) => (value || '').replace(/[*#>`~\[\]()]|\s+/g, ' ').trim()
const excerpt = (value?: string, limit = 78) => { const text = stripMarkdown(value); return text.length > limit ? `${text.slice(0, limit)}…` : text }
const month = (value?: string) => value ? value.slice(0, 7).replace('-', '.') : ''
const period = (item: WorkExperienceItem) => `${month(item.startDate)} 至 ${item.isCurrent === 1 ? '今' : month(item.endDate)}`
const lines = (value?: string) => value?.split(/\r?\n/).map(item => item.trim()).filter(Boolean) || []
const featured = computed(() => recommended.value[0] || articles.value[0])
const current = computed(() => experiences.value.find(item => item.isCurrent === 1) || experiences.value[0])
const technologies = computed(() => [...new Set(experiences.value.flatMap(item => lines(item.techStack)).flatMap(item => item.split(/[,，/|]/)).map(item => item.trim()).filter(Boolean))].slice(0, 12))

onMounted(async () => {
  try {
    const [articleRes, recommendRes, experienceRes]: any[] = await Promise.all([
      getArticleList(1, 6), getRecommendArticleList(), experienceList(),
    ])
    articles.value = articleRes?.data?.page || []
    totalArticles.value = Number(articleRes?.data?.total || articles.value.length)
    recommended.value = recommendRes?.data || []
    experiences.value = [...(experienceRes?.data || [])].sort((a, b) => b.isCurrent - a.isCurrent || new Date(b.startDate).getTime() - new Date(a.startDate).getTime())
  }
  catch { failed.value = true }
  finally { loading.value = false }
})
</script>

<template>
  <div class="home-content">
    <section class="snapshot section-shell" aria-labelledby="snapshot-title">
      <div class="snapshot-statement">
        <h2 id="snapshot-title">代码是手段，<br>交付才是答案。</h2>
        <p>关注真实业务中的复杂度、可靠性与长期维护，也把过程整理成可复用的技术记录。</p>
      </div>
      <dl class="snapshot-data">
        <div><dt>公开文章</dt><dd>{{ totalArticles }}</dd></div>
        <div><dt>职业经历</dt><dd>{{ experiences.length }}</dd></div>
        <div v-if="current"><dt>当前方向</dt><dd>{{ current.roleTitle }}</dd></div>
      </dl>
    </section>

    <section v-if="experiences.length" class="selected-work section-shell" aria-labelledby="work-title">
      <header><h2 id="work-title">真实业务，真实交付。</h2><router-link to="/experience">完整工作经历 <span aria-hidden="true">↗</span></router-link></header>
      <article v-for="(item,index) in experiences.slice(0,2)" :key="item.id" class="work-entry" tabindex="0" @click="router.push(`/experience/${item.id}`)" @keydown.enter="router.push(`/experience/${item.id}`)">
        <div class="work-index">{{ String(index + 1).padStart(2,'0') }}</div>
        <div class="work-role"><p>{{ item.company }}</p><h3>{{ item.roleTitle }}</h3><time>{{ period(item) }}</time></div>
        <div class="work-evidence"><p>{{ item.projectSummary || lines(item.highlights)[0] || '负责业务系统的设计、开发与持续优化。' }}</p><ul v-if="lines(item.highlights).length"><li v-for="line in lines(item.highlights).slice(0,3)" :key="line">{{ line }}</li></ul></div>
        <span class="work-open" aria-hidden="true">↗</span>
      </article>
    </section>

    <section v-if="technologies.length" class="technology section-shell" aria-labelledby="technology-title">
      <div><h2 id="technology-title">Technology<br>Landscape</h2><p>来自真实工作经历中记录的技术，而非主观熟练度评分。</p></div>
      <ol><li v-for="(technology,index) in technologies" :key="technology"><span>{{ String(index + 1).padStart(2,'0') }}</span>{{ technology }}</li></ol>
    </section>

    <section v-if="featured || articles.length" class="writing section-shell" aria-labelledby="writing-title">
      <header><h2 id="writing-title">近期写作</h2><router-link to="/category">查看全部文章 <span aria-hidden="true">→</span></router-link></header>
      <div class="writing-grid">
        <article v-if="featured" class="featured-story" tabindex="0" @click="router.push(`/article/${featured.id}`)" @keydown.enter="router.push(`/article/${featured.id}`)">
          <img v-if="featured.articleCover" :src="featured.articleCover" :alt="featured.articleTitle" loading="lazy">
          <div><p>{{ featured.categoryName || '推荐阅读' }}</p><h3>{{ featured.articleTitle }}</h3><span>{{ excerpt(featured.articleContent) }}</span></div>
        </article>
        <div class="story-list">
          <article v-for="item in articles.filter(article => article.id !== featured?.id).slice(0,4)" :key="item.id" tabindex="0" @click="router.push(`/article/${item.id}`)" @keydown.enter="router.push(`/article/${item.id}`)">
            <time>{{ item.createTime?.slice(0,10) }}</time><div><p>{{ item.categoryName }}</p><h3>{{ item.articleTitle }}</h3></div><span aria-hidden="true">↗</span>
          </article>
        </div>
      </div>
    </section>

    <section v-if="failed && !loading" class="state section-shell"><h2>内容暂时没有连上</h2><p>首页视觉已经就绪，刷新后可以重新读取文章与经历。</p></section>
    <section class="closing section-shell"><p>持续学习，也持续把事情做完。</p><h2>下一段值得解决的问题，<br>正在路上。</h2><nav><router-link to="/about">关于我</router-link><router-link to="/category">阅读博客</router-link></nav></section>
  </div>
</template>

<style scoped lang="scss">
.home-content{--accent:var(--brand-accent);position:relative;z-index:3;background:var(--brand-canvas);color:var(--brand-ink)}.section-shell{width:min(calc(100% - 3rem),88rem);margin:auto}.snapshot{display:grid;grid-template-columns:minmax(0,1.2fr) minmax(28rem,.8fr);gap:clamp(3rem,9vw,10rem);align-items:end;padding:clamp(6rem,11vw,10rem) 0;border-bottom:1px solid var(--brand-line)}.snapshot-statement h2{margin:0;font-size:clamp(3rem,6vw,6.6rem);font-weight:780;line-height:1;letter-spacing:-.07em}.snapshot-statement p{max-width:38rem;margin:2rem 0 0;color:var(--brand-ink-soft);font-size:1rem;line-height:1.8}.snapshot-data{margin:0}.snapshot-data div{display:grid;grid-template-columns:7rem 1fr;align-items:baseline;padding:1.2rem 0;border-top:1px solid var(--brand-line)}.snapshot-data dt{color:var(--brand-ink-faint);font-family:"Share TechMono",monospace;font-size:.72rem}.snapshot-data dd{margin:0;font-size:clamp(1rem,2vw,1.55rem);font-weight:700;line-height:1.35}.selected-work{padding:clamp(6rem,10vw,9rem) 0}.selected-work>header,.writing>header{display:flex;align-items:end;justify-content:space-between;margin-bottom:2.5rem}.selected-work header h2,.writing header h2{max-width:13ch;margin:0;font-size:clamp(2.5rem,5vw,5rem);line-height:1;letter-spacing:-.065em}.selected-work header a,.writing header a{color:var(--brand-ink);font-size:.78rem;font-weight:700;text-decoration:none}.selected-work header a span,.writing header a span{color:var(--accent)}.work-entry{position:relative;display:grid;grid-template-columns:4rem minmax(15rem,.8fr) minmax(18rem,1.2fr);gap:clamp(2rem,6vw,7rem);padding:clamp(2.5rem,5vw,4.5rem) 4rem clamp(2.5rem,5vw,4.5rem) 0;border-top:1px solid var(--brand-line);cursor:pointer;transition:background .22s ease}.work-entry:last-child{border-bottom:1px solid var(--brand-line)}.work-entry:hover,.work-entry:focus-visible{background:var(--brand-accent-soft);outline:none}.work-index{color:var(--accent);font-family:"Share TechMono",monospace;font-size:.72rem}.work-role p{margin:0 0 .6rem;color:var(--brand-ink-soft);font-size:.8rem}.work-role h3{margin:0;font-size:clamp(1.6rem,3vw,2.7rem);line-height:1.08;letter-spacing:-.045em}.work-role time{display:block;margin-top:1rem;color:var(--brand-ink-faint);font-family:"Share TechMono",monospace;font-size:.72rem}.work-evidence>p{margin:0;font-size:clamp(1rem,1.6vw,1.25rem);font-weight:650;line-height:1.65}.work-evidence ul{display:grid;gap:.55rem;margin:1.3rem 0 0;padding:0;list-style:none}.work-evidence li{color:var(--brand-ink-soft);font-size:.84rem;line-height:1.6}.work-evidence li::before{content:'+';margin-right:.7rem;color:var(--accent)}.work-open{position:absolute;right:.25rem;top:50%;color:var(--accent);font-size:1.2rem;transform:translateY(-50%)}.technology{display:grid;grid-template-columns:minmax(18rem,.7fr) minmax(0,1.3fr);gap:clamp(4rem,9vw,10rem);padding:clamp(5rem,9vw,8rem) 0;border-block:1px solid var(--brand-line)}.technology h2{margin:0;font-size:clamp(2.5rem,5vw,5rem);line-height:.95;letter-spacing:-.07em}.technology p{max-width:24rem;margin:1.5rem 0 0;color:var(--brand-ink-soft);line-height:1.75}.technology ol{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));margin:0;padding:0;list-style:none}.technology li{padding:1rem 0;border-top:1px solid var(--brand-line);font-family:"Share TechMono",monospace;font-size:.88rem}.technology li span{display:inline-block;width:3rem;color:var(--brand-ink-faint);font-size:.65rem}.writing{padding:clamp(6rem,10vw,10rem) 0}.writing-grid{display:grid;grid-template-columns:minmax(0,1.15fr) minmax(20rem,.85fr);gap:clamp(2rem,5vw,5rem)}.featured-story{position:relative;min-height:34rem;overflow:hidden;background:var(--brand-canvas-soft);cursor:pointer}.featured-story img{width:100%;height:100%;object-fit:cover;transition:transform .6s cubic-bezier(.2,.65,.3,1)}.featured-story::after{content:'';position:absolute;inset:0;background:linear-gradient(0deg,rgba(8,14,24,.88),rgba(8,14,24,.04) 72%)}.featured-story>div{position:absolute;right:0;bottom:0;left:0;z-index:1;padding:clamp(1.5rem,4vw,3rem);color:#fff}.featured-story p{margin:0;color:#ffb09d;font-size:.7rem;font-weight:700}.featured-story h3{max-width:14ch;margin:.7rem 0;font-size:clamp(2rem,4vw,4rem);line-height:1.03;letter-spacing:-.055em}.featured-story>div>span{display:block;max-width:38rem;color:rgba(255,255,255,.7);line-height:1.7}.featured-story:hover img{transform:scale(1.035)}.story-list{border-top:1px solid var(--brand-line)}.story-list article{display:grid;grid-template-columns:6.5rem 1fr auto;gap:1.2rem;align-items:start;padding:1.5rem 0;border-bottom:1px solid var(--brand-line);cursor:pointer}.story-list article:hover h3{color:var(--accent)}.story-list time,.story-list p{margin:0;color:var(--brand-ink-faint);font-family:"Share TechMono",monospace;font-size:.66rem}.story-list h3{margin:.35rem 0 0;font-size:1.1rem;line-height:1.35;transition:color .2s ease}.story-list article>span{color:var(--accent)}.state{padding:4rem 0;border-top:1px solid var(--brand-line)}.state h2{margin:0}.state p{color:var(--brand-ink-soft)}.closing{display:grid;min-height:70dvh;align-content:center;padding:7rem 0}.closing>p{margin:0 0 1.5rem;color:var(--accent);font-family:"Share TechMono",monospace;font-size:.75rem}.closing h2{max-width:15ch;margin:0;font-size:clamp(3rem,7vw,7rem);line-height:.98;letter-spacing:-.075em}.closing nav{display:flex;gap:1.7rem;margin-top:2.5rem}.closing a{padding-bottom:.3rem;border-bottom:1px solid var(--brand-ink);color:var(--brand-ink);font-size:.82rem;font-weight:700;text-decoration:none}
@media(max-width:800px){.section-shell{width:min(calc(100% - 2rem),44rem)}.snapshot{grid-template-columns:1fr;gap:3rem;padding:5rem 0}.snapshot-statement h2{font-size:clamp(3rem,13vw,5rem)}.snapshot-data div{grid-template-columns:6rem 1fr}.selected-work>header,.writing>header{align-items:flex-start;flex-direction:column;gap:1rem}.work-entry{grid-template-columns:2rem 1fr;padding:2.3rem 0;gap:1rem}.work-role,.work-evidence{grid-column:2}.work-open{top:2.1rem;transform:none}.technology{grid-template-columns:1fr;gap:3rem}.writing-grid{grid-template-columns:1fr}.featured-story{min-height:28rem}.closing{min-height:60dvh}}
@media(max-width:480px){.snapshot-statement h2{font-size:3rem}.work-entry{grid-template-columns:1.5rem 1fr}.technology ol{grid-template-columns:1fr}.featured-story{min-height:24rem}.featured-story h3{font-size:2.2rem}.story-list article{grid-template-columns:1fr auto}.story-list time{grid-column:1/-1}.closing h2{font-size:3.2rem}}
@supports(animation-timeline:view()){.section-shell{animation:section-enter linear both;animation-timeline:view();animation-range:entry 8% cover 24%}@keyframes section-enter{from{opacity:.15;transform:translateY(28px)}to{opacity:1;transform:none}}}
@media(prefers-reduced-motion:reduce){.section-shell{animation:none}.work-entry,.featured-story img,.story-list h3{transition:none}}
</style>
