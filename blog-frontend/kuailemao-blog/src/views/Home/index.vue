<script setup lang="ts">
import Images from "@/views/Home/Images/index.vue";
import Brand from "@/views/Home/Brand/index.vue";
import {defineAsyncComponent, onBeforeUnmount, onMounted, ref} from 'vue'
import ScrollParallax from "@/components/ScrollParallax/index.vue";

const Main = defineAsyncComponent(() => import('@/views/Home/Main/index.vue'))
const Particles = defineAsyncComponent(() => import('@/components/Particles/index.vue'))
const MouseTrail = defineAsyncComponent(() => import('@/components/MouseTrail/index.vue'))
const flourishesReady = ref(false)
const mainReady = ref(false)
const mainTrigger = ref<HTMLElement | null>(null)
let mainObserver: IntersectionObserver | undefined
const revealFlourishes = () => { flourishesReady.value = true }

onMounted(() => {
  window.addEventListener('pointermove', revealFlourishes, {once: true, passive: true})
  if (!mainTrigger.value || !('IntersectionObserver' in window)) {
    mainReady.value = true
    return
  }
  mainObserver = new IntersectionObserver(([entry]) => {
    if (!entry.isIntersecting) return
    mainReady.value = true
    mainObserver?.disconnect()
  }, {rootMargin: '160px 0px'})
  mainObserver.observe(mainTrigger.value)
})

onBeforeUnmount(() => {
  mainObserver?.disconnect()
  window.removeEventListener('pointermove', revealFlourishes)
})

</script>
<template>
  <div class="home_container">
    <MouseTrail v-if="flourishesReady" class="desktop-flourish"/>
    <Images/>
    <Particles v-if="flourishesReady" class="desktop-flourish"/>
    <Brand/>
    <div ref="mainTrigger" class="bg">
      <ScrollParallax :speed="0.1" direction="up">
        <Main v-if="mainReady"/>
      </ScrollParallax>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.bg {
  position: relative;
  z-index: 2;
  transition: background-color 1s ease !important;
  background:
    radial-gradient(circle at 12% 14%, var(--brand-accent-soft), transparent 25rem),
    var(--brand-canvas);

  &::before {
    content: '';
    position: absolute;
    inset: 0;
    pointer-events: none;
    display: none;
  }
}

@media (max-width: 767px), (prefers-reduced-motion: reduce) {
  .desktop-flourish { display: none !important; }
}

.home_container{
  // 浏览器宽度 - 滚动条宽度，设置为100vw首页底部会出现滚动条
  width: 100%;
  // 屏幕小910
  @media screen and (max-width: 910px) {
    width: 100vw;
  }
}
</style>
