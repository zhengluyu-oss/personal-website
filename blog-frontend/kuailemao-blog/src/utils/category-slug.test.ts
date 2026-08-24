import assert from 'node:assert/strict'
import test from 'node:test'
import {
  buildCategorySlugEntries,
  categoryBaseSlug,
  categoryRoute,
  resolveCategorySlug,
} from './category-slug.ts'

test('中文分类逐字生成拼音首字母', () => {
  assert.equal(categoryBaseSlug('技术笔记'), 'jsbj')
  assert.equal(categoryBaseSlug('网络防护'), 'wlfh')
})

test('英文分类只使用第一个英文单词并转为小写', () => {
  assert.equal(categoryBaseSlug('Network Security Notes'), 'network')
  assert.equal(categoryBaseSlug('skills'), 'skills')
})

test('混合分类按原顺序组合英文首词和中文首字母', () => {
  assert.equal(categoryBaseSlug('AI工具'), 'ai-gj')
  assert.equal(categoryBaseSlug('技术AI工具'), 'js-ai-gj')
})

test('重复、保留字和空结果使用确定性分类 ID 消歧', () => {
  const entries = buildCategorySlugEntries([
    { id: 14, categoryName: '技术笔记' },
    { id: 15, categoryName: '技术笔记' },
    { id: 16, categoryName: 'Archive' },
    { id: 17, categoryName: '---' },
  ])
  assert.deepEqual(entries.map(entry => entry.slug), ['jsbj-14', 'jsbj-15', 'archive-16', 'category-17'])
})

test('slug 可以反查分类并统一生成分类路由', () => {
  const categories = [{ id: 3, categoryName: 'AI工具' }]
  assert.equal(resolveCategorySlug(categories, 'ai-gj')?.categoryId, 3)
  assert.equal(categoryRoute(categories, 3), '/blog/ai-gj')
  assert.equal(categoryRoute(categories, 99), '/blog')
})
