<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { MdPreview } from 'md-editor-v3'
import useUserStore from '@/store/modules/user'
import { assistantCapability, askKnowledgeAssistant, type AssistantAnswer } from '@/utils/knowledge-assistant-api'
import { sanitizePrivateDiary as sanitizeAssistantMarkdown } from '@/utils/diary-preview'

const user = useUserStore()
const available = ref(false), open = ref(false), busy = ref(false), question = ref(''), error = ref('')
const turns = ref<{ question: string; result: AssistantAnswer }[]>([])
const composer = ref<HTMLTextAreaElement>(), toggle = ref<HTMLButtonElement>()
let generation = 0, checkSequence = 0, timer: ReturnType<typeof setInterval> | undefined
function clear() { generation++; checkSequence++; available.value = false; open.value = false; busy.value = false; question.value = ''; error.value = ''; turns.value = [] }
function clearConversation() { if (busy.value) return; generation++; question.value = ''; error.value = ''; turns.value = [] }
function recentHistory() {
  const history: { question: string; answer: string }[] = []
  let remaining = 1200
  for (const turn of [...turns.value].reverse()) {
    if (history.length >= 3 || turn.result.state !== 'ANSWERED') continue
    const question = turn.question.slice(0, 500), answer = turn.result.answer.slice(0, 800)
    if (question.length + answer.length > remaining) break
    history.unshift({ question, answer }); remaining -= question.length + answer.length
  }
  return history
}
function sourceFor(turn: AssistantAnswer, id: string) { return turn.sources.find(source => source.id === id) }
async function check() {
  const token = user.token
  if (!token) { clear(); return }
  const sequence = ++checkSequence
  try {
    const result = await assistantCapability()
    if (sequence !== checkSequence || user.token !== token) return
    if (!result.available) clear()
    else available.value = true
  } catch { if (sequence === checkSequence) clear() }
}
async function togglePanel() {
  open.value = !open.value
  if (open.value) await nextTick(() => composer.value?.focus())
  else await nextTick(() => toggle.value?.focus())
}
async function ask() {
  const text = question.value.trim()
  if (!text || busy.value || !available.value || text.length > 500) return
  const sequence = generation, token = user.token
  busy.value = true; error.value = ''
  try {
    const result = await askKnowledgeAssistant(text, recentHistory())
    if (sequence !== generation || !available.value || user.token !== token) return
    turns.value = [...turns.value.slice(-3), { question: text, result }]
    question.value = ''
  } catch (failure: any) {
    if (sequence !== generation) return
    if ([401, 403].includes(failure.status)) { clear(); return }
    error.value = failure.message || '客服暂不可用，请稍后再试'
  } finally { if (sequence === generation) busy.value = false }
}
watch(() => user.token, () => { clear(); void check() }, { immediate: true, flush: 'sync' })
onMounted(() => { window.addEventListener('knowledge-access-invalid', clear); window.addEventListener('focus', check); timer = setInterval(check, 30000) })
onBeforeUnmount(() => { clear(); clearInterval(timer); window.removeEventListener('knowledge-access-invalid', clear); window.removeEventListener('focus', check) })
</script>

<template>
  <div v-if="available" class="assistant-shell">
    <section v-if="open" class="assistant-panel" aria-label="知识库客服" role="dialog" aria-modal="false">
      <header><div><strong>知识库客服</strong><small>依据站内资料回答 · 请核对来源</small></div><div class="actions"><button type="button" aria-label="清空对话" :disabled="busy" @click="clearConversation">清空</button><button type="button" aria-label="关闭客服" @click="togglePanel">×</button></div></header>
      <div class="assistant-history" aria-live="polite">
        <p v-if="!turns.length" class="hint">可以询问知识库中的内容。回答会附上可打开的原文来源。</p>
        <article v-for="(turn, index) in turns" :key="index"><p class="visitor">{{ turn.question }}</p><template v-if="turn.result.claims?.length"><div v-for="(claim, claimIndex) in turn.result.claims" :key="claimIndex" class="claim"><div class="reply assistant-markdown"><MdPreview :model-value="claim.text" theme="light" :sanitize="sanitizeAssistantMarkdown" no-mermaid no-katex no-highlight /></div><blockquote v-if="sourceFor(turn.result, claim.sourceId)" class="evidence"><div class="assistant-markdown"><MdPreview :model-value="sourceFor(turn.result, claim.sourceId)!.excerpt" theme="light" :sanitize="sanitizeAssistantMarkdown" no-mermaid no-katex no-highlight /></div><RouterLink :to="sourceFor(turn.result, claim.sourceId)!.url" @click="open = false">{{ sourceFor(turn.result, claim.sourceId)!.title }}</RouterLink><small v-if="sourceFor(turn.result, claim.sourceId)!.page"> · 第 {{ sourceFor(turn.result, claim.sourceId)!.page }} 页</small><small v-else-if="sourceFor(turn.result, claim.sourceId)!.fromLine"> · 第 {{ sourceFor(turn.result, claim.sourceId)!.fromLine }} 行</small></blockquote></div></template><p v-else class="reply">{{ turn.result.answer }}</p><ul v-if="!turn.result.claims?.length && turn.result.sources.length"><li v-for="source in turn.result.sources" :key="source.id"><RouterLink :to="source.url" @click="open = false">{{ source.title }} <span v-if="source.page">· 第 {{ source.page }} 页</span></RouterLink></li></ul></article>
        <p v-if="busy" role="status" class="hint">正在查找资料并整理回答…</p><p v-if="error" role="alert" class="error">{{ error }}</p>
      </div>
      <form @submit.prevent="ask"><label for="knowledge-assistant-question">向知识库提问</label><textarea id="knowledge-assistant-question" ref="composer" v-model="question" maxlength="500" rows="2" placeholder="例如：Redis 持久化有哪些方式？" @keydown.ctrl.enter.prevent="ask"/><div class="composer-footer"><small>{{ question.length }}/500 · Ctrl+Enter 发送</small><button type="submit" :disabled="busy || !question.trim()">发送</button></div></form>
    </section>
    <button ref="toggle" type="button" class="assistant-toggle" :aria-expanded="open" aria-label="打开知识库客服" @click="togglePanel">{{ open ? '收起' : '知识库客服' }}</button>
  </div>
</template>

<style scoped>
.assistant-shell{position:fixed;right:7rem;bottom:4rem;z-index:10000;color:var(--font-color,#24364b);font-family:inherit}
.assistant-toggle{border:1px solid #8bb9a4;background:#176a55;color:white;border-radius:999px;padding:.72rem 1rem;box-shadow:0 8px 24px #163c3240;font:inherit;font-weight:600;cursor:pointer}
.assistant-toggle:focus-visible,.assistant-panel button:focus-visible,.assistant-panel textarea:focus-visible,.assistant-panel a:focus-visible{outline:3px solid #56a78b;outline-offset:2px}
.assistant-panel{position:absolute;right:-6rem;bottom:3.8rem;width:min(390px,calc(100vw - 24px));max-height:min(620px,calc(100dvh - 9rem));min-height:min(280px,calc(100dvh - 9rem));display:flex;flex-direction:column;background:var(--card-bg,#fff);border:1px solid #ceded8;border-radius:16px;box-shadow:0 16px 45px #132e2940;overflow:hidden}
.assistant-panel header{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:14px 16px;background:#eff6f2;border-bottom:1px solid #d5e4dc}.assistant-panel header div:first-child{display:grid;gap:2px}.assistant-panel small{font-size:12px;color:#54746a}.actions{display:flex;gap:6px}.actions button{border:0;background:transparent;color:#315b4c;cursor:pointer;font:inherit;padding:5px}.assistant-history{flex:1;overflow:auto;min-height:100px;padding:16px;line-height:1.6}.assistant-history article{margin-bottom:20px}.assistant-history p{white-space:pre-wrap;overflow-wrap:anywhere}.visitor{margin-left:20px;padding:9px 12px;border-radius:12px;background:#eaf2ef}.reply{margin-right:20px}.assistant-history ul{margin:8px 0 0;padding-left:18px}.assistant-history a{color:#176a55}.hint{color:#627a70}.error{color:#a43832}.assistant-panel form{padding:12px 16px 14px;border-top:1px solid #d5e4dc}.assistant-panel label{display:block;font-size:13px;margin-bottom:6px}.assistant-panel textarea{display:block;width:100%;box-sizing:border-box;resize:vertical;border:1px solid #b7cfc4;border-radius:8px;padding:8px;font:inherit;background:var(--card-bg,#fff);color:inherit}.composer-footer{display:flex;justify-content:space-between;align-items:center;margin-top:8px}.composer-footer button{border:0;border-radius:8px;background:#176a55;color:#fff;padding:7px 16px;cursor:pointer}.composer-footer button:disabled{opacity:.5;cursor:default}
.claim{margin:12px 0}.evidence{margin:6px 20px 12px 0;padding:9px 12px;border-left:3px solid #8bb9a4;background:#f3f8f5;font-size:12px}.evidence p{margin:0 0 6px}
.assistant-markdown{min-width:0;overflow-wrap:anywhere}.assistant-markdown :deep(.md-editor-preview-wrapper){padding:0;background:transparent}.assistant-markdown :deep(.md-editor-preview){font-family:inherit;font-size:inherit;line-height:inherit;color:inherit;overflow-wrap:anywhere}.assistant-markdown :deep(pre){max-width:100%;overflow-x:auto;white-space:pre-wrap;overflow-wrap:anywhere}.assistant-markdown :deep(p){margin:0 0 6px}.assistant-markdown :deep(p:last-child){margin-bottom:0}
@media(max-width:768px){.assistant-shell{right:5.5rem;bottom:1rem}.assistant-panel{right:-5rem;max-height:calc(100dvh - 9rem);min-height:min(280px,calc(100dvh - 9rem))}}
</style>
