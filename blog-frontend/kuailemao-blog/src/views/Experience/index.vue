<script setup lang="ts">
import { experienceList, type WorkExperienceItem } from '@/apis/experience'

const router = useRouter()
const loading = ref(true)
const failed = ref(false)
const list = ref<WorkExperienceItem[]>([])

onMounted(async () => {
  try {
    const response: any = await experienceList()
    response.code === 200 ? list.value = response.data || [] : failed.value = true
  }
  catch { failed.value = true }
  finally { loading.value = false }
})

const lines = (value?: string) => value?.split(/\r?\n/).map(line => line.trim()).filter(Boolean) || []
const month = (value?: string) => value ? value.slice(0, 7).replace('-', '.') : ''
const period = (item: WorkExperienceItem) => `${month(item.startDate)} - ${item.isCurrent === 1 ? '至今' : month(item.endDate)}`
const summary = (item: WorkExperienceItem) => item.projectSummary || lines(item.highlights)[0] || '负责业务系统的设计、开发与持续优化。'
const outcomes = (item: WorkExperienceItem) => lines(item.metrics).map(line => {
  const [value, ...label] = line.split('|')
  return { value, label: label.join('|') }
}).filter(item => item.value)
const latest = computed(() => list.value[0])
</script>

<template>
  <main class="resume-page">
    <header class="resume-hero">
      <div class="hero-main">
        <p class="eyebrow">工作经历</p>
        <h1>从业务问题出发，<br>交付可靠的软件。</h1>
        <p class="hero-summary">我的职业经历、核心职责和可验证成果。</p>
      </div>
      <aside v-if="latest" class="current-role" aria-label="最近一段工作经历">
        <span>{{ latest.isCurrent === 1 ? '当前任职' : '最近经历' }}</span>
        <h2>{{ latest.roleTitle }}</h2>
        <p>{{ latest.company }}</p>
        <time>{{ period(latest) }}</time>
      </aside>
    </header>

    <section class="experience" aria-labelledby="experience-heading">
      <div class="section-heading">
        <h2 id="experience-heading">职业经历</h2>
        <p v-if="list.length">共 {{ list.length }} 段，按时间由近到远</p>
      </div>

      <div v-if="loading" class="loading-state" aria-live="polite">
        <div v-for="i in 2" :key="i" class="skeleton"><span /><div><b /><i /><i /></div></div>
      </div>
      <div v-else-if="failed" class="empty-state"><h2>暂时无法读取工作经历</h2><p>请稍后刷新页面。</p></div>
      <div v-else-if="!list.length" class="empty-state"><h2>工作经历正在整理</h2><p>完成后会在这里公开。</p></div>

      <div v-else class="experience-list">
        <article v-for="item in list" :key="item.id" class="experience-row" tabindex="0" role="link" :aria-label="`查看 ${item.company} 工作经历`" @click="router.push(`/experience/${item.id}`)" @keydown.enter="router.push(`/experience/${item.id}`)" @keydown.space.prevent="router.push(`/experience/${item.id}`)">
          <div class="date-column"><time>{{ period(item) }}</time><span v-if="item.isCurrent === 1">在职</span></div>
          <div class="role-column"><p>{{ item.company }}</p><h3>{{ item.roleTitle }}</h3></div>
          <div class="evidence-column">
            <p class="summary">{{ summary(item) }}</p>
            <ul v-if="outcomes(item).length" class="metrics"><li v-for="metric in outcomes(item).slice(0, 3)" :key="metric.value + metric.label"><strong>{{ metric.value }}</strong><span>{{ metric.label }}</span></li></ul>
            <ul v-else-if="lines(item.highlights).length" class="highlights"><li v-for="line in lines(item.highlights).slice(0, 3)" :key="line">{{ line }}</li></ul>
            <div v-if="lines(item.techStack).length" class="stack"><span v-for="tech in lines(item.techStack).slice(0, 6)" :key="tech">{{ tech }}</span></div>
          </div>
          <span class="open-detail" aria-hidden="true">查看详情</span>
        </article>
      </div>
    </section>
  </main>
</template>

<style scoped lang="scss">
.resume-page{--accent:#2f62d6;--line:color-mix(in srgb,var(--el-text-color-primary) 14%,transparent);--subtle:color-mix(in srgb,var(--el-text-color-primary) 4%,transparent);min-height:100dvh;padding:clamp(6.5rem,9vw,8rem) clamp(1.1rem,5vw,5rem) 6rem;background:var(--mao-background-color);color:var(--el-text-color-primary)}.resume-hero,.experience{width:min(100%,76rem);margin-inline:auto}.resume-hero{display:grid;grid-template-columns:minmax(0,1.5fr) minmax(17rem,.5fr);gap:clamp(3rem,8vw,8rem);align-items:end;padding:clamp(2rem,5vw,4.5rem) 0 clamp(4rem,8vw,7rem)}.eyebrow{margin:0 0 1.5rem;color:var(--accent);font-size:.75rem;font-weight:750;letter-spacing:.08em}.hero-main h1{max-width:16ch;margin:0;font-family:"PingFang SC","Microsoft YaHei",system-ui,sans-serif;font-size:clamp(3rem,6vw,5.8rem);font-weight:720;line-height:1.08;letter-spacing:-.055em;text-wrap:balance}.hero-summary{max-width:34rem;margin:1.7rem 0 0;color:var(--el-text-color-secondary);font-size:clamp(.95rem,1.3vw,1.1rem);line-height:1.75}.current-role{padding:1.5rem 0;border-top:2px solid var(--accent);border-bottom:1px solid var(--line)}.current-role span{color:var(--accent);font-size:.72rem;font-weight:750}.current-role h2{margin:1.4rem 0 .4rem;font-size:1.25rem;letter-spacing:-.02em}.current-role p,.current-role time{display:block;margin:.25rem 0;color:var(--el-text-color-secondary);font-size:.85rem;line-height:1.6}.section-heading{display:flex;align-items:baseline;justify-content:space-between;padding-bottom:1.2rem;border-bottom:1px solid var(--line)}.section-heading h2{margin:0;font-size:1.5rem;letter-spacing:-.025em}.section-heading p{margin:0;color:var(--el-text-color-secondary);font-size:.78rem}.experience-list{margin-top:0}.experience-row{position:relative;display:grid;grid-template-columns:10rem minmax(12rem,.65fr) minmax(0,1.35fr);gap:clamp(1.5rem,4vw,4.5rem);padding:clamp(2.5rem,5vw,4.5rem) 7rem clamp(2.5rem,5vw,4.5rem) 0;border-bottom:1px solid var(--line);cursor:pointer;transition:background-color .2s ease}.experience-row:hover,.experience-row:focus-visible{background:var(--subtle);outline:none}.date-column time{display:block;color:var(--el-text-color-secondary);font-size:.78rem;line-height:1.6;white-space:nowrap}.date-column span{display:inline-block;margin-top:.75rem;color:var(--accent);font-size:.7rem;font-weight:750}.role-column p{margin:0 0 .65rem;color:var(--el-text-color-secondary);font-size:.82rem}.role-column h3{margin:0;font-size:clamp(1.25rem,2vw,1.8rem);line-height:1.3;letter-spacing:-.03em}.summary{max-width:43rem;margin:0;color:var(--el-text-color-primary);font-size:1rem;font-weight:620;line-height:1.7}.metrics{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:1.5rem;margin:1.8rem 0 0;padding:0;list-style:none}.metrics strong,.metrics span{display:block}.metrics strong{color:var(--accent);font-size:clamp(1.25rem,2vw,1.7rem);letter-spacing:-.04em}.metrics span{margin-top:.35rem;color:var(--el-text-color-secondary);font-size:.72rem;line-height:1.5}.highlights{display:grid;gap:.6rem;margin:1.4rem 0 0;padding:0;list-style:none}.highlights li{position:relative;padding-left:1rem;color:var(--el-text-color-secondary);font-size:.84rem;line-height:1.65}.highlights li::before{position:absolute;left:0;content:'·';color:var(--accent);font-weight:800}.stack{display:flex;flex-wrap:wrap;gap:.55rem;margin-top:1.5rem}.stack span{padding:.4rem .65rem;border:1px solid var(--line);border-radius:8px;color:var(--el-text-color-secondary);font-size:.7rem}.open-detail{position:absolute;right:0;top:50%;color:var(--accent);font-size:.74rem;font-weight:750;transform:translateY(-50%);transition:transform .2s ease}.experience-row:hover .open-detail{transform:translate(3px,-50%)}.empty-state{padding:6rem 0;text-align:center;color:var(--el-text-color-secondary)}.empty-state h2{margin:0;color:var(--el-text-color-primary)}.empty-state p{margin:.8rem 0 0}.loading-state{padding-top:1rem}.skeleton{display:grid;grid-template-columns:10rem 1fr;gap:3rem;padding:3rem 0;border-bottom:1px solid var(--line)}.skeleton>span,.skeleton b,.skeleton i{display:block;border-radius:6px;background:var(--subtle);animation:pulse 1.2s ease-in-out infinite}.skeleton>span{width:7rem;height:.8rem}.skeleton b{width:40%;height:1.4rem}.skeleton i{width:90%;height:.75rem;margin-top:1rem}.skeleton i:last-child{width:65%}@keyframes pulse{50%{opacity:.38}}
@media(max-width:800px){.resume-page{padding:calc(5.5rem + env(safe-area-inset-top)) max(1rem,env(safe-area-inset-right)) calc(4rem + env(safe-area-inset-bottom)) max(1rem,env(safe-area-inset-left));overflow-x:hidden}.resume-hero{grid-template-columns:1fr;gap:3rem;padding:2.5rem 0 4rem}.hero-main h1{font-size:clamp(2.7rem,13vw,4.5rem)}.current-role{max-width:none}.section-heading{align-items:flex-start;flex-direction:column;gap:.5rem}.experience-row{grid-template-columns:1fr;padding:2.5rem 0;gap:1.2rem}.date-column{display:flex;align-items:center;justify-content:space-between}.date-column span{margin:0}.role-column h3{font-size:1.65rem}.metrics{grid-template-columns:repeat(2,minmax(0,1fr));gap:1.2rem}.open-detail{position:static;display:block;margin-top:.4rem;transform:none}.experience-row:hover .open-detail{transform:none}.skeleton{grid-template-columns:1fr;gap:1rem}}@media(max-width:420px){.hero-main h1{font-size:2.75rem}.metrics{grid-template-columns:1fr}.resume-hero{padding-top:1.5rem}}@media(prefers-reduced-motion:reduce){.experience-row,.open-detail,.skeleton>*{animation:none!important;transition:none!important}}
@media(min-width:801px){.resume-hero:not(:has(.current-role)){grid-template-columns:minmax(0,52rem)}}
</style>
