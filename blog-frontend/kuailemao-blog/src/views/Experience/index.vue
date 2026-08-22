<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { experienceList, type WorkExperienceItem } from '@/apis/experience'

const router = useRouter()
const list = ref<WorkExperienceItem[]>([])
const loading = ref(true)
const failed = ref(false)
const lines = (value?: string) => value?.split(/\r?\n/).map(item => item.trim()).filter(Boolean) || []
const tokens = (value?: string) => lines(value).flatMap(item => item.split(/[,，/|]/)).map(item => item.trim()).filter(Boolean)
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
      <div class="hero-copy"><p>EXPERIENCE</p><h1>从业务问题出发，<br>交付可靠的软件。</h1><span>我的职业经历、核心职责和可验证成果。</span></div>
      <dl class="career-snapshot">
        <div v-if="current"><dt>当前岗位</dt><dd>{{ current.roleTitle }}</dd><small>{{ current.company }}</small></div>
        <div><dt>经历记录</dt><dd>{{ list.length }} 段真实业务经历</dd></div>
        <div v-if="allTech.length"><dt>技术范围</dt><dd>{{ allTech.slice(0,4).join(' / ') }}</dd></div>
      </dl>
    </section>

    <section class="career-list page-shell" aria-labelledby="career-title">
      <header><h2 id="career-title">职业轨迹</h2><p>按时间由近到远，点击进入完整案例。</p></header>
      <div v-if="loading" class="loading" aria-label="工作经历加载中"><div v-for="n in 2" :key="n"><span/><b/><i/></div></div>
      <div v-else-if="failed" class="state"><h2>工作经历暂时无法读取</h2><p>请稍后刷新页面重试。</p></div>
      <div v-else-if="!list.length" class="state"><h2>工作经历正在整理</h2><p>完成后会在这里公开。</p></div>
      <div v-else class="timeline">
        <article v-for="item in list" :key="item.id" class="career-case" :class="{'career-case--current':item.isCurrent===1}" tabindex="0" role="link" :aria-label="`查看 ${item.company} 工作经历`" @click="router.push(`/experience/${item.id}`)" @keydown.enter="router.push(`/experience/${item.id}`)">
          <aside class="case-time"><strong>{{ year(item.startDate) }}</strong><time>{{ period(item) }}</time><span>{{ duration(item) }}</span></aside>
          <div class="case-main">
            <p class="company">{{ item.company }}</p><h3>{{ item.roleTitle }}</h3>
            <p class="case-summary">{{ item.projectSummary || lines(item.highlights)[0] || '负责业务系统的设计、开发与持续优化。' }}</p>
            <section v-if="lines(item.highlights).length" class="selected"><h4>Selected Work</h4><ol><li v-for="work in lines(item.highlights).slice(0,4)" :key="work">{{ work }}</li></ol></section>
            <section v-if="lines(item.metrics).length" class="impact"><h4>Impact</h4><div><p v-for="metric in lines(item.metrics).slice(0,3)" :key="metric">{{ metric }}</p></div></section>
            <ul v-if="tokens(item.techStack).length" class="metadata" aria-label="使用技术"><li v-for="tech in tokens(item.techStack).slice(0,12)" :key="tech">{{ tech }}</li></ul>
          </div>
          <div class="case-action"><span v-if="item.isCurrent===1">CURRENT</span><b>查看详情 ↗</b></div>
        </article>
      </div>
    </section>

    <section class="career-closing page-shell"><p>持续构建，持续复盘。</p><h2>让每一次交付，<br>都成为下一次进步的证据。</h2><nav><router-link to="/category">阅读技术文章</router-link><router-link to="/about">了解更多</router-link></nav></section>
  </main>
</template>

<style scoped lang="scss">
.career-page{--accent:#e36f55;--ink:#ecf1f7;--muted:#a9b5c4;--faint:#748296;--line:rgba(232,239,247,.12);--surface:#151d27;min-height:100dvh;padding-top:64px;background:#0f151d;color:var(--ink)}.page-shell{width:min(calc(100% - 3rem),84rem);margin:auto}.career-hero{display:grid;min-height:min(44rem,calc(100dvh - 64px));grid-template-columns:minmax(0,1.35fr) minmax(20rem,.65fr);gap:clamp(4rem,10vw,11rem);align-items:center;padding:5rem 0}.hero-copy>p{margin:0 0 1.4rem;color:var(--accent);font-family:"Share TechMono",monospace;font-size:.7rem;font-weight:700;letter-spacing:.17em}.hero-copy h1{max-width:13ch;margin:0;font-size:clamp(3.3rem,6vw,6.4rem);line-height:1;letter-spacing:-.07em}.hero-copy>span{display:block;max-width:32rem;margin-top:1.8rem;color:var(--muted);font-size:1rem;line-height:1.75}.career-snapshot{margin:0;border-top:2px solid var(--accent)}.career-snapshot div{padding:1.2rem 0;border-bottom:1px solid var(--line)}.career-snapshot dt{color:var(--faint);font-family:"Share TechMono",monospace;font-size:.65rem}.career-snapshot dd{margin:.55rem 0 0;font-size:1rem;font-weight:700;line-height:1.5}.career-snapshot small{display:block;margin-top:.3rem;color:var(--muted);font-size:.76rem}.career-list{padding:clamp(5rem,9vw,8rem) 0}.career-list>header{display:flex;align-items:end;justify-content:space-between;margin-bottom:2.5rem}.career-list>header h2{margin:0;font-size:clamp(2.3rem,4vw,4rem);letter-spacing:-.055em}.career-list>header p{margin:0;color:var(--faint);font-size:.76rem}.timeline{position:relative}.timeline::before{content:'';position:absolute;top:0;bottom:0;left:8.8rem;width:1px;background:var(--line)}.career-case{position:relative;display:grid;grid-template-columns:9rem minmax(0,1fr) 8rem;gap:clamp(2rem,6vw,6rem);padding:clamp(3rem,6vw,5.5rem) 0;border-top:1px solid var(--line);cursor:pointer}.career-case:last-child{border-bottom:1px solid var(--line)}.career-case::before{content:'';position:absolute;top:4.2rem;left:8.52rem;width:.58rem;height:.58rem;border:2px solid #0f151d;border-radius:50%;background:var(--faint);box-shadow:0 0 0 1px var(--line)}.career-case--current::before{background:var(--accent)}.career-case:hover h3,.career-case:focus-visible h3{color:var(--accent)}.career-case:focus-visible{outline:2px solid var(--accent);outline-offset:8px}.case-time{position:sticky;top:6rem;align-self:start}.case-time strong,.case-time time,.case-time span{display:block}.case-time strong{font-size:clamp(2rem,4vw,3.2rem);line-height:1;letter-spacing:-.06em}.case-time time{margin-top:1rem;color:var(--muted);font-family:"Share TechMono",monospace;font-size:.68rem}.case-time span{margin-top:.4rem;color:var(--faint);font-size:.68rem}.company{margin:0;color:var(--muted);font-size:.86rem}.case-main h3{margin:.65rem 0 0;font-size:clamp(2rem,4vw,3.8rem);line-height:1.03;letter-spacing:-.06em;transition:color .2s ease}.case-summary{max-width:48rem;margin:1.7rem 0 0;color:var(--ink);font-size:clamp(1rem,1.45vw,1.2rem);font-weight:620;line-height:1.75}.selected,.impact{display:grid;grid-template-columns:8rem 1fr;gap:1.5rem;margin-top:2.5rem;padding-top:1.2rem;border-top:1px solid var(--line)}.selected h4,.impact h4{margin:0;color:var(--faint);font-family:"Share TechMono",monospace;font-size:.65rem;font-weight:600}.selected ol{display:grid;gap:.75rem;margin:0;padding:0;list-style:none}.selected li{color:var(--muted);font-size:.84rem;line-height:1.65}.selected li::before{content:'+';margin-right:.7rem;color:var(--accent)}.impact>div{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:1rem}.impact p{margin:0;color:var(--muted);font-size:.82rem;line-height:1.6}.metadata{display:flex;flex-wrap:wrap;gap:.45rem;margin:2rem 0 0;padding:0;list-style:none}.metadata li{padding:.38rem .55rem;border:1px solid var(--line);border-radius:3px;color:var(--muted);font-family:"Share TechMono",monospace;font-size:.65rem}.case-action{display:flex;align-self:start;flex-direction:column;align-items:flex-end;gap:1rem}.case-action span{color:var(--accent);font-family:"Share TechMono",monospace;font-size:.64rem;font-weight:700}.case-action b{color:var(--muted);font-size:.72rem}.loading>div{display:grid;grid-template-columns:9rem 1fr;gap:4rem;padding:4rem 0;border-top:1px solid var(--line)}.loading span,.loading b,.loading i{display:block;background:var(--surface);animation:pulse 1.2s ease-in-out infinite}.loading span{height:3rem}.loading b{width:55%;height:3.4rem}.loading i{grid-column:2;width:85%;height:1rem}.state{padding:5rem 0;border-top:1px solid var(--line)}.state h2{margin:0}.state p{color:var(--muted)}.career-closing{display:grid;min-height:72dvh;align-content:center;border-top:1px solid var(--line)}.career-closing>p{margin:0 0 1.4rem;color:var(--accent);font-family:"Share TechMono",monospace;font-size:.7rem}.career-closing h2{max-width:17ch;margin:0;font-size:clamp(3rem,6vw,6.2rem);line-height:1;letter-spacing:-.07em}.career-closing nav{display:flex;gap:1.8rem;margin-top:2.5rem}.career-closing a{padding-bottom:.3rem;border-bottom:1px solid var(--muted);color:var(--ink);font-size:.78rem;text-decoration:none}@keyframes pulse{50%{opacity:.45}}
@media(max-width:850px){.page-shell{width:min(calc(100% - 2rem),44rem)}.career-hero{min-height:auto;grid-template-columns:1fr;gap:3rem;padding:5rem 0}.hero-copy h1{font-size:clamp(3rem,12vw,5rem)}.career-list>header{align-items:flex-start;flex-direction:column;gap:.8rem}.timeline::before{left:.28rem}.career-case{grid-template-columns:1.5rem 1fr;padding:3rem 0;gap:1rem}.career-case::before{top:3.35rem;left:0}.case-time{position:static;grid-column:2}.case-time strong{font-size:2.4rem}.case-main{grid-column:2}.case-action{position:absolute;top:3rem;right:0}.selected,.impact{grid-template-columns:1fr;gap:.8rem}.loading>div{grid-template-columns:1fr;gap:1rem}.loading i{grid-column:1}.career-closing{min-height:65dvh}}
@media(max-width:480px){.career-page{padding-top:56px}.hero-copy h1{max-width:none;font-size:clamp(2rem,9vw,2.35rem);line-height:1.08;letter-spacing:-.055em}.case-main h3{font-size:2.25rem;padding-right:1rem}.case-action b{display:none}.impact>div{grid-template-columns:1fr}.career-closing h2{font-size:2.7rem}}
@media(prefers-reduced-motion:reduce){.career-case h3,.loading>*{animation:none;transition:none}}
</style>
