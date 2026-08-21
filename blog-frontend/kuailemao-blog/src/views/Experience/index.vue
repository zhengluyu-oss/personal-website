<script setup lang="ts">
import { experienceList, type WorkExperienceItem } from '@/apis/experience'

const router = useRouter()
const loading = ref(true)
const list = ref<WorkExperienceItem[]>([])

onMounted(async () => {
  try {
    const res: any = await experienceList()
    if (res.code === 200) list.value = res.data || []
  } finally { loading.value = false }
})

function formatDate(value?: string) {
  if (!value) return ''
  const [year, month] = value.toString().slice(0, 10).split('-')
  return month ? `${year}.${month}` : year
}
function formatPeriod(item: WorkExperienceItem) {
  const start = formatDate(item.startDate)
  if (item.isCurrent === 1) return `${start} — NOW`
  const end = formatDate(item.endDate)
  return end ? `${start} — ${end}` : start
}
function highlightLines(text?: string) {
  return text ? text.split(/\r?\n/).map(s => s.trim()).filter(Boolean) : []
}
function openDetail(id: number) { router.push(`/experience/${id}`) }
</script>

<template>
  <main class="experience-page">
    <div class="ambient ambient-one" aria-hidden="true" />
    <div class="ambient ambient-two" aria-hidden="true" />
    <header class="page-hero">
      <p class="eyebrow"><span /> CAREER JOURNEY</p>
      <h1>把经历写成一条<br><em>持续向前的轨迹</em></h1>
      <p class="lede">在实践中理解复杂问题，在协作中交付可靠结果。这里记录我的职业选择、角色变化，以及每一段路带来的成长。</p>
      <div class="hero-meta">
        <span><b>{{ list.length || '—' }}</b> 段职业经历</span>
        <span><b>{{ list.some(item => item.isCurrent === 1) ? 'NOW' : '∞' }}</b> 持续探索</span>
      </div>
    </header>

    <div v-if="loading" class="state"><span class="state-pulse" /> 正在整理职业轨迹…</div>
    <div v-else-if="!list.length" class="state empty">暂时还没有公开的工作经历。</div>
    <section v-else class="journey-layout">
      <aside class="journey-aside">
        <span class="aside-index">EXPERIENCE / {{ String(list.length).padStart(2, '0') }}</span>
        <h2>从每一次<br>真实问题出发</h2>
        <p>时间不会自动变成经验。只有复盘、沉淀和持续创造，才让走过的路拥有意义。</p>
        <div class="aside-keywords"><span>产品思维</span><span>工程实践</span><span>持续成长</span></div>
      </aside>
      <div class="timeline">
        <article v-for="(item, index) in list" :key="item.id" class="timeline-item" role="link" tabindex="0"
          :aria-label="`查看 ${item.company} ${item.roleTitle} 的经历详情`"
          @click="openDetail(item.id)" @keydown.enter="openDetail(item.id)">
          <div class="track" aria-hidden="true"><span class="dot"><i /></span></div>
          <div class="card">
            <div class="card-topline">
              <span class="sequence">{{ String(index + 1).padStart(2, '0') }}</span>
              <span class="period">{{ formatPeriod(item) }}</span>
              <span v-if="item.isCurrent === 1" class="current"><i /> CURRENT</span>
            </div>
            <div class="card-heading">
              <div><p class="role">{{ item.roleTitle }}</p><h2>{{ item.company }}</h2></div>
              <span class="open-arrow" aria-hidden="true">↗</span>
            </div>
            <ul v-if="highlightLines(item.highlights).length" class="highlights">
              <li v-for="(line, idx) in highlightLines(item.highlights).slice(0, 3)" :key="idx">
                <span>{{ String(idx + 1).padStart(2, '0') }}</span><p>{{ line }}</p>
              </li>
            </ul>
            <div class="card-footer"><span>VIEW CASE</span><i /></div>
          </div>
        </article>
      </div>
    </section>
  </main>
</template>

<style scoped lang="scss">
.experience-page{position:relative;min-height:100vh;overflow:hidden;padding:8rem max(1.25rem,6vw) 7rem;background:radial-gradient(circle at 90% 8%,rgba(91,124,250,.13),transparent 26rem),radial-gradient(circle at 8% 48%,rgba(139,92,246,.08),transparent 30rem),var(--mao-background-color);color:var(--el-text-color-primary)}
.ambient{position:absolute;border:1px solid rgba(91,124,250,.13);border-radius:50%;pointer-events:none}.ambient-one{width:26rem;height:26rem;top:-14rem;right:-10rem;box-shadow:0 0 0 4rem rgba(91,124,250,.025),0 0 0 8rem rgba(91,124,250,.018)}.ambient-two{width:12rem;height:12rem;top:42rem;left:-8rem;box-shadow:0 0 0 2.5rem rgba(139,92,246,.025)}
.page-hero{position:relative;z-index:1;max-width:76rem;margin:0 auto 7rem}.eyebrow{display:flex;align-items:center;gap:.65rem;margin:0 0 1.4rem;color:#5b7cfa;font-size:.68rem;font-weight:800;letter-spacing:.22em}.eyebrow span{width:2.6rem;height:1px;background:#5b7cfa}.page-hero h1{margin:0;font-family:Georgia,'Times New Roman',serif;font-size:clamp(3rem,7.2vw,6.4rem);font-weight:500;line-height:.98;letter-spacing:-.055em;text-wrap:balance}.page-hero h1 em{color:#5b7cfa;font-weight:500}.lede{max-width:39rem;margin:2rem 0 2.4rem;color:var(--el-text-color-secondary);font-size:1rem;line-height:1.9}.hero-meta{display:flex;gap:2.5rem;color:var(--el-text-color-secondary);font-size:.75rem;letter-spacing:.08em}.hero-meta span{display:flex;align-items:baseline;gap:.5rem}.hero-meta b{color:var(--el-text-color-primary);font-family:Georgia,serif;font-size:1.4rem;font-weight:500}
.journey-layout{position:relative;z-index:1;display:grid;max-width:76rem;margin:0 auto;grid-template-columns:minmax(15rem,.72fr) minmax(0,1.55fr);gap:clamp(3rem,8vw,8rem);align-items:start}.journey-aside{position:sticky;top:7rem;padding-top:1rem}.aside-index{color:#5b7cfa;font-size:.62rem;font-weight:800;letter-spacing:.19em}.journey-aside h2{margin:1rem 0 1.2rem;font-family:Georgia,serif;font-size:clamp(2rem,3.5vw,3.2rem);font-weight:500;line-height:1.1;letter-spacing:-.035em}.journey-aside p{max-width:22rem;color:var(--el-text-color-secondary);font-size:.9rem;line-height:1.85}.aside-keywords{display:flex;flex-wrap:wrap;gap:.55rem;margin-top:1.8rem}.aside-keywords span{padding:.42rem .7rem;border:1px solid var(--el-border-color-lighter);border-radius:99px;color:var(--el-text-color-secondary);font-size:.68rem}
.timeline{position:relative}.timeline:before{content:'';position:absolute;top:1.2rem;bottom:1.2rem;left:.4rem;width:1px;background:linear-gradient(#5b7cfa,rgba(91,124,250,.1))}.timeline-item{position:relative;display:grid;grid-template-columns:1rem minmax(0,1fr);gap:1.4rem;padding-bottom:2rem;cursor:pointer;outline:none}.track{position:relative;z-index:2;padding-top:2rem}.dot{display:grid;width:.85rem;height:.85rem;place-items:center;border:1px solid #5b7cfa;border-radius:50%;background:var(--mao-background-color);transition:.25s}.dot i{width:.28rem;height:.28rem;border-radius:50%;background:#5b7cfa}
.card{position:relative;overflow:hidden;padding:1.8rem 2rem 1.5rem;border:1px solid color-mix(in srgb,var(--el-border-color) 78%,transparent);border-radius:1.35rem;background:color-mix(in srgb,var(--el-bg-color) 94%,transparent);box-shadow:0 .8rem 2.5rem rgba(20,27,52,.055);backdrop-filter:blur(12px);transition:transform .35s cubic-bezier(.2,.7,.3,1),border-color .3s,box-shadow .3s}.card:after{content:'';position:absolute;width:9rem;height:9rem;top:-5rem;right:-4rem;border-radius:50%;background:rgba(91,124,250,.08);transition:transform .5s}.timeline-item:hover .card,.timeline-item:focus-visible .card{transform:translateY(-5px);border-color:rgba(91,124,250,.4);box-shadow:0 1.4rem 3.5rem rgba(20,27,52,.12)}.timeline-item:hover .card:after{transform:scale(1.25)}.timeline-item:hover .dot,.timeline-item:focus-visible .dot{transform:scale(1.2);background:#5b7cfa}.timeline-item:hover .dot i,.timeline-item:focus-visible .dot i{background:#fff}
.card-topline{position:relative;z-index:1;display:flex;align-items:center;gap:.8rem;margin-bottom:1.4rem}.sequence{color:var(--el-text-color-placeholder);font-family:Georgia,serif;font-size:1.1rem;font-style:italic}.period{color:#5b7cfa;font-size:.68rem;font-weight:700;letter-spacing:.1em}.current{display:inline-flex;align-items:center;gap:.35rem;margin-left:auto;color:#22a06b;font-size:.6rem;font-weight:800;letter-spacing:.12em}.current i{width:.38rem;height:.38rem;border-radius:50%;background:#22a06b;box-shadow:0 0 0 .22rem rgba(34,160,107,.13)}.card-heading{position:relative;z-index:1;display:flex;justify-content:space-between;gap:1rem}.role{margin:0 0 .35rem;color:var(--el-text-color-secondary);font-size:.76rem;font-weight:600;letter-spacing:.06em}.card h2{margin:0;font-family:Georgia,serif;font-size:clamp(1.55rem,3vw,2.25rem);font-weight:500;line-height:1.15;letter-spacing:-.025em}.open-arrow{display:grid;width:2.8rem;height:2.8rem;place-items:center;flex:0 0 auto;border:1px solid var(--el-border-color);border-radius:50%;color:#5b7cfa;font-size:1.05rem;transition:.25s}.timeline-item:hover .open-arrow{transform:rotate(7deg);background:#5b7cfa;color:#fff}
.highlights{position:relative;z-index:1;margin:1.7rem 0 0;padding:1.2rem 0 0;border-top:1px solid var(--el-border-color-lighter);list-style:none}.highlights li{display:grid;grid-template-columns:1.6rem minmax(0,1fr);gap:.6rem;color:var(--el-text-color-regular);line-height:1.65}.highlights li+li{margin-top:.55rem}.highlights li span{padding-top:.16rem;color:#5b7cfa;font-family:Georgia,serif;font-size:.68rem;font-style:italic}.highlights p{margin:0;font-size:.82rem}.card-footer{position:relative;z-index:1;display:flex;align-items:center;gap:.65rem;margin-top:1.4rem;color:var(--el-text-color-placeholder);font-size:.58rem;font-weight:800;letter-spacing:.16em}.card-footer i{width:2.5rem;height:1px;background:currentColor;transition:width .25s}.timeline-item:hover .card-footer i{width:4rem}
.state{position:relative;z-index:1;max-width:76rem;margin:0 auto;padding:4rem 1rem;border:1px dashed var(--el-border-color);border-radius:1.3rem;text-align:center;color:var(--el-text-color-secondary)}.state-pulse{display:inline-block;width:.5rem;height:.5rem;margin-right:.5rem;border-radius:50%;background:#5b7cfa;animation:pulse 1.2s infinite}@keyframes pulse{50%{opacity:.35;transform:scale(.7)}}
@media(max-width:900px){.experience-page{padding-top:6.5rem}.page-hero{margin-bottom:4.5rem}.journey-layout{grid-template-columns:1fr;gap:3rem}.journey-aside{position:static}.journey-aside p{max-width:36rem}}
@media(max-width:640px){.experience-page{padding:5.5rem 1rem 4rem}.page-hero{margin-bottom:3.5rem}.page-hero h1{font-size:clamp(2.55rem,13vw,4rem)}.lede{font-size:.9rem}.hero-meta{gap:1.2rem}.timeline-item{gap:.85rem}.card{padding:1.4rem 1.2rem 1.25rem;border-radius:1.1rem}.card-topline{flex-wrap:wrap;gap:.5rem}.current{width:100%;margin:.15rem 0 0}.open-arrow{width:2.4rem;height:2.4rem}}
@media(prefers-reduced-motion:reduce){*,*:before,*:after{animation:none!important;transition:none!important}.timeline-item:hover .card,.timeline-item:focus-visible .card{transform:none}}
</style>
