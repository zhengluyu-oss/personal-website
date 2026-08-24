<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { categoryList } from '@/apis/category'
import WritingPageHeader from '@/components/Writing/WritingPageHeader.vue'

const categories = ref<Array<{ id: number; categoryName: string; articleCount?: number }>>([])
onMounted(async () => {
  const res = await categoryList()
  if (res.code === 200) categories.value = res.data || []
})
</script>

<template>
  <Main only-father-container>
    <template #content>
      <section class="category-index">
        <WritingPageHeader title="文章分类" description="按主题进入技术笔记与项目复盘。" :meta="`${categories.length} categories`" />
        <nav class="category-grid" aria-label="文章分类">
          <router-link v-for="item in categories" :key="item.id" :to="`/blog/categories/${item.id}`">
            <strong>{{ item.categoryName }}</strong><span>{{ item.articleCount || 0 }} 篇</span>
          </router-link>
        </nav>
      </section>
    </template>
  </Main>
</template>

<style scoped>
.category-index { width: min(calc(100% - 1rem), 72rem); margin-inline: auto; padding: clamp(1.25rem, 3vw, 2.5rem); }
.category-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(14rem, 1fr)); gap: 1rem; }
.category-grid a { display: flex; justify-content: space-between; gap: 1rem; padding: 1.25rem; border: 1px solid var(--brand-line); border-radius: 1rem; color: var(--brand-ink); background: var(--brand-surface); text-decoration: none; }
.category-grid a:hover { border-color: var(--brand-accent); transform: translateY(-2px); }
.category-grid span { color: var(--brand-ink-soft); font-size: .82rem; }
</style>
