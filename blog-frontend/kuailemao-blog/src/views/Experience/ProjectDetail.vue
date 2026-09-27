<script setup lang="ts">
import 'md-editor-v3/lib/preview.css'
import { MdCatalog, MdPreview } from 'md-editor-v3'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { experienceProjectList, getExperienceProject, type ExperienceProjectItem } from '@/apis/experience'
import { setSeoMeta } from '@/utils/seo'
import { sanitizeRenderedHtml } from '@/utils/sanitize-html'

const route=useRoute(); const item=ref<ExperienceProjectItem>(); const others=ref<ExperienceProjectItem[]>([])
const loading=ref(true); const error=ref(false); const catalogOpen=ref(false); let requestVersion=0
const scrollElement=ref<HTMLElement>()
const hasCatalog=ref(false)
const receiveCatalog=(catalog: unknown[])=>{ hasCatalog.value=catalog.length>0; if(!hasCatalog.value)catalogOpen.value=false }
const lines=(v?:string)=>v?.split(/\r?\n/).map(x=>x.trim()).filter(Boolean)||[]
const tokens=(v?:string)=>lines(v).flatMap(x=>x.split(/[,，、/|]/)).map(x=>x.trim()).filter(Boolean)
const period=computed(()=>[item.value?.startDate?.slice(0,7),item.value?.endDate?.slice(0,7)].filter(Boolean).join(' - '))
async function load(){const version=++requestVersion;loading.value=true;error.value=false;try{const [detail,list]:any[]=await Promise.all([getExperienceProject(String(route.params.id),String(route.params.projectId)),experienceProjectList(String(route.params.id))]);if(version!==requestVersion)return;if(detail.code!==200||!detail.data)throw new Error();item.value=detail.data;others.value=(list.data||[]).filter((x:ExperienceProjectItem)=>x.id!==detail.data.id).slice(0,3);setSeoMeta({title:`${detail.data.projectName} | ${detail.data.company || '项目经历'}`,description:detail.data.summary,keywords:`${detail.data.projectName},${detail.data.company || ''},项目经验,软件开发`})}catch{if(version===requestVersion)error.value=true}finally{if(version===requestVersion)loading.value=false}}
watch(()=>[route.params.id,route.params.projectId],()=>{hasCatalog.value=false;catalogOpen.value=false;void load()},{immediate:true});onMounted(()=>scrollElement.value=document.documentElement);onBeforeUnmount(()=>requestVersion++)
</script>

<template><main class="project-page"><div class="shell">
  <nav class="crumb" aria-label="面包屑"><router-link to="/experience">工作经历</router-link><span>/</span><router-link :to="`/experience/${route.params.id}`">{{ item?.company || '公司经历' }}</router-link><span>/</span><b>{{ item?.projectName || '项目详情' }}</b></nav>
  <div v-if="loading" class="state">正在加载项目…</div><div v-else-if="error" class="state"><h1>项目暂时无法查看</h1><p>项目可能尚未发布，或链接已经失效。</p><router-link to="/experience">返回工作经历</router-link></div>
  <template v-else-if="item"><header class="hero"><p>{{ item.company }} · {{ item.roleTitle || item.companyRoleTitle }}</p><h1>{{ item.projectName }}</h1><div class="summary">{{ item.summary }}</div><time v-if="period">{{ period }}</time><ul v-if="tokens(item.techStack).length" class="stack" aria-label="项目技术"><li v-for="tech in tokens(item.techStack)" :key="tech">{{ tech }}</li></ul></header>
  <img v-if="item.coverImage" class="cover" :src="item.coverImage" :alt="`${item.projectName} 项目封面`">
  <button v-if="hasCatalog" type="button" class="catalog-toggle" :aria-expanded="catalogOpen" aria-controls="project-catalog" @click="catalogOpen=!catalogOpen">{{ catalogOpen?'收起目录':'查看目录' }}</button>
  <div class="layout" :class="{'layout--single':!hasCatalog}"><aside v-if="hasCatalog" id="project-catalog" class="catalog" :class="{open:catalogOpen}"><strong>目录</strong><MdCatalog editor-id="experience-project" :scroll-element="scrollElement" /></aside>
  <article class="content"><section v-if="lines(item.contributions).length" class="contribution"><h2>我负责的部分</h2><ul><li v-for="x in lines(item.contributions)" :key="x">{{ x }}</li></ul></section><section v-if="lines(item.outcomes).length" class="outcomes"><h2>项目成果</h2><ul><li v-for="x in lines(item.outcomes)" :key="x">{{ x }}</li></ul></section><section v-if="item.content" class="story"><MdPreview :key="item.id" editor-id="experience-project" :model-value="item.content" :sanitize="sanitizeRenderedHtml" @on-get-catalog="receiveCatalog" /></section></article></div>
  <section v-if="others.length" class="others"><h2>同一经历中的其他项目</h2><div><router-link v-for="x in others" :key="x.id" :to="`/experience/${route.params.id}/projects/${x.id}`"><b>{{ x.projectName }}</b><span>{{ x.summary }}</span></router-link></div></section></template>
</div></main></template>

<style scoped lang="scss">
.project-page{min-height:100dvh;padding:88px 0 5rem;background:#f7f9fc;color:var(--brand-ink)}.shell{width:min(calc(100% - 2rem),78rem);margin:auto}.crumb{display:flex;gap:.65rem;align-items:center;min-height:44px;color:var(--brand-ink-faint);font-size:.8rem}.crumb a{color:var(--brand-accent);text-decoration:none}.crumb b{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.hero{max-width:52rem;padding:5rem 0 3.5rem}.hero>p{color:var(--brand-accent);font-weight:650}.hero h1{margin:.8rem 0 1.2rem;font-size:clamp(2.6rem,6vw,5.8rem);line-height:1.03;letter-spacing:-.065em}.summary{max-width:46rem;color:var(--brand-ink-soft);font-size:1.12rem;line-height:1.8}.hero time{display:block;margin-top:1.4rem;color:var(--brand-ink-faint);font-size:.78rem}.cover{display:block;width:100%;aspect-ratio:16/10;object-fit:contain;border:1px solid var(--brand-line);border-radius:1rem;background:#fff}.layout{display:grid;grid-template-columns:14rem minmax(0,48rem);gap:4rem;justify-content:center;padding:5rem 0}.catalog{position:sticky;top:90px;align-self:start;max-height:calc(100dvh - 110px);overflow:auto;padding:1rem;border-left:2px solid var(--brand-line)}.catalog strong{display:block;margin-bottom:1rem}.content{min-width:0}.content section{margin-bottom:3.5rem}.content h2,.others h2{font-size:1.5rem}.content li{margin:.75rem 0;color:var(--brand-ink-soft);line-height:1.75}.content :deep(.md-editor-preview-wrapper){padding:0}.content :deep(pre),.content :deep(table){max-width:100%;overflow:auto}.others{padding-top:3rem;border-top:1px solid var(--brand-line)}.others>div{display:grid;grid-template-columns:repeat(3,1fr);gap:1rem}.others a{display:flex;min-height:9rem;flex-direction:column;gap:.7rem;padding:1.3rem;border:1px solid var(--brand-line);border-radius:.75rem;background:#fff;color:inherit;text-decoration:none}.others span{color:var(--brand-ink-soft);font-size:.82rem;line-height:1.6}.state{padding:8rem 0}.catalog-toggle{display:none;min-height:44px;margin-top:2rem;border:1px solid var(--brand-line);background:#fff}
@media(max-width:760px){.project-page{padding-top:64px}.hero{padding:3rem 0}.layout{display:block;padding:3rem 0}.catalog-toggle{display:block}.catalog{display:none;position:static;margin-top:1rem}.catalog.open{display:block}.others>div{grid-template-columns:1fr}.cover{border-radius:.6rem}}@media(prefers-reduced-motion:reduce){*{scroll-behavior:auto!important}}
@media(max-width:899px){.project-page{padding-top:64px}.hero{padding:3rem 0}.layout{display:block;padding:3rem 0}.catalog-toggle{display:block}.catalog{display:none;position:static;margin-top:1rem}.catalog.open{display:block}.others>div{grid-template-columns:1fr}.cover{border-radius:.6rem}}
.layout--single{grid-template-columns:minmax(0,48rem)}
.hero{padding:2.5rem 0}.hero h1{font-size:clamp(2rem,4vw,3.2rem);line-height:1.3;letter-spacing:-.035em}
.hero,.content,.others a{overflow-wrap:anywhere}.crumb{flex-wrap:wrap}.crumb b{white-space:normal}.crumb a{display:inline-flex;align-items:center;min-height:44px}
.content ul{padding-left:1.25rem;list-style:disc}.content :deep(.md-editor){background:transparent}.content :deep(img){max-width:100%;height:auto}
a:focus-visible,button:focus-visible{outline:2px solid var(--brand-accent);outline-offset:4px}
.project-page{background:var(--brand-canvas);color:var(--brand-ink)}
.hero{max-width:58rem;padding:2.75rem 0 3rem;border-bottom:1px solid var(--brand-line)}
.hero>p{color:var(--brand-accent-strong)}
.hero h1{max-width:18ch;letter-spacing:-.035em;text-wrap:balance}
.summary{color:var(--brand-ink-soft)}
.stack{display:flex;flex-wrap:wrap;gap:.5rem .85rem;margin:1.4rem 0 0;padding:0;list-style:none;color:var(--brand-ink-soft);font-size:.84rem}
.stack li+li::before{content:'/';margin-right:.85rem;color:var(--brand-ink-faint)}
.layout{padding:3.5rem 0;gap:3rem}
.catalog{border-left-color:var(--brand-accent)}
.content section{margin-bottom:2.75rem}
.content h2{margin:0 0 1rem;color:var(--brand-ink);font-size:1.25rem}
.content ul{margin:0;padding-left:1.25rem}
.content li{margin:.7rem 0;line-height:1.8}
.outcomes{padding:1.35rem 1.6rem;border-left:3px solid var(--brand-accent);background:var(--brand-canvas-soft)}
.story{padding-top:1rem;border-top:1px solid var(--brand-line)}
.content :deep(.md-editor){background:transparent;color:var(--brand-ink)}
.content :deep(.md-editor-preview){color:var(--brand-ink);line-height:1.9}
.content :deep(.md-editor-preview p),.content :deep(.md-editor-preview li){color:var(--brand-ink-soft)}
.content :deep(.md-editor-preview h2){margin-top:2.3rem;color:var(--brand-ink)}
.content :deep(.md-editor-preview h3){margin-top:1.7rem;color:var(--brand-ink)}
.content :deep(.md-editor-preview blockquote){background:var(--brand-canvas-soft);border-left-color:var(--brand-accent)}
.others a,.catalog-toggle{background:var(--brand-surface)}
.others a{transition:border-color .2s ease,transform .2s ease}
.others a:hover{border-color:var(--brand-accent);transform:translateY(-2px)}
@media(max-width:899px){.hero{padding:2.25rem 0}.layout{padding:2.5rem 0}.outcomes{padding:1.2rem}}
@media(prefers-reduced-motion:reduce){.others a{transition:none}.others a:hover{transform:none}}
</style>
