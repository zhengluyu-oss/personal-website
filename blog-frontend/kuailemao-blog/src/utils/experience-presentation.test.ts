import assert from 'node:assert/strict'
import test from 'node:test'
import { contributionParts } from './experience-presentation.ts'

test('separates concise contribution labels without losing detail', () => {
  assert.deepEqual(contributionParts('任务编排： 创建、执行、回写'), { title: '任务编排', detail: '创建、执行、回写' })
  assert.deepEqual(contributionParts('前端: 查询与筛选'), { title: '前端', detail: '查询与筛选' })
})
test('preserves legacy paragraphs, empty values and long prefixes', () => {
  for (const value of ['', '参与前后端开发', '说明：', '很长的原始段落'.repeat(6) + '：后半段']) {
    assert.deepEqual(contributionParts(value), { title: '', detail: value })
  }
})
