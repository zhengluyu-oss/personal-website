<script setup lang="ts">
import useWebsiteStore from '@/store/modules/website'

const website = useWebsiteStore()
const hero = computed(() => {
  const info = website.webInfo
  return {
    kicker: info?.heroKicker ?? 'PERSONAL JOURNAL · SINCE 2024',
    title: info?.heroTitle ?? '你好，我是郑陆宇',
    subtitle: info?.heroSubtitle ?? '一名持续构建、记录与分享的开发者',
    description: info?.heroDescription ?? '在这里记录技术实践、项目复盘与成长轨迹，也分享那些值得被长期保存的思考。',
    primaryText: info?.heroPrimaryText ?? '浏览文章',
    primaryUrl: info?.heroPrimaryUrl ?? '/pigeonhole',
    secondaryText: info?.heroSecondaryText ?? '了解我',
    secondaryUrl: info?.heroSecondaryUrl ?? '/about',
    asideLabel: info?.heroAsideLabel ?? 'CURRENTLY',
    asideText: info?.heroAsideText ?? '专注于把想法变成真实、可靠且有温度的产品。',
  }
})

const primaryVisible = computed(() => Boolean(hero.value.primaryText.trim() && hero.value.primaryUrl.trim()))
const secondaryVisible = computed(() => Boolean(hero.value.secondaryText.trim() && hero.value.secondaryUrl.trim()))
const asideVisible = computed(() => Boolean(hero.value.asideLabel.trim() || hero.value.asideText.trim()))
const isExternal = (url: string) => /^https?:\/\//i.test(url)

</script>

<template>
  <section class="hero-copy">
    <div class="hero-layout">
      <main class="hero-main">
        <p v-if="hero.kicker" class="hero-kicker"><span />{{ hero.kicker }}</p>
        <h1 class="hero-title">{{ hero.title }}</h1>
        <p v-if="hero.subtitle" class="hero-subtitle">{{ hero.subtitle }}</p>
        <p v-if="hero.description" class="hero-description">{{ hero.description }}</p>

        <div v-if="primaryVisible || secondaryVisible" class="hero-actions">
          <a
            v-if="primaryVisible"
            class="hero-action hero-action--primary"
            :href="hero.primaryUrl"
            :target="isExternal(hero.primaryUrl) ? '_blank' : undefined"
            :rel="isExternal(hero.primaryUrl) ? 'noopener noreferrer' : undefined"
          >
            {{ hero.primaryText }}<span aria-hidden="true">↗</span>
          </a>
          <a
            v-if="secondaryVisible"
            class="hero-action hero-action--secondary"
            :href="hero.secondaryUrl"
            :target="isExternal(hero.secondaryUrl) ? '_blank' : undefined"
            :rel="isExternal(hero.secondaryUrl) ? 'noopener noreferrer' : undefined"
          >
            {{ hero.secondaryText }}<span aria-hidden="true">→</span>
          </a>
        </div>
      </main>

      <aside v-if="asideVisible" class="hero-aside">
        <div>
          <p v-if="hero.asideLabel" class="hero-aside__label">{{ hero.asideLabel }}</p>
          <p v-if="hero.asideText" class="hero-aside__text">{{ hero.asideText }}</p>
        </div>
      </aside>
    </div>

  </section>
</template>

<style scoped lang="scss">
.hero-copy {
  position: relative;
  z-index: 1;
  width: 100%;
  height: 100svh;
  min-height: 40rem;
  overflow: hidden;
  color: #fff;

  &::after {
    content: '';
    position: absolute;
    right: 0;
    bottom: 0;
    left: 0;
    height: 19vh;
    background: linear-gradient(to bottom, transparent, color-mix(in srgb, var(--mao-background-color) 86%, transparent) 82%, var(--mao-background-color));
    pointer-events: none;
  }
}

.hero-layout {
  position: fixed;
  z-index: 1;
  top: 0;
  left: 0;
  display: grid;
  width: 100%;
  height: 100svh;
  grid-template-columns: minmax(0, 1.3fr) minmax(16rem, .7fr);
  align-items: center;
  gap: clamp(3rem, 8vw, 10rem);
  padding: 5rem clamp(4rem, 8vw, 10rem) 6rem;
}

.hero-main {
  width: min(43rem, 100%);
  margin-top: 3vh;
}

.hero-kicker {
  display: flex;
  align-items: center;
  gap: .9rem;
  margin: 0 0 1.25rem;
  color: rgba(255, 226, 213, .84);
  font-size: .66rem;
  font-weight: 700;
  letter-spacing: .28em;
  line-height: 1.5;
  text-shadow: 0 2px 12px rgba(4, 8, 23, .6);

  span { width: 2.7rem; height: 1px; background: linear-gradient(90deg, #ff9d79, rgba(255,255,255,.12)); }
}

.hero-title {
  max-width: 10ch;
  margin: 0;
  color: #fff;
  font-family: Georgia, 'Songti SC', 'STSong', serif;
  font-size: clamp(3.7rem, 6.4vw, 7rem);
  font-weight: 500;
  line-height: .98;
  letter-spacing: -.055em;
  text-wrap: balance;
  text-shadow: 0 4px 5px rgba(6, 12, 30, .3), 0 20px 50px rgba(6, 12, 30, .42);
}

.hero-subtitle {
  margin: 1.6rem 0 0;
  color: rgba(255,255,255,.94);
  font-size: clamp(1rem, 1.5vw, 1.28rem);
  font-weight: 600;
  letter-spacing: .08em;
  line-height: 1.6;
  text-shadow: 0 3px 16px rgba(4, 8, 23, .7);
}

.hero-description {
  max-width: 39rem;
  margin: .8rem 0 0;
  color: rgba(237, 241, 255, .72);
  font-size: clamp(.86rem, 1.05vw, 1rem);
  letter-spacing: .035em;
  line-height: 1.9;
  text-shadow: 0 2px 12px rgba(4, 8, 23, .76);
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: .8rem;
  margin-top: 1.8rem;
}

.hero-action {
  display: inline-flex;
  min-width: 8.6rem;
  align-items: center;
  justify-content: space-between;
  gap: 1.4rem;
  padding: .8rem 1.05rem;
  border: 1px solid rgba(255,255,255,.22);
  border-radius: .35rem;
  color: #fff;
  font-size: .76rem;
  font-weight: 600;
  letter-spacing: .08em;
  text-decoration: none;
  transition: transform .25s ease, border-color .25s ease, background .25s ease;

  span { color: #ffad8b; font-size: 1rem; }
  &:hover { transform: translateY(-3px); border-color: rgba(255, 190, 164, .7); }
}

.hero-action--primary {
  border-color: rgba(255, 221, 206, .28);
  background: rgba(255,255,255,.14);
  box-shadow: inset 0 1px 0 rgba(255,255,255,.12), 0 12px 32px rgba(4,8,23,.16);
  backdrop-filter: blur(12px) saturate(120%);
}
.hero-action--secondary { background: rgba(8, 14, 34, .16); backdrop-filter: blur(8px); }

.hero-aside {
  position: relative;
  display: grid;
  max-width: 21rem;
  grid-template-columns: auto 1fr;
  align-items: start;
  gap: 1rem;
  padding: 1.15rem 1.2rem;
  border-top: 1px solid rgba(255,255,255,.28);
  border-bottom: 1px solid rgba(255,255,255,.12);
  background: linear-gradient(110deg, rgba(11,17,38,.24), rgba(11,17,38,.06));
  box-shadow: 0 18px 50px rgba(3,8,23,.16);
  backdrop-filter: blur(10px);
}

.hero-aside__index { color: #ffad8b; font-family: Georgia, serif; font-size: .7rem; letter-spacing: .12em; }
.hero-aside__label { margin: 0 0 .55rem; color: rgba(255,255,255,.62); font-size: .58rem; font-weight: 700; letter-spacing: .27em; }
.hero-aside__text { margin: 0; color: rgba(255,255,255,.9); font-size: .83rem; letter-spacing: .045em; line-height: 1.8; text-wrap: balance; }

.scroll-cue {
  position: absolute;
  z-index: 5;
  bottom: clamp(2rem, 5vh, 3.8rem);
  left: clamp(4rem, 8vw, 10rem);
  display: flex;
  align-items: center;
  gap: .8rem;
  padding: .4rem 0;
  border: 0;
  background: none;
  color: rgba(255,255,255,.66);
  cursor: pointer;

  span { font-size: .52rem; font-weight: 700; letter-spacing: .28em; }
  i { width: 2.8rem; height: 1px; background: linear-gradient(90deg, rgba(255,255,255,.75), transparent); animation: cue 2.4s ease-in-out infinite; }
}

@keyframes cue { 50% { width: 4rem; opacity: .55; } }

@media (max-width: 900px) {
  .hero-layout {
    grid-template-columns: 1fr;
    align-content: center;
    gap: 1.7rem;
    padding: 5rem 2rem 7rem;
  }
  .hero-main { margin-top: 0; }
  .hero-aside { max-width: min(28rem, 100%); }
  .scroll-cue { left: 2rem; }
}

@media (max-width: 640px) {
  .hero-copy { min-height: 42rem; }
  .hero-layout { padding: 4.5rem 1.3rem 6rem; }
  .hero-kicker { margin-bottom: .9rem; font-size: .56rem; letter-spacing: .19em; }
  .hero-kicker span { width: 1.8rem; }
  .hero-title { max-width: 9ch; font-size: clamp(3rem, 14vw, 4.5rem); line-height: 1.04; }
  .hero-subtitle { margin-top: 1.15rem; font-size: .92rem; }
  .hero-description { font-size: .78rem; line-height: 1.75; }
  .hero-actions { margin-top: 1.25rem; }
  .hero-action { min-width: 0; padding: .7rem .85rem; }
  .hero-aside { padding: .85rem .9rem; }
  .scroll-cue { bottom: 1.8rem; left: 1.3rem; }
}

@media (max-height: 700px) and (min-width: 901px) {
  .hero-layout { padding-top: 4rem; padding-bottom: 4rem; }
  .hero-title { font-size: clamp(3.2rem, 5.5vw, 5.5rem); }
  .hero-subtitle { margin-top: 1rem; }
  .hero-actions { margin-top: 1.1rem; }
  .scroll-cue { bottom: 1.2rem; }
}

@media (prefers-reduced-motion: reduce) {
  .hero-action { transition: none; }
  .scroll-cue i { animation: none; }
}
</style>
