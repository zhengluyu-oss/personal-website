<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { MdPreview } from 'md-editor-v3'
import Connections from './Connections.vue'
import 'md-editor-v3/lib/style.css'
import { sanitizePrivateDiary } from '@/utils/diary-preview'
import useUserStore from '@/store/modules/user'
import { knowledgeRequest as request, knowledgeDownloadFile, type KnowledgeCapability, type KnowledgeEntry, type KnowledgeFileInfo, type KnowledgeLabel, type KnowledgePage } from '@/utils/knowledge-api'

type SearchLocation = { excerpt: string; page: number | null; fromLine: number | null; toLine: number | null }
type Segment = { ordinal: number; page: number | null; fromLine: number | null; toLine: number | null; text: string }
const user = useUserStore()
const capability = ref<KnowledgeCapability>(), rows = ref<KnowledgeEntry[]>([]), labels = ref<KnowledgeLabel[]>([])
const selected = ref<KnowledgeEntry>(), currentFile = ref<KnowledgeFileInfo>(), segments = ref<Segment[]>([])
const keyword = ref(''), kind = ref(''), category = ref(''), tag = ref(''), page = ref(1), total = ref(0)
const segmentPage = ref(1), segmentTotal = ref(0), showSegments = ref(false)
const matches = ref<Record<string, SearchLocation>>({}), busy = ref(false), error = ref('')
const categories = computed(() => labels.value.filter(item => item.kind === 'CATEGORY'))
const tags = computed(() => labels.value.filter(item => item.kind === 'TAG'))
const kinds = { NOTE: '笔记', DOCUMENT: '文件资料', CONCEPT: '概念' }
let generation = 0, listSequence = 0, detailSequence = 0, segmentSequence = 0, disposed = false
let timer: ReturnType<typeof setInterval> | undefined

function clear() {
  generation++; listSequence++; detailSequence++; segmentSequence++
  rows.value = []; labels.value = []; selected.value = undefined; currentFile.value = undefined
  segments.value = []; showSegments.value = false; total.value = 0; matches.value = {}
}
function denied() { clear(); capability.value = undefined; error.value = '查看资格已变化，请重新确认权限。' }
async function check(reset = false) {
  if (reset) { clear(); capability.value = undefined }
  const current = generation
  try {
    const result = await request<KnowledgeCapability>('GET', '/view/capabilities')
    if (disposed || current !== generation) return
    if (!result.allowed) clear()
    capability.value = result
    if (reset && result.allowed && result.available) await load()
  } catch (failure: any) { if (current === generation) error.value = failure.message }
}
async function load() {
  const seq = ++listSequence, current = generation; busy.value = true; error.value = ''
  try {
    const [result, options] = await Promise.all([
      request<KnowledgePage<{ entry: KnowledgeEntry } & SearchLocation>>('POST', '/view/search', {
        keyword: keyword.value || null, kind: kind.value || null, categoryId: category.value || null,
        tagId: tag.value || null, page: page.value, pageSize: 20, deleted: false,
      }),
      request<KnowledgeLabel[]>('GET', '/view/labels'),
    ])
    if (disposed || seq !== listSequence || current !== generation) return
    rows.value = result.records.map(hit => hit.entry)
    matches.value = Object.fromEntries(result.records.map(({ entry, excerpt, page, fromLine, toLine }) =>
      [entry.id, { excerpt, page, fromLine, toLine }]))
    total.value = result.total; labels.value = options
  } catch (failure: any) { if (seq === listSequence && current === generation) error.value = failure.message }
  finally { if (seq === listSequence && current === generation) busy.value = false }
}
async function open(id: string) {
  const seq = ++detailSequence, current = generation; error.value = ''; currentFile.value = undefined
  segments.value = []; showSegments.value = false; segmentSequence++
  try {
    const entry = await request<KnowledgeEntry>('GET', `/view/entries/${id}`)
    if (disposed || seq !== detailSequence || current !== generation) return
    selected.value = entry
    if (entry.fileVersion) void refreshFile(entry.id, entry.fileVersion, current, seq)
  } catch (failure: any) { if (seq === detailSequence && current === generation) error.value = failure.message }
}
async function refreshFile(id: string, version: string, current = generation, seq = detailSequence) {
  try {
    const result = await request<KnowledgeFileInfo>('GET', `/view/files/${id}/${version}`)
    if (!disposed && current === generation && seq === detailSequence && selected.value?.id === id && selected.value.fileVersion === version) currentFile.value = result
  } catch (failure: any) { if (current === generation && seq === detailSequence) error.value = failure.message }
}
async function loadSegments(number = 1) {
  if (!selected.value?.fileVersion || currentFile.value?.state !== 'READY') return
  const id = selected.value.id, version = selected.value.fileVersion, current = generation, seq = detailSequence, segmentSeq = ++segmentSequence
  try {
    const result = await request<KnowledgePage<Segment>>('GET', `/view/files/${id}/${version}/segments?page=${number}`)
    if (disposed || current !== generation || seq !== detailSequence || segmentSeq !== segmentSequence || selected.value?.fileVersion !== version) return
    segments.value = result.records; segmentPage.value = number; segmentTotal.value = result.total; showSegments.value = true
  } catch (failure: any) { if (current === generation) error.value = failure.message }
}
async function download() {
  if (!selected.value || !currentFile.value) return
  try { await knowledgeDownloadFile(selected.value.id, currentFile.value.version, currentFile.value.filename) }
  catch (failure: any) { error.value = failure.message }
}
watch(() => user.token, () => { void check(true) }, { immediate: true, flush: 'sync' })
onMounted(() => {
  window.addEventListener('knowledge-access-invalid', denied)
  window.addEventListener('focus', refresh)
  timer = setInterval(refresh, 30000)
})
const refresh = () => { void check() }
onBeforeUnmount(() => { disposed = true; clear(); clearInterval(timer); window.removeEventListener('knowledge-access-invalid', denied); window.removeEventListener('focus', refresh) })
</script>

<template>
  <main class="knowledge-reader">
    <header><div><p class="eyebrow">个人资料与知识关联</p><h1>知识库</h1><p>浏览笔记、资料和它们之间的关联。</p></div></header>
    <p v-if="error" role="alert" class="error">{{ error }}</p>
    <section v-if="!capability?.allowed || !capability.available" class="empty"><h2>笔记、资料、概念</h2><p>{{ capability?.message || '正在确认知识库访问权限…' }}</p><button @click="check(true)">重新检查</button></section>
    <template v-else>
      <form class="filters" @submit.prevent="page = 1; load()">
        <input v-model="keyword" aria-label="关键词" placeholder="搜索标题、正文、标签…" maxlength="100">
        <select v-model="kind" aria-label="条目类型"><option value="">全部类型</option><option v-for="(name, key) in kinds" :key="key" :value="key">{{ name }}</option></select>
        <select v-model="category" aria-label="分类"><option value="">全部分类</option><option v-for="item in categories" :key="item.id" :value="item.id">{{ item.name }}</option></select>
        <select v-model="tag" aria-label="标签"><option value="">全部标签</option><option v-for="item in tags" :key="item.id" :value="item.id">{{ item.name }}</option></select>
        <button :disabled="busy">{{ busy ? '读取中…' : '查找' }}</button>
      </form>
      <div class="columns">
        <aside><p>{{ total }} 个条目</p><ul><li v-for="row in rows" :key="row.id"><button :class="{ active: selected?.id === row.id }" @click="open(row.id)"><small>{{ kinds[row.content.kind] }}</small><strong>{{ row.content.title }}</strong><span v-if="keyword">{{ matches[row.id]?.excerpt }}</span><small v-if="keyword && matches[row.id]?.page">命中第 {{ matches[row.id].page }} 页</small><small v-else-if="keyword && matches[row.id]?.fromLine">命中第 {{ matches[row.id].fromLine }}–{{ matches[row.id].toLine }} 行</small><time>{{ new Date(row.updatedAt).toLocaleDateString('zh-CN') }}</time></button></li></ul><p v-if="!busy && !rows.length">当前范围没有条目。</p><nav><button :disabled="page <= 1 || busy" @click="page--; load()">上一页</button><span>{{ page }}</span><button :disabled="page * 20 >= total || busy" @click="page++; load()">下一页</button></nav></aside>
        <section class="detail">
          <template v-if="selected">
            <div v-if="keyword && matches[selected.id]?.excerpt" class="search-context"><strong>本次检索命中</strong><span v-if="matches[selected.id].page"> · 文件第 {{ matches[selected.id].page }} 页</span><span v-else-if="matches[selected.id].fromLine"> · 第 {{ matches[selected.id].fromLine }}–{{ matches[selected.id].toLine }} 行</span><p>{{ matches[selected.id].excerpt }}</p></div>
            <h2>{{ selected.content.title }}</h2>
            <p v-if="selected.content.sourceUrl">来源：<a :href="selected.content.sourceUrl" rel="noopener noreferrer" target="_blank">{{ selected.content.sourceUrl }}</a></p>
            <section v-if="selected.content.kind === 'DOCUMENT' && selected.fileVersion && capability.filesAvailable" class="knowledge-file"><h3>原始文件</h3><p v-if="currentFile">{{ currentFile.filename }} · {{ (currentFile.sizeBytes / 1048576).toFixed(1) }} MiB · {{ currentFile.state === 'READY' ? '文字已提取，可检索' : '文字提取尚未完成' }}</p><button v-if="currentFile" @click="download">下载原文件</button><button v-if="currentFile?.state === 'READY'" @click="showSegments ? showSegments = false : loadSegments()">{{ showSegments ? '收起提取内容' : '查看提取内容与页码' }}</button><div v-if="showSegments" class="extracted-content"><article v-for="segment in segments" :key="segment.ordinal"><strong>{{ segment.page ? `第 ${segment.page} 页` : `第 ${segment.fromLine}–${segment.toLine} 行` }}</strong><pre>{{ segment.text }}</pre></article><nav><button :disabled="segmentPage <= 1" @click="loadSegments(segmentPage - 1)">上一页</button><span>{{ segmentPage }} / {{ Math.max(1, Math.ceil(segmentTotal / 20)) }}</span><button :disabled="segmentPage * 20 >= segmentTotal" @click="loadSegments(segmentPage + 1)">下一页</button></nav></div></section>
            <MdPreview :model-value="selected.content.body" :sanitize="sanitizePrivateDiary" />
            <Connections :key="selected.id" :entry="selected" @open="open" />
          </template>
          <div v-else class="empty"><h2>从一条笔记开始</h2><p>选择左侧资料阅读；维护知识库请前往管理后台。</p></div>
        </section>
      </div>
    </template>
  </main>
</template>

<style scoped>
.knowledge-reader{max-width:1280px;margin:90px auto 50px;padding:24px;color:var(--font-color,#24364b)}header{border-bottom:1px solid #dfe5ed;padding-bottom:24px}h1{font-size:32px;margin:5px 0}p{line-height:1.8}.eyebrow,small,time{color:#64748b;font-size:12px}.filters,nav{display:flex;flex-wrap:wrap;gap:12px;align-items:center}.filters{padding:22px 0}.filters>input{flex:1;min-width:160px}.columns{display:grid;grid-template-columns:280px minmax(0,1fr);gap:28px}aside,.detail{border:1px solid #dfe5ed;border-radius:12px;padding:20px;background:var(--card-bg,#fff)}ul{list-style:none;padding:0}li button{display:grid;text-align:left;width:100%;gap:7px;margin:8px 0;padding:14px}.active,.search-context{background:#edf3ff}.search-context{padding:14px;border-left:3px solid #285de0;overflow-wrap:anywhere}.knowledge-file,.extracted-content{border-top:1px solid #dfe5ed;margin-top:16px;padding-top:12px}.extracted-content pre{white-space:pre-wrap;overflow-wrap:anywhere;max-height:420px;overflow:auto;font:inherit;line-height:1.7}input,select,button{font:inherit;border:1px solid #cdd6e2;border-radius:6px;padding:9px 12px;max-width:100%;box-sizing:border-box}button{cursor:pointer;background:transparent;color:inherit}button:disabled{opacity:.5;cursor:default}.empty{padding:35px 10px}.error{border-left:3px solid #b23d32;background:#fff1ee;padding:14px}strong{overflow-wrap:anywhere}a{color:#285de0}@media(max-width:760px){.knowledge-reader{padding:16px;margin-top:70px}.columns{grid-template-columns:1fr}aside,.detail{padding:16px}}
</style>
