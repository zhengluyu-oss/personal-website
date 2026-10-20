<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { MdPreview } from 'md-editor-v3'
import Connections from './Connections.vue'
import Draft from './Draft.vue'
import 'md-editor-v3/lib/style.css'
import { sanitizePrivateDiary } from '@/utils/diary-preview'
import useUserStore from '@/store/modules/user'
import { registerPrivateWorkspaceGuard } from '@/utils/private-workspace'
import { knowledgeRequest as request, type KnowledgeAck, type KnowledgeCapability, type KnowledgeContent, type KnowledgeEntry, type KnowledgeLabel, type KnowledgePage } from '@/utils/knowledge-api'

const user = useUserStore()
const capability = ref<KnowledgeCapability>(), rows = ref<KnowledgeEntry[]>([]), labels = ref<KnowledgeLabel[]>([])
const selected = ref<KnowledgeEntry>(), editor = ref<KnowledgeContent>(), baseline = ref('')
const keyword = ref(''), kind = ref(''), category = ref(''), tag = ref(''), trash = ref(false), page = ref(1), total = ref(0)
const busy = ref(false), saving = ref(false), error = ref(''), notice = ref(''), preview = ref(true)
const history = ref<{ revision: number; content: KnowledgeContent; savedAt: string }[]>([])
const excerpts = ref<Record<string, string>>({})
const labelManager = ref(false), labelName = ref(''), labelKind = ref<'CATEGORY' | 'TAG'>('TAG')
const dirty = computed(() => !!editor.value && JSON.stringify(editor.value) !== baseline.value)
const categoryOptions = computed(() => labels.value.filter(l => l.kind === 'CATEGORY'))
const tagOptions = computed(() => labels.value.filter(l => l.kind === 'TAG'))
const kindNames = { NOTE: '笔记', DOCUMENT: '文件资料', CONCEPT: '概念' }
let generation = 0, listSequence = 0, detailSequence = 0, disposed = false
let pending: { snapshot: string; mutationId: string } | undefined
let timer: ReturnType<typeof setInterval> | undefined
const discard = () => !dirty.value || window.confirm('编辑尚未获得服务器保存确认，确定离开并放弃吗？')
const unregisterPrivateGuard = registerPrivateWorkspaceGuard(async () => discard())
function clearPrivate() { generation++; listSequence++; detailSequence++; rows.value = []; labels.value = []; excerpts.value = {}; selected.value = undefined; editor.value = undefined; history.value = []; total.value = 0; pending = undefined; baseline.value = ''; saving.value = false; busy.value = false }
function denied() { clearPrivate(); capability.value = undefined; error.value = '查看资格已变化，请重新确认权限。' }
async function check(reset = false) {
  if (reset) { clearPrivate(); capability.value = undefined }
  const current = generation
  try {
    const value = await request<KnowledgeCapability>('GET', '/view/capabilities')
    if (disposed || current !== generation) return
    if (!value.allowed || capability.value?.canManage && !value.canManage) clearPrivate()
    capability.value = value
    if (reset && value.allowed && value.available) await load()
  } catch (e: any) { if (current === generation) error.value = e.message }
}
async function load() {
  const seq = ++listSequence, current = generation; busy.value = true; error.value = ''
  try {
    const [entries, options] = await Promise.all([
      request<KnowledgePage<{ entry: KnowledgeEntry; excerpt: string }>>('POST', '/view/search', { keyword: keyword.value || null, kind: kind.value || null, categoryId: category.value || null, tagId: tag.value || null, page: page.value, pageSize: 20, deleted: trash.value }),
      request<KnowledgeLabel[]>('GET', '/view/labels'),
    ])
    if (disposed || seq !== listSequence || current !== generation) return
    rows.value = entries.records.map(hit => hit.entry); excerpts.value = Object.fromEntries(entries.records.map(hit => [hit.entry.id, hit.excerpt])); total.value = entries.total; labels.value = options
  } catch (e: any) { if (seq === listSequence && current === generation) error.value = e.message }
  finally { if (seq === listSequence && current === generation) busy.value = false }
}
async function open(id: string) {
  if (!discard()) return
  const seq = ++detailSequence, current = generation; error.value = ''; history.value = []
  try {
    const entry = await request<KnowledgeEntry>('GET', (trash.value ? '/manage' : '/view') + '/entries/' + id)
    if (disposed || seq !== detailSequence || current !== generation) return
    selected.value = entry; editor.value = structuredClone(entry.content); baseline.value = JSON.stringify(editor.value); pending = undefined; notice.value = ''
  } catch (e: any) { if (seq === detailSequence && current === generation) error.value = e.message }
}
function create() {
  if (!discard()) return
  detailSequence++; selected.value = undefined; history.value = []; pending = undefined
  editor.value = { kind: 'NOTE', title: '', body: '', sourceUrl: '', categoryId: null, tagIds: [] }; baseline.value = ''; notice.value = '新条目尚未保存'
}
async function save() {
  if (!editor.value || saving.value) return
  const snapshot = JSON.stringify(editor.value), current = generation
  if (!pending || pending.snapshot !== snapshot) pending = { snapshot, mutationId: crypto.randomUUID() }
  const id = selected.value?.id || crypto.randomUUID()
  // Keep the same entry id and request id after an uncertain network response.
  if (!selected.value) selected.value = { id, revision: 0, content: JSON.parse(snapshot), fileVersion: null, updatedAt: '', deletedAt: null }
  saving.value = true; error.value = ''; notice.value = '正在等待服务器保存确认…'
  try {
    const ack = await request<KnowledgeAck>('POST', '/manage/entries', { mutationId: pending.mutationId, entryId: id, expectedRevision: selected.value.revision, content: JSON.parse(snapshot) })
    if (current !== generation || disposed) return
    selected.value.revision = ack.revision; baseline.value = snapshot; pending = undefined; notice.value = '已可靠保存 · 版本 ' + ack.revision; await load()
  } catch (e: any) { if (current === generation) { error.value = e.status === 409 ? '服务器版本已变化。编辑仍保留，请先复制正文，再重新打开条目核对。' : e.message; notice.value = '尚未确认保存，请保留编辑' } }
  finally { if (current === generation) saving.value = false }
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
function beforeUnload(e: BeforeUnloadEvent) { if (dirty.value) { e.preventDefault(); e.returnValue = '' } }
const refresh = () => { void check() }
watch(() => user.token, () => { void check(true) }, { immediate: true, flush: 'sync' })
onBeforeRouteLeave(discard)
onMounted(() => { window.addEventListener('beforeunload', beforeUnload); window.addEventListener('knowledge-access-invalid', denied); window.addEventListener('focus', refresh); timer = setInterval(refresh, 30000) })
onBeforeUnmount(() => { disposed = true; unregisterPrivateGuard(); clearPrivate(); clearInterval(timer); window.removeEventListener('beforeunload', beforeUnload); window.removeEventListener('knowledge-access-invalid', denied); window.removeEventListener('focus', refresh) })
</script>

<template>
  <main class="knowledge-workspace">
    <header><div><p class="eyebrow">个人资料与知识关联</p><h1>知识库</h1><p>让笔记留下来，让知识连接起来。</p></div><div v-if="capability?.canManage && capability.available" class="actions"><button @click="labelManager = !labelManager">分类与标签</button><button @click="create">新建条目</button></div></header>
    <p v-if="error" role="alert" class="error">{{ error }}</p>
    <section v-if="!capability?.allowed || !capability.available" class="empty"><h2>笔记、资料、概念</h2><p>{{ capability?.message || '正在确认知识库访问权限…' }}</p><button @click="check(true)">重新检查</button></section>
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
        <aside><p>{{ total }} 个条目</p><ul><li v-for="row in rows" :key="row.id"><button :class="{ active: selected?.id === row.id }" @click="open(row.id)"><small>{{ kindNames[row.content.kind] }}</small><strong>{{ row.content.title }}</strong><span v-if="keyword">{{ excerpts[row.id] }}</span><time>{{ new Date(row.updatedAt).toLocaleDateString('zh-CN') }}</time></button></li></ul><p v-if="!busy && !rows.length">当前范围没有条目。</p><nav><button :disabled="page <= 1 || busy" @click="page--; load()">上一页</button><span>{{ page }}</span><button :disabled="page * 20 >= total || busy" @click="page++; load()">下一页</button></nav></aside>
        <section class="detail">
          <template v-if="editor">
            <template v-if="capability.canManage && !selected?.deletedAt">
              <label>标题<input v-model="editor.title" maxlength="200" :disabled="saving"></label>
              <div class="metadata"><label>类型<select v-model="editor.kind" :disabled="!!selected?.revision || saving"><option v-for="(name, key) in kindNames" :key="key" :value="key">{{ name }}</option></select></label><label>分类<select v-model="editor.categoryId" :disabled="saving"><option :value="null">未分类</option><option v-for="l in categoryOptions" :key="l.id" :value="l.id">{{ l.name }}</option></select></label></div>
              <label>来源链接（仅记录）<input v-model="editor.sourceUrl" maxlength="2000" :disabled="saving" placeholder="https://…"></label>
              <label>标签（最多20个）<select v-model="editor.tagIds" multiple :disabled="saving"><option v-for="l in tagOptions" :key="l.id" :value="l.id">{{ l.name }}</option></select></label>
              <label>Markdown 正文<textarea v-model="editor.body" :disabled="saving" rows="18" spellcheck="false" /></label>
              <div class="actions"><button :disabled="saving || !editor.title.trim()" @click="save">{{ saving ? '正在保存…' : '保存条目' }}</button><button @click="preview = !preview">{{ preview ? '收起预览' : '阅读预览' }}</button><button v-if="selected?.revision" @click="versions">历史版本</button><button v-if="selected?.revision" @click="action('delete')">移入回收站</button></div><p role="status">{{ notice }}{{ dirty ? ' · 有未确认编辑' : '' }}</p>
            </template>
            <template v-else><h2>{{ editor.title }}</h2><p v-if="editor.sourceUrl">来源：<a :href="editor.sourceUrl" rel="noopener noreferrer" target="_blank">{{ editor.sourceUrl }}</a></p><button v-if="capability.canManage && selected?.deletedAt" @click="action('restore')">恢复条目</button></template>
            <MdPreview v-if="preview || !capability.canManage" :model-value="editor.body" :sanitize="sanitizePrivateDiary" />
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
.knowledge-workspace{max-width:1280px;margin:90px auto 50px;padding:24px;color:var(--font-color,#24364b)}header{display:flex;justify-content:space-between;align-items:center;gap:20px;border-bottom:1px solid #dfe5ed;padding-bottom:24px}h1{font-size:32px;margin:5px 0}p{line-height:1.8}.eyebrow,small,time{color:#64748b;font-size:12px}.filters,.metadata,.actions{display:flex;flex-wrap:wrap;gap:12px;align-items:center}.filters{padding:22px 0}.columns{display:grid;grid-template-columns:280px minmax(0,1fr);gap:28px}aside,.detail{border:1px solid #dfe5ed;border-radius:12px;padding:20px;background:var(--card-bg,#fff)}ul{list-style:none;padding:0}li button{display:grid;text-align:left;width:100%;gap:7px;margin:8px 0;padding:14px}.active{border-color:#285de0;background:#edf3ff}.detail label{display:grid;gap:8px;margin-bottom:18px}input,select,textarea,button{font:inherit;border:1px solid #cdd6e2;border-radius:6px;padding:9px 12px;max-width:100%;box-sizing:border-box}button{cursor:pointer;background:transparent;color:inherit}button:disabled{opacity:.5;cursor:default}textarea{resize:vertical;width:100%;line-height:1.8}input[type=checkbox]{width:auto}.filters>input{flex:1;min-width:160px}.metadata>label{flex:1}nav{display:flex;align-items:center;justify-content:space-between}.empty{padding:35px 10px}.error{border-left:3px solid #b23d32;background:#fff1ee;padding:14px}.version{display:flex;justify-content:space-between;gap:10px;padding:12px 0;border-top:1px solid #e1e6ed}strong{overflow-wrap:anywhere}a{color:#285de0}@media(max-width:760px){.knowledge-workspace{padding:16px;margin-top:70px}.columns{grid-template-columns:1fr}header{align-items:flex-start}.detail,aside{padding:16px}.version{flex-wrap:wrap}}
</style>
