<script setup lang="ts">
import { backGetBanners } from '@/apis/website'

const canvasRef = ref<HTMLCanvasElement>()
const bannerReady = ref(false)
let loadedImage: HTMLImageElement | undefined

function drawBanner() {
  const canvas = canvasRef.value
  if (!canvas || !loadedImage)
    return
  const width = window.innerWidth
  const height = Math.max(window.innerHeight, 640)
  const ratio = Math.max(width / loadedImage.naturalWidth, height / loadedImage.naturalHeight)
  const drawWidth = loadedImage.naturalWidth * ratio
  const drawHeight = loadedImage.naturalHeight * ratio
  const offsetX = (width - drawWidth) / 2
  const offsetY = (height - drawHeight) / 2
  const pixelRatio = Math.min(window.devicePixelRatio || 1, 2)
  canvas.width = width * pixelRatio
  canvas.height = height * pixelRatio
  canvas.style.width = `${width}px`
  canvas.style.height = `${height}px`
  const context = canvas.getContext('2d')
  context?.setTransform(pixelRatio, 0, 0, pixelRatio, 0, 0)
  context?.drawImage(loadedImage, offsetX, offsetY, drawWidth, drawHeight)
}

function preloadBanner(url: string) {
  const image = new Image()
  image.fetchPriority = 'high'
  image.decoding = 'async'
  image.onload = () => {
    loadedImage = image
    bannerReady.value = true
    nextTick(drawBanner)
  }
  image.onerror = () => { bannerReady.value = false }
  image.src = url
}

onMounted(async () => {
  try {
    const res: any = await backGetBanners()
    const url = (res.data || []).find((image: unknown) => typeof image === 'string' && image.trim()) || ''
    if (url)
      preloadBanner(url)
  }
  catch {
    bannerImage.value = ''
  }
})

onMounted(() => window.addEventListener('resize', drawBanner, { passive: true }))
onBeforeUnmount(() => window.removeEventListener('resize', drawBanner))
</script>

<template>
  <div class="hero-image" :class="{ 'has-image': bannerReady }" aria-hidden="true">
    <canvas ref="canvasRef" class="hero-image__photo" />
    <div class="hero-image__cinema" aria-hidden="true" />
    <div class="hero-image__focus" aria-hidden="true" />
    <div class="hero-image__grain" aria-hidden="true" />
    <div class="hero-image__frame" aria-hidden="true">
      <span class="corner corner--top-left" />
      <span class="corner corner--top-right" />
      <span class="corner corner--bottom-left" />
      <span class="corner corner--bottom-right" />
    </div>
  </div>
</template>

<style scoped lang="scss">
.hero-image {
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  height: 100svh;
  min-height: 40rem;
  overflow: hidden;
  background:
    radial-gradient(circle at 68% 24%, rgba(239, 125, 119, .32), transparent 34%),
    linear-gradient(145deg, #14213e 0%, #382d54 48%, #a6535f 100%);

  &__photo {
    position: absolute;
    inset: -1px;
    width: 100%;
    height: 100%;
    display: block;
  }

  &__cinema {
    position: absolute;
    inset: 0;
    background:
      linear-gradient(180deg, rgba(8, 13, 31, .58) 0%, rgba(8, 13, 31, .08) 28%, transparent 52%, rgba(7, 12, 26, .34) 78%, rgba(7, 12, 26, .82) 100%),
      linear-gradient(90deg, rgba(8, 13, 31, .3) 0%, transparent 32%, transparent 70%, rgba(8, 13, 31, .24) 100%),
      radial-gradient(ellipse at center 43%, transparent 18%, rgba(5, 10, 25, .12) 62%, rgba(5, 10, 25, .46) 112%);
  }

  &__focus {
    position: absolute;
    width: min(58rem, 82vw);
    height: min(30rem, 52vh);
    left: 50%;
    top: 42%;
    transform: translate(-50%, -50%);
    border-radius: 50%;
    background: radial-gradient(ellipse, rgba(255, 178, 143, .1), transparent 67%);
    filter: blur(1px);
  }

  &__grain {
    position: absolute;
    inset: 0;
    opacity: .12;
    background-image:
      radial-gradient(circle at 12% 18%, rgba(255,255,255,.9) 0 .5px, transparent .7px),
      radial-gradient(circle at 72% 64%, rgba(255,255,255,.75) 0 .45px, transparent .65px);
    background-size: 9px 9px, 13px 13px;
    mix-blend-mode: soft-light;
  }

  &__frame {
    position: absolute;
    inset: clamp(5rem, 8vw, 7.5rem) clamp(1.25rem, 4vw, 4rem) clamp(2rem, 4vw, 3.5rem);
    border-left: 1px solid rgba(255,255,255,.12);
    border-right: 1px solid rgba(255,255,255,.12);
    pointer-events: none;
  }

  &::after {
    content: '';
    position: absolute;
    right: -12vw;
    bottom: -26vh;
    width: 54vw;
    height: 48vh;
    border-radius: 50%;
    background: rgba(245, 113, 115, .11);
    filter: blur(90px);
  }
}

.corner {
  position: absolute;
  width: 1.4rem;
  height: 1.4rem;
  border-color: rgba(255,255,255,.4);
  border-style: solid;
  &--top-left { top: 0; left: -.5px; border-width: 1px 0 0 1px; }
  &--top-right { top: 0; right: -.5px; border-width: 1px 1px 0 0; }
  &--bottom-left { bottom: 0; left: -.5px; border-width: 0 0 1px 1px; }
  &--bottom-right { right: -.5px; bottom: 0; border-width: 0 1px 1px 0; }
}

@media (max-width: 640px) {
  .hero-image__cinema {
    background:
      linear-gradient(180deg, rgba(8,13,31,.62), rgba(8,13,31,.08) 30%, rgba(8,13,31,.16) 60%, rgba(7,12,26,.84) 100%),
      radial-gradient(ellipse at center 42%, transparent 12%, rgba(5,10,25,.4) 110%);
  }
  .hero-image__frame { inset: 5.4rem .8rem 1.2rem; }
}
</style>
