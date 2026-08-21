import assert from 'node:assert/strict'
import test from 'node:test'
import { createRequestTracker } from './request-tracker.ts'

function setup() {
  const events: string[] = []
  const tracker = createRequestTracker({
    onProgressStart: () => events.push('progress:start'),
    onProgressDone: () => events.push('progress:done'),
    onBlockingStart: () => events.push('blocking:start'),
    onBlockingDone: () => events.push('blocking:done'),
  })
  return { events, tracker }
}

test('成功、网络错误、超时和取消均使用相同的幂等收敛路径', () => {
  for (const outcome of ['success', 'network-error', 'timeout', 'cancel']) {
    const { events, tracker } = setup()
    const token = tracker.begin(true)
    tracker.finalize(token)
    tracker.finalize(token)
    assert.deepEqual(tracker.snapshot(), { activeRequests: 0, blockingRequests: 0 }, outcome)
    assert.deepEqual(events, ['progress:start', 'blocking:start', 'blocking:done', 'progress:done'])
  }
})

test('交错结束的并发请求不会提前清除共享状态', () => {
  const { events, tracker } = setup()
  const first = tracker.begin(true)
  const second = tracker.begin(true)
  const third = tracker.begin(false)
  tracker.finalize(second)
  assert.deepEqual(tracker.snapshot(), { activeRequests: 2, blockingRequests: 1 })
  tracker.finalize(third)
  assert.deepEqual(tracker.snapshot(), { activeRequests: 1, blockingRequests: 1 })
  tracker.finalize(first)
  assert.deepEqual(tracker.snapshot(), { activeRequests: 0, blockingRequests: 0 })
  assert.deepEqual(events, ['progress:start', 'blocking:start', 'blocking:done', 'progress:done'])
})

