<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MdEditor, MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import useUserStore from '@/store/modules/user'
import { diaryApi, type DiaryAction, type DiaryFilter, type DiaryHistory } from '@/utils/diary-api'
import { DiarySaver, type DiaryEntry, type DiaryContent } from '@/utils/diary-save'
import { sanitizePrivateDiary } from '@/utils/diary-preview'
import { registerPrivateWorkspaceGuard } from '@/utils/private-workspace'

const user = useUserStore(), route = useRoute(), router = useRouter()
const gate = ref('checking'), gateMessage = ref('正在确认私密日记访问资格'), account = ref('')
const saver = new DiarySaver(diaryApi.save, () => { view.value = saver.snapshot() })
const view = ref(saver.snapshot()), metadata = ref<DiaryEntry>(), serverConflict = ref<DiaryEntry>()
const rows = ref<DiaryEntry[]>([]), total = ref<number | null>(null), listError = ref(''), listBusy = ref(false)
const filter = ref<DiaryFilter>({ axis: 'created', keyword: '', page: 1, pageSize: 20, from: '', to: '', deleted: false })
const today = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date())
const month = ref(today.slice(0, 7)), calendar = ref<{ date: string; count: number }[]>([]), calendarError = ref('')
const preview = ref(false), browsing = ref(false), historyOpen = ref(false), history = ref<DiaryHistory[]>([]), historyPage = ref(1), historyTotal = ref(0)
const actionBusy = ref(false)
const historyError = ref(''), leaveOpen = ref(false), leavingBusy = ref(false), actionError = ref('')
const pendingAction = ref<{ id: string; type: 'remove' | 'restore'; payload: DiaryAction }>()
let actionFlight: Promise<void> | undefined, leavePromise: Promise<boolean> | undefined, finishLeave: ((value: boolean) => void) | undefined
let identitySequence = 0, detailSequence = 0, listSequence = 0, calendarSequence = 0, historySequence = 0, disposed = false
const statusText = computed(() => actionBusy.value ? '正在确认操作结果' : pendingAction.value ? '操作结果未确认' : ({ clean: view.value.revision ? '已保存' : '尚未保存', dirty: '有修改未保存', saving: '保存中', failed: '保存未确认', conflict: '存在版本冲突', locked: '编辑已锁定' }[view.value.state]))
const editable = computed(() => gate.value === 'ready' && !metadata.value?.deletedAt && !pendingAction.value && !actionBusy.value && !['locked'].includes(view.value.state))
const calendarCells = computed(() => {
  if (!/^\d{4}-\d{2}$/.test(month.value)) return []
  const date = new Date(month.value + '-01T00:00:00Z'), days = new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth() + 1, 0)).getUTCDate()
  return [...Array(date.getUTCDay()).fill(null), ...Array.from({ length: days }, (_, index) => ({ day: index + 1, date: month.value + '-' + String(index + 1).padStart(2, '0') }))]
})
const countFor = (date: string) => calendar.value.find(item => item.date === date)?.count || 0
const formatTime = (time?: string) => time ? new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', dateStyle: 'medium', timeStyle: 'short' }).format(new Date(time)) : '首次保存后记录'
function edit(patch: Partial<DiaryContent>) { if (editable.value) saver.edit(patch) }
function resetPrivateView() { saver.clear(); view.value = saver.snapshot(); rows.value = []; total.value = null; metadata.value = undefined; serverConflict.value = undefined; history.value = []; historyOpen.value = false; calendar.value = []; pendingAction.value = undefined }
function download(content: Blob | string, name: string) {
  const url = URL.createObjectURL(typeof content === 'string' ? new Blob([content], { type: 'application/json;charset=UTF-8' }) : content)
  const anchor = document.createElement('a'); anchor.href = url; anchor.download = name; anchor.click(); setTimeout(() => URL.revokeObjectURL(url), 1000)
}
function downloadDraft() {
  if (gate.value !== 'ready') return
  download(JSON.stringify({ id: view.value.id, revision: view.value.revision, ...view.value.content }, null, 2), 'diary-draft-' + (view.value.id || 'unsaved') + '.json')
}
async function loadList() {
  if (gate.value !== 'ready') return
  const sequence = ++listSequence, identity = account.value; listBusy.value = true; listError.value = ''
  try {
    const result = await diaryApi.search({ ...filter.value, from: filter.value.from || null, to: filter.value.to || null })
    if (sequence !== listSequence || gate.value !== 'ready' || identity !== account.value) return
    rows.value = result.records; total.value = result.total
  } catch (error: any) { if (sequence === listSequence) listError.value = error.message || '列表加载失败' }
  finally { if (sequence === listSequence) listBusy.value = false }
}
async function loadCalendar() {
  if (gate.value !== 'ready' || !/^\d{4}-\d{2}$/.test(month.value)) return
  const sequence = ++calendarSequence, identity = account.value; calendarError.value = ''
  try { const result = await diaryApi.calendar(month.value); if (sequence === calendarSequence && gate.value === 'ready' && identity === account.value) calendar.value = result }
  catch { if (sequence === calendarSequence) calendarError.value = '日历加载失败，可重新选择月份重试' }
}
async function readEntry(id: string) {
  const sequence = ++detailSequence, identity = account.value
  try {
    const entry = await diaryApi.read(id)
    if (sequence !== detailSequence || gate.value !== 'ready' || identity !== account.value || route.query.entry !== id) return
    saver.attach(entry); metadata.value = entry; serverConflict.value = undefined; historyOpen.value = false; preview.value = false
  } catch (error: any) { if (sequence === detailSequence) ElMessage.error(error.message || '日记读取失败') }
}
function guardLeave(): Promise<boolean> {
  if (!saver.dirty && !pendingAction.value) return Promise.resolve(true)
  if (leavePromise) return leavePromise
  leaveOpen.value = true
  leavePromise = new Promise(resolve => { finishLeave = value => { leaveOpen.value = false; leavePromise = undefined; finishLeave = undefined; resolve(value) } })
  return leavePromise
}
async function answerLeave(action: 'save' | 'stay' | 'discard') {
  if (action === 'stay') { finishLeave?.(false); return }
  if (action === 'discard') { saver.clear(); pendingAction.value = undefined; metadata.value = undefined; finishLeave?.(true); return }
  leavingBusy.value = true
  try { if (pendingAction.value) await retryAction(); await saver.flush(); if (saver.dirty || pendingAction.value) throw new Error('仍有未确认内容'); finishLeave?.(true) }
  catch (error: any) { ElMessage.error(error.message || '保存未完成，请继续编辑或下载草稿') }
  finally { leavingBusy.value = false }
}
async function choose(id: string) { if (id === view.value.id || !await guardLeave()) return; browsing.value = false; await router.replace({ path: '/diary', query: { entry: id } }) }
async function createEntry() {
  if (!await guardLeave()) return
  const id = crypto.randomUUID(); saver.attach({ id }); metadata.value = undefined; preview.value = false; serverConflict.value = undefined; historyOpen.value = false; browsing.value = false
  await router.replace({ path: '/diary', query: { entry: id } })
}
async function save() { try { await saver.flush() } catch (error: any) { ElMessage.error(error.message || '保存未获确认') } }
async function compare() { try { serverConflict.value = await diaryApi.read(view.value.id) } catch (error: any) { ElMessage.error(error.message) } }
async function reloadServer() { if (!await guardLeave()) return; if (typeof route.query.entry === 'string') await readEntry(route.query.entry) }
async function useLocal() {
  if (!serverConflict.value) return
  try { await ElMessageBox.confirm('确认将当前本地内容作为新版本保存？服务器现有版本仍保留在历史中。', '处理冲突'); saver.resolveUsingLocal(serverConflict.value); serverConflict.value = undefined; await saver.flush() } catch (error: any) { if (error instanceof Error) ElMessage.error(error.message) }
}
async function loadHistory() {
  historyOpen.value = true; historyError.value = ''; const sequence = ++historySequence, id = view.value.id, identity = account.value
  try { const result = await diaryApi.history(id, historyPage.value); if (sequence === historySequence && id === view.value.id && gate.value === 'ready' && identity === account.value) { history.value = result.records; historyTotal.value = result.total } }
  catch (error: any) { if (sequence === historySequence) historyError.value = error.message || '历史加载失败' }
}
async function retryAction() {
  if (actionFlight) return actionFlight
  const action = pendingAction.value; if (!action) return
  const identity = account.value
  actionBusy.value = true
  actionFlight = (async () => {
    try {
      const ack = await (action.type === 'remove' ? diaryApi.remove(action.id, action.payload) : diaryApi.restore(action.id, action.payload))
      if (disposed || gate.value !== 'ready' || identity !== account.value || pendingAction.value !== action) return
      if (ack.entryId !== action.id || ack.mutationId !== action.payload.mutationId) throw new Error('操作确认不完整，请重试原操作')
      pendingAction.value = undefined; actionError.value = ''; const result = await diaryApi.read(action.id)
      if (disposed || identity !== account.value || gate.value !== 'ready' || view.value.id !== action.id) return
      saver.attach(result); metadata.value = result; await loadList(); await loadCalendar(); if (historyOpen.value) await loadHistory()
    } catch (error: any) {
      if (disposed || identity !== account.value) return
      actionError.value = error.status === 409 ? '版本已变化，操作未执行，请重新读取并比较' : '操作结果未确认，请保留此页并重试原操作'
      if ([400, 403, 404, 409].includes(Number(error.status))) pendingAction.value = undefined
      throw error
    } finally { actionFlight = undefined; actionBusy.value = false }
  })()
  return actionFlight
}
async function changeEntry(type: 'remove' | 'restore', sourceRevision?: number) {
  const id = view.value.id
  if (!await guardLeave()) return
  try {
    await ElMessageBox.confirm(type === 'remove' ? '移入已删除记录，正文与修改历史仍可恢复。' : '将所选历史内容及日期范围恢复为新的版本？', type === 'remove' ? '删除日记' : '恢复历史')
    actionBusy.value = true; const current = await diaryApi.read(id); saver.attach(current); metadata.value = current
    pendingAction.value = { id, type, payload: { mutationId: crypto.randomUUID(), expectedRevision: current.revision, ...(sourceRevision ? { sourceRevision } : {}) } }
    await retryAction()
  } catch (error: any) { if (error instanceof Error) ElMessage.error(error.message) }
  finally { actionBusy.value = false }
}
async function exportEntries(format: 'json' | 'markdown') {
  if (!filter.value.from || !filter.value.to) { ElMessage.warning('请先选择导出的起止日期，最多366天'); return }
  try { if (saver.dirty) await ElMessageBox.confirm('导出仅含数据库已保存内容，不包含当前未确认的修改。', '导出范围'); download(await diaryApi.export(filter.value, format), format === 'json' ? 'diary.json' : 'diary-markdown.zip') }
  catch (error: any) { if (error instanceof Error) ElMessage.error(error.message) }
}
watch(() => [user.token, user.sessionState, user.userInfo?.username], async () => {
  const sequence = ++identitySequence, identity = user.userInfo?.username, token = user.token
  if (user.sessionState !== 'ready' || !token || !identity) { saver.lock(); gate.value = 'locked'; gateMessage.value = '请在新标签页登录同一账号，再回到这里恢复编辑。未确认的草稿只保留在本页内存中。'; return }
  if (account.value && identity !== account.value) resetPrivateView()
  gate.value = 'checking'; saver.lock()
  try {
    const access = await diaryApi.capabilities()
    if (disposed || sequence !== identitySequence || token !== user.token) return
    if (!access.allowed) { resetPrivateView(); account.value = ''; gate.value = 'forbidden'; gateMessage.value = '当前账号无权访问私密日记'; return }
    account.value = identity
    if (!access.available) { gate.value = 'unavailable'; gateMessage.value = '日记未启用或暂时无法连接存储，公开博客不受影响'; return }
    gate.value = 'ready'
    if (saver.id) {
      let server: DiaryEntry | undefined
      try { server = await diaryApi.read(saver.id) } catch (error: any) { if (error.status !== 404 || saver.revision) throw error }
      if (sequence !== identitySequence || disposed) return
      if (!saver.dirty && server) { saver.attach(server); metadata.value = server } else saver.resume(server)
    } else {
      saver.attach({ id: '' })
      if (typeof route.query.entry === 'string' && /^[0-9a-f-]{36}$/i.test(route.query.entry)) await readEntry(route.query.entry)
    }
    await Promise.all([loadList(), loadCalendar()])
  } catch (error: any) { if (sequence === identitySequence && !disposed) { gate.value = 'unavailable'; gateMessage.value = error.message || '无法确认日记可用状态' } }
}, { immediate: true, flush: 'sync' })
watch(() => route.query.entry, id => { if (gate.value === 'ready' && typeof id === 'string' && id !== view.value.id) void readEntry(id) })
watch(() => view.value.savedAt, async savedAt => {
  if (!savedAt || gate.value !== 'ready') return
  const id = view.value.id, identity = account.value
  void loadList(); void loadCalendar()
  try { const result = await diaryApi.read(id); if (!disposed && gate.value === 'ready' && identity === account.value && id === view.value.id) metadata.value = result } catch { /* 保存ACK仍有效；元数据读取失败不覆盖草稿。 */ }
})
onBeforeRouteLeave(() => guardLeave())
onBeforeRouteUpdate((to, from) => to.query.entry !== from.query.entry && to.query.entry !== view.value.id ? guardLeave() : true)
const unregisterGuard = registerPrivateWorkspaceGuard(guardLeave)
const beforeUnload = (event: BeforeUnloadEvent) => { if (saver.dirty || pendingAction.value) { event.preventDefault(); event.returnValue = '' } }
const keydown = (event: KeyboardEvent) => { if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's' && gate.value === 'ready') { event.preventDefault(); void save() } }
const sessionInvalid = (event: Event) => {
  saver.lock(); gate.value = (event as CustomEvent<number>).detail === 403 ? 'forbidden' : 'locked'
  gateMessage.value = gate.value === 'forbidden' ? '当前账号无权访问私密日记' : '登录已失效，请在新标签页登录同一账号，再回到此页恢复草稿'
  if (gate.value === 'forbidden') resetPrivateView()
}
let restoreRobots = () => {}
onMounted(() => {
  window.addEventListener('beforeunload', beforeUnload); window.addEventListener('keydown', keydown); window.addEventListener('diary-session-invalid', sessionInvalid)
  const previous = document.head.querySelector<HTMLMetaElement>('meta[name="robots"]'), old = previous?.content
  const robots = previous || document.createElement('meta'); robots.name = 'robots'; robots.content = 'noindex,nofollow,noarchive'; if (!previous) document.head.append(robots)
  restoreRobots = () => { if (previous) previous.content = old || ''; else robots.remove() }
})
onBeforeUnmount(() => { disposed = true; identitySequence++; detailSequence++; listSequence++; calendarSequence++; historySequence++; finishLeave?.(false); unregisterGuard(); saver.dispose(); resetPrivateView(); window.removeEventListener('beforeunload', beforeUnload); window.removeEventListener('keydown', keydown); window.removeEventListener('diary-session-invalid', sessionInvalid); restoreRobots() })
</script>

<template>
  <Header />
  <main class="diary-workspace">
    <header class="diary-heading"><div><h1>我的日记</h1><p>记录经历，也留一点时间给自己。</p></div><el-button v-if="gate === 'ready'" type="primary" @click="createEntry">新建日记</el-button></header>
    <section v-if="gate !== 'ready'" class="diary-gate" role="status"><h2>{{ gate === 'checking' ? '正在确认访问资格' : gate === 'forbidden' ? '私密空间' : '暂时无法编辑' }}</h2><p>{{ gateMessage }}</p><a v-if="gate === 'locked'" href="/auth/login" target="_blank" rel="noopener">在新标签页登录</a><el-button @click="user.getInfo().catch(() => {})">重新确认登录与存储状态</el-button></section>
    <template v-else>
      <button class="browse-toggle" type="button" :aria-expanded="browsing" @click="browsing = !browsing">{{ browsing ? '收起查阅区域' : '查阅与筛选日记' }}</button>
      <div class="diary-columns">
        <aside class="diary-browser" :class="{ expanded: browsing }" aria-label="查阅日记">
          <h2>写作日历</h2><label>月份<input v-model="month" type="month" @change="loadCalendar" /></label><p class="hint">按北京时间的创建日期计数，一篇跨日记录只算一篇。</p><p v-if="calendarError" role="alert">{{ calendarError }}</p>
          <div class="calendar-grid"><span v-for="day in ['日','一','二','三','四','五','六']" :key="day" class="weekday">{{ day }}</span><template v-for="(cell, index) in calendarCells" :key="index"><span v-if="!cell" /><button v-else type="button" :title="cell.date + ' 创建 ' + countFor(cell.date) + ' 篇'" @click="filter.axis = 'created'; filter.from = cell.date; filter.to = cell.date; filter.page = 1; loadList()">{{ cell.day }}<small v-if="countFor(cell.date)">{{ countFor(cell.date) }}篇</small></button></template></div>
          <h2>筛选记录</h2><form class="diary-filters" @submit.prevent="filter.page = 1; loadList()"><label>搜索标题与正文<input v-model="filter.keyword" type="search" placeholder="输入关键词" /></label><label>时间依据<select v-model="filter.axis"><option value="created">写下这篇的时间</option><option value="covered">内容记录的时间范围</option></select></label><label>开始日期<input v-model="filter.from" type="date" /></label><label>结束日期<input v-model="filter.to" type="date" /></label><p v-if="filter.axis === 'covered'" class="hint">按时间范围重叠匹配；未填写记录日期的篇目不会匹配。</p><label>每页篇数<input v-model.number="filter.pageSize" type="number" min="1" max="100" /></label><label class="checkbox"><input v-model="filter.deleted" type="checkbox" />查看已删除记录</label><el-button native-type="submit">应用筛选</el-button></form>
          <h2>日记列表 <small v-if="total !== null">{{ total }}篇</small></h2><p v-if="listBusy" role="status">加载中…</p><div v-if="listError" role="alert"><p>{{ listError }}</p><el-button @click="loadList">重试</el-button></div><p v-else-if="!listBusy && total === 0" class="hint">此筛选下还没有记录。</p>
          <ul class="entry-list" :aria-busy="listBusy"><li v-for="entry in rows" :key="entry.id"><button type="button" :class="{ selected: view.id === entry.id }" @click="choose(entry.id)"><strong>{{ entry.title || '无标题日记' }}</strong><span>{{ formatTime(entry.updatedAt) }}</span><small v-if="entry.coveredFrom">记录 {{ entry.coveredFrom }} 至 {{ entry.coveredTo }}</small></button></li></ul>
          <div class="pagination"><el-button :disabled="(filter.page || 1) <= 1" @click="filter.page = (filter.page || 1) - 1; loadList()">上一页</el-button><span>{{ filter.page }} / {{ Math.max(1, Math.ceil((total || 0) / (filter.pageSize || 20))) }}</span><el-button :disabled="(filter.page || 1) * (filter.pageSize || 20) >= (total || 0)" @click="filter.page = (filter.page || 1) + 1; loadList()">下一页</el-button></div>
          <details class="export"><summary>导出已保存记录</summary><p class="hint">先选择起止日期。每次最多366天、1000篇、20MiB，不包含本地未保存修改。</p><el-button @click="exportEntries('json')">JSON</el-button><el-button @click="exportEntries('markdown')">Markdown</el-button></details>
        </aside>
        <section v-if="!view.id" class="diary-empty"><h2>从一篇记录开始</h2><p>可以写今天，也可以一次记下过去十几天。记录日期可以留空。</p><el-button type="primary" @click="createEntry">新建第一篇日记</el-button></section>
        <section v-else class="diary-editor" aria-label="日记编辑器">
          <div class="save-toolbar"><div aria-live="polite"><strong>{{ statusText }}</strong><small v-if="view.savedAt">最近确认 {{ formatTime(view.savedAt) }}</small></div><div><el-button :disabled="!editable || !view.dirty || view.state === 'conflict'" @click="save">{{ view.state === 'saving' ? '保存中…' : '保存 / 重试' }}</el-button><el-button @click="downloadDraft">下载本地草稿</el-button></div></div>
          <p v-if="view.message" class="save-message" role="alert">{{ view.message }}</p><div v-if="actionError" role="alert"><p>{{ actionError }}</p><el-button v-if="pendingAction" @click="retryAction().catch(() => {})">重试原操作</el-button><el-button v-else @click="reloadServer">重新读取</el-button></div>
          <div v-if="view.state === 'conflict'" class="conflict"><h2>保留本地内容，先比较服务器版本</h2><el-button @click="compare">查看服务器内容</el-button><el-button @click="reloadServer">重新载入服务器版本</el-button><template v-if="serverConflict"><h3>服务器版本 {{ serverConflict.revision }}{{ serverConflict.deletedAt ? '（已删除）' : '' }}</h3><p>{{ serverConflict.title }}</p><pre>{{ serverConflict.body }}</pre><p>本地未保存内容仍保留在下面的编辑器中。</p><el-button :disabled="!!serverConflict.deletedAt" @click="useLocal">确认以本地内容保存新版本</el-button></template></div>
          <p v-if="metadata?.deletedAt" role="status">这篇日记已删除，可在修改历史中选择版本恢复。</p>
          <label class="title-field">标题（可选）<input :value="view.content.title" :disabled="!editable" placeholder="给这段经历一个标题" @input="edit({ title: ($event.target as HTMLInputElement).value })" /></label>
          <div class="coverage"><label>记录开始日期（可选）<input type="date" :value="view.content.coveredFrom || ''" :disabled="!editable" @input="edit({ coveredFrom: ($event.target as HTMLInputElement).value || null })" /></label><label>记录结束日期（可选）<input type="date" :value="view.content.coveredTo || ''" :disabled="!editable" @input="edit({ coveredTo: ($event.target as HTMLInputElement).value || null })" /></label></div>
          <p class="hint">创建：{{ formatTime(metadata?.createdAt) }} · 修改：{{ formatTime(metadata?.updatedAt) }}（北京时间）；与内容所记录的日期分开保存。</p>
          <div class="editor-tabs"><el-button :type="!preview ? 'primary' : 'default'" @click="preview = false">写日记</el-button><el-button :type="preview ? 'primary' : 'default'" @click="preview = true">安全预览</el-button><span>{{ Array.from(view.content.body).length }} / 100000 字</span></div>
          <MdPreview v-if="preview" :model-value="view.content.body" editor-id="private-diary-preview" :sanitize="sanitizePrivateDiary" no-mermaid no-katex no-highlight />
          <MdEditor v-else :model-value="view.content.body" :disabled="!editable" :preview="false" :toolbars="['bold','italic','title','quote','unorderedList','orderedList','code','link','revoke','next']" :footers="[]" :sanitize="sanitizePrivateDiary" no-prettier no-upload-img no-mermaid no-katex no-highlight @update:model-value="value => edit({ body: value })" />
          <p class="hint">正文仅保留在本页内存与私密数据库中；不会离线缓存。首版不加载外部图片或嵌入内容。</p>
          <div class="entry-actions"><el-button :disabled="!view.revision || !!pendingAction" @click="historyPage = 1; loadHistory()">修改历史</el-button><el-button v-if="!metadata?.deletedAt" type="danger" plain :disabled="!view.revision || !!pendingAction" @click="changeEntry('remove')">删除日记</el-button></div>
          <section v-if="historyOpen" class="history"><h2>修改历史</h2><p v-if="historyError" role="alert">{{ historyError }}</p><article v-for="item in history" :key="item.revision"><h3>版本 {{ item.revision }} · {{ formatTime(item.savedAt) }}</h3><p>{{ item.title || '无标题' }} · {{ item.action }} · {{ item.coveredFrom || '未填日期' }}{{ item.coveredTo ? ' 至 ' + item.coveredTo : '' }}</p><details><summary>查看此版本正文</summary><pre>{{ item.body }}</pre></details><el-button @click="changeEntry('restore', item.revision)">恢复为新版本</el-button></article><div class="pagination"><el-button :disabled="historyPage <= 1" @click="historyPage--; loadHistory()">上一页</el-button><span>{{ historyPage }}</span><el-button :disabled="historyPage * 20 >= historyTotal" @click="historyPage++; loadHistory()">下一页</el-button></div></section>
        </section>
      </div>
    </template>
    <el-dialog v-model="leaveOpen" title="还有内容或操作未获确认" :close-on-click-modal="false" :close-on-press-escape="false" :show-close="false" width="min(92vw, 480px)"><p>保存成功后再离开，或继续留在本页。放弃只清除本页待保存内容，已经发出的请求仍可能完成。</p><template #footer><el-button @click="answerLeave('stay')">继续编辑</el-button><el-button type="danger" plain :disabled="leavingBusy" @click="answerLeave('discard')">放弃并继续</el-button><el-button type="primary" :loading="leavingBusy" :disabled="gate !== 'ready' || view.state === 'conflict'" @click="answerLeave('save')">保存后继续</el-button></template></el-dialog>
  </main>
</template>

<style scoped>
.diary-workspace{max-width:1440px;margin:0 auto;padding:100px 28px 70px;color:var(--el-text-color-primary,#172c47)}
.diary-workspace :is(button,input,select,[contenteditable]){scroll-margin-top:90px}
.diary-heading,.save-toolbar,.pagination,.entry-actions,.editor-tabs{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap}.diary-heading{margin-bottom:28px}.diary-heading h1{font-size:30px;margin:0 0 8px}.diary-heading p{margin:0;color:var(--el-text-color-secondary,#65748b)}
.diary-columns{display:grid;grid-template-columns:280px minmax(0,1fr);gap:30px}.diary-browser{border-right:1px solid var(--el-border-color,#dce3ed);padding-right:22px}.diary-browser h2{font-size:17px;margin:25px 0 12px}.diary-browser h2:first-child{margin-top:0}.diary-browser h2 small{font-weight:400;color:var(--el-text-color-secondary)}
label{display:grid;gap:7px;font-size:14px}input,select{width:100%;min-width:0;box-sizing:border-box;border:1px solid var(--el-border-color,#cdd6e3);border-radius:6px;padding:9px 10px;background:var(--el-bg-color,#fff);color:inherit;font:inherit}input:focus,select:focus{outline:2px solid var(--el-color-primary,#164bce);outline-offset:2px}.diary-filters{display:grid;gap:12px}.checkbox{display:flex;align-items:center}.checkbox input{width:auto}.hint{font-size:12px;line-height:1.75;color:var(--el-text-color-secondary,#65748b)}
.calendar-grid{display:grid;grid-template-columns:repeat(7,minmax(0,1fr));gap:3px}.calendar-grid button{border:0;border-radius:4px;background:var(--el-fill-color-light,#f0f4fa);color:inherit;min-height:38px;cursor:pointer}.calendar-grid small{display:block;font-size:9px;color:var(--el-color-primary)}.weekday{font-size:12px;text-align:center;padding:6px 0}.entry-list{padding:0;margin:12px 0;list-style:none}.entry-list li+li{margin-top:5px}.entry-list button{border:0;border-left:3px solid transparent;border-radius:4px;background:transparent;color:inherit;text-align:left;padding:12px 10px;width:100%;cursor:pointer}.entry-list button.selected{border-color:var(--el-color-primary);background:var(--el-fill-color-light)}.entry-list button:hover{background:var(--el-fill-color-light)}.entry-list strong{display:block;overflow-wrap:anywhere;font-size:14px}.entry-list span,.entry-list small{display:block;font-size:11px;line-height:1.8;color:var(--el-text-color-secondary)}.pagination{font-size:12px;gap:6px;margin:16px 0}.export{margin-top:25px;font-size:14px}summary{cursor:pointer}
.diary-editor{min-width:0}.save-toolbar{padding-bottom:16px;border-bottom:1px solid var(--el-border-color);margin-bottom:22px}.save-toolbar small{display:block;margin-top:5px;color:var(--el-text-color-secondary);font-size:12px}.save-message,.conflict{background:var(--el-color-warning-light-9,#fff5df);padding:14px;border-radius:6px;line-height:1.7}.title-field input{font-size:24px;padding:12px 0;border:0;border-bottom:1px solid var(--el-border-color);border-radius:0;background:transparent}.coverage{display:grid;grid-template-columns:1fr 1fr;gap:15px;margin:20px 0 10px}.editor-tabs{justify-content:flex-start;margin:22px 0 14px}.editor-tabs span{margin-left:auto;font-size:12px;color:var(--el-text-color-secondary)}.diary-editor :deep(.md-editor){height:58dvh;min-height:340px}.entry-actions{justify-content:flex-start;margin-top:24px}.history article{padding:16px 0;border-top:1px solid var(--el-border-color)}.history h3{font-size:15px}.history p{font-size:13px}.history pre,.conflict pre{white-space:pre-wrap;overflow-wrap:anywhere;max-height:320px;overflow:auto}.diary-empty,.diary-gate{padding:60px 30px;background:var(--el-bg-color,#fff);border-radius:8px;line-height:1.9}.diary-gate a{display:inline-block;margin-right:16px}.browse-toggle{display:none}
@media(max-width:760px){.diary-workspace{padding:90px 16px 50px}.diary-heading h1{font-size:25px}.diary-columns{grid-template-columns:minmax(0,1fr);gap:20px}.diary-browser{display:none;border:0;padding:0}.diary-browser.expanded{display:block}.browse-toggle{display:block;margin:0 0 20px;background:var(--el-bg-color,#fff);border:1px solid var(--el-border-color);border-radius:6px;padding:10px 14px;color:inherit}.coverage{grid-template-columns:1fr}.save-toolbar>div:last-child{display:flex;gap:6px;flex-wrap:wrap}.save-toolbar .el-button+.el-button{margin-left:0}.diary-empty,.diary-gate{padding:28px 18px}.diary-editor :deep(.md-editor){min-height:360px}.title-field input{font-size:21px}}
</style>
