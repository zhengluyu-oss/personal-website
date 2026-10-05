import assert from 'node:assert/strict'
import test from 'node:test'
import { loadContent, mergeExperience, mergeProject } from './guangtuo-experience-content.mjs'

const manifest = await loadContent()
test('six scoped projects have substantial content and labeled responsibilities', () => {
  assert.equal(manifest.projects.length, 6)
  for (const project of manifest.projects) {
    assert.ok(project.content.length > 1000, String(project.id))
    assert.ok(project.contributions.split('\n').length >= 3)
    assert.ok(!/D:\/|密码|密钥=|192\.168\./.test(project.content))
  }
})
test('retains identity, dates, status, images and gallery while merging text', () => {
  const gallery = '## 功能示意图\n\n### 页面\n![原图](/existing.webp)\n'
  const original = { id: 4, experienceId: 2, projectName: '原名称', startDate: null, endDate: null, status: 0, orderNum: 6, coverImage: '/original.webp', content: '旧正文\n\n' + gallery }
  const revised = mergeProject(original, manifest)
  for (const key of ['id', 'experienceId', 'projectName', 'startDate', 'endDate', 'status', 'orderNum', 'coverImage']) assert.equal(revised[key], original[key])
  assert.ok(revised.content.endsWith(gallery))
  assert.equal(original.content, '旧正文\n\n' + gallery)
  assert.equal(mergeProject(revised, manifest).content, revised.content)
})
test('handles absent galleries and leaves other companies/projects untouched', () => {
  const original = { id: 10, experienceId: 1, content: '保留' }
  assert.equal(mergeProject(original, manifest), original)
  const wrongOwner = { id: 4, experienceId: 1, content: '保留' }
  assert.equal(mergeProject(wrongOwner, manifest), wrongOwner)
  assert.ok(mergeProject({ id: 4, experienceId: 2 }, manifest).content.length > 1000)
  const company = { id: 1, content: '保留' }
  assert.equal(mergeExperience(company, manifest), company)
  assert.equal(mergeExperience({ id: 2, startDate: '2026-04-22' }, manifest).startDate, '2026-04-22')
})
