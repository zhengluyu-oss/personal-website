import test from 'node:test'
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const req = createRequire(new URL('../../blog-frontend/luyu-blog/package.json', import.meta.url))
const admin = createRequire(new URL('../../blog-frontend/luyu-admin/package.json', import.meta.url))
const { JSDOM } = admin('jsdom')
const dom = new JSDOM('<html><body></body></html>', { url: 'https://fixture.invalid/knowledge' })
for (const key of ['window', 'document', 'Document', 'Element', 'HTMLElement', 'SVGElement', 'Node', 'navigator'])
  Object.defineProperty(globalThis, key, { configurable: true, value: dom.window[key] })
const Vue = req('vue')
const { parse, compileScript } = req('vue/compiler-sfc')
const { build } = createRequire(req.resolve('vite/package.json'))('esbuild')
const user = Vue.reactive({ token: 'owner-token' })
const calls = []
const id = '11111111-1111-4111-8111-111111111111'
const entry = { id, revision: 2, content: { kind: 'NOTE', title: '已有笔记', body: '可阅读的内容', sourceUrl: '', categoryId: null, tagIds: [] }, fileVersion: null, updatedAt: '2026-10-20T00:00:00Z', deletedAt: null }
globalThis.__knowledgeReaderFixture = { user, request: async (method, path, data) => {
  calls.push({ method, path, data })
  if (path === '/view/capabilities') return { allowed: true, available: true, canManage: true, filesAvailable: true, message: 'ready' }
  if (path === '/view/search') return { records: [{ entry, excerpt: '命中正文', page: null, fromLine: null, toLine: null }], total: 1, page: 1, pageSize: 20 }
  if (path === '/view/labels') return []
  if (path === '/view/entries/' + id) return structuredClone(entry)
  throw new Error('Unexpected request ' + method + ' ' + path)
} }
const root = fileURLToPath(new URL('../../blog-frontend/luyu-blog/', import.meta.url))
const filename = root + '/src/views/Knowledge/Workspace.vue'
const { descriptor } = parse(readFileSync(filename, 'utf8'), { filename })
const source = compileScript(descriptor, { id: 'knowledge-reader-fixture', inlineTemplate: true }).content
const built = await build({ stdin: { contents: source, loader: 'ts', resolveDir: root }, bundle: true, write: false, platform: 'node', format: 'cjs', external: ['vue'], plugins: [{ name: 'fixture', setup(builder) {
  builder.onResolve({ filter: /^(md-editor-v3|@\/|\.\/Connections\.vue)/ }, args => ({ path: args.path, namespace: 'fixture' }))
  builder.onLoad({ filter: /.*/, namespace: 'fixture' }, args => ({ loader: 'js', contents: args.path.includes('knowledge-api')
    ? 'export const knowledgeRequest=globalThis.__knowledgeReaderFixture.request; export const knowledgeDownloadFile=async()=>{};'
    : args.path.includes('store/modules/user') ? 'export default ()=>globalThis.__knowledgeReaderFixture.user;'
      : args.path.includes('diary-preview') ? 'export const sanitizePrivateDiary=x=>x;'
        : args.path === 'md-editor-v3' ? 'export const MdPreview={props:["modelValue"],template:"<article>{{modelValue}}</article>"};'
          : args.path.includes('Connections.vue') ? 'export default {props:["entry","candidates","canManage"],template:"<section>知识关联</section>"};' : '' }))
} }] })
const module = { exports: {} }
new Function('require', 'module', 'exports', built.outputFiles[0].text)(req, module, module.exports)
async function flush() { await new Promise(resolve => setImmediate(resolve)); await Vue.nextTick() }

test('owner has read-only public knowledge view and issues no write request', async () => {
  const host = document.createElement('div'); document.body.append(host); calls.length = 0
  const app = Vue.createApp(module.exports.default); app.config.warnHandler = () => {}; app.mount(host)
  try {
    await flush()
    assert.doesNotMatch(host.textContent, /新建条目|保存条目|上传文件|分类与标签管理|整理为博客草稿/)
    assert.equal(host.querySelector('textarea'), null)
    const row = [...host.querySelectorAll('button')].find(button => button.textContent.includes('已有笔记'))
    assert.ok(row); row.click(); await flush()
    assert.match(host.textContent, /可阅读的内容/)
    assert.equal(host.querySelector('textarea'), null)
    assert.equal(calls.some(call => call.path.startsWith('/manage/') || !['GET', 'POST'].includes(call.method)), false)
    assert.equal(calls.some(call => call.method === 'POST' && call.path !== '/view/search'), false)
  } finally { app.unmount(); host.remove() }
})
