<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import useWebsiteStore from '@/store/modules/website'
import { resolveHomeHero } from '@/utils/home-hero'
import Images from '../Images/index.vue'

const website = useWebsiteStore()
const hero = computed(() => resolveHomeHero(website.webInfo))
const actions = computed(() => [hero.value.primary, hero.value.secondary].filter(item => item !== null))
const videoUrl = computed(() => website.webInfo?.heroVideoUrl?.trim() || '')
const shell = ref<HTMLElement>()
const video = ref<HTMLVideoElement>()
const visible = ref(true)
const reducedMotion = ref(false)
const saveData = ref(false)
const manualLoad = ref(false)
const pausedByUser = ref(false)
const videoPlaying = ref(false)
const videoFailed = ref(false)
const coverReady = ref(false)
const mediaActivated = ref(false)
const allowAuto = computed(() => !reducedMotion.value && !saveData.value)
const videoSource = computed(() => mediaActivated.value ? videoUrl.value : '')
let observer: IntersectionObserver | undefined
let motionPreference: MediaQueryList | undefined

async function attemptPlay() {
  if (!videoSource.value || !visible.value || pausedByUser.value || videoFailed.value) return
  await nextTick()
  try { await video.value?.play() }
  catch { videoPlaying.value = false }
}
async function toggleVideo() {
  if (videoPlaying.value) { pausedByUser.value = true; video.value?.pause(); return }
  const retry = videoFailed.value
  pausedByUser.value = false
  videoFailed.value = false
  manualLoad.value = true
  await nextTick()
  if (retry) video.value?.load()
  await attemptPlay()
}
function onMotionChange() {
  reducedMotion.value = motionPreference?.matches || false
  if (reducedMotion.value) video.value?.pause()
}
function onVideoError() {
  if (videoSource.value) videoFailed.value = true
  videoPlaying.value = false
}
watch(videoUrl, () => { videoFailed.value = false; manualLoad.value = false; pausedByUser.value = false; mediaActivated.value = false })
watch([videoUrl, visible, coverReady, allowAuto, manualLoad], () => {
  if (videoUrl.value && visible.value && ((allowAuto.value && coverReady.value) || manualLoad.value)) mediaActivated.value = true
})
watch([videoSource, visible], () => {
  if (!videoSource.value || !visible.value) { video.value?.pause(); videoPlaying.value = false }
  else if (allowAuto.value && !pausedByUser.value) void attemptPlay()
})
onMounted(() => {
  motionPreference = window.matchMedia('(prefers-reduced-motion: reduce)')
  onMotionChange()
  motionPreference.addEventListener('change', onMotionChange)
  saveData.value = (navigator as Navigator & { connection?: { saveData?: boolean } }).connection?.saveData === true
  if (shell.value) {
    observer = new IntersectionObserver(([entry]) => { visible.value = entry.isIntersecting }, { threshold: 0.05 })
    observer.observe(shell.value)
  }
  if (videoSource.value) void attemptPlay()
})
onBeforeUnmount(() => {
  observer?.disconnect()
  motionPreference?.removeEventListener('change', onMotionChange)
  video.value?.pause()
})
</script>

<template>
  <div ref="shell" class="hero-shell">
    <div class="hero-media" aria-hidden="true">
      <Images @ready="coverReady = true" />
      <video v-if="videoUrl" ref="video" class="hero-video" :class="{ 'hero-video--playing': videoPlaying }"
        :src="videoSource || undefined" muted loop playsinline preload="none" aria-hidden="true"
        @playing="videoPlaying = true" @pause="videoPlaying = false" @error="onVideoError" />
    </div>
    <div class="hero-shade" aria-hidden="true" />
    <div class="hero-content">
      <div class="hero-copy">
        <div class="hero-identity"><span>{{ hero.identityRole }}</span><small v-if="hero.kicker">{{ hero.kicker }}</small></div>
        <h1>{{ hero.identityName }}</h1>
        <div class="hero-intro"><p class="hero-statement">{{ hero.title }}</p><p class="hero-subtitle">{{ hero.subtitle }}</p></div>
        <nav v-if="actions.length" class="hero-actions" aria-label="首页快捷入口">
          <template v-for="(action, index) in actions" :key="action.href">
            <a v-if="action.external" class="hero-link" :class="{ 'hero-link--solid': index === 0 }" :href="action.href" target="_blank" rel="noopener noreferrer">{{ action.text }}<span aria-hidden="true">↗</span></a>
            <RouterLink v-else class="hero-link" :class="{ 'hero-link--solid': index === 0 }" :to="action.href">{{ action.text }}<span aria-hidden="true">→</span></RouterLink>
          </template>
        </nav>
        <p v-if="hero.description" class="hero-description">{{ hero.description }}</p>
        <aside v-if="hero.asideLabel || hero.asideText" class="hero-aside" aria-label="个人简介摘要">
          <strong v-if="hero.asideLabel">{{ hero.asideLabel }}</strong><p v-if="hero.asideText">{{ hero.asideText }}</p>
        </aside>
      </div>
      <button v-if="videoUrl" class="hero-play" type="button" :aria-label="videoPlaying ? '暂停背景视频' : '播放背景视频'" @click="toggleVideo">{{ videoPlaying ? '暂停视频' : '播放视频' }}</button>
    </div>
  </div>
</template>

<style scoped lang="scss">
.hero-shell { position: relative; display: flex; align-items: center; width: 100%; min-height: calc(100svh - 4.5rem); overflow: hidden; isolation: isolate; background: #10243b; color: #fff; }
.hero-media, .hero-shade { position: absolute; inset: 0; }
.hero-media { z-index: -2; }
.hero-media :deep(.hero-image), .hero-media :deep(.home-media) { width: 100%; height: 100%; aspect-ratio: auto; border-radius: 0; }
.hero-media :deep(.home-media img) { object-fit: cover; object-position: center; }
.hero-media :deep(.media-status) { color: rgba(255,255,255,.75); }
.hero-video { position: absolute; inset: 0; display: block; width: 100%; height: 100%; object-fit: cover; opacity: 0; transition: opacity .35s ease; }
.hero-video--playing { opacity: 1; }
.hero-shade { z-index: -1; background: linear-gradient(90deg, rgba(4,16,33,.86), rgba(7,22,39,.63) 46%, rgba(7,22,39,.24)), linear-gradient(0deg, rgba(4,16,33,.55), transparent 42%); pointer-events: none; }
.hero-content { display: flex; justify-content: space-between; align-items: flex-end; gap: 2rem; width: min(calc(100% - 4rem),76rem); margin: auto; padding: clamp(3rem,7vh,6rem) 0; }
.hero-copy { min-width: 0; max-width: 45rem; text-shadow: 0 2px 12px rgba(0,0,0,.2); }
.hero-identity { display: flex; flex-wrap: wrap; align-items: center; gap: .45rem 1rem; margin-bottom: .9rem; color: #bad9ff; font-family: "Share TechMono", Consolas, monospace; }
.hero-identity span { font-size: .9rem; font-weight: 700; letter-spacing: .04em; }
.hero-identity small { font-size: .72rem; letter-spacing: .08em; }
.hero-copy h1 { margin: 0; color: #fff; font-family: "阿里妈妈方圆体 VF Regular", "PingFang SC", sans-serif; font-size: clamp(3rem,5.8vw,5.25rem); font-weight: 740; line-height: 1.08; letter-spacing: -.055em; text-wrap: balance; overflow-wrap: anywhere; }
.hero-intro { margin-top: 1.1rem; }
.hero-statement { margin: 0; font-size: clamp(1.2rem,1.9vw,1.55rem); font-weight: 650; line-height: 1.5; }
.hero-subtitle { margin: .65rem 0 0; color: #e4ecf7; font-size: 1rem; line-height: 1.75; }
.hero-actions { display: flex; flex-wrap: wrap; gap: .75rem; margin-top: 1.75rem; }
.hero-link { display: inline-flex; align-items: center; justify-content: center; gap: 1rem; min-height: 2.85rem; padding: .7rem 1.15rem; border: 1px solid rgba(255,255,255,.5); border-radius: var(--brand-radius-sm); color: #fff; background: rgba(4,16,33,.28); font-size: .95rem; font-weight: 650; text-decoration: none; white-space: nowrap; }
.hero-link--solid { color: #10243b; background: #fff; border-color: #fff; }
.hero-link:hover { background: rgba(255,255,255,.2); }
.hero-link--solid:hover { background: #f3e8e3; }
.hero-link:focus-visible, .hero-play:focus-visible { outline: 2px solid #fff; outline-offset: 4px; }
.hero-description { max-width: 54ch; margin: 1.4rem 0 0; color: #e4ecf7; font-size: .9rem; line-height: 1.75; white-space: pre-line; }
.hero-aside { display: grid; gap: .35rem; max-width: 42rem; margin-top: 1.5rem; padding-left: 1rem; border-left: 2px solid rgba(255,255,255,.65); color: #e4ecf7; font-size: .95rem; line-height: 1.65; }
.hero-aside strong { color: #fff; font-weight: 600; }
.hero-aside p { margin: 0; white-space: pre-line; }
.hero-play { flex: 0 0 auto; padding: .65rem 1rem; border: 1px solid rgba(255,255,255,.7); border-radius: var(--brand-radius-sm); background: rgba(4,16,33,.45); color: #fff; cursor: pointer; }
@media (max-width: 767px) { .hero-shell { min-height: calc(100svh - 4rem); } .hero-shade { background: linear-gradient(0deg, rgba(4,16,33,.83), rgba(4,16,33,.55)); } .hero-content { display: block; width: calc(100% - 2rem); padding: 2.5rem 0; } .hero-copy h1 { font-size: clamp(2.7rem,14vw,4rem); } .hero-statement { font-size: 1.18rem; } .hero-actions { margin-top: 1.25rem; } .hero-link { gap: .65rem; padding: .7rem .9rem; font-size: .9rem; } .hero-play { margin-top: 1.5rem; } }
@media (max-width: 390px) { .hero-identity small { flex-basis: 100%; } }
@media (prefers-reduced-motion: reduce) { .hero-video { transition: none; } }
</style>
