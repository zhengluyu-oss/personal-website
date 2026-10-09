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

test('每页篇数接受 1 到 100 的整数，默认值不占用网址参数', () => {
  assert.equal(parseBlogPageSize(undefined), 9)
  assert.equal(parseBlogPageSize('1'), 1)
  assert.equal(parseBlogPageSize('7'), 7)
  assert.equal(parseBlogPageSize('18'), 18)
  assert.equal(parseBlogPageSize('100'), 100)
  for (const value of ['0', '-1', '101', '1.5', 'abc', '01', ['18']])
    assert.equal(parseBlogPageSize(value), 9)
  assert.equal(blogPageSizeQuery(9), undefined)
  assert.equal(blogPageSizeQuery(18), '18')
  assert.equal(isCanonicalBlogPageSize(undefined), true)
  assert.equal(isCanonicalBlogPageSize('1'), true)
  assert.equal(isCanonicalBlogPageSize('18'), true)
  assert.equal(isCanonicalBlogPageSize('100'), true)
  assert.equal(isCanonicalBlogPageSize('9'), false)
  assert.equal(isCanonicalBlogPageSize('018'), false)
  assert.equal(isCanonicalBlogPageSize(['18']), false)
})

test('顶部推荐单独展示，最近更新按用户输入的篇数分页', () => {
  assert.equal(blogPageCount(44, 1), 44)
  assert.equal(blogPageCount(44, 9), 5)
  assert.equal(blogPageCount(44, 18), 3)
  assert.equal(clampBlogPage(6, 44, 36), 2)
})
