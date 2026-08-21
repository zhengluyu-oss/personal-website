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
    const res: any = await getExperience(route.params.id as string)
    if (res.code === 200 && res.data) item.value = res.data
    else notFound.value = true
  } catch { notFound.value = true }
  finally { loading.value = false }
})

function formatDate(value?: string) {
  if (!value) return ''
  const [year, month] = value.toString().slice(0, 10).split('-')
  return month ? `${year}.${month}` : year
}
function formatPeriod(exp: WorkExperienceItem) {
  const start = formatDate(exp.startDate)
  if (exp.isCurrent === 1) return `${start} — NOW`
  const end = formatDate(exp.endDate)
  return end ? `${start} — ${end}` : start
}
const hasContent = computed(() => !!(item.value?.content && item.value.content.trim()))
</script>

<template>
  <main class="experience-detail">
    <div class="detail-glow" aria-hidden="true" />
    <button class="back-link" type="button" @click="router.push('/experience')"><span>←</span> BACK TO JOURNEY</button>
    <div v-if="loading" class="state"><span class="state-pulse" /> 正在加载经历详情…</div>
    <div v-else-if="notFound || !item" class="state empty">
      <b>404 / EXPERIENCE</b><h1>这段经历暂时无法查看</h1><p>它可能已停用，或链接已经失效。</p>
    </div>
    <template v-else>
      <header class="hero">
        <div class="hero-kicker"><span>{{ formatPeriod(item) }}</span><i v-if="item.isCurrent === 1" />{{ item.isCurrent === 1 ? 'CURRENT POSITION' : 'PAST EXPERIENCE' }}</div>
        <p class="role">{{ item.roleTitle }}</p>
        <h1>{{ item.company }}</h1>
        <p v-if="item.highlights" class="summary-lead">{{ item.highlights.split(/\r?\n/).filter(Boolean)[0] }}</p>
        <div class="hero-rule"><span>WORK / IMPACT / GROWTH</span><i /></div>
      </header>
      <article v-if="hasContent" class="body">
        <div class="body-label">THE FULL STORY</div>
        <MdPreview :model-value="item.content || ''" :theme="mode" />
      </article>
      <div v-else class="state empty content-empty">
        <b>DETAILS COMING SOON</b>
        <template v-if="item.highlights"><p>完整故事仍在整理，先看看这段经历的关键摘要。</p><pre class="summary">{{ item.highlights }}</pre></template>
        <p v-else>这段经历的详细内容还在整理中。</p>
      </div>
    </template>
  </main>
</template>

<style scoped lang="scss">
.experience-detail{position:relative;min-height:100vh;overflow:hidden;padding:7.5rem max(1.25rem,6vw) 7rem;background:radial-gradient(circle at 85% 4%,rgba(91,124,250,.14),transparent 28rem),var(--mao-background-color);color:var(--el-text-color-primary)}
.detail-glow{position:absolute;width:28rem;height:28rem;top:-17rem;right:-13rem;border:1px solid rgba(91,124,250,.13);border-radius:50%;box-shadow:0 0 0 4rem rgba(91,124,250,.025),0 0 0 8rem rgba(91,124,250,.018);pointer-events:none}
.back-link{position:relative;z-index:1;display:inline-flex;align-items:center;gap:.7rem;padding:0;border:0;background:transparent;color:var(--el-text-color-secondary);cursor:pointer;font-size:.65rem;font-weight:800;letter-spacing:.16em;transition:color .2s}.back-link span{display:grid;width:2.35rem;height:2.35rem;place-items:center;border:1px solid var(--el-border-color);border-radius:50%;color:#5b7cfa;font-size:1rem;transition:.2s}.back-link:hover{color:#5b7cfa}.back-link:hover span{transform:translateX(-3px);background:rgba(91,124,250,.08)}
.hero{position:relative;z-index:1;max-width:68rem;margin:5rem auto 4rem}.hero-kicker{display:flex;align-items:center;gap:.55rem;color:var(--el-text-color-placeholder);font-size:.62rem;font-weight:800;letter-spacing:.15em}.hero-kicker span{color:#5b7cfa}.hero-kicker i{width:.38rem;height:.38rem;margin-left:.5rem;border-radius:50%;background:#22a06b;box-shadow:0 0 0 .22rem rgba(34,160,107,.13)}.role{margin:2.1rem 0 .65rem;color:var(--el-text-color-secondary);font-size:.9rem;font-weight:600;letter-spacing:.08em}.hero h1{max-width:58rem;margin:0;font-family:Georgia,'Times New Roman',serif;font-size:clamp(3rem,7vw,6.4rem);font-weight:500;line-height:.98;letter-spacing:-.055em;text-wrap:balance}.summary-lead{max-width:42rem;margin:2rem 0 0;color:var(--el-text-color-secondary);font-size:1rem;line-height:1.85}.hero-rule{display:flex;align-items:center;gap:1rem;margin-top:3.5rem;color:var(--el-text-color-placeholder);font-size:.58rem;font-weight:800;letter-spacing:.18em}.hero-rule i{height:1px;flex:1;background:var(--el-border-color-lighter)}
.body{position:relative;z-index:1;max-width:58rem;margin:0 auto;padding:clamp(1.5rem,5vw,4rem);border:1px solid color-mix(in srgb,var(--el-border-color) 78%,transparent);border-radius:1.6rem;background:color-mix(in srgb,var(--el-bg-color) 96%,transparent);box-shadow:0 1.5rem 4rem rgba(20,27,52,.08)}.body-label{margin-bottom:2rem;color:#5b7cfa;font-size:.62rem;font-weight:800;letter-spacing:.2em}.body :deep(.md-editor){background:transparent}.body :deep(.md-editor-preview-wrapper){padding:0}.body :deep(.md-editor-preview){color:var(--el-text-color-primary);font-size:1rem;line-height:1.9}.body :deep(h1),.body :deep(h2),.body :deep(h3){font-family:Georgia,'Times New Roman',serif;letter-spacing:-.02em}.body :deep(img){border-radius:.9rem}
.state{position:relative;z-index:1;max-width:58rem;margin:5rem auto 0;padding:4rem 1.25rem;border:1px dashed var(--el-border-color);border-radius:1.4rem;text-align:center;color:var(--el-text-color-secondary)}.state b{color:#5b7cfa;font-size:.63rem;letter-spacing:.18em}.state h1{margin:1rem 0 .5rem;color:var(--el-text-color-primary);font-family:Georgia,serif;font-weight:500}.state p{margin:.6rem 0 0;line-height:1.7}.state-pulse{display:inline-block;width:.5rem;height:.5rem;margin-right:.5rem;border-radius:50%;background:#5b7cfa;animation:pulse 1.2s infinite}.content-empty{margin-top:0}.summary{max-width:40rem;margin:1.5rem auto 0;padding:1.3rem;border-radius:.8rem;background:rgba(91,124,250,.06);text-align:left;white-space:pre-wrap;color:var(--el-text-color-regular);font-family:inherit;line-height:1.8}@keyframes pulse{50%{opacity:.35;transform:scale(.7)}}
@media(max-width:640px){.experience-detail{padding:5.5rem 1rem 4rem}.hero{margin:3.5rem 0 3rem}.hero h1{font-size:clamp(2.65rem,14vw,4.2rem)}.hero-kicker{flex-wrap:wrap}.summary-lead{font-size:.9rem}.body{padding:1.4rem 1.1rem;border-radius:1.1rem}}
@media(prefers-reduced-motion:reduce){*,*:before,*:after{animation:none!important;transition:none!important}}
</style>
