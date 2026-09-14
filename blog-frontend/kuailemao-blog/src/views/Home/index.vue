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
.portfolio-home{width:100%;padding-top:4.5rem;background:var(--brand-canvas);color:var(--brand-ink)}
.hero-stage{width:min(calc(100% - 4rem),76rem);margin:auto}
@media(max-width:767px){.portfolio-home{padding-top:4rem}.hero-stage{width:calc(100% - 2rem)}}
</style>
