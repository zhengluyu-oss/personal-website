<script setup lang="ts">
import { useRouter } from 'vue-router'

export interface WritingArticleItem {
  id: number | string
  articleTitle: string
  articleCover?: string
  articleContent?: string
  categoryName?: string
  createTime?: string
  visitCount?: number
  tags?: Array<{ id: number; tagName: string }>
}

const props = defineProps<{
  articles: WritingArticleItem[]
  emptyText?: string
}>()

const router = useRouter()

const excerpt = (value?: string, limit = 72) => {
  const text = (value || '').replace(/[*#>`~\[\]()\s]+/g, ' ').trim()
  return text.length > limit ? `${text.slice(0, limit)}…` : text
}

const displayDate = (value?: string) => value?.slice(0, 10) || ''

function openArticle(id: number | string) {
  router.push(`/article/${id}`)
}
</script>

<template>
  <div class="writing-article-list">
    <p v-if="!articles.length" class="writing-article-list__empty">
      {{ emptyText || '暂无文章' }}
    </p>
    <article
      v-for="item in articles"
      :key="item.id"
      class="writing-article-card"
      tabindex="0"
      @click="openArticle(item.id)"
      @keydown.enter="openArticle(item.id)"
    >
      <div class="writing-article-card__cover">
        <img
          v-if="item.articleCover"
          :src="item.articleCover"
          :alt="item.articleTitle"
          loading="lazy"
        >
        <div v-else class="writing-article-card__cover-placeholder" aria-hidden="true" />
      </div>
      <div class="writing-article-card__body">
        <div class="writing-article-card__meta">
          <time>{{ displayDate(item.createTime) }}</time>
          <span v-if="item.categoryName" class="writing-article-card__dot">{{ item.categoryName }}</span>
          <span v-if="item.visitCount !== undefined" class="writing-article-card__dot">{{ item.visitCount }} 阅读</span>
        </div>
        <h3>{{ item.articleTitle }}</h3>
        <p v-if="item.articleContent" class="writing-article-card__excerpt">
          {{ excerpt(item.articleContent) }}
        </p>
        <div v-if="item.tags?.length" class="writing-article-card__tags">
          <span
            v-for="tag in item.tags"
            :key="tag.id"
            @click.stop="router.push(`/tags/${tag.id}`)"
          >#{{ tag.tagName }}</span>
        </div>
      </div>
      <span class="writing-article-card__arrow" aria-hidden="true">↗</span>
    </article>
  </div>
</template>

<style scoped lang="scss">
.writing-article-list {
  display: grid;
  gap: 0;
}

.writing-article-list__empty {
  margin: 0;
  padding: 2rem 0;
  color: var(--brand-ink-soft);
  text-align: center;
}

.writing-article-card {
  --accent: var(--brand-accent);
  position: relative;
  display: grid;
  grid-template-columns: minmax(9rem, 16rem) minmax(0, 1fr) auto;
  gap: clamp(1rem, 2.5vw, 1.5rem);
  align-items: start;
  padding: 1.35rem 0;
  border-top: 1px solid var(--brand-line);
  cursor: pointer;
  transition: background 0.22s ease;

  &:last-child {
    border-bottom: 1px solid var(--brand-line);
  }

  &:hover,
  &:focus-visible {
    background: var(--brand-accent-soft);
    outline: none;
  }

  &:hover h3,
  &:focus-visible h3 {
    color: var(--accent);
  }

  &__cover {
    overflow: hidden;
    aspect-ratio: 16 / 10;
    border-radius: var(--brand-radius-sm);
    background: var(--brand-canvas-soft);

    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
      transition: transform 0.45s cubic-bezier(0.2, 0.65, 0.3, 1);
    }
  }

  &__cover-placeholder {
    width: 100%;
    height: 100%;
    background:
      linear-gradient(135deg, var(--brand-accent-soft), var(--brand-canvas-soft)),
      repeating-linear-gradient(
        -45deg,
        transparent,
        transparent 8px,
        rgba(211, 95, 69, 0.06) 8px,
        rgba(211, 95, 69, 0.06) 9px
      );
  }

  &:hover &__cover img {
    transform: scale(1.04);
  }

  &__body {
    min-width: 0;
  }

  &__meta {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 0.45rem;
    color: var(--brand-ink-faint);
    font-family: "Share TechMono", monospace;
    font-size: 0.66rem;
  }

  &__dot::before {
    content: '·';
    margin-right: 0.45rem;
    color: var(--brand-line);
  }

  h3 {
    margin: 0.55rem 0 0;
    font-size: clamp(1.05rem, 2vw, 1.28rem);
    font-weight: 700;
    line-height: 1.35;
    letter-spacing: -0.02em;
    transition: color 0.2s ease;
  }

  &__excerpt {
    margin: 0.55rem 0 0;
    color: var(--brand-ink-soft);
    font-size: 0.88rem;
    line-height: 1.65;
  }

  &__tags {
    display: flex;
    flex-wrap: wrap;
    gap: 0.5rem;
    margin-top: 0.65rem;

    span {
      color: var(--brand-ink-faint);
      font-size: 0.75rem;
      transition: color 0.2s ease;

      &:hover {
        color: var(--accent);
      }
    }
  }

  &__arrow {
    align-self: center;
    color: var(--accent);
    font-size: 1.1rem;
  }
}

@media (max-width: 720px) {
  .writing-article-card {
    grid-template-columns: 1fr auto;

    &__cover {
      grid-column: 1 / -1;
      aspect-ratio: 16 / 9;
    }

    &__arrow {
      align-self: start;
      margin-top: 0.35rem;
    }
  }
}

@media (prefers-reduced-motion: reduce) {
  .writing-article-card,
  .writing-article-card__cover img,
  .writing-article-card h3 {
    transition: none;
  }
}
</style>
