<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import { diaryAiApi as api, type AiIndex, type AiJob, type AiPreview, type AiReport, type AiReportSummary, type AiRequest, type AiResult, type AiScope, type AiSource } from '@/utils/diary-ai-api'
import { diaryApi } from '@/utils/diary-api'
const props = defineProps<{ blocked: boolean; entryId: string; revision: number; choices: { id: string; title: string }[]; canAdopt?: boolean }>()
const emit = defineEmits<{ (e: 'open-entry', id: string): void; (e: 'adopt-suggestion', value: { reportId: string; index: number; suggestion: Record<string, unknown> }): void }>()
const capability = ref<{ enabled: boolean; available: boolean; message: string }>(), index = ref<AiIndex>()
const opened = ref(false), consent = ref(false), busy = ref(false), error = ref(''), question = ref(''), partial = ref(false)
const mode = ref<'current' | 'range' | 'selected'>('current'), ids = ref(''), axis = ref<'created' | 'covered'>('covered'), from = ref(''), to = ref('')
const preview = ref<AiPreview>(), job = ref<AiJob>(), report = ref<AiReport>(), result = ref<AiResult>()
const reports = ref<AiReportSummary[]>([]), page = ref(1), total = ref(0)
const pending = ref<{ kind: 'questions' | 'reviews'; body: AiRequest }>(), indexPending = ref<{ start: boolean; id: string; retry: boolean }>()
const source = ref<AiSource>(), sourceBefore = ref(''), sourceAfter = ref(''), sourceValid = ref(false)
let disposed = false, timer: ReturnType<typeof setTimeout> | undefined, epoch = 0
const running = computed(() => !!job.value && ['QUEUED', 'RUNNING'].includes(job.value.state))
const canSubmit = computed(() => !!capability.value?.available && !props.blocked && !busy.value && !running.value && !pending.value)
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
  await run(async () => { const s = scope(true)!; const p = await api.preview(s); if (!disposed) preview.value = p })
}
async function submit(kind: 'questions' | 'reviews') {
  if (props.blocked || running.value || busy.value) return
  await run(async () => {
    if (!pending.value) {
      if (kind === 'reviews' && !preview.value) throw new Error('请先预览完整范围')
      pending.value = { kind, body: { requestId: crypto.randomUUID(), question: kind === 'questions' ? question.value : undefined, scope: scope(kind === 'reviews'), snapshotDigest: kind === 'reviews' ? preview.value?.snapshotDigest : undefined, allowPartialIndex: partial.value } }
    }
    const p = pending.value, j = await api.submit(p.kind, p.body)
    if (disposed) return; pending.value = undefined; job.value = j; report.value = undefined; result.value = undefined; poll()
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
  const parsed = JSON.parse(r.result) as AiResult; report.value = r; result.value = parsed
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
onBeforeUnmount(() => { disposed = true; epoch++; clearTimeout(timer); capability.value = undefined; index.value = undefined; preview.value = undefined; question.value = ''; pending.value = undefined; reports.value = []; report.value = undefined; result.value = undefined; source.value = undefined; sourceBefore.value = ''; sourceAfter.value = '' })
</script>

<template>
  <section class="diary-ai" aria-label="私密日记助手">
    <button class="ai-heading" type="button" :aria-expanded="opened" @click="opened = !opened"><span>日记助手 <small>查找记录 · 手动复盘</small></span><span>{{ opened ? '收起 −' : '展开 +' }}</span></button>
    <div v-if="opened" class="ai-body">
      <p>{{ capability?.message || '正在确认 AI 功能状态…' }}</p>
      <p v-if="error" class="ai-error" role="alert">{{ error }}</p>
      <el-button v-if="!capability?.available" :loading="busy" @click="run(initialize)">重新检查</el-button>
      <template v-else>
        <details><summary>数据会怎样处理？</summary><p>建立索引会将已保存的日记片段发送给站点配置的云端 Embedding 服务；问答会发送你的问题和检索出的片段，复盘会发送预览范围内的完整正文。向量和报告保存在私密 PostgreSQL，公开文章与聊天记录不参与。</p><p>首次建立后会增量处理已保存的新版本，暂停可停止后续索引；已经发出的请求无法撤回，仍可能计费。模型供应商的数据保留政策由站点配置确认。报告只是辅助阅读，引用正确也不保证解释正确。</p></details>
        <div class="ai-index"><strong>索引 {{ index?.coverage.indexed ?? '—' }} / {{ index?.coverage.total ?? '—' }} 篇</strong><span>异常 {{ index?.coverage.failed ?? '—' }} 篇 · {{ index?.syncing ? '自动增量中' : '已暂停' }}</span><label><input v-model="consent" type="checkbox" />我已了解云端数据流，允许建立索引</label><div class="ai-actions"><el-button :disabled="busy || !consent" @click="changeIndex(true)">建立 / 继续索引</el-button><el-button :disabled="busy" @click="changeIndex(false)">暂停索引</el-button><el-button :disabled="busy || !consent" @click="changeIndex(true, true)">明确重试异常篇目</el-button><el-button :disabled="busy" @click="run(refresh)">刷新状态</el-button><el-button v-if="indexPending" :disabled="busy" @click="changeIndex(indexPending.start, indexPending.retry)">重试原索引操作</el-button></div><small>{{ index?.message }}</small></div>
        <p v-if="blocked" class="ai-error">请先保存并确认当前修改，再发起问答或复盘。这里不会分析未保存草稿。</p>
        <fieldset :disabled="busy || running || !!pending"><legend>资料范围</legend><label>选择方式<select v-model="mode"><option value="current">问答检索全部；复盘当前篇</option><option value="range">按日期范围</option><option value="selected">指定多篇</option></select></label><div v-if="mode === 'selected'"><p>已选 {{ ids.split(' ').filter(Boolean).length }} / 40 篇</p><small>下方列出查阅区域当前页的日记。可以先筛选或翻页，再继续勾选；已有选择会保留。</small><label v-for="entry in choices" :key="entry.id"><input type="checkbox" :data-entry-id="entry.id" :checked="ids.split(' ').includes(entry.id)" @change="selectEntry(entry.id)" />{{ entry.title || '无标题日记' }}</label><el-button v-if="ids" @click="ids = ''">清空选择</el-button></div><template v-if="mode === 'range'"><label>时间依据<select v-model="axis"><option value="created">创建时间（北京时间）</option><option value="covered">内容记录的起止日期</option></select></label><div class="ai-dates"><label>开始<input v-model="from" type="date" /></label><label>结束<input v-model="to" type="date" /></label></div><small>内容日期按区间重叠匹配，未填日期的篇目不会匹配；跨日篇目不会当作逐日记录。</small></template></fieldset>
        <div class="ai-question"><h3>问问日记</h3><label>问题<textarea v-model="question" :disabled="busy || running || !!pending" maxlength="1000" rows="3" placeholder="例如：最近几次记录中，哪些做法让我更容易开始阅读？" /></label><label><input v-model="partial" type="checkbox" :disabled="busy || running || !!pending" />索引不完整时，允许仅依据已索引资料回答</label><el-button type="primary" :disabled="!canSubmit || !question.trim()" @click="submit('questions')">查找依据并回答</el-button></div>
        <div><h3>生成复盘</h3><p>使用所选范围的完整正文，最多40篇及24,000字预算，超限请缩小范围。</p><el-button :disabled="!canSubmit" @click="previewReview">预览完整范围</el-button><div v-if="preview" class="ai-preview"><strong>{{ preview.entries }} 篇 · 输入预算约 {{ preview.characters }} 字符</strong><ul><li v-for="e in preview.records" :key="e.id">{{ e.title || '无标题日记' }}</li></ul><p>{{ preview.limitation }}</p><el-button type="primary" :disabled="!canSubmit" @click="submit('reviews')">确认范围并手动生成</el-button></div></div>
        <div v-if="pending" role="status"><p>提交结果未确认，重试会使用原请求标识。先确认这次请求，再更换内容。</p><el-button :disabled="busy || blocked" @click="submit(pending.kind)">重试原请求</el-button></div>
        <div v-if="job" class="ai-job" aria-live="polite"><strong>{{ job.message }}</strong><p v-if="job.possiblyCharged">请求可能已产生费用，等待结果或取消不会退回额度。</p><el-button v-if="running" :disabled="busy" @click="cancel">取消任务</el-button><el-button v-if="running" :disabled="busy" @click="poll">继续查询状态</el-button></div>
        <article v-if="result && report" class="ai-result"><h3>{{ report.kind === 'QUESTION' ? '有依据的回答' : '范围复盘' }}</h3><p class="ai-meta">{{ report.model }} · {{ report.createdAt }}<span v-if="report.partial"> · 仅覆盖部分索引</span></p><p v-if="result.insufficientEvidence">记录不足，无法得出可靠结论。</p><template v-for="section in [{ title: '回答', items: result.answer ? [result.answer] : [] }, { title: '概览', items: result.summary ? [result.summary] : [] }, { title: '记录中的观察', items: result.observations }, { title: '待核实的推测', items: result.hypotheses }]" :key="section.title"><section v-if="section.items?.length"><h4>{{ section.title }}</h4><div v-for="(item, n) in section.items" :key="n"><p>{{ item.text }}</p><blockquote v-for="(q, i) in item.quotes" :key="i">{{ q.text }} <small>[{{ q.sourceId }}]</small></blockquote></div></section></template><section v-if="result.suggestions?.length"><h4>可以尝试的小行动</h4><div v-for="(s, n) in result.suggestions" :key="n"><h5>{{ s.action }}{{ s.general ? '（一般性建议）' : '' }}</h5><p>{{ s.reason }}</p><small>投入：{{ s.effort }} · 复查：{{ s.reviewAfter }} · 依据 {{ s.evidenceIds.join('、') || '通用建议' }}</small><el-button v-if="canAdopt" :disabled="blocked || busy" @click="emit('adopt-suggestion', { reportId: report.id, index: Number(n), suggestion: s })">编辑后采纳为行动</el-button></div></section><h4>资料与解释的限制</h4><ul><li v-for="(l, n) in result.limitations" :key="n">{{ l }}</li></ul><h4>回到原文核对</h4><div class="ai-actions"><el-button v-for="s in report.sources" :key="s.sourceId" :disabled="busy" @click="locate(s)">{{ s.sourceId }} · {{ s.title || '无标题' }} · v{{ s.revision }}</el-button></div></article>
        <section v-if="source && sourceValid" class="ai-source"><h4>{{ source.title }} · 版本 {{ source.revision }}</h4><pre>{{ sourceBefore }}<mark>{{ source.text }}</mark>{{ sourceAfter }}</pre><el-button :disabled="blocked" @click="emit('open-entry', source.entryId)">在编辑区打开这篇</el-button></section>
        <section class="ai-reports"><h3>私密分析记录</h3><p v-if="!reports.length">还没有报告。保存日记后，可以手动发起分析。</p><div v-for="r in reports" :key="r.id" class="ai-report-row"><span>{{ r.kind === 'QUESTION' ? '问答' : '复盘' }} · {{ r.createdAt }} · {{ r.entries }}篇 · {{ r.state === 'STALE' ? '来源已变化' : '已完成' }}</span><div><el-button :disabled="busy || r.state !== 'SUCCEEDED'" @click="run(() => showReport(r.id))">查看</el-button><el-button :disabled="busy" @click="removeReport(r.id)">删除</el-button></div></div><div class="ai-actions"><el-button :disabled="busy || page <= 1" @click="page--; run(refresh)">上一页</el-button><span>{{ page }} / {{ Math.max(1, Math.ceil(total / 20)) }}</span><el-button :disabled="busy || page * 20 >= total" @click="page++; run(refresh)">下一页</el-button></div></section>
      </template>
    </div>
  </section>
</template>

<style scoped>
.diary-ai{margin:28px 0 0;border:1px solid var(--el-border-color);border-radius:8px;background:var(--el-bg-color);color:var(--el-text-color-primary)}.ai-heading{display:flex;justify-content:space-between;gap:16px;width:100%;padding:18px 20px;border:0;background:transparent;color:inherit;text-align:left;font:inherit;font-weight:600;cursor:pointer}.ai-heading small{font-size:12px;font-weight:400;margin-left:12px;color:var(--el-text-color-secondary)}.ai-body{padding:0 20px 24px;line-height:1.8;overflow-wrap:anywhere}.ai-body>div,.ai-body>section,.ai-body>article{margin-top:22px}.ai-body h3{font-size:18px;margin:20px 0 12px}.ai-body h4{font-size:15px;margin-top:24px}.ai-body h5{font-size:14px;margin:15px 0 4px}.ai-body p{white-space:pre-wrap}.ai-body small,.ai-meta{color:var(--el-text-color-secondary);font-size:12px}.ai-body label{display:block;margin:12px 0;font-size:14px}.ai-body :is(input:not([type=checkbox]),select,textarea){display:block;width:100%;box-sizing:border-box;margin-top:6px;padding:9px 10px;border:1px solid var(--el-border-color);border-radius:5px;background:var(--el-bg-color);color:inherit;font:inherit}.ai-body input[type=checkbox]{margin-right:6px}.ai-body fieldset{margin:22px 0;border:1px solid var(--el-border-color);border-radius:5px;min-width:0}.ai-body details{padding:12px 0;border-bottom:1px solid var(--el-border-color)}.ai-body summary{cursor:pointer}.ai-index{padding-bottom:20px;border-bottom:1px solid var(--el-border-color)}.ai-index>span{margin-left:14px;font-size:13px}.ai-actions{display:flex;gap:8px;flex-wrap:wrap;align-items:center}.ai-actions .el-button+.el-button{margin-left:0}.ai-error{color:var(--el-color-danger,#b23b31)}.ai-preview,.ai-job,.ai-source{padding:15px;background:var(--el-fill-color-light);border-radius:5px}.ai-dates{display:grid;grid-template-columns:1fr 1fr;gap:14px}.ai-result{border-top:1px solid var(--el-border-color);padding-top:12px}.ai-result blockquote{margin:8px 0;padding:8px 14px;border-left:3px solid var(--el-border-color);white-space:pre-wrap;color:var(--el-text-color-secondary)}.ai-source pre{max-height:340px;overflow:auto;white-space:pre-wrap;overflow-wrap:anywhere;font:inherit}.ai-source mark{background:var(--el-color-warning-light-5,#fbe6a2);color:inherit}.ai-report-row{display:flex;justify-content:space-between;gap:12px;border-top:1px solid var(--el-border-color);padding:12px 0;font-size:13px}.ai-body :focus-visible{outline:2px solid var(--el-color-primary);outline-offset:3px}@media(max-width:760px){.ai-heading{padding:16px}.ai-heading small{display:block;margin-left:0;margin-top:4px}.ai-body{padding:0 16px 20px}.ai-dates{grid-template-columns:1fr}.ai-report-row{flex-direction:column}.ai-index>span{display:block;margin-left:0}}
</style>
