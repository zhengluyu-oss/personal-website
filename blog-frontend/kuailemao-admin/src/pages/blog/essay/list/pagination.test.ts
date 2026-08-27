import assert from 'node:assert/strict'
import test from 'node:test'
import { articleLastPage, normalizeArticlePageSize, validArticlePage } from './pagination.ts'

test('后台仅接受约定的每页条数', () => {
  assert.equal(normalizeArticlePageSize(10), 10)
  assert.equal(normalizeArticlePageSize(20), 20)
  assert.equal(normalizeArticlePageSize(50), 50)
  assert.equal(normalizeArticlePageSize(100), 10)
})

test('空结果和删除末页记录后回到有效页', () => {
  assert.equal(articleLastPage(0, 10), 1)
  assert.equal(articleLastPage(21, 10), 3)
  assert.equal(validArticlePage(3, 20, 10), 2)
  assert.equal(validArticlePage(0, 20, 10), 1)
})
