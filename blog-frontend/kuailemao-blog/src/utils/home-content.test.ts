import assert from 'node:assert/strict'
import test from 'node:test'
import { articlePage, createHomeResource, firstBanner, homeCount, loadHomeResource, selectHomeArticles } from './home-content.ts'
import { resolveHomeHero, safeHeroUrl } from './home-hero.ts'

test('a slow or failed module does not block successful content', async () => {
  const latest = createHomeResource<number>()
  const recommendations = createHomeResource<number>()
  const experience = createHomeResource<number>()
  let finish!: (value: number) => void
  const pending = loadHomeResource(experience, () => new Promise(resolve => { finish = resolve }))
  await loadHomeResource(latest, async () => 30)
  await loadHomeResource(recommendations, async () => { throw new Error('network') })
  assert.equal(latest.data, 30)
  assert.equal(recommendations.status, 'error')
  assert.equal(experience.status, 'loading')
  finish(2)
  await pending
  assert.equal(experience.data, 2)
})

test('retry recovers only the failed resource and ignores concurrent clicks', async () => {
  const resource = createHomeResource<number>()
  await loadHomeResource(resource, async () => { throw new Error('timeout') })
  let calls = 0
  await Promise.all([1, 2].map(() => loadHomeResource(resource, async () => ++calls)))
  assert.equal(calls, 1)
  assert.equal(resource.status, 'success')
  assert.equal(resource.data, 1)
  await loadHomeResource(resource, async () => { throw new Error('refresh failed') })
  assert.equal(resource.data, 1)
})

test('late responses after leaving the page do not publish', async () => {
  const resource = createHomeResource<number>()
  let active = true
  let finish!: (value: number) => void
  const pending = loadHomeResource(resource, () => new Promise(resolve => { finish = resolve }), () => active)
  active = false
  finish(3)
  await pending
  assert.equal(resource.data, null)
})

test('unknown, failed, and genuinely empty totals are distinct', () => {
  assert.equal(homeCount('loading'), '加载中')
  assert.equal(homeCount('error'), '暂不可用')
  assert.equal(homeCount('success', 0), '0')
  assert.deepEqual(articlePage({ page: [], total: 0 }), { articles: [], total: 0 })
  assert.throws(() => articlePage(null))
  assert.throws(() => articlePage({ page: [], total: null }))
  assert.throws(() => articlePage({ page: [], total: -1 }))
})

test('latest first article stays when an older article is featured; IDs normalize', () => {
  const latest = [1, 2, 3, 4, 5, 6].map(id => ({ id, articleTitle: `文章 ${id}` }))
  assert.deepEqual(selectHomeArticles(latest, [{ id: 99, articleTitle: '旧推荐' }]).articles.map(x => x.id), [1, 2, 3, 4, 5])
  assert.deepEqual(selectHomeArticles(latest, [{ id: '1', articleTitle: '推荐' }]).articles.map(x => x.id), [2, 3, 4, 5, 6])
  assert.equal(selectHomeArticles(latest, []).featured?.id, 1)
  assert.equal(selectHomeArticles([], []).featured, undefined)
})

test('first valid banner only, empty and malformed payloads handled', () => {
  assert.equal(firstBanner([null, '', '  ', ' /image.jpg ', '/other.jpg']), '/image.jpg')
  assert.equal(firstBanner([]), '')
  assert.throws(() => firstBanner(null))
})

test('configured hero fields are retained; empty optional fields stay hidden', () => {
  assert.equal(resolveHomeHero().primary?.href, '/blog')
  assert.equal(resolveHomeHero().secondary?.href, '/experience')
  assert.equal(resolveHomeHero().title, '你好，我是郑陆宇。')
  assert.equal(resolveHomeHero({ webmasterName: '陆屿' }).identityName, '陆屿')
  assert.match(resolveHomeHero().identityRole, /后端 \/ 全栈/)
  const empty = resolveHomeHero({ heroTitle: ' ', heroPrimaryText: '阅读', heroPrimaryUrl: '' })
  assert.equal(empty.primary?.href, '/blog')
  assert.equal(empty.secondary?.href, '/experience')
  assert.equal(empty.asideText, '')
  assert.equal(empty.title, '你好，我是郑陆宇。')
  assert.match(empty.subtitle, /后端与全栈/)
  const configured = resolveHomeHero({ heroTitle: '自定义标题', heroDescription: '介绍', heroAsideLabel: '专注', heroAsideText: 'Java', heroPrimaryText: '源码', heroPrimaryUrl: 'https://github.com/zhengluyu-oss' })
  assert.equal(configured.title, '自定义标题')
  assert.equal(configured.description, '介绍')
  assert.equal(configured.asideText, 'Java')
  assert.equal(configured.primary?.external, true)
  assert.equal(configured.secondary, null)
})

test('one configured action does not create a placeholder for its incomplete pair', () => {
  const configured = resolveHomeHero({
    heroPrimaryText: '看文章',
    heroPrimaryUrl: '/blog',
    heroSecondaryText: '缺少链接',
  })
  assert.deepEqual(configured.primary, { text: '看文章', href: '/blog', external: false })
  assert.equal(configured.secondary, null)
})

test('article proof selection is deterministic, deduplicated and capped at five', () => {
  const latest = [1, 1, 2, 3, 4, 5, 6, 7].map((id, index) => ({ id, articleTitle: `文章 ${id}-${index}` }))
  const selected = selectHomeArticles(latest, [{ id: 4, articleTitle: '指定推荐' }])
  assert.equal(selected.featured?.id, 4)
  assert.deepEqual(selected.articles.map(item => item.id), [1, 2, 3, 5, 6])
})

test('unsafe configured URLs never become links', () => {
  for (const url of ['javascript:alert(1)', '//evil.test', '/\\evil.test', 'data:text/html,x', ' https://exa\nmple.test']) assert.equal(safeHeroUrl(url), '')
  assert.equal(safeHeroUrl('/blog'), '/blog')
})
