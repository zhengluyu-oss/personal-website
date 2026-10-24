<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { MdEditor } from 'md-editor-v3'
import Connections from './Connections.vue'
import Draft from './Draft.vue'
import PrivateMarkdown from './PrivateMarkdown.vue'
import 'md-editor-v3/lib/style.css'
import { useAuthorization } from '~/composables/authorization'
import { knowledgeRequest as request, knowledgeUploadFile, knowledgeDownloadFile, knowledgeUploadImage, type KnowledgeFileInfo, type KnowledgeAck, type KnowledgeCapability, type KnowledgeContent, type KnowledgeEntry, type KnowledgeLabel, type KnowledgePage } from '~/utils/knowledge-api'

const authorization = useAuthorization()
const capability = ref<KnowledgeCapability>(), rows = ref<KnowledgeEntry[]>([]), labels = ref<KnowledgeLabel[]>([])
const selected = ref<KnowledgeEntry>(), editor = ref<KnowledgeContent>(), baseline = ref('')
const keyword = ref(''), kind = ref(''), category = ref(''), tag = ref(''), trash = ref(false), page = ref(1), total = ref(0)
const busy = ref(false), saving = ref(false), error = ref(''), notice = ref(''), preview = ref(true)
const history = ref<{ revision: number; content: KnowledgeContent; savedAt: string }[]>([])
const currentFile = ref<KnowledgeFileInfo>(), uploadingFile = ref(false), uploadingImage = ref(false)
const imageMutations = new Map<string, string>()
type ExtractedSegment = { ordinal: number; page: number | null; fromLine: number | null; toLine: number | null; text: string }
const fileSegments = ref<ExtractedSegment[]>([]), fileSegmentsPage = ref(1), fileSegmentsTotal = ref(0), fileSegmentsVisible = ref(false)
type SearchLocation = { excerpt: string; page: number | null; fromLine: number | null; toLine: number | null }
const searchLocations = ref<Record<string, SearchLocation>>({})
const labelManager = ref(false), labelName = ref(''), labelKind = ref<'CATEGORY' | 'TAG'>('TAG')
const dirty = computed(() => !!editor.value && JSON.stringify(editor.value) !== baseline.value)
const categoryOptions = computed(() => labels.value.filter(l => l.kind === 'CATEGORY'))
const tagOptions = computed(() => labels.value.filter(l => l.kind === 'TAG'))
const kindNames = { NOTE: '笔记', DOCUMENT: '文件资料', CONCEPT: '概念' }
const extractionErrors: Record<string, string> = { ENCRYPTED_PDF: 'PDF 已加密，无法提取文字', NEEDS_OCR: '扫描件没有可提取文字，需要先做 OCR', PAGE_LIMIT: 'PDF 超过 300 页', OUTPUT_LIMIT: '文字提取量超过限制', INVALID_FILE: '文件内容无法解析', TIMEOUT: '解析超时', RESOURCE_LIMIT: '解析进程内存不足或被系统终止', PARSER_UNAVAILABLE: '解析服务暂不可用', STORAGE_UNAVAILABLE: '私有文件存储暂不可用', LEASE_EXPIRED: '解析中断' }
let generation = 0, listSequence = 0, detailSequence = 0, fileSegmentsSequence = 0, disposed = false
let pending: { snapshot: string; mutationId: string } | undefined
let filePending: { entryId: string; revision: number; fingerprint: string; mutationId: string } | undefined
let timer: ReturnType<typeof setInterval> | undefined
const discard = () => !uploadingImage.value && (!dirty.value || window.confirm('编辑尚未获得服务器保存确认，确定离开并放弃吗？'))
function clearPrivate() { generation++; listSequence++; detailSequence++; fileSegmentsSequence++; rows.value = []; labels.value = []; searchLocations.value = {}; selected.value = undefined; editor.value = undefined; currentFile.value = undefined; fileSegments.value = []; fileSegmentsVisible.value = false; history.value = []; total.value = 0; pending = undefined; filePending = undefined; baseline.value = ''; saving.value = false; busy.value = false }
function denied() { clearPrivate(); capability.value = undefined; error.value = '查看资格已变化，请重新确认权限。' }
async function check(reset = false) {
  if (reset) { clearPrivate(); capability.value = undefined }
  const current = generation
  try {
    const value = await request<KnowledgeCapability>('GET', '/view/capabilities')
    if (disposed || current !== generation) return
    if (!value.canManage) clearPrivate()
    capability.value = value
    if (reset && value.canManage && value.available) await load()
  } catch (e: any) { if (current === generation) error.value = e.message }
}
async function load() {
  const seq = ++listSequence, current = generation; busy.value = true; error.value = ''
  try {
    const [entries, options] = await Promise.all([
      request<KnowledgePage<{ entry: KnowledgeEntry } & SearchLocation>>('POST', '/view/search', { keyword: keyword.value || null, kind: kind.value || null, categoryId: category.value || null, tagId: tag.value || null, page: page.value, pageSize: 20, deleted: trash.value }),
      request<KnowledgeLabel[]>('GET', '/view/labels'),
    ])
    if (disposed || seq !== listSequence || current !== generation) return
    rows.value = entries.records.map(hit => hit.entry); searchLocations.value = Object.fromEntries(entries.records.map(({ entry, excerpt, page, fromLine, toLine }) => [entry.id, { excerpt, page, fromLine, toLine }])); total.value = entries.total; labels.value = options
  } catch (e: any) { if (seq === listSequence && current === generation) error.value = e.message }
  finally { if (seq === listSequence && current === generation) busy.value = false }
}
async function open(id: string) {
  if (!discard()) return
  const seq = ++detailSequence, current = generation; error.value = ''; history.value = []; currentFile.value = undefined; fileSegments.value = []; fileSegmentsVisible.value = false; fileSegmentsSequence++
  try {
    const entry = await request<KnowledgeEntry>('GET', (trash.value ? '/manage' : '/view') + '/entries/' + id)
    if (disposed || seq !== detailSequence || current !== generation) return
    selected.value = entry; editor.value = structuredClone(entry.content); baseline.value = JSON.stringify(editor.value); pending = undefined; if (filePending?.entryId !== entry.id) filePending = undefined; notice.value = ''
    if (entry.fileVersion) void refreshFile(entry.id, entry.fileVersion, current, seq)
  } catch (e: any) { if (seq === detailSequence && current === generation) error.value = e.message }
}
function create() {
  if (!discard()) return
  detailSequence++; fileSegmentsSequence++; selected.value = undefined; history.value = []; currentFile.value = undefined; fileSegments.value = []; fileSegmentsVisible.value = false; pending = undefined; filePending = undefined
  editor.value = { kind: 'NOTE', title: '', body: '', sourceUrl: '', categoryId: null, tagIds: [] }; baseline.value = ''; notice.value = '新条目尚未保存'
}
async function refreshFile(entryId: string, version: string, current = generation, seq = detailSequence) {
  try {
    const info = await request<KnowledgeFileInfo>('GET', `/view/files/${entryId}/${version}`)
    if (!disposed && current === generation && seq === detailSequence && selected.value?.id === entryId && selected.value.fileVersion === version) currentFile.value = info
  } catch (failure: any) { if (current === generation && seq === detailSequence) error.value = failure.message }
}
async function showFileSegments(page = 1) {
  if (!selected.value?.fileVersion || currentFile.value?.state !== 'READY') return
  const entryId = selected.value.id, version = selected.value.fileVersion, current = generation, seq = detailSequence, segmentSeq = ++fileSegmentsSequence
  try {
    const result = await request<KnowledgePage<ExtractedSegment>>('GET', `/view/files/${entryId}/${version}/segments?page=${page}`)
    if (disposed || current !== generation || seq !== detailSequence || segmentSeq !== fileSegmentsSequence || selected.value?.fileVersion !== version) return
    fileSegments.value = result.records; fileSegmentsPage.value = page; fileSegmentsTotal.value = result.total; fileSegmentsVisible.value = true
  } catch (failure: any) { if (current === generation && seq === detailSequence) error.value = failure.message }
}
async function uploadFile(event: Event) {
  const input = event.target as HTMLInputElement, file = input.files?.[0]
  input.value = ''
  if (!file || !selected.value || !capability.value?.filesAvailable || dirty.value || uploadingFile.value) return
  const entryId = selected.value.id, revision = selected.value.revision, current = generation, seq = detailSequence
  const fingerprint = `${file.name}:${file.size}:${file.lastModified}`
  if (!filePending || filePending.entryId !== entryId || filePending.revision !== revision || filePending.fingerprint !== fingerprint)
    filePending = { entryId, revision, fingerprint, mutationId: crypto.randomUUID() }
  uploadingFile.value = true; error.value = ''; notice.value = '正在上传并验证私有文件…'
  try {
    const saved = await knowledgeUploadFile(entryId, revision, filePending.mutationId, file)
    if (disposed || current !== generation || seq !== detailSequence || selected.value?.id !== entryId) return
    const version = saved.version || (await request<KnowledgeEntry>('GET', `/view/entries/${entryId}`)).fileVersion
    if (disposed || current !== generation || seq !== detailSequence || selected.value?.id !== entryId) return
    if (version) {
      selected.value.revision = saved.ack.revision; selected.value.fileVersion = version
      currentFile.value = undefined; fileSegments.value = []; fileSegmentsVisible.value = false; fileSegmentsSequence++; await refreshFile(entryId, version, current, seq)
    }
    filePending = undefined; notice.value = '文件已私密保存，正文提取进度会单独显示。'; await load()
  } catch (failure: any) { if (current === generation && seq === detailSequence) { error.value = failure.message; notice.value = '文件上传未获确认，请核对条目版本和网络状态' } }
  finally { uploadingFile.value = false }
}
async function downloadFile() {
  if (!selected.value || !currentFile.value) return
  try { await knowledgeDownloadFile(selected.value.id, currentFile.value.version, currentFile.value.filename) }
  catch (failure: any) { error.value = failure.message }
}
async function changeFileJob(retry: boolean) {
  if (!selected.value || !currentFile.value || !capability.value?.canManage) return
  const entryId = selected.value.id, version = currentFile.value.version, current = generation, seq = detailSequence
  try {
    const result = await request<KnowledgeFileInfo>('POST', `/manage/entries/${entryId}/files/${version}/${retry ? 'retry' : 'cancel'}`)
    if (current === generation && seq === detailSequence && selected.value?.id === entryId) currentFile.value = result
  } catch (failure: any) { if (current === generation) error.value = failure.message }
}
async function save(): Promise<boolean> {
  if (!editor.value || saving.value) return false
  const snapshot = JSON.stringify(editor.value), current = generation, detail = detailSequence
  if (!pending || pending.snapshot !== snapshot) pending = { snapshot, mutationId: crypto.randomUUID() }
  const id = selected.value?.id || crypto.randomUUID()
  // Keep the same entry id and request id after an uncertain network response.
  if (!selected.value) selected.value = { id, revision: 0, content: JSON.parse(snapshot), fileVersion: null, updatedAt: '', deletedAt: null }
  saving.value = true; error.value = ''; notice.value = '正在等待服务器保存确认…'
  try {
    const ack = await request<KnowledgeAck>('POST', '/manage/entries', { mutationId: pending.mutationId, entryId: id, expectedRevision: selected.value.revision, content: JSON.parse(snapshot) })
    if (current !== generation || detail !== detailSequence || selected.value?.id !== id || disposed) return false
    selected.value.revision = ack.revision; baseline.value = snapshot; pending = undefined; notice.value = '已可靠保存 · 版本 ' + ack.revision; await load()
    return true
  } catch (e: any) { if (current === generation && detail === detailSequence && selected.value?.id === id) { error.value = e.status === 409 ? '服务器版本已变化。编辑仍保留，请先复制正文，再重新打开条目核对。' : e.message; notice.value = '尚未确认保存，请保留编辑' } return false }
  finally { if (current === generation) saving.value = false }
}
async function uploadBodyImages(files: File[], callback: (urls: string[]) => void) {
  if (!editor.value || uploadingImage.value) return
  if (!editor.value.title.trim()) { error.value = '请先填写标题，再插入图片'; return }
  if (!selected.value?.revision && !await save()) return
  const entryId = selected.value?.id
  if (!entryId) return
  uploadingImage.value = true; error.value = ''; notice.value = '正在私密上传图片…'
  try {
    const urls: string[] = []
    for (const file of files) {
      if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type) || file.size > 5 * 1024 * 1024) throw new Error('仅支持小于 5 MiB 的 JPG、PNG 或 WebP 图片')
      const fingerprint = `${entryId}:${file.name}:${file.size}:${file.lastModified}`
      const mutation = imageMutations.get(fingerprint) || crypto.randomUUID()
      imageMutations.set(fingerprint, mutation)
      const imageId = await knowledgeUploadImage(entryId, mutation, file)
      imageMutations.delete(fingerprint)
      urls.push(`knowledge-image:${imageId}`)
    }
    callback(urls)
    notice.value = '图片已私密上传，请保存正文以保留引用。'
  } catch (failure: any) { error.value = failure.message || '图片上传失败，请重试'; notice.value = '正文未插入失败图片' }
  finally { uploadingImage.value = false }
}
async function action(name: 'delete' | 'restore' | 'recover', sourceRevision?: number) {
  if (!selected.value || !discard() || !window.confirm(name === 'delete' ? '移入回收站？将立即停止对外显示。' : '确认恢复？将产生新的版本。')) return
  const current = generation, id = selected.value.id
  try {
    await request('POST', `/manage/entries/${id}/${name}`, { mutationId: crypto.randomUUID(), expectedRevision: selected.value.revision, sourceRevision })
    if (current !== generation) return
    selected.value = undefined; editor.value = undefined; history.value = []; await load()
  } catch (e: any) { if (current === generation) error.value = e.message }
}
async function versions() {
  if (!selected.value) return
  const id = selected.value.id, current = generation
  try {
    const result = await request<KnowledgePage<{ revision: number; content: KnowledgeContent; savedAt: string }>>('GET', `/manage/entries/${id}/history`)
    if (current === generation && id === selected.value?.id) history.value = result.records
  } catch (e: any) { if (current === generation) error.value = e.message }
}
async function manageLabel(existing?: KnowledgeLabel, remove = false) {
  const current = generation
  const name = existing && !remove ? window.prompt('修改名称', existing.name) : labelName.value
  if (!remove && !name?.trim()) return
  if (remove && !window.confirm('删除此分类或标签？仍有条目使用时会拒绝删除。')) return
  try {
    if (remove && existing) await request('DELETE', `/manage/labels/${existing.id}?revision=${existing.revision}`)
    else await request('POST', '/manage/labels', { id: existing?.id || crypto.randomUUID(), kind: existing?.kind || labelKind.value, name: name?.trim(), expectedRevision: existing?.revision || 0 })
    if (current === generation) { labelName.value = ''; await load() }
  } catch (e: any) { if (current === generation) error.value = e.message }
}
async function purgeExpired() {
  if (!capability.value?.canManage || !window.confirm('清理移入回收站超过30天且不含私有文件的条目？此操作不可撤销。')) return
  try {
    const result = await request<{ purged: number; pendingFileCleanup: number }>('POST', '/manage/trash/purge')
    notice.value = `已清理 ${result.purged} 条；${result.pendingFileCleanup} 条含私有文件的记录暂缓清理。`
    await load()
  } catch (failure: any) { error.value = failure.message }
}
function beforeUnload(e: BeforeUnloadEvent) { if (dirty.value || uploadingImage.value) { e.preventDefault(); e.returnValue = '' } }
const refresh = () => { void check() }
watch(() => authorization.value, () => { void check(true) }, { immediate: true, flush: 'sync' })
onBeforeRouteLeave(discard)
onMounted(() => { window.addEventListener('beforeunload', beforeUnload); window.addEventListener('knowledge-access-invalid', denied); window.addEventListener('focus', refresh); timer = setInterval(refresh, 30000) })
onBeforeUnmount(() => { disposed = true; clearPrivate(); clearInterval(timer); window.removeEventListener('beforeunload', beforeUnload); window.removeEventListener('knowledge-access-invalid', denied); window.removeEventListener('focus', refresh) })
</script>

<template>
  <main class="knowledge-workspace">
    <header><div><p class="eyebrow">个人资料与知识关联</p><h1>知识库</h1><p>让笔记留下来，让知识连接起来。</p></div><div v-if="capability?.canManage && capability.available" class="actions"><button @click="labelManager = !labelManager">分类与标签</button><button @click="create">新建条目</button></div></header>
    <p v-if="error" role="alert" class="error">{{ error }}</p>
    <section v-if="!capability?.canManage || !capability.available" class="empty"><h2>仅站主可以管理知识库</h2><p>{{ capability?.message || '正在确认后台管理权限…' }}</p><button @click="check(true)">重新检查</button></section>
    <template v-else>
      <details v-if="capability.canManage && labelManager" open><summary>分类与标签管理</summary><form class="filters" @submit.prevent="manageLabel()"><select v-model="labelKind" aria-label="分类或标签"><option value="CATEGORY">分类</option><option value="TAG">标签</option></select><input v-model="labelName" placeholder="名称，最多80字" maxlength="80" aria-label="新分类标签名称"><button>添加</button></form><div v-for="l in labels" :key="l.id" class="version"><span>{{ l.kind === 'TAG' ? '标签' : '分类' }} · {{ l.name }}</span><div><button @click="manageLabel(l)">改名</button><button @click="manageLabel(l, true)">删除</button></div></div></details>
      <form class="filters" @submit.prevent="page = 1; load()">
        <input v-model="keyword" aria-label="关键词" placeholder="搜索标题、正文、标签…" maxlength="100">
        <select v-model="kind" aria-label="条目类型"><option value="">全部类型</option><option v-for="(name, key) in kindNames" :key="key" :value="key">{{ name }}</option></select>
        <select v-model="category" aria-label="分类"><option value="">全部分类</option><option v-for="l in categoryOptions" :key="l.id" :value="l.id">{{ l.name }}</option></select>
        <select v-model="tag" aria-label="标签"><option value="">全部标签</option><option v-for="l in tagOptions" :key="l.id" :value="l.id">{{ l.name }}</option></select>
        <label v-if="capability.canManage"><input v-model="trash" type="checkbox">回收站</label><button :disabled="busy">{{ busy ? '读取中…' : '查找' }}</button><button v-if="capability.canManage && trash" type="button" @click="purgeExpired">清理30天前的回收站</button>
      </form>
      <div class="columns">
        <aside><p>{{ total }} 个条目</p><ul><li v-for="row in rows" :key="row.id"><button :class="{ active: selected?.id === row.id }" @click="open(row.id)"><small>{{ kindNames[row.content.kind] }}</small><strong>{{ row.content.title }}</strong><span v-if="keyword">{{ searchLocations[row.id]?.excerpt }}</span><small v-if="keyword && searchLocations[row.id]?.page">命中第 {{ searchLocations[row.id].page }} 页</small><small v-else-if="keyword && searchLocations[row.id]?.fromLine">命中第 {{ searchLocations[row.id].fromLine }}–{{ searchLocations[row.id].toLine }} 行</small><time>{{ new Date(row.updatedAt).toLocaleDateString('zh-CN') }}</time></button></li></ul><p v-if="!busy && !rows.length">当前范围没有条目。</p><nav><button :disabled="page <= 1 || busy" @click="page--; load()">上一页</button><span>{{ page }}</span><button :disabled="page * 20 >= total || busy" @click="page++; load()">下一页</button></nav></aside>
        <section class="detail">
          <template v-if="editor">
            <div v-if="keyword && selected && searchLocations[selected.id]?.excerpt" class="search-context"><strong>本次检索命中</strong><span v-if="searchLocations[selected.id].page"> · 文件第 {{ searchLocations[selected.id].page }} 页</span><span v-else-if="searchLocations[selected.id].fromLine"> · 第 {{ searchLocations[selected.id].fromLine }}–{{ searchLocations[selected.id].toLine }} 行</span><p>{{ searchLocations[selected.id].excerpt }}</p></div>
            <template v-if="capability.canManage && !selected?.deletedAt">
              <label>标题<input v-model="editor.title" maxlength="200" :disabled="saving"></label>
              <div class="metadata"><label>类型<select v-model="editor.kind" :disabled="!!selected?.revision || saving"><option v-for="(name, key) in kindNames" :key="key" :value="key">{{ name }}</option></select></label><label>分类<select v-model="editor.categoryId" :disabled="saving"><option :value="null">未分类</option><option v-for="l in categoryOptions" :key="l.id" :value="l.id">{{ l.name }}</option></select></label></div>
              <label>来源链接（仅记录）<input v-model="editor.sourceUrl" maxlength="2000" :disabled="saving" placeholder="https://…"></label>
              <fieldset class="tag-picker" :disabled="saving"><legend>标签（最多20个）</legend><p v-if="!tagOptions.length">暂无标签，请先在“分类与标签”中添加。</p><label v-for="l in tagOptions" :key="l.id"><input v-model="editor.tagIds" type="checkbox" :value="l.id" :disabled="saving || (!editor.tagIds.includes(l.id) && editor.tagIds.length >= 20)">{{ l.name }}</label></fieldset>
              <div class="markdown-field"><span class="field-title">Markdown 正文</span><MdEditor v-model="editor.body" :disabled="saving || uploadingImage" :preview="false" style="height:460px" @onUploadImg="uploadBodyImages" /></div>
              <div class="actions"><button :disabled="saving || !editor.title.trim()" @click="save">{{ saving ? '正在保存…' : '保存条目' }}</button><button @click="preview = !preview">{{ preview ? '收起预览' : '阅读预览' }}</button><button v-if="selected?.revision" @click="versions">历史版本</button><button v-if="selected?.revision" @click="action('delete')">移入回收站</button></div><p role="status">{{ notice }}{{ dirty ? ' · 有未确认编辑' : '' }}</p>
            </template>
            <template v-else><h2>{{ editor.title }}</h2><p v-if="editor.sourceUrl">来源：<a :href="editor.sourceUrl" rel="noopener noreferrer" target="_blank">{{ editor.sourceUrl }}</a></p><button v-if="capability.canManage && selected?.deletedAt" @click="action('restore')">恢复条目</button></template>
            <section v-if="editor.kind === 'DOCUMENT' && selected?.revision && !selected.deletedAt && capability.filesAvailable" class="knowledge-file">
              <h3>原始文件</h3>
              <p v-if="currentFile">{{ currentFile.filename }} · {{ (currentFile.sizeBytes / 1048576).toFixed(1) }} MiB · {{ currentFile.state === 'READY' ? '文字已提取，可检索' : currentFile.state === 'QUEUED' || currentFile.state === 'RUNNING' || currentFile.state === 'RETRY_WAIT' ? '原文件已保存，正在提取文字' : '原文件已保存，文字提取未完成' }}</p>
              <p v-if="currentFile?.errorCode">提取状态：{{ extractionErrors[currentFile.errorCode] || currentFile.errorCode }}</p>
              <div class="actions"><button v-if="currentFile" @click="downloadFile">下载原文件</button><label v-if="capability.canManage && !dirty && !uploadingFile">{{ currentFile ? '替换文件' : '上传文件' }}<input type="file" accept=".pdf,.md,.markdown,.txt" @change="uploadFile"></label><span v-if="uploadingFile">正在上传，请勿离开…</span><button v-if="currentFile && currentFile.state !== 'READY'" @click="refreshFile(selected!.id, currentFile!.version)">刷新提取状态</button><button v-if="capability.canManage && currentFile && ['FAILED','NEEDS_OCR','CANCELED'].includes(currentFile.state)" @click="changeFileJob(true)">重新提取</button><button v-if="capability.canManage && currentFile && ['QUEUED','RUNNING','RETRY_WAIT'].includes(currentFile.state)" @click="changeFileJob(false)">停止提取</button></div>
              <div v-if="currentFile?.state === 'READY'" class="actions"><button @click="fileSegmentsVisible ? fileSegmentsVisible = false : showFileSegments()">{{ fileSegmentsVisible ? '收起提取内容' : '查看提取内容与页码' }}</button></div>
              <div v-if="fileSegmentsVisible" class="extracted-content"><article v-for="segment in fileSegments" :key="segment.ordinal"><strong>{{ segment.page ? `第 ${segment.page} 页` : `第 ${segment.fromLine}–${segment.toLine} 行` }}</strong><pre>{{ segment.text }}</pre></article><nav><button :disabled="fileSegmentsPage <= 1" @click="showFileSegments(fileSegmentsPage - 1)">上一页</button><span>{{ fileSegmentsPage }} / {{ Math.max(1, Math.ceil(fileSegmentsTotal / 20)) }}</span><button :disabled="fileSegmentsPage * 20 >= fileSegmentsTotal" @click="showFileSegments(fileSegmentsPage + 1)">下一页</button></nav></div>
              <small>仅站主可上传；有知识库查看权限的读者可下载当前文件。历史版本不提供下载。</small>
            </section>
            <PrivateMarkdown v-if="preview || !capability.canManage" :body="editor.body" />
            <Connections v-if="selected && selected.revision > 0 && !selected.deletedAt" :key="selected.id" :entry="selected" :candidates="rows" :can-manage="capability.canManage" @open="open" />
            <details v-if="history.length" open><summary>历史版本（最近20版）</summary><div v-for="v in history" :key="v.revision" class="version"><strong>版本 {{ v.revision }} · {{ v.content.title }}</strong><button @click="action('recover', v.revision)">恢复此版本</button></div></details>
          </template>
          <div v-else class="empty"><h2>从一条笔记开始</h2><p>选择左侧资料阅读。公开文章仍留在博客，日常积累在这里整理。</p></div>
        </section>
      </div>
      <Draft v-if="capability.canManage" :selected="selected" :can-manage="capability.canManage" />
    </template>
  </main>
</template>

<style scoped>
.knowledge-workspace{max-width:1280px;margin:0 auto 50px;padding:24px;color:#24364b}header{display:flex;justify-content:space-between;align-items:center;gap:20px;border-bottom:1px solid #dfe5ed;padding-bottom:24px}h1{font-size:32px;margin:5px 0}p{line-height:1.8}.eyebrow,small,time{color:#64748b;font-size:12px}.filters,.metadata,.actions{display:flex;flex-wrap:wrap;gap:12px;align-items:center}.filters{padding:22px 0}.columns{display:grid;grid-template-columns:280px minmax(0,1fr);gap:28px}aside,.detail{border:1px solid #dfe5ed;border-radius:12px;padding:20px;background:#fff}ul{list-style:none;padding:0}li button{display:grid;text-align:left;width:100%;gap:7px;margin:8px 0;padding:14px}.active{border-color:#285de0;background:#edf3ff}.detail label{display:grid;gap:8px;margin-bottom:18px}.search-context{margin-bottom:20px;padding:14px;border-left:3px solid #285de0;background:#edf3ff;overflow-wrap:anywhere}.search-context p{margin:6px 0 0}.extracted-content{border-top:1px solid #dfe5ed;margin-top:16px;padding-top:12px}.extracted-content article{padding:12px 0;border-bottom:1px solid #e4e9ef}.extracted-content pre{white-space:pre-wrap;overflow-wrap:anywhere;max-height:420px;overflow:auto;font:inherit;line-height:1.7}input,select,textarea,button{font:inherit;border:1px solid #cdd6e2;border-radius:6px;padding:9px 12px;max-width:100%;box-sizing:border-box}button{cursor:pointer;background:transparent;color:inherit}button:disabled{opacity:.5;cursor:default}textarea{resize:vertical;width:100%;line-height:1.8}input[type=checkbox]{width:auto}.filters>input{flex:1;min-width:160px}.metadata>label{flex:1}nav{display:flex;align-items:center;justify-content:space-between}.empty{padding:35px 10px}.error{border-left:3px solid #b23d32;background:#fff1ee;padding:14px}.version{display:flex;justify-content:space-between;gap:10px;padding:12px 0;border-top:1px solid #e1e6ed}strong{overflow-wrap:anywhere}a{color:#285de0}@media(max-width:760px){.knowledge-workspace{padding:16px}.columns{grid-template-columns:1fr}header{align-items:flex-start}.detail,aside{padding:16px}.version{flex-wrap:wrap}}
.tag-picker{display:flex;flex-wrap:wrap;gap:8px 16px;margin:0 0 18px;border:1px solid #cdd6e2;border-radius:6px;padding:12px}.tag-picker legend{padding:0 4px}.tag-picker label{display:inline-flex;align-items:center;gap:6px;margin:0;cursor:pointer}.tag-picker input{margin:0}.tag-picker p{margin:0}
.markdown-field{margin-bottom:18px}.field-title{display:block;margin-bottom:8px}
.knowledge-workspace{box-sizing:border-box;width:100%;min-width:0}
.columns,aside,.detail{min-width:0}
.detail :deep(.md-editor){max-width:100%;min-width:0}
.detail :deep(.md-editor-preview){max-width:100%;min-width:0;overflow-wrap:anywhere}
.detail :deep(.md-editor-preview img){max-width:100%;height:auto}
.detail :deep(.md-editor-preview pre){max-width:100%;overflow-x:auto}
.detail :deep(.md-editor-preview table){display:block;max-width:100%;overflow-x:auto}
@media(max-width:760px){.columns{grid-template-columns:minmax(0,1fr)}}
</style>
