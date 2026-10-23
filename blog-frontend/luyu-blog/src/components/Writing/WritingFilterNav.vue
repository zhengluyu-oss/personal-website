<script setup lang="ts">
export interface WritingFilterItem {
  id: number | string
  label: string
}

defineProps<{
  items: WritingFilterItem[]
  activeId?: number | string
  basePath: string
}>()
</script>

<template>
  <nav class="writing-filter-nav" aria-label="筛选导航">
    <router-link
      v-for="item in items"
      :key="item.id"
      :to="`${basePath}/${item.id}`"
      class="writing-filter-nav__item"
      :class="{ 'is-active': String(item.id) === String(activeId) }"
    >
      {{ item.label }}
    </router-link>
  </nav>
</template>

<style scoped lang="scss">
.writing-filter-nav {
  display: flex;
  flex-wrap: wrap;
  gap: 0.55rem;
  margin-bottom: 1.25rem;

  &__item {
    padding: 0.45rem 0.85rem;
    border: 1px solid var(--brand-line);
    border-radius: var(--brand-radius-sm);
    background: var(--brand-canvas-soft);
    color: var(--brand-ink-soft);
    font-size: 0.82rem;
    font-weight: 600;
    text-decoration: none;
    transition: border-color 0.2s ease, color 0.2s ease, background 0.2s ease;

    &:hover {
      border-color: var(--brand-accent);
      color: var(--brand-accent-strong);
    }

    &.is-active {
      border-color: var(--brand-accent);
      background: var(--brand-accent-soft);
      color: var(--brand-accent-strong);
    }
  }
}
</style>
