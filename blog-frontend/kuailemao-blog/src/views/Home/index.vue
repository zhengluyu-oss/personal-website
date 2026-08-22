<script setup lang="ts">
import Images from '@/views/Home/Images/index.vue'
import Brand from '@/views/Home/Brand/index.vue'
import Main from '@/views/Home/Main/index.vue'
</script>

<template>
  <main class="portfolio-home">
    <section class="hero-stage" aria-label="个人介绍">
      <Images />
      <Brand />
    </section>
    <Main />
  </main>
</template>

<style scoped lang="scss">
.portfolio-home{width:100%;overflow:clip;background:var(--brand-canvas);color:var(--brand-ink)}
.hero-stage{position:relative;min-height:100dvh;isolation:isolate}
</style>
