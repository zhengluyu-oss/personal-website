import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import { fileURLToPath } from 'node:url'
import { parse } from 'vue/compiler-sfc'
import ts from 'typescript'

// Execute the component's actual loading functions with isolated API responses.
const source = readFileSync(fileURLToPath(new URL('./index.vue', import.meta.url)), 'utf8')
const script = parse(source).descriptor.scriptSetup.content
const ast = ts.createSourceFile('home.ts', script, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS)
const functions = ast.statements.filter(node => ts.isFunctionDeclaration(node) && ['clearDiary', 'loadDiary'].includes(node.name?.text))
assert.equal(functions.length, 2)
const code = ts.transpileModule(functions.map(node => node.getText(ast)).join('\n'), { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText

function setup(allowed, request) {
  const diary = { data: null, status: 'idle' }
  const diaryAllowed = { value: allowed }
  const diaryDenied = { value: false }
  const user = { token: 'session-a' }
  let calls = 0
  const api = async (...args) => { calls++; return request(...args) }
  const instance = new Function('diary', 'diaryAllowed', 'diaryDenied', 'user', 'diaryReadRequest',
    `let active = true; let diarySequence = 0; ${code}; return { clearDiary, loadDiary }`)(diary, diaryAllowed, diaryDenied, user, api)
  return { ...instance, diary, diaryAllowed, diaryDenied, user, get calls() { return calls } }
}

test('no permission makes no diary request or visible data', async () => {
  const state = setup(false, () => { throw new Error('unexpected request') })
  await state.loadDiary()
  assert.equal(state.calls, 0)
  assert.equal(state.diary.data, null)
})

test('authorized preview keeps only three titles and dates, never bodies', async () => {
  const state = setup(true, (_method, path) => path === '/capabilities'
    ? { allowed: true, available: true }
    : { records: Array.from({ length: 4 }, (_, index) => ({ id: String(index), title: `记录 ${index}`, createdAt: '2026-10-28', body: 'private body' })) })
  await state.loadDiary()
  assert.equal(state.calls, 2)
  assert.equal(state.diary.status, 'success')
  assert.equal(state.diary.data.length, 3)
  assert.deepEqual(Object.keys(state.diary.data[0]).sort(), ['createdAt', 'id', 'title'])
})

test('denial, unavailable service and empty list clear or limit the preview', async () => {
  const denied = setup(true, () => ({ allowed: false, available: true }))
  denied.diary.data = [{ id: 'old' }]
  await denied.loadDiary(true)
  assert.equal(denied.diaryDenied.value, true)
  assert.equal(denied.diary.data, null)
  assert.equal(denied.calls, 1)

  const unavailable = setup(true, () => ({ allowed: true, available: false }))
  await unavailable.loadDiary()
  assert.equal(unavailable.diary.status, 'error')
  assert.equal(unavailable.diary.data, null)

  const empty = setup(true, (_method, path) => path === '/capabilities' ? { allowed: true, available: true } : { records: [] })
  await empty.loadDiary()
  assert.deepEqual(empty.diary.data, [])
  assert.equal(empty.diary.status, 'success')

  const failed = setup(true, () => { throw new Error('network unavailable') })
  failed.diary.data = [{ id: 'old' }]
  await failed.loadDiary(true)
  assert.equal(failed.diary.status, 'error')
  assert.equal(failed.diary.data, null)
})

test('logout or permission change prevents a late response from restoring old data', async () => {
  let release
  const state = setup(true, (_method, path) => path === '/capabilities'
    ? { allowed: true, available: true }
    : new Promise(resolve => { release = resolve }))
  const pending = state.loadDiary()
  for (let i = 0; i < 10 && !release; i++) await Promise.resolve()
  state.user.token = null
  state.diaryAllowed.value = false
  state.clearDiary()
  release({ records: [{ id: 'old', title: '旧日记', createdAt: '2026-10-28' }] })
  await pending
  assert.equal(state.diary.data, null)
  assert.equal(state.diary.status, 'idle')
})
