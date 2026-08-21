<template>
  <Main is-side-bar>
    <!-- 内容区 -->
    <template #content>
      <section class="editorial-intro">
        <div class="intro-copy">
          <span class="eyebrow">陆屿的个人博客</span>
          <h2>把复杂问题想清楚，<br><em>再把它做出来。</em></h2>
          <p>这里记录技术实践、产品思考和职业成长，也展示我持续解决真实问题的方式。</p>
        </div>
        <div class="intro-mark" aria-hidden="true">
          <span>陆屿</span>
          <small>开发者</small>
        </div>
      </section>
      <div class="announcement">
        <span class="announcement-icon"><SvgIcon name="notice" color="currentColor"/></span>
        <div>
          <small>今日札记</small>
          <span>{{ useWebsite?.webInfo?.headerNotification }}</span>
        </div>
      </div>
      <RecommendArticle/>
      <div class="section-heading essay_title">
        <div>
          <div class="section-copy">
            <h2>近期文章</h2>
          </div>
        </div>
        <span class="section-line" aria-hidden="true"/>
        <router-link class="section-note" to="/category">浏览全部文章</router-link>
      </div>
      <div>
        <CardEssay/>
      </div>
      <div>
        <Pagination/>
      </div>
      <div>
        <el-divider border-style="dashed" content-position="center">
          <div class="end-note">感谢你读到这里</div>
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
    color: var(--brand-accent-strong);
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

    em { color: var(--brand-accent-strong); font-weight: 500; }
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
  border: 1px solid var(--brand-line);
  border-radius: 50%;
  color: var(--brand-accent-strong);
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
  border-left: 3px solid var(--brand-accent);
  border-radius: .25rem 1rem 1rem .25rem;
  background: var(--brand-accent-soft);
  color: var(--el-text-color-regular);

  .announcement-icon {
    display: grid;
    width: 2.4rem;
    height: 2.4rem;
    place-items: center;
    flex: 0 0 auto;
    border-radius: .75rem;
    background: var(--brand-accent);
    color: #fff;
  }

  div { display: flex; min-width: 0; flex-direction: column; gap: .16rem; }
  small { color: var(--brand-accent-strong); font-size: .7rem; font-weight: 700; letter-spacing: .08em; }
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
  .section-copy { display: flex; flex-direction: column; }
  h2 { margin: .08rem 0 0; color: var(--el-text-color-primary); font-family: Georgia, serif; font-size: 1.55rem; font-weight: 500; }
  .section-line { height: 1px; flex: 1; background: var(--el-border-color-lighter); }
  .section-note { color: var(--brand-accent-strong); font-size: .78rem; text-decoration:none; }
}
.end-note { color: var(--brand-ink-faint); font-size:.8rem; font-weight:600; }

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
