<script setup lang="ts">
import { getRecommendArticleList } from "@/apis/home";
import { Swiper, SwiperSlide } from 'swiper/vue';
import { Navigation, Pagination, Autoplay } from 'swiper/modules';
// Import Swiper styles
import 'swiper/css';
import 'swiper/css/navigation';
import 'swiper/css/pagination';

const recommendArticles = ref([])

const modules = ref([Navigation,Pagination,Autoplay]);

function loadContent(){
  getRecommendArticleList().then(res => {
    // 过滤内容
    res.data = res.data.map((item: any) => {
      item.articleContent = item.articleContent.replace(/[*#>`~\-\\[\]()\s]|(\n\n)/g, '')
      // 提取前 50 个字符
      item.articleContent = item.articleContent.substring(0, 25) + '...';
      return item;
    });
    recommendArticles.value = res.data
  })
}

</script>

<template>
  <div class="section-heading">
    <div class="section-title">
      <span class="section-index">01</span>
      <div>
        <small>EDITOR'S SELECTION</small>
        <h2>值得一读</h2>
      </div>
    </div>
    <span class="section-line" aria-hidden="true"/>
    <span class="section-note">Curated stories</span>
  </div>
  <div  v-view-request="{ callback: loadContent }">
    <swiper class="recommend"
            loop
            navigation
            :pagination="{ clickable: true }"
            :autoplay="{ delay: 2500 }"
            :modules="modules"
            v-if="recommendArticles.length > 0"
    >
      <swiper-slide v-for="recommendArticle in recommendArticles" :key="recommendArticle.id"
                    @click="$router.push(`/article/${recommendArticle.id}`)">
        <el-image :src="recommendArticle.articleCover" fit="cover"/>
        <div class="slide-shade" aria-hidden="true"/>
        <div class="item_text">
          <div class="slide-meta">
            <span class="choice">FEATURED</span>
            <span>{{ recommendArticle.createTime }}</span>
          </div>
          <h3>{{ recommendArticle.articleTitle }}</h3>
          <p>{{ recommendArticle.articleContent }}</p>
          <span class="read-more">阅读全文 <b>↗</b></span>
        </div>
      </swiper-slide>
      <div class="swiper-pagination"></div>
    </swiper>
  </div>
  <el-skeleton v-if="recommendArticles.length == 0" :rows="5" animated />
</template>

<style scoped lang="scss">
.recommend {
  height: clamp(19rem, 38vw, 26rem);
  overflow: hidden;
  border-radius: 1.5rem;
  box-shadow: 0 1.5rem 3.5rem rgba(19, 27, 52, .18);

  :deep(.swiper-button-prev), :deep(.swiper-button-next) {
    width: 2.75rem;
    height: 2.75rem;
    border: 1px solid rgba(255, 255, 255, .4);
    border-radius: 50%;
    background: rgba(10, 15, 30, .22);
    color: #fff;
    backdrop-filter: blur(10px);
    &::after { font-size: .8rem; font-weight: 800; }
  }

  :deep(.swiper-pagination-bullet) { width: 1.5rem; height: 2px; border-radius: 0; background: rgba(255,255,255,.75); }
  :deep(.swiper-pagination-bullet-active) { width: 2.8rem; background: #fff; }

  .item_text {
    position: absolute;
    inset: 0;
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    justify-content: flex-end;
    max-width: 48rem;
    padding: clamp(2rem, 5vw, 4rem);
    color: #fff;
    z-index: 2;

    .slide-meta { display: flex; align-items: center; gap: .8rem; font-size: .7rem; letter-spacing: .08em; opacity: .85; }
    .choice { padding: .3rem .55rem; border: 1px solid rgba(255,255,255,.5); border-radius: 99px; font-weight: 700; letter-spacing: .14em; }
    h3 { max-width: 40rem; margin: .9rem 0 .65rem; font-family: Georgia, serif; font-size: clamp(1.85rem, 4vw, 3.2rem); font-weight: 500; line-height: 1.12; letter-spacing: -.03em; text-wrap: balance; }
    p { max-width: 34rem; margin: 0; overflow: hidden; font-size: .92rem; line-height: 1.75; opacity: .84; text-overflow: ellipsis; white-space: nowrap; }
    .read-more { display: inline-flex; align-items: center; gap: .6rem; margin-top: 1.2rem; font-size: .78rem; font-weight: 700; letter-spacing: .08em; }
    .read-more b { font-size: 1rem; transition: transform .25s ease; }
  }

  .el-image {
    width: 100%;
    height: 100%;
    transition: transform .8s cubic-bezier(.2,.65,.3,1);
  }

  .slide-shade { position: absolute; inset: 0; z-index: 1; background: linear-gradient(90deg, rgba(8,13,29,.86) 0%, rgba(8,13,29,.52) 55%, rgba(8,13,29,.1) 100%), linear-gradient(0deg, rgba(8,13,29,.65), transparent 55%); }
  .swiper-slide { cursor: pointer; }
  .swiper-slide:hover .el-image { transform: scale(1.045); }
  .swiper-slide:hover .read-more b { transform: translate(2px, -2px); }
}

.section-heading {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-bottom: 1.2rem;
  .section-title { display: flex; align-items: center; gap: .8rem; }
  .section-index { color: #5b7cfa; font-family: Georgia, serif; font-size: 1.5rem; font-style: italic; }
  small { color: var(--el-text-color-placeholder); font-size: .58rem; font-weight: 700; letter-spacing: .17em; }
  h2 { margin: .08rem 0 0; color: var(--el-text-color-primary); font-family: Georgia, serif; font-size: 1.55rem; font-weight: 500; }
  .section-line { height: 1px; flex: 1; background: var(--el-border-color-lighter); }
  .section-note { color: var(--el-text-color-placeholder); font-family: Georgia, serif; font-size: .72rem; font-style: italic; }
}

@media (max-width: 640px) {
  .recommend { height: 23rem; border-radius: 1.2rem; }
  .recommend .item_text { padding: 1.6rem 1.35rem 2.4rem; }
  .recommend .item_text p { max-width: 100%; }
  .recommend :deep(.swiper-button-prev), .recommend :deep(.swiper-button-next) { display: none; }
  .section-note { display: none; }
}

@media (prefers-reduced-motion: reduce) {
  .recommend .el-image, .recommend .read-more b { transition: none; }
  .recommend .swiper-slide:hover .el-image { transform: none; }
}
</style>
