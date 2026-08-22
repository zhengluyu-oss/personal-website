<script setup lang="ts">
import { experienceList, type WorkExperienceItem } from '@/apis/experience'

const router = useRouter()
const loading = ref(true)
const failed = ref(false)
const list = ref<WorkExperienceItem[]>([])

onMounted(async () => {
  try {
    const response: any = await experienceList()
    if (response.code === 200) list.value = response.data || []
    else failed.value = true
  } catch { failed.value = true }
  finally { loading.value = false }
})

const date = (value?: string) => value ? value.toString().slice(0, 7).replace('-', '.') : ''
const period = (item: WorkExperienceItem) => item.isCurrent === 1 ? `${date(item.startDate)} 至今` : `${date(item.startDate)} 至 ${date(item.endDate)}`
const highlights = (value?: string) => value?.split(/\r?\n/).map(line => line.trim()).filter(Boolean) || []
const openExperience = (id: number) => router.push(`/experience/${id}`)
</script>

<template>
  <main class="experience-page">
    <header class="page-hero">
      <p class="hero-label">工作经历</p>
      <h1>把复杂问题，<br><strong>做成可靠产品。</strong></h1>
      <p class="hero-copy">这里记录我参与过的业务、承担的责任，以及每一次实践留下的可复用经验。</p>
    </header>

    <section class="experience-section" aria-labelledby="experience-list-title">
      <div class="section-heading">
        <h2 id="experience-list-title">职业轨迹</h2>
        <p v-if="list.length">{{ list.length }} 段经历，按时间由近到远</p>
      </div>

      <div v-if="loading" class="state" aria-live="polite">
        <div class="state-lines"><span /><span /><span /></div><p>正在加载工作经历</p>
      </div>
      <div v-else-if="failed" class="state state--error"><h2>暂时无法读取工作经历</h2><p>请稍后刷新页面，其他内容仍可正常浏览。</p></div>
      <div v-else-if="!list.length" class="state"><h2>经历档案正在整理</h2><p>整理完成后会在这里公开。</p></div>

      <div v-else class="experience-list">
        <article v-for="item in list" :key="item.id" class="experience-item" tabindex="0" role="link" :aria-label="`查看 ${item.company} 的工作经历`" @click="openExperience(item.id)" @keydown.enter="openExperience(item.id)" @keydown.space.prevent="openExperience(item.id)">
          <div class="time-column"><span v-if="item.isCurrent === 1" class="current">目前在职</span><time>{{ period(item) }}</time></div>
          <div class="experience-content">
            <p class="role">{{ item.roleTitle }}</p><h3>{{ item.company }}</h3>
            <ul v-if="highlights(item.highlights).length" class="highlights"><li v-for="line in highlights(item.highlights).slice(0, 3)" :key="line">{{ line }}</li></ul>
          </div>
          <span class="view-detail" aria-hidden="true">查看详情 <b>→</b></span>
        </article>
      </div>
    </section>

    <footer class="page-note"><p>我重视清晰的协作、可维护的实现，以及真正解决业务问题的技术。</p></footer>
  </main>
</template>

<style scoped lang="scss">
.experience-page{--accent:#4769e8;--surface:color-mix(in srgb,var(--mao-background-color) 94%,var(--accent));--soft:color-mix(in srgb,var(--accent) 9%,var(--mao-background-color));min-height:100dvh;padding:clamp(7.5rem,11vw,10rem) clamp(1.25rem,6vw,6rem) 6rem;background:var(--mao-background-color);color:var(--el-text-color-primary)}
.page-hero,.experience-section,.page-note{width:min(100%,78rem);margin-inline:auto}.page-hero{padding-bottom:clamp(4rem,8vw,7rem)}.hero-label{margin:0 0 1.3rem;color:var(--accent);font-size:.72rem;font-weight:750;letter-spacing:.16em}.page-hero h1{margin:0;font-family:"PingFang SC","Microsoft YaHei",system-ui,sans-serif;font-size:clamp(3.1rem,6vw,5.6rem);font-weight:350;line-height:1.06;letter-spacing:-.055em;text-wrap:balance}.page-hero h1 strong{color:var(--accent);font-weight:650}.hero-copy{max-width:40rem;margin:2rem 0 0;color:var(--el-text-color-secondary);font-size:clamp(.95rem,1.25vw,1.08rem);line-height:1.9}
.section-heading{display:flex;align-items:end;justify-content:space-between;gap:2rem;margin-bottom:1.25rem;padding-bottom:1rem;border-bottom:1px solid var(--el-border-color)}.section-heading h2{margin:0;font-size:1rem;font-weight:700;letter-spacing:.04em}.section-heading p{margin:0;color:var(--el-text-color-placeholder);font-size:.76rem}.experience-list{display:grid;gap:.9rem}
.experience-item{position:relative;display:grid;grid-template-columns:11.5rem minmax(0,1fr) auto;gap:clamp(1.5rem,4vw,4.5rem);align-items:start;padding:clamp(1.5rem,3vw,2.5rem);overflow:hidden;border:1px solid var(--el-border-color);border-radius:14px;background:var(--surface);cursor:pointer;transition:border-color .25s ease,background-color .25s ease,transform .25s ease,box-shadow .25s ease}.experience-item:hover,.experience-item:focus-visible{border-color:color-mix(in srgb,var(--accent) 52%,var(--el-border-color));background:var(--soft);box-shadow:0 20px 48px color-mix(in srgb,var(--accent) 10%,transparent);outline:none;transform:translateY(-3px)}.experience-item:active{transform:translateY(-1px)}
.time-column{display:flex;flex-direction:column;gap:.75rem;padding-top:.25rem}.time-column time{color:var(--el-text-color-secondary);font-size:.78rem;font-weight:650;letter-spacing:.03em}.current{width:max-content;padding:.3rem .55rem;border-radius:999px;background:color-mix(in srgb,var(--accent) 13%,transparent);color:var(--accent);font-size:.66rem;font-weight:750}.experience-content{min-width:0}.role{margin:0 0 .55rem;color:var(--accent);font-size:.78rem;font-weight:700}.experience-content h3{margin:0;font-family:"PingFang SC","Microsoft YaHei",system-ui,sans-serif;font-size:clamp(1.65rem,3vw,2.6rem);font-weight:650;line-height:1.18;letter-spacing:-.04em;overflow-wrap:anywhere}.highlights{display:grid;max-width:48rem;gap:.55rem;margin:1.35rem 0 0;padding:0;list-style:none;color:var(--el-text-color-secondary);font-size:.84rem;line-height:1.65}.highlights li{position:relative;padding-left:1rem}.highlights li:before{content:"";position:absolute;top:.72em;left:0;width:.34rem;height:1px;background:var(--accent)}
.view-detail{display:flex;align-items:center;gap:.6rem;margin-top:.2rem;color:var(--el-text-color-placeholder);font-size:.72rem;white-space:nowrap}.view-detail b{color:var(--accent);font-size:1.1rem;transition:transform .25s ease}.experience-item:hover .view-detail b,.experience-item:focus-visible .view-detail b{transform:translateX(4px)}
.state{display:grid;min-height:18rem;place-items:center;align-content:center;gap:1rem;border:1px solid var(--el-border-color);border-radius:14px;background:var(--surface);text-align:center;color:var(--el-text-color-secondary)}.state h2,.state p{margin:0}.state h2{color:var(--el-text-color-primary);font-size:1.25rem}.state-lines{display:flex;gap:.4rem}.state-lines span{width:2.2rem;height:.2rem;border-radius:999px;background:var(--accent);animation:loading 1.1s ease-in-out infinite}.state-lines span:nth-child(2){animation-delay:.12s}.state-lines span:nth-child(3){animation-delay:.24s}.state--error{border-color:color-mix(in srgb,#c85151 34%,var(--el-border-color))}.page-note{margin-top:clamp(4rem,8vw,7rem);padding-top:1.5rem;border-top:1px solid var(--el-border-color)}.page-note p{max-width:45rem;margin:0;color:var(--el-text-color-secondary);font-size:clamp(1.05rem,2vw,1.45rem);line-height:1.65}@keyframes loading{50%{opacity:.25;transform:scaleX(.65)}}
@media(max-width:780px){.experience-page{padding:calc(6rem + env(safe-area-inset-top)) max(1rem,env(safe-area-inset-right)) calc(4rem + env(safe-area-inset-bottom)) max(1rem,env(safe-area-inset-left))}.page-hero{padding-bottom:4.5rem}.page-hero h1{font-size:clamp(2.45rem,10.8vw,4rem);letter-spacing:-.05em}.hero-copy{margin-top:1.4rem;font-size:.9rem}.section-heading{display:block}.section-heading p{margin-top:.45rem}.experience-item{grid-template-columns:1fr auto;gap:1rem;padding:1.4rem}.time-column{grid-column:1}.experience-content{grid-column:1/3}.view-detail{grid-column:2;grid-row:1;align-self:center}.highlights{margin-top:1rem;font-size:.8rem}.page-note{margin-top:4rem}}
@media(max-width:420px){.page-hero h1 br{display:none}.experience-item{padding:1.2rem}.view-detail{font-size:0}.view-detail b{font-size:1.2rem}.experience-content h3{font-size:1.6rem}.highlights li:nth-child(n+3){display:none}}@media(prefers-reduced-motion:reduce){.experience-item,.view-detail b,.state-lines span{animation:none!important;transition:none!important}}
</style>
