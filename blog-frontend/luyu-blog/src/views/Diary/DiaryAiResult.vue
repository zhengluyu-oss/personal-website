<script setup lang="ts">
import { computed } from 'vue'
import { ChatDotRound, DocumentChecked, ArrowRight } from '@element-plus/icons-vue'
import type { AiReport, AiResult, AiSource } from '@/utils/diary-ai-api'

const props = defineProps<{ report: AiReport; result: AiResult; question?: string; busy: boolean; blocked: boolean; canAdopt?: boolean }>()
const emit = defineEmits<{
  (event: 'locate', source: AiSource): void
  (event: 'adopt', value: { reportId: string; index: number; suggestion: Record<string, unknown> }): void
}>()
const isAnswer = computed(() => props.report.kind === 'QUESTION')
const lead = computed(() => isAnswer.value ? props.result.answer : props.result.summary)
// Provider content is rendered as text, including quotes and suggestions; never as HTML/Markdown.
const date = computed(() => new Date(props.report.createdAt).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', dateStyle: 'medium', timeStyle: 'short' }))
</script>

<template>
  <article class="ai-result" aria-label="AI 分析结果">
    <div v-if="isAnswer && question" class="answer-question">
      <span class="result-label">你的问题</span>
      <p>{{ question }}</p>
    </div>
    <div v-else-if="isAnswer" class="answer-question history-context">
      <span class="result-label">历史问答</span>
      <p>正在查看过去生成的回答。这份历史报告没有附带原问题。</p>
    </div>

    <header class="answer-heading">
      <span class="answer-symbol" aria-hidden="true"><ChatDotRound v-if="isAnswer" /><DocumentChecked v-else /></span>
      <div><h3>{{ isAnswer ? 'AI 回答' : 'AI 复盘' }}</h3><p>{{ date }} · {{ report.model }}</p></div>
      <span class="result-state">{{ result.insufficientEvidence ? '资料不足' : '已生成' }}</span>
    </header>
    <p v-if="report.partial" class="result-notice">本次只依据已完成索引的部分日记，可能遗漏其他记录。</p>
    <section class="answer-main" :class="{ insufficient: result.insufficientEvidence }" aria-label="回答正文">
      <p v-if="result.insufficientEvidence">{{ isAnswer ? '现有记录不足以回答这个问题。可以补充日记，或换一个更具体的问题。' : '现有记录不足以形成这次复盘。可以调整所选范围，或补充相关经历后再试。' }}</p>
      <p v-else-if="lead">{{ lead.text }}</p>
      <details v-if="!result.insufficientEvidence && lead?.quotes?.length" class="answer-quotes">
        <summary>查看这段{{ isAnswer ? '回答' : '概览' }}的原文摘录 · {{ lead.quotes.length }} 条</summary>
        <blockquote v-for="(quote, i) in lead.quotes" :key="i"><span>日记原文 · {{ quote.sourceId }}</span><p>{{ quote.text }}</p></blockquote>
      </details>
    </section>

    <section v-if="result.observations?.length" class="result-section">
      <h4>记录中的观察</h4><p class="section-help">以下内容来自你写下的经历。</p>
      <div v-for="(item, i) in result.observations" :key="i" class="result-claim">
        <p>{{ item.text }}</p><details v-if="item.quotes?.length" class="answer-quotes"><summary>核对原文</summary><blockquote v-for="(quote, q) in item.quotes" :key="q"><span>日记原文 · {{ quote.sourceId }}</span><p>{{ quote.text }}</p></blockquote></details>
      </div>
    </section>
    <details v-if="result.hypotheses?.length" class="result-section result-disclosure">
      <summary>可能的解释 <span>待你核实，{{ result.hypotheses.length }} 项</span></summary>
      <div v-for="(item, i) in result.hypotheses" :key="i" class="result-claim"><p>{{ item.text }}</p><details v-if="item.quotes?.length" class="answer-quotes"><summary>核对原文</summary><blockquote v-for="(quote, q) in item.quotes" :key="q"><span>日记原文 · {{ quote.sourceId }}</span><p>{{ quote.text }}</p></blockquote></details></div>
    </details>
    <section v-if="result.suggestions?.length" class="result-section">
      <h4>你可以尝试</h4><p class="section-help">这些是建议，是否采用由你决定。</p>
      <div v-for="(item, i) in result.suggestions" :key="i" class="result-suggestion">
        <h5>{{ item.action }}</h5><span v-if="item.general" class="suggestion-kind">一般性建议</span><p>{{ item.reason }}</p>
        <dl><div><dt>预计投入</dt><dd>{{ item.effort }}</dd></div><div><dt>建议回看</dt><dd>{{ item.reviewAfter }}</dd></div></dl>
        <small>依据：{{ item.evidenceIds.join('、') || '通用建议，不代表个人事实' }}</small>
        <el-button v-if="canAdopt" :disabled="blocked || busy" @click="emit('adopt', { reportId: report.id, index: Number(i), suggestion: item })">编辑后采纳为行动 <ArrowRight aria-hidden="true" /></el-button>
      </div>
    </section>
    <details class="result-section result-disclosure">
      <summary>引用来源 <span>{{ report.sources.length }} 条，点击可核对原文</span></summary>
      <div class="ai-actions source-links"><el-button v-for="source in report.sources" :key="source.sourceId" :disabled="busy" @click="emit('locate', source)">{{ source.sourceId }} · {{ source.title || '无标题日记' }}</el-button></div>
    </details>
    <details class="result-section result-disclosure limitations" :open="result.insufficientEvidence">
      <summary>阅读这份结果时需要注意</summary>
      <ul><li v-for="(item, i) in result.limitations" :key="i">{{ item }}</li></ul>
    </details>
    <p class="answer-footnote">AI 生成内容仅供回顾。引用可核对，解释仍需你判断。</p>
  </article>
</template>

<style scoped>
.ai-result{background:var(--diary-surface,var(--el-bg-color));border:1px solid var(--diary-line,var(--el-border-color));border-radius:16px;overflow:hidden;color:var(--diary-text,var(--el-text-color-primary));font-size:15px;line-height:1.85}
.answer-question{padding:22px 30px;background:var(--diary-tint,var(--el-fill-color-light));border-bottom:1px solid var(--diary-line,var(--el-border-color))}.result-label{font-weight:600;font-size:12px;color:var(--diary-muted,var(--el-text-color-regular))}.answer-question p{margin:6px 0 0;white-space:pre-wrap;font-size:16px}.history-context p{font-size:13px;color:var(--diary-muted)}
.answer-heading{display:flex;align-items:center;gap:12px;padding:28px 30px 12px}.answer-symbol{display:grid;place-items:center;flex:none;width:38px;height:38px;background:var(--brand-accent-soft,var(--el-fill-color-light));border-radius:11px;color:var(--diary-accent)}.answer-symbol svg{width:22px;height:22px}.answer-heading h3{font-size:21px;line-height:1.4;margin:0}.answer-heading p{font-size:12px;color:var(--diary-muted);margin:3px 0 0}.result-state{font-size:12px;margin-left:auto;color:var(--diary-muted);white-space:nowrap}
.answer-main{padding:2px 30px 24px}.answer-main>p{font-size:17px;line-height:1.95;white-space:pre-wrap;margin:12px 0 18px;overflow-wrap:anywhere}.insufficient>p{font-size:16px}.answer-quotes{font-size:13px;color:var(--diary-muted);margin-top:14px}.answer-quotes summary{width:fit-content;cursor:pointer}.answer-quotes blockquote{margin:12px 0 0;padding:12px 16px;border-left:3px solid var(--diary-line);background:var(--diary-tint)}blockquote span{font-size:11px;font-weight:600}blockquote p{white-space:pre-wrap;margin:6px 0 0;overflow-wrap:anywhere}
.result-section{padding:22px 30px;border-top:1px solid var(--diary-line)}.result-section h4{font-size:16px;margin:0 0 5px}.section-help{font-size:13px;color:var(--diary-muted);margin:0 0 16px}.result-claim+.result-claim{margin-top:20px}.result-claim>p{white-space:pre-wrap;margin:0;overflow-wrap:anywhere}.result-disclosure>summary{cursor:pointer;font-size:14px;font-weight:600}.result-disclosure>summary span{font-weight:400;font-size:12px;color:var(--diary-muted);margin-left:8px}.result-disclosure[open]>summary{margin-bottom:16px}.result-suggestion{margin-top:18px}.result-suggestion+.result-suggestion{padding-top:20px;border-top:1px dashed var(--diary-line)}.result-suggestion h5{font-size:15px;margin:0;white-space:pre-wrap}.result-suggestion p{margin:8px 0;white-space:pre-wrap}.suggestion-kind{font-size:12px;color:var(--diary-muted)}.result-suggestion dl{display:grid;grid-template-columns:1fr 1fr;gap:14px;margin:14px 0}.result-suggestion dt,.result-suggestion small{font-size:12px;color:var(--diary-muted)}.result-suggestion dd{margin:2px 0 0;font-size:13px}.result-suggestion .el-button{display:flex;margin:14px 0 0;max-width:100%}.result-suggestion svg{width:15px;margin-left:8px}.source-links{display:flex;gap:8px;flex-wrap:wrap}.source-links .el-button{height:auto;min-height:34px;white-space:normal;max-width:100%;margin:0;text-align:left}.source-links :deep(.el-button>span){white-space:normal;overflow-wrap:anywhere}.limitations{font-size:13px}.limitations ul{margin:0;padding-left:20px}.answer-footnote{padding:16px 30px;background:var(--diary-tint);font-size:12px;color:var(--diary-muted);margin:0}.result-notice{margin:12px 30px;padding:10px 12px;background:var(--diary-tint);font-size:13px;line-height:1.7}
summary:focus-visible{outline:2px solid var(--diary-accent);outline-offset:5px;border-radius:3px}
@media(max-width:760px){.answer-heading{padding:22px 20px 8px}.answer-main,.result-section{padding:18px 20px}.answer-question{padding:18px 20px}.answer-main>p{font-size:16px;margin-top:0}.answer-heading h3{font-size:19px}.answer-heading p{max-width:200px;overflow-wrap:anywhere}.answer-footnote{padding:14px 20px}.result-state{align-self:flex-start}.result-disclosure>summary span{display:block;margin:4px 0 0}.result-suggestion dl{grid-template-columns:1fr}.result-notice{margin:12px 20px}}
</style>
