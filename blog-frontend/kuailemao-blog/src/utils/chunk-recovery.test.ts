import assert from 'node:assert/strict'
import test from 'node:test'
import { canRetryChunkLoad, isAsyncChunkLoadError } from './chunk-recovery.ts'

test('识别主流异步分包加载错误', () => {
  assert.equal(isAsyncChunkLoadError(new TypeError('Failed to fetch dynamically imported module: /js/page-old.js')), true)
  assert.equal(isAsyncChunkLoadError(new Error('Loading chunk 42 failed')), true)
  assert.equal(isAsyncChunkLoadError(new Error('普通接口请求失败')), false)
})
test('相同地址一分钟内只允许自动刷新一次', () => {
  const now = 1_000_000
  const stored = JSON.stringify({ target: '/blog', timestamp: now - 10_000 })
  assert.equal(canRetryChunkLoad('/blog', stored, now), false)
  assert.equal(canRetryChunkLoad('/experience', stored, now), true)
  assert.equal(canRetryChunkLoad('/blog', stored, now + 60_001), true)
  assert.equal(canRetryChunkLoad('/blog', 'invalid-json', now), true)
})
