<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import { diaryReadRequest, type DiaryPage } from '@/utils/diary-api'
import type { DiaryEntry } from '@/utils/diary-save'
import { sanitizePrivateDiary } from '@/utils/diary-preview'
import DiaryAiResult from './DiaryAiResult.vue'
import type { AiReport, AiResult } from '@/utils/diary-ai-api'

type Tab = 'entries' | 'ai' | 'period' | 'goals' | 'actions' | 'feedback'
interface Summary { id: string; kind?: string; model?: string; createdAt?: string; content?: Record<string, unknown>; updatedAt?: string }
interface Report { id: string; kind: string; result: AiResult & Record<string, any>; sources: { sourceId: string; entryId?: string; title: string; text: string }[]; model: string; createdAt: string }
const tabs: { key: Tab; label: string }[] = [{ key: 'entries', label: '日记' }, { key: 'ai', label: '问答与复盘' }, { key: 'period', label: '期间复盘' }, { key: 'goals', label: '目标' }, { key: 'actions', label: '行动' }, { key: 'feedback', label: '实际反馈' }]
const tab = ref<Tab>('entries'), rows = ref<Summary[]>([]), page = ref(1), total = ref(0)
const busy = ref(false), detailBusy = ref(false), error = ref(''), entry = ref<DiaryEntry>(), report = ref<Report>()
const keyword = ref(''), axis = ref<'created' | 'covered'>('created'), from = ref(''), to = ref(''), month = ref('')
const calendar = ref<{ date: string; count: number }[]>([])
let listSequence = 0, detailSequence = 0, disposed = false
const format = (value?: string) => value ? new Date(value).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', dateStyle: 'medium' }) : ''
const reportLabels: Record<string, string> = { QUESTION: '历史问答', REVIEW: '日记复盘', PERIOD: '期间复盘' }
const contentLabels: Record<string, string> = { title: '目标', outcome: '期望结果', criterion: '衡量标准', startOn: '开始日期', targetOn: '目标日期', state: '状态', text: '行动计划', plannedOn: '计划日期', execution: '执行情况', effect: '效果', occurredOn: '实际日期', note: '本人反馈' }
const values: Record<string, string> = { ACTIVE: '进行中', PAUSED: '暂停', COMPLETED: '已完成', ARCHIVED: '已归档', PLANNED: '计划中', IN_PROGRESS: '进行中', SKIPPED: '未尝试', TRIED: '已尝试', HELPFUL: '有帮助', NOT_HELPFUL: '没有帮助', UNSURE: '尚不确定', NOT_EVALUATED: '尚未评价' }
const displayValue = (value: unknown) => values[String(value)] || String(value ?? '未填写')
const aiReport = computed<AiReport | undefined>(() => report.value ? ({ ...report.value, partial: false, result: JSON.stringify(report.value.result), sources: report.value.sources.map(s => ({ ...s, entryId: s.entryId || '', revision: 0, start: 0, end: 0 })) }) : undefined)
async function load() {
  const seq = ++listSequence; ++detailSequence
  rows.value = []; entry.value = undefined; report.value = undefined; total.value = 0; busy.value = true; detailBusy.value = false; error.value = ''
  try {
    const response = tab.value === 'entries'
      ? await diaryReadRequest<DiaryPage<Summary>>('POST', '/entries/search', { axis: axis.value, from: from.value || null, to: to.value || null, keyword: keyword.value, page: page.value, pageSize: 20, deleted: false })
      : await diaryReadRequest<DiaryPage<Summary>>('GET', (['ai', 'period'].includes(tab.value) ? '/reports/' : '/coaching/') + tab.value + '?page=' + page.value)
    if (disposed || seq !== listSequence) return
    rows.value = response.records; total.value = response.total
  } catch (failure: any) { if (!disposed && seq === listSequence) error.value = failure.message || '读取失败，请重试。' }
  finally { if (seq === listSequence) busy.value = false }
}
async function open(row: Summary) {
  if (!['entries', 'ai', 'period'].includes(tab.value)) return
  const seq = ++detailSequence; const scope = listSequence
  entry.value = undefined; report.value = undefined; detailBusy.value = true; error.value = ''
  try {
    if (tab.value === 'entries') {
      const value = await diaryReadRequest<DiaryEntry>('GET', '/entries/' + encodeURIComponent(row.id))
      if (!disposed && seq === detailSequence && scope === listSequence) entry.value = value
    } else {
      const value = await diaryReadRequest<Report>('GET', '/reports/' + tab.value + '/' + encodeURIComponent(row.id))
      if (!disposed && seq === detailSequence && scope === listSequence) report.value = value
    }
  } catch (failure: any) { if (!disposed && seq === detailSequence) error.value = failure.message || '内容已变化或不可查看。' }
  finally { if (seq === detailSequence) detailBusy.value = false }
}
async function loadCalendar() {
  const selected = month.value; calendar.value = []
  if (!/^\d{4}-\d{2}$/.test(selected)) return
  try { const value = await diaryReadRequest<{ date: string; count: number }[]>('GET', '/calendar?month=' + selected); if (!disposed && selected === month.value) calendar.value = value }
  catch { if (!disposed) error.value = '日历读取失败，请重试。' }
}
async function locate(source: { entryId: string }) {
  if (!source.entryId) return
  tab.value = 'entries'; await nextTick(); await open({ id: source.entryId })
}
watch(tab, () => { page.value = 1; void load() }, { immediate: true })
onBeforeUnmount(() => { disposed = true; listSequence++; detailSequence++; rows.value = []; entry.value = undefined; report.value = undefined; calendar.value = [] })
</script>
<template>
  <main class="diary-reader">
    <header class="reader-heading"><div><p class="reader-eyebrow">记录与回看</p><h1>我的日记</h1><p>阅读站主的经历、思考和已经生成的分析。</p></div><span class="readonly-badge">只读浏览</span></header>
    <nav class="reader-tabs" aria-label="日记内容分类"><button v-for="item in tabs" :key="item.key" :aria-current="tab === item.key ? 'page' : undefined" @click="tab = item.key">{{ item.label }}</button></nav>
    <form v-if="tab === 'entries'" class="reader-filters" @submit.prevent="page = 1; load()">
      <label>关键词<input v-model="keyword" maxlength="120" placeholder="搜索日记文字" /></label>
      <label>日期依据<select v-model="axis"><option value="created">写作日期</option><option value="covered">内容日期</option></select></label>
      <label>开始日期<input v-model="from" type="date" /></label><label>结束日期<input v-model="to" type="date" /></label><el-button native-type="submit">筛选</el-button>
      <details><summary>按写作月份查阅</summary><label>月份<input v-model="month" type="month" @change="loadCalendar" /></label><p v-for="day in calendar" :key="day.date">{{ day.date }} · {{ day.count }} 篇</p></details>
    </form>
    <div v-if="error" class="reader-error" role="alert"><p>{{ error }}</p><el-button @click="load">重新读取</el-button></div>
    <div class="reader-layout" :class="{ 'records-only': ['goals', 'actions', 'feedback'].includes(tab) }">
      <section class="reader-list" aria-label="记录列表" :aria-busy="busy">
        <h2>{{ tabs.find(item => item.key === tab)?.label }} <small>{{ total }} 条</small></h2><p v-if="busy" role="status">正在读取…</p><p v-else-if="!rows.length && !error">暂无可查看的内容。</p>
        <template v-for="row in rows" :key="row.id">
          <article v-if="row.content" class="coaching-read-row"><dl><template v-for="(value, key) in row.content" :key="key"><template v-if="contentLabels[key]"><dt>{{ contentLabels[key] }}</dt><dd>{{ displayValue(value) }}</dd></template></template></dl><small>{{ format(row.updatedAt) }}</small></article>
          <button v-else class="reader-row" :class="{ selected: entry?.id === row.id || report?.id === row.id }" @click="open(row)"><strong>{{ (row as any).title || reportLabels[row.kind || ''] || '无标题日记' }}</strong><span>{{ format(row.createdAt) }}</span></button>
        </template>
        <div v-if="total > 20" class="reader-pages"><el-button :disabled="page <= 1 || busy" @click="page--; load()">上一页</el-button><span>{{ page }} / {{ Math.ceil(total / 20) }}</span><el-button :disabled="page * 20 >= total || busy" @click="page++; load()">下一页</el-button></div>
      </section>
      <section v-if="!['goals', 'actions', 'feedback'].includes(tab)" class="reader-detail" aria-label="阅读内容" :aria-busy="detailBusy">
        <p v-if="detailBusy" role="status">正在读取内容…</p>
        <article v-else-if="entry"><p class="reader-eyebrow">日记正文</p><h2>{{ entry.title || '无标题日记' }}</h2><p class="reader-date">写于 {{ format(entry.createdAt) }}<span v-if="entry.coveredFrom"> · 记录 {{ entry.coveredFrom }} 至 {{ entry.coveredTo }}</span></p><MdPreview :model-value="entry.body" :sanitize="sanitizePrivateDiary" no-mermaid no-katex no-highlight /></article>
        <DiaryAiResult v-else-if="report && report.kind !== 'PERIOD' && aiReport" :report="aiReport" :result="report.result" :busy="false" :blocked="true" :can-adopt="false" @locate="locate" />
        <article v-else-if="report" class="period-read"><p class="reader-eyebrow">已生成的分析</p><h2>期间复盘</h2><p class="reader-date">{{ format(report.createdAt) }} · {{ report.model }}</p><p>{{ report.result.coverage }}</p>
          <section v-for="group in [{key:'currentGoals',label:'目标回顾'},{key:'observations',label:'记录中的观察'},{key:'feedback',label:'实际反馈'},{key:'hypotheses',label:'可能的解释'}]" :key="group.key"><template v-if="report.result[group.key]?.length"><h3>{{ group.label }}</h3><div v-for="(claim, i) in report.result[group.key]" :key="i"><p>{{ claim.text }}</p><details v-if="claim.quotes?.length"><summary>核对原文</summary><blockquote v-for="(quote, j) in claim.quotes" :key="j">{{ quote.text }}</blockquote></details></div></template></section>
          <section v-if="report.result.suggestions?.length"><h3>当时生成的建议</h3><div v-for="(item, i) in report.result.suggestions" :key="i"><h4>{{ item.action }}</h4><p>{{ item.reason }}</p><p>投入：{{ item.effort }} · 回看：{{ item.reviewAfter }}</p></div></section><p v-for="(item, i) in report.result.limitations" :key="i">{{ item }}</p>
          <details><summary>引用来源 · {{ report.sources.length }} 条</summary><blockquote v-for="source in report.sources" :key="source.sourceId"><strong>{{ source.sourceId }} · {{ source.title }}</strong><p>{{ source.text }}</p></blockquote></details>
        </article>
        <div v-else class="reader-empty"><h2>选择一条记录，开始阅读</h2><p>这里只展示已有内容，不会发起 AI 请求或修改日记。</p></div>
      </section>
    </div>
  </main>
</template>
<style scoped>
.diary-reader { max-width: 86rem; margin: 5.5rem auto 3rem; padding: 1.5rem 2rem; color: var(--brand-ink, #172d4a); }
.reader-heading { display: flex; align-items: center; justify-content: space-between; gap: 1rem; margin-bottom: 1.5rem; }.reader-heading h1 { margin: .3rem 0 .7rem; font-size: 2rem; }.reader-heading p,.reader-date { color: var(--brand-muted, #62758c); line-height: 1.7; }.reader-eyebrow { font-size: .8rem; letter-spacing: .08em; }.readonly-badge { border: 1px solid #ccd8e6; border-radius: 2rem; padding: .5rem .8rem; white-space: nowrap; font-size: .8rem; }
.reader-tabs { display: flex; gap: .3rem; padding: .5rem; background: white; border: 1px solid #dbe2ec; border-radius: .8rem; flex-wrap: wrap; }.reader-tabs button { padding: .8rem 1.1rem; border: none; border-radius: .5rem; background: transparent; color: inherit; cursor: pointer; }.reader-tabs button[aria-current] { background: #e8efff; color: #1046d2; font-weight: 600; }
.reader-filters { display: flex; align-items: end; gap: .7rem; flex-wrap: wrap; margin: 1.2rem 0; }.reader-filters label { display: grid; gap: .4rem; font-size: .85rem; }.reader-filters input,.reader-filters select { border: 1px solid #cdd8e6; border-radius: .4rem; min-height: 2.5rem; padding: .4rem; max-width: 100%; background: white; color: inherit; }.reader-filters details { flex-basis: 100%; }.reader-filters summary { cursor: pointer; padding: .5rem 0; }
.reader-layout { display: grid; grid-template-columns: minmax(14rem, 20rem) minmax(0,1fr); gap: 1.5rem; margin-top: 1.5rem; }.reader-layout.records-only { grid-template-columns: 1fr; }.reader-list,.reader-detail { min-width: 0; }.reader-list h2 { font-size: 1rem; }.reader-list small { font-size: .8rem; font-weight: normal; color: #62758c; }.reader-row { display: grid; gap: .6rem; width: 100%; padding: 1rem; text-align: left; border: 1px solid transparent; border-radius: .5rem; background: transparent; cursor: pointer; color: inherit; overflow-wrap: anywhere; }.reader-row span { color: #62758c; font-size: .8rem; }.reader-row.selected { border-color: #bbceff; background: #e8efff; }.reader-detail { background: white; border: 1px solid #dbe2ec; border-radius: .8rem; padding: 1.5rem; min-height: 24rem; overflow-wrap: anywhere; }.reader-detail h2 { line-height: 1.5; }.reader-date { font-size: .85rem; }.reader-empty { padding: 3rem .5rem; }.reader-empty h2 { font-size: 1.2rem; }.reader-pages { display: flex; align-items: center; flex-wrap: wrap; gap: .5rem; margin-top: 1rem; }.reader-error { padding: 1rem; border-left: 3px solid #d67641; background: #fff6ef; margin-top: 1rem; }
.coaching-read-row { background: white; padding: 1rem 1.2rem; border-bottom: 1px solid #dbe2ec; }.coaching-read-row dl { display: grid; grid-template-columns: 6rem minmax(0,1fr); gap: .6rem; }.coaching-read-row dt { color: #62758c; }.coaching-read-row dd { margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; }.period-read p { white-space: pre-wrap; line-height: 1.85; }.period-read section { margin-top: 2rem; border-top: 1px solid #dbe2ec; padding-top: .5rem; }blockquote { margin: 1rem 0; border-left: 3px solid #cbd8f0; padding-left: 1rem; white-space: pre-wrap; }
@media(max-width: 760px) { .diary-reader { padding: 1rem; margin-top: 4rem; }.reader-heading { align-items: flex-start; }.reader-heading h1 { font-size: 1.6rem; }.reader-tabs button { flex: 1 0 28%; padding: .7rem .4rem; }.reader-layout { grid-template-columns: 1fr; gap: 1rem; }.reader-list { max-height: 23rem; overflow-y: auto; }.reader-layout.records-only .reader-list { max-height: none; }.reader-detail { padding: 1rem; }.reader-filters label { flex: 1 1 40%; min-width: 0; }.reader-filters input,.reader-filters select { width: 100%; box-sizing: border-box; } }
</style>
