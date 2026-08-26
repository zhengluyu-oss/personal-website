<script setup lang="ts">
import {computed, ref} from 'vue'
import {MdPreview} from 'md-editor-v3';
import 'md-editor-v3/lib/preview.css';
import {
  addArticleVisit,
  getArticleDetail
} from "@/apis/article";
import {cancelFavorite, userFavorite, isFavorite} from '@/apis/favorite'
import {cancelLike, isLike, userLike} from '@/apis/like';
import DirectoryCard from "./DirectoryCard/index.vue";
import {ElMessage} from "element-plus";
import router from "@/router";
import useWebsiteStore from "@/store/modules/website.ts";
import MobileDirectoryCard from "./MobileDirectoryCard/index.vue";
import {ARTICLE_VISIT_PREFIX} from "@/const/Visits";
import { ossUrl } from '@/config/site'
import { setSeoMeta } from '@/utils/seo'
import { categoryRoute } from '@/utils/category-slug'
import { useBlogCategories } from '@/composables/useBlogCategories'

const payQrUrl = ossUrl('blog/pay/支付宝支付二维码_.png')
const env = import.meta.env;

const websiteStore = useWebsiteStore()
const { categories, loadCategories } = useBlogCategories()

async function openArticleCategory() {
  await loadCategories()
  router.push(categoryRoute(categories.value, articleDetail.value.categoryId))
}
const mode = 'light'
const id = 'preview-only';
const scrollElement = document.documentElement;
const isShowMoveCatalog = ref(false)

const articleDetail = ref({
  articleCover: '',
  articleTitle: '',
  seoTitle: '',
  seoDescription: '',
  seoKeywords: '',
  articleContent: undefined,
  categoryName: '',
  categoryId: '',
  tags: [],
  visitCount: 0,
  commentCount: 0,
  likeCount: 0,
  favoriteCount: 0,
  createTime: '',
  updateTime: '',
  userId: 0,
  id: "0"
})

const route = useRoute();

// 是否加载
const loading = ref(false)

// 字数 统计
const countMd = ref(0)

// 监听路由变化
watch(() => route.params.id, () => {
  getArticleDetailById()
})


onMounted(async () => {
  void loadCategories()
  await getArticleDetailById()
})


async function getArticleDetailById() {
  getArticleDetail(route.params.id).then(res => {
    if (!res.data) {
      ElMessage.warning({
        message: '文章不存在',
      })
      // 跳转回去
      router.push({path: '/'})
      return
    }
    const plainText = String(res.data.articleContent || '').replace(/```[\s\S]*?```/g, ' ').replace(/[#>*_`\[\]()!-]/g, ' ').replace(/\s+/g, ' ').trim()
    const tagKeywords = (res.data.tags || []).map((tag: any) => tag.tagName).filter(Boolean).join(',')
    setSeoMeta({
      title: res.data.seoTitle?.trim() || `${res.data.articleTitle} | 陆屿的个人博客`,
      description: res.data.seoDescription?.trim() || plainText.slice(0, 160),
      keywords: res.data.seoKeywords?.trim() || tagKeywords || '技术博客,开发实践',
    })
    if (route.params.id) {
      if (!sessionStorage.getItem(ARTICLE_VISIT_PREFIX + route.params.id)) {
        // 避免重复刷新
        sessionStorage.setItem(ARTICLE_VISIT_PREFIX + route.params.id, route.params.id as string)
        addArticleVisit(route.params.id as string)
      }
    }
    // 时间去掉时分秒
    res.data.createTime = res.data.createTime.split(' ')[0]
    res.data.updateTime = res.data.updateTime.split(' ')[0]
    articleDetail.value = res.data
    loading.value = true
    // 收藏
    isFavoriteFunc()
    // 点赞
    isLikeFunc()
  })
}

function mdHtml(htmlText: string) {
  // 获取html中的所有文字，去掉空格与标点符号
  const text = htmlText.replace(/<[^>]+>/g, "").replace(/[\r\n]/g, "").replace(/[ ]/g, "").replace(/[\s+\.\!\/_,$%^*(+\"\']+|[+——！，。？、~@#￥%……&*（）]+/g, "")
  countMd.value = <number>countWords(text.length)
}

// 字数统计
function countWords(count: number) {
  if (count <= 1000) {
    return count
  } else {
    let counts = (count / 1000);
    // 留小数点一位数
    counts = Number(counts.toFixed(1));
    return counts + 'k';
  }
}


// 分享
const copyToClipboard = async () => {
  try {
    const content = `欢迎访问博客文章：${articleDetail.value.articleTitle} \n通往地址：${env.VITE_FRONTEND_URL}${route.path}`;
    // 替换为你要分享的实际内容
    await navigator.clipboard.writeText(content);
    ElMessage.success("已复制分享链接");
  } catch (error) {
    ElMessage.error("复制失败，请联系网站管理员");
  }
}

// 收藏标记
const collection = ref(false)
// 点赞标记
const like = ref(false)

const collectionBtn = (detail: object) => {

  if (collection.value) {
    // 取消收藏
    cancelFavorite(1, articleDetail.value.id).then(res => {
      if (res.code === 200) {
        detail.favoriteCount -= 1
        collection.value = false
        ElMessage.info("取消收藏");
      } else {
        ElMessage.error(res.msg);
      }
    })
  } else {
    // 收藏
    userFavorite(1, articleDetail.value.id).then(res => {
      if (res.code === 200) {
        detail.favoriteCount += 1
        collection.value = true
        ElMessage.success("收藏成功");
      } else {
        ElMessage.error(res.msg);
      }
    })
  }
}

function likeBtn(detail: object) {
  if (like.value) {
    cancelLike(1, articleDetail.value.id).then(res => {
      if (res.code === 200) {
        detail.likeCount -= 1
        like.value = false
        ElMessage.info("我会继续努力的");
      } else {
        ElMessage.error(res.msg);
      }
    })
  } else {
    userLike(1, articleDetail.value.id).then(res => {
      if (res.code === 200) {
        detail.likeCount += 1
        like.value = true
        ElMessage.success("感谢你的认可");
      } else {
        ElMessage.error(res.msg);
      }
    })
  }
}

// 是否收藏
function isFavoriteFunc() {
  isFavorite(1, articleDetail.value.id).then(res => {
    collection.value = res.data === true;
  })
}

// 是否点赞
function isLikeFunc() {
  isLike(1, articleDetail.value.id).then(res => {
    like.value = res.code === 200;
  })
}

const readingMinutes = computed(() => {
  const content = String(articleDetail.value.articleContent || '')
    .replace(/```[\s\S]*?```/g, ' ')
    .replace(/[#>*_`\[\]()!-]/g, ' ')
    .replace(/\s+/g, '')
  return Math.max(1, Math.ceil(content.length / 420))
})

</script>

<template>
  <div class="article-page">
    <Header/>

    <main v-if="loading" class="article-shell">
      <header class="article-intro">
        <div class="article-kicker">
          <button type="button" @click="openArticleCategory">{{ articleDetail.categoryName }}</button>
          <span>ARTICLE {{ String(articleDetail.id).padStart(3, '0') }}</span>
        </div>
        <h1>{{ articleDetail.articleTitle }}</h1>
        <div class="article-byline">
          <span>撰文 {{ websiteStore.webInfo?.webmasterName || '陆屿' }}</span>
          <span>{{ articleDetail.createTime }}</span>
          <span>约 {{ readingMinutes }} 分钟阅读</span>
          <span>{{ articleDetail.visitCount }} 次阅读</span>
        </div>
        <figure v-if="articleDetail.articleCover" class="article-cover">
          <img :src="articleDetail.articleCover" :alt="articleDetail.articleTitle"/>
          <figcaption>陆屿的个人博客 · 技术与实践记录</figcaption>
        </figure>
      </header>

      <div class="article-layout">
        <aside class="article-rail" aria-label="文章信息与目录">
          <div class="rail-section rail-overview">
            <span class="rail-label">阅读信息</span>
            <dl>
              <div><dt>更新</dt><dd>{{ articleDetail.updateTime }}</dd></div>
              <div><dt>字数</dt><dd>{{ countMd }}</dd></div>
              <div><dt>评论</dt><dd>{{ articleDetail.commentCount }}</dd></div>
            </dl>
          </div>
          <div class="rail-section rail-directory">
            <DirectoryCard/>
          </div>
        </aside>

        <article class="article-reading">
          <MdPreview :editorId="id" :theme="mode" :modelValue="articleDetail.articleContent" :on-html-changed="mdHtml"/>

          <section class="article-license" aria-labelledby="license-title">
            <p id="license-title">关于本文</p>
            <strong>{{ websiteStore.webInfo?.webmasterName || '陆屿' }} 原创内容</strong>
            <span>除特别声明外，文章采用 CC BY-NC-SA 4.0 许可协议。转载请保留作者与本文链接。</span>
            <a :href="env.VITE_FRONTEND_URL + $route.path">{{ env.VITE_FRONTEND_URL + $route.path }}</a>
          </section>

          <footer class="article-ending">
            <div class="article-tags">
              <button v-for="tag in articleDetail.tags" :key="tag.id" type="button" @click="$router.push(`/blog/tags/${tag.id}`)"># {{ tag.tagName }}</button>
            </div>
            <div class="article-actions" aria-label="文章互动">
              <button type="button" :class="{active: like}" @click="likeBtn(articleDetail)">
                <SvgIcon :name="like ? 'like-selected' : 'like'"/><span>认可 {{ articleDetail.likeCount }}</span>
              </button>
              <button type="button" :class="{active: collection}" @click="collectionBtn(articleDetail)">
                <SvgIcon :name="collection ? 'collection-selected' : 'collection'"/><span>收藏 {{ articleDetail.favoriteCount }}</span>
              </button>
              <button type="button" @click="copyToClipboard"><SvgIcon name="share"/><span>分享</span></button>
              <el-tooltip effect="light" placement="top">
                <template #content><div class="qr-code"><span>请作者喝杯咖啡</span><el-image :src="payQrUrl"/></div></template>
                <button type="button"><SvgIcon name="gift"/><span>支持创作</span></button>
              </el-tooltip>
            </div>
          </footer>

          <nav class="article-neighbours" aria-label="上一篇和下一篇">
            <button v-if="articleDetail.preArticleId > 0" type="button" @click="$router.push(`/blog/articles/${articleDetail.preArticleId}`)">
              <span>上一篇</span><strong>{{ articleDetail.preArticleTitle }}</strong>
            </button>
            <button v-if="articleDetail.nextArticleId > 0" type="button" @click="$router.push(`/blog/articles/${articleDetail.nextArticleId}`)">
              <span>下一篇</span><strong>{{ articleDetail.nextArticleTitle }}</strong>
            </button>
          </nav>

          <section class="article-comments">
            <div class="section-heading"><span>DISCUSSION</span><h2>继续这场讨论</h2></div>
            <Comment :type="1" :like-type="2" :author-id="articleDetail.userId" :type-id="articleDetail.id"/>
          </section>
        </article>
      </div>
    </main>

    <main v-else class="article-loading" aria-live="polite">
      <span>ARTICLE</span><p>正在整理文章内容</p>
    </main>

    <button class="mobile-directory-button" type="button" @click="isShowMoveCatalog = true">
      <SvgIcon name="directory"/><span>目录</span>
    </button>
    <MobileDirectoryCard :id="id" :scroll-element="scrollElement" :is-show-move-catalog="isShowMoveCatalog"
                         @update:isShowMoveCatalog="(value) => isShowMoveCatalog = value"/>
    <Footer/>
  </div>
</template>

<style scoped lang="scss">
.article-page {
  --article-accent: var(--brand-accent);
  --article-accent-strong: var(--brand-accent-strong);
  --article-ink: var(--brand-ink);
  --article-muted: var(--brand-ink-soft);
  --article-line: color-mix(in srgb, var(--article-ink) 14%, transparent);
  min-height: 100dvh;
  background:
    radial-gradient(circle at 78% 6%, rgba(21, 94, 239, .08), transparent 28rem),
    linear-gradient(rgba(21, 94, 239, .025) 1px, transparent 1px),
    linear-gradient(90deg, rgba(21, 94, 239, .025) 1px, transparent 1px),
    var(--brand-canvas);
  background-size: auto, 32px 32px, 32px 32px, auto;
  color: var(--article-ink);
}

.article-shell { width: min(calc(100% - 3rem), 88rem); margin: 0 auto; padding: clamp(3rem, 6vw, 6rem) 0 6rem; }
.article-intro { position: relative; width: min(100%, 64rem); margin: 0 auto 3.25rem; padding-left: clamp(1.1rem, 2.4vw, 2rem); border-left: 3px solid var(--article-accent); }
.article-kicker, .article-byline { display: flex; flex-wrap: wrap; align-items: center; gap: .75rem 1.25rem; color: var(--article-muted); font-size: .78rem; letter-spacing: .08em; text-transform: uppercase; }
.article-kicker button { padding: .45rem .72rem; border: 1px solid var(--brand-line); border-radius: var(--brand-radius-sm); color: var(--article-accent-strong); background: var(--brand-accent-soft); font-weight: 700; cursor: pointer; }
.article-intro h1 {
  max-width: min(100%, 38rem);
  margin: 1rem 0 1.1rem;
  font-size: clamp(1.85rem, 3.1vw, 2.75rem);
  font-weight: 700;
  line-height: 1.2;
  letter-spacing: -.03em;
  text-wrap: balance;
}
.article-byline span:not(:first-child)::before { content: '/'; margin-right: 1.25rem; color: color-mix(in srgb, var(--article-muted) 45%, transparent); }
.article-cover { margin: 2.75rem 0 0; }
.article-cover img { display: block; width: 100%; max-height: 42rem; object-fit: cover; border-radius: var(--brand-radius-lg); box-shadow: var(--brand-shadow-lg); }
.article-cover figcaption { margin-top: .75rem; color: var(--article-muted); font-size: .72rem; letter-spacing: .08em; text-align: right; text-transform: uppercase; }

.article-layout { display: grid; grid-template-columns: 14rem minmax(0, 50rem); justify-content: center; gap: clamp(2.5rem, 5vw, 5.5rem); align-items: start; }
.article-rail { position: sticky; top: 6rem; min-width: 0; }
.rail-section { padding: 1.25rem 0; border-top: 1px solid var(--article-line); }
.rail-label { color: var(--article-muted); font-size: .68rem; letter-spacing: .16em; text-transform: uppercase; }
.rail-overview dl { margin: 1rem 0 0; }
.rail-overview dl div { display: flex; justify-content: space-between; gap: 1rem; margin: .65rem 0; font-size: .76rem; }
.rail-overview dt { color: var(--article-muted); }
.rail-overview dd { margin: 0; font-variant-numeric: tabular-nums; }

.article-reading { min-width: 0; padding: clamp(1.5rem, 4vw, 3.5rem); border: 1px solid var(--brand-line); border-radius: var(--brand-radius-lg); background: var(--brand-surface-solid); box-shadow: var(--brand-shadow-sm); }
:deep(.md-editor) { background: var(--brand-surface-solid); }
:deep(.md-editor-preview-wrapper) { padding: 0; }
:deep(.md-editor-preview) { color: var(--article-ink); font-size: 1.05rem; line-height: 1.95; }
:deep(.md-editor-preview p), :deep(.md-editor-preview li) { color: color-mix(in srgb, var(--article-ink) 88%, transparent); }
:deep(.md-editor-preview h1), :deep(.md-editor-preview h2), :deep(.md-editor-preview h3) { scroll-margin-top: 6rem; color: var(--article-ink); letter-spacing: -.03em; }
:deep(.md-editor-preview h2) { margin-top: 3.6rem; padding-top: 1rem; border-top: 1px solid var(--article-line); font-size: clamp(1.65rem, 3vw, 2.25rem); }
:deep(.md-editor-preview h3) { margin-top: 2.5rem; font-size: 1.35rem; }
:deep(.md-editor-preview a) { color: var(--article-accent); text-decoration-thickness: 1px; text-underline-offset: .2em; }
:deep(.md-editor-preview blockquote) { margin: 2rem 0; padding: 1.1rem 1.35rem; border-left: 3px solid var(--article-accent); border-radius: 0 var(--brand-radius-sm) var(--brand-radius-sm) 0; background: var(--brand-accent-soft); color: var(--article-muted); }
:deep(.md-editor-preview pre) { margin: 2rem 0; border: 1px solid rgba(255,255,255,.08); border-radius: var(--brand-radius-sm); box-shadow: 0 1rem 3rem rgba(4, 13, 29, .09); }
:deep(.md-editor-preview img) { border-radius: var(--brand-radius-sm); box-shadow: var(--brand-shadow-sm); }
:deep(.md-editor-preview table) { display: block; max-width: 100%; overflow-x: auto; border-radius: var(--brand-radius-sm); }

.article-license { margin: 5rem 0 2rem; padding: 1.5rem 0; display: grid; grid-template-columns: 8rem 1fr; gap: .5rem 1.5rem; border-block: 1px solid var(--article-line); font-size: .82rem; }
.article-license p { grid-row: 1 / 4; margin: 0; color: var(--article-accent); font-size: .68rem; letter-spacing: .14em; text-transform: uppercase; }
.article-license strong { font-size: .95rem; }
.article-license span { color: var(--article-muted); line-height: 1.7; }
.article-license a { max-width: 100%; overflow: hidden; color: var(--article-muted); text-overflow: ellipsis; white-space: nowrap; }
.article-ending { padding: 1.5rem 0 3rem; }
.article-tags, .article-actions { display: flex; flex-wrap: wrap; gap: .65rem; }
.article-tags { margin-bottom: 1.25rem; }
.article-tags button, .article-actions button { border: 0; background: transparent; color: var(--article-muted); cursor: pointer; }
.article-tags button { padding: .35rem 0; font-size: .78rem; }
.article-tags button:hover, .article-actions button:hover, .article-actions button.active { color: var(--article-accent); }
.article-actions button { min-height: 2.75rem; padding: .6rem .8rem; display: inline-flex; align-items: center; gap: .45rem; border: 1px solid var(--article-line); font-size: .78rem; }
.article-actions button:focus-visible, .article-tags button:focus-visible, .article-kicker button:focus-visible, .article-neighbours button:focus-visible { outline: 2px solid var(--article-accent); outline-offset: 3px; }
.qr-code { display: flex; flex-direction: column; gap: .6rem; align-items: center; }
.qr-code .el-image { width: 9rem; height: 9rem; }

.article-neighbours { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); border-block: 1px solid var(--article-line); }
.article-neighbours button { min-height: 8.5rem; padding: 1.4rem 0; border: 0; background: transparent; color: var(--article-ink); text-align: left; cursor: pointer; }
.article-neighbours button + button { padding-left: 1.5rem; border-left: 1px solid var(--article-line); }
.article-neighbours span { display: block; margin-bottom: .6rem; color: var(--article-muted); font-size: .68rem; letter-spacing: .12em; }
.article-neighbours strong { display: block; max-width: 25ch; font-size: 1rem; line-height: 1.5; }
.article-neighbours button:hover strong { color: var(--article-accent); }
.article-comments { padding-top: 5rem; }
.section-heading { margin-bottom: 2rem; }
.section-heading span { color: var(--article-accent); font-size: .68rem; letter-spacing: .18em; }
.section-heading h2 { margin: .55rem 0 0; font-size: clamp(1.8rem, 4vw, 2.8rem); letter-spacing: -.04em; }
.article-loading { min-height: 70dvh; display: grid; place-content: center; text-align: center; }
.article-loading span { color: var(--article-accent); font-size: .7rem; letter-spacing: .24em; }
.article-loading p { font-size: 1.1rem; }

.mobile-directory-button { display: none; }
:deep(.rail-directory .card) { width: 100%; margin: 0; padding: 0 !important; border: 0; border-radius: 0; background: transparent; box-shadow: none; }
:deep(.rail-directory .card .title) { padding: 0 0 .8rem; background: transparent; font-size: .78rem; }
:deep(.rail-directory .card .title svg) { display: none; }
:deep(.rail-directory .card .title span) { margin-left: 0 !important; color: var(--article-muted); font-size: .68rem; letter-spacing: .16em; }
:deep(.rail-directory .md-editor-catalog-link span) { margin: .25rem 0; padding: .3rem 0; color: var(--article-muted); font-size: .76rem; line-height: 1.4; }
:deep(.rail-directory .md-editor-catalog-active > span) { padding-left: .65rem; border-left: 2px solid var(--article-accent); border-radius: 0; background: transparent; color: var(--article-ink); }

@media (max-width: 900px) {
  .article-page { background: var(--brand-canvas); }
  .article-shell { width: min(calc(100% - 2rem), 48rem); padding-top: 3.5rem; }
  .article-layout { display: block; }
  .article-rail { display: none; }
  .article-intro { margin-bottom: 2.25rem; }
  .article-intro h1 { font-size: clamp(1.75rem, 6.5vw, 2.35rem); }
  .mobile-directory-button { position: fixed; z-index: 20; right: 1rem; bottom: 1rem; min-height: 2.75rem; padding: .65rem .8rem; display: inline-flex; align-items: center; gap: .4rem; border: 1px solid var(--article-line); border-radius: var(--brand-radius-sm); background: rgba(255,255,255,.92); color: var(--article-ink); backdrop-filter: blur(14px); box-shadow: var(--brand-shadow-sm); }
}

@media (max-width: 560px) {
  .article-shell { width: calc(100% - 1.25rem); padding: 2.4rem 0 4rem; }
  .article-intro { padding-left: 1rem; }
  .article-kicker { justify-content: space-between; }
  .article-intro h1 { margin-top: 1rem; font-size: clamp(1.65rem, 7.5vw, 2.1rem); line-height: 1.22; }
  .article-byline { gap: .55rem 1rem; font-size: .68rem; }
  .article-byline span:not(:first-child)::before { margin-right: 1rem; }
  .article-cover { margin-top: 2rem; }
  .article-cover img { min-height: 13rem; max-height: 21rem; }
  .article-cover figcaption { text-align: left; }
  :deep(.md-editor-preview) { font-size: 1rem; line-height: 1.85; }
  :deep(.md-editor-preview h2) { margin-top: 3rem; }
  .article-reading { padding: 1.1rem; border-radius: var(--brand-radius-md); }
  .article-license { grid-template-columns: 1fr; }
  .article-license p { grid-row: auto; }
  .article-actions button { flex: 1 1 calc(50% - .65rem); justify-content: center; }
  .article-neighbours { grid-template-columns: 1fr; }
  .article-neighbours button { min-height: 7rem; }
  .article-neighbours button + button { padding-left: 0; border-top: 1px solid var(--article-line); border-left: 0; }
}

@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after { scroll-behavior: auto !important; }
}
</style>
