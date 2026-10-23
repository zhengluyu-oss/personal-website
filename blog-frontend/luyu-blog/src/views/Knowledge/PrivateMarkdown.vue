<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { MdPreview } from 'md-editor-v3'
import { sanitizeRenderedHtml } from '@/utils/sanitize-html'
import { knowledgeImageBlob } from '@/utils/knowledge-api'

const props = defineProps<{ body: string }>()
const replacements = ref<Record<string, string>>({})
const rendered = computed(() => props.body.replace(/knowledge-image:([0-9a-f-]{36})/gi, (all, id: string) => replacements.value[id.toLowerCase()] || all))
const allowed = new Set<string>()
let generation = 0

function release() {
  generation++
  for (const url of allowed) URL.revokeObjectURL(url)
  allowed.clear()
  replacements.value = {}
}
function sanitize(html: string): string {
  const template = document.createElement('template')
  template.innerHTML = html
  template.content.querySelectorAll('img').forEach(image => { if (!allowed.has(image.getAttribute('src') || '')) image.remove() })
  template.content.querySelectorAll('picture,source,video,audio,svg,math,iframe,object,embed,style,link,script').forEach(element => element.remove())
  return sanitizeRenderedHtml(template.innerHTML)
}
const imageIds = (body: string) => [...new Set(Array.from(body.matchAll(/knowledge-image:([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})/gi), match => match[1].toLowerCase()))]
watch(() => imageIds(props.body).join(','), async (ids) => {
  release()
  const current = generation
  const loaded: Record<string, string> = {}
  for (const id of ids ? ids.split(',') : []) {
    try {
      const blob = await knowledgeImageBlob(id)
      if (current !== generation) return
      if (!['image/jpeg', 'image/png', 'image/webp'].includes(blob.type)) continue
      const url = URL.createObjectURL(blob)
      allowed.add(url)
      loaded[id] = url
    } catch { /* A missing or revoked image stays hidden. */ }
  }
  if (current === generation) replacements.value = loaded
}, { immediate: true })
window.addEventListener('knowledge-access-invalid', release)
onBeforeUnmount(() => { release(); window.removeEventListener('knowledge-access-invalid', release) })
</script>

<template><MdPreview :model-value="rendered" :sanitize="sanitize" /></template>
