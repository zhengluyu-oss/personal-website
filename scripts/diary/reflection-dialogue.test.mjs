import test from 'node:test'
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
const req = createRequire(new URL('../../blog-frontend/luyu-blog/package.json', import.meta.url))
const admin = createRequire(new URL('../../blog-frontend/luyu-admin/package.json', import.meta.url))
const { JSDOM } = admin('jsdom')
const dom = new JSDOM('<html><body></body></html>', { url: 'https://fixture.invalid/diary' })
for (const key of ['window', 'document', 'Document', 'Element', 'HTMLElement', 'SVGElement', 'Node', 'navigator']) Object.defineProperty(globalThis, key, { configurable: true, value: dom.window[key] })
const Vue = req('vue'), { parse, compileScript } = req('vue/compiler-sfc'), { build } = createRequire(req.resolve('vite/package.json'))('esbuild')
const root = fileURLToPath(new URL('../../blog-frontend/luyu-blog/', import.meta.url))
const id = '11111111-1111-4111-8111-111111111111', submitted = []
const preview = { entries: 1, characters: 250, snapshotDigest: 'a'.repeat(64), records: [{ id, title: '虚构：协作与休息', coveredFrom: '2026-10-23', coveredTo: '2026-10-29' }], limitation: '仅虚构测试资料' }
let currentPreview, previewCalls
globalThis.__reflectionFixture = { api: {
  conversations: async () => ({ records: [], total: 0 }),
  preview: async scope => { previewCalls.push(scope); return currentPreview || preview },
  startConversation: async (kind, request) => { submitted.push({ kind, request: JSON.parse(JSON.stringify(request)) }); return { conversationId: 'fictional-conversation' } },
  conversation: async () => ({ conversation: { id: 'fictional-conversation', kind: 'REVIEW', firstQuestion: submitted.at(-1)?.request.question, createdAt: '2026-10-29T01:00:00Z' }, turns: [], total: 0, canFollow: true }),
} }
async function compile(name) {
  const filename = `${root}/src/views/Diary/${name}.vue`
  const { descriptor } = parse(readFileSync(filename, 'utf8'), { filename })
  const source = compileScript(descriptor, { id: name, inlineTemplate: true }).content
  const built = await build({ stdin: { contents: source, loader: 'ts', resolveDir: root }, bundle: true, write: false, platform: 'node', format: 'cjs', external: ['vue'], plugins: [{ name: 'fixture', setup(b) {
    b.onResolve({ filter: /^(element-plus|@\/|\.\/)/ }, a => ({ path: a.path, namespace: 'fixture' }))
    b.onLoad({ filter: /.*/, namespace: 'fixture' }, a => ({ loader: 'js', contents: a.path.includes('diary-ai-api') ? 'export const diaryAiApi=globalThis.__reflectionFixture.api;' : a.path.includes('diary-api') ? 'export const diaryApi={};' : a.path === 'element-plus' ? 'export const ElMessageBox={confirm:async()=>{}};' : a.path.includes('icons-vue') ? 'export const ChatDotRound={},DocumentChecked={},ArrowRight={};' : 'export default {};' }))
  } }] })
  const module = { exports: {} }
  new Function('require', 'module', 'exports', built.outputFiles[0].text)(req, module, module.exports)
  return module.exports.default
}
const Panel = await compile('DiaryConversationPanel'), Result = await compile('DiaryAiResult')
async function flush() { await new Promise(resolve => setImmediate(resolve)); await Vue.nextTick() }
async function mount(component, props) {
  const host = document.createElement('div'); document.body.append(host)
  const state = Vue.reactive(props)
  const app = Vue.createApp({ setup: () => () => Vue.h(component, state) })
  app.config.warnHandler = () => {}
  app.component('el-button', { props: ['disabled', 'loading', 'nativeType'], setup: (p, { slots, attrs }) => () => Vue.h('button', { ...attrs, disabled: p.disabled || p.loading, type: p.nativeType || 'button' }, slots.default?.()) })
  app.mount(host); await flush()
  return { host, state, close() { app.unmount(); host.remove() } }
}
function button(host, text) { const found = [...host.querySelectorAll('button')].find(b => b.textContent === text); assert.ok(found, text); return found }
function change(element, value) { element.value = value; element.dispatchEvent(new dom.window.Event(element.tagName === 'TEXTAREA' ? 'input' : 'change', { bubbles: true })) }
const panelProps = () => ({ blocked: false, entryId: id, revision: 1, choices: [{ id, title: '虚构资料' }], section: 'review', ready: true })
function reset() { submitted.length = 0; currentPreview = undefined; previewCalls = [] }

test('complete review submits the custom focus and exact previewed range, then restores its question', async () => {
  reset(); const f = await mount(Panel, panelProps())
  try {
    change(f.host.querySelector('textarea'), '请比较这些经历，提出可能被忽略的角度。')
    button(f.host, '预览完整范围').click(); await flush()
    assert.deepEqual(previewCalls, [{ entryIds: [id] }])
    assert.match(f.host.querySelector('.preview').textContent, /将完整阅读 1 篇/)
    assert.match(f.host.querySelector('.preview-records').textContent, /2026-10-23 至 2026-10-29/)
    button(f.host, '确认范围并开始复盘').click(); await flush()
    assert.equal(submitted[0].kind, 'reviews')
    assert.equal(submitted[0].request.question, '请比较这些经历，提出可能被忽略的角度。')
    assert.equal(submitted[0].request.snapshotDigest, preview.snapshotDigest)
    assert.deepEqual(submitted[0].request.scope, { entryIds: [id] })
    assert.match(f.host.querySelector('.conversation-thread h4').textContent, /请比较这些经历/)
  } finally { f.close() }
})

test('a changed range invalidates its preview and recent week is an explicit seven day range', async () => {
  reset(); const f = await mount(Panel, panelProps())
  try {
    button(f.host, '预览完整范围').click(); await flush(); assert.ok(f.host.querySelector('.preview'))
    button(f.host, '选择最近七天').click(); await flush()
    assert.equal(f.host.querySelector('.preview'), null)
    const dates = [...f.host.querySelectorAll('input[type=date]')].map(input => input.value)
    assert.equal((new Date(dates[1]) - new Date(dates[0])) / 86400000, 6)
    button(f.host, '预览完整范围').click(); await flush()
    assert.deepEqual(previewCalls.at(-1), { axis: 'covered', from: dates[0], to: dates[1] })
    assert.match(f.host.textContent, /未填写内容日期的篇目不在此范围/)
  } finally { f.close() }
})

test('new perspectives are visible, general advice is labeled, and model markup remains inert text', async () => {
  const unsafe = '<img src="https://fixture-blocked.invalid/private">'
  const statement = { text: '一种待核实解释：任务边界不清。' + unsafe, evidenceIds: ['S1'], quotes: [{ sourceId: 'S1', text: '虚构原文' }] }
  const f = await mount(Result, { report: { id: 'report', kind: 'REVIEW', model: 'fictional', createdAt: '2026-10-29T01:00:00Z', sources: [] }, result: { summary: statement, observations: [], hypotheses: [statement], suggestions: [{ action: '先确认一个完成标准', reason: '一般方法', evidenceIds: [], effort: '按实际需要', reviewAfter: '下次记录时核实', general: true }], limitations: ['仅虚构资料'], insufficientEvidence: false }, question: '请提出其他解释', busy: false, blocked: false })
  try {
    assert.match(f.host.querySelector('.answer-question').textContent, /这次复盘重点/)
    assert.equal(f.host.querySelector('details.result-section').open, true)
    assert.match(f.host.textContent, /一般性建议/)
    assert.equal(f.host.querySelectorAll('img').length, 0)
    assert.match(f.host.textContent, /fixture-blocked.invalid/)
  } finally { f.close() }
})
