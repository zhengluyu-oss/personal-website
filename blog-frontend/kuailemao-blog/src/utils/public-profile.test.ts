import assert from 'node:assert/strict'
import test from 'node:test'
import { publicContacts, publicProfileUrl } from './public-profile.ts'

test('public contacts are opt-in and preserve explicit valid labels', () => {
  assert.deepEqual(publicContacts([]), [])
  assert.deepEqual(publicContacts([{ label: ' 邮箱 ', href: 'mailto:owner@example.test' }, { label: '', href: 'https://example.test' }]), [{ label: '邮箱', href: 'mailto:owner@example.test' }])
  assert.equal(publicProfileUrl('https://example.test/profile'), 'https://example.test/profile')
  assert.equal(publicProfileUrl('/resume.pdf', 'resume'), '/resume.pdf')
  assert.equal(publicProfileUrl('/resume.pdf'), '')
  assert.equal(publicProfileUrl('mailto:owner@example.test', 'resume'), '')
})

test('invalid public destinations never become active links', () => {
  for (const href of ['', 'javascript:alert(1)', 'data:text/html,x', '//example.test', '/\\example.test', 'https://example.test\n', 'https://user:secret@example.test', 'https://', 'mailto:no-address', 'mailto:a@b.test%0d%0aBcc:x@y.test']) {
    assert.equal(publicProfileUrl(href), '', href)
    assert.equal(publicProfileUrl(href, 'resume'), '', href)
  }
})
