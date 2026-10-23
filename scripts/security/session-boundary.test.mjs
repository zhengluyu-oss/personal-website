import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
import { dirname } from 'node:path'
import { execFileSync } from 'node:child_process'

const root = fileURLToPath(new URL('../../blog-frontend/luyu-blog/', import.meta.url))
const req = createRequire(`${root}/package.json`)
const adminReq = createRequire(new URL('../../blog-frontend/luyu-admin/package.json', import.meta.url))
const { JSDOM } = adminReq('jsdom')
const dom = new JSDOM('', { url: 'https://blog.example/' })
globalThis.localStorage = dom.window.localStorage
globalThis.sessionStorage = dom.window.sessionStorage
const redirects = []
globalThis.window = { location: { href: 'https://blog.example/', replace: value => redirects.push(value) } }
const { createPinia } = req('pinia')
const { AxiosError } = req('axios')
const { build } = createRequire(req.resolve('vite/package.json'))('esbuild')
const fixture = { auth: undefined, store: {}, profile: async () => ({ code: 200, data: { username: 'synthetic', permissions: [] } }) }
globalThis.__sessionFixture = fixture

async function load(relative, modules = {}, external = []) {
  const filename = `${root}/${relative}`
  const source = process.env.SESSION_TEST_BASELINE && ['src/utils/http.ts', 'src/store/modules/user.ts'].includes(relative)
    ? execFileSync('git', ['show', `HEAD:blog-frontend/luyu-blog/${relative}`], { encoding: 'utf8' })
    : readFileSync(filename, 'utf8')
  const output = await build({ stdin: { contents: source, loader: 'ts', resolveDir: dirname(filename) },
    bundle: true, write: false, platform: 'node', format: 'cjs', external,
    define: { 'import.meta.env': JSON.stringify({ VITE_APP_BASE_API: '/api', VITE_MUSIC_BASE_API: '/wapi', VITE_YIYAN_API: 'https://v1.hitokoto.cn' }) },
    plugins: [{ name: 'isolated-boundaries', setup(b) {
      b.onResolve({ filter: /^(element-plus|nprogress|@)/ }, a => ({ path: a.path, namespace: 'fixture' }))
      b.onLoad({ filter: /.*/, namespace: 'fixture' }, a => {
        if (!(a.path in modules)) throw new Error(`Missing explicit fixture: ${a.path}`)
        return { loader: 'js', contents: modules[a.path] }
      })
    } }],
  })
  const mod = { exports: {} }
  new Function('require', 'module', 'exports', output.outputFiles[0].text)(req, mod, mod.exports)
  return mod.exports
}

const auth = await load('src/utils/auth.ts', {
  '@/const/Jwt': 'export const TOKEN_KEY = "Token"',
  'element-plus': 'export const ElMessage = { warning() {} }',
  '@/store/modules/user.ts': 'export default () => globalThis.__sessionFixture.store',
})
fixture.auth = auth
const authModule = 'export const GET_TOKEN = () => globalThis.__sessionFixture.auth.GET_TOKEN(); export const REMOVE_TOKEN = () => globalThis.__sessionFixture.auth.REMOVE_TOKEN();'
const storeModule = await load('src/store/modules/user.ts', {
  '@/utils/auth.ts': authModule,
  '@/apis/user': 'export const getUserInfo = () => globalThis.__sessionFixture.profile()',
}, ['vue', 'pinia'])
const { default: client } = await load('src/utils/http.ts', {
  'element-plus': 'export const ElMessage = { error() {} }',
  nprogress: 'export default { start() {}, done() {} }',
  'nprogress/nprogress.css': '',
  '@/const/Jwt': 'export const Jwt_Prefix = "Bearer "',
  '@/utils/auth.ts': authModule,
  '@/store/modules/loading.ts': 'export default () => ({ show() {}, hide() {} })',
  '@/utils/enum.ts': 'export const REQUEST_LOADING_PATH = []',
  '@/utils/request-tracker.ts': 'export const createRequestTracker = () => ({ begin: () => 1, finalize() {} })',
}, ['axios'])

function reset(remember = true) {
  localStorage.clear(); sessionStorage.clear(); redirects.length = 0
  client.defaults.headers.common = {}
  fixture.store = storeModule.default(createPinia())
  auth.SET_TOKEN('synthetic-token', '2099-01-01', remember)
  fixture.profile = async () => ({ code: 200, data: { username: 'synthetic', permissions: [] } })
  return fixture.store
}
function adapter(data = { code: 200 }) {
  const captured = []
  client.defaults.adapter = async config => {
    captured.push(config)
    return { data, status: 200, statusText: 'OK', headers: {}, config }
  }
  return captured
}

for (const url of ['/user/auth/info', 'https://blog.example/api/user/auth/info']) {
  test(`trusted API carries the current token: ${url}`, async () => {
    reset(); const captured = adapter()
    await client.get(url)
    assert.equal(captured[0].headers.get('Authorization'), 'Bearer synthetic-token')
  })
}
for (const url of ['https://v1.hitokoto.cn/?c=a', '//external.example/api/user',
  'https://blog.example.attacker.invalid/api/user', 'https://blog.example/admin/index.html',
  'https://blog.example/apix/user', 'https://blog.example/api/../admin/',
  'https://blog.example/api%2fuser', 'https://user@blog.example/api/user']) {
  test(`untrusted destination removes explicit and inherited credentials: ${url}`, async () => {
    reset(); const captured = adapter({ code: 1002 })
    client.defaults.headers.common.authorization = 'Bearer stale-default'
    await client.get(url, { headers: { AUTHORIZATION: 'Bearer stale-explicit' }, withCredentials: true })
    assert.equal(captured[0].headers.has('Authorization'), false)
    assert.equal(captured[0].withCredentials, false)
    assert.equal(auth.GET_TOKEN(), 'synthetic-token')
    assert.deepEqual(redirects, [])
  })
}

test('music boundary excludes identity without confusing a similar API path', async () => {
  reset(); const captured = adapter()
  await client.get('/wapi/song')
  assert.equal(captured[0].headers.has('Authorization'), false)
  await client.get('/wapix/song')
  assert.equal(captured[1].headers.get('Authorization'), 'Bearer synthetic-token')
})

test('trusted business authentication failure clears only the frontend session', async () => {
  reset(); adapter({ code: 1002 })
  localStorage.setItem('admin-token', 'synthetic-admin')
  await client.get('/user/auth/info')
  assert.equal(auth.GET_TOKEN(), null)
  assert.equal(localStorage.getItem('admin-token'), 'synthetic-admin')
  assert.deepEqual(redirects, ['/auth/login'])
})

test('old authentication failure cannot sign out a newer token', async () => {
  reset()
  client.defaults.adapter = async config => {
    auth.SET_TOKEN('new-synthetic-token', '2099-01-01', false)
    return { data: { code: 1002 }, status: 200, statusText: 'OK', headers: {}, config }
  }
  await client.get('/user/auth/info')
  assert.equal(auth.GET_TOKEN(), 'new-synthetic-token')
  assert.deepEqual(redirects, [])
})

for (const [label, error] of [
  ['network', new Error('Network Error')], ['timeout', new Error('timeout')],
  ['cancelled', new AxiosError('cancelled', 'ERR_CANCELED')],
  ['server error', { response: { status: 503, data: { code: 1002 } } }],
  ['permission denied', { response: { status: 403 } }],
]) {
  test(`profile ${label} preserves storage and can recover safely`, async () => {
    const store = reset(false)
    await store.getInfo()
    fixture.profile = async () => { throw error }
    await assert.rejects(store.getInfo())
    assert.equal(auth.GET_TOKEN(), 'synthetic-token')
    assert.equal(localStorage.getItem('Token'), null)
    assert.ok(sessionStorage.getItem('Token'))
    assert.equal(store.token, 'synthetic-token')
    assert.equal(store.userInfo, undefined)
    assert.equal(store.sessionState, 'unavailable')
    fixture.profile = async () => ({ code: 200, data: { username: 'recovered', permissions: ['synthetic-permission'] } })
    await store.getInfo()
    assert.equal(store.sessionState, 'ready')
    assert.equal(store.userInfo.username, 'recovered')
  })
}

for (const [label, value] of [['business invalid', { code: 1002 }], ['HTTP invalid', { response: { status: 401 } }]]) {
  test(`profile ${label} clears invalid credentials`, async () => {
    const store = reset()
    fixture.profile = async () => { throw value }
    await assert.rejects(store.getInfo())
    assert.equal(auth.GET_TOKEN(), null)
    assert.equal(store.token, null)
    assert.equal(store.userInfo, undefined)
    assert.equal(store.sessionState, 'anonymous')
  })
}

for (const raw of ['broken json', '{"token":"synthetic","expire":"2000-01-01"}', '{"expire":"2099-01-01"}']) {
  test(`invalid stored credential is safely cleared: ${raw}`, async () => {
    reset(); localStorage.setItem('Token', raw)
    let requested = false
    fixture.profile = async () => { requested = true; return { code: 200 } }
    await fixture.store.getInfo()
    assert.equal(auth.GET_TOKEN(), null)
    assert.equal(requested, false)
    assert.equal(fixture.store.sessionState, 'anonymous')
  })
}

test('profile result arriving after logout cannot restore stale permissions', async () => {
  const store = reset()
  let complete
  fixture.profile = () => new Promise(resolve => { complete = resolve })
  const pending = store.getInfo()
  store.clearSession()
  complete({ code: 200, data: { username: 'stale', permissions: ['old-privilege'] } })
  await assert.rejects(pending)
  assert.equal(store.userInfo, undefined)
  assert.equal(store.token, null)
  assert.equal(store.sessionState, 'anonymous')
})

test('older profile request cannot replace a new session', async () => {
  const store = reset()
  let complete
  fixture.profile = () => new Promise(resolve => { complete = resolve })
  const pending = store.getInfo()
  auth.SET_TOKEN('new-session', '2099-01-01', false)
  fixture.profile = async () => ({ code: 200, data: { username: 'new-user', permissions: [] } })
  await store.getInfo()
  complete({ code: 200, data: { username: 'old-user', permissions: ['old-privilege'] } })
  await assert.rejects(pending)
  assert.equal(store.userInfo.username, 'new-user')
  assert.equal(store.token, 'new-session')
})
