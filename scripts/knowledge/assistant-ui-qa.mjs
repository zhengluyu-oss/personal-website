// Isolated browser regression using a local preview and fictional API responses.
import { createRequire } from 'node:module'
import assert from 'node:assert/strict'
const require = createRequire(import.meta.url)
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright')
const base = 'http://127.0.0.1:15174'
const entryId = '00000000-0000-4000-8000-000000000123'
const entry = { id: entryId, revision: 1, fileVersion: null, content: { kind: 'NOTE', title: 'Redis 持久化', body: 'Redis 使用 AOF 记录命令。', sourceUrl: '', categoryId: null, tagIds: [] }, createdAt: '2026-10-26T00:00:00Z', updatedAt: '2026-10-26T00:00:00Z', deletedAt: null }
const browser = await chromium.launch({ headless: true, ...(process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE ? { executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE } : {}) })
try {
  for (const width of [360, 390, 430, 1440]) {
    const context = await browser.newContext({ viewport: { width, height: 800 }, reducedMotion: 'reduce' })
    await context.addInitScript(() => localStorage.setItem('Token', JSON.stringify({ token: 'fictional-assistant-qa', expire: new Date(Date.now() + 3600000).toISOString() })))
    await context.route('**/*', route => {
      const url = new URL(route.request().url())
      if (url.origin !== base) return route.abort()
      if (!url.pathname.startsWith('/api/')) return route.continue()
      const path = url.pathname.slice(4)
      let data = []
      if (path === '/knowledge/view/assistant/capabilities') data = { available: true, message: '可以提问' }
      else if (path === '/knowledge/view/assistant/ask') data = { state: 'ANSWERED', answer: route.request().postData()?.includes('注入') ? '<img src=x onerror="window.assistantInjected=true">' : 'Redis 使用 AOF 记录命令。', sources: [{ id: 'S1', entryId, revision: 1, fileVersion: null, title: 'Redis 持久化', excerpt: 'Redis 使用 AOF 记录命令。', page: null, fromLine: null, toLine: null, url: `/knowledge?entry=${entryId}` }] }
      else if (path === '/knowledge/view/capabilities') data = { allowed: true, available: true, canManage: false, filesAvailable: false, message: '' }
      else if (path === '/knowledge/view/search') data = { records: [{ entry, excerpt: '', page: null, fromLine: null, toLine: null }], total: 1, page: 1, pageSize: 20 }
      else if (path === '/knowledge/view/labels') data = []
      else if (path === `/knowledge/view/entries/${entryId}`) data = entry
      else if (path === '/site-modules/access') data = ['home', 'experience', 'blog', 'shares', 'photos', 'about', 'diary', 'knowledge'].map(key => ({ key, name: key, allowed: true, publicAccess: false }))
      else if (path === '/user/auth/info') data = { id: 9, username: 'fixture', nickname: '虚构访客', roles: ['ROLE_USER'] }
      else if (path === '/websiteInfo/front') data = { websiteName: '本地验收', webmasterAvatar: '', recordNumber: '' }
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 200, data }) })
    })
    const page = await context.newPage()
    for (const path of ['/', '/blog', '/about', '/blog/articles/1', '/knowledge']) {
      await page.goto(base + path)
      await page.getByRole('button', { name: '打开知识库客服' }).waitFor()
      const assistantButton = await page.getByRole('button', { name: '打开知识库客服' }).boundingBox()
      const existingControls = await page.locator('.container_div').count() ? await page.locator('.container_div').first().boundingBox() : null
      if (assistantButton && existingControls) assert.ok(
        assistantButton.x + assistantButton.width <= existingControls.x || existingControls.x + existingControls.width <= assistantButton.x ||
        assistantButton.y + assistantButton.height <= existingControls.y || existingControls.y + existingControls.height <= assistantButton.y,
        `assistant avoids existing bottom controls on ${path} at ${width}px`
      )
    }
    await page.getByRole('button', { name: '打开知识库客服' }).click()
    const panel = page.getByRole('dialog', { name: '知识库客服' })
    await panel.waitFor()
    assert.equal(await panel.getByLabel('向知识库提问').evaluate(element => element === document.activeElement), true, 'composer receives focus')
    await panel.getByRole('button', { name: '关闭客服' }).click()
    assert.equal(await page.getByRole('button', { name: '打开知识库客服' }).evaluate(element => element === document.activeElement), true, 'closing restores focus')
    await page.getByRole('button', { name: '打开知识库客服' }).click()
    const bounds = await panel.boundingBox()
    assert.ok(bounds && bounds.x >= 0 && bounds.x + bounds.width <= width && bounds.y >= 56 && bounds.y + bounds.height <= 800, `panel fits ${width}px`)
    await panel.getByLabel('向知识库提问').fill('Redis 持久化')
    await panel.getByRole('button', { name: '发送' }).click()
    await panel.getByText('Redis 使用 AOF 记录命令。', { exact: true }).waitFor()
    await panel.getByLabel('向知识库提问').fill('注入')
    await panel.getByRole('button', { name: '发送' }).click()
    await panel.getByText('<img src=x onerror="window.assistantInjected=true">', { exact: true }).waitFor()
    assert.equal(await page.evaluate(() => window.assistantInjected === true), false, 'model HTML remains text')
    await panel.getByRole('link', { name: 'Redis 持久化' }).first().click()
    await page.waitForURL(`**/knowledge?entry=${entryId}`)
    await page.getByRole('heading', { name: 'Redis 持久化' }).waitFor()
    await page.reload()
    await page.getByRole('button', { name: '打开知识库客服' }).click()
    assert.equal(await page.getByRole('dialog', { name: '知识库客服' }).getByText('Redis 使用 AOF 记录命令。', { exact: true }).count(), 0, 'conversation cleared on reload')
    await page.evaluate(() => window.dispatchEvent(new CustomEvent('knowledge-access-invalid')))
    await page.getByRole('button', { name: '打开知识库客服' }).waitFor({ state: 'hidden' })
    await context.close()
  }
  console.log(JSON.stringify({ widths: [360, 390, 430, 1440], routes: ['/', '/blog', '/about', '/blog/articles/1', '/knowledge'], sourceNavigation: true, clearsOnReloadAndRevocation: true }))
} finally { await browser.close() }
