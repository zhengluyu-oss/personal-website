<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { experienceList, type WorkExperienceItem } from '@/apis/experience'

const list = ref<WorkExperienceItem[]>([])
const loading = ref(true)
const failed = ref(false)
const lines = (value?: string) => value?.split(/\r?\n/).map(item => item.trim()).filter(Boolean) || []
const tokens = (value?: string) => lines(value).flatMap(item => item.split(/[,，、/|]/)).map(item => item.trim()).filter(Boolean)
const month = (value?: string) => value ? value.slice(0,7).replace('-','.') : ''
const year = (value?: string) => value?.slice(0,4) || ''
const period = (item: WorkExperienceItem) => `${month(item.startDate)} 至 ${item.isCurrent === 1 ? '今' : month(item.endDate)}`
const duration = (item: WorkExperienceItem) => {
  const start = new Date(item.startDate); const end = item.isCurrent === 1 ? new Date() : new Date(item.endDate || item.startDate)
  const total = Math.max(1,(end.getFullYear()-start.getFullYear())*12+end.getMonth()-start.getMonth()+1)
  return `${Math.floor(total/12) ? `${Math.floor(total/12)} 年 ` : ''}${total%12 ? `${total%12} 个月` : ''}`.trim()
}
const current = computed(() => list.value.find(item => item.isCurrent === 1))
const allTech = computed(() => [...new Set(list.value.flatMap(item => tokens(item.techStack)))])

onMounted(async () => {
  try { const response:any = await experienceList(); if(response.code !== 200) throw new Error(); list.value=[...(response.data || [])].sort((a,b)=>b.isCurrent-a.isCurrent || new Date(b.startDate).getTime()-new Date(a.startDate).getTime()) }
  catch { failed.value=true }
  finally { loading.value=false }
})
</script>

<template>
  <main class="career-page">
    <section class="career-hero page-shell">
      <div class="hero-copy"><p>工作经历</p><h1>从业务问题出发，交付可靠的软件。</h1><span>这里记录我承担过的职责、参与的项目，以及在真实业务中的技术实践。</span></div>
      <dl class="career-snapshot">
        <div v-if="current"><dt>当前岗位</dt><dd>{{ current.roleTitle }}</dd><small>{{ current.company }}</small></div>
        <div><dt>经历记录</dt><dd>{{ loading ? '加载中' : failed ? '暂不可用' : `${list.length} 段经历` }}</dd></div>
        <div v-if="allTech.length"><dt>技术范围</dt><dd>{{ allTech.slice(0,4).join(' / ') }}</dd></div>
      </dl>
    </section>

    <section class="career-list page-shell" aria-labelledby="career-title">
      <header><div><span>经历汇总</span><h2 id="career-title">职业轨迹</h2></div><p>按时间由近到远，点击查看公司与项目详情。</p></header>
      <div v-if="loading" class="loading" aria-label="工作经历加载中"><div v-for="n in 2" :key="n"><span/><b/><i/></div></div>
      <div v-else-if="failed" class="state"><h2>工作经历暂时无法读取</h2><p>请稍后刷新页面重试。</p></div>
      <div v-else-if="!list.length" class="state"><h2>工作经历正在整理</h2><p>完成后会在这里公开。</p></div>
      <div v-else class="timeline">
        <RouterLink v-for="item in list" :key="item.id" class="career-case" :class="{'career-case--current':item.isCurrent===1}" :to="`/experience/${item.id}`" :aria-label="`查看 ${item.company} 工作经历`">
          <aside class="case-time"><strong>{{ year(item.startDate) }}</strong><time>{{ period(item) }}</time><span>{{ duration(item) }}</span></aside>
          <div class="case-main">
            <h3>{{ item.company }}</h3><p class="company">{{ item.roleTitle }}</p>
            <p v-if="item.projectSummary?.trim()" class="case-summary">{{ item.projectSummary }}</p>
            <section v-if="lines(item.highlights).some(line => line !== item.projectSummary?.trim())" class="selected"><h4>主要工作</h4><ol><li v-for="work in [...new Set(lines(item.highlights))].filter(line => line !== item.projectSummary?.trim()).slice(0,2)" :key="work">{{ work }}</li></ol></section>
            <ul v-if="tokens(item.techStack).length" class="metadata" aria-label="使用技术"><li v-for="tech in tokens(item.techStack).slice(0,12)" :key="tech">{{ tech }}</li></ul>
          </div>
          <div class="case-action"><span v-if="item.isCurrent===1">在职</span><b>查看详情 ↗</b></div>
        </RouterLink>
      </div>
    </section>

  </main>
</template>

<style scoped lang="scss">
.career-page { min-height: 100dvh; padding: 6rem 0 4rem; background: var(--brand-canvas); color: var(--brand-ink); }
.page-shell { width: min(calc(100% - 4rem), 76rem); margin: auto; }
.career-hero { display: grid; grid-template-columns: minmax(0, 1.5fr) minmax(0, 1fr); gap: 3rem; padding: 2rem 0 3rem; border-bottom: 1px solid var(--brand-line); }
.hero-copy > p { margin: 0 0 1rem; color: var(--brand-accent-strong); font-size: .95rem; }
.hero-copy h1 { max-width: 20ch; margin: 0; font-size: clamp(1.9rem, 3vw, 2.7rem); line-height: 1.4; text-wrap: balance; }
.hero-copy > span { display: block; margin-top: 1.2rem; color: var(--brand-ink-soft); line-height: 1.8; }
.career-snapshot { margin: 0; }
.career-snapshot > div { padding: .7rem 0; }
.career-snapshot dt, .career-snapshot small { color: var(--brand-ink-soft); font-size: .85rem; }
.career-snapshot dd { margin: .35rem 0; font-size: 1rem; line-height: 1.65; }
.career-list { padding-top: 2.5rem; }
.career-list > header { margin-bottom: 1.5rem; }
.career-list > header span { display: none; }
.career-list h2 { margin: 0; font-size: 1.4rem; }
.career-list > header p { color: var(--brand-ink-soft); line-height: 1.7; }
.career-case { display: grid; grid-template-columns: 9rem minmax(0,1fr) 6rem; gap: 2rem; padding: 2rem 0; border-top: 1px solid var(--brand-line); color: inherit; text-decoration: none; }
.case-time { color: var(--brand-ink-soft); }
.case-time strong, .case-time time, .case-time span { display: block; }
.case-time strong { font-size: 1.2rem; font-weight: 600; }
.case-time time, .case-time span { margin-top: .5rem; font-size: .85rem; line-height: 1.6; }
.case-main { min-width: 0; overflow-wrap: anywhere; }
.case-main h3 { margin: 0; font-size: clamp(1.4rem, 2.5vw, 2rem); line-height: 1.4; }
.company { margin: .6rem 0 0; color: var(--brand-ink-soft); font-size: 1rem; }
.case-summary { margin: 1rem 0 0; line-height: 1.8; }
.selected { margin-top: 1rem; }
.selected h4 { margin: 0; color: var(--brand-ink-soft); font-weight: 500; font-size: .85rem; }
.selected ol { margin: .6rem 0 0; padding-left: 1.15rem; list-style: disc; color: var(--brand-ink-soft); line-height: 1.8; }
.metadata { display: flex; flex-wrap: wrap; gap: .4rem .8rem; margin: 1rem 0 0; padding: 0; list-style: none; color: var(--brand-ink-soft); font-size: .8rem; }
.case-action { display: flex; flex-direction: column; align-items: end; gap: .6rem; color: var(--brand-accent-strong); font-size: .85rem; }
.case-action b { font-weight: 500; white-space: nowrap; }
.career-case:hover h3 { color: var(--brand-accent-strong); }
.career-case:focus-visible { outline: 2px solid var(--brand-accent); outline-offset: 4px; }
.loading > div { height: 10rem; margin-bottom: 1rem; border-radius: var(--brand-radius-sm); background: var(--brand-canvas-soft); }
.state { padding: 2rem 0; color: var(--brand-ink-soft); }
@media (max-width: 899px) { .page-shell { width: calc(100% - 2rem); } .career-hero { grid-template-columns: 1fr; gap: 1.5rem; } .career-case { grid-template-columns: 1fr; gap: 1rem; } .case-time { display: flex; flex-wrap: wrap; align-items: baseline; gap: .5rem 1rem; } .case-time time, .case-time span { margin: 0; } .case-action { flex-direction: row; align-items: center; } }
</style>
