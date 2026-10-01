import assert from 'node:assert/strict'
import test from 'node:test'
import { applyFixedSeo, setSeoMeta } from './seo.ts'

test('fixed SEO uses the completed navigation path for canonical', () => {
  const elements: Array<Record<string, string>> = []
  const originalWindow = (globalThis as any).window
  const originalDocument = (globalThis as any).document
  ;(globalThis as any).window = { location: { origin: 'https://www.zhengluyu.com', pathname: '/old-page' } }
  ;(globalThis as any).document = {
    title: '',
    head: {
      querySelector(selector: string) {
        return elements.find(element => selector === 'link[rel="canonical"]'
          ? element.rel === 'canonical'
          : element.name === selector.match(/^meta\[name="(.+)"\]$/)?.[1])
      },
      appendChild(element: Record<string, string>) { elements.push(element) },
    },
    createElement() { return {} },
  }

  try {
    applyFixedSeo('experience', '', '/experience')
    assert.equal(elements.find(element => element.rel === 'canonical')?.href,
      'https://www.zhengluyu.com/experience')
    assert.equal((globalThis as any).document.title, '工作经历 | 郑陆宇')

    ;(globalThis as any).window.location.pathname = '/experience/2'
    setSeoMeta({ title: '项目详情', description: '项目', keywords: '项目' })
    assert.equal(elements.find(element => element.rel === 'canonical')?.href,
      'https://www.zhengluyu.com/experience/2')
  } finally {
    ;(globalThis as any).window = originalWindow
    ;(globalThis as any).document = originalDocument
  }
})
