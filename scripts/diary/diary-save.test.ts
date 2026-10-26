import test from 'node:test'
import assert from 'node:assert/strict'
import { DiarySaver, diaryValidation, type DiaryWrite, type DiaryAck } from '../../blog-frontend/luyu-blog/src/utils/diary-save.ts'
const ack = (request: DiaryWrite): DiaryAck => ({ entryId: request.entryId, mutationId: request.mutationId, revision: request.expectedRevision + 1, savedAt: '2026-10-15T04:00:00Z' })
function queue() { const calls: { request: DiaryWrite; resolve: (value: DiaryAck) => void; reject: (error: unknown) => void }[] = []; const saver = new DiarySaver(request => new Promise((resolve, reject) => calls.push({ request, resolve, reject })), () => {}); return { saver, calls } }
test('只读取或打开新草稿不写入；两秒停顿后自动保存', async t => {
  t.mock.timers.enable({ apis: ['setTimeout', 'Date'] }); const calls: DiaryWrite[] = []
  const saver = new DiarySaver(async request => { calls.push(request); return ack(request) }, () => {}); t.after(() => saver.dispose())
  saver.attach({ id: crypto.randomUUID() }); t.mock.timers.tick(60000); assert.equal(calls.length, 0)
  saver.edit({ body: '今天补写过去十几天' }); t.mock.timers.tick(1999); assert.equal(calls.length, 0)
  t.mock.timers.tick(1); await saver.save(); assert.equal(calls.length, 1); assert.equal(saver.state, 'clean')
})
test('持续编辑最多等待30秒，日期修改与正文进入同一快照', async t => {
  t.mock.timers.enable({ apis: ['setTimeout', 'Date'] }); const calls: DiaryWrite[] = []
  const saver = new DiarySaver(async request => { calls.push(request); return ack(request) }, () => {}); t.after(() => saver.dispose()); saver.attach({ id: crypto.randomUUID() })
  for (let i = 0; i < 30; i++) { saver.edit({ body: String(i), coveredFrom: '2026-10-01', coveredTo: '2026-10-15' }); t.mock.timers.tick(1000) }
  await saver.save(); assert.equal(calls.length, 1); assert.equal(calls[0].body, '29'); assert.equal(calls[0].coveredTo, '2026-10-15')
})
test('慢ACK只确认旧快照，后续手动保存串行推进版本', async t => {
  const { saver, calls } = queue(); t.after(() => saver.dispose()); saver.attach({ id: crypto.randomUUID() }); saver.edit({ body: 'a' }); const first = saver.save()
  saver.edit({ body: 'ab' }); assert.equal(saver.save(), first); assert.equal(calls.length, 1)
  calls[0].resolve(ack(calls[0].request)); await first; assert.equal(saver.state, 'dirty'); assert.equal(saver.content.body, 'ab')
  const second = saver.save(); assert.equal(calls[1].request.expectedRevision, 1); calls[1].resolve(ack(calls[1].request)); await second; assert.equal(saver.state, 'clean')
})
test('未知结果重试原ID和mutation，确认后才发送之后的修改', async t => {
  const { saver, calls } = queue(); t.after(() => saver.dispose()); saver.attach({ id: crypto.randomUUID() }); saver.edit({ body: 'first' }); const failed = saver.save()
  calls[0].reject(Object.assign(new Error('offline'), { status: 503 })); await assert.rejects(failed); saver.edit({ body: 'later' })
  const retry = saver.save(); assert.deepEqual(calls[1].request, calls[0].request); calls[1].resolve(ack(calls[1].request)); await retry
  const next = saver.save(); assert.equal(calls[2].request.body, 'later'); assert.equal(calls[2].request.expectedRevision, 1); assert.notEqual(calls[2].request.mutationId, calls[0].request.mutationId); calls[2].resolve(ack(calls[2].request)); await next
})
test('冲突不自动覆盖，明确选择本地内容后使用最新版本', async t => {
  t.mock.timers.enable({ apis: ['setTimeout'] }); const { saver, calls } = queue(); t.after(() => saver.dispose()); saver.attach({ id: crypto.randomUUID() }); saver.edit({ body: 'local' }); const failed = saver.save()
  calls[0].reject(Object.assign(new Error('conflict'), { status: 409 })); await assert.rejects(failed); t.mock.timers.tick(60000); assert.equal(calls.length, 1); assert.equal(saver.state, 'conflict'); await assert.rejects(saver.save())
  saver.resolveUsingLocal({ id: saver.id, revision: 9, title: '', body: 'remote', coveredFrom: null, coveredTo: null, createdAt: '', updatedAt: '' }); const next = saver.save(); assert.equal(calls[1].request.body, 'local'); assert.equal(calls[1].request.expectedRevision, 9); calls[1].resolve(ack(calls[1].request)); await next
})
test('切换篇目或清除身份后，迟到响应不能更新新草稿', async t => {
  const { saver, calls } = queue(); t.after(() => saver.dispose()); saver.attach({ id: crypto.randomUUID() }); saver.edit({ body: 'old' }); const old = saver.save(); saver.attach({ id: crypto.randomUUID() }); saver.edit({ body: 'new' }); calls[0].resolve(ack(calls[0].request)); await old; assert.equal(saver.content.body, 'new'); assert.equal(saver.revision, 0); assert.equal(saver.state, 'dirty')
  const pending = saver.save(); saver.clear(); calls[1].resolve(ack(calls[1].request)); await pending; assert.equal(saver.content.body, ''); assert.equal(saver.id, ''); assert.equal(saver.revision, 0)
})
test('认证失败锁定，恢复身份后先比较服务器版本', async t => {
  const { saver, calls } = queue(); t.after(() => saver.dispose()); saver.attach({ id: crypto.randomUUID() }); saver.edit({ body: 'private' }); const failed = saver.save(); calls[0].reject(Object.assign(new Error('auth'), { status: 401 })); await assert.rejects(failed); assert.equal(saver.state, 'locked'); saver.edit({ body: 'not allowed' }); assert.equal(saver.content.body, 'private'); await assert.rejects(saver.save())
  saver.resume(); const retry = saver.save(); assert.deepEqual(calls[1].request, calls[0].request); calls[1].resolve(ack(calls[1].request)); await retry; assert.equal(saver.state, 'clean')
})
test('Unicode字符上限与可选成对日期一致', () => {
  assert.equal(diaryValidation({ title: '🙂'.repeat(120), body: '🙂'.repeat(100000), coveredFrom: null, coveredTo: null }), '')
  assert.notEqual(diaryValidation({ title: '', body: '', coveredFrom: '2026-10-15', coveredTo: null }), '')
  assert.notEqual(diaryValidation({ title: '', body: '', coveredFrom: '2026-10-15', coveredTo: '2026-10-01' }), '')
  assert.notEqual(diaryValidation({ title: '', body: '', coveredFrom: '2026-02-30', coveredTo: '2026-03-01' }), '')
})
