<template>
  <div>
    <router-view></router-view>
  </div>
  <!-- 全局loading -->
  <loading></loading>
  <!-- 无音乐后端时不挂载，避免启动时请求 /wapi -->
  <Music v-if="enhancementsReady && enableMusic" />
  <DevToolsBlocker v-if="enhancementsReady && enableDevToolsBlocker" :enableDevToolsBlocker="true" />
  <ContextMenu v-if="enhancementsReady" />
</template>

<script setup lang="ts">
import {defineAsyncComponent, onBeforeUnmount, onMounted, ref} from 'vue'
import {useDark, useToggle} from "@vueuse/core";
import useWebsiteStore from "@/store/modules/website.ts";

const Music = defineAsyncComponent(() => import('@/components/Music/index.vue'))
const DevToolsBlocker = defineAsyncComponent(() => import('@/components/DevToolsBlocker/index.vue'))
const ContextMenu = defineAsyncComponent(() => import('@/components/ContextMenu/index.vue'))

const useWebsite = useWebsiteStore()
const enableMusic = Boolean(import.meta.env.VITE_MUSIC_SERVE)
const enableDevToolsBlocker = import.meta.env.VITE_ENABLE_DEV_TOOLSBLOCKER === 'true'
const enhancementsReady = ref(false)
const revealEnhancements = () => { enhancementsReady.value = true }

onMounted(() => {
  void useWebsite.getInfo()
  window.addEventListener('pointerdown', revealEnhancements, {once: true, passive: true})
})

onBeforeUnmount(() => window.removeEventListener('pointerdown', revealEnhancements))

//  深色切换
useDark({
  selector: 'html',
  attribute: 'class',
  valueLight: 'light',
  valueDark: 'dark'
})

useDark({
  onChanged(dark) {
    useToggle(dark)
  }
})
</script>

<style scoped lang="scss">

</style>
