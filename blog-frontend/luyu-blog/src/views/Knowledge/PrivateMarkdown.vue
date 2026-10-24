<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { MdCatalog, MdPreview } from 'md-editor-v3'
import { sanitizeRenderedHtml } from '@/utils/sanitize-html'
import { knowledgeImageBlob } from '@/utils/knowledge-api'

const props = defineProps<{ body: string }>()
const editorId = 'knowledge-preview'
const scrollElement = document.documentElement
const hasHeadings = ref(false)
const catalogOpen = ref(false)
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
function updateCatalog(html: string) {
  const template = document.createElement('template')
  template.innerHTML = html
  hasHeadings.value = Boolean(template.content.querySelector('h1,h2,h3,h4,h5,h6'))
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

<template>
  <div class="knowledge-markdown" :class="{ 'has-catalog': hasHeadings }">
    <nav v-if="hasHeadings" class="knowledge-catalog" aria-label="正文目录">
      <button class="catalog-toggle" type="button" :aria-expanded="catalogOpen" aria-controls="knowledge-catalog-list" @click="catalogOpen = !catalogOpen">目录 <span aria-hidden="true">{{ catalogOpen ? '收起' : '展开' }}</span></button>
      <div id="knowledge-catalog-list" class="catalog-list" :class="{ open: catalogOpen }" @click="catalogOpen = false">
        <strong class="catalog-heading">目录</strong>
        <MdCatalog :editor-id="editorId" :scroll-element="scrollElement" :scroll-element-offset-top="88" />
      </div>
    </nav>
    <MdPreview :editor-id="editorId" theme="light" :model-value="rendered" :sanitize="sanitize" :on-html-changed="updateCatalog" />
  </div>
</template>

<style scoped>
.knowledge-markdown{min-width:0}
.knowledge-markdown.has-catalog{display:grid;grid-template-columns:180px minmax(0,1fr);gap:24px;align-items:start}
.knowledge-catalog{position:sticky;top:96px;min-width:0;max-height:calc(100dvh - 112px);overflow-y:auto;border-left:2px solid var(--brand-accent,#285de0);padding-left:14px}
.catalog-heading{display:block;margin-bottom:12px;font-size:.9rem}
.catalog-toggle{display:none}
.catalog-list :deep(.md-editor-catalog-link span){display:block;margin:.2rem 0;padding:.3rem 0;color:var(--brand-ink-soft,#64748b);font-size:.82rem;line-height:1.5;overflow-wrap:anywhere}
.catalog-list :deep(.md-editor-catalog-active > span){padding-left:.6rem;border-left:2px solid var(--brand-accent,#285de0);color:var(--brand-ink,#24364b);font-weight:600}
.knowledge-markdown :deep(.md-editor-preview h1),.knowledge-markdown :deep(.md-editor-preview h2),.knowledge-markdown :deep(.md-editor-preview h3),.knowledge-markdown :deep(.md-editor-preview h4),.knowledge-markdown :deep(.md-editor-preview h5),.knowledge-markdown :deep(.md-editor-preview h6){scroll-margin-top:96px}
@media(max-width:1100px){
  .knowledge-markdown.has-catalog{display:block}
  .knowledge-catalog{position:static;max-height:none;overflow:visible;margin:0 0 18px;padding:0;border:0}
  .catalog-toggle{display:flex;align-items:center;justify-content:space-between;width:100%;min-height:44px;padding:10px 14px;border:1px solid #cdd6e2;border-radius:6px;background:transparent;color:inherit;font:inherit;cursor:pointer}
  .catalog-toggle span{font-size:.85rem;color:var(--brand-ink-soft,#64748b)}
  .catalog-list{display:none;max-height:45dvh;overflow-y:auto;margin-top:8px;padding:14px;border:1px solid #dfe5ed;border-radius:6px}
  .catalog-list.open{display:block}
  .catalog-heading{display:none}
}
</style>
