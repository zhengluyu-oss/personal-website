<script setup lang="ts">
import Brand from '@/views/Home/Brand/index.vue'
import Main from '@/views/Home/Main/index.vue'
</script>

<template>
  <main class="portfolio-home">
    <section class="hero-stage" aria-label="个人介绍">
      <Brand />
    </section>
    <Main />
  </main>
</template>

<style scoped lang="scss">
.portfolio-home {
  width: 100%;
  padding-top: 4.5rem;
  background: var(--brand-canvas);
  color: var(--brand-ink);
}
.hero-stage {
  position: relative;
  width: 100%;
  margin: auto;
}
.hero-stage::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: clamp(3.5rem, 8vh, 5.5rem);
  pointer-events: none;
  background: linear-gradient(180deg, transparent 0%, var(--brand-canvas) 100%);
  z-index: 1;
}
@media (max-width: 767px) {
  .portfolio-home { padding-top: 4rem; }
}
</style>
