import { test } from 'node:test'
import assert from 'node:assert/strict'
import { moduleForPath, SITE_MODULES, validateModuleAccess } from './site-module-access.ts'

test('all content detail and category routes use their parent module', () => {
  for (const path of ['/blog', '/blog/articles/5', '/blog/java', '/blog/tags/2']) assert.equal(moduleForPath(path), 'blog')
  assert.equal(moduleForPath('/experience/2/projects/5'), 'experience')
  assert.equal(moduleForPath('/website-shares/3'), 'shares')
  assert.equal(moduleForPath('/photos'), 'photos')
  assert.equal(moduleForPath('/about'), 'about')
  assert.equal(moduleForPath('/'), 'home')
  for (const path of ['/auth/login', '/account', '/access-denied', '/blogger']) assert.equal(moduleForPath(path), undefined)
})
test('missing or malformed access data never permits a route', () => {
  const valid = SITE_MODULES.map(key => ({ key, name: key, publicAccess: true, allowed: true }))
  assert.deepEqual(validateModuleAccess(valid), valid)
  for (const value of [null, [], valid.slice(1), [...valid.slice(1), valid[1]], valid.map(item => ({ ...item, allowed: 'true' }))])
    assert.throws(() => validateModuleAccess(value))
})
