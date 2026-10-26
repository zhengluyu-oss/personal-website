<script setup lang="ts">
import 'md-editor-v3/lib/preview.css'
import { MdCatalog, MdPreview } from 'md-editor-v3'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { experienceProjectList, getExperienceProject, type ExperienceProjectItem } from '@/apis/experience'
import { setSeoMeta } from '@/utils/seo'
import { sanitizeRenderedHtml } from '@/utils/sanitize-html'
import { contributionParts } from '@/utils/experience-presentation'

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
  <template v-else-if="item"><header class="hero" :class="{ 'hero--illustrated': item.coverImage }"><div><p class="eyebrow">{{ item.company }} · {{ item.roleTitle || item.companyRoleTitle }}</p><h1>{{ item.projectName }}</h1><div class="summary">{{ item.summary }}</div><time v-if="period">{{ period }}</time><ul v-if="tokens(item.techStack).length" class="stack" aria-label="项目技术"><li v-for="tech in [...new Set(tokens(item.techStack))]" :key="tech">{{ tech }}</li></ul></div><img v-if="item.coverImage" class="cover" :src="item.coverImage" :alt="`${item.projectName} 项目封面`" decoding="async"></header>
  <nav class="section-nav" aria-label="项目章节"><a v-if="lines(item.contributions).length" href="#project-work">我的工作</a><a v-if="item.content" href="#project-story">业务与实现</a><a v-if="lines(item.outcomes).length" href="#project-outcomes">交付结果</a></nav>
  <button v-if="hasCatalog" type="button" class="catalog-toggle" :aria-expanded="catalogOpen" aria-controls="project-catalog" @click="catalogOpen=!catalogOpen">{{ catalogOpen?'收起目录':'查看目录' }}</button>
  <div class="layout" :class="{'layout--single':!hasCatalog}"><aside v-if="hasCatalog" id="project-catalog" class="catalog" :class="{open:catalogOpen}"><strong>目录</strong><MdCatalog editor-id="experience-project" :scroll-element="scrollElement" /></aside>
  <article class="content"><section v-if="lines(item.contributions).length" id="project-work" class="contribution"><h2>我负责的部分</h2><ul><li v-for="x in lines(item.contributions)" :key="x"><h3 v-if="contributionParts(x).title">{{ contributionParts(x).title }}</h3><p>{{ contributionParts(x).detail }}</p></li></ul></section><section v-if="item.content" id="project-story" class="story"><MdPreview :key="item.id" editor-id="experience-project" :model-value="item.content" :sanitize="sanitizeRenderedHtml" @on-get-catalog="receiveCatalog" /></section><section v-if="lines(item.outcomes).length" id="project-outcomes" class="outcomes"><h2>交付结果</h2><ul><li v-for="x in lines(item.outcomes)" :key="x">{{ x }}</li></ul></section></article></div>
  <section v-if="others.length" class="others"><h2>同一经历中的其他项目</h2><div><router-link v-for="x in others" :key="x.id" :to="`/experience/${route.params.id}/projects/${x.id}`"><b>{{ x.projectName }}</b><span>{{ x.summary }}</span></router-link></div><router-link class="all-projects" :to="`/experience/${route.params.id}`">返回公司经历，查看全部项目 →</router-link></section></template>
</div></main></template>

<style scoped lang="scss">
.project-page { min-height: 100dvh; padding: 6rem 0 5rem; background: var(--brand-canvas); color: var(--brand-ink); }
.shell { width: min(calc(100% - 4rem), 74rem); margin: auto; }
.crumb { display: flex; flex-wrap: wrap; gap: .65rem; align-items: center; font-size: .85rem; color: var(--brand-ink-soft); overflow-wrap: anywhere; }
a { color: var(--brand-accent-strong); text-decoration: none; }
a:hover { text-decoration: underline; text-underline-offset: .25em; }
.crumb a { display: inline-flex; align-items: center; min-height: 44px; }
.crumb b { font-weight: 400; }
.hero { padding: 2.75rem 0; border-bottom: 1px solid var(--brand-line); overflow-wrap: anywhere; }
.hero--illustrated { display: grid; grid-template-columns: minmax(0, 1.25fr) minmax(0, 1fr); align-items: center; gap: 3rem; }
.eyebrow { margin: 0; color: var(--brand-accent-strong); font-size: .9rem; line-height: 1.8; }
.hero h1 { margin: .85rem 0 1.25rem; font-size: clamp(2rem, 3.3vw, 3rem); line-height: 1.3; letter-spacing: -.03em; text-wrap: balance; }
.summary { max-width: 46rem; color: var(--brand-ink-soft); font-size: 1.05rem; line-height: 1.9; white-space: pre-line; }
.hero time { display: block; margin-top: 1rem; color: var(--brand-ink-soft); font-size: .85rem; }
.stack { display: flex; flex-wrap: wrap; gap: .5rem 1rem; margin: 1.2rem 0 0; padding: 0; list-style: none; color: var(--brand-ink-soft); font-size: .82rem; }
.cover { display: block; width: 100%; aspect-ratio: 16/10; object-fit: contain; background: var(--brand-canvas-soft); border: 1px solid var(--brand-line); border-radius: var(--brand-radius-sm); }
.section-nav { display: flex; flex-wrap: wrap; gap: .4rem 1.8rem; padding: .75rem 0; border-bottom: 1px solid var(--brand-line); }
.section-nav a { display: inline-flex; align-items: center; min-height: 44px; font-size: .9rem; }
.layout { display: grid; grid-template-columns: 14rem minmax(0, 46rem); justify-content: center; gap: 3rem; padding: 3rem 0 0; }
.layout--single { grid-template-columns: minmax(0, 46rem); }
.catalog { position: sticky; top: 90px; align-self: start; max-height: calc(100dvh - 110px); overflow: auto; padding: .5rem 1rem; border-left: 2px solid var(--brand-line); }
.catalog strong { display: block; margin-bottom: 1rem; font-size: .9rem; }
.catalog :deep(.md-editor-catalog-link span) { color: var(--brand-ink-soft); white-space: normal; overflow-wrap: anywhere; line-height: 1.65; }
.catalog :deep(.md-editor-catalog-active > span) { color: var(--brand-accent-strong); }
.catalog-toggle { display: none; min-height: 44px; padding: .6rem 1rem; border: 1px solid var(--brand-line); border-radius: var(--brand-radius-sm); background: var(--brand-surface); color: var(--brand-accent-strong); cursor: pointer; }
.content { min-width: 0; overflow-wrap: anywhere; }
.content section { margin-bottom: 3rem; scroll-margin-top: 100px; }
.content h2, .others h2 { margin: 0 0 1.25rem; color: var(--brand-ink); font-size: 1.5rem; line-height: 1.5; }
.contribution ul { list-style: none; padding: 0; margin: 0; }
.contribution li { padding: 1.15rem 0; border-bottom: 1px solid var(--brand-line); }
.contribution h3 { margin: 0 0 .3rem; font-size: 1.05rem; line-height: 1.6; }
.contribution p { margin: 0; color: var(--brand-ink-soft); line-height: 1.85; }
.outcomes { padding: 1.4rem 1.6rem; border-left: 3px solid var(--brand-accent); background: var(--brand-canvas-soft); }
.outcomes ul { padding-left: 1.2rem; margin: 0; list-style: disc; }
.outcomes li { margin: .65rem 0; color: var(--brand-ink-soft); line-height: 1.85; }
.content :deep(.md-editor) { background: transparent; color: var(--brand-ink); }
.content :deep(.md-editor-preview-wrapper) { padding: 0; }
.content :deep(.md-editor-preview) { color: var(--brand-ink); font-size: 1rem; line-height: 1.95; }
.content :deep(.md-editor-preview p), .content :deep(.md-editor-preview li) { color: var(--brand-ink-soft); }
.content :deep(.md-editor-preview h2) { margin: 3rem 0 1.25rem; padding-top: 1.5rem; border-top: 1px solid var(--brand-line); font-size: 1.5rem; color: var(--brand-ink); scroll-margin-top: 100px; }
.content :deep(.md-editor-preview h3) { margin: 1.8rem 0 .8rem; font-size: 1.15rem; color: var(--brand-ink); scroll-margin-top: 100px; }
.content :deep(.md-editor-preview strong) { color: var(--brand-ink); }
.content :deep(.md-editor-preview blockquote) { background: var(--brand-canvas-soft); border-left-color: var(--brand-accent); }
.content :deep(pre), .content :deep(table) { max-width: 100%; overflow: auto; }
.content :deep(img) { max-width: 100%; height: auto; border-radius: var(--brand-radius-sm); }
.others { padding-top: 2rem; border-top: 1px solid var(--brand-line); }
.others > div { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 1.25rem; }
.others a { display: flex; flex-direction: column; gap: .7rem; padding: 1.3rem; border: 1px solid var(--brand-line); border-radius: var(--brand-radius-sm); background: var(--brand-surface); color: inherit; overflow-wrap: anywhere; transition: border-color .2s ease; }
.others a:hover { border-color: var(--brand-accent); }
.others .all-projects { display: inline-flex; min-height: 44px; justify-content: center; border: 0; background: transparent; padding: .75rem 0; margin-top: .75rem; color: var(--brand-accent-strong); }
.others span { color: var(--brand-ink-soft); font-size: .9rem; line-height: 1.8; }
.state { padding: 5rem 0; }
a:focus-visible, button:focus-visible { outline: 2px solid var(--brand-accent); outline-offset: 4px; }
@media(max-width:899px) { .shell { width: calc(100% - 2rem); } .hero--illustrated { gap: 1.5rem; } .hero h1 { font-size: 2rem; } .layout { display: block; padding-top: 2rem; } .catalog-toggle { display: block; margin-top: 1.25rem; } .catalog { display: none; position: static; margin-bottom: 2rem; } .catalog.open { display: block; } .others > div { grid-template-columns: 1fr; } }
@media(max-width:640px) { .hero--illustrated { grid-template-columns: 1fr; } .hero { padding: 1.75rem 0; } .cover { max-height: 15rem; } .outcomes { padding: 1rem; } }
@media(prefers-reduced-motion:reduce) { .others a { transition: none; } }
</style>
