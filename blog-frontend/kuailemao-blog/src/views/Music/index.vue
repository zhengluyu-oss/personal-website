<script setup lang="ts">
const musicUrl = String(import.meta.env.VITE_MUSIC_FRONTEND_URL || '').trim()
const hasMusic = Boolean(musicUrl)
const loading = ref(hasMusic)

function iframeLoaded() {
  loading.value = false
}
</script>

<template>
  <div class="music-page">
    <template v-if="hasMusic">
      <div v-if="loading" class="music-loading" aria-live="polite">正在加载...</div>
      <iframe
        :src="musicUrl"
        class="music-frame"
        :class="{ 'is-ready': !loading }"
        title="音乐空间"
        frameborder="0"
        @load="iframeLoaded"
      />
    </template>
    <div v-else class="music-empty" role="status">
      <h1>音乐空间暂未开放</h1>
      <p>当前环境未配置音乐前端地址，稍后再来看吧。</p>
    </div>
  </div>
</template>

<style scoped lang="scss">
.music-page {
  position: relative;
  width: 100%;
  min-height: calc(100vh - 4rem);
  max-width: 100vw;
  overflow: hidden;
}

.music-loading {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  font-size: 1.1rem;
  color: var(--brand-ink, #10243e);
}

.music-frame {
  width: 100%;
  height: 100vh;
  max-width: 100%;
  border: 0;
  visibility: hidden;
}

.music-frame.is-ready {
  visibility: visible;
}

.music-empty {
  box-sizing: border-box;
  width: min(28rem, calc(100% - 2rem));
  margin: 6rem auto 3rem;
  padding: 2rem 1.25rem;
  text-align: center;
  color: var(--brand-ink, #10243e);
}

.music-empty h1 {
  margin: 0 0 0.75rem;
  font-size: clamp(1.4rem, 5vw, 1.8rem);
}

.music-empty p {
  margin: 0;
  line-height: 1.7;
  color: var(--brand-ink-soft, #52657a);
}
</style>
