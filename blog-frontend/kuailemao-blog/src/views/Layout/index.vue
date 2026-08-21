<script setup lang="ts">
import {defineAsyncComponent, onBeforeUnmount, onMounted, ref} from 'vue'

const Footer = defineAsyncComponent(() => import('@/components/Layout/Footer/index.vue'))
const BottomRightLayout = defineAsyncComponent(() => import('@/components/BottomRightLayout/index.vue'))
const footerTrigger = ref<HTMLElement | null>(null)
const footerReady = ref(false)
let footerObserver: IntersectionObserver | undefined

const noDisplayComponent = computed(() => {
  // 树洞不显示底部
  return useRoute().path != '/tree-hole';
})

onMounted(() => {
  if (!footerTrigger.value || !('IntersectionObserver' in window)) {
    footerReady.value = true
    return
  }
  footerObserver = new IntersectionObserver(([entry]) => {
    if (!entry.isIntersecting) return
    footerReady.value = true
    footerObserver?.disconnect()
  })
  footerObserver.observe(footerTrigger.value)
})

onBeforeUnmount(() => footerObserver?.disconnect())

</script>

<template>
  <Header/>
  <div style="min-height: 100vh">
    <router-view v-slot="{Component}">
      <transition name="el-fade-in-linear" mode="out-in">
        <component :is="Component"/>
      </transition>
    </router-view>
  </div>
  <div ref="footerTrigger" class="footer-trigger" aria-hidden="true" />
  <template v-if="noDisplayComponent && footerReady">
    <Footer/>
  </template>
  <BottomRightLayout to-top/>
</template>

<style scoped lang="scss">
.footer-trigger { width: 1px; height: 1px; }
</style>
