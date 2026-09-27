<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useBlogCategories } from '@/composables/useBlogCategories'

const emit = defineEmits(['update:closeDrawer'])
function isClose() { emit('update:closeDrawer') }
const route = useRoute()
const expanded = ref(false)
const toggle = ref<HTMLButtonElement>()
const { categoryEntries, loadCategories } = useBlogCategories()
const blogCategoryEntries = computed(() => categoryEntries.value.filter(entry => entry.categoryId !== 21))
onMounted(loadCategories)
</script>

<template>
  <nav class="mobile-menu" aria-label="手机主导航">
    <RouterLink to="/" @click="isClose">首页</RouterLink>
    <RouterLink to="/experience" :class="{ 'is-section': route.path.startsWith('/experience') }" @click="isClose">工作经历</RouterLink>
    <div class="blog-group" @keydown.esc.stop.prevent="expanded = false; toggle?.focus()">
      <div class="blog-row">
        <RouterLink to="/blog" :class="{ 'is-section': route.path.startsWith('/blog') }" @click="isClose">个人博客</RouterLink>
        <button ref="toggle" type="button" aria-label="展开博客栏目" aria-controls="mobile-categories" :aria-expanded="expanded" @click="expanded = !expanded">{{ expanded ? '收起' : '展开' }}</button>
      </div>
      <div v-show="expanded" id="mobile-categories" class="categories">
        <RouterLink to="/blog" @click="isClose">全部栏目</RouterLink>
        <RouterLink v-for="entry in blogCategoryEntries" :key="entry.categoryId" :to="entry.path" @click="isClose"><span>{{ entry.category.categoryName }}</span><small v-if="entry.category.articleCount !== undefined">{{ entry.category.articleCount }}</small></RouterLink>
      </div>
    </div>
    <RouterLink to="/website-shares" :class="{ 'is-section': route.path.startsWith('/website-shares') }" @click="isClose">网站分享</RouterLink>
    <RouterLink to="/photos" @click="isClose">相册</RouterLink>
    <RouterLink to="/about" @click="isClose">关于我</RouterLink>
  </nav>
</template>

<style scoped lang="scss">
.mobile-menu { display: grid; gap: .35rem; }
.mobile-menu a { display: flex; align-items: center; justify-content: space-between; gap: .6rem; min-height: 44px; padding: .75rem; color: var(--brand-ink); border-radius: var(--brand-radius-sm); text-decoration: none; overflow-wrap: anywhere; }
.mobile-menu a:hover, .mobile-menu .router-link-exact-active, .mobile-menu .is-section { background: var(--brand-accent-soft); color: var(--brand-accent-strong); }
.blog-row { display: flex; align-items: center; gap: .5rem; }
.blog-row > a { flex: 1; min-width: 0; }
.blog-row button { min-height: 44px; min-width: 44px; padding: .5rem; border: 0; background: transparent; color: var(--brand-accent-strong); cursor: pointer; }
.categories { margin: .25rem 0 .5rem .75rem; padding-left: .5rem; border-left: 1px solid var(--brand-line); }
.categories small { color: var(--brand-ink-soft); }
.mobile-menu a:focus-visible, .mobile-menu button:focus-visible { outline: 2px solid var(--brand-accent); outline-offset: 2px; }
</style>
