<script setup lang="ts">
defineProps<{
  title: string
  description?: string
  meta?: string
}>()
</script>

<template>
  <header class="writing-page-header">
    <div>
      <h1>{{ title }}</h1>
      <p v-if="description">{{ description }}</p>
    </div>
    <p v-if="meta" class="writing-page-header__meta">{{ meta }}</p>
  </header>
</template>

<style scoped lang="scss">
.writing-page-header {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 1.5rem;
  padding-bottom: clamp(1.5rem, 4vw, 2.5rem);
  margin-bottom: clamp(1.5rem, 4vw, 2.5rem);
  border-bottom: 1px solid var(--brand-line);

  h1 {
    margin: 0;
    font-size: clamp(2rem, 5vw, 3.2rem);
    font-weight: 750;
    line-height: 1.05;
    letter-spacing: -0.055em;
    color: var(--brand-ink);
  }

  p {
    max-width: 36rem;
    margin: 0.75rem 0 0;
    color: var(--brand-ink-soft);
    font-size: 0.95rem;
    line-height: 1.7;
  }

  &__meta {
    margin: 0;
    color: var(--brand-ink-faint);
    font-family: "Share TechMono", monospace;
    font-size: 0.72rem;
    white-space: nowrap;
  }
}

@media (max-width: 720px) {
  .writing-page-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.75rem;
  }
}
</style>
