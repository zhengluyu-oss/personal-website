import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import { JSDOM } from 'jsdom'
import { transform } from 'esbuild'

const dom = new JSDOM('<!doctype html><html><body></body></html>', { url: 'https://example.invalid', runScripts: 'dangerously' })
for (const name of ['window', 'document', 'DOMParser', 'Element', 'HTMLElement', 'SVGElement', 'Node', 'navigator']) {
  Object.defineProperty(globalThis, name, { configurable: true, value: dom.window[name] })
}
const { code } = await transform(readFileSync(new URL('../../../kuailemao-blog/src/utils/sanitize-html.ts', import.meta.url), 'utf8'), { loader: 'ts', format: 'esm' })
const { sanitizeRenderedHtml } = await import(`data:text/javascript;base64,${Buffer.from(code).toString('base64')}`)
const { createApp, h, nextTick } = await import('vue')
const { MdPreview } = await import('md-editor-v3')

test('real Markdown preview neutralizes event handlers and preserves legitimate formatting', async () => {
  const host = document.createElement('div')
  document.body.append(host)
  const markdown = '# Heading\n\n| A | B |\n|---|---|\n| one | two |\n\n```js\nconst safe = true\n```\n\n[docs](https://example.invalid/docs)\n\n<img src="invalid" onerror="window.auditMarker=73">'
  const app = createApp({ render: () => h(MdPreview, { modelValue: markdown, sanitize: sanitizeRenderedHtml, noMermaid: true, noKatex: true, noHighlight: true }) })
  app.mount(host)
  await nextTick()
  assert.equal(host.querySelector('[onerror]'), null)
  host.querySelectorAll('img').forEach(img => img.dispatchEvent(new window.Event('error')))
  assert.equal(window.auditMarker, undefined)
  assert.ok(host.querySelector('h1'))
  assert.ok(host.querySelector('table'))
  assert.ok(host.querySelector('pre code'))
  assert.equal(host.querySelector('a[href="https://example.invalid/docs"]')?.textContent, 'docs')
  app.unmount(); host.remove()
})

test('SVG mutation, dangerous schemes and styling cannot bypass the shared boundary', () => {
  const html = sanitizeRenderedHtml('<svg><a href="https://example.invalid"><animate attributeName="href" values="javascript:alert(1)"/></a><foreignObject><img src=x onerror=alert(1)></foreignObject></svg><a href="java&#x09;script:alert(1)" onclick="alert(1)">bad</a><p style="position:fixed">safe</p>')
  const host = document.createElement('div'); host.innerHTML = html
  assert.equal(host.querySelector('animate,foreignObject,[onclick],[onerror],[style]'), null)
  assert.equal(host.querySelector('a[href^="javascript:"]'), null)
})

test('favorite messages use plain text while only articles request sanitized Markdown', () => {
  const component = readFileSync(new URL('./SafeContentPreview.vue', import.meta.url), 'utf8')
  const page = readFileSync(new URL('../pages/blog/collect/index.vue', import.meta.url), 'utf8')
  assert.ok(component.includes(':sanitize="sanitizeRenderedHtml"'))
  assert.ok(component.includes('{{ content }}'))
  assert.ok(page.includes(':markdown="contentModel.type === 1"'))
  const host = document.createElement('div')
  const app = createApp({ render: () => h('div', '<img src=x onerror="window.auditMarker=1">') })
  app.mount(host)
  assert.equal(host.querySelector('img'), null)
  app.unmount()
})
