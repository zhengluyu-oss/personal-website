<script setup lang="ts">
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/preview.css'
import { useColorMode } from '@vueuse/core'
import { getExperience, type WorkExperienceItem } from '@/apis/experience'

const mode = useColorMode()
const route = useRoute()
const router = useRouter()
const loading = ref(true)
const notFound = ref(false)
const item = ref<WorkExperienceItem | null>(null)

onMounted(async () => {
  try {
    const response: any = await getExperience(route.params.id as string)
    response.code === 200 && response.data ? item.value = response.data : notFound.value = true
  }
  catch { notFound.value = true }
  finally { loading.value = false }
})

const lines = (value?: string) => value?.split(/\r?\n/).map(line => line.trim()).filter(Boolean) || []
const month = (value?: string) => value ? value.slice(0, 7).replace('-', '.') : ''
const period = (experience: WorkExperienceItem) => `${month(experience.startDate)} - ${experience.isCurrent === 1 ? '至今' : month(experience.endDate)}`
const metrics = computed(() => lines(item.value?.metrics).map(line => {
  const [value, ...label] = line.split('|')
  return { value, label: label.join('|') || '项目成果' }
}))
const responsibilities = computed(() => lines(item.value?.responsibilities).length ? lines(item.value?.responsibilities) : lines(item.value?.highlights))
</script>

<template>
  <main class="detail-page">
    <nav class="back-nav"><button type="button" @click="router.push('/experience')">← 返回工作经历</button></nav>
    <div v-if="loading" class="state" aria-live="polite"><div class="skeleton-title"/><div class="skeleton-line"/><div class="skeleton-block"/></div>
    <div v-else-if="notFound || !item" class="state state--center"><h1>这段经历暂时无法查看</h1><p>内容可能已停用，或链接已经失效。</p><button type="button" @click="router.push('/experience')">返回列表</button></div>
    <template v-else>
      <header class="detail-hero">
        <div class="hero-copy"><p>{{ period(item) }}</p><h1>{{ item.roleTitle }}</h1><h2>{{ item.company }}</h2><div class="position">{{ item.projectSummary || lines(item.highlights)[0] || '负责业务系统的设计、开发与持续优化。' }}</div></div>
        <img v-if="item.coverImage" :src="item.coverImage" :alt="`${item.company} 项目展示`" class="cover">
      </header>

      <section v-if="metrics.length" class="metric-section" aria-label="量化成果"><article v-for="metric in metrics" :key="metric.value + metric.label"><strong>{{ metric.value }}</strong><p>{{ metric.label }}</p></article></section>

      <div class="detail-body">
        <aside class="facts"><h2>经历概览</h2><dl><div><dt>公司</dt><dd>{{ item.company }}</dd></div><div><dt>岗位</dt><dd>{{ item.roleTitle }}</dd></div><div><dt>时间</dt><dd>{{ period(item) }}</dd></div><div><dt>状态</dt><dd>{{ item.isCurrent === 1 ? '目前在职' : '已完成' }}</dd></div></dl><section v-if="lines(item.techStack).length" class="tech"><h2>技术栈</h2><div><span v-for="techItem in lines(item.techStack)" :key="techItem">{{ techItem }}</span></div></section></aside>
        <div class="main-content">
          <section v-if="responsibilities.length" class="responsibility"><h2>核心职责与贡献</h2><ol><li v-for="(line, index) in responsibilities" :key="line"><span>{{ String(index + 1).padStart(2, '0') }}</span><p>{{ line }}</p></li></ol></section>
          <article class="story"><h2>经历详情</h2><MdPreview v-if="item.content?.trim()" :model-value="item.content" :theme="mode"/><div v-else class="empty-content">详细内容正在整理，可先查看上方的职责与成果。</div></article>
        </div>
      </div>
    </template>
  </main>
</template>

<style scoped lang="scss">
.detail-page{--accent:#2f62d6;--line:color-mix(in srgb,var(--el-text-color-primary) 14%,transparent);--subtle:color-mix(in srgb,var(--el-text-color-primary) 4%,transparent);min-height:100dvh;padding:6rem clamp(1.1rem,5vw,5rem);background:var(--mao-background-color);color:var(--el-text-color-primary)}.back-nav,.detail-hero,.metric-section,.detail-body,.state{width:min(100%,76rem);margin-inline:auto}.back-nav{padding:1rem 0;border-bottom:1px solid var(--line)}.back-nav button{padding:.4rem 0;border:0;background:transparent;color:var(--el-text-color-secondary);font-size:.78rem;font-weight:650;cursor:pointer}.back-nav button:hover{color:var(--accent)}.detail-hero{display:grid;grid-template-columns:minmax(0,1fr) minmax(20rem,.72fr);gap:clamp(3rem,8vw,8rem);align-items:end;padding:clamp(3rem,6vw,6rem) 0}.detail-hero:not(:has(.cover)){grid-template-columns:minmax(0,52rem)}.hero-copy>p{margin:0;color:var(--accent);font-size:.76rem;font-weight:750}.hero-copy h1{max-width:15ch;margin:1.3rem 0 .7rem;font-size:clamp(3rem,6vw,6rem);line-height:1.02;letter-spacing:-.06em;overflow-wrap:anywhere}.hero-copy h2{margin:0;color:var(--el-text-color-secondary);font-size:clamp(1.1rem,2vw,1.5rem);font-weight:600}.position{max-width:42rem;margin-top:2.5rem;padding-top:1.2rem;border-top:1px solid var(--line);font-size:clamp(1rem,1.4vw,1.15rem);font-weight:560;line-height:1.75}.cover{display:block;width:100%;aspect-ratio:4/3;object-fit:cover;border-radius:12px}.metric-section{display:grid;grid-template-columns:repeat(auto-fit,minmax(12rem,1fr));padding:2rem 0;border-block:1px solid var(--line)}.metric-section article{padding:1rem clamp(1rem,3vw,2.5rem);border-right:1px solid var(--line)}.metric-section article:first-child{padding-left:0}.metric-section article:last-child{border-right:0}.metric-section strong{color:var(--accent);font-size:clamp(1.8rem,3.5vw,3rem);letter-spacing:-.05em}.metric-section p{margin:.55rem 0 0;color:var(--el-text-color-secondary);font-size:.78rem;line-height:1.55}.detail-body{display:grid;grid-template-columns:minmax(13rem,17rem) minmax(0,1fr);gap:clamp(3rem,9vw,9rem);padding-top:clamp(4rem,8vw,7rem)}.facts{align-self:start;position:sticky;top:6rem}.facts h2,.responsibility>h2,.story>h2{margin:0 0 1.5rem;font-size:.82rem;font-weight:750}.facts dl{margin:0}.facts dl>div{margin-bottom:1.3rem}.facts dt{color:var(--el-text-color-secondary);font-size:.7rem}.facts dd{margin:.35rem 0 0;font-size:.83rem;line-height:1.5}.tech{margin-top:3rem}.tech>div{display:flex;flex-wrap:wrap;gap:.55rem}.tech span{padding:.4rem .6rem;border:1px solid var(--line);border-radius:8px;color:var(--el-text-color-secondary);font-size:.7rem}.main-content{min-width:0}.responsibility{margin-bottom:clamp(4rem,8vw,7rem)}.responsibility ol{margin:0;padding:0;list-style:none}.responsibility li{display:grid;grid-template-columns:3rem 1fr;gap:1rem;padding:1.25rem 0}.responsibility li+li{border-top:1px solid var(--line)}.responsibility li span{color:var(--accent);font-size:.7rem;font-weight:750;line-height:1.9}.responsibility li p{margin:0;font-size:clamp(.95rem,1.25vw,1.06rem);line-height:1.75}.story{padding-top:3rem;border-top:1px solid var(--line)}.story :deep(.md-editor){background:transparent}.story :deep(.md-editor-preview-wrapper){padding:0}.story :deep(.md-editor-preview){color:var(--el-text-color-primary);font-size:1rem;line-height:1.95}.story :deep(h1),.story :deep(h2),.story :deep(h3){margin:2em 0 .8em;line-height:1.35;letter-spacing:-.025em}.story :deep(h2){padding-bottom:.6rem;border-bottom:1px solid var(--line)}.story :deep(a){color:var(--accent)}.story :deep(blockquote){margin:1.8rem 0;padding:1rem 1.3rem;border-left:3px solid var(--accent);background:var(--subtle)}.story :deep(img){display:block;max-width:100%;height:auto;margin:1.5rem 0;border-radius:12px}.story :deep(pre),.story :deep(table){max-width:100%;overflow:auto}.empty-content{padding:2.5rem 0;color:var(--el-text-color-secondary)}.state{padding:5rem 0}.state--center{text-align:center}.state--center h1{margin:0;font-size:clamp(2rem,5vw,4rem)}.state--center p{color:var(--el-text-color-secondary)}.state--center button{margin-top:1rem;padding:.75rem 1rem;border:0;border-radius:8px;background:var(--accent);color:#f7f9ff;font-weight:700;cursor:pointer}.skeleton-title,.skeleton-line,.skeleton-block{border-radius:8px;background:var(--subtle);animation:pulse 1.2s ease-in-out infinite}.skeleton-title{width:60%;height:4rem}.skeleton-line{width:35%;height:1rem;margin-top:1.5rem}.skeleton-block{height:18rem;margin-top:4rem}@keyframes pulse{50%{opacity:.38}}
@media(max-width:800px){.detail-page{padding:calc(5rem + env(safe-area-inset-top)) max(1rem,env(safe-area-inset-right)) calc(4rem + env(safe-area-inset-bottom)) max(1rem,env(safe-area-inset-left));overflow-x:hidden}.detail-hero{grid-template-columns:1fr;gap:2.5rem;padding:3rem 0 4rem}.hero-copy h1{font-size:clamp(2.8rem,13vw,4.5rem)}.cover{aspect-ratio:16/10}.metric-section{grid-template-columns:repeat(2,minmax(0,1fr))}.metric-section article,.metric-section article:first-child{padding:1rem}.metric-section article:nth-child(2n){border-right:0}.detail-body{grid-template-columns:1fr;gap:4rem;padding-top:4rem}.facts{position:static}.facts dl{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:1rem}.facts dl>div{margin:0}.tech{margin-top:2rem}.skeleton-title{width:90%}}@media(max-width:420px){.metric-section{grid-template-columns:1fr}.metric-section article{border-right:0;border-bottom:1px solid var(--line)}.metric-section article:last-child{border-bottom:0}.hero-copy h1{font-size:2.8rem}}@media(prefers-reduced-motion:reduce){.back-nav button,.skeleton-title,.skeleton-line,.skeleton-block{animation:none!important;transition:none!important}}
.detail-page{background:radial-gradient(ellipse 62% 34% at 82% 0%,color-mix(in srgb,var(--accent) 12%,transparent),transparent 72%),linear-gradient(180deg,color-mix(in srgb,var(--mao-background-color) 72%,#14253d) 0%,color-mix(in srgb,var(--mao-background-color) 84%,#17283f) 52%,color-mix(in srgb,var(--mao-background-color) 90%,#182a42) 100%);background-attachment:fixed}
.detail-page{--resume-muted:color-mix(in srgb,var(--el-text-color-primary) 72%,transparent);background-attachment:scroll}.back-nav button,.hero-copy h2,.facts dt,.tech span,.metric-section p,.empty-content{color:var(--resume-muted)}.hero-copy h1{font-size:clamp(2.8rem,5.4vw,5.2rem)}
</style>
