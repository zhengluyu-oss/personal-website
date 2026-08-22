<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { MdPreview } from 'md-editor-v3'
import { useDark } from '@vueuse/core'
import { getExperience, type WorkExperienceItem } from '@/apis/experience'

const route=useRoute(); const router=useRouter(); const item=ref<WorkExperienceItem>(); const loading=ref(true); const notFound=ref(false); const mode=computed(()=>useDark().value?'dark':'light')
const lines=(value?:string)=>value?.split(/\r?\n/).map(line=>line.trim()).filter(Boolean)||[]
const tokens=(value?:string)=>lines(value).flatMap(line=>line.split(/[,，/|]/)).map(line=>line.trim()).filter(Boolean)
const month=(value?:string)=>value?value.slice(0,7).replace('-','.') : ''
const period=(value:WorkExperienceItem)=>`${month(value.startDate)} 至 ${value.isCurrent===1?'今':month(value.endDate)}`
const metrics=computed(()=>lines(item.value?.metrics))
const responsibilities=computed(()=>lines(item.value?.responsibilities).length?lines(item.value?.responsibilities):lines(item.value?.highlights))
onMounted(async()=>{try{const response:any=await getExperience(String(route.params.id)); if(response.code!==200||!response.data) throw new Error(); item.value=response.data}catch{notFound.value=true}finally{loading.value=false}})
</script>

<template>
  <main class="case-page">
    <nav class="back page-shell"><button type="button" @click="router.push('/experience')">← 返回职业轨迹</button></nav>
    <div v-if="loading" class="case-loading page-shell"><span/><b/><i/></div>
    <section v-else-if="notFound || !item" class="case-state page-shell"><h1>这段经历暂时无法查看</h1><p>内容可能已停用，或链接已经失效。</p><button type="button" @click="router.push('/experience')">返回列表</button></section>
    <template v-else>
      <header class="case-hero page-shell">
        <div><p>{{ period(item) }}</p><h1>{{ item.roleTitle }}</h1><h2>{{ item.company }}</h2></div>
        <aside><span v-if="item.isCurrent===1">CURRENT</span><p>{{ item.projectSummary || lines(item.highlights)[0] || '负责业务系统的设计、开发与持续优化。' }}</p></aside>
      </header>
      <figure v-if="item.coverImage" class="case-cover page-shell"><img :src="item.coverImage" :alt="`${item.company} 工作经历封面`"></figure>
      <section class="case-body page-shell">
        <aside class="facts"><h2>经历概览</h2><dl><div><dt>公司</dt><dd>{{ item.company }}</dd></div><div><dt>岗位</dt><dd>{{ item.roleTitle }}</dd></div><div><dt>时间</dt><dd>{{ period(item) }}</dd></div><div><dt>状态</dt><dd>{{ item.isCurrent===1?'目前在职':'已结束' }}</dd></div></dl><div v-if="tokens(item.techStack).length" class="tech"><h3>Technology</h3><ul><li v-for="tech in tokens(item.techStack)" :key="tech">{{ tech }}</li></ul></div></aside>
        <div class="narrative">
          <section v-if="responsibilities.length" class="work"><h2>Selected Work</h2><ol><li v-for="(line,index) in responsibilities" :key="line"><span>{{ String(index+1).padStart(2,'0') }}</span><p>{{ line }}</p></li></ol></section>
          <section v-if="metrics.length" class="outcomes"><h2>Impact</h2><div><p v-for="metric in metrics" :key="metric">{{ metric }}</p></div></section>
          <article class="story"><h2>经历详情</h2><MdPreview v-if="item.content?.trim()" :model-value="item.content" :theme="mode"/><p v-else>详细内容正在整理，可先查看上方公开的职责与成果。</p></article>
        </div>
      </section>
      <footer class="next page-shell"><p>继续了解</p><h2>真实经历之外，<br>还有持续写下的思考。</h2><nav><router-link to="/category">阅读技术文章</router-link><router-link to="/about">关于我</router-link></nav></footer>
    </template>
  </main>
</template>

<style scoped lang="scss">
.case-page{--accent:#e36f55;--ink:#ecf1f7;--muted:#a9b5c4;--faint:#748296;--line:rgba(232,239,247,.12);min-height:100dvh;padding-top:64px;background:#0f151d;color:var(--ink)}.page-shell{width:min(calc(100% - 3rem),84rem);margin:auto}.back{padding:2rem 0;border-bottom:1px solid var(--line)}.back button{padding:.35rem 0;border:0;background:none;color:var(--muted);font-size:.75rem;cursor:pointer}.back button:hover{color:var(--accent)}.case-hero{display:grid;grid-template-columns:minmax(0,1.3fr) minmax(18rem,.7fr);gap:clamp(4rem,10vw,11rem);align-items:end;padding:clamp(5rem,9vw,8rem) 0}.case-hero>div>p{margin:0;color:var(--accent);font-family:"Share TechMono",monospace;font-size:.72rem}.case-hero h1{max-width:13ch;margin:1.2rem 0 .8rem;font-size:clamp(3.7rem,7vw,7rem);line-height:.95;letter-spacing:-.075em}.case-hero h2{margin:0;color:var(--muted);font-size:clamp(1.1rem,2vw,1.5rem);font-weight:600}.case-hero aside{padding-top:1.3rem;border-top:2px solid var(--accent)}.case-hero aside span{color:var(--accent);font-family:"Share TechMono",monospace;font-size:.65rem;font-weight:700}.case-hero aside p{margin:1.2rem 0 0;font-size:1rem;font-weight:620;line-height:1.75}.case-cover{margin-bottom:5rem}.case-cover img{display:block;width:100%;max-height:42rem;object-fit:cover}.case-body{display:grid;grid-template-columns:minmax(13rem,18rem) minmax(0,1fr);gap:clamp(4rem,10vw,11rem);padding:clamp(5rem,9vw,8rem) 0;border-top:1px solid var(--line)}.facts{position:sticky;top:6rem;align-self:start}.facts h2,.work h2,.outcomes h2,.story>h2{margin:0 0 1.5rem;font-family:"Share TechMono",monospace;font-size:.7rem;letter-spacing:.08em}.facts dl{margin:0}.facts dl>div{padding:1rem 0;border-top:1px solid var(--line)}.facts dt{color:var(--faint);font-size:.65rem}.facts dd{margin:.35rem 0 0;color:var(--muted);font-size:.82rem;line-height:1.5}.tech{margin-top:3rem}.tech h3{color:var(--faint);font-family:"Share TechMono",monospace;font-size:.65rem;font-weight:500}.tech ul{display:flex;flex-wrap:wrap;gap:.4rem;margin:0;padding:0;list-style:none}.tech li{padding:.35rem .5rem;border:1px solid var(--line);border-radius:3px;color:var(--muted);font-family:"Share TechMono",monospace;font-size:.62rem}.work ol{margin:0;padding:0;list-style:none}.work li{display:grid;grid-template-columns:3rem 1fr;gap:1.2rem;padding:1.35rem 0;border-top:1px solid var(--line)}.work li span{color:var(--accent);font-family:"Share TechMono",monospace;font-size:.65rem}.work li p{margin:0;font-size:clamp(.95rem,1.3vw,1.08rem);line-height:1.75}.outcomes{margin-top:5rem}.outcomes>div{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:1px;background:var(--line)}.outcomes p{margin:0;padding:1.5rem;background:#0f151d;color:var(--muted);line-height:1.7}.story{margin-top:5rem;padding-top:2rem;border-top:1px solid var(--line)}.story>p{color:var(--muted);line-height:1.8}.story :deep(.md-editor){background:transparent}.story :deep(.md-editor-preview-wrapper){padding:0}.story :deep(.md-editor-preview){color:var(--ink);font-size:1rem;line-height:1.95}.story :deep(h1),.story :deep(h2),.story :deep(h3){color:var(--ink);letter-spacing:-.03em}.story :deep(a){color:var(--accent)}.story :deep(img){max-width:100%;height:auto}.next{display:grid;min-height:68dvh;align-content:center;border-top:1px solid var(--line)}.next>p{margin:0 0 1.3rem;color:var(--accent);font-family:"Share TechMono",monospace;font-size:.68rem}.next h2{max-width:16ch;margin:0;font-size:clamp(3rem,6vw,6rem);line-height:1;letter-spacing:-.07em}.next nav{display:flex;gap:1.7rem;margin-top:2.5rem}.next a{padding-bottom:.3rem;border-bottom:1px solid var(--muted);color:var(--ink);font-size:.78rem;text-decoration:none}.case-loading{padding:7rem 0}.case-loading span,.case-loading b,.case-loading i{display:block;background:#17212d;animation:pulse 1.2s ease-in-out infinite}.case-loading span{width:10rem;height:.8rem}.case-loading b{width:65%;height:6rem;margin-top:2rem}.case-loading i{width:40%;height:1.2rem;margin-top:1.5rem}.case-state{padding:8rem 0}.case-state h1{font-size:clamp(2.5rem,5vw,5rem)}.case-state p{color:var(--muted)}.case-state button{padding:.8rem 1rem;border:1px solid var(--accent);border-radius:3px;background:var(--accent);color:#10151d;font-weight:700;cursor:pointer}@keyframes pulse{50%{opacity:.45}}
@media(max-width:800px){.page-shell{width:min(calc(100% - 2rem),44rem)}.case-hero{grid-template-columns:1fr;gap:3rem;padding:4rem 0}.case-hero h1{font-size:clamp(3.2rem,13vw,5rem)}.case-body{grid-template-columns:1fr;gap:4rem}.facts{position:static}.facts dl{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:0 1.5rem}.outcomes>div{grid-template-columns:1fr}.next{min-height:62dvh}}
@media(max-width:480px){.case-page{padding-top:56px}.case-hero h1{font-size:clamp(2.7rem,12vw,3.2rem)}.facts dl{grid-template-columns:1fr}.work li{grid-template-columns:2rem 1fr}.next h2{font-size:2.7rem}}
@media(prefers-reduced-motion:reduce){.case-loading>*{animation:none}}
</style>
