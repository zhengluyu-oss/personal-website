<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import { ChatDotRound, DocumentChecked, Setting } from '@element-plus/icons-vue'
import DiaryAiResult from './DiaryAiResult.vue'
import DiaryConversationPanel from './DiaryConversationPanel.vue'
import DiaryValidationDiagnostic from './DiaryValidationDiagnostic.vue'
import { diaryAiApi as api, type AiIndex, type AiJob, type AiPreview, type AiReport, type AiReportSummary, type AiRequest, type AiResult, type AiScope, type AiSource } from '@/utils/diary-ai-api'
import { diaryApi } from '@/utils/diary-api'
const props = withDefaults(defineProps<{ blocked: boolean; entryId: string; revision: number; choices: { id: string; title: string }[]; canAdopt?: boolean; section?: 'question' | 'review'; active?: boolean }>(), { section: 'question', active: true })
const emit = defineEmits<{ (e: 'open-entry', id: string): void; (e: 'browse-entries'): void; (e: 'adopt-suggestion', value: { reportId: string; index: number; suggestion: Record<string, unknown> }): void; (e: 'section-change', section: 'question' | 'review'): void }>()
const capability = ref<{ enabled: boolean; available: boolean; message: string }>(), index = ref<AiIndex>()
const consent = ref(false), busy = ref(false), previewing = ref(false), error = ref(''), question = ref(''), partial = ref(false)
const responseZone = ref<HTMLElement>(), submittedQuestion = ref(''), reportQuestion = ref('')
// Old reports do not carry their question. Keep only known request/report associations in memory.
const questionsByReport = new Map<string, string>()
const mode = ref<'current' | 'range' | 'selected'>('current'), ids = ref(''), axis = ref<'created' | 'covered'>('covered'), from = ref(''), to = ref('')
const preview = ref<AiPreview>(), job = ref<AiJob>(), report = ref<AiReport>(), result = ref<AiResult>()
const reports = ref<AiReportSummary[]>([]), page = ref(1), total = ref(0)
const pending = ref<{ kind: 'questions' | 'reviews'; body: AiRequest }>(), indexPending = ref<{ start: boolean; id: string; retry: boolean }>()
const source = ref<AiSource>(), sourceBefore = ref(''), sourceAfter = ref(''), sourceValid = ref(false)
let disposed = false, timer: ReturnType<typeof setTimeout> | undefined, epoch = 0
const running = computed(() => !!job.value && ['QUEUED', 'RUNNING'].includes(job.value.state))
const canSubmit = computed(() => !!capability.value?.available && !props.blocked && !busy.value && !running.value && !pending.value)
const reviewing = computed(() => props.section === 'review')
const visibleReport = computed(() => report.value && (reviewing.value ? report.value.kind === 'REVIEW' : report.value.kind === 'QUESTION'))
const visibleJob = computed(() => job.value && job.value.state !== 'SUCCEEDED' && (reviewing.value ? job.value.kind === 'REVIEW' : job.value.kind === 'QUESTION'))
const ready = computed(() => !!index.value && index.value.coverage.total === index.value.coverage.indexed && !index.value.coverage.failed)
const scopeLabel = computed(() => mode.value === 'range' ? '按日期范围' : mode.value === 'selected' ? `已选 ${ids.value.split(' ').filter(Boolean).length} 篇` : reviewing.value ? '当前篇日记' : '全部已保存日记')
const taskTitle = computed(() => {
  const noun = job.value?.kind === 'REVIEW' ? '复盘' : '回答'
  return ({ QUEUED: `正在等待生成${noun}`, RUNNING: `正在生成${noun}`, SUCCEEDED: `${noun}已生成`, FAILED: `这次${noun}没有完成`, UNCERTAIN: '请求结果暂未确认', STALE: '资料已变化，请重新生成', CANCELED: '本次任务已取消' } as Record<string, string>)[job.value?.state || ''] || '正在确认任务状态'
})
const displayTime = (time: string) => new Date(time).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', dateStyle: 'medium', timeStyle: 'short' })
function scope(review: boolean): AiScope | undefined {
  if (mode.value === 'current') { if (review && (!props.entryId || !props.revision)) throw new Error('请先保存或选中一篇日记'); return review ? { entryIds: [props.entryId] } : undefined }
  if (mode.value === 'selected') {
    const entries = ids.value.split(/[\s,，]+/).filter(Boolean)
    if (!entries.length || entries.length > 40 || entries.some(id => !/^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i.test(id))) throw new Error('请选择1至40篇日记')
    return { entryIds: entries }
  }
  if (!from.value || !to.value || from.value > to.value) throw new Error('请选择有效的起止日期')
  return { axis: axis.value, from: from.value, to: to.value }
}
function selectEntry(id: string) { const selected = new Set(ids.value.split(' ').filter(Boolean)); if (selected.has(id)) selected.delete(id); else if (selected.size < 40) selected.add(id); ids.value = [...selected].join(' ') }
async function run(action: () => Promise<void>) {
  if (busy.value || disposed) return
  const e = epoch; busy.value = true; error.value = ''
  try { await action() } catch (failure: any) { if (!disposed && e === epoch) error.value = failure.message || '请求未获确认，请重试' }
  finally { if (!disposed && e === epoch) busy.value = false }
}
async function refresh() {
  const [i, r] = await Promise.all([api.index(), api.reports(page.value)])
  if (disposed) return; index.value = i; reports.value = r.records; total.value = r.total
}
async function initialize() {
  const c = await api.capabilities(); if (disposed) return; capability.value = c
  if (c.available) await refresh()
}
async function changeIndex(start: boolean, retry = false) {
  if (start && !consent.value) { error.value = '请先阅读并确认云端数据流说明'; return }
  if (!indexPending.value) indexPending.value = { start, id: crypto.randomUUID(), retry }
  await run(async () => { const p = indexPending.value!; await api.indexAction(p.start, p.id, p.retry); if (disposed) return; indexPending.value = undefined; await refresh() })
}
async function previewReview() {
  if (!canSubmit.value) return
  previewing.value = true; preview.value = undefined
  try {
    await run(async () => {
      const s = scope(true)!, signature = JSON.stringify(s)
      const p = await api.preview(s)
      if (disposed) return
      if (signature !== JSON.stringify(scope(true)) || props.blocked) throw new Error('复盘范围已变化，请重新预览完整范围')
      preview.value = p
    })
  } finally { if (!disposed) previewing.value = false }
}
async function submit(kind: 'questions' | 'reviews') {
  if (props.blocked || running.value || busy.value) return
  await run(async () => {
    if (!pending.value) {
      if (kind === 'reviews' && !preview.value) throw new Error('请先预览完整范围')
      pending.value = { kind, body: { requestId: crypto.randomUUID(), question: kind === 'questions' ? question.value : undefined, scope: scope(kind === 'reviews'), snapshotDigest: kind === 'reviews' ? preview.value?.snapshotDigest : undefined, allowPartialIndex: partial.value } }
    }
    const p = pending.value, j = await api.submit(p.kind, p.body)
    if (disposed) return; submittedQuestion.value = p.kind === 'questions' ? p.body.question || '' : ''; pending.value = undefined; job.value = j; report.value = undefined; result.value = undefined; source.value = undefined; poll()
  })
}
function poll() {
  clearTimeout(timer); if (disposed || !running.value) return
  timer = setTimeout(() => { timer = undefined; void run(async () => {
    const j = await api.job(job.value!.id); if (disposed) return; job.value = j
    if (j.reportId && j.state === 'SUCCEEDED') await showReport(j.reportId)
    if (!running.value) await refresh()
  }).then(() => { if (!disposed && running.value) poll() }) }, 2000)
}
async function cancel() { await run(async () => { const j = await api.cancel(job.value!.id); if (!disposed) { job.value = j; clearTimeout(timer) } }) }
async function showReport(id: string) {
  report.value = undefined; result.value = undefined; source.value = undefined
  const r = await api.report(id); if (disposed) return
  const parsed = JSON.parse(r.result) as AiResult
  if (job.value?.reportId === id && r.kind === 'QUESTION' && submittedQuestion.value) questionsByReport.set(id, submittedQuestion.value)
  reportQuestion.value = questionsByReport.get(id) || ''; report.value = r; result.value = parsed
  await nextTick()
  if (props.active && visibleReport.value && responseZone.value) responseZone.value.scrollIntoView({ block: 'start', behavior: 'instant' })
}
async function openHistory(id: string) {
  await showReport(id)
  if (disposed || !report.value) return
  emit('section-change', report.value.kind === 'QUESTION' ? 'question' : 'review')
  await nextTick()
  if (!disposed && props.active) responseZone.value?.scrollIntoView({ block: 'start', behavior: 'instant' })
}
async function removeReport(id: string) {
  await run(async () => { await ElMessageBox.confirm('只删除这份分析报告，日记和修改历史会保留。', '删除报告'); await api.remove(id); if (disposed) return; if (report.value?.id === id) { report.value = undefined; result.value = undefined; source.value = undefined }; await refresh() })
}
async function locate(s: AiSource) {
  source.value = undefined; sourceValid.value = false
  await run(async () => {
    const e = await diaryApi.read(s.entryId); if (disposed) return
    const chars = Array.from(e.body)
    if (e.deletedAt || e.revision !== s.revision || chars.slice(s.start, s.end).join('') !== s.text) { report.value = undefined; result.value = undefined; throw new Error('来源已修改或删除，请重新生成报告') }
    source.value = s; sourceBefore.value = chars.slice(Math.max(0, s.start - 120), s.start).join(''); sourceAfter.value = chars.slice(s.end, s.end + 120).join(''); sourceValid.value = true
  })
}
watch([mode, ids, axis, from, to, () => props.entryId, () => props.revision, () => props.blocked], () => { preview.value = undefined; source.value = undefined })
onMounted(() => void run(initialize))
onBeforeUnmount(() => { disposed = true; epoch++; clearTimeout(timer); capability.value = undefined; index.value = undefined; preview.value = undefined; question.value = ''; submittedQuestion.value = ''; reportQuestion.value = ''; questionsByReport.clear(); pending.value = undefined; reports.value = []; report.value = undefined; result.value = undefined; source.value = undefined; sourceBefore.value = ''; sourceAfter.value = '' })
</script>

<template>
  <section class="diary-ai" aria-label="私密日记助手">
    <header class="ai-heading">
      <div><h2>{{ reviewing ? '做复盘' : '问日记' }}</h2><p>{{ reviewing ? '选一段经历，回看观察、变化和下一步。' : '从你写过的经历里，找到问题的线索。' }}</p></div>
      <span class="index-status">{{ !index ? '正在检查资料' : ready ? `${index.coverage.indexed} 篇日记可供检索` : `${index.coverage.indexed} / ${index.coverage.total} 篇已索引` }}</span>
    </header>
    <div v-if="error && (!reviewing || !capability?.available)" class="ai-error" role="alert">{{ error }}</div>
    <div v-if="!capability?.available" class="ai-unavailable" role="status"><h3>{{ busy ? '正在准备日记助手' : '日记助手暂不可用' }}</h3><p>{{ capability?.message || '正在确认 AI 功能状态…' }}</p><el-button :loading="busy" @click="run(initialize)">重新检查</el-button></div>
    <template v-else>
      <details class="ai-settings">
        <summary><Setting aria-hidden="true" />索引与数据设置<span>{{ ready ? '已就绪' : '查看处理状态' }}</span></summary>
        <div class="settings-body">
          <details class="data-policy"><summary>数据会怎样处理？</summary><p>建立索引会将已保存的日记片段发送给站点配置的云端 Embedding 服务；问答会发送你的问题和检索出的片段，复盘会发送预览范围内的完整正文。向量和报告保存在私密 PostgreSQL，公开文章与聊天记录不参与。</p><p>首次建立后会增量处理已保存的新版本，暂停可停止后续索引；已经发出的请求无法撤回，仍可能计费。模型供应商的数据保留政策由站点配置确认。报告只是辅助阅读，引用正确也不保证解释正确。</p><p>{{ capability.message }}</p></details>
          <div class="ai-index"><strong>索引 {{ index?.coverage.indexed ?? '待确认' }} / {{ index?.coverage.total ?? '待确认' }} 篇</strong><p>异常 {{ index?.coverage.failed ?? '待确认' }} 篇 · {{ index?.syncing ? '自动增量中' : '已暂停' }}</p><label class="check-label"><input v-model="consent" type="checkbox" />我已了解云端数据流，允许建立索引</label><div class="ai-actions"><el-button :disabled="busy || !consent" @click="changeIndex(true)">建立 / 继续索引</el-button><el-button :disabled="busy" @click="changeIndex(false)">暂停索引</el-button><el-button :disabled="busy || !consent" @click="changeIndex(true, true)">明确重试异常篇目</el-button><el-button :disabled="busy" @click="run(refresh)">刷新状态</el-button><el-button v-if="indexPending" :disabled="busy" @click="changeIndex(indexPending.start, indexPending.retry)">重试原索引操作</el-button></div><small>{{ index?.message }}</small></div>
        </div>
      </details>
      <p v-if="blocked" class="ai-error">请先在“写日记”中保存并确认当前修改，再发起问答或复盘。这里不会分析未保存草稿。</p>
      <DiaryConversationPanel :blocked="blocked" :entry-id="entryId" :revision="revision" :choices="choices" :section="section" :ready="ready" :can-adopt="canAdopt" @open-entry="emit('open-entry', $event)" @browse-entries="emit('browse-entries')" @adopt-suggestion="emit('adopt-suggestion', $event)" />
      <details class="ai-legacy"><summary>查看旧版单次问答与复盘</summary>
      <div class="ai-layout">
        <section class="ai-composer" :aria-label="reviewing ? '复盘范围与提交' : '提出问题'">
          <div class="composer-title"><DocumentChecked v-if="reviewing" aria-hidden="true" /><ChatDotRound v-else aria-hidden="true" /><h3>{{ reviewing ? '选择复盘资料' : '你想了解什么？' }}</h3></div>
          <details class="scope-details" :open="reviewing">
            <summary>资料范围 <span>{{ scopeLabel }}</span></summary>
            <fieldset :disabled="busy || running || !!pending"><legend class="sr-only">资料范围</legend>
              <label>选择方式<select v-model="mode"><option value="current">{{ reviewing ? '当前篇日记' : '全部已保存日记' }}</option><option value="range">按日期范围</option><option value="selected">指定多篇</option></select></label>
              <div v-if="mode === 'selected'"><p>已选 {{ ids.split(' ').filter(Boolean).length }} / 40 篇</p><small>下方列出查阅区域当前页的日记。可以先筛选或翻页，再继续勾选；已有选择会保留。</small><label v-for="entry in choices" :key="entry.id" class="check-label"><input type="checkbox" :data-entry-id="entry.id" :checked="ids.split(' ').includes(entry.id)" @change="selectEntry(entry.id)" />{{ entry.title || '无标题日记' }}</label><el-button v-if="ids" @click="ids = ''">清空选择</el-button></div>
              <template v-if="mode === 'range'"><label>时间依据<select v-model="axis"><option value="created">创建时间（北京时间）</option><option value="covered">内容记录的起止日期</option></select></label><div class="ai-dates"><label>开始<input v-model="from" type="date" /></label><label>结束<input v-model="to" type="date" /></label></div><small>内容日期按区间重叠匹配，未填日期的篇目不会匹配；跨日篇目不会当作逐日记录。</small></template>
            </fieldset>
          </details>
          <div v-if="!reviewing" class="ai-question">
            <label for="diary-ai-question">问题<textarea id="diary-ai-question" v-model="question" :disabled="busy || running || !!pending" maxlength="1000" rows="5" placeholder="例如：哪些做法让我更容易开始阅读？" /></label>
            <div class="question-length">{{ question.length }} / 1000</div>
            <details class="question-options"><summary>检索选项</summary><label class="check-label"><input v-model="partial" type="checkbox" :disabled="busy || running || !!pending" />索引不完整时，允许仅依据已索引资料回答</label></details>
            <el-button type="primary" :disabled="!canSubmit || !question.trim()" @click="submit('questions')">查找依据并回答</el-button>
            <p class="composer-help">提交后，可在“AI 回答”中阅读结果并核对引用。</p>
          </div>
          <div v-else class="review-composer">
            <p class="composer-help">使用所选范围的完整正文，最多40篇及24,000字预算，超限请缩小范围。预览只读取资料，不调用模型。</p>
            <div v-if="mode === 'current'" class="composer-help">
              <p v-if="entryId && revision">当前篇：{{ choices.find(entry => entry.id === entryId)?.title || '已选日记' }} · 版本 {{ revision }}</p>
              <p v-else>尚未选中已保存的日记。“当前篇”是你在“写日记”中打开的篇目，也可改用“指定多篇”。</p>
              <el-button :disabled="busy || running || !!pending" @click="emit('browse-entries')">选择已保存日记</el-button>
            </div>
            <el-button :disabled="!canSubmit" :loading="previewing" @click="previewReview">{{ previewing ? '正在读取完整范围…' : '预览完整范围' }}</el-button>
            <p v-if="error" class="review-preview-status" role="alert">{{ error }}</p>
            <div v-if="preview" class="ai-preview"><h4>本次将分析</h4><strong>{{ preview.entries }} 篇 · 输入预算约 {{ preview.characters }} 字符</strong><details><summary>查看所选篇目</summary><ul><li v-for="entry in preview.records" :key="entry.id">{{ entry.title || '无标题日记' }}</li></ul></details><p>{{ preview.limitation }}</p><el-button type="primary" :disabled="!canSubmit" @click="submit('reviews')">确认范围并手动生成</el-button></div>
          </div>
          <div v-if="pending" class="ai-pending" role="status"><h4>请求结果待确认</h4><p>提交结果未确认，重试会使用原请求标识。先确认这次请求，再更换内容。</p><el-button :disabled="busy || blocked" @click="submit(pending.kind)">重试原请求</el-button></div>
        </section>
        <div ref="responseZone" class="response-zone" aria-label="回答与复盘阅读区">
          <div v-if="visibleJob && (!visibleReport || running)" class="ai-job" :class="{ 'job-problem': !running && job?.state !== 'SUCCEEDED' }" role="status" aria-live="polite">
            <span class="result-label">处理进度</span><h3>{{ taskTitle }}</h3><p>{{ job?.message }}</p>
            <div v-if="running" class="response-skeleton" aria-hidden="true"><span /><span /><span /></div>
            <p v-if="job?.possiblyCharged">请求可能已产生费用，等待结果或取消不会退回额度。</p>
            <p v-if="job && ['FAILED', 'UNCERTAIN'].includes(job.state)" class="retry-guidance">核对原因后，可修改问题或资料范围，再点击提交。再次提交会创建新任务，可能再次产生费用；本页不会自动重发。</p>
            <DiaryValidationDiagnostic v-if="active && job?.state === 'FAILED'" :key="job.id" :id="job.id" :kind="job.kind === 'REVIEW' ? 'REVIEW' : 'QUESTION'" />
            <div class="ai-actions"><el-button v-if="running" :disabled="busy" @click="cancel">取消任务</el-button><el-button v-if="running" :disabled="busy" @click="poll">继续查询状态</el-button></div>
          </div>
          <DiaryAiResult v-if="visibleReport && report && result" :report="report" :result="result" :question="reportQuestion" :busy="busy" :blocked="blocked" :can-adopt="canAdopt" @locate="locate" @adopt="emit('adopt-suggestion', $event)" />
          <section v-else-if="!visibleJob" class="response-empty"><DocumentChecked v-if="reviewing" aria-hidden="true" /><ChatDotRound v-else aria-hidden="true" /><h3>{{ reviewing ? '复盘会出现在这里' : '答案会出现在这里' }}</h3><p>{{ reviewing ? '先预览资料范围，确认后再生成。观察、推测和建议会分开展示。' : '提出一个具体问题。AI 会先查找相关日记，再给出带有原文依据的回答。' }}</p><p v-if="running" class="other-task">另一个{{ job?.kind === 'REVIEW' ? '复盘' : '问答' }}任务正在处理。<button type="button" @click="emit('section-change', job?.kind === 'REVIEW' ? 'review' : 'question')">查看处理进度</button></p></section>
          <section v-if="visibleReport && source && sourceValid" class="ai-source" aria-label="引用原文"><div class="source-heading"><div><span class="result-label">引用原文 · {{ source.sourceId }}</span><h3>{{ source.title || '无标题日记' }}</h3></div><el-button @click="source = undefined">关闭原文</el-button></div><p class="composer-help">高亮内容是本次回答引用的片段，已核对版本 {{ source.revision }}。</p><pre>{{ sourceBefore }}<mark>{{ source.text }}</mark>{{ sourceAfter }}</pre><el-button :disabled="blocked" @click="emit('open-entry', source.entryId)">在编辑区打开这篇</el-button></section>
        </div>
      </div>
      <details class="ai-reports"><summary>历史问答与复盘 <span>{{ total }} 份记录</span></summary><p class="composer-help">历史结果单独查看，不会与本次问题混在一起。</p><p v-if="!reports.length">还没有报告。保存日记后，可以手动发起分析。</p><div v-for="item in reports" :key="item.id" class="ai-report-row"><div><strong>{{ item.kind === 'QUESTION' ? '问答记录' : '日记复盘' }}</strong><span>{{ displayTime(item.createdAt) }} · {{ item.entries }} 篇日记</span><small v-if="item.state === 'STALE'">来源已变化，请重新生成</small></div><div class="ai-actions"><el-button :disabled="busy || running || item.state !== 'SUCCEEDED'" @click="run(() => openHistory(item.id))">查看</el-button><el-button :disabled="busy || running" @click="removeReport(item.id)">删除</el-button></div></div><div class="ai-actions report-pagination"><el-button :disabled="busy || page <= 1" @click="page--; run(refresh)">上一页</el-button><span>{{ page }} / {{ Math.max(1, Math.ceil(total / 20)) }}</span><el-button :disabled="busy || page * 20 >= total" @click="page++; run(refresh)">下一页</el-button></div></details>
      </details>
    </template>
  </section>
</template>

<style scoped>
.diary-ai{color:var(--diary-text,var(--el-text-color-primary));min-width:0}.ai-heading{display:flex;align-items:center;justify-content:space-between;gap:20px;margin:6px 0 22px}.ai-heading h2{font-size:25px;line-height:1.5;margin:0 0 5px}.ai-heading p{margin:0;color:var(--diary-muted);font-size:14px}.index-status{font-size:12px;white-space:nowrap;color:var(--diary-muted)}
.ai-legacy{margin-top:30px;border-top:1px solid var(--diary-line);padding-top:16px}.ai-legacy>summary{font-size:13px;color:var(--diary-muted);cursor:pointer;margin-bottom:18px}
.ai-layout{display:grid;grid-template-columns:minmax(260px,330px) minmax(0,1fr);gap:28px;align-items:start}.ai-composer{min-width:0;background:var(--diary-surface);padding:24px;border:1px solid var(--diary-line);border-radius:16px}.composer-title{display:flex;gap:9px;align-items:center;margin-bottom:20px}.composer-title svg{width:20px;color:var(--diary-accent)}.composer-title h3{font-size:17px;margin:0}.ai-composer label{display:grid;gap:8px;margin:15px 0 0;font-size:13px;font-weight:500}.ai-composer :is(input:not([type=checkbox]),select,textarea){width:100%;min-width:0;box-sizing:border-box;background:var(--diary-surface);border:1px solid var(--diary-line);border-radius:8px;padding:11px;color:inherit;font:inherit;font-size:14px}.ai-composer textarea{resize:vertical;line-height:1.8;min-height:140px}.ai-composer input::placeholder,.ai-composer textarea::placeholder{color:var(--diary-muted)}.ai-composer .check-label,.settings-body .check-label{display:flex;align-items:flex-start;gap:8px;font-size:13px;line-height:1.75;font-weight:400}.check-label input{width:auto;flex:none;margin-top:5px;accent-color:var(--diary-accent)}.question-length{font-size:11px;text-align:right;margin-top:5px;color:var(--diary-muted)}.composer-help{font-size:12px;color:var(--diary-muted);line-height:1.8;margin:12px 0}.ai-question>.el-button{width:100%;margin-top:18px}.ai-composer small{display:block;font-size:12px;color:var(--diary-muted);line-height:1.7;margin-top:8px}fieldset{border:0;min-width:0;padding:0;margin:0}.scope-details{border-bottom:1px solid var(--diary-line);padding-bottom:16px}.scope-details>summary{font-size:13px;cursor:pointer;line-height:1.8}.scope-details>summary>span{display:block;color:var(--diary-muted);padding-left:14px;font-size:12px}.question-options{font-size:12px;margin-top:18px;color:var(--diary-muted)}summary{cursor:pointer}.ai-dates{display:grid;grid-template-columns:minmax(0,1fr);gap:0}.ai-preview{margin-top:20px;padding-top:18px;border-top:1px solid var(--diary-line);font-size:13px;line-height:1.8}.ai-preview h4{margin:0 0 8px;font-size:14px}.ai-preview>p{color:var(--diary-muted)}.ai-preview details{margin:12px 0}.ai-preview .el-button{width:100%;margin-top:8px}.review-composer>.el-button{width:100%}
.response-zone{min-width:0;scroll-margin-top:110px}.response-empty{min-height:360px;display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center;border:1px dashed var(--diary-line);border-radius:16px;padding:48px 32px;box-sizing:border-box;background:var(--diary-surface)}.response-empty>svg{width:32px;height:32px;color:var(--diary-muted);margin-bottom:16px}.response-empty h3{font-size:20px;margin:0 0 12px}.response-empty p{font-size:14px;line-height:1.9;max-width:360px;color:var(--diary-muted);margin:0}.other-task{margin-top:18px!important}.other-task button{color:var(--diary-accent);background:none;border:0;cursor:pointer;font:inherit;text-decoration:underline}.ai-job{padding:28px 30px;border:1px solid var(--diary-line);border-radius:16px;background:var(--diary-surface);margin-bottom:20px}.result-label{font-size:12px;font-weight:600;color:var(--diary-muted)}.ai-job h3{font-size:20px;margin:10px 0}.ai-job p{font-size:14px;line-height:1.8;white-space:pre-wrap}.response-skeleton{display:grid;gap:12px;margin:24px 0}.response-skeleton span{height:10px;background:var(--diary-tint);border-radius:4px}.response-skeleton span:last-child{width:60%}.ai-actions{display:flex;flex-wrap:wrap;align-items:center;gap:8px}.ai-actions .el-button+.el-button{margin-left:0}.ai-error,.ai-pending{margin:18px 0;padding:14px 18px;background:var(--diary-tint);border-left:3px solid var(--diary-accent);font-size:14px;line-height:1.8}.ai-pending h4{margin:0}.ai-pending p{font-size:13px}.ai-settings{margin-bottom:24px;border:1px solid var(--diary-line);border-radius:10px;background:var(--diary-surface);font-size:13px}.ai-settings>summary{padding:13px 18px;display:flex;align-items:center;gap:8px;list-style:none}.ai-settings>summary::-webkit-details-marker{display:none}.ai-settings>summary svg{width:16px}.ai-settings>summary>span{margin-left:auto;color:var(--diary-muted);font-size:12px}.settings-body{padding:0 20px 20px;line-height:1.8}.data-policy{padding:10px 0;font-size:13px}.data-policy p{white-space:pre-wrap;overflow-wrap:anywhere;color:var(--diary-muted)}.ai-index{border-top:1px solid var(--diary-line);padding-top:18px;margin-top:10px}.ai-index p{font-size:12px;color:var(--diary-muted)}.ai-index .ai-actions{margin:14px 0}.ai-index small{font-size:12px;color:var(--diary-muted)}.ai-reports{margin-top:30px;border-top:1px solid var(--diary-line);padding-top:22px}.ai-reports>summary{font-size:15px;font-weight:600}.ai-reports>summary span{font-size:12px;font-weight:400;color:var(--diary-muted);margin-left:12px}.ai-report-row{display:flex;justify-content:space-between;gap:16px;padding:18px 0;border-bottom:1px solid var(--diary-line)}.ai-report-row strong{display:block;font-size:14px}.ai-report-row span,.ai-report-row small{display:block;font-size:12px;color:var(--diary-muted);margin-top:5px}.report-pagination{margin-top:20px;font-size:13px}.ai-source{margin-top:20px;padding:24px;border:1px solid var(--diary-line);border-radius:16px;background:var(--diary-surface)}.source-heading{display:flex;align-items:center;justify-content:space-between;gap:14px}.source-heading h3{font-size:17px;margin:7px 0}.ai-source pre{max-height:360px;overflow:auto;white-space:pre-wrap;overflow-wrap:anywhere;font:inherit;font-size:14px;line-height:1.9;background:var(--diary-tint);padding:16px;border-radius:8px}.ai-source mark{color:var(--diary-text);background:var(--brand-accent-soft)}.ai-unavailable{padding:36px;background:var(--diary-surface);border-radius:16px;border:1px solid var(--diary-line)}.ai-unavailable h3{font-size:20px}.ai-unavailable p{line-height:1.8;font-size:14px}.sr-only{position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0}:is(button,summary,input,textarea,select):focus-visible{outline:2px solid var(--diary-accent);outline-offset:4px}
@media(max-width:1000px){.ai-layout{grid-template-columns:minmax(230px,290px) minmax(0,1fr);gap:18px}.ai-composer{padding:20px}}
@media(max-width:760px){.ai-heading{display:block}.ai-heading h2{font-size:23px}.index-status{display:block;margin-top:12px}.ai-layout{grid-template-columns:minmax(0,1fr);gap:20px}.ai-composer{padding:20px}.response-empty{min-height:240px;padding:32px 22px}.ai-job{padding:24px 20px}.ai-report-row{flex-direction:column}.ai-source{padding:20px}.ai-source pre{padding:12px}.composer-help{font-size:13px}.ai-settings>summary{padding:13px 14px}.settings-body{padding:0 14px 18px}}
</style>
