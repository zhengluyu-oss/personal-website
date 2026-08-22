<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { categoryList } from '@/apis/category'
import { whereArticleList } from '@/apis/article'
import { getArticleList } from '@/apis/home'
import WritingPageHeader from '@/components/Writing/WritingPageHeader.vue'
import WritingArticleList, { type WritingArticleItem } from '@/components/Writing/WritingArticleList.vue'
import WritingFilterNav from '@/components/Writing/WritingFilterNav.vue'

interface CategoryItem {
  id: number
  categoryName: string
  articleCount?: number
}

const route = useRoute()
const categories = ref<CategoryItem[]>([])
const articleList = ref<WritingArticleItem[]>([])
const groupedArticles = ref<Array<{ category: CategoryItem; articles: WritingArticleItem[] }>>([])
const loading = ref(true)

const isDetailView = computed(() => Boolean(route.params.id))
const activeCategoryId = computed(() => route.params.id as string | undefined)
const activeCategory = computed(() =>
  categories.value.find(item => String(item.id) === String(activeCategoryId.value)),
)
const totalArticles = computed(() =>
  categories.value.reduce((sum, item) => sum + Number(item.articleCount || 0), 0),
)
const filterItems = computed(() =>
  categories.value.map(item => ({ id: item.id, label: item.categoryName })),
)

async function loadCategories() {
  const res = await categoryList()
  if (res.code === 200) {
    categories.value = res.data || []
  }
}

async function loadGroupedArticles() {
  const res = await getArticleList(1, 100)
  const articles: WritingArticleItem[] = res?.data?.page || []
  const map = new Map<number, WritingArticleItem[]>()

  articles.forEach(article => {
    const categoryId = Number((article as WritingArticleItem & { categoryId?: number }).categoryId)
    if (!categoryId) return
    const bucket = map.get(categoryId) || []
    bucket.push(article)
    map.set(categoryId, bucket)
  })

  groupedArticles.value = categories.value
    .filter(category => (map.get(category.id)?.length || Number(category.articleCount) > 0))
    .map(category => ({
      category,
      articles: (map.get(category.id) || []).slice(0, 3),
    }))
    .filter(group => group.articles.length > 0)
}

async function loadCategoryArticles(id: string) {
  const res = await whereArticleList(1, id)
  articleList.value = res.code === 200 && res.data ? res.data : []
}

async function bootstrap() {
  loading.value = true
  try {
    await loadCategories()
    if (route.params.id) {
      await loadCategoryArticles(String(route.params.id))
    } else {
      await loadGroupedArticles()
    }
  } finally {
    loading.value = false
  }
}

onMounted(bootstrap)

watch(() => route.params.id, async (id) => {
  loading.value = true
  try {
    if (id) {
      await loadCategoryArticles(String(id))
    } else {
      await loadGroupedArticles()
    }
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <Main only-father-container>
    <template #content>
      <div class="writing-page">
        <template v-if="!isDetailView">
          <WritingPageHeader
            title="技术写作"
            description="按主题整理的技术笔记与项目复盘，延续首页「近期写作」的阅读体验。"
            :meta="`${totalArticles} articles`"
          />

          <p v-if="loading" class="writing-page__state">加载中…</p>

          <section
            v-for="group in groupedArticles"
            :key="group.category.id"
            class="writing-section"
          >
            <header class="writing-section__header">
              <h2>{{ group.category.categoryName }}</h2>
              <router-link :to="`/category/${group.category.id}`">
                查看全部 <span aria-hidden="true">→</span>
              </router-link>
            </header>
            <WritingArticleList :articles="group.articles" />
          </section>

          <section v-if="!loading && !groupedArticles.length" class="writing-section">
            <WritingArticleList :articles="[]" empty-text="暂无文章，稍后再来看看。" />
          </section>
        </template>

        <template v-else>
          <WritingPageHeader
            :title="activeCategory?.categoryName || '文章分类'"
            description="该分类下的全部文章。"
            :meta="`${articleList.length} articles`"
          />

          <WritingFilterNav
            v-if="filterItems.length"
            :items="filterItems"
            :active-id="activeCategoryId"
            base-path="/category"
          />

          <p v-if="loading" class="writing-page__state">加载中…</p>
          <WritingArticleList
            v-else
            :articles="articleList"
            empty-text="该分类下暂无文章。"
          />
        </template>
      </div>
    </template>
  </Main>
</template>

<style scoped lang="scss">
.writing-page {
  --accent: var(--brand-accent);
  width: min(calc(100% - 1rem), 72rem);
  margin-inline: auto;
  padding: clamp(1.25rem, 3vw, 2.5rem);
}

.writing-page__state {
  margin: 0;
  padding: 2rem 0;
  color: var(--brand-ink-soft);
  text-align: center;
}

.writing-section + .writing-section {
  margin-top: clamp(2.5rem, 6vw, 4rem);
}

.writing-section__header {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 0.5rem;

  h2 {
    margin: 0;
    font-size: clamp(1.35rem, 3vw, 2rem);
    font-weight: 700;
    letter-spacing: -0.04em;
    color: var(--brand-ink);
  }

  a {
    color: var(--brand-ink);
    font-size: 0.78rem;
    font-weight: 700;
    text-decoration: none;
    white-space: nowrap;

    span {
      color: var(--accent);
    }
  }
}

@media (max-width: 720px) {
  .writing-section__header {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
