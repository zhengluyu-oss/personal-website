<script setup lang="ts">
import useWebsiteStore from '@/store/modules/website'
import { getSoupTyping } from '@/apis/thirdParty'

const useWebsite = useWebsiteStore()
const obj = reactive({
  output: '',
  isEnd: false,
  speed: 300,
  singleBack: false,
  sleep: 3000,
  type: 'rollback',
  backSpeed: 100,
  sentencePause: false,
})

function scrollDown() {
  window.scrollTo({ behavior: 'smooth', top: document.documentElement.clientHeight })
}

onMounted(() => getSoupTyping(obj))
</script>

<template>
  <section class="brand-container">
    <div class="brand">
      <div class="brand-kicker"><span /> PERSONAL JOURNAL · SINCE 2024 <span /></div>
      <h1 class="brand-title" :data-text="useWebsite?.webInfo?.websiteName || '个人博客'">
        {{ useWebsite?.webInfo?.websiteName || '个人博客' }}
      </h1>
      <div class="brand-rule"><i /><span>THINK · BUILD · SHARE</span><i /></div>
      <div class="brand-text">
        <span class="quote-mark">“</span>
        <p class="title">{{ obj.output || '在这里，记录思考与持续生长。' }}<span class="easy-typed-cursor">|</span></p>
      </div>
    </div>

    <button class="scroll-cue" type="button" aria-label="向下浏览首页内容" @click="scrollDown">
      <span class="scroll-cue__label">SCROLL TO EXPLORE</span>
      <span class="scroll-cue__circle"><i /></span>
    </button>
  </section>
</template>

<style scoped lang="scss">
.brand-container {
  position: relative;
  display: grid;
  width: 100%;
  height: 100svh;
  min-height: 38rem;
  place-items: center;
  overflow: hidden;
  color: #fff;

  &::after {
    content: '';
    position: absolute;
    right: 0;
    bottom: 0;
    left: 0;
    height: 18vh;
    background: linear-gradient(to bottom, transparent, color-mix(in srgb, var(--mao-background-color) 88%, transparent) 82%, var(--mao-background-color));
    pointer-events: none;
  }
}

.brand {
  position: fixed;
  z-index: -1;
  top: 50%;
  left: 50%;
  display: flex;
  width: min(72rem, calc(100% - 3rem));
  align-items: center;
  flex-direction: column;
  transform: translate(-50%, -52%);
  text-align: center;
}

.brand-kicker {
  display: flex;
  align-items: center;
  gap: .9rem;
  margin-bottom: 1.5rem;
  color: rgba(255,255,255,.74);
  font-size: .62rem;
  font-weight: 700;
  letter-spacing: .28em;
  text-shadow: 0 2px 12px rgba(5,10,25,.5);

  span { width: 2.8rem; height: 1px; background: linear-gradient(90deg, transparent, rgba(255,255,255,.7)); }
  span:last-child { transform: rotate(180deg); }
}

.brand-title {
  position: relative;
  margin: 0;
  color: #fff;
  font-family: Georgia, 'Songti SC', 'STSong', serif;
  font-size: clamp(3.6rem, 7.4vw, 7.2rem);
  font-weight: 500;
  line-height: 1.04;
  letter-spacing: -.055em;
  text-wrap: balance;
  text-shadow: 0 3px 3px rgba(9,15,35,.28), 0 16px 42px rgba(7,12,28,.42);

  &::after {
    content: attr(data-text);
    position: absolute;
    inset: 0;
    z-index: -1;
    color: transparent;
    -webkit-text-stroke: 1px rgba(255, 184, 160, .34);
    transform: translate(3px, 4px);
  }
}

.brand-rule {
  display: flex;
  width: min(30rem, 76vw);
  align-items: center;
  gap: .8rem;
  margin: 1.6rem 0 1.35rem;
  color: rgba(255,255,255,.58);
  font-size: .54rem;
  font-weight: 700;
  letter-spacing: .22em;

  i { height: 1px; flex: 1; background: linear-gradient(90deg, transparent, rgba(255,255,255,.36)); }
  i:last-child { transform: rotate(180deg); }
}

.brand-text {
  display: flex;
  max-width: min(44rem, calc(100vw - 2rem));
  align-items: flex-start;
  gap: .65rem;
  padding: .85rem 1.25rem .9rem 1rem;
  border: 1px solid rgba(255,255,255,.18);
  border-radius: 999px;
  background: rgba(12,18,39,.25);
  box-shadow: inset 0 1px 0 rgba(255,255,255,.08), 0 12px 45px rgba(5,10,25,.18);
  backdrop-filter: blur(14px) saturate(115%);
}

.quote-mark { color: #ffb28e; font-family: Georgia, serif; font-size: 1.2rem; line-height: 1; }
.title { margin: 0; color: rgba(255,255,255,.94); font-size: clamp(.82rem, 1.3vw, 1rem); font-weight: 500; letter-spacing: .08em; line-height: 1.45; }
.easy-typed-cursor { margin-left: .18rem; color: #ffb28e; animation: blink .9s steps(1) infinite; }

.scroll-cue {
  position: absolute;
  z-index: 5;
  bottom: clamp(2rem, 6vh, 4.2rem);
  left: 50%;
  display: flex;
  align-items: center;
  flex-direction: column;
  gap: .7rem;
  padding: 0;
  border: 0;
  background: none;
  color: rgba(255,255,255,.75);
  cursor: pointer;
  transform: translateX(-50%);
}

.scroll-cue__label { font-size: .52rem; font-weight: 700; letter-spacing: .24em; text-shadow: 0 2px 8px rgba(5,10,25,.45); }
.scroll-cue__circle {
  position: relative;
  display: grid;
  width: 3.5rem;
  height: 3.5rem;
  place-items: center;
  border: 1px solid rgba(255,255,255,.44);
  border-radius: 50%;
  background: rgba(12,18,39,.18);
  box-shadow: 0 0 0 .45rem rgba(255,255,255,.045);
  backdrop-filter: blur(8px);
  animation: breathe 2.8s ease-in-out infinite;

  i { width: .55rem; height: .55rem; border-right: 1.5px solid #fff; border-bottom: 1.5px solid #fff; transform: translateY(-2px) rotate(45deg); }
}
.scroll-cue:hover .scroll-cue__circle { border-color: rgba(255,178,142,.9); background: rgba(255,178,142,.12); }

@keyframes blink { 50% { opacity: 0; } }
@keyframes breathe { 50% { transform: translateY(5px); box-shadow: 0 0 0 .75rem rgba(255,255,255,.025); } }

@media (max-width: 640px) {
  .brand { width: calc(100% - 2rem); transform: translate(-50%, -56%); }
  .brand-kicker { gap: .55rem; letter-spacing: .18em; }
  .brand-kicker span { width: 1.4rem; }
  .brand-title { font-size: clamp(3rem, 14vw, 5.1rem); line-height: 1.1; }
  .brand-rule { margin: 1.15rem 0 1rem; }
  .brand-text { border-radius: 1rem; padding: .75rem .9rem; }
  .title { letter-spacing: .04em; }
  .scroll-cue__label { display: none; }
}

@media (max-height: 650px) {
  .brand { transform: translate(-50%, -58%); }
  .brand-kicker { margin-bottom: .8rem; }
  .brand-rule { margin: .8rem 0; }
  .scroll-cue { bottom: 1.25rem; }
  .scroll-cue__circle { width: 2.8rem; height: 2.8rem; }
}

@media (prefers-reduced-motion: reduce) {
  .easy-typed-cursor, .scroll-cue__circle { animation: none; }
  .scroll-cue { scroll-behavior: auto; }
}
</style>
