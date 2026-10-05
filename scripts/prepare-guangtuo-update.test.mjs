import assert from 'node:assert/strict'
import test from 'node:test'
import { buildUpdatePlan } from './prepare-guangtuo-update.mjs'
import { loadContent } from './guangtuo-experience-content.mjs'

const manifest = await loadContent()
const company = { id: 2, company: '广拓时代信息技术有限公司', startDate: '2026-04-22' }
const projects = [4, 5, 6, 7, 8, 9].map(id => ({ id, experienceId: 2, projectName: `项目${id}`, status: 1, content: '旧内容\n\n## 功能示意图\n![图](/original.webp)' }))

test('update plan contains seven text-only changes and retains original galleries', () => {
  const plan = buildUpdatePlan(company, projects, manifest)
  assert.equal(plan.changes.length, 7)
  assert.equal(plan.mode, 'prepared-only')
  for (const change of plan.changes) {
    assert.match(change.beforeSha256, /^[a-f0-9]{64}$/)
    assert.match(change.afterSha256, /^[a-f0-9]{64}$/)
    for (const field of plan.preservedFields) assert.equal(field in change.after, false, field)
    if (change.kind === 'project') assert.ok(change.after.content.endsWith('![图](/original.webp)'))
  }
  assert.equal(projects[0].content.startsWith('旧内容'), true)
})
test('wrong company, project ownership, missing and duplicate records stop preparation', () => {
  assert.throws(() => buildUpdatePlan({ ...company, id: 1 }, projects, manifest))
  assert.throws(() => buildUpdatePlan(company, projects.slice(1), manifest))
  assert.throws(() => buildUpdatePlan(company, [...projects.slice(1), projects[1]], manifest))
  assert.throws(() => buildUpdatePlan(company, projects.map(x => ({ ...x, experienceId: 1 })), manifest))
})
test('source-backed supplemental sections are present without changing contribution ownership', () => {
  assert.match(manifest.projects.find(x => x.id === 4).content, /直播录屏评论识别与截图归档/)
  assert.match(manifest.projects.find(x => x.id === 4).content, /不能把测试样例称为真实用户发言/)
  const geo = manifest.projects.find(x => x.id === 8)
  assert.match(geo.content, /关键词|引用来源|报告生成/)
  assert.doesNotMatch(geo.contributions, /独立.*系统|全权负责|主导.*架构/)
})
