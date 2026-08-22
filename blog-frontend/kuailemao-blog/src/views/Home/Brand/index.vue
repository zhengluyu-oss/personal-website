<script setup lang="ts">
import { computed } from 'vue'
import useWebsiteStore from '@/store/modules/website'

const website = useWebsiteStore()
const hero = computed(() => {
  const info = website.webInfo
  return {
    kicker: info?.heroKicker || 'DEVELOPER / WRITER',
    title: info?.heroTitle || '陆屿，持续构建真实产品的开发者。',
    subtitle: info?.heroSubtitle || '把复杂问题想清楚，再把它稳定地交付出来。',
    primaryText: info?.heroPrimaryText || '查看工作经历',
    primaryUrl: info?.heroPrimaryUrl || '/experience',
    secondaryText: info?.heroSecondaryText || '阅读博客',
    secondaryUrl: info?.heroSecondaryUrl || '/category',
  }
})
const isExternal = (url: string) => /^https?:\/\//i.test(url)
</script>

<template>
  <div class="hero-shell">
    <div class="hero-copy">
      <p class="hero-kicker">{{ hero.kicker }}</p>
      <h1>{{ hero.title }}</h1>
      <p class="hero-subtitle">{{ hero.subtitle }}</p>
      <nav class="hero-actions" aria-label="首页快捷入口">
        <a class="hero-link hero-link--solid" :href="hero.primaryUrl" :target="isExternal(hero.primaryUrl) ? '_blank' : undefined" :rel="isExternal(hero.primaryUrl) ? 'noopener noreferrer' : undefined">{{ hero.primaryText }}<span aria-hidden="true">↗</span></a>
        <a class="hero-link" :href="hero.secondaryUrl" :target="isExternal(hero.secondaryUrl) ? '_blank' : undefined" :rel="isExternal(hero.secondaryUrl) ? 'noopener noreferrer' : undefined">{{ hero.secondaryText }}<span aria-hidden="true">→</span></a>
      </nav>
    </div>
    <div class="signature" aria-hidden="true">
      <span class="signature-lu">陆</span><span class="signature-yu">屿</span>
      <i class="signature-axis signature-axis--x" /><i class="signature-axis signature-axis--y" />
    </div>
  </div>
</template>

<style scoped lang="scss">
.hero-shell{position:relative;z-index:2;display:grid;width:min(calc(100% - 3rem),92rem);min-height:100dvh;margin:auto;grid-template-columns:minmax(0,1.05fr) minmax(22rem,.95fr);align-items:center;gap:clamp(2rem,7vw,8rem);padding:clamp(5.5rem,9vh,7rem) 0 3rem;color:#f7f9fc}.hero-copy{max-width:48rem;animation:hero-enter .65s cubic-bezier(.2,.7,.2,1) both}.hero-kicker{margin:0 0 1.3rem;color:#ff9a83;font-family:"Share TechMono","SFMono-Regular",Consolas,monospace;font-size:.72rem;font-weight:700;letter-spacing:.18em}.hero-copy h1{max-width:13ch;margin:0;font-family:"阿里妈妈方圆体 VF Regular","PingFang SC",sans-serif;font-size:clamp(3.8rem,7vw,7.3rem);font-weight:760;line-height:.98;letter-spacing:-.07em;text-wrap:balance;text-shadow:0 20px 60px rgba(3,8,20,.38)}.hero-subtitle{max-width:31rem;margin:1.6rem 0 0;color:rgba(242,246,255,.78);font-size:clamp(.95rem,1.25vw,1.12rem);line-height:1.75}.hero-actions{display:flex;flex-wrap:wrap;gap:.7rem;margin-top:1.8rem}.hero-link{display:inline-flex;min-height:2.8rem;align-items:center;justify-content:space-between;gap:2rem;padding:.72rem 1rem;border:1px solid rgba(255,255,255,.3);border-radius:4px;color:#fff;font-size:.78rem;font-weight:700;text-decoration:none;transition:transform .22s ease,border-color .22s ease,background .22s ease}.hero-link span{color:#ff9a83}.hero-link:hover{transform:translateY(-2px);border-color:#ff9a83}.hero-link:active{transform:translateY(1px)}.hero-link--solid{background:#f6f8fc;color:#101722;border-color:#f6f8fc}.hero-link--solid span{color:#b84631}.signature{position:relative;width:min(37vw,34rem);aspect-ratio:1;justify-self:end;border:1px solid rgba(255,255,255,.14);background:linear-gradient(rgba(255,255,255,.035) 1px,transparent 1px),linear-gradient(90deg,rgba(255,255,255,.035) 1px,transparent 1px);background-size:12.5% 12.5%;overflow:hidden;animation:signature-enter .8s .1s cubic-bezier(.2,.7,.2,1) both}.signature::before{content:'';position:absolute;inset:12.5%;border:1px solid rgba(255,154,131,.38);transform:rotate(4deg)}.signature-lu,.signature-yu{position:absolute;font-size:clamp(8rem,18vw,18rem);font-weight:800;line-height:.78;letter-spacing:-.15em}.signature-lu{top:5%;left:-3%;color:rgba(255,255,255,.94)}.signature-yu{right:3%;bottom:4%;color:transparent;-webkit-text-stroke:1px rgba(255,154,131,.86)}.signature-axis{position:absolute;background:rgba(255,255,255,.24)}.signature-axis--x{top:50%;left:0;width:100%;height:1px}.signature-axis--y{top:0;left:50%;width:1px;height:100%}@keyframes hero-enter{from{opacity:0;transform:translateY(18px)}}@keyframes signature-enter{from{opacity:0;transform:translateX(22px)}}
@media(max-width:900px){.hero-shell{grid-template-columns:1fr;width:min(calc(100% - 2rem),46rem);align-content:center;gap:1.6rem}.signature{position:absolute;right:-18%;bottom:5%;z-index:-1;width:72vw;max-width:28rem;opacity:.42}.hero-copy h1{font-size:clamp(3.4rem,12vw,6rem)}}
@media(max-width:520px){.hero-shell{min-height:100dvh;padding-top:5rem}.hero-kicker{margin-bottom:1rem}.hero-copy h1{max-width:100%;font-size:clamp(2.35rem,10.8vw,3rem);line-height:1.04;letter-spacing:-.065em}.hero-subtitle{max-width:19rem;font-size:.88rem}.hero-actions{margin-top:1.4rem}.hero-link{gap:1.2rem}.signature{right:-34%;bottom:6%;width:94vw}}
@media(prefers-reduced-motion:reduce){.hero-copy,.signature{animation:none}.hero-link{transition:none}}
</style>
