import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'

const req = createRequire(new URL('../../blog-frontend/kuailemao-blog/package.json', import.meta.url))
const adminReq = createRequire(new URL('../../blog-frontend/kuailemao-admin/package.json', import.meta.url))
const { JSDOM } = adminReq('jsdom')
const dom = new JSDOM('<html><body></body></html>', { url: 'https://blog.example' })
for (const key of ['window', 'document', 'Element', 'HTMLElement', 'SVGElement', 'Node', 'navigator', 'localStorage', 'sessionStorage'])
  Object.defineProperty(globalThis, key, { configurable: true, value: dom.window[key] })
const Vue = req('vue')
const { parse, compileScript } = req('vue/compiler-sfc')
const { build } = createRequire(req.resolve('vite/package.json'))('esbuild')
const root = fileURLToPath(new URL('../../blog-frontend/kuailemao-blog/', import.meta.url))
const authFixture = { store: Vue.reactive({ token: null }), warnings: [] }
globalThis.__authFixture = authFixture
const authBundle = await build({ entryPoints: [`${root}/src/utils/auth.ts`], bundle: true, write: false,
  platform: 'node', format: 'cjs', plugins: [{ name: 'auth-fixtures', setup(b) {
    b.onResolve({ filter: /^(element-plus|@)/ }, args => ({ path: args.path, namespace: 'auth-fixture' }))
    b.onLoad({ filter: /.*/, namespace: 'auth-fixture' }, args => ({ loader: 'js', contents: args.path === 'element-plus'
      ? 'export const ElMessage = { warning: message => globalThis.__authFixture.warnings.push(message) };'
      : args.path === '@/const/Jwt' ? 'export const TOKEN_KEY = "Token";'
        : 'export default () => globalThis.__authFixture.store;' }))
  } }] })
const authModule = { exports: {} }
new Function('require', 'module', 'exports', authBundle.outputFiles[0].text)(req, authModule, authModule.exports)
const { GET_TOKEN, SET_TOKEN, REMOVE_TOKEN } = authModule.exports
const events = [], requests = []
let loginResult, verifyResult, resolveInfo
const store = Vue.reactive({ userInfo: undefined, token: null, getInfo: async () => {
  await new Promise(resolve => { resolveInfo = resolve })
  store.userInfo = { username: 'test' }; events.push('profile')
} })
globalThis.__loginFixture = { Vue, store, events,
  login: async () => loginResult,
  http: { post: async (url, data) => { requests.push([url, data]); return verifyResult } },
}
const modules = {
  '@element-plus/icons-vue': 'export const User = {}; export const Lock = {};',
  '@/apis/user': 'export const login = () => globalThis.__loginFixture.login();',
  '@/apis/email': 'export const confirmEmailDelivery = async () => {};',
  '@/router': 'export default { push: async () => globalThis.__loginFixture.events.push("navigate") };',
  '@/utils/auth': 'export const SET_TOKEN = (token) => { globalThis.__loginFixture.store.token = token; globalThis.__loginFixture.events.push("token") }; export const REMOVE_TOKEN = () => globalThis.__loginFixture.events.push("remove");',
  '@/utils/http': 'export default globalThis.__loginFixture.http;',
  '@/store/modules/user.ts': 'export default () => globalThis.__loginFixture.store;',
  'element-plus': 'export const ElMessage = Object.fromEntries(["success","error","warning"].map(k => [k, m => globalThis.__loginFixture.events.push(m)]));',
}
const filename = `${root}/src/views/Welcome/Login/index.vue`
const { descriptor } = parse(readFileSync(filename, 'utf8').replace('import.meta.env', '{}'), { filename })
const compiled = compileScript(descriptor, { id: 'login-test', inlineTemplate: true }).content
const bundle = await build({ stdin: { contents: compiled, loader: 'ts', resolveDir: root }, bundle: true, write: false,
  platform: 'node', format: 'cjs', external: ['vue'], plugins: [{ name: 'fixtures', setup(b) {
    b.onResolve({ filter: /^(element-plus|@)/ }, args => ({ path: args.path, namespace: 'fixture' }))
    b.onLoad({ filter: /.*/, namespace: 'fixture' }, args => ({ contents: modules[args.path], loader: 'js' }))
  } }] })
const mod = { exports: {} }
new Function('require', 'module', 'exports', bundle.outputFiles[0].text)(req, mod, mod.exports)
const Container = { setup: (_, { slots }) => () => Vue.h('div', slots.default?.()) }
const Form = { setup: (_, { slots, expose }) => { expose({ validate: async () => true }); return () => Vue.h('div', slots.default?.()) } }
const Input = { props: ['modelValue'], emits: ['update:modelValue'], setup: (p, { emit }) => () => Vue.h('input', { value: p.modelValue, onInput: e => emit('update:modelValue', e.target.value) }) }
const Button = { props: ['disabled', 'loading'], emits: ['click'], setup: (p, { emit, slots }) => () => Vue.h('button', { disabled: p.disabled || p.loading, onClick: () => emit('click') }, slots.default?.()) }
async function flush() { await new Promise(r => setImmediate(r)); await Vue.nextTick() }
function mount() {
  events.length = 0; requests.length = 0; store.userInfo = undefined; store.token = null; resolveInfo = undefined
  const host = document.createElement('div'); document.body.append(host)
  const app = Vue.createApp(mod.exports.default)
  app.config.warnHandler = () => {}
  app.component('el-form', Form); app.component('el-input', Input); app.component('el-button', Button)
  for (const name of ['el-form-item', 'el-row', 'el-col', 'el-link', 'el-divider', 'el-icon', 'el-checkbox', 'svg-icon']) app.component(name, Container)
  app.mount(host)
  return { host, cleanup: () => { app.unmount(); host.remove() } }
}
function click(host, label) { [...host.querySelectorAll('button')].find(b => b.textContent === label).click() }

test('administrator challenge never becomes a false login; verification waits for profile before navigation', async () => {
  loginResult = { code: 200, data: { secondFactorRequired: true, challengeId: 'test-challenge', maskedEmail: 't***@example.invalid', resendAfter: 0 } }
  verifyResult = { code: 200, data: { token: 'synthetic', expire: '2099-01-01' } }
  const { host, cleanup } = mount()
  try {
    click(host, '立即登录'); await flush()
    assert.ok(host.textContent.includes('请完成邮箱验证'))
    assert.deepEqual(events, []); assert.equal(store.token, null)
    const input = host.querySelector('input'); input.value = '123456'; input.dispatchEvent(new window.Event('input')); await flush()
    click(host, '验证并登录'); await flush()
    assert.deepEqual(requests[0], ['/user/admin-login/verify', { challengeId: 'test-challenge', code: '123456' }])
    assert.deepEqual(events, ['token']); assert.equal(store.userInfo, undefined)
    resolveInfo(); await flush()
    assert.deepEqual(events, ['token', 'profile', '登录成功', 'navigate'])
  } finally { cleanup() }
})
test('normal login loads profile, while missing token never reports success', async () => {
  for (const data of [{ token: 'synthetic', expire: '2099-01-01' }, {}]) {
    loginResult = { code: 200, data }
    const { host, cleanup } = mount()
    try {
      click(host, '立即登录'); await flush()
      if (data.token) { assert.deepEqual(events, ['token']); resolveInfo(); await flush(); assert.equal(events.at(-1), 'navigate') }
      else { assert.equal(store.token, null); assert.ok(!events.includes('登录成功')); assert.ok(!events.includes('navigate')) }
    } finally { cleanup() }
  }
})

test('real user store returns only after profile has updated and exposes failures to callers', async () => {
  let respond
  globalThis.__userInfoRequest = () => new Promise(resolve => { respond = resolve })
  const source = 'import { shallowRef } from "vue";\n' + readFileSync(`${root}/src/store/modules/user.ts`, 'utf8')
  const output = await build({ stdin: { contents: source, loader: 'ts', resolveDir: root }, bundle: true, write: false,
    platform: 'node', format: 'cjs', external: ['vue', 'pinia'], plugins: [{ name: 'user-boundaries', setup(b) {
      b.onResolve({ filter: /^@\// }, args => ({ path: args.path, namespace: 'fixture' }))
      b.onLoad({ filter: /.*/, namespace: 'fixture' }, args => ({ loader: 'js', contents: args.path.includes('auth')
        ? 'export const GET_TOKEN = () => "synthetic"; export const REMOVE_TOKEN = () => { globalThis.__authFixture.cleared = true };'
        : 'export const getUserInfo = () => globalThis.__userInfoRequest();' }))
    } }] })
  const userModule = { exports: {} }
  new Function('require', 'module', 'exports', output.outputFiles[0].text)(req, userModule, userModule.exports)
  const { createPinia } = req('pinia')
  const user = userModule.exports.default(createPinia())
  let finished = false
  const pending = user.getInfo().then(() => { finished = true })
  await flush(); assert.equal(finished, false); assert.equal(user.userInfo, undefined)
  respond({ code: 200, data: { username: 'test' } }); await pending
  assert.equal(user.userInfo.username, 'test'); assert.equal(finished, true)
  const failed = user.getInfo()
  respond({ code: 1002, msg: 'not authenticated' })
  await assert.rejects(failed, /not authenticated/)
  assert.equal(user.token, null)
  assert.equal(user.userInfo, undefined)
  assert.equal(globalThis.__authFixture.cleared, true)
})

test('frontend token storage validates data and replaces stale storage when login preference changes', () => {
  localStorage.clear(); sessionStorage.clear(); authFixture.warnings.length = 0
  localStorage.setItem('Token', 'broken-json')
  assert.equal(GET_TOKEN(), null)
  assert.equal(localStorage.getItem('Token'), null)

  localStorage.setItem('Token', JSON.stringify({ token: 'expired', expire: '2000-01-01T00:00:00Z' }))
  assert.equal(GET_TOKEN(), null)
  assert.equal(authFixture.warnings.length, 1)

  localStorage.setItem('Token', JSON.stringify({ token: 'old-token', expire: '2099-01-01T00:00:00Z' }))
  SET_TOKEN('session-token', '2099-01-01T00:00:00Z', false)
  assert.equal(localStorage.getItem('Token'), null)
  assert.equal(sessionStorage.getItem('Token'), JSON.stringify({ token: 'session-token', expire: '2099-01-01T00:00:00Z' }))
  assert.equal(GET_TOKEN(), 'session-token')
  assert.equal(authFixture.store.token, 'session-token')

  SET_TOKEN('remembered-token', '2099-01-01T00:00:00Z', true)
  assert.equal(sessionStorage.getItem('Token'), null)
  assert.equal(GET_TOKEN(), 'remembered-token')
  REMOVE_TOKEN()
  assert.equal(GET_TOKEN(), null)
})
