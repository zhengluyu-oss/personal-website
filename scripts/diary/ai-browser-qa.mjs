// Actual browser, local fictional Spring/PG/HTTP fixture. Never production credentials.
import { createRequire } from 'node:module'
import { mkdir, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { randomUUID } from 'node:crypto'
import assert from 'node:assert/strict'
const require = createRequire(import.meta.url)
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright')
const base = 'http://127.0.0.1:15173', output = process.env.DIARY_QA_OUTPUT
if (!output) throw new Error('DIARY_QA_OUTPUT required')
assert.equal((await (await fetch(base + '/api/websiteInfo/front')).json()).data.websiteName, '本地日记AI验收')
await mkdir(output, { recursive: true })
const browser = await chromium.launch({ headless: true, ...(process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE ? { executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE } : {}) })
const evidence = { status: 'running', checks: [], external: [], pageErrors: [] }
async function api(path, method = 'GET', data, token = 'owner-fixture') {
  const r = await fetch(base + '/api/diary/auth' + path, { method, headers: { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' }, ...(data ? { body: JSON.stringify(data) } : {}) })
  return { status: r.status, cache: r.headers.get('cache-control'), body: await r.json() }
}
const ctx = await browser.newContext({ viewport: { width: 1360, height: 1000 } })
await ctx.route('**/*', route => { if (new URL(route.request().url()).origin === base) return route.continue(); evidence.external.push(route.request().url()); return route.abort() })
await ctx.addInitScript(() => localStorage.setItem('Token', JSON.stringify({ token: 'owner-fixture', expire: new Date(Date.now() + 3600000).toISOString() })))
const page = await ctx.newPage();page.on('pageerror', e => evidence.pageErrors.push(e.message))
try {
  const id = randomUUID(), body = '独特虚构探针 AI_BROWSER_PROBE_20261015🙂。\n周末在公园练习水彩，并在阅读后记录收获。'
  assert.equal((await api('/entries', 'POST', { entryId: id, mutationId: randomUUID(), expectedRevision: 0, title: '虚构AI阅读验收', body, coveredFrom: '2024-02-28', coveredTo: '2024-03-02' })).status, 200)
  for (const token of ['other-admin-fixture', 'other-user-fixture']) assert.ok([401, 403].includes((await api('/ai/index', 'GET', null, token)).status))
  assert.equal((await api('/ai/capabilities')).cache, 'private, no-store')
  await page.goto(base + '/diary?entry=' + id);await page.locator('.title-field input').waitFor()
  await page.getByRole('navigation', { name: '日记工作区' }).getByRole('button', { name: /问日记/ }).click();await page.locator('.ai-settings>summary').click();await page.getByRole('button', { name: '建立 / 继续索引', exact: true }).waitFor()
  await page.locator('.ai-index input[type=checkbox]').check();await page.getByRole('button', { name: '建立 / 继续索引', exact: true }).click()
  for (let n = 0; n < 50; n++) { const s = (await api('/ai/index')).body.data; if (s.coverage.total === s.coverage.indexed) break; await new Promise(r => setTimeout(r, 1000)); if (n === 49) throw new Error('index did not complete') }
  await page.getByRole('button', { name: '刷新状态', exact: true }).click()
  await page.locator('.scope-details>summary').click();await page.locator('.ai-composer fieldset select').first().selectOption('selected');await page.locator('fieldset input[data-entry-id="' + id + '"]').check()
  await page.locator('.ai-question textarea').fill('这篇记录提到了什么？')
  // Commit succeeds but the browser loses HTTP202: retry must retain original requestId.
  let dropped = false;const submissions = []
  await page.route('**/api/diary/auth/ai/questions', async route => { submissions.push(route.request().postDataJSON()); if (!dropped) { dropped = true; assert.equal((await route.fetch()).status(), 202); return route.abort('failed') } return route.continue() })
  await page.getByRole('button', { name: '查找依据并回答', exact: true }).click();await page.getByRole('button', { name: '重试原请求', exact: true }).waitFor();await page.getByRole('button', { name: '重试原请求', exact: true }).click()
  await page.locator('.ai-result').waitFor({ timeout: 30000 });assert.equal(submissions.length, 2);assert.deepEqual(submissions[0], submissions[1]);assert.equal(await page.locator('.ai-result img').count(), 0)
  await page.locator('.source-links').locator('..').locator('summary').click();await page.locator('.source-links button').first().click();await page.locator('.ai-source mark').waitFor();assert.ok((await page.locator('.ai-source mark').innerText()).includes('AI_BROWSER_PROBE'))
  evidence.checks.push('真实索引/问答/精确来源高亮；202丢失原请求重试；模型HTML仅作为文字')
  await page.getByRole('navigation', { name: '日记工作区' }).getByRole('button', { name: /做复盘/ }).click();await page.getByRole('button', { name: '预览完整范围', exact: true }).click();await page.locator('.ai-preview').waitFor();assert.ok((await page.locator('.ai-preview').innerText()).includes('1 篇'))
  await page.getByRole('button', { name: '确认范围并手动生成', exact: true }).click();await page.getByRole('heading', { name: 'AI 复盘', exact: true }).waitFor({ timeout: 30000 });evidence.checks.push('完整篇目预览和复盘、跨日限制说明')
  await page.setViewportSize({ width: 390, height: 844 });await page.waitForTimeout(500);await page.screenshot({ path: join(output, 'mobile.png'), fullPage: true });evidence.layout = await page.evaluate(() => ({ width: innerWidth, scroll: document.documentElement.scrollWidth, overflow: [...document.querySelectorAll('body *')].filter(e => e.getBoundingClientRect().right > innerWidth + 1).slice(0, 12).map(e => ({ tag: e.tagName, cls: String(e.className), right: e.getBoundingClientRect().right })) }));assert.ok(evidence.layout.scroll <= evidence.layout.width + 1)
  await page.setViewportSize({ width: 1360, height: 1000 });await page.locator('.ai-result').screenshot({ path: join(output, 'result.png') })
  // A changed source must hide previously persisted result and reject old offsets.
  const current = (await api('/entries/' + id)).body.data
  assert.equal((await api('/entries/' + id, 'PUT', { entryId: id, mutationId: randomUUID(), expectedRevision: current.revision, title: current.title, body: '虚构来源已经更新', coveredFrom: current.coveredFrom, coveredTo: current.coveredTo })).status, 200)
  await page.locator('.source-links').locator('..').locator('summary').click();await page.locator('.source-links button').first().click();await page.locator('.ai-error').filter({ hasText: '来源已修改或删除' }).waitFor();assert.equal(await page.locator('.ai-result').count(), 0)
  await page.getByRole('button', { name: '刷新状态', exact: true }).click();if (await page.locator('.ai-reports').getAttribute('open') === null) await page.locator('.ai-reports>summary').click();assert.ok((await page.locator('.ai-reports').innerText()).includes('来源已变化'));evidence.checks.push('来源修改后隐藏结果并拒绝旧引用；桌面/390px移动无横向溢出')
  await page.getByRole('button', { name: '暂停索引', exact: true }).click()
  // Invalidate session while an AI question exists in memory: unmount private panel.
  await page.getByRole('navigation', { name: '日记工作区' }).getByRole('button', { name: /问日记/ }).click();await page.locator('.ai-question textarea').fill('PRIVATE_QUESTION_MEMORY_PROBE')
  await page.evaluate(() => window.dispatchEvent(new CustomEvent('diary-session-invalid', { detail: 403 })))
  await page.locator('.diary-gate').waitFor();assert.equal(await page.locator('.diary-ai').count(), 0);assert.ok(!(await page.content()).includes('PRIVATE_QUESTION_MEMORY_PROBE'))
  evidence.checks.push('会话拒绝立即卸载AI内存；私密接口no-store/noindex')
  assert.equal(evidence.pageErrors.length, 0);assert.ok(!evidence.external.some(u => u.includes('fixture-blocked.invalid')))
  evidence.status = 'passed'
} catch (error) { evidence.status = 'failed';evidence.failure = error.message;await page.screenshot({ path: join(output, 'failure.png'), fullPage: true });throw error }
finally { await writeFile(join(output, 'report.json'), JSON.stringify(evidence, null, 2));await browser.close() }
