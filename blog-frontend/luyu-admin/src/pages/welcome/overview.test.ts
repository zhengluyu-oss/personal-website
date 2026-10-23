import { describe, expect, it, vi } from 'vitest'
import { loadOverviewMetric, type OverviewMetric } from './overview'

const metric = (key = 'articles'): OverviewMetric => ({ key, label: key, path: '/', status: 'idle', value: null })

describe('工作台指标状态', () => {
  it('并行请求互不阻塞，成功零值与失败不同，并可重试', async () => {
    const articles = metric()
    const comments = metric('comments')
    const experiences = metric('experiences')
    await Promise.all([
      loadOverviewMetric(articles, async () => ({ code: 200, data: { total: 0 } })),
      loadOverviewMetric(comments, async () => { throw new Error('offline') }),
      loadOverviewMetric(experiences, async () => ({ code: 200, data: [{ id: 1 }] })),
    ])
    expect(articles).toMatchObject({ value: 0, status: 'ready' })
    expect(comments).toMatchObject({ value: null, status: 'error' })
    expect(experiences).toMatchObject({ value: 1, status: 'ready' })
    await loadOverviewMetric(comments, async () => ({ code: 200, data: [] }))
    expect(comments).toMatchObject({ value: 0, status: 'ready' })
  })

  it.each([undefined, { code: 500 }, { code: 200, data: {} }, { code: 200, data: { total: -1 } }])('不将无效响应显示为零：%j', async (response) => {
    const target = metric()
    await loadOverviewMetric(target, async () => response)
    expect(target).toMatchObject({ value: null, status: 'error' })
  })

  it('加载时阻止重复请求', async () => {
    const target = metric()
    let resolve!: (value: any) => void
    const request = vi.fn(() => new Promise(done => { resolve = done }))
    const pending = loadOverviewMetric(target, request)
    expect(target.status).toBe('loading')
    await loadOverviewMetric(target, request)
    expect(request).toHaveBeenCalledTimes(1)
    resolve({ code: 200, data: { total: 4 } })
    await pending
    expect(target.value).toBe(4)
  })
})
