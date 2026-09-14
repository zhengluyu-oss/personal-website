<script setup lang="ts">
import { ref, watch } from 'vue'
const props = withDefaults(defineProps<{ src?: string; alt: string; eager?: boolean; emptyText?: string }>(), { emptyText: '暂无封面' })
const failed = ref(false)
const pending = ref(true)
const attempt = ref(0)
watch(() => props.src, () => { failed.value = false; pending.value = true })
function retry() { attempt.value++; failed.value = false; pending.value = true }
</script>

<template>
  <div class="home-media">
    <img v-if="src && !failed" :key="src + ':' + attempt" :src="src" :alt="alt"
      :loading="eager ? 'eager' : 'lazy'" :fetchpriority="eager ? 'high' : 'auto'" decoding="async"
      @load="pending = false" @error="failed = true; pending = false">
    <div v-if="!src || failed" class="media-status" role="status">
      <p>{{ failed ? '图片暂时无法显示' : emptyText }}</p>
      <button v-if="failed" type="button" @click="retry">重新加载图片</button>
    </div>
    <div v-else-if="pending" class="media-loading" aria-hidden="true" />
  </div>
</template>

<style scoped lang="scss">
.home-media { position: relative; aspect-ratio: 16 / 9; overflow: hidden; background: var(--brand-canvas-soft); border-radius: var(--brand-radius-md); }
.home-media img { display: block; width: 100%; height: 100%; object-fit: contain; }
.media-status { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: .75rem; padding: 1rem; color: var(--brand-ink-soft); text-align: center; }
.media-status p { margin: 0; font-size: .95rem; }
.media-status button { padding: .5rem .85rem; border: 1px solid var(--brand-line); border-radius: var(--brand-radius-sm); background: var(--brand-surface); color: var(--brand-accent-strong); font-size: .9rem; cursor: pointer; }
.media-status button:hover { background: var(--brand-accent-soft); }
.media-status button:focus-visible { outline: 2px solid var(--brand-accent); outline-offset: 3px; }
.media-loading { position: absolute; inset: 0; background: linear-gradient(100deg, var(--brand-canvas-soft) 30%, var(--brand-surface) 50%, var(--brand-canvas-soft) 70%); background-size: 300% 100%; animation: media-shimmer 1.4s linear infinite; pointer-events: none; }
@keyframes media-shimmer { to { background-position-x: -200%; } }
@media (prefers-reduced-motion: reduce) { .media-loading { animation: none; } }
</style>
