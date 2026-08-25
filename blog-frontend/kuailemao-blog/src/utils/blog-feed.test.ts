import assert from 'node:assert/strict'
import test from 'node:test'
import { normalizeBlogFeed } from './blog-feed.ts'

test('保留显式主推荐并从普通列表中去重', () => {
  const result = normalizeBlogFeed({
    featuredArticle: { id: 2, title: '推荐' },
    articles: [{ id: 3, title: '普通' }, { id: 2, title: '重复项' }],
    total: 2,
  })
  assert.equal(result.featuredArticle?.id, 2)
  assert.deepEqual(result.articles.map(article => article.id), [3])
  assert.equal(result.total, 2)
})

test('空响应产生稳定空状态', () => {
  assert.deepEqual(normalizeBlogFeed(), {
    featuredArticle: undefined,
    articles: [],
    total: 0,
  })
})

test('聚合页与分类页切换时各自保留独立主推荐', () => {
  const aggregation = normalizeBlogFeed({
    featuredArticle: { id: 1, title: '聚合页推荐' },
    articles: [{ id: 2, title: '聚合页普通文章' }],
    total: 2,
  })
  const category = normalizeBlogFeed({
    featuredArticle: { id: 3, title: '分类页推荐' },
    articles: [{ id: 4, title: '分类页普通文章' }],
    total: 2,
  })

  assert.equal(aggregation.featuredArticle?.id, 1)
  assert.equal(category.featuredArticle?.id, 3)
  assert.deepEqual(category.articles.map(article => article.id), [4])
})
