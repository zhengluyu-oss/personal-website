<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { MdPreview } from 'md-editor-v3'
import { sanitizePrivateDiary } from '~/utils/knowledge-preview'
import { knowledgeRequest as request, knowledgeArticleOptions, knowledgeUploadArticleCover, type KnowledgeEntry, type KnowledgeFileInfo, type KnowledgePage } from '~/utils/knowledge-api'

type Source = { entryId: string; revision: number; fileVersion: string | null; segmentOrdinal: number | null }
type ExtractedSegment = { ordinal: number; page: number | null; fromLine: number | null; toLine: number | null; text: string }
type SavedSource = { source: Source; title: string; kind: string; text: string }
type ExportResult = { exportId: string; state: string; articleId: number | null; snapshot: { sources: SavedSource[] } }
type Category = { id: number; categoryName: string }
type Tag = { id: number; tagName: string }
const props = defineProps<{ selected?: KnowledgeEntry; canManage: boolean }>()
const active = ref(false), sources = ref<{ source: Source; title: string }[]>([])
const revisions = ref<{ revision: number; content: { title: string } }[]>([])
const segments = ref<ExtractedSegment[]>([]), segmentPage = ref(1), segmentTotal = ref(0), selectedOrdinal = ref(-1)
const sourceRevision = ref(0), title = ref(''), body = ref(''), categoryId = ref(0), tagIds = ref<number[]>([])
const categories = ref<Category[]>([]), tags = ref<Tag[]>([]), coverUrl = ref('')
const ready = ref<ExportResult>(), busy = ref(false), error = ref(''), notice = ref('')
const snapshotOpen = ref(false)
const exportId = ref(crypto.randomUUID()), pendingFingerprint = ref('')
let versionRequest = 0
let segmentRequest = 0
const requestBody = computed(() => ({ exportId: exportId.value, title: title.value, body: body.value,
  categoryId: categoryId.value, tagIds: tagIds.value, coverUrl: coverUrl.value, sources: sources.value.map(s => s.source) }))
const fingerprint = computed(() => JSON.stringify({ ...requestBody.value, exportId: undefined }))
const confirmed = computed(() => !!ready.value && pendingFingerprint.value === fingerprint.value)

async function show() {
  active.value = !active.value
  if (!active.value) { versionRequest++; segmentRequest++; revisions.value = []; segments.value = []; return }
  try {
    const [categoryList, tagList] = await Promise.all([
      knowledgeArticleOptions<Category[]>('/category/list'), knowledgeArticleOptions<Tag[]>('/tag/list'),
    ])
    categories.value = categoryList; tags.value = tagList
    await loadVersions()
    await loadSegments()
  } catch (failure: any) { error.value = failure.message }
}
async function loadVersions() {
  const current = ++versionRequest
  revisions.value = []; sourceRevision.value = props.selected?.revision || 0
  if (!props.selected || !active.value) return
  const entryId = props.selected.id
  try {
    const result = await request<KnowledgePage<{ revision: number; content: { title: string } }>>('GET', `/manage/entries/${entryId}/history`)
    if (current === versionRequest && active.value && props.selected?.id === entryId) revisions.value = result.records
  } catch (failure: any) { if (current === versionRequest) error.value = failure.message }
}
async function loadSegments() {
  const current = ++segmentRequest, entry = props.selected
  segments.value = []; segmentTotal.value = 0; selectedOrdinal.value = -1
  if (!active.value || !entry?.fileVersion || entry.content.kind !== 'DOCUMENT') return
  try {
    const info = await request<KnowledgeFileInfo>('GET', `/view/files/${entry.id}/${entry.fileVersion}`)
    if (current !== segmentRequest || props.selected?.id !== entry.id || props.selected.fileVersion !== entry.fileVersion || !active.value || info.state !== 'READY') return
    const result = await request<KnowledgePage<ExtractedSegment>>('GET', `/view/files/${entry.id}/${entry.fileVersion}/segments?page=${segmentPage.value}`)
    if (current === segmentRequest && props.selected?.id === entry.id && props.selected.fileVersion === entry.fileVersion && active.value) {
      segments.value = result.records; segmentTotal.value = result.total
    }
  } catch (failure: any) { if (current === segmentRequest) error.value = failure.message }
}
watch(() => [props.selected?.id, props.selected?.fileVersion], () => { segmentPage.value = 1; void loadVersions(); void loadSegments() })
function addSource() {
  if (!props.selected || props.selected.revision < 1 || props.selected.deletedAt || !sourceRevision.value) return
  if (sources.value.length >= 10) { error.value = '最多选择10个来源'; return }
  if (sources.value.some(item => item.source.entryId === props.selected!.id && item.source.revision === sourceRevision.value && item.source.segmentOrdinal === null)) return
  sources.value.push({ source: { entryId: props.selected.id, revision: sourceRevision.value, fileVersion: null, segmentOrdinal: null }, title: props.selected.content.title })
  if (!title.value) title.value = props.selected.content.title
  if (!body.value) body.value = `# ${title.value}\n\n请在这里整理、改写资料后再创建草稿。`
  ready.value = undefined
}
function addSegment() {
  const entry=props.selected, segment=segments.value.find(item => item.ordinal === selectedOrdinal.value)
  if (!entry?.fileVersion || !segment || entry.deletedAt) return
  if (sources.value.length >= 10) { error.value = '最多选择10个来源'; return }
  if (sources.value.some(item => item.source.entryId === entry.id && item.source.fileVersion === entry.fileVersion && item.source.segmentOrdinal === segment.ordinal)) return
  sources.value.push({ source: { entryId: entry.id, revision: entry.revision, fileVersion: entry.fileVersion, segmentOrdinal: segment.ordinal }, title: `${entry.content.title} · ${segment.page ? `第 ${segment.page} 页` : `第 ${segment.fromLine}–${segment.toLine} 行`}` })
  ready.value = undefined
}
async function upload(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  busy.value = true; error.value = ''
  try { coverUrl.value = await knowledgeUploadArticleCover(file); notice.value = '封面已上传，创建草稿前请保留本页' }
  catch (failure: any) { error.value = failure.message }
  finally { busy.value = false }
}
function normalizeRequest() {
  if (!sources.value.length || !title.value.trim() || !body.value.trim() || !categoryId.value || !tagIds.value.length || !coverUrl.value) {
    throw new Error('请选择来源，并填完标题、正文、文章分类、标签和封面')
  }
  if (pendingFingerprint.value && pendingFingerprint.value !== fingerprint.value) {
    exportId.value = crypto.randomUUID(); ready.value = undefined
  }
  pendingFingerprint.value = fingerprint.value
  return requestBody.value
}
async function prepare() {
  busy.value = true; error.value = ''; notice.value = ''
  try { ready.value = await request<ExportResult>('POST', '/manage/exports/prepare', normalizeRequest()); notice.value = '来源快照已确认，请阅读最终正文后再创建草稿' }
  catch (failure: any) { error.value = failure.message }
  finally { busy.value = false }
}
async function create() {
  if (!confirmed.value || busy.value || !window.confirm('确认生成未公开的博客草稿？此操作不会发布文章。')) return
  busy.value = true; error.value = ''
  try {
    ready.value = await request<ExportResult>('POST', '/manage/exports/create', requestBody.value)
    notice.value = '草稿创建成功，可前往原后台继续编辑、审阅和发布。'
  } catch (failure: any) { error.value = failure.message + '；请保留本页，使用相同内容再次点击创建以核对结果。' }
  finally { busy.value = false }
}
</script>

<template>
  <section v-if="canManage" class="draft" aria-label="整理为博客草稿">
    <button @click="show">{{ active ? '收起草稿整理' : '整理为博客草稿' }}</button>
    <div v-if="active">
      <h3>选择来源，再亲自整理正文</h3><p>最多10条笔记、概念或已提取的文件片段；来源快照仅你可查看。</p>
      <div v-if="selected && !selected.deletedAt && selected.revision > 0" class="source-picker">
        <strong>当前条目：{{ selected.content.title }}</strong>
        <label>来源版本<select v-model.number="sourceRevision"><option v-for="revision in revisions" :key="revision.revision" :value="revision.revision">第 {{ revision.revision }} 版</option></select></label>
        <button @click="addSource">加入来源</button>
      </div>
      <div v-if="selected?.content.kind === 'DOCUMENT' && selected.fileVersion" class="source-picker">
        <label>当前文件片段<select v-model.number="selectedOrdinal"><option :value="-1">{{ segments.length ? '请选择页码或行号' : '暂无完整提取结果' }}</option><option v-for="segment in segments" :key="segment.ordinal" :value="segment.ordinal">{{ segment.page ? `第 ${segment.page} 页` : `第 ${segment.fromLine}–${segment.toLine} 行` }} · {{ segment.text.slice(0, 32) }}</option></select></label>
        <button :disabled="selectedOrdinal < 0" @click="addSegment">加入片段</button>
        <button :disabled="segmentPage <= 1" @click="segmentPage--; loadSegments()">上一页</button><span>{{ segmentPage }} / {{ Math.max(1, Math.ceil(segmentTotal / 20)) }}</span><button :disabled="segmentPage * 20 >= segmentTotal" @click="segmentPage++; loadSegments()">下一页</button>
      </div>
      <ol><li v-for="(item, index) in sources" :key="item.source.entryId + ':' + item.source.revision">{{ item.title }} · 第 {{ item.source.revision }} 版 <button @click="sources.splice(index,1); ready = undefined">移除</button></li></ol>
      <label>文章标题<input v-model="title" maxlength="200"></label>
      <label>分类<select v-model.number="categoryId"><option :value="0">请选择文章分类</option><option v-for="item in categories" :key="item.id" :value="item.id">{{ item.categoryName }}</option></select></label>
      <label>标签<select v-model="tagIds" multiple><option v-for="item in tags" :key="item.id" :value="item.id">{{ item.tagName }}</option></select></label>
      <label>文章封面<input type="file" accept="image/png,image/jpeg,image/webp" @change="upload"></label>
      <p v-if="coverUrl">已上传封面：{{ coverUrl }}</p>
      <label>人工整理的 Markdown 正文<textarea v-model="body" rows="14" spellcheck="false"></textarea></label>
      <h4>文章预览</h4><MdPreview :model-value="body" :sanitize="sanitizePrivateDiary" no-mermaid no-katex no-highlight />
      <div class="actions"><button :disabled="busy" @click="prepare">确认来源与预览</button><button :disabled="!confirmed || busy" @click="create">{{ busy ? '处理中…' : '创建未公开草稿' }}</button></div>
      <p v-if="notice" role="status">{{ notice }}</p><p v-if="error" role="alert">{{ error }}</p>
      <div v-if="ready"><p>导出状态：{{ ready.state }} · 来源 {{ ready.snapshot.sources.length }} 条</p>
        <details @toggle="snapshotOpen = ($event.target as HTMLDetailsElement).open"><summary>核对本次来源快照（仅站主可见）</summary><template v-if="snapshotOpen"><div v-for="source in ready.snapshot.sources" :key="source.source.entryId + ':' + source.source.revision"><strong>{{ source.title }} · 第 {{ source.source.revision }} 版</strong><pre>{{ source.text }}</pre></div></template></details>
        <a v-if="ready.articleId" :href="`/admin/articles/${ready.articleId}`">在后台编辑草稿</a>
      </div>
    </div>
  </section>
</template>

<style scoped>
.draft{margin:24px 0;padding:20px;border:1px solid #dfe5ed;border-radius:12px;background:var(--card-bg,#fff)}.draft h3{margin-bottom:0}.draft p{line-height:1.6}.draft label{display:grid;gap:7px;margin:12px 0}.draft input,.draft select,.draft textarea,.draft button{font:inherit;padding:9px;border:1px solid #cdd6e2;border-radius:6px;max-width:100%;box-sizing:border-box;background:transparent;color:inherit}.draft button{cursor:pointer}.draft button:disabled{opacity:.5}.draft textarea{width:100%;resize:vertical}.source-picker,.actions{display:flex;gap:12px;align-items:end;flex-wrap:wrap}.source-picker label{min-width:130px}.draft li{margin:8px 0}.draft a{color:#285de0}
.draft pre{white-space:pre-wrap;overflow-wrap:anywhere;max-height:360px;overflow:auto;background:#f5f7fb;padding:12px;border-radius:6px}
</style>
