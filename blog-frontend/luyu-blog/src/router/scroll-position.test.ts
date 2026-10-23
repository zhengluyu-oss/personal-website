import assert from 'node:assert/strict'
import test from 'node:test'
import { scrollPositionForRoute } from './scroll-position.ts'

test('ordinary navigation starts at the top', () => {
  assert.deepEqual(scrollPositionForRoute('', null), { left: 0, top: 0 })
})

test('browser history restores the saved position before considering an anchor', () => {
  const saved = { left: 0, top: 640 }
  assert.equal(scrollPositionForRoute('#section', saved), saved)
})

test('anchor navigation targets the matching element', () => {
  assert.deepEqual(scrollPositionForRoute('#section', null), { el: '#section' })
})
