// Optional browser acceptance. Use an existing Playwright installation; no download or production requests.
import assert from 'node:assert/strict'
import { mkdir, readFile } from 'node:fs/promises'
import { createRequire } from 'node:module'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { preview } from 'vite'

const require = createRequire(import.meta.url)
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright')
const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const html = await readFile(resolve(root, 'dist/index.html'), 'utf8')
const base = html.match(/src="([^"]*)assets\/index-[^/]+\.js"/)?.[1]
assert.ok(base?.startsWith('/'))
const server = await preview({ root, configFile: false, base, logLevel: 'silent',
  preview: { host: '127.0.0.1', port: 0, open: false, proxy: {} } })
let browser
try {
  browser = await chromium.launch({ headless: true, channel: process.env.PLAYWRIGHT_CHANNEL || undefined })
  const origin = `http://127.0.0.1:${server.httpServer.address().port}`
  for (const viewport of [{ width: 1440, height: 1000 }, { width: 390, height: 844 }]) {
    const context = await browser.newContext({ viewport, serviceWorkers: 'block', locale: 'zh-CN' })
    const page = await context.newPage()
    const errors = []
    const missingAssets = []
    let loginCount = 0
    let verificationCount = 0
    page.on('pageerror', error => errors.push(error.message))
    page.on('response', response => {
      if (response.url().startsWith(origin + base) && response.status() >= 400) missingAssets.push(response.url())
    })
    // Deny all external traffic, mock every API request, and serve only our local static files.
    await context.route('**/*', route => {
      const url = new URL(route.request().url())
      if (url.origin !== origin) return route.abort()
      if (url.pathname.endsWith('/user/login')) {
        loginCount++
        return route.fulfill({ json: { code: 200, data: { secondFactorRequired: true,
          challengeId: 'synthetic-build-qa', maskedEmail: 'q***@example.invalid', expiresIn: 300, resendAfter: 60 } } })
      }
      if (url.pathname.endsWith('/user/admin-login/verify')) {
        verificationCount++
        return route.fulfill({ status: 400, json: { code: 1005, msg: '模拟验证码无效' } })
      }
      if (url.pathname.startsWith(base) && !url.pathname.includes('/api/')) return route.continue()
      return route.fulfill({ json: { code: 200, data: {} } })
    })
    try {
      await page.goto(origin + base + 'login', { waitUntil: 'networkidle' })
      await page.getByText('欢迎登录后台系统', { exact: true }).waitFor()
      await page.locator('input[autocomplete="off"]').first().fill('synthetic-admin')
      await page.locator('input[type="password"]').fill('synthetic-not-a-real-password')
      await page.getByRole('button', { name: /登\s*录/ }).first().click()
      await page.getByText('验证管理员邮箱', { exact: true }).waitFor()
      assert.equal(loginCount, 1)
      assert.equal(await page.locator('input[type="password"]').count(), 0)
      await page.getByRole('textbox', { name: '邮箱验证码', exact: true }).fill('123456')
      await page.getByRole('button', { name: '验证并登录' }).click()
      await page.getByText('模拟验证码无效', { exact: true }).first().waitFor()
      await page.getByText('正在加载中...', { exact: true }).waitFor({ state: 'hidden' })
      assert.equal(verificationCount, 1)
      assert.equal(await page.getByRole('textbox', { name: '邮箱验证码', exact: true }).inputValue(), '')
      if (process.env.BUILD_QA_SCREENSHOTS) {
        const directory = resolve(process.env.BUILD_QA_SCREENSHOTS)
        await mkdir(directory, { recursive: true })
        await page.screenshot({ path: resolve(directory, `admin-login-${viewport.width}.png`), fullPage: true })
      }
      await page.getByRole('button', { name: '返回重新登录' }).click()
      await page.getByText('欢迎登录后台系统', { exact: true }).waitFor()
      assert.deepEqual(errors, [], 'no browser runtime errors')
      assert.deepEqual(missingAssets, [], 'all local chunks and assets load')
      console.log(`PASS browser ${viewport.width}px: login, second factor, invalid-code retry, assets; APIs mocked`)
    } finally { await context.close() }
  }
} finally {
  if (browser) await browser.close()
  await new Promise(resolve => server.httpServer.close(resolve))
}
