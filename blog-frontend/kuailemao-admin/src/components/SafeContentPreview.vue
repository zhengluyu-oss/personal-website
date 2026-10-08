<script setup lang="ts">
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import { sanitizeRenderedHtml } from '../../../kuailemao-blog/src/utils/sanitize-html'

defineProps<{ content: string, markdown?: boolean }>()
</script>

<template>
  <MdPreview v-if="markdown" :model-value="content" :sanitize="sanitizeRenderedHtml" />
  <div v-else class="plain-content">{{ content }}</div>
</template>

<style scoped>
.plain-content { white-space: pre-wrap; overflow-wrap: anywhere; }
</style>
