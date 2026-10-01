import assert from 'node:assert/strict'
import test from 'node:test'
import { createLatestRequest } from './latest-request.ts'

test('a late article response cannot replace a newer article', () => {
  const requests = createLatestRequest()
  const first = requests.start()
  const second = requests.start()
  assert.equal(requests.isCurrent(first), false)
  assert.equal(requests.isCurrent(second), true)
  requests.invalidate()
  assert.equal(requests.isCurrent(second), false)
})
