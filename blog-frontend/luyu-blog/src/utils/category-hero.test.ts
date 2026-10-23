import assert from 'node:assert/strict'
import test from 'node:test'
import { DEFAULT_BLOG_HERO, resolveCategoryHero } from './category-hero.ts'

test('全部文章页使用稳定默认文案', () => {
  assert.deepEqual(resolveCategoryHero(), DEFAULT_BLOG_HERO)
})

test('完整分类配置覆盖全部默认字段', () => {
  assert.deepEqual(resolveCategoryHero({
    heroEyebrow: 'AI · TOOLS',
    heroTitleAccent: '与智能协作，',
    heroTitle: '让想法更快落地。',
    heroDescription: '记录 AI 工具实践。',
  }), {
    eyebrow: 'AI · TOOLS',
    titleAccent: '与智能协作，',
    title: '让想法更快落地。',
    description: '记录 AI 工具实践。',
  })
})

test('部分分类配置逐字段回退', () => {
  const result = resolveCategoryHero({ heroTitle: '只覆盖标题后半段。' })
  assert.equal(result.eyebrow, DEFAULT_BLOG_HERO.eyebrow)
  assert.equal(result.titleAccent, DEFAULT_BLOG_HERO.titleAccent)
  assert.equal(result.title, '只覆盖标题后半段。')
  assert.equal(result.description, DEFAULT_BLOG_HERO.description)
})

test('纯空白配置被视为空并回退', () => {
  assert.deepEqual(resolveCategoryHero({
    heroEyebrow: '  ',
    heroTitleAccent: '\n',
    heroTitle: '',
    heroDescription: '\t',
  }), DEFAULT_BLOG_HERO)
})

test('分类切换和返回全部文章页不会残留上一分类文案', () => {
  const first = resolveCategoryHero({ heroTitle: '第一个分类' })
  const second = resolveCategoryHero({ heroTitle: '第二个分类' })
  const all = resolveCategoryHero()
  assert.equal(first.title, '第一个分类')
  assert.equal(second.title, '第二个分类')
  assert.deepEqual(all, DEFAULT_BLOG_HERO)
})
