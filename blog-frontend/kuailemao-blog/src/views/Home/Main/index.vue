<template>
  <Main is-side-bar>
    <!-- 内容区 -->
    <template #content>
      <section class="editorial-intro">
        <div class="intro-copy">
          <span class="eyebrow">WELCOME TO MY DIGITAL GARDEN</span>
          <h2>记录思考，也记录<br><em>持续生长的轨迹。</em></h2>
          <p>关于技术、创造与生活的长期笔记。愿每一次阅读，都能带来一点新的启发。</p>
        </div>
        <div class="intro-mark" aria-hidden="true">
          <span>RUYU</span>
          <small>EST. 2024</small>
        </div>
      </section>
      <div class="announcement">
        <span class="announcement-icon"><SvgIcon name="notice" color="currentColor"/></span>
        <div>
          <small>NOTICE / 今日札记</small>
          <span>{{ useWebsite?.webInfo?.headerNotification }}</span>
        </div>
      </div>
      <RecommendArticle/>
      <div class="section-heading essay_title">
        <div>
          <span class="section-index">02</span>
          <div class="section-copy">
            <small>THE LATEST NOTES</small>
            <h2>近期文章</h2>
          </div>
        </div>
        <span class="section-line" aria-hidden="true"/>
        <span class="section-note">Ideas · Code · Life</span>
      </div>
      <div>
        <CardEssay/>
      </div>
      <div>
        <Pagination/>
      </div>
      <div>
        <el-divider border-style="dashed" content-position="center">
          <div style="font-weight: bold">~~到达底部啦~~</div>
        </el-divider>
      </div>
    </template>
    <!-- 侧边栏 -->
    <template #information>
      <SideBar/>
    </template>
  </Main>
</template>

<script setup lang="ts">
import RecommendArticle from './RecommendArticle/index.vue'
import useWebsiteStore from "@/store/modules/website.ts";

const useWebsite = useWebsiteStore()

</script>

<style scoped lang="scss">
.editorial-intro {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 2rem;
  align-items: end;
  padding: 2.8rem 2.6rem 2.5rem;
  margin-bottom: 1rem;
  overflow: hidden;
  border: 1px solid color-mix(in srgb, var(--el-border-color) 72%, transparent);
  border-radius: 1.5rem;
  background:
    radial-gradient(circle at 88% 12%, rgba(91, 124, 250, .18), transparent 34%),
    linear-gradient(145deg, color-mix(in srgb, var(--el-bg-color) 94%, #eff3ff), var(--el-bg-color));

  &::before {
    content: '';
    position: absolute;
    width: 10rem;
    height: 10rem;
    left: -5rem;
    bottom: -6rem;
    border: 1px solid rgba(91, 124, 250, .18);
    border-radius: 50%;
    box-shadow: 0 0 0 2rem rgba(91, 124, 250, .035), 0 0 0 4rem rgba(91, 124, 250, .025);
  }
}

.intro-copy {
  position: relative;
  z-index: 1;

  .eyebrow {
    display: inline-block;
    margin-bottom: 1rem;
    color: #5b7cfa;
    font-size: .68rem;
    font-weight: 700;
    letter-spacing: .2em;
  }

  h2 {
    margin: 0;
    color: var(--el-text-color-primary);
    font-family: Georgia, 'Times New Roman', serif;
    font-size: clamp(2rem, 4vw, 3.5rem);
    font-weight: 500;
    line-height: 1.08;
    letter-spacing: -.04em;

    em { color: #5b7cfa; font-weight: 500; }
  }

  p {
    max-width: 34rem;
    margin: 1.25rem 0 0;
    color: var(--el-text-color-secondary);
    font-size: .94rem;
    line-height: 1.8;
  }
}

.intro-mark {
  position: relative;
  z-index: 1;
  display: flex;
  width: 6.5rem;
  height: 6.5rem;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(91, 124, 250, .3);
  border-radius: 50%;
  color: #5b7cfa;
  transform: rotate(8deg);

  span { font-family: Georgia, serif; font-size: 1.15rem; letter-spacing: .12em; }
  small { margin-top: .25rem; font-size: .54rem; letter-spacing: .14em; }
}

.announcement {
  display: flex;
  align-items: center;
  gap: .9rem;
  min-height: 4rem;
  margin: 1rem 0 2.3rem;
  padding: .75rem 1rem;
  border-left: 3px solid #5b7cfa;
  border-radius: .25rem 1rem 1rem .25rem;
  background: color-mix(in srgb, #5b7cfa 7%, var(--el-bg-color));
  color: var(--el-text-color-regular);

  .announcement-icon {
    display: grid;
    width: 2.4rem;
    height: 2.4rem;
    place-items: center;
    flex: 0 0 auto;
    border-radius: .75rem;
    background: #5b7cfa;
    color: #fff;
  }

  div { display: flex; min-width: 0; flex-direction: column; gap: .16rem; }
  small { color: #5b7cfa; font-size: .62rem; font-weight: 700; letter-spacing: .16em; }
  span:last-child { overflow: hidden; font-size: .9rem; text-overflow: ellipsis; white-space: nowrap; }
}

.information_container {
  width: 30%;
  height: 100%;
  //border: red 1px solid;
}

.section-heading {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin: 3rem 0 1.2rem;

  > div { display: flex; align-items: center; gap: .8rem; }
  .section-index { color: #5b7cfa; font-family: Georgia, serif; font-size: 1.5rem; font-style: italic; }
  .section-copy { display: flex; flex-direction: column; }
  small { color: var(--el-text-color-placeholder); font-size: .58rem; font-weight: 700; letter-spacing: .17em; }
  h2 { margin: .08rem 0 0; color: var(--el-text-color-primary); font-family: Georgia, serif; font-size: 1.55rem; font-weight: 500; }
  .section-line { height: 1px; flex: 1; background: var(--el-border-color-lighter); }
  .section-note { color: var(--el-text-color-placeholder); font-family: Georgia, serif; font-size: .72rem; font-style: italic; }
}

@media (max-width: 640px) {
  .editorial-intro { grid-template-columns: 1fr; padding: 2rem 1.25rem; border-radius: 1.2rem; }
  .intro-mark { display: none; }
  .intro-copy p { font-size: .86rem; }
  .announcement { margin-bottom: 1.8rem; }
  .section-heading { margin-top: 2.4rem; }
  .section-note { display: none; }
}

@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after { scroll-behavior: auto !important; transition-duration: .01ms !important; }
}
</style>
