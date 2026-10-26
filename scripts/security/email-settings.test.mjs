import assert from 'node:assert/strict'
import { test } from 'node:test'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'

const adminRequire = createRequire(new URL('../../blog-frontend/luyu-admin/package.json', import.meta.url))
const appRequire = createRequire(new URL('../../blog-frontend/luyu-blog/package.json', import.meta.url))
const { JSDOM } = adminRequire('jsdom')
const dom = new JSDOM('<html><body></body></html>', { url: 'https://example.invalid/account' })
for (const key of ['window', 'document', 'Element', 'HTMLElement', 'SVGElement', 'Node', 'navigator', 'sessionStorage']) {
  Object.defineProperty(globalThis, key, { configurable: true, value: dom.window[key] })
}
const Vue = appRequire('vue')
const { parse, compileScript } = appRequire('vue/compiler-sfc')
const { build } = createRequire(appRequire.resolve('vite/package.json'))('esbuild')
const filename = fileURLToPath(new URL('../../blog-frontend/luyu-blog/src/views/Setting/EmailSecuritySettings.vue', import.meta.url))
const source = readFileSync(filename, 'utf8')
const { descriptor } = parse(source.replace('<script setup lang="ts">', '<script setup lang="ts">\nimport { reactive, ref, computed, onMounted, onBeforeUnmount, watch } from "vue";'), { filename })
const compiled = compileScript(descriptor, { id: 'qa-email-settings', inlineTemplate: true }).content
const store = Vue.reactive({ userInfo: { username: 'qa', registerType: 0 }, token: 'qa' })
const messages = [], requests = []
let responder
const http = { post: async (...args) => { requests.push(args); return responder(...args) } }
let deliveryResponder = async () => {}
const deliveryRequests = []
globalThis.__emailSettingsFixture = { store, http, messages, confirmEmailDelivery: (...args) => {
  deliveryRequests.push(args); return deliveryResponder(...args)
} }
const modules = {
  'element-plus': 'export const ElMessage = { success: m => globalThis.__emailSettingsFixture.messages.push(m), error: m => globalThis.__emailSettingsFixture.messages.push(m) };',
  '@/utils/http': 'export default globalThis.__emailSettingsFixture.http;',
  '@/store/modules/user': 'export default () => globalThis.__emailSettingsFixture.store;',
  '@/utils/auth': 'export const REMOVE_TOKEN = () => {};',
  '@/apis/email': `export class EmailDeliveryError extends Error { constructor(message, status) { super(message); this.status = status } }
    export const confirmEmailDelivery = (...args) => globalThis.__emailSettingsFixture.confirmEmailDelivery(...args);
    globalThis.__emailSettingsFixture.EmailDeliveryError = EmailDeliveryError;`,
}
const bundle = await build({ stdin: { contents: compiled, loader: 'ts', resolveDir: fileURLToPath(new URL('../../blog-frontend/luyu-blog', import.meta.url)) },
  bundle: true, write: false, platform: 'node', format: 'cjs', external: ['vue'], plugins: [{ name: 'qa-only-boundaries', setup(build) {
    build.onResolve({ filter: /^(element-plus|@\/)/ }, args => ({ path: args.path, namespace: 'qa' }))
    build.onLoad({ filter: /.*/, namespace: 'qa' }, args => ({ contents: modules[args.path], loader: 'js' }))
  } }] })
const module = { exports: {} }
new Function('require', 'module', 'exports', bundle.outputFiles[0].text)(appRequire, module, module.exports)
const component = module.exports.default
const Input = { props: ['modelValue', 'disabled', 'type'], emits: ['update:modelValue'], setup: (props, { emit }) => () => Vue.h('input', { value: props.modelValue, disabled: props.disabled, type: props.type, onInput: e => emit('update:modelValue', e.target.value) }) }
const Button = { props: ['disabled', 'loading'], emits: ['click'], setup: (props, { slots, emit }) => () => Vue.h('button', { disabled: props.disabled || props.loading, onClick: () => emit('click') }, slots.default?.()) }
const Container = { setup: (_, { slots }) => () => Vue.h('div', slots.default?.()) }
async function flush() { await new Promise(resolve => setImmediate(resolve)); await Vue.nextTick() }
function mount(type = 0) {
  store.userInfo = { username: 'qa', registerType: type }; requests.length = 0; messages.length = 0; deliveryRequests.length = 0
  const host = document.createElement('div'); document.body.append(host)
  const app = Vue.createApp(component)
  app.component('el-input', Input); app.component('el-button', Button)
  for (const name of ['el-form', 'el-form-item', 'el-alert']) app.component(name, Container)
  app.mount(host)
  return { host, cleanup: () => { app.unmount(); host.remove(); sessionStorage.clear() } }
}
function input(host, index, value) { const el = host.querySelectorAll('input')[index]; el.value = value; el.dispatchEvent(new window.Event('input', { bubbles: true })) }
function click(host, text) { const el = [...host.querySelectorAll('button')].find(el => el.textContent.includes(text)); assert.ok(el); el.click() }

test('password and mailbox codes are not persisted; challenge locks target and failed verification remains retryable', async () => {
  responder = async url => url.endsWith('/start') ? { code: 200, data: { challengeId: 'a'.repeat(64), expiresIn: 300, oldEmailRequired: true, maskedOldEmail: 'q***@example.invalid', provider: 0 } } : { code: 1005, msg: 'invalid proof' }
  const { host, cleanup } = mount()
  try {
    input(host, 0, 'new@example.invalid'); input(host, 1, 'SYNTHETIC_PRIVATE_PASSWORD'); await flush()
    click(host, '发起'); await flush()
    assert.equal(requests[0][0], '/user/auth/email-change/start')
    const saved = sessionStorage.getItem('email-change-challenge-v1'); assert.ok(saved); assert.ok(!saved.includes('SYNTHETIC_PRIVATE_PASSWORD'))
    assert.equal(host.querySelector('input').disabled, true)
    input(host, 1, '111111'); input(host, 2, '222222'); await flush(); click(host, '完成'); await flush()
    assert.deepEqual(requests[1][1], { challengeId: 'a'.repeat(64), email: 'new@example.invalid', code: '222222', oldCode: '111111' })
    assert.ok(!saved.includes('111111')); assert.ok(!saved.includes('222222'))
    assert.ok(host.textContent.includes('完成验证')); assert.equal(store.token, 'qa')
    assert.equal(host.querySelectorAll('input')[1].value, ''); assert.equal(host.querySelectorAll('input')[2].value, '')
  } finally { cleanup() }
})
test('third-party flow has no fake password and rejects unexpected provider navigation', async () => {
  responder = async url => url.includes('email-change') ? { code: 200, data: { challengeId: 'b'.repeat(64), expiresIn: 300, oldEmailRequired: false, maskedOldEmail: '', provider: 2 } } : { code: 200, data: 'https://evil.invalid/auth' }
  const { host, cleanup } = mount(2)
  try {
    assert.equal(host.querySelector('input[type=password]'), null)
    input(host, 0, 'new@example.invalid'); await flush(); click(host, '发起'); await flush()
    click(host, 'GitHub'); await flush()
    assert.equal(requests[1][0], '/oauth/reauth/start'); assert.ok(messages.includes('身份验证无法发起，请重新尝试'))
    assert.equal(window.location.hostname, 'example.invalid')
  } finally { cleanup() }
})
test('expired saved challenge cannot restore verification controls', async () => {
  sessionStorage.setItem('email-change-challenge-v1', JSON.stringify({ challengeId: 'c'.repeat(64), expiresAt: 1 }))
  const { host, cleanup } = mount()
  try { await flush(); assert.equal(sessionStorage.getItem('email-change-challenge-v1'), null); assert.ok(host.textContent.includes('发起安全验证')) }
  finally { cleanup() }
})

test('enqueue acceptance stays pending until delivery confirms success', async () => {
  let delivered
  deliveryResponder = () => new Promise(resolve => { delivered = resolve })
  responder = async () => ({ code: 200, data: { challengeId: 'd'.repeat(64), taskId: 'e'.repeat(64), expiresIn: 300, oldEmailRequired: false, provider: 0 } })
  const { host, cleanup } = mount()
  try {
    input(host, 0, 'new@example.invalid'); await flush(); click(host, '发起'); await flush()
    assert.equal(deliveryRequests[0][0], 'e'.repeat(64)); assert.equal(messages.length, 0)
    assert.ok(host.textContent.includes('正在发送'))
    delivered(); await flush(); assert.ok(messages.some(m => m.includes('验证码已发送')))
  } finally { cleanup(); deliveryResponder = async () => {} }
})

test('delivery failure clears invalid challenge and never announces success', async () => {
  deliveryResponder = async () => { throw new globalThis.__emailSettingsFixture.EmailDeliveryError('SMTP failed', 'FAILED') }
  responder = async () => ({ code: 200, data: { challengeId: 'd'.repeat(64), taskId: 'e'.repeat(64), expiresIn: 300, oldEmailRequired: false, provider: 0 } })
  const { host, cleanup } = mount()
  try {
    input(host, 0, 'new@example.invalid'); await flush(); click(host, '发起'); await flush()
    assert.deepEqual(messages, ['SMTP failed']); assert.equal(sessionStorage.getItem('email-change-challenge-v1'), null)
    assert.ok(host.textContent.includes('发起安全验证'))
  } finally { cleanup(); deliveryResponder = async () => {} }
})

test('unmount aborts pending delivery without success or error notification', async () => {
  let signal
  deliveryResponder = (_, passedSignal) => new Promise((_, reject) => {
    signal = passedSignal; signal.addEventListener('abort', () => reject(new Error('cancelled')), { once: true })
  })
  responder = async () => ({ code: 200, data: { challengeId: 'd'.repeat(64), taskId: 'e'.repeat(64), expiresIn: 300, oldEmailRequired: false, provider: 0 } })
  const { host, cleanup } = mount()
  input(host, 0, 'new@example.invalid'); await flush(); click(host, '发起'); await flush()
  cleanup(); await flush(); assert.equal(signal.aborted, true); assert.deepEqual(messages, [])
  deliveryResponder = async () => {}
})
