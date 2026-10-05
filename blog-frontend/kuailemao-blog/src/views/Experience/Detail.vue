<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/preview.css'
import { useRoute } from 'vue-router'
import { sanitizeRenderedHtml } from '@/utils/sanitize-html'
import { experienceProjectList, getExperience, type ExperienceProjectItem, type WorkExperienceItem } from '@/apis/experience'
import { createHomeResource, loadHomeResource } from '@/utils/home-content'
import { setSeoMeta } from '@/utils/seo'
import { contributionParts } from '@/utils/experience-presentation'

const route = useRoute()
const item = ref<WorkExperienceItem>()
const projectResource = reactive(createHomeResource<ExperienceProjectItem[]>())
const loading = ref(true)
const notFound = ref(false)
let requestVersion = 0
const lines=(value?:string)=>value?.split(/\r?\n/).map(line=>line.trim()).filter(Boolean)||[]
const tokens=(value?:string)=>lines(value).flatMap(line=>line.split(/[,，、/|]/)).map(line=>line.trim()).filter(Boolean)
const month=(value?:string)=>value?value.slice(0,7).replace('-','.') : ''
const period=(value:WorkExperienceItem)=>`${month(value.startDate)} 至 ${value.isCurrent===1?'今':month(value.endDate)}`
const projectCardImage=(cover:string)=>cover.replace(/(\/experience-gallery\/project-\d+-cover)-v3\.webp$/, '$1-card-v3.webp')
const metrics=computed(()=>lines(item.value?.metrics))
const responsibilities=computed(()=>lines(item.value?.responsibilities).length?lines(item.value?.responsibilities):lines(item.value?.highlights))

function loadProjects() {
  const version = requestVersion
  const id = String(route.params.id)
  return loadHomeResource(projectResource, async () => {
    const response: any = await experienceProjectList(id)
    if (response.code !== 200 || !Array.isArray(response.data)) throw new Error('Invalid project response')
    return response.data
  }, () => version === requestVersion)
}

async function load() {
  const version = ++requestVersion
  const id = String(route.params.id)
  loading.value = true
  notFound.value = false
  item.value = undefined
  projectResource.status = 'idle'
  projectResource.data = null
  void loadProjects()
  try {
    const response: any = await getExperience(id)
    if (version !== requestVersion) return
    if (response.code !== 200 || !response.data) throw new Error('Experience unavailable')
    item.value = response.data
    setSeoMeta({ title: `${response.data.company}工作经历 | 郑陆宇`, description: response.data.projectSummary || `${response.data.company}工作经历与项目实践`, keywords: `${response.data.company},${response.data.roleTitle},工作经历,项目经验` })
  } catch { if (version === requestVersion) notFound.value = true }
  finally { if (version === requestVersion) loading.value = false }
}
watch(() => route.params.id, load, { immediate: true })
onBeforeUnmount(() => { requestVersion++ })
</script>

<template>
  <main class="case-page"><div class="page-shell">
    <nav class="back" aria-label="面包屑"><RouterLink to="/experience">工作经历</RouterLink><span>/</span><span>{{ item?.company || '经历详情' }}</span></nav>
    <div v-if="loading" class="case-state" role="status">正在加载经历…</div>
    <section v-else-if="notFound || !item" class="case-state"><h1>这段经历暂时无法查看</h1><p>内容可能已停用，或暂时无法加载。</p><button type="button" @click="load">重试</button><RouterLink to="/experience">返回列表</RouterLink></section>
    <template v-else>
      <header class="case-hero">
        <p class="period">{{ period(item) }}<span v-if="item.isCurrent === 1">在职</span></p>
        <h1>{{ item.company }}</h1><p class="role">{{ item.roleTitle }}</p>
        <p v-if="item.projectSummary?.trim()" class="summary">{{ item.projectSummary }}</p>
        <ul v-if="tokens(item.techStack).length" class="tech" aria-label="使用技术"><li v-for="tech in [...new Set(tokens(item.techStack))]" :key="tech">{{ tech }}</li></ul>
        <a v-if="projectResource.data?.length" class="browse-projects" href="#projects-title">浏览 {{ projectResource.data.length }} 个项目及我的工作 →</a>
      </header>
      <section v-if="responsibilities.length" class="responsibility-overview" aria-labelledby="responsibility-title">
        <div><p class="section-label">职责概览</p><h2 id="responsibility-title">我在这里做了什么</h2></div>
        <ul><li v-for="line in [...new Set(responsibilities)]" :key="line"><strong v-if="contributionParts(line).title">{{ contributionParts(line).title }}</strong><span>{{ contributionParts(line).detail }}</span></li></ul>
      </section>
      <section class="projects" aria-labelledby="projects-title">
        <div class="projects-heading"><h2 id="projects-title">参与项目</h2><span v-if="projectResource.data?.length">{{ projectResource.data.length }} 个项目</span></div>
        <p v-if="projectResource.status === 'loading'" role="status">正在加载项目…</p>
        <p v-else-if="projectResource.status === 'error'" class="project-error" role="status">项目暂未加载。<button type="button" @click="loadProjects">重试项目</button></p>
        <div v-if="projectResource.data?.length" class="project-grid">
          <RouterLink v-for="project in projectResource.data" :key="project.id" :to="`/experience/${item.id}/projects/${project.id}`">
            <img v-if="project.coverImage" :src="projectCardImage(project.coverImage)" :alt="project.projectName + '封面'" loading="lazy" decoding="async">
            <div><h3>{{ project.projectName }}</h3><p v-if="project.summary">{{ project.summary }}</p><ul v-if="lines(project.contributions).length" class="project-work"><li v-for="line in lines(project.contributions).slice(0, 3)" :key="line">{{ contributionParts(line).title || line }}</li></ul><small v-if="tokens(project.techStack).length">{{ tokens(project.techStack).slice(0,5).join(' / ') }}</small><span class="project-entry">了解我的工作与实现细节 →</span></div>
          </RouterLink>
        </div>
        <p v-else-if="projectResource.status === 'success'" class="project-empty">暂无公开项目，以下为这段经历的已有资料。</p>
      </section>
      <div class="narrative">
        <section v-if="metrics.length"><h2>工作成果</h2><ul><li v-for="metric in [...new Set(metrics)]" :key="metric">{{ metric }}</li></ul></section>
        <section v-if="item.companyIntroduction?.trim()"><h2>公司介绍</h2><p>{{ item.companyIntroduction }}</p></section>
        <section v-if="item.mainBusiness?.trim()"><h2>主营业务</h2><p>{{ item.mainBusiness }}</p></section>
        <figure v-if="item.coverImage" class="case-cover"><img :src="item.coverImage" :alt="item.company + ' 工作经历封面'" loading="lazy"></figure>
        <article v-if="item.content?.trim()" class="story"><h2>经历补充</h2><MdPreview :model-value="item.content" theme="light" :sanitize="sanitizeRenderedHtml"/></article>
      </div>
    </template>
  </div></main>
</template>

<style scoped lang="scss">
.case-page { min-height: 100dvh; padding: 6rem 0 4rem; background: var(--brand-canvas); color: var(--brand-ink); }
.page-shell { width: min(calc(100% - 4rem), 70rem); margin: auto; }
.back { display: flex; flex-wrap: wrap; gap: .7rem; align-items: center; color: var(--brand-ink-soft); font-size: .9rem; overflow-wrap: anywhere; }
.back a { display: inline-flex; align-items: center; min-height: 44px; }
a { color: var(--brand-accent-strong); text-decoration: none; }
a:hover { text-decoration: underline; text-underline-offset: .25em; }
.case-hero { padding: 2rem 0 2.5rem; border-bottom: 1px solid var(--brand-line); overflow-wrap: anywhere; }
.period { display: flex; flex-wrap: wrap; gap: 1rem; color: var(--brand-ink-soft); font-size: .95rem; }
.period span { color: var(--brand-accent-strong); }
.case-hero h1 { margin: .8rem 0; font-size: clamp(2rem, 4vw, 3.2rem); line-height: 1.3; }
.role { margin: 0; color: var(--brand-ink-soft); font-size: 1.15rem; }
.summary { max-width: 48rem; margin: 1.4rem 0 0; line-height: 1.9; white-space: pre-line; }
.tech { display: flex; flex-wrap: wrap; gap: .5rem 1rem; margin: 1.2rem 0 0; padding: 0; list-style: none; color: var(--brand-ink-soft); font-size: .85rem; }
.projects { padding: 2rem 0; }
h2 { margin: 0 0 1.2rem; font-size: 1.4rem; line-height: 1.5; }
.projects-heading { display: flex; align-items: baseline; justify-content: space-between; gap: 1rem; }
.projects-heading span { color: var(--brand-ink-soft); font-size: .9rem; white-space: nowrap; }
.project-grid { display: grid; grid-template-columns: 1fr; gap: 1.25rem; }
.project-grid a { display: grid; grid-template-columns: minmax(0, .75fr) minmax(0, 1.25fr); align-items: start; overflow: hidden; border: 1px solid var(--brand-line); border-radius: var(--brand-radius-sm); background: var(--brand-surface); color: inherit; text-decoration: none; transition: border-color .2s ease, transform .2s ease; }
.project-grid a:not(:has(> img)) { grid-template-columns: 1fr; }
.project-grid a:hover { border-color: var(--brand-accent); transform: translateY(-2px); }
.project-grid a:focus-visible { outline: 2px solid var(--brand-accent); outline-offset: 3px; }
.project-grid a:hover h3 { color: var(--brand-accent-strong); }
.project-grid img { display: block; width: 100%; aspect-ratio: 16/10; object-fit: contain; background: var(--brand-canvas-soft); margin-top: 1.5rem; }
.project-grid a > div { display: flex; flex: 1; flex-direction: column; gap: .8rem; padding: 1.5rem 1.75rem; overflow-wrap: anywhere; }
.project-grid h3 { margin: 0; font-size: 1.2rem; line-height: 1.5; }
.project-grid h3 { font-size: clamp(1.25rem, 2vw, 1.6rem); }
.section-label { color: var(--brand-ink-soft); font-size: .8rem; letter-spacing: .05em; }
.browse-projects { display: inline-flex; align-items: center; min-height: 44px; margin-top: 1rem; font-size: .9rem; }
#projects-title { scroll-margin-top: 100px; }
.project-work { display: flex; flex-wrap: wrap; gap: .5rem 1.1rem; padding: 0; margin: .15rem 0; list-style: none; color: var(--brand-ink); font-size: .9rem; }
.project-work li::before { content: '·'; color: var(--brand-accent); margin-right: .4rem; }
.responsibility-overview { display: grid; grid-template-columns: 1fr 2fr; gap: 2rem; padding: 2.5rem 0; border-bottom: 1px solid var(--brand-line); }
.responsibility-overview .section-label { margin: 0 0 .6rem; }
.responsibility-overview ul { padding: 0; margin: 0; list-style: none; display: grid; gap: 1.2rem; }
.responsibility-overview li { display: grid; gap: .3rem; line-height: 1.85; }
.responsibility-overview span { color: var(--brand-ink-soft); }
.project-grid p { margin: 0; color: var(--brand-ink-soft); line-height: 1.8; }
.project-grid small { color: var(--brand-ink-soft); }
.project-entry { margin-top: auto; padding-top: .4rem; color: var(--brand-accent-strong); font-size: .9rem; }
.project-empty, .project-error { color: var(--brand-ink-soft); line-height: 1.8; }
.narrative { max-width: 48rem; margin: 0 auto; min-width: 0; overflow-wrap: anywhere; }
.narrative > section, .story { margin-top: 2.5rem; padding-top: 1.75rem; border-top: 1px solid var(--brand-line); }
.narrative p { color: var(--brand-ink-soft); line-height: 1.9; white-space: pre-line; }
.narrative ul { padding-left: 1.25rem; list-style: disc; }
.narrative li { margin: .65rem 0; color: var(--brand-ink-soft); line-height: 1.85; }
.case-cover { margin: 2.5rem 0; }
.case-cover img { display: block; width: 100%; max-height: 38rem; object-fit: contain; }
.story :deep(.md-editor) { background: transparent; }
.story :deep(.md-editor-preview-wrapper) { padding: 0; }
.story :deep(pre), .story :deep(table) { max-width: 100%; overflow: auto; }
.story :deep(img) { max-width: 100%; height: auto; }
.case-state { padding: 3rem 0; }
button { min-height: 44px; margin: 0 .75rem; padding: .5rem 1rem; border: 1px solid var(--brand-line); border-radius: var(--brand-radius-sm); background: var(--brand-surface); color: var(--brand-accent-strong); cursor: pointer; }
a:focus-visible, button:focus-visible { outline: 2px solid var(--brand-accent); outline-offset: 4px; }
@media(max-width:899px) { .page-shell { width: calc(100% - 2rem); } .responsibility-overview { grid-template-columns: 1fr; gap: .5rem; } }
@media(max-width:640px) { .project-grid a { grid-template-columns: 1fr; } .project-grid img { margin: 0; max-height: 15rem; } .project-grid a > div { padding: 1.25rem; } }
@media(prefers-reduced-motion:reduce) { .project-grid a { transition: none; } .project-grid a:hover { transform: none; } }
</style>
