<script setup lang="ts">
import WritingArticleList, { type WritingArticleItem } from '@/components/Writing/WritingArticleList.vue'

defineProps<{
  articleList: WritingArticleItem[]
}>()
</script>

<template>
  <WritingArticleList :articles="articleList" />
</template>
