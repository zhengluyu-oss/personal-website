<script setup lang="ts">
import Main from "@/views/Home/Main/index.vue";
import Images from "@/views/Home/Images/index.vue";
import Brand from "@/views/Home/Brand/index.vue";
import Particles from "@/components/Particles/index.vue";
import MouseTrail from "@/components/MouseTrail/index.vue";
import ScrollParallax from "@/components/ScrollParallax/index.vue";

</script>
<template>
  <div class="home_container">
    <MouseTrail class="desktop-flourish"/>
    <Images/>
    <Particles class="desktop-flourish"/>
    <Brand/>
    <div class="bg">
      <ScrollParallax :speed="0.1" direction="up">
        <Main/>
      </ScrollParallax>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.bg {
  position: relative;
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
