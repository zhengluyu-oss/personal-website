<script setup lang="ts">
import { computed } from 'vue'
import useWebsiteStore from '@/store/modules/website'
import { HERO_SIDE_LINES } from '@/config/site'

const website = useWebsiteStore()
const hero = computed(() => {
  const info = website.webInfo
  const sideLines = [
    info?.heroSideLine1,
    info?.heroSideLine2,
    info?.heroSideLine3,
  ].filter((line): line is string => Boolean(line?.trim()))

  return {
    kicker: info?.heroKicker || 'DEVELOPER / WRITER',
    title: info?.heroTitle || '陆屿，持续构建真实产品的开发者。',
    subtitle: info?.heroSubtitle || '把复杂问题想清楚，再把它稳定地交付出来。',
    primaryText: info?.heroPrimaryText || '查看工作经历',
    primaryUrl: info?.heroPrimaryUrl || '/experience',
    secondaryText: info?.heroSecondaryText || '阅读博客',
    secondaryUrl: info?.heroSecondaryUrl || '/blog',
    sideLines: sideLines.length ? sideLines : [...HERO_SIDE_LINES],
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
    <aside class="hero-aside" aria-label="个人简介摘要">
      <p v-for="(line, index) in hero.sideLines" :key="index">{{ line }}</p>
    </aside>
  </div>
</template>

<style scoped lang="scss">
.hero-shell {
  position: relative;
  z-index: 2;
  display: grid;
  width: min(calc(100% - 3rem), 92rem);
  min-height: 100dvh;
  margin: auto;
  grid-template-columns: minmax(0, 1.05fr) minmax(16rem, 0.95fr);
  align-items: center;
  gap: clamp(2rem, 7vw, 8rem);
  padding: clamp(5.5rem, 9vh, 7rem) 0 3rem;
  color: #f7f9fc;
}

.hero-copy {
  max-width: 48rem;
  animation: hero-enter 0.65s cubic-bezier(0.2, 0.7, 0.2, 1) both;
}

.hero-kicker {
  margin: 0 0 1.3rem;
  color: #ff9a83;
  font-family: "Share TechMono", "SFMono-Regular", Consolas, monospace;
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.18em;
}

.hero-copy h1 {
  max-width: 13ch;
  margin: 0;
  font-family: "阿里妈妈方圆体 VF Regular", "PingFang SC", sans-serif;
  font-size: clamp(3.8rem, 7vw, 7.3rem);
  font-weight: 760;
  line-height: 0.98;
  letter-spacing: -0.07em;
  text-wrap: balance;
  text-shadow: 0 20px 60px rgba(3, 8, 20, 0.38);
}

.hero-subtitle {
  max-width: 31rem;
  margin: 1.6rem 0 0;
  color: rgba(242, 246, 255, 0.78);
  font-size: clamp(0.95rem, 1.25vw, 1.12rem);
  line-height: 1.75;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.7rem;
  margin-top: 1.8rem;
}

.hero-link {
  display: inline-flex;
  min-height: 2.8rem;
  align-items: center;
  justify-content: space-between;
  gap: 2rem;
  padding: 0.72rem 1rem;
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 4px;
  color: #fff;
  font-size: 0.78rem;
  font-weight: 700;
  text-decoration: none;
  transition: transform 0.22s ease, border-color 0.22s ease, background 0.22s ease;
}

.hero-link span {
  color: #ff9a83;
}

.hero-link:hover {
  transform: translateY(-2px);
  border-color: #ff9a83;
}

.hero-link:active {
  transform: translateY(1px);
}

.hero-link--solid {
  background: #f6f8fc;
  color: #101722;
  border-color: #f6f8fc;
}

.hero-link--solid span {
  color: #b84631;
}

.hero-aside {
  justify-self: end;
  max-width: 18rem;
  padding: 1.25rem 0;
  border-top: 1px solid rgba(255, 255, 255, 0.18);
  border-bottom: 1px solid rgba(255, 255, 255, 0.18);
  text-align: right;
  animation: hero-aside-enter 0.7s 0.12s cubic-bezier(0.2, 0.7, 0.2, 1) both;
}

.hero-aside p {
  margin: 0;
  color: rgba(242, 246, 255, 0.72);
  font-family: "Share TechMono", "SFMono-Regular", Consolas, monospace;
  font-size: clamp(0.72rem, 1vw, 0.84rem);
  font-weight: 400;
  line-height: 1.7;
  letter-spacing: 0.02em;
  text-shadow: 0 8px 24px rgba(3, 8, 20, 0.32);
}

.hero-aside p + p {
  margin-top: 0.55rem;
}

@keyframes hero-enter {
  from {
    opacity: 0;
    transform: translateY(18px);
  }
}

@keyframes hero-aside-enter {
  from {
    opacity: 0;
    transform: translateX(12px);
  }
}

@media (max-width: 900px) {
  .hero-shell {
    grid-template-columns: 1fr;
    width: min(calc(100% - 2rem), 46rem);
    align-content: center;
    gap: 1.6rem;
  }

  .hero-copy h1 {
    font-size: clamp(3.4rem, 12vw, 6rem);
  }

  .hero-aside {
    justify-self: start;
    max-width: 100%;
    text-align: left;
    padding: 1rem 0 0;
    border-top: 1px solid rgba(255, 255, 255, 0.14);
    border-bottom: none;
  }
}

@media (max-width: 520px) {
  .hero-shell {
    min-height: 100dvh;
    padding-top: 5rem;
  }

  .hero-kicker {
    margin-bottom: 1rem;
  }

  .hero-copy h1 {
    max-width: 100%;
    font-size: clamp(2.35rem, 10.8vw, 3rem);
    line-height: 1.04;
    letter-spacing: -0.065em;
  }

  .hero-subtitle {
    max-width: 19rem;
    font-size: 0.88rem;
  }

  .hero-actions {
    margin-top: 1.4rem;
  }

  .hero-link {
    gap: 1.2rem;
  }

  .hero-aside p {
    font-size: 0.72rem;
  }
}

@media (prefers-reduced-motion: reduce) {
  .hero-copy,
  .hero-aside {
    animation: none;
  }

  .hero-link {
    transition: none;
  }
}
</style>
