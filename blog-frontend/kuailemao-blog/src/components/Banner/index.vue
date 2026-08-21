<script setup lang="ts">
import { ossUrl } from '@/config/site'

defineProps({
  title: String,
  subtitle: String
})

const bannerBg = ossUrl('blog/background/学习的史蒂夫.jpg')
</script>

<template>
  <header class="page-hero" :style="{ backgroundImage: `url(${bannerBg})` }">
    <div class="page-hero__shade" aria-hidden="true"/>
    <div class="page-hero__content">
      <p v-if="subtitle" class="subtitle">{{ subtitle }}</p>
      <h1 class="title">{{ title }}</h1>
    </div>
  </header>
</template>

<style scoped lang="scss">
.page-hero {
  position: relative;
  min-height: clamp(19rem, 42dvh, 31rem);
  width: 100%;
  margin-bottom: clamp(2rem, 5vw, 4.5rem);
  display: flex;
  justify-content: center;
  flex-direction: column;
  align-items: center;
  background-size: cover;
  background-position: center 42%;
  overflow: hidden;

  &__shade { position:absolute; inset:0; background: linear-gradient(90deg, rgba(11,17,27,.82), rgba(11,17,27,.34) 56%, rgba(11,17,27,.2)), linear-gradient(0deg, rgba(11,17,27,.5), transparent 62%); }
  &__content { position:relative; width:min(100% - 3rem, var(--brand-content)); margin:auto; padding-top:3rem; color:#f7f4ef; }

  .title {
    color: white;
    max-width: 10em;
    margin: 0;
    font-size: clamp(2.8rem, 7vw, 6.4rem);
    font-weight: 650;
    line-height: .98;
    letter-spacing: -.06em;
    text-wrap: balance;
    text-shadow: 0 12px 38px rgba(0,0,0,.28);
  }

  .subtitle {
    margin: 0 0 1rem;
    color: #ffad99;
    font-family: "Share Tech Mono", monospace;
    font-size: .78rem;
    font-weight: 700;
    letter-spacing: .16em;
    text-transform: uppercase;
  }
}

@media (max-width: 767px) {
  .page-hero { min-height: 17rem; align-items:flex-end; background-position: 58% center; }
  .page-hero__content { width:calc(100% - 2rem); padding:0 0 2.4rem; }
  .page-hero .title { font-size:clamp(2.5rem, 15vw, 4.2rem); }
}
</style>
