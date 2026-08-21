<script setup lang="ts">
import { experienceList, type WorkExperienceItem } from '@/apis/experience'
const router=useRouter(),loading=ref(true),list=ref<WorkExperienceItem[]>([])
onMounted(async()=>{try{const r:any=await experienceList();if(r.code===200)list.value=r.data||[]}finally{loading.value=false}})
const date=(v?:string)=>v?v.toString().slice(0,7).replace('-','.') : ''
const period=(x:WorkExperienceItem)=>x.isCurrent===1?`${date(x.startDate)} — 现在`:`${date(x.startDate)} — ${date(x.endDate)}`
const lines=(v?:string)=>v?v.split(/\r?\n/).map(x=>x.trim()).filter(Boolean):[]
</script>
<template>
<main class="page">
  <header class="intro">
    <div><p class="overline">SELECTED EXPERIENCE · {{ String(list.length).padStart(2,'0') }}</p><h1>职业不是时间线，<br><em>是持续解决问题。</em></h1></div>
    <div class="manifesto"><span>2019—NOW</span><p>从真实业务出发，把复杂问题拆开，把想法做成稳定、清晰、可以长期演进的产品。</p></div>
  </header>
  <div class="rule"><span>CAREER INDEX</span><i/><span>SCROLL TO READ</span></div>
  <div v-if="loading" class="state">正在加载经历档案…</div>
  <div v-else-if="!list.length" class="state">暂时还没有公开的工作经历。</div>
  <section v-else class="index">
    <article v-for="(item,i) in list" :key="item.id" tabindex="0" class="row" @click="router.push(`/experience/${item.id}`)" @keydown.enter="router.push(`/experience/${item.id}`)">
      <span class="num">{{ String(i+1).padStart(2,'0') }}</span>
      <div class="when"><span v-if="item.isCurrent===1" class="live">● CURRENT</span><b>{{ period(item) }}</b></div>
      <div class="identity"><p>{{ item.roleTitle }}</p><h2>{{ item.company }}</h2></div>
      <ul><li v-for="(x,n) in lines(item.highlights).slice(0,3)" :key="n">{{ x }}</li></ul>
      <span class="arrow">↗</span><span class="watermark">{{ String(i+1).padStart(2,'0') }}</span>
    </article>
  </section>
  <footer class="closing"><span>下一步</span><p>继续学习，继续构建，继续把事情做得更好。</p></footer>
</main>
</template>
<style scoped lang="scss">
.page{min-height:100vh;padding:10rem max(1.2rem,6vw) 7rem;background:linear-gradient(140deg,color-mix(in srgb,var(--mao-background-color) 96%,#5267ff),var(--mao-background-color) 35%);color:var(--el-text-color-primary)}
.intro{display:grid;max-width:82rem;margin:auto;grid-template-columns:1.55fr .65fr;align-items:end;gap:6vw}.overline{margin:0 0 1.5rem;color:#6c78ff;font-size:.65rem;font-weight:800;letter-spacing:.25em}.intro h1{margin:0;font-family:Georgia,'Songti SC',serif;font-size:clamp(3.5rem,7.3vw,7rem);font-weight:500;line-height:.96;letter-spacing:-.065em}.intro h1 em{font-weight:500;color:transparent;-webkit-text-stroke:1px #6c78ff}.manifesto{padding:0 0 .6rem 1.4rem;border-left:1px solid #6c78ff}.manifesto span{color:#6c78ff;font-size:.62rem;font-weight:800;letter-spacing:.16em}.manifesto p{margin:1rem 0 0;color:var(--el-text-color-secondary);font-size:.9rem;line-height:1.9}.rule{display:flex;max-width:82rem;align-items:center;gap:1rem;margin:6rem auto 0;color:var(--el-text-color-placeholder);font-size:.55rem;font-weight:800;letter-spacing:.2em}.rule i{height:1px;flex:1;background:var(--el-border-color)}
.index{max-width:82rem;margin:1rem auto 0;border-top:1px solid var(--el-border-color)}.row{position:relative;display:grid;overflow:hidden;grid-template-columns:3rem 10rem minmax(15rem,.8fr) minmax(18rem,1.2fr) 2.5rem;align-items:center;gap:1.5rem;min-height:13rem;padding:2rem .4rem;border-bottom:1px solid var(--el-border-color);cursor:pointer;isolation:isolate;transition:color .3s,padding .35s}.row:before{content:'';position:absolute;inset:0;z-index:-2;background:#5968ed;transform:scaleY(0);transform-origin:bottom;transition:transform .4s cubic-bezier(.2,.8,.2,1)}.row:hover,.row:focus-visible{padding-right:1.5rem;padding-left:1.5rem;color:#fff;outline:none}.row:hover:before,.row:focus-visible:before{transform:scaleY(1)}.num{font-family:Georgia,serif;color:#6c78ff;font-style:italic}.row:hover .num{color:#fff}.when{display:flex;flex-direction:column;gap:.7rem}.when b{font-size:.66rem;letter-spacing:.08em}.live{color:#20a56b;font-size:.54rem;font-weight:800;letter-spacing:.14em}.row:hover .live{color:#b8ffd8}.identity p{margin:0 0 .5rem;color:var(--el-text-color-secondary);font-size:.7rem;letter-spacing:.08em}.row:hover .identity p{color:rgba(255,255,255,.7)}.identity h2{margin:0;font-family:Georgia,'Songti SC',serif;font-size:clamp(1.7rem,3.2vw,3rem);font-weight:500;line-height:1.05;letter-spacing:-.035em}.row ul{margin:0;padding:0;list-style:none;color:var(--el-text-color-secondary);font-size:.76rem;line-height:1.65}.row li+li{margin-top:.45rem}.row li:before{content:'—';margin-right:.5rem;color:#6c78ff}.row:hover ul{color:rgba(255,255,255,.82)}.row:hover li:before{color:#fff}.arrow{font-size:1.4rem;transition:transform .3s}.row:hover .arrow{transform:translate(4px,-4px)}.watermark{position:absolute;z-index:-1;right:5%;bottom:-.38em;color:transparent;font:italic 9rem Georgia;-webkit-text-stroke:1px rgba(255,255,255,.11);opacity:0;transform:translateY(20px);transition:.4s}.row:hover .watermark{opacity:1;transform:none}
.state{max-width:82rem;margin:2rem auto;padding:5rem;border:1px dashed var(--el-border-color);text-align:center;color:var(--el-text-color-secondary)}.closing{display:flex;max-width:82rem;margin:6rem auto 0;align-items:baseline;gap:2rem;border-top:1px solid var(--el-border-color);padding-top:2rem}.closing span{color:#6c78ff;font-size:.62rem;font-weight:800;letter-spacing:.18em}.closing p{margin:0;font-family:Georgia,'Songti SC',serif;font-size:clamp(1.4rem,2.5vw,2.2rem)}
@media(max-width:900px){.page{padding-top:7rem}.intro{grid-template-columns:1fr}.manifesto{max-width:32rem}.row{grid-template-columns:2rem 1fr 2rem;gap:1rem}.when{grid-column:2}.identity{grid-column:2}.row ul{grid-column:2}.arrow{grid-column:3;grid-row:1/4}.rule{margin-top:4rem}}
@media(max-width:560px){
  .page{padding:calc(5.25rem + env(safe-area-inset-top)) max(1rem,env(safe-area-inset-right)) calc(3rem + env(safe-area-inset-bottom)) max(1rem,env(safe-area-inset-left));background:linear-gradient(160deg,color-mix(in srgb,var(--mao-background-color) 92%,#5267ff),var(--mao-background-color) 22rem)}
  .intro{gap:2rem}.overline{margin-bottom:1rem;font-size:.58rem;letter-spacing:.18em}.intro h1{font-size:clamp(2.55rem,12.5vw,3.65rem);line-height:1.02;letter-spacing:-.055em}.intro h1 br{display:none}.intro h1 em{display:block;margin-top:.18em;-webkit-text-stroke:.8px #6c78ff}
  .manifesto{padding:.9rem 0 0;border-top:1px solid color-mix(in srgb,#6c78ff 45%,transparent);border-left:0}.manifesto p{margin-top:.65rem;font-size:.82rem;line-height:1.75}
  .rule{margin-top:3rem;gap:.65rem}.rule span:last-child{display:none}.index{margin-top:.75rem}
  .row{grid-template-columns:2.2rem minmax(0,1fr) 2rem;grid-template-rows:auto auto auto;gap:.65rem .8rem;min-height:0;padding:1.35rem .15rem;touch-action:manipulation}.num{grid-column:1;grid-row:1;font-size:.88rem}.when{grid-column:2;grid-row:1;flex-direction:row;align-items:center;flex-wrap:wrap;gap:.45rem .7rem}.when b{font-size:.61rem}.identity{grid-column:1/4;grid-row:2;padding-top:.25rem}.identity p{margin-bottom:.35rem;font-size:.65rem}.identity h2{font-size:clamp(1.65rem,8.5vw,2.3rem);line-height:1.08;overflow-wrap:anywhere}.row ul{display:-webkit-box;grid-column:1/4;grid-row:3;margin-top:.3rem;overflow:hidden;color:var(--el-text-color-secondary);font-size:.75rem;line-height:1.7;-webkit-box-orient:vertical;-webkit-line-clamp:3}.row li{display:inline}.row li+li{margin:0}.row li+li:before{content:' · ';margin:0 .25rem;color:#6c78ff}.row li:first-child:before{display:none}.arrow{grid-column:3;grid-row:1;align-self:start;justify-self:end;font-size:1.2rem}.watermark{display:none}
  .row:hover,.row:focus-visible{padding-right:.65rem;padding-left:.65rem}.row:active{padding-right:.65rem;padding-left:.65rem;color:#fff}.row:active:before{transform:scaleY(1)}.row:active .num,.row:active li:before{color:#fff}.row:active .identity p,.row:active ul{color:rgba(255,255,255,.8)}
  .state{margin:1rem auto;padding:3.5rem 1rem;line-height:1.7}.closing{margin-top:4rem;align-items:flex-start;flex-direction:column;gap:.65rem}.closing p{font-size:1.45rem;line-height:1.45}
}
@media(max-width:380px){.page{padding-right:.85rem;padding-left:.85rem}.intro h1{font-size:2.45rem}.identity h2{font-size:1.65rem}.row{grid-template-columns:1.8rem minmax(0,1fr) 1.6rem;column-gap:.55rem}}
@media(prefers-reduced-motion:reduce){.row,.row:before,.arrow,.watermark{transition:none!important}}
</style>
