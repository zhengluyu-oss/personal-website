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
  if (!value) return '-'
  const [year, month] = value.toString().slice(0, 10).split('-')
  return month ? `${year}.${month}` : year
}
function formatPeriod(exp: WorkExperienceItem) {
  return `${formatDate(exp.startDate)} - ${exp.isCurrent === 1 ? '现在' : formatDate(exp.endDate)}`
}
const highlightLines = computed(() => item.value?.highlights?.split(/\r?\n/).map(line => line.trim()).filter(Boolean) || [])
const hasContent = computed(() => !!item.value?.content?.trim())
</script>

<template>
  <main class="case-page">
    <nav class="case-nav">
      <button type="button" @click="router.push('/experience')"><span>←</span> 返回工作经历</button>
      <span>职业档案</span>
    </nav>
    <div v-if="loading" class="state"><i />正在整理职业档案…</div>
    <div v-else-if="notFound || !item" class="state state--empty">
      <small>内容不可用</small><h1>这段经历暂时无法查看</h1><p>它可能已停用，或链接已经失效。</p>
    </div>
    <template v-else>
      <header class="case-hero">
        <p>{{ item.isCurrent === 1 ? '目前在职' : '过往经历' }} / {{ formatPeriod(item) }}</p>
        <h1>{{ item.company }}</h1>
        <div class="hero-foot"><strong>{{ item.roleTitle }}</strong></div>
      </header>
      <div class="case-layout">
        <aside class="dossier">
          <div class="dossier-sticky">
            <p class="eyebrow">基本信息</p>
            <dl>
              <div><dt>职位</dt><dd>{{ item.roleTitle }}</dd></div>
              <div><dt>时间</dt><dd>{{ formatPeriod(item) }}</dd></div>
              <div><dt>状态</dt><dd :class="{ current: item.isCurrent === 1 }">{{ item.isCurrent === 1 ? '在职' : '已完成' }}</dd></div>
            </dl>
            <section v-if="highlightLines.length" class="impact">
              <p class="eyebrow">关键成果</p>
              <ol><li v-for="(line, index) in highlightLines" :key="index"><span>{{ String(index + 1).padStart(2, '0') }}</span>{{ line }}</li></ol>
            </section>
          </div>
        </aside>
        <article class="story">
          <div class="story-head"><span>经历详情</span><i /></div>
          <MdPreview v-if="hasContent" :model-value="item.content || ''" :theme="mode" />
          <div v-else class="story-empty"><small>内容整理中</small><h2>完整故事仍在整理</h2><p>你可以先从左侧的关键成果了解这段职业经历。</p></div>
        </article>
      </div>
      <footer class="case-footer"><button type="button" @click="router.push('/experience')"><span>返回</span><b>继续浏览职业经历</b><i>↗</i></button></footer>
    </template>
  </main>
</template>

<style scoped lang="scss">
.case-page{min-height:100vh;padding:6.5rem clamp(1.25rem,5vw,5.5rem) 6rem;background:var(--mao-background-color);color:var(--el-text-color-primary)}
.case-nav{display:flex;align-items:center;justify-content:space-between;padding-bottom:1rem;border-bottom:1px solid var(--el-border-color);color:var(--el-text-color-placeholder);font-size:.6rem;font-weight:800;letter-spacing:.18em}.case-nav button{display:flex;gap:.7rem;padding:0;border:0;background:none;color:inherit;cursor:pointer;font:inherit;letter-spacing:inherit}.case-nav button span{color:#635bff;transition:transform .2s}.case-nav button:hover{color:var(--el-text-color-primary)}.case-nav button:hover span{transform:translateX(-4px)}
.case-hero{padding:clamp(4rem,9vw,8rem) 0 3rem;border-bottom:1px solid var(--el-border-color)}.case-hero>p{margin:0 0 1.5rem;color:#635bff;font-size:.63rem;font-weight:800;letter-spacing:.18em}.case-hero h1{max-width:72rem;margin:0;font-family:Georgia,'Times New Roman','Noto Serif SC',serif;font-size:clamp(3.8rem,10vw,9rem);font-weight:400;line-height:.88;letter-spacing:-.065em;text-wrap:balance}.hero-foot{display:flex;align-items:end;justify-content:space-between;gap:2rem;margin-top:3rem}.hero-foot strong{font-size:clamp(1.05rem,2vw,1.5rem);font-weight:600}.hero-foot span{color:var(--el-text-color-placeholder);font-size:.58rem;font-weight:800;letter-spacing:.18em}
.case-layout{display:grid;grid-template-columns:minmax(15rem,25rem) minmax(0,1fr);gap:clamp(3rem,8vw,9rem);padding-top:4rem}.dossier{border-right:1px solid var(--el-border-color);padding-right:clamp(1.5rem,4vw,4rem)}.dossier-sticky{position:sticky;top:6rem}.eyebrow{margin:0 0 1.3rem;color:#635bff;font-size:.6rem;font-weight:800;letter-spacing:.2em}.dossier dl{margin:0}.dossier dl>div{display:grid;grid-template-columns:4.5rem 1fr;gap:1rem;padding:1rem 0;border-top:1px solid var(--el-border-color-lighter)}.dossier dt{color:var(--el-text-color-placeholder);font-size:.58rem;font-weight:800;letter-spacing:.12em}.dossier dd{margin:0;color:var(--el-text-color-regular);font-size:.84rem;line-height:1.6}.dossier dd.current{color:#16865d}.impact{margin-top:3rem}.impact ol{margin:0;padding:0;list-style:none}.impact li{display:grid;grid-template-columns:2rem 1fr;gap:.8rem;padding:1rem 0;border-top:1px solid var(--el-border-color-lighter);color:var(--el-text-color-regular);font-size:.82rem;line-height:1.75}.impact li span{color:var(--el-text-color-placeholder);font-size:.55rem;font-weight:800;letter-spacing:.1em}
.story{min-width:0;max-width:54rem}.story-head{display:flex;align-items:center;gap:1rem;margin-bottom:3rem;color:#635bff;font-size:.6rem;font-weight:800;letter-spacing:.2em}.story-head i{height:1px;flex:1;background:var(--el-border-color)}.story :deep(.md-editor){background:transparent}.story :deep(.md-editor-preview-wrapper){padding:0}.story :deep(.md-editor-preview){color:var(--el-text-color-primary);font-size:1rem;line-height:2}.story :deep(h1),.story :deep(h2),.story :deep(h3){margin-top:2.3em;font-family:Georgia,'Times New Roman','Noto Serif SC',serif;font-weight:500;letter-spacing:-.025em}.story :deep(h2){padding-bottom:.55rem;border-bottom:1px solid var(--el-border-color)}.story :deep(blockquote){margin:2rem 0;padding:1.5rem 2rem;border-left:2px solid #635bff;background:color-mix(in srgb,#635bff 5%,transparent)}.story :deep(img){margin:1.5rem 0;border-radius:.25rem}.story-empty{padding:clamp(3rem,7vw,7rem) 0;border-top:1px solid var(--el-border-color);border-bottom:1px solid var(--el-border-color)}.story-empty small{color:#635bff;font-weight:800;letter-spacing:.16em}.story-empty h2{margin:.8rem 0;font-family:Georgia,'Noto Serif SC',serif;font-size:clamp(2rem,4vw,3.5rem);font-weight:400}.story-empty p{color:var(--el-text-color-secondary)}
.case-footer{margin-top:clamp(5rem,10vw,10rem);border-top:1px solid var(--el-border-color)}.case-footer button{display:grid;width:100%;grid-template-columns:5rem 1fr auto;align-items:center;gap:2rem;padding:2.5rem 0;border:0;background:none;color:inherit;text-align:left;cursor:pointer}.case-footer span{color:var(--el-text-color-placeholder);font-size:.65rem;font-weight:800;letter-spacing:.16em}.case-footer b{font-family:Georgia,'Noto Serif SC',serif;font-size:clamp(1.7rem,4vw,3.5rem);font-weight:400}.case-footer i{font-size:2rem;font-style:normal;transition:transform .2s}.case-footer button:hover i{transform:translate(5px,-5px);color:#635bff}
.state{display:flex;align-items:center;justify-content:center;min-height:55vh;color:var(--el-text-color-secondary)}.state i{width:.45rem;height:.45rem;margin-right:.7rem;border-radius:50%;background:#635bff;animation:pulse 1.2s infinite}.state--empty{display:block;padding-top:8rem;text-align:center}.state--empty small{color:#635bff;font-weight:800;letter-spacing:.18em}.state--empty h1{font-family:Georgia,'Noto Serif SC',serif;font-size:clamp(2.4rem,6vw,5rem);font-weight:400}.state--empty p{color:var(--el-text-color-secondary)}@keyframes pulse{50%{opacity:.25;transform:scale(.65)}}
@media(max-width:800px){
  .case-page{padding:calc(5.15rem + env(safe-area-inset-top)) max(1rem,env(safe-area-inset-right)) calc(3.25rem + env(safe-area-inset-bottom)) max(1rem,env(safe-area-inset-left));overflow-x:hidden}.case-nav{min-height:2.75rem;padding-bottom:.65rem}.case-nav>span{display:none}.case-nav button{align-items:center;min-height:2.75rem;font-size:.58rem;letter-spacing:.14em}
  .case-hero{padding:3rem 0 2rem}.case-hero>p{margin-bottom:1rem;font-size:.56rem;line-height:1.7;letter-spacing:.12em}.case-hero h1{font-size:clamp(2.75rem,13vw,4.75rem);line-height:.98;letter-spacing:-.055em;overflow-wrap:anywhere}.hero-foot{display:block;margin-top:1.6rem}.hero-foot strong{display:block;font-size:1rem;line-height:1.5}.hero-foot span{display:block;margin-top:.65rem;font-size:.53rem;letter-spacing:.14em}
  .case-layout{grid-template-columns:1fr;gap:3rem;padding-top:2rem}.dossier{border-right:0;border-bottom:1px solid var(--el-border-color);padding:0 0 2.5rem}.dossier-sticky{position:static}.eyebrow{margin-bottom:.9rem}.dossier dl{display:grid;grid-template-columns:1fr 1fr}.dossier dl>div{display:block;padding:.85rem .65rem .85rem 0}.dossier dl>div:first-child{grid-column:1/3}.dossier dt{margin-bottom:.35rem}.dossier dd{font-size:.79rem}.impact{margin-top:2rem}.impact li{grid-template-columns:1.65rem 1fr;gap:.5rem;padding:.85rem 0;font-size:.78rem;line-height:1.7}
  .story{width:100%;max-width:100%}.story-head{margin-bottom:1.5rem}.story :deep(.md-editor-preview){overflow-wrap:anywhere;font-size:.94rem;line-height:1.9}.story :deep(h1){font-size:1.85rem}.story :deep(h2){font-size:1.5rem}.story :deep(h3){font-size:1.2rem}.story :deep(h1),.story :deep(h2),.story :deep(h3){margin-top:1.8em;line-height:1.35}.story :deep(p){margin:1em 0}.story :deep(img){max-width:100%;height:auto;margin:1rem 0}.story :deep(pre),.story :deep(.md-editor-code){max-width:100%;overflow-x:auto;-webkit-overflow-scrolling:touch}.story :deep(table){display:block;width:max-content;max-width:100%;overflow-x:auto;-webkit-overflow-scrolling:touch}.story :deep(blockquote){margin:1.35rem 0;padding:1rem 1.1rem}.story-empty{padding:3rem 0}.story-empty h2{font-size:2rem;line-height:1.25}
  .case-footer{margin-top:5rem}.case-footer button{grid-template-columns:1fr auto;gap:1rem;min-height:5.5rem;padding:1.5rem 0}.case-footer span{display:none}.case-footer b{font-size:1.65rem;line-height:1.25}.case-footer i{font-size:1.5rem}
}
@media(max-width:380px){.case-page{padding-right:.85rem;padding-left:.85rem}.case-hero h1{font-size:2.65rem}.dossier dl{grid-template-columns:1fr}.dossier dl>div:first-child{grid-column:auto}.case-footer b{font-size:1.45rem}}
@media(prefers-reduced-motion:reduce){*,*:before,*:after{animation:none!important;transition:none!important}}
</style>
