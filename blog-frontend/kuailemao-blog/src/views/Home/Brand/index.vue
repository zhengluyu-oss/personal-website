<script setup lang="ts">
import { computed } from 'vue'
import useWebsiteStore from '@/store/modules/website'
import { resolveHomeHero } from '@/utils/home-hero'
import Images from '../Images/index.vue'

const website = useWebsiteStore()
const hero = computed(() => resolveHomeHero(website.webInfo))
const actions = computed(() => [hero.value.primary, hero.value.secondary].filter(item => item !== null))
</script>

<template>
  <div class="hero-shell">
    <div class="hero-copy">
      <div class="hero-identity">
        <span>{{ hero.identityRole }}</span>
        <small v-if="hero.kicker">{{ hero.kicker }}</small>
      </div>
      <h1>{{ hero.identityName }}</h1>
      <div class="hero-intro">
        <p class="hero-statement">{{ hero.title }}</p>
        <p class="hero-subtitle">{{ hero.subtitle }}</p>
        <p v-if="hero.description" class="hero-description">{{ hero.description }}</p>
      </div>
      <nav v-if="actions.length" class="hero-actions" aria-label="首页快捷入口">
        <template v-for="(action, index) in actions" :key="action.href">
          <a v-if="action.external" class="hero-link" :class="{ 'hero-link--solid': index === 0 }" :href="action.href" target="_blank" rel="noopener noreferrer">{{ action.text }}<span aria-hidden="true">↗</span></a>
          <RouterLink v-else class="hero-link" :class="{ 'hero-link--solid': index === 0 }" :to="action.href">{{ action.text }}<span aria-hidden="true">→</span></RouterLink>
        </template>
      </nav>
    </div>
    <div class="hero-visual">
      <Images />
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
.hero-visual { min-width: 0; }
.hero-aside { display: grid; gap: .35rem; margin-top: 1rem; padding-left: 1rem; border-left: 2px solid var(--brand-line); color: var(--brand-ink-soft); font-size: .95rem; line-height: 1.65; overflow-wrap: anywhere; }
.hero-aside strong { color: var(--brand-ink); font-weight: 600; }
.hero-aside p { margin: 0; white-space: pre-line; }
.hero-description { margin: .65rem 0 0; max-width: 62ch; color: var(--brand-ink-soft); font-size: .92rem; line-height: 1.75; white-space: pre-line; overflow-wrap: anywhere; }
@media (max-width: 767px) {
  .hero-shell { grid-template-columns: 1fr; gap: 1.75rem; padding: 1.75rem 0; }
  .hero-copy h1 { font-size: clamp(2.7rem, 14vw, 4rem); }
  .hero-statement { font-size: 1.18rem; }
  .hero-actions { margin-top: 1.25rem; }
  .hero-link { gap: .65rem; padding: .7rem .9rem; font-size: .9rem; }
}
@media (prefers-reduced-motion: reduce) { .hero-link { transition: none; transform: none; } }
</style>
