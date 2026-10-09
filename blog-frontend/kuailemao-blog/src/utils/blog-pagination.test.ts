import assert from 'node:assert/strict'
import test from 'node:test'
import { blogPageCount, blogPageQuery, blogPageSizeQuery, clampBlogPage, isCanonicalBlogPage, isCanonicalBlogPageSize, parseBlogPage, parseBlogPageSize } from './blog-pagination.ts'

test('缺省页和合法页码可以稳定解析', () => {
  assert.equal(parseBlogPage(undefined), 1)
  assert.equal(parseBlogPage('3'), 3)
  assert.equal(isCanonicalBlogPage(undefined), true)
  assert.equal(isCanonicalBlogPage('3'), true)
})

test('非法页码统一回到第一页', () => {
  for (const value of ['0', '-1', 'abc', '1.5', '01', ['0']])
    assert.equal(parseBlogPage(value), 1)
  assert.equal(isCanonicalBlogPage('1'), false)
  assert.equal(isCanonicalBlogPage('abc'), false)
})

test('总数决定末页并限制超范围页码', () => {
  assert.equal(blogPageCount(0), 1)
  assert.equal(blogPageCount(9), 1)
  assert.equal(blogPageCount(10), 2)
  assert.equal(clampBlogPage(8, 19), 3)
})

test('第一页保持干净路由，后续页写入查询参数', () => {
  assert.equal(blogPageQuery(1), undefined)
  assert.equal(blogPageQuery(4), '4')
})

test('每页篇数只接受支持的选项，默认值不占用网址参数', () => {
  assert.equal(parseBlogPageSize(undefined), 9)
  assert.equal(parseBlogPageSize('18'), 18)
  assert.equal(parseBlogPageSize('36'), 36)
  assert.equal(parseBlogPageSize('100'), 9)
  assert.equal(blogPageSizeQuery(9), undefined)
  assert.equal(blogPageSizeQuery(18), '18')
  assert.equal(isCanonicalBlogPageSize(undefined), true)
  assert.equal(isCanonicalBlogPageSize('18'), true)
  assert.equal(isCanonicalBlogPageSize('9'), false)
  assert.equal(isCanonicalBlogPageSize('018'), false)
  assert.equal(isCanonicalBlogPageSize(['18']), false)
})

test('推荐文章计入每页篇数，剩余文章按对应数量分页', () => {
  assert.equal(blogPageCount(44, 9 - 1), 6)
  assert.equal(blogPageCount(44, 18 - 1), 3)
  assert.equal(clampBlogPage(6, 44, 36 - 1), 2)
})
