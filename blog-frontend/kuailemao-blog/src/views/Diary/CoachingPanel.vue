<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { coachingApi, type CoachingKind, type CoachingRow, type CoachingWrite, type CoachingPeriod, type CoachingPreview, type CoachingOrigin, type CoachingJob, type CoachingAiRequest, type CoachingReport, type CoachingReportSummary } from '@/utils/coaching-api'
import { registerPrivateWorkspaceGuard } from '@/utils/private-workspace'

const props = defineProps<{ blocked: boolean; section?: 'goals' | 'review' }>()
const emit = defineEmits<{ (event: 'ready', available: boolean): void; (event: 'section-change', section: 'goals' | 'review'): void }>()
const cap = ref<{ enabled: boolean; available: boolean; aiAvailable: boolean; message: string }>(), message = ref(''), busy = ref(false)
const localTab = ref<'goals' | 'review'>('goals')
const tab = computed({ get: () => props.section || localTab.value, set: (value: 'goals' | 'review') => { localTab.value = value; emit('section-change', value) } })
const goals = ref<CoachingRow[]>([]), actions = ref<CoachingRow[]>([]), feedback = ref<CoachingRow[]>([])
const selectedGoal = ref(''), selectedAction = ref(''), goalPage = ref(1), goalTotal = ref(0), actionPage = ref(1), actionTotal = ref(0), feedbackPage = ref(1), feedbackTotal = ref(0)
const form = ref<{ kind: CoachingKind; row?: CoachingRow; data: Record<string, any>; baseline: string; origin?: CoachingOrigin }>(), pending = ref<{ kind: CoachingKind; payload: CoachingWrite; remove: boolean }>()
const saving = ref(false), formError = ref(''), conflict = ref(false), completeAction = ref(false)
const history = ref<{ revision: number; content: Record<string, any>; savedAt: string; deletedAt?: string }[]>([]), historyTarget = ref<{ kind: CoachingKind; id: string }>(), historyPage = ref(1), historyTotal = ref(0)
const today = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date())
const period = ref<CoachingPeriod>({ type: 'WEEK', anchor: today, axis: 'covered', goalIds: [], includeUndated: false }), preview = ref<CoachingPreview>(), previewKey = ref('')
const selectedSource = ref(''), originStatus = ref('')
const consent = ref(false), aiRequest = ref<CoachingAiRequest>(), job = ref<CoachingJob>(), report = ref<CoachingReport>(), reports = ref<CoachingReportSummary[]>([]), reportPage = ref(1), reportTotal = ref(0)
const sending = ref(false), aiMessage = ref(''), sourceText = ref('')
let pollTimer: ReturnType<typeof setTimeout> | undefined
let disposed = false, requestSequence = 0, guardFlight: Promise<boolean> | undefined
const dirty = computed(() => !!pending.value || !!aiRequest.value && !job.value || !!form.value && (JSON.stringify(form.value.data) !== form.value.baseline || completeAction.value))
const currentGoal = computed(() => goals.value.find(r => r.id === selectedGoal.value)), currentAction = computed(() => actions.value.find(r => r.id === selectedAction.value))
const currentPreview = computed(() => previewKey.value === JSON.stringify(period.value) ? preview.value : undefined)
const reportResult = computed(() => { try { return report.value ? JSON.parse(report.value.result) : undefined } catch { return undefined } })
const sections = [{ key: 'currentGoals', label: '当前目标' }, { key: 'observations', label: '资料中的观察' }, { key: 'feedback', label: '本人执行与效果反馈' }, { key: 'hypotheses', label: '待验证的推测' }]
const label: Record<string, string> = { ACTIVE: '进行中', PAUSED: '暂停', COMPLETED: '已完成', ARCHIVED: '已归档', PLANNED: '计划中', IN_PROGRESS: '正在尝试', SKIPPED: '跳过', TRIED: '已尝试', HELPFUL: '有帮助', NOT_HELPFUL: '无帮助', UNSURE: '不确定', NOT_EVALUATED: '未评价' }
const displayDate = (value?: string) => value ? new Date(value).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' }) : '未知日期'
const errorText = (error: unknown) => error instanceof Error ? error.message : '请求未获确认，请保留未保存内容'
const copy = <T,>(value: T): T => JSON.parse(JSON.stringify(value))
async function guardedDiscard() {
  if (!dirty.value) { form.value = undefined; return true }
  if (saving.value) return false
  if (guardFlight) return guardFlight
  guardFlight = (async () => {
    try {
      await ElMessageBox.confirm(pending.value ? '这次保存的结果尚未确认，服务端可能已经保存。离开会清除本地请求；返回后请先查阅记录。确定离开？' : '目标、行动或反馈有未保存修改。确定放弃这些修改？', '保留未保存内容', { confirmButtonText: '放弃并离开', cancelButtonText: '继续编辑', type: 'warning' })
      form.value = undefined; pending.value = undefined; aiRequest.value = undefined; formError.value = ''; return true
    } catch { return false } finally { guardFlight = undefined }
  })()
  return guardFlight
}
const unregister = registerPrivateWorkspaceGuard(guardedDiscard)
onBeforeRouteLeave(guardedDiscard); onBeforeRouteUpdate(guardedDiscard)
const beforeUnload = (event: BeforeUnloadEvent) => { if (dirty.value) { event.preventDefault(); event.returnValue = '' } }
async function loadGoals() {
  const result = await coachingApi.list('goals', '', goalPage.value); if (disposed) return
  goals.value = result.records; goalTotal.value = result.total
  if (!goals.value.some(r => r.id === selectedGoal.value)) selectedGoal.value = goals.value[0]?.id || ''
  if (!period.value.goalIds.length && selectedGoal.value) period.value.goalIds = [selectedGoal.value]
  await loadActions()
}
async function loadActions() {
  const seq = ++requestSequence; const id = selectedGoal.value
  actions.value = []; feedback.value = []; originStatus.value = ''; preview.value = undefined
  if (!id) { selectedAction.value = ''; actionTotal.value = 0; return }
  const result = await coachingApi.list('actions', id, actionPage.value); if (disposed || seq !== requestSequence) return
  actions.value = result.records; actionTotal.value = result.total
  if (!actions.value.some(r => r.id === selectedAction.value)) selectedAction.value = actions.value[0]?.id || ''
  await loadFeedback()
}
async function loadFeedback() {
  const seq = ++requestSequence; const id = selectedAction.value; feedback.value = []; originStatus.value = ''; preview.value = undefined
  if (!id) { feedbackTotal.value = 0; return }
  const [result, origin] = await Promise.all([coachingApi.list('feedback', id, feedbackPage.value), coachingApi.origin(id)])
  if (disposed || seq !== requestSequence) return
  feedback.value = result.records; feedbackTotal.value = result.total; originStatus.value = origin
}
async function run(operation: () => Promise<void>) {
  if (busy.value || props.blocked) return
  busy.value = true; message.value = ''
  try { await operation() } catch (error) { if (!disposed) message.value = errorText(error) } finally { if (!disposed) busy.value = false }
}
async function edit(kind: CoachingKind, row?: CoachingRow) {
  if (props.blocked || !await guardedDiscard()) return
  const data = row ? copy(row.content) : kind === 'goals' ? { title: '', outcome: '', criterion: '', startOn: '', targetOn: '', state: 'ACTIVE' } : kind === 'actions' ? { goalId: selectedGoal.value, text: '', plannedOn: '', state: 'PLANNED' } : { actionId: selectedAction.value, execution: 'TRIED', effect: 'NOT_EVALUATED', occurredOn: '', note: '' }
  form.value = { kind, row, data, baseline: JSON.stringify(data) }; formError.value = ''; conflict.value = false; completeAction.value = false
  await nextTick(); document.querySelector<HTMLElement>('.coaching .editor')?.scrollIntoView({ block: 'center', behavior: 'smooth' }); document.querySelector<HTMLInputElement>('.coaching .editor input, .coaching .editor textarea')?.focus({ preventScroll: true })
}
async function save(remove = false) {
  if (!form.value || saving.value || props.blocked) return
  const draft = form.value
  if (!pending.value) pending.value = { kind: draft.kind, remove, payload: { id: draft.row?.id || crypto.randomUUID(), mutationId: crypto.randomUUID(), expectedRevision: draft.row?.revision || 0, content: remove ? null : copy(draft.data), ...(draft.origin ? { origin: draft.origin } : {}), ...(completeAction.value && currentAction.value ? { completeActionRevision: currentAction.value.revision } : {}) } }
  saving.value = true; formError.value = ''
  try {
    const attempt = pending.value; const ack = await coachingApi.write(attempt.kind, attempt.payload, attempt.remove)
    if (disposed) return
    if (ack.id !== attempt.payload.id || ack.mutationId !== attempt.payload.mutationId) throw new Error('保存确认不一致，请保留草稿')
    pending.value = undefined; form.value = undefined; preview.value = undefined; report.value = undefined; sourceText.value = ''; await loadGoals(); message.value = '已收到保存确认'
  } catch (error: any) {
    if (disposed) return
    formError.value = errorText(error); conflict.value = Number(error.status) === 409
    if ([400, 404, 409, 429].includes(Number(error.status))) pending.value = undefined
  } finally { if (!disposed) saving.value = false }
}
async function removeFeedback(row: CoachingRow) {
  try { await ElMessageBox.confirm('软删除这条反馈后，它不再计入汇总，历史版本仍保留。', '删除反馈', { confirmButtonText: '确认删除', cancelButtonText: '取消' }) } catch { return }
  await edit('feedback', row); if (form.value?.row?.id === row.id) await save(true)
}
async function showHistory(kind: CoachingKind, row: CoachingRow) {
  historyTarget.value = { kind, id: row.id }; historyPage.value = 1
  await historyLoad()
}
async function historyLoad() {
  const target = historyTarget.value; if (!target) return
  const result = await coachingApi.history(target.kind, target.id, historyPage.value)
  if (!disposed && historyTarget.value === target) { history.value = result.records; historyTotal.value = result.total }
}
async function makePreview() {
  const request = copy(period.value); const key = JSON.stringify(request)
  if (request.type !== 'CUSTOM') { delete request.from; delete request.to } else delete request.anchor
  const result = await coachingApi.preview(request); if (!disposed && key === JSON.stringify(period.value)) { preview.value = result; previewKey.value = key; selectedSource.value = '' }
}
async function exportData() {
  const blob = await coachingApi.export(); if (disposed) return
  const url = URL.createObjectURL(blob); const link = document.createElement('a'); link.href = url; link.download = '我的目标与反馈.json'; link.click(); setTimeout(() => URL.revokeObjectURL(url), 1000)
}
async function loadReports() {
  const value = await coachingApi.reports(reportPage.value); if (!disposed) { reports.value = value.records; reportTotal.value = value.total }
}
async function openReport(id: string) {
  report.value = undefined; sourceText.value = ''
  try { const value = await coachingApi.report(id); if (!disposed) report.value = value } catch (error) { if (!disposed) aiMessage.value = errorText(error) }
}
async function submitPeriod() {
  if (sending.value || props.blocked || !cap.value?.aiAvailable || !consent.value) return
  if (!aiRequest.value) {
    if (!currentPreview.value?.withinBudget) return
    const request = copy(period.value); if (request.type === 'CUSTOM') delete request.anchor; else { delete request.from; delete request.to }
    aiRequest.value = { requestId: crypto.randomUUID(), period: request, snapshotDigest: currentPreview.value.snapshotDigest }; job.value = undefined
  }
  sending.value = true; aiMessage.value = ''
  try { const value = await coachingApi.submit(aiRequest.value); if (disposed) return; job.value = value; await pollJob() }
  catch (error: any) { if (!disposed) { aiMessage.value = errorText(error); if ([400, 404, 409, 429].includes(Number(error.status))) { aiRequest.value = undefined; preview.value = undefined } } }
  finally { if (!disposed) sending.value = false }
}
async function pollJob() {
  clearTimeout(pollTimer); if (!job.value || disposed) return
  try {
    const value = await coachingApi.job(job.value.id); if (disposed) return; job.value = value
    if (['QUEUED', 'RUNNING'].includes(value.state)) pollTimer = setTimeout(pollJob, 1500)
    else { aiRequest.value = undefined; if (value.reportId && value.state === 'SUCCEEDED') await openReport(value.reportId); await loadReports() }
  } catch (error) { if (!disposed) aiMessage.value = errorText(error) }
}
async function cancelJob() { if (!job.value) return; const value = await coachingApi.cancel(job.value.id); if (!disposed) { job.value = value; aiRequest.value = undefined; clearTimeout(pollTimer) } }
async function deleteReport(id: string) {
  try { await ElMessageBox.confirm('只删除这份AI派生报告。你确认的目标、行动和反馈都会保留。', '删除报告', { confirmButtonText: '确认删除', cancelButtonText: '取消' }) } catch { return }
  await coachingApi.removeReport(id); if (disposed) return; if (report.value?.id === id) { report.value = undefined; sourceText.value = '' }; await loadReports()
}
async function sha(text: string) { return [...new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(text)))].map(v => v.toString(16).padStart(2, '0')).join('') }
function canonical(value: any): any { return Array.isArray(value) ? value.map(canonical) : value && typeof value === 'object' ? Object.fromEntries(Object.keys(value).sort().map(k => [k, canonical(value[k])])) : value }
async function inspectSource(id: string) {
  if (!report.value) return
  const reportId = report.value.id; sourceText.value = ''; const latest = await coachingApi.report(reportId); if (disposed || report.value?.id !== reportId) return
  const source = latest.sources.find(s => s.sourceId === id)
  if (!source || Array.from(source.text).length !== source.end - source.start || await sha(source.text) !== source.sha256) { report.value = undefined; throw new Error('原文定位校验失败，已隐藏报告') }
  if (!disposed) { report.value = latest; sourceText.value = source.text }
}
async function adopt(index: number) {
  if (!report.value || !reportResult.value || !selectedGoal.value) return
  const suggestion = reportResult.value.suggestions[index], id = report.value.id
  const origin: CoachingOrigin = { type: 'PERIOD_REPORT', reportId: id, suggestionIndex: index, suggestionHash: await sha(JSON.stringify(canonical(suggestion))) }
  const latest = await coachingApi.suggestion(origin); if (disposed) return
  await edit('actions'); if (!form.value || disposed) return
  form.value.origin = origin; form.value.data.text = latest.action; tab.value = 'goals'
}
async function adoptExisting(value: { reportId: string; index: number; suggestion: Record<string, unknown> }) {
  await run(async () => {
    if (!selectedGoal.value) { message.value = '请先创建或选择一个目标，再打开建议草稿'; return }
    const origin: CoachingOrigin = { type: 'AI_REPORT', reportId: value.reportId, suggestionIndex: value.index, suggestionHash: await sha(JSON.stringify(canonical(value.suggestion))) }
    const latest = await coachingApi.suggestion(origin); if (disposed) return
    await edit('actions'); if (!form.value || disposed) return
    form.value.origin = origin; form.value.data.text = latest.action; tab.value = 'goals'
  })
}
defineExpose({ adoptExisting })
onMounted(async () => {
  window.addEventListener('beforeunload', beforeUnload)
  try { const value = await coachingApi.capabilities(); if (disposed) return; cap.value = value; emit('ready', value.available); if (value.available) await run(loadGoals) } catch (error) { if (!disposed) message.value = errorText(error) }
})
onBeforeUnmount(() => { disposed = true; emit('ready', false); requestSequence++; clearTimeout(pollTimer); unregister(); window.removeEventListener('beforeunload', beforeUnload); form.value = undefined; pending.value = undefined; goals.value = []; actions.value = []; feedback.value = []; preview.value = undefined; history.value = []; report.value = undefined; reports.value = []; aiRequest.value = undefined; sourceText.value = '' })
</script>

<template>
  <section class="coaching" aria-labelledby="coaching-title">
    <header><div><h2 id="coaching-title">{{ tab === 'review' ? '周／月回顾' : '目标行动' }}</h2><p>{{ tab === 'review' ? '把日记、行动与自己的反馈放在一起，回看这一段时间。' : '先选一个目标，再记录行动和实际感受。' }}</p></div><button v-if="cap?.available" :disabled="busy || blocked" @click="run(exportData)">导出目标与反馈</button></header>
    <details v-if="cap" class="coaching-settings"><summary>存储、模型与数据说明</summary><p class="muted">目标功能：{{ cap.enabled ? '已启用' : '未启用' }} · 保存服务：{{ cap.available ? '可用' : cap.enabled ? '暂不可用' : '未启用' }} · AI回顾：{{ cap.aiAvailable ? '可按需生成' : '未配置或关闭' }}</p><p class="notice">{{ cap.message }}</p></details><p v-if="blocked" class="notice">请先在“写日记”中确认保存结果，再修改实践记录。</p><p v-if="message" role="status">{{ message }}</p>
    <template v-if="cap?.available">
      <nav v-if="!section" aria-label="实践记录"><button :aria-pressed="tab === 'goals'" @click="tab = 'goals'">目标与行动</button><button :aria-pressed="tab === 'review'" @click="tab = 'review'">周／月回顾</button></nav>
      <div v-show="tab === 'goals'" class="practice-grid">
        <section class="practice-column"><div class="section-title"><h3>我的目标 <small>{{ goalTotal }}</small></h3><button :disabled="blocked || busy" @click="edit('goals')">新目标</button></div>
          <p v-if="!goals.length">还没有目标。先写清楚想改变什么，以及怎样判断是否有帮助。</p>
          <article v-for="goal in goals" :key="goal.id" :class="{ selected: selectedGoal === goal.id }"><button class="record-title" @click="run(async () => { selectedGoal = goal.id; actionPage = 1; await loadActions() })">{{ goal.content.title }}</button><span class="tag">{{ label[goal.content.state] }}</span><p>{{ goal.content.outcome || '尚未填写期待的变化' }}</p><p class="muted">判断标准：{{ goal.content.criterion || '未填写' }}</p><p class="muted">{{ goal.content.startOn || '未定开始' }} 至 {{ goal.content.targetOn || '未定目标日期' }}</p><div class="row-buttons"><button :disabled="blocked" @click="edit('goals', goal)">修改与状态</button><button @click="run(() => showHistory('goals', goal))">历史</button></div></article>
          <div v-if="goalTotal > 100" class="pagination"><button :disabled="goalPage <= 1 || busy" @click="goalPage--; run(loadGoals)">上一页</button><span>{{ goalPage }}</span><button :disabled="goalPage * 100 >= goalTotal || busy" @click="goalPage++; run(loadGoals)">下一页</button></div>
        </section>
        <section class="practice-column"><div class="section-title"><h3>确认的行动 <small>{{ actionTotal }}</small></h3><button :disabled="!selectedGoal || blocked || busy" @click="edit('actions')">新行动</button></div><p class="muted">{{ currentGoal?.content.title || '先选择一个目标' }}</p>
          <p v-if="selectedGoal && !actions.length">还没有行动。写一个可以尝试的小步骤，稍后再记录实际反馈。</p>
          <article v-for="action in actions" :key="action.id" :class="{ selected: selectedAction === action.id }"><button class="record-title" @click="run(async () => { selectedAction = action.id; feedbackPage = 1; await loadFeedback() })">{{ action.content.text }}</button><span class="tag">{{ label[action.content.state] }}</span><p class="muted">计划日期：{{ action.content.plannedOn || '未定' }}</p><div class="row-buttons"><button :disabled="blocked" @click="edit('actions', action)">修改与状态</button><button @click="run(() => showHistory('actions', action))">历史</button></div></article>
          <div v-if="actionTotal > 100" class="pagination"><button :disabled="actionPage <= 1 || busy" @click="actionPage--; run(loadActions)">上一页</button><span>{{ actionPage }}</span><button :disabled="actionPage * 100 >= actionTotal || busy" @click="actionPage++; run(loadActions)">下一页</button></div>
        </section>
        <section class="practice-column"><div class="section-title"><h3>我的实际反馈 <small>{{ feedbackTotal }}</small></h3><button :disabled="!selectedAction || blocked || busy" @click="edit('feedback')">记录反馈</button></div><p class="muted">{{ currentAction?.content.text || '先选择一个行动' }}</p><p v-if="originStatus === 'STALE'" class="notice">原建议来源已变化或删除。你确认的行动和反馈仍保留，旧引文已隐藏。</p><p v-if="originStatus === 'UNAVAILABLE'" class="notice">原建议暂时无法核验。人工记录不受影响。</p><p v-if="selectedAction && !feedback.length">尚未记录反馈，不代表你没有行动。</p>
          <article v-for="item in feedback" :key="item.id"><strong>{{ item.content.occurredOn || '实际日期未知' }}</strong><p>{{ label[item.content.execution] }} · {{ label[item.content.effect] }}</p><p>{{ item.content.note || '未补充说明' }}</p><p class="muted">录入 {{ displayDate(item.createdAt) }}<br />最近修正 {{ displayDate(item.updatedAt) }}</p><div class="row-buttons"><button :disabled="blocked" @click="edit('feedback', item)">纠正</button><button @click="run(() => showHistory('feedback', item))">历史</button><button :disabled="blocked" @click="removeFeedback(item)">软删除</button></div></article>
          <div v-if="feedbackTotal > 100" class="pagination"><button :disabled="feedbackPage <= 1 || busy" @click="feedbackPage--; run(loadFeedback)">上一页</button><span>{{ feedbackPage }}</span><button :disabled="feedbackPage * 100 >= feedbackTotal || busy" @click="feedbackPage++; run(loadFeedback)">下一页</button></div>
        </section>
      </div>
      <section v-show="tab === 'review'" class="period-area"><h3>先看完整资料，再作解释</h3><form class="period-form" @submit.prevent="run(makePreview)"><label>期间<select v-model="period.type"><option value="WEEK">自然周（周一至周日）</option><option value="MONTH">自然月</option><option value="CUSTOM">自定义范围</option></select></label><label v-if="period.type !== 'CUSTOM'">期间内任意一天<input v-model="period.anchor" type="date" :max="today" required /></label><template v-else><label>开始<input v-model="period.from" type="date" :max="today" required /></label><label>结束<input v-model="period.to" type="date" required /></label></template><label>日记时间依据<select v-model="period.axis"><option value="covered">内容记录起止日期</option><option value="created">写下这篇的时间</option></select></label><fieldset><legend>本次查看的目标（最多20个）</legend><label v-for="goal in goals" :key="goal.id" class="checkbox"><input v-model="period.goalIds" type="checkbox" :value="goal.id" />{{ goal.content.title }}</label></fieldset><label class="checkbox"><input v-model="period.includeUndated" type="checkbox" />单列实际日期未知的反馈背景，不计入期间统计</label><button type="submit" :disabled="busy || blocked || !period.goalIds.length">查看范围与真实汇总</button></form>
        <template v-if="currentPreview"><h4>{{ currentPreview.window.type === 'WEEK' ? '自然周' : currentPreview.window.type === 'MONTH' ? '自然月' : '自定义期间' }} · {{ currentPreview.window.from }} 至 {{ currentPreview.window.to }} <span v-if="currentPreview.window.unfinished" class="tag">尚未结束</span></h4><div class="statistics"><p><strong>{{ currentPreview.diaries.length }}</strong> 匹配日记</p><p><strong>{{ currentPreview.statistics.feedback }}</strong> 已知日期反馈</p><p><strong>{{ currentPreview.statistics.undated }}</strong> 日期未知</p><p><strong>{{ currentPreview.statistics.helpfulPercent === null ? '暂无评价' : currentPreview.statistics.helpfulPercent + '%' }}</strong> 本人认为有帮助</p></div><p>已尝试 {{ currentPreview.statistics.tried }} · 已完成 {{ currentPreview.statistics.completed }} · 跳过 {{ currentPreview.statistics.skipped }}；有帮助 {{ currentPreview.statistics.helpful }} / 可评价 {{ currentPreview.statistics.evaluable }}，不确定 {{ currentPreview.statistics.unsure }}，未评价 {{ currentPreview.statistics.notEvaluated }}。</p><p class="muted">{{ currentPreview.statistics.explanation }}</p><ul><li v-for="item in currentPreview.limitations" :key="item">{{ item }}</li></ul><p class="notice">资料序列化共 {{ currentPreview.characters }} 码点。{{ currentPreview.withinBudget ? '在AI预算内。' : '超出预算，请缩小范围。' }} {{ cap.aiAvailable ? '' : '当前只提供本地资料预览，尚未向模型发送。' }}</p><details><summary>查看完整资料与来源</summary><p v-for="diary in currentPreview.diaries" :key="diary.id">{{ diary.title || '超限篇目（未加载正文）' }} · {{ diary.coveredFrom || '未知起始' }} 至 {{ diary.coveredTo || '未知结束' }} <span v-if="diary.crossesBoundary">（跨出所选边界）</span></p><div v-for="source in currentPreview.sources" :key="source.sourceId"><button @click="selectedSource = selectedSource === source.sourceId ? '' : source.sourceId">{{ source.kind === 'D' ? '日记' : source.kind === 'G' ? '目标' : source.kind === 'A' ? '行动计划' : '本人反馈' }} · {{ source.sourceId }}</button><pre v-if="selectedSource === source.sourceId">{{ source.text }}</pre></div></details></template>
      </section>
      <section v-show="tab === 'review'" class="period-area" aria-label="AI期间回顾">
        <h3>手动生成AI回顾</h3><p>只分析本次预览的资料。生成内容是解释和建议，需要你自己判断；点击采纳也只会打开草稿。</p>
        <label v-if="cap.aiAvailable" class="checkbox"><input v-model="consent" type="checkbox" />我已阅读上方模型与数据处理说明，同意仅为这次回顾发送预览中的所选私密资料。</label>
        <button v-if="cap.aiAvailable" :disabled="blocked || sending || !consent || !aiRequest && !currentPreview?.withinBudget || !!job && ['QUEUED','RUNNING'].includes(job.state)" @click="submitPeriod">{{ sending ? '等待请求确认…' : aiRequest && !job ? '用原请求再次确认' : '生成本次回顾' }}</button>
        <p v-if="aiRequest && !job" class="notice">请求结果未确认。请用原请求重试，避免重复生成或计费。</p><p v-if="aiMessage" role="alert">{{ aiMessage }}</p>
        <div v-if="job"><p>{{ job.message }} <span v-if="job.possiblyCharged">· 请求可能已计费</span></p><button v-if="['QUEUED','RUNNING'].includes(job.state)" @click="run(cancelJob)">取消任务</button><button @click="run(pollJob)">重新确认任务状态</button></div>
        <div class="section-title"><h3>历史回顾</h3><button :disabled="busy" @click="run(loadReports)">读取历史AI报告</button></div><p>历史报告每次打开都会重新核对来源。资料变化后隐藏旧结论，人工记录仍保留。</p>
        <article v-for="item in reports" :key="item.id"><button @click="run(() => openReport(item.id))">{{ item.window.type === 'CUSTOM' ? '自定义回顾' : item.window.type === 'WEEK' ? '周回顾' : '月回顾' }} · {{ item.window.from }} 至 {{ item.window.to }}</button><span class="tag">{{ item.state === 'STALE' ? '来源已变化' : '可重新核验' }}</span><button @click="run(() => deleteReport(item.id))">删除派生报告</button></article>
        <div v-if="reportTotal" class="pagination"><button :disabled="reportPage <= 1 || busy" @click="reportPage--; run(loadReports)">上一页</button><span>{{ reportPage }}</span><button :disabled="reportPage * 20 >= reportTotal || busy" @click="reportPage++; run(loadReports)">下一页</button></div>
        <article v-if="report && reportResult" class="report"><h3>AI回顾 · {{ report.window.from }} 至 {{ report.window.to }}</h3><p class="muted">{{ report.model }} · {{ displayDate(report.createdAt) }} · {{ report.window.unfinished ? '未结束期间' : '完整日历期间（以已录入资料为准）' }}</p><h4>覆盖与边界</h4><p>{{ reportResult.coverage }}</p>
          <section v-for="section in sections" :key="section.key"><h4>{{ section.label }}</h4><p v-if="!reportResult[section.key]?.length">资料不足或没有可列出的内容。</p><div v-for="(claim, index) in reportResult[section.key]" :key="index"><p>{{ claim.text }}</p><blockquote v-for="(quote, q) in claim.quotes" :key="q">{{ quote.text }} <button @click="run(() => inspectSource(quote.sourceId))">核验原文 {{ quote.sourceId }}</button></blockquote></div></section>
          <h4>可选择的下一步</h4><article v-for="(suggestion, index) in reportResult.suggestions" :key="index"><strong>{{ suggestion.action }}</strong><p>{{ suggestion.reason }}</p><p class="muted">尝试成本：{{ suggestion.effort }} · 建议回看时间：{{ suggestion.reviewAfter }}</p><p v-if="suggestion.general">这是一般性建议，不能当作对你行为的事实判断。</p><button :disabled="blocked || !selectedGoal || busy" @click="run(() => adopt(Number(index)))">打开可编辑的行动草稿</button></article>
          <h4>限制与不确定性</h4><ul><li v-for="item in reportResult.limitations" :key="item">{{ item }}</li></ul><pre v-if="sourceText" class="source-highlight"><mark>{{ sourceText }}</mark></pre>
        </article>
      </section>
    </template>
    <section v-if="form" class="editor" aria-labelledby="practice-edit-title"><h3 id="practice-edit-title">{{ form.row ? '修改' : '新增' }}{{ form.kind === 'goals' ? '目标' : form.kind === 'actions' ? '确认的行动' : '本人反馈' }}</h3><form @submit.prevent="save()"><fieldset :disabled="saving || !!pending || blocked">
      <template v-if="form.kind === 'goals'"><label>目标标题<input v-model="form.data.title" maxlength="240" required /></label><label>期待的变化<textarea v-model="form.data.outcome" rows="3" /></label><label>怎样判断有帮助<textarea v-model="form.data.criterion" rows="3" /></label><div class="date-pair"><label>开始日期（可空）<input v-model="form.data.startOn" type="date" /></label><label>目标日期（可空）<input v-model="form.data.targetOn" type="date" /></label></div><label>本人确认的状态<select v-model="form.data.state"><option v-for="state in ['ACTIVE','PAUSED','COMPLETED','ARCHIVED']" :key="state" :value="state">{{ label[state] }}</option></select></label></template>
      <template v-else-if="form.kind === 'actions'"><label v-if="!form.row">归属目标<select v-model="form.data.goalId" aria-label="归属目标"><option v-for="goal in goals" :key="goal.id" :value="goal.id">{{ goal.content.title }}</option></select></label><label>准备尝试的具体行动<textarea v-model="form.data.text" rows="4" required /></label><label>计划日期（可空）<input v-model="form.data.plannedOn" type="date" /></label><label>本人确认的状态<select v-model="form.data.state"><option v-for="state in ['PLANNED','IN_PROGRESS','COMPLETED','SKIPPED','ARCHIVED']" :key="state" :value="state">{{ label[state] }}</option></select></label><p>计划不证明已经执行，模型和到期日期都不会替你改变状态。</p></template>
      <template v-else><label>实际执行<select v-model="form.data.execution" aria-label="实际执行"><option v-for="state in ['TRIED','COMPLETED','SKIPPED']" :key="state" :value="state">{{ label[state] }}</option></select></label><label>本人感受到的效果<select v-model="form.data.effect" aria-label="本人感受到的效果"><option v-for="state in ['HELPFUL','NOT_HELPFUL','UNSURE','NOT_EVALUATED']" :key="state" :value="state">{{ label[state] }}</option></select></label><label>实际发生日期（不知道就留空）<input v-model="form.data.occurredOn" type="date" :max="today" /></label><label>说明<textarea v-model="form.data.note" rows="4" /></label><label v-if="form.data.execution === 'COMPLETED'" class="checkbox"><input v-model="completeAction" type="checkbox" />同时把这项行动明确标为已完成（两项一起保存）</label></template>
      </fieldset><button v-if="form.origin && !pending" type="button" :disabled="saving" @click="form.origin = undefined; conflict = false; formError = '已移除建议来源，请重新确认后作为手工行动保存'">明确改为手工行动（保留编辑文字）</button><p v-if="formError" role="alert">{{ formError }}</p><p v-if="conflict" class="notice">服务端版本已变化，本地修改仍保留。请先查看最新记录，再重新编辑；不能直接覆盖。</p><div class="row-buttons"><button type="submit" :disabled="saving || blocked || conflict">{{ saving ? '等待保存确认…' : pending ? '用原请求再次确认' : '确认保存' }}</button><button type="button" :disabled="saving" @click="guardedDiscard">取消编辑</button><button v-if="conflict" type="button" @click="run(loadGoals)">刷新记录列表</button></div></form></section>
    <section v-if="historyTarget" class="history"><h3>历史版本</h3><button @click="historyTarget = undefined; history = []">关闭历史</button><article v-for="item in history" :key="item.revision"><strong>版本 {{ item.revision }} · {{ displayDate(item.savedAt) }} {{ item.deletedAt ? '（软删除）' : '' }}</strong><pre>{{ JSON.stringify(item.content, null, 2) }}</pre></article><div class="pagination"><button :disabled="historyPage <= 1 || busy" @click="historyPage--; run(historyLoad)">上一页</button><span>{{ historyPage }}</span><button :disabled="historyPage * 20 >= historyTotal || busy" @click="historyPage++; run(historyLoad)">下一页</button></div></section>
  </section>
</template>

<style scoped>
.coaching{margin-top:32px;padding:28px;border:1px solid var(--el-border-color);border-radius:12px;background:var(--el-bg-color);color:var(--el-text-color-primary)}header{display:flex;align-items:start;justify-content:space-between;gap:24px}h2{font-size:25px;margin:6px 0 12px}.eyebrow{font-size:12px;letter-spacing:.12em;color:var(--el-color-primary);margin:0}p,li{line-height:1.8;overflow-wrap:anywhere}.muted,small{color:var(--el-text-color-secondary);font-size:13px}.notice{padding:12px 16px;background:var(--el-fill-color-light);border-left:3px solid var(--el-color-primary);font-size:14px}nav{display:flex;gap:12px;margin:24px 0}button{cursor:pointer;border:1px solid var(--el-border-color);background:var(--el-bg-color);color:inherit;padding:8px 12px;border-radius:6px;font:inherit;font-size:14px}button:disabled{cursor:not-allowed;opacity:.5}button[aria-pressed=true],button[type=submit]{color:var(--el-color-primary);border-color:var(--el-color-primary)}button:focus-visible,input:focus-visible,select:focus-visible,textarea:focus-visible{outline:2px solid var(--el-color-primary);outline-offset:2px}.practice-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:20px}.practice-column{min-width:0}.section-title{display:flex;align-items:center;justify-content:space-between;gap:8px}article{padding:16px 0;border-bottom:1px solid var(--el-border-color-lighter)}article.selected{border-bottom-color:var(--el-color-primary)}.record-title{display:block;text-align:left;border:0;padding:0;font-size:16px;font-weight:600;overflow-wrap:anywhere;white-space:pre-wrap}.tag{display:inline-block;font-size:12px;margin-top:10px;color:var(--el-text-color-secondary);background:var(--el-fill-color-light);padding:2px 8px;border-radius:4px}.row-buttons,.pagination{display:flex;flex-wrap:wrap;gap:8px;margin-top:16px;align-items:center}.pagination{justify-content:space-between}label{display:flex;flex-direction:column;gap:7px;margin:14px 0;font-size:14px}input,select,textarea{width:100%;min-width:0;box-sizing:border-box;padding:10px;border:1px solid var(--el-border-color);border-radius:6px;background:var(--el-bg-color);color:inherit;font:inherit}textarea{resize:vertical}.checkbox{flex-direction:row;align-items:start;line-height:1.7}.checkbox input{width:auto;margin-top:6px}.editor,.history,.period-area{border-top:1px solid var(--el-border-color);padding-top:20px;margin-top:24px}.editor{max-width:760px}fieldset{border:0;padding:0;min-width:0}.period-form{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:0 20px}.period-form fieldset,.period-form>.checkbox{grid-column:1/-1}.period-form button{align-self:end;margin:14px 0}.date-pair{display:grid;grid-template-columns:1fr 1fr;gap:16px}.statistics{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px}.statistics strong{display:block;font-size:23px}pre{white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.8;padding:16px;background:var(--el-fill-color-light);font-size:14px}details{margin:20px 0}summary{cursor:pointer}details button{margin:6px 0}@media(max-width:1000px){.practice-grid{grid-template-columns:1fr}.practice-column{padding-bottom:12px;border-bottom:1px solid var(--el-border-color)}.statistics{grid-template-columns:1fr 1fr}}@media(max-width:600px){.coaching{padding:18px 14px}header{display:block}h2{font-size:22px}.period-form,.date-pair{grid-template-columns:1fr}.statistics strong{font-size:21px}.section-title h3{font-size:17px}.record-title{font-size:15px}}

.coaching{margin:0;padding:6px 0 0;border:0;background:transparent;color:var(--diary-text)}header{margin-bottom:24px}h2{font-size:25px;margin:0 0 6px;line-height:1.5}header p{font-size:14px;color:var(--diary-muted);margin:0}.eyebrow{display:none}.coaching-settings{margin:18px 0 24px;border:1px solid var(--diary-line);background:var(--diary-surface);border-radius:10px;padding:13px 18px;font-size:13px}.coaching-settings summary{color:var(--diary-muted)}.coaching-settings .notice{margin-top:16px}.practice-grid{grid-template-columns:minmax(210px,260px) minmax(0,1fr);gap:24px}.practice-column{padding:24px;border:1px solid var(--diary-line);border-radius:16px;background:var(--diary-surface)}.practice-column:first-child{grid-row:span 2}.practice-column:nth-child(3){grid-column:2}.practice-column h3{font-size:17px;margin:0}.practice-column h3 small{margin-left:8px;font-weight:400}.practice-column>p{font-size:13px;color:var(--diary-muted)}.record-title{font-size:15px;line-height:1.8}.practice-column article p{font-size:13px}.practice-column article.selected{border-color:var(--diary-accent)}.tag{color:var(--diary-muted);background:var(--diary-tint)}.period-area{border:1px solid var(--diary-line);border-radius:16px;background:var(--diary-surface);padding:24px;margin-top:22px}.period-area>h3{font-size:20px;margin:0 0 12px}.period-area>p{font-size:14px}.period-form{column-gap:24px}.period-form fieldset{border:1px solid var(--diary-line);padding:14px 18px;border-radius:8px}.period-form label{font-size:13px}.statistics{padding:18px 0;gap:18px;border-block:1px solid var(--diary-line);margin:18px 0}.statistics p{font-size:12px;margin:0;color:var(--diary-muted)}.statistics strong{font-size:23px;color:var(--diary-text)}.notice{background:var(--diary-tint);border-left-color:var(--diary-accent);font-size:13px}.muted,small{color:var(--diary-muted)}button{border-radius:8px;border-color:var(--diary-line);background:var(--diary-surface);font-size:13px;min-height:34px}button[aria-pressed=true],button[type=submit]{color:var(--diary-accent);border-color:var(--diary-accent)}input,select,textarea{border-radius:8px;border-color:var(--diary-line);background:var(--diary-surface)}.editor,.history{padding:26px;margin-top:24px;background:var(--diary-surface);border:1px solid var(--diary-line);border-radius:16px}.editor h3,.history h3{font-size:19px;margin-top:0}.report{border:1px solid var(--diary-line);border-radius:16px;margin-top:26px;padding:26px 30px;background:var(--diary-surface)}.report>h3{font-size:23px;margin-top:0}.report h4{font-size:16px;margin:26px 0 12px}.report p{font-size:15px;line-height:1.95}.report blockquote{border-left:3px solid var(--diary-line);padding:12px 16px;margin:14px 0 22px;background:var(--diary-tint);font-size:13px;color:var(--diary-muted);white-space:pre-wrap}.report blockquote button{display:block;margin-top:10px}.report>article{margin-top:18px}.report .muted{font-size:12px}.report ul{font-size:13px;color:var(--diary-muted)}pre{background:var(--diary-tint)}.source-highlight{border:1px solid var(--diary-line);border-radius:8px}.source-highlight mark{background:var(--brand-accent-soft);color:var(--diary-text)}
@media(max-width:1000px){.practice-grid{grid-template-columns:minmax(0,1fr)}.practice-column:first-child{grid-row:auto}.practice-column:nth-child(3){grid-column:auto}.practice-column{border-bottom:1px solid var(--diary-line)}}
@media(max-width:760px){h2{font-size:23px}.practice-column,.period-area,.editor,.history{padding:20px 16px}.period-form{grid-template-columns:minmax(0,1fr)}.date-pair{grid-template-columns:minmax(0,1fr)}.statistics{grid-template-columns:1fr 1fr}.report{padding:22px 18px}.report>h3{font-size:20px}.report blockquote{padding:12px}.coaching-settings{padding:13px 14px}.period-area button,.report button{max-width:100%;white-space:normal}}

</style>
