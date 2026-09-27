<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import useWebsiteStore from '@/store/modules/website'
import { resolveHomeHero } from '@/utils/home-hero'
import Images from '../Images/index.vue'

const website = useWebsiteStore()
const hero = computed(() => resolveHomeHero(website.webInfo))
const actions = computed(() => [hero.value.primary, hero.value.secondary].filter(item => item !== null))
const shell = ref<HTMLElement>()
const visual = ref<HTMLElement>()
let cleanup = () => {}

onMounted(() => {
  const region = shell.value
  const media = visual.value
  if (!region || !media) return
  const preference = window.matchMedia('(min-width: 768px) and (hover: hover) and (pointer: fine) and (prefers-reduced-motion: no-preference)')
  let visible = true
  let frame = 0
  let bounds: DOMRect | undefined
  const reset = () => {
    cancelAnimationFrame(frame)
    frame = 0
    bounds = undefined
    media.style.removeProperty('transform')
  }
  const enter = () => { bounds = region.getBoundingClientRect() }
  const move = (event: PointerEvent) => {
    if (!preference.matches || !visible || !bounds) return
    const x = Math.max(-6, Math.min(6, ((event.clientX - bounds.left) / bounds.width - .5) * 12))
    const y = Math.max(-6, Math.min(6, ((event.clientY - bounds.top) / bounds.height - .5) * 12))
    cancelAnimationFrame(frame)
    frame = requestAnimationFrame(() => { media.style.transform = `translate(${x}px, ${y}px)`; frame = 0 })
  }
  const observer = new IntersectionObserver(([entry]) => { visible = entry.isIntersecting; if (!visible) reset() })
  observer.observe(region)
  region.addEventListener('pointerenter', enter)
  region.addEventListener('pointermove', move, { passive: true })
  region.addEventListener('pointerleave', reset)
  preference.addEventListener('change', reset)
  window.addEventListener('resize', reset)
  cleanup = () => {
    reset()
    observer.disconnect()
    region.removeEventListener('pointerenter', enter)
    region.removeEventListener('pointermove', move)
    region.removeEventListener('pointerleave', reset)
    preference.removeEventListener('change', reset)
    window.removeEventListener('resize', reset)
  }
})
onBeforeUnmount(() => cleanup())
</script>

<template>
  <div ref="shell" class="hero-shell">
    <div class="hero-copy">
      <div class="hero-identity">
        <span>{{ hero.identityRole }}</span>
        <small v-if="hero.kicker">{{ hero.kicker }}</small>
      </div>
      <h1>{{ hero.identityName }}</h1>
      <div class="hero-intro">
        <p class="hero-statement">{{ hero.title }}</p>
        <p class="hero-subtitle">{{ hero.subtitle }}</p>
      </div>
      <nav v-if="actions.length" class="hero-actions" aria-label="首页快捷入口">
        <template v-for="(action, index) in actions" :key="action.href">
          <a v-if="action.external" class="hero-link" :class="{ 'hero-link--solid': index === 0 }" :href="action.href" target="_blank" rel="noopener noreferrer">{{ action.text }}<span aria-hidden="true">↗</span></a>
          <RouterLink v-else class="hero-link" :class="{ 'hero-link--solid': index === 0 }" :to="action.href">{{ action.text }}<span aria-hidden="true">→</span></RouterLink>
        </template>
      </nav>
      <p v-if="hero.description" class="hero-description">{{ hero.description }}</p>
    </div>
    <div class="hero-visual">
      <div ref="visual" class="hero-media-motion"><Images /></div>
      <aside v-if="hero.asideLabel || hero.asideText" class="hero-aside" aria-label="个人简介摘要">
        <strong v-if="hero.asideLabel">{{ hero.asideLabel }}</strong>
        <p v-if="hero.asideText">{{ hero.asideText }}</p>
      </aside>
    </div>
  </div>
</template>

<style scoped lang="scss">
.hero-shell { display: grid; grid-template-columns: minmax(0, 1.15fr) minmax(0, 1fr); align-items: center; gap: 1.5rem clamp(2rem, 5vw, 4.5rem); padding: 2.5rem 0 2rem; }
.hero-copy { min-width: 0; }
.hero-identity { display: flex; flex-wrap: wrap; align-items: center; gap: .45rem 1rem; margin: 0 0 .9rem; color: var(--brand-accent-strong); font-family: "Share TechMono", Consolas, monospace; overflow-wrap: anywhere; }
.hero-identity span { font-size: .9rem; font-weight: 700; letter-spacing: .04em; }
.hero-identity small { color: var(--brand-ink-faint); font-size: .72rem; letter-spacing: .08em; }
.hero-copy h1 { margin: 0; color: var(--brand-ink); font-family: "阿里妈妈方圆体 VF Regular", "PingFang SC", sans-serif; font-size: clamp(3rem, 5.8vw, 5.25rem); font-weight: 740; line-height: 1.08; letter-spacing: -.055em; text-wrap: balance; overflow-wrap: anywhere; }
.hero-intro { max-width: 38rem; margin-top: 1.1rem; }
.hero-statement { margin: 0; color: var(--brand-ink); font-size: clamp(1.2rem, 1.9vw, 1.55rem); font-weight: 650; line-height: 1.5; overflow-wrap: anywhere; }
.hero-subtitle { margin: .65rem 0 0; color: var(--brand-ink-soft); font-size: 1rem; line-height: 1.75; overflow-wrap: anywhere; }
.hero-actions { display: flex; flex-wrap: wrap; gap: .75rem; margin-top: 1.75rem; }
.hero-link { display: inline-flex; align-items: center; justify-content: center; gap: 1rem; min-height: 2.85rem; padding: .7rem 1.15rem; border: 1px solid var(--brand-line); border-radius: var(--brand-radius-sm); color: var(--brand-ink); background: var(--brand-surface); font-size: .95rem; font-weight: 650; text-decoration: none; white-space: nowrap; transition: background .2s, transform .2s; }
.hero-link--solid { color: #fff; background: var(--brand-accent-strong); border-color: var(--brand-accent-strong); }
.hero-link:hover { background: var(--brand-accent-soft); }
.hero-link--solid:hover { background: var(--brand-accent); }
.hero-link:active { transform: translateY(1px); }
.hero-link:focus-visible { outline: 2px solid var(--brand-accent); outline-offset: 4px; }
.hero-visual { position: relative; min-width: 0; padding-top: 1.5rem; }
.hero-visual::before { content: ''; position: absolute; inset: 0 -1rem 2rem 12%; z-index: 0; border-radius: var(--brand-radius-md); background: var(--brand-accent-soft); pointer-events: none; }
.hero-media-motion, .hero-aside { position: relative; }
.hero-media-motion { transition: transform 180ms ease-out; }
.hero-aside { display: grid; gap: .35rem; margin-top: 1rem; padding-left: 1rem; border-left: 2px solid var(--brand-line); color: var(--brand-ink-soft); font-size: .95rem; line-height: 1.65; overflow-wrap: anywhere; }
.hero-aside strong { color: var(--brand-ink); font-weight: 600; }
.hero-aside p { margin: 0; white-space: pre-line; }
.hero-description { margin: 1.4rem 0 0; max-width: 54ch; color: var(--brand-ink-soft); font-size: .9rem; line-height: 1.75; white-space: pre-line; overflow-wrap: anywhere; }
@media (min-width: 768px) and (hover: hover) and (pointer: fine) and (prefers-reduced-motion: no-preference) {
  .hero-visual { animation: hero-arrive 500ms cubic-bezier(.2,.7,.2,1) both; }
  @keyframes hero-arrive { from { transform: translateY(10px); opacity: .8; } to { transform: none; opacity: 1; } }
}
@media (max-width: 767px) {
  .hero-shell { grid-template-columns: 1fr; gap: 1.75rem; padding: 1.75rem 0; }
  .hero-visual { padding-top: 0; }
  .hero-visual::before { inset: .8rem -.35rem -.7rem 12%; }
  .hero-copy h1 { font-size: clamp(2.7rem, 14vw, 4rem); }
  .hero-statement { font-size: 1.18rem; }
  .hero-actions { margin-top: 1.25rem; }
  .hero-link { gap: .65rem; padding: .7rem .9rem; font-size: .9rem; }
}
@media (prefers-reduced-motion: reduce), (hover: none), (pointer: coarse), (max-width: 767px) { .hero-link, .hero-media-motion, .hero-visual { animation: none; transition: none; transform: none !important; } }
</style>
