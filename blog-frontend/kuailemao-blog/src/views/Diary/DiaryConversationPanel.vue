<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import { diaryAiApi as api, type AiConversationDetail, type AiConversationSummary, type AiConversationTurn, type AiPreview, type AiRequest, type AiScope, type AiSource, type AiResult } from '@/utils/diary-ai-api'
import { diaryApi } from '@/utils/diary-api'
import DiaryAiResult from './DiaryAiResult.vue'
import DiaryValidationDiagnostic from './DiaryValidationDiagnostic.vue'

const props = defineProps<{ blocked: boolean; entryId: string; revision: number; choices: { id: string; title: string }[]; section: 'question' | 'review'; ready: boolean; canAdopt?: boolean }>()
const emit = defineEmits<{ (e: 'open-entry', id: string): void; (e: 'browse-entries'): void; (e: 'adopt-suggestion', value: { reportId: string; index: number; suggestion: Record<string, unknown> }): void }>()
const kind = computed(() => props.section === 'review' ? 'REVIEW' : 'QUESTION')
const conversations = ref<AiConversationSummary[]>([]), listPage = ref(1), listTotal = ref(0)
const selected = ref<AiConversationDetail>(), older = ref<AiConversationTurn[]>([])
const olderPage = ref(1)
const question = ref(''), followup = ref(''), error = ref(''), busy = ref(false), previewing = ref(false)
const mode = ref<'current' | 'range' | 'selected'>('current'), ids = ref(''), axis = ref<'created' | 'covered'>('covered'), from = ref(''), to = ref('')
const preview = ref<AiPreview>(), partial = ref(false)
const pending = ref<{ type: 'start' | 'followup'; requestId: string; request?: AiRequest; question?: string; conversationId?: string }>()
const source = ref<AiSource>(), sourceBefore = ref(''), sourceAfter = ref('')
let timer: ReturnType<typeof setTimeout> | undefined, disposed = false, epoch = 0

const turns = computed(() => [...older.value, ...(selected.value?.turns || [])])
const last = computed(() => turns.value[turns.value.length - 1])
const running = computed(() => !!last.value && ['QUEUED', 'RUNNING'].includes(last.value.job.state))
const stale = computed(() => turns.value.some(t => t.job.state === 'STALE'))
const canFollow = computed(() => !!selected.value?.canFollow && !running.value && !stale.value && !props.blocked && !pending.value)
const canStart = computed(() => !selected.value && !busy.value && !props.blocked && !running.value && !pending.value)
const displayTime = (time: string) => new Date(time).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', dateStyle: 'medium', timeStyle: 'short' })

function scope(review: boolean): AiScope | undefined {
  if (mode.value === 'current') {
    if (review && (!props.entryId || !props.revision)) throw new Error('请先在“写日记”中打开一篇已保存的日记')
    return review ? { entryIds: [props.entryId] } : undefined
  }
  if (mode.value === 'selected') {
    const values = ids.value.split(/[\s,，]+/).filter(Boolean)
    if (!values.length || values.length > 40) throw new Error('请选择1至40篇日记')
    return { entryIds: values }
  }
  if (!from.value || !to.value || from.value > to.value) throw new Error('请选择有效的起止日期')
  return { axis: axis.value, from: from.value, to: to.value }
}
function selectEntry(id: string) {
  const values = new Set(ids.value.split(' ').filter(Boolean))
  if (values.has(id)) values.delete(id); else if (values.size < 40) values.add(id)
  ids.value = [...values].join(' ')
}
async function run(action: () => Promise<void>) {
  if (busy.value || disposed) return
  const current = epoch; busy.value = true; error.value = ''
  try { await action() } catch (failure: any) { if (!disposed && current === epoch) error.value = failure.message || '请求结果未确认，请核对后再操作' }
  finally { if (!disposed && current === epoch) busy.value = false }
}
async function loadList() {
  const response = await api.conversations(kind.value, listPage.value)
  if (!disposed) { conversations.value = response.records; listTotal.value = response.total }
}
async function open(id: string) {
  const response = await api.conversation(id)
  if (disposed) return
  selected.value = response; older.value = []; olderPage.value = 1; source.value = undefined; followup.value = ''; poll()
}
async function loadOlder() {
  if (!selected.value || (older.value.length + selected.value.turns.length) >= selected.value.total) return
  const page = olderPage.value + 1
  const response = await api.conversation(selected.value.conversation.id, page)
  if (!disposed && selected.value?.conversation.id === response.conversation.id) {
    older.value = [...response.turns, ...older.value.filter(t => !response.turns.some(old => old.id === t.id))]
    olderPage.value = page
  }
}
function newConversation() {
  if (busy.value || pending.value) return
  selected.value = undefined; older.value = []; olderPage.value = 1; source.value = undefined; clearTimeout(timer)
}
function poll() {
  clearTimeout(timer)
  if (disposed || !selected.value || !running.value) return
  const id = selected.value.conversation.id
  timer = setTimeout(() => { void run(async () => {
    const response = await api.conversation(id)
    if (!disposed && selected.value?.conversation.id === id) selected.value = response
  }).then(() => { if (!disposed && selected.value?.conversation.id === id && running.value) poll() }) }, 2000)
}
async function makePreview() {
  if (busy.value || props.blocked) return
  previewing.value = true; preview.value = undefined
  try { await run(async () => {
    const value = scope(true)!, signature = JSON.stringify(value)
    const result = await api.preview(value)
    if (!disposed && signature === JSON.stringify(scope(true))) preview.value = result
  }) } finally { previewing.value = false }
}
async function start() {
  if (props.blocked || running.value) return
  await run(async () => {
    if (!pending.value) {
      if (kind.value === 'REVIEW' && !preview.value) throw new Error('请先预览完整范围')
      pending.value = { type: 'start', requestId: crypto.randomUUID(), request: {
        requestId: '', question: kind.value === 'QUESTION' ? question.value : undefined,
        scope: scope(kind.value === 'REVIEW'), snapshotDigest: kind.value === 'REVIEW' ? preview.value?.snapshotDigest : undefined,
        allowPartialIndex: partial.value } }
      pending.value.request!.requestId = pending.value.requestId
    }
    if (pending.value.type !== 'start' || !pending.value.request) throw new Error('请先确认上一条追问的状态')
    let response
    try { response = await api.startConversation(kind.value === 'REVIEW' ? 'reviews' : 'questions', pending.value.request) }
    catch (failure: any) { if (Number(failure.status) >= 400 && Number(failure.status) < 500) pending.value = undefined; throw failure }
    if (disposed) return
    pending.value = undefined; question.value = ''; preview.value = undefined
    await loadList(); await open(response.conversationId)
  })
}
async function askFollowup() {
  if (!selected.value || props.blocked || running.value || stale.value) return
  await run(async () => {
    if (!pending.value) pending.value = { type: 'followup', requestId: crypto.randomUUID(), question: followup.value, conversationId: selected.value!.conversation.id }
    if (pending.value.type !== 'followup') throw new Error('请先确认上一条请求的状态')
    const id = pending.value.conversationId!
    try { await api.followup(id, pending.value.requestId, pending.value.question || '') }
    catch (failure: any) { if (Number(failure.status) >= 400 && Number(failure.status) < 500) pending.value = undefined; throw failure }
    if (disposed) return
    pending.value = undefined; followup.value = ''
    await open(id); await loadList()
  })
}
async function cancel() {
  if (!last.value || !running.value) return
  await run(async () => { await api.cancel(last.value!.job.id); if (selected.value) await open(selected.value.conversation.id) })
}
async function remove() {
  if (!selected.value || pending.value) return
  try { await ElMessageBox.confirm('删除整段对话及其派生回答，原日记不会删除。', '删除对话') }
  catch { return }
  await run(async () => {
    await api.removeConversation(selected.value!.conversation.id)
    if (disposed) return
    selected.value = undefined; older.value = []; source.value = undefined; clearTimeout(timer); await loadList()
  })
}
async function locate(value: AiSource) {
  await run(async () => {
    const entry = await diaryApi.read(value.entryId)
    if (disposed) return
    const chars = Array.from(entry.body)
    if (entry.deletedAt || entry.revision !== value.revision || chars.slice(value.start, value.end).join('') !== value.text)
      throw new Error('日记原文已修改，请刷新对话核对引用')
    source.value = value; sourceBefore.value = chars.slice(Math.max(0, value.start - 120), value.start).join('')
    sourceAfter.value = chars.slice(value.end, value.end + 120).join('')
  })
}
function parsed(turn: AiConversationTurn): AiResult | undefined {
  try { return turn.report ? JSON.parse(turn.report.result) as AiResult : undefined } catch { return undefined }
}
watch(() => props.section, () => { epoch++; clearTimeout(timer); selected.value = undefined; older.value = []; olderPage.value = 1; source.value = undefined; pending.value = undefined; listPage.value = 1; void run(loadList) })
watch([mode, ids, axis, from, to, () => props.entryId, () => props.revision], () => { preview.value = undefined })
onMounted(() => void run(loadList))
onBeforeUnmount(() => { disposed = true; epoch++; clearTimeout(timer); selected.value = undefined; older.value = []; conversations.value = [] })
</script>

<template>
  <section class="conversation-workspace" :aria-label="kind === 'QUESTION' ? '问日记连续对话' : '做复盘连续对话'">
    <header class="conversation-heading"><div><h3>{{ kind === 'QUESTION' ? '和日记继续聊' : '围绕一次复盘继续聊' }}</h3><p>每次追问都会重新核对日记原文，并可能产生一次新的模型费用。</p></div><el-button :disabled="busy || !!pending" @click="newConversation">新建对话</el-button></header>
    <p v-if="error" class="conversation-error" role="alert">{{ error }}</p>
    <p v-if="pending" class="conversation-note" role="status">提交结果尚未确认。再次确认会沿用原请求，不会新建任务。<el-button :disabled="busy" @click="pending.type === 'start' ? start() : askFollowup()">确认原请求</el-button></p>
    <div class="conversation-layout">
      <aside class="conversation-list" aria-label="历史对话"><h4>对话记录</h4><p v-if="!conversations.length">还没有对话</p><button v-for="item in conversations" :key="item.id" type="button" class="conversation-item" :disabled="busy || !!pending" :aria-current="selected?.conversation.id === item.id ? 'true' : undefined" @click="run(() => open(item.id))"><strong>{{ item.firstQuestion || '日记复盘' }}</strong><small>{{ displayTime(item.updatedAt) }}</small></button><div class="conversation-pagination"><el-button :disabled="busy || !!pending || listPage <= 1" @click="listPage--; run(loadList)">上一页</el-button><span>{{ listPage }} / {{ Math.max(1, Math.ceil(listTotal / 20)) }}</span><el-button :disabled="busy || !!pending || listPage * 20 >= listTotal" @click="listPage++; run(loadList)">下一页</el-button></div></aside>
      <div class="conversation-main">
        <section v-if="!selected" class="conversation-start"><h4>{{ kind === 'QUESTION' ? '先问一个问题' : '先选择复盘资料' }}</h4><label>资料范围<select v-model="mode"><option value="current">{{ kind === 'QUESTION' ? '全部已保存日记' : '当前篇日记' }}</option><option value="range">按日期范围</option><option value="selected">指定多篇</option></select></label><template v-if="mode === 'range'"><label>时间依据<select v-model="axis"><option value="created">创建时间</option><option value="covered">内容起止日期</option></select></label><div class="date-pair"><label>开始<input v-model="from" type="date" /></label><label>结束<input v-model="to" type="date" /></label></div></template><div v-if="mode === 'selected'" class="entry-options"><label v-for="entry in choices" :key="entry.id"><input type="checkbox" :checked="ids.split(' ').includes(entry.id)" @change="selectEntry(entry.id)" />{{ entry.title || '无标题日记' }}</label><small>可以在“写日记”中筛选或翻页，再勾选当前页的日记。</small></div>
          <template v-if="kind === 'QUESTION'"><label>你的问题<textarea v-model="question" :disabled="busy || blocked" maxlength="1000" rows="4" placeholder="例如：我上个月坚持了哪些值得保留的做法？" /></label><label class="check"><input v-model="partial" type="checkbox" :disabled="busy" />索引未完成时允许仅用已索引资料</label><el-button type="primary" :disabled="!canStart || !question.trim() || (!ready && !partial)" @click="start">查找日记并回答</el-button></template>
          <template v-else><p v-if="mode === 'current' && !entryId">请先在“写日记”中打开已保存的篇目。<el-button @click="emit('browse-entries')">去选择</el-button></p><el-button :loading="previewing" :disabled="busy || blocked" @click="makePreview">预览完整范围</el-button><div v-if="preview" class="preview"><strong>{{ preview.entries }} 篇 · 约 {{ preview.characters }} 字符</strong><p>{{ preview.limitation }}</p><el-button type="primary" :disabled="!canStart" @click="start">确认范围并开始复盘</el-button></div></template></section>
        <section v-else class="conversation-thread" aria-label="对话消息"><header><div><h4>{{ selected.conversation.firstQuestion || '日记复盘' }}</h4><small>始于 {{ displayTime(selected.conversation.createdAt) }} · {{ selected.total }} 轮</small></div><el-button :disabled="busy || !!pending" @click="remove">删除对话</el-button></header><el-button v-if="turns.length < selected.total" :disabled="busy" @click="run(loadOlder)">加载更早的对话</el-button><article v-for="turn in turns" :key="turn.id" class="conversation-turn"><div class="user-message"><span>你的{{ turn.sequence === 1 && kind === 'REVIEW' ? '复盘范围' : '问题' }} · 第 {{ turn.sequence }} 轮</span><p>{{ turn.question || '按选定范围生成完整复盘' }}</p></div><div v-if="turn.report && parsed(turn)" class="assistant-message"><span>AI {{ turn.report.kind === 'REVIEW' ? '复盘' : '回答' }}</span><DiaryAiResult :report="turn.report" :result="parsed(turn)!" :question="turn.question || ''" :busy="busy" :blocked="blocked" :can-adopt="canAdopt" @locate="locate" @adopt="emit('adopt-suggestion', $event)" /></div><div v-else class="turn-status" :class="{ failed: !['QUEUED', 'RUNNING'].includes(turn.job.state) }"><strong>{{ ['QUEUED', 'RUNNING'].includes(turn.job.state) ? '正在处理' : '本轮未生成回答' }}</strong><p>{{ turn.job.message }}</p><p v-if="turn.job.possiblyCharged">本轮请求可能已产生费用；等待、取消或失败不会自动重发。</p><DiaryValidationDiagnostic v-if="turn.job.state === 'FAILED'" :id="turn.job.id" :kind="turn.job.kind === 'REVIEW' ? 'REVIEW' : 'QUESTION'" /><el-button v-if="turn.id === last?.id && running" :disabled="busy" @click="cancel">取消本轮</el-button></div></article><div v-if="source" class="conversation-source"><strong>引用原文 · {{ source.title }}</strong><pre>{{ sourceBefore }}<mark>{{ source.text }}</mark>{{ sourceAfter }}</pre><el-button @click="emit('open-entry', source.entryId)">打开这篇日记</el-button><el-button @click="source = undefined">关闭</el-button></div><p v-if="stale" class="conversation-error">日记资料已变化，旧回答已隐藏。请重新预览或新建对话。</p><form class="followup-form" @submit.prevent="askFollowup"><label for="diary-followup">继续追问</label><textarea id="diary-followup" v-model="followup" maxlength="1000" rows="3" :disabled="!canFollow || busy" placeholder="例如：你刚才提到的第二条建议，有哪段日记作为依据？" /><div><small>每次提交都是新任务，可能再次产生模型费用。</small><el-button type="primary" native-type="submit" :disabled="!canFollow || busy || !followup.trim()">发送追问</el-button></div></form></section>
      </div>
    </div>
  </section>
</template>

<style scoped>
.conversation-workspace{margin:24px 0 34px;min-width:0;color:var(--diary-text)}.conversation-heading{display:flex;justify-content:space-between;align-items:start;gap:16px;margin-bottom:20px}.conversation-heading h3{font-size:20px;margin:0 0 6px}.conversation-heading p,.conversation-start p{font-size:13px;color:var(--diary-muted);line-height:1.8;margin:0}.conversation-layout{display:grid;grid-template-columns:minmax(190px,240px) minmax(0,1fr);gap:18px;align-items:start}.conversation-list,.conversation-main{min-width:0;border:1px solid var(--diary-line);background:var(--diary-surface);border-radius:16px}.conversation-list{padding:18px}.conversation-list h4,.conversation-start h4{margin:0 0 16px;font-size:16px}.conversation-list>p{font-size:13px;color:var(--diary-muted)}.conversation-item{display:block;width:100%;padding:12px;text-align:left;background:transparent;color:inherit;border:0;border-bottom:1px solid var(--diary-line);cursor:pointer}.conversation-item[aria-current=true]{background:var(--diary-tint);border-radius:8px}.conversation-item strong{display:block;font-size:13px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.conversation-item small,.conversation-thread small{display:block;margin-top:5px;color:var(--diary-muted);font-size:11px}.conversation-pagination{display:flex;align-items:center;justify-content:space-between;gap:4px;margin-top:15px;font-size:12px}.conversation-pagination .el-button{padding:6px}.conversation-main{padding:22px;min-height:300px}.conversation-start{max-width:720px}.conversation-start label,.followup-form label{display:grid;gap:6px;margin:13px 0;font-size:13px}.conversation-start :is(select,input[type=date],textarea),.followup-form textarea{box-sizing:border-box;min-width:0;width:100%;padding:10px;border:1px solid var(--diary-line);border-radius:8px;background:var(--diary-surface);color:inherit;font:inherit;line-height:1.7}.conversation-start textarea,.followup-form textarea{resize:vertical}.conversation-start .check,.entry-options label{display:flex;align-items:start;gap:8px}.conversation-start .check input,.entry-options input{width:auto;margin-top:4px}.date-pair{display:grid;grid-template-columns:1fr 1fr;gap:12px}.entry-options{max-height:240px;overflow:auto;padding:8px;border:1px solid var(--diary-line);border-radius:8px}.entry-options small{font-size:12px;color:var(--diary-muted)}.preview{padding:16px;margin-top:16px;border:1px solid var(--diary-line);border-radius:10px}.preview p{margin:10px 0}.conversation-note,.conversation-error{padding:12px 14px;background:var(--diary-tint);border-left:3px solid var(--diary-accent);font-size:13px;line-height:1.7}.conversation-thread>header{display:flex;align-items:start;justify-content:space-between;gap:12px;border-bottom:1px solid var(--diary-line);padding-bottom:16px}.conversation-thread h4{font-size:18px;margin:0}.conversation-turn{margin-top:24px}.user-message{max-width:85%;margin-left:auto;padding:14px 18px;background:var(--diary-tint);border-radius:14px 14px 3px 14px}.user-message span,.assistant-message>span{font-size:11px;color:var(--diary-muted);font-weight:600}.user-message p{white-space:pre-wrap;overflow-wrap:anywhere;font-size:14px;line-height:1.8;margin:6px 0 0}.assistant-message{margin-top:14px;min-width:0}.assistant-message>span{display:block;margin-bottom:8px}.turn-status{margin:14px 0;padding:18px;border:1px solid var(--diary-line);border-radius:12px;font-size:13px}.turn-status.failed{border-left:3px solid var(--diary-accent)}.turn-status p{line-height:1.7}.conversation-source{padding:18px;margin-top:24px;border:1px solid var(--diary-line);border-radius:12px}.conversation-source pre{white-space:pre-wrap;overflow-wrap:anywhere;max-height:300px;overflow:auto;background:var(--diary-tint);padding:12px;line-height:1.8}.conversation-source mark{background:var(--brand-accent-soft)}.followup-form{border-top:1px solid var(--diary-line);padding-top:18px;margin-top:28px}.followup-form>div{display:flex;justify-content:space-between;align-items:center;gap:12px}.followup-form small{font-size:12px;color:var(--diary-muted)}:is(button,textarea,select,input):focus-visible{outline:2px solid var(--diary-accent);outline-offset:3px}@media(max-width:780px){.conversation-layout{grid-template-columns:minmax(0,1fr)}.conversation-list{padding:14px}.conversation-main{padding:18px}.conversation-item{display:inline-block;vertical-align:top;width:min(220px,70vw)}.conversation-list{overflow-x:auto;white-space:nowrap}.conversation-list h4,.conversation-list p,.conversation-pagination{white-space:normal}.conversation-item strong{white-space:nowrap}.user-message{max-width:100%}.date-pair{grid-template-columns:minmax(0,1fr)}.conversation-heading{flex-wrap:wrap}.followup-form>div{align-items:flex-start;flex-direction:column}}
</style>
