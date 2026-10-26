<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { MdPreview } from 'md-editor-v3'
import { sanitizeRenderedHtml } from '~/utils/sanitize-html'
import { knowledgeImageUrl } from '~/utils/knowledge-api'

const props = defineProps<{ body: string }>()
const replacements = ref<Record<string, string>>({})
const rendered = computed(() => props.body.replace(/knowledge-image:([0-9a-f-]{36})/gi, (all, id: string) => replacements.value[id.toLowerCase()] || all))
const allowed = new Set<string>()
let generation = 0

function release() {
  generation++
  allowed.clear()
  replacements.value = {}
}
function sanitize(html: string): string {
  const template = document.createElement('template')
  template.innerHTML = html
  template.content.querySelectorAll('img').forEach(image => {
    if (!allowed.has(image.getAttribute('src') || '')) image.remove()
    else image.setAttribute('referrerpolicy', 'no-referrer')
  })
  template.content.querySelectorAll('picture,source,video,audio,svg,math,iframe,object,embed,style,link,script').forEach(element => element.remove())
  return sanitizeRenderedHtml(template.innerHTML)
}
const imageIds = (body: string) => [...new Set(Array.from(body.matchAll(/knowledge-image:([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})/gi), match => match[1].toLowerCase()))]
watch(() => imageIds(props.body).join(','), (ids) => {
  release()
  const current = generation
  for (const id of ids ? ids.split(',') : []) void (async () => {
    try {
      const url = await knowledgeImageUrl(id)
      if (current !== generation) return
      if (new URL(url).protocol !== 'https:') return
      allowed.add(url)
      replacements.value = { ...replacements.value, [id]: url }
    } catch { /* A missing or revoked image stays hidden. */ }
  })()
}, { immediate: true })
window.addEventListener('knowledge-access-invalid', release)
onBeforeUnmount(() => { release(); window.removeEventListener('knowledge-access-invalid', release) })
</script>

<template><MdPreview :model-value="rendered" :sanitize="sanitize" /></template>
