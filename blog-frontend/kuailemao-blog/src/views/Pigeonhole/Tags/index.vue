<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { tagList } from '@/apis/tag'
import { whereArticleList } from '@/apis/article'
import WritingPageHeader from '@/components/Writing/WritingPageHeader.vue'
import WritingArticleList, { type WritingArticleItem } from '@/components/Writing/WritingArticleList.vue'
import WritingFilterNav from '@/components/Writing/WritingFilterNav.vue'

interface TagItem {
  id: number
  tagName: string
  articleCount?: number
}

const route = useRoute()
const tags = ref<TagItem[]>([])
const articleList = ref<WritingArticleItem[]>([])
const loading = ref(true)

const isDetailView = computed(() => Boolean(route.params.id))
const activeTagId = computed(() => route.params.id as string | undefined)
const activeTag = computed(() =>
  tags.value.find(item => String(item.id) === String(activeTagId.value)),
)
const totalArticles = computed(() =>
  tags.value.reduce((sum, item) => sum + Number(item.articleCount || 0), 0),
)
const filterItems = computed(() =>
  tags.value.map(item => ({ id: item.id, label: `#${item.tagName}` })),
)

async function loadTags() {
  const res = await tagList()
  if (res.code === 200) {
    tags.value = res.data || []
  }
}

async function loadTagArticles(id: string) {
  const res = await whereArticleList(2, id)
  articleList.value = res.code === 200 && res.data ? res.data : []
}

async function bootstrap() {
  loading.value = true
  try {
    await loadTags()
    if (route.params.id) {
      await loadTagArticles(String(route.params.id))
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
      await loadTagArticles(String(id))
    } else {
      articleList.value = []
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
            title="标签索引"
            description="按关键词快速定位相关文章，与首页写作区保持同一套视觉语言。"
            :meta="`${tags.length} tags · ${totalArticles} articles`"
          />

          <p v-if="loading" class="writing-page__state">加载中…</p>

          <nav v-else class="tag-index" aria-label="标签列表">
            <router-link
              v-for="tag in tags"
              :key="tag.id"
              :to="`/tags/${tag.id}`"
              class="tag-index__item"
            >
              <span>#{{ tag.tagName }}</span>
              <em>{{ tag.articleCount || 0 }}</em>
            </router-link>
          </nav>
        </template>

        <template v-else>
          <WritingPageHeader
            :title="`#${activeTag?.tagName || '标签'}`"
            description="该标签下的全部文章。"
            :meta="`${articleList.length} articles`"
          />

          <WritingFilterNav
            v-if="filterItems.length"
            :items="filterItems"
            :active-id="activeTagId"
            base-path="/tags"
          />

          <p v-if="loading" class="writing-page__state">加载中…</p>
          <WritingArticleList
            v-else
            :articles="articleList"
            empty-text="该标签下暂无文章。"
          />
        </template>
      </div>
    </template>
  </Main>
</template>

<style scoped lang="scss">
.writing-page {
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

.tag-index {
  display: flex;
  flex-wrap: wrap;
  gap: 0.65rem;
}

.tag-index__item {
  display: inline-flex;
  align-items: center;
  gap: 0.55rem;
  padding: 0.55rem 0.9rem;
  border: 1px solid var(--brand-line);
  border-radius: var(--brand-radius-sm);
  background: var(--brand-canvas-soft);
  color: var(--brand-ink-soft);
  font-size: 0.88rem;
  font-weight: 600;
  text-decoration: none;
  transition: border-color 0.2s ease, color 0.2s ease, transform 0.2s ease;

  em {
    min-width: 1.4rem;
    padding: 0.1rem 0.45rem;
    border-radius: 999px;
    background: var(--brand-accent-soft);
    color: var(--brand-accent-strong);
    font-style: normal;
    font-family: "Share TechMono", monospace;
    font-size: 0.62rem;
    text-align: center;
  }

  &:hover {
    border-color: var(--brand-accent);
    color: var(--brand-accent-strong);
    transform: translateY(-2px);
  }
}
</style>
