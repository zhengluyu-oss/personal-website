// 显式本机fixture验收：先运行DiaryBrowserFixture和Vite，不使用任何真实账号。
import { createRequire } from 'node:module'
import { mkdir, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'
const require = createRequire(import.meta.url)
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright')
const base = 'http://127.0.0.1:15173', output = process.env.DIARY_QA_OUTPUT
if (!output) throw new Error('请设置独立的DIARY_QA_OUTPUT测试产物目录')
assert.equal((await (await fetch(base + '/api/websiteInfo/front')).json()).data.websiteName, '本地日记验收', '拒绝向非fixture服务执行写入')
await mkdir(output, { recursive: true })
const browser = await chromium.launch({ headless: true, ...(process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE ? { executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE } : {}) })
const report = { status: 'running', checks: [], screenshots: [], pageErrors: [], externalRequests: [] }
let page, firstWrite
const headers = { Authorization: 'Bearer owner-fixture' }
async function context(token = 'owner-fixture') {
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } })
  await context.route('**/*', route => { const url = route.request().url(); if (new URL(url).origin === base) return route.continue(); report.externalRequests.push(url); return route.abort() })
  await context.addInitScript(token => localStorage.setItem('Token', JSON.stringify({ token, expire: new Date(Date.now() + 3600000).toISOString() })), token)
  return context
}
async function api(path, method = 'GET', data, token = 'owner-fixture') {
  const response = await fetch(base + '/api/diary/auth' + path, { method, headers: { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' }, ...(data ? { body: JSON.stringify(data) } : {}) })
  return { status: response.status, cache: response.headers.get('cache-control'), body: await response.json() }
}
const saved = () => page.waitForFunction(() => document.querySelector('.save-toolbar strong')?.textContent === '已保存', null, { timeout: 20000 })
const editor = () => page.locator('.cm-content[contenteditable=true]')
const confirm = () => page.locator('.el-message-box__btns button').last().click()
try {
  assert.equal((await fetch(base + '/api/diary/auth/capabilities')).status, 401)
  const ctx = await context(); page = await ctx.newPage(); const writes = []
  page.on('pageerror', error => report.pageErrors.push(error.message))
  page.on('request', request => { if (request.url().includes('/api/diary/auth/entries') && ['POST', 'PUT'].includes(request.method())) { try { const data = request.postDataJSON(); if (data?.mutationId && 'body' in data) writes.push(data) } catch {} } })
  await page.goto(base + '/diary'); await page.getByRole('button', { name: '新建日记', exact: true }).waitFor()
  await page.getByRole('button', { name: '新建日记', exact: true }).click(); await editor().waitFor()
  const title = '本地日记验收 ' + Date.now()
  await page.locator('.title-field input').fill(title)
  await page.locator('.coverage input').nth(0).fill('2026-10-01'); await page.locator('.coverage input').nth(1).fill('2026-10-15')
  await editor().fill('# 过去十几天\n\n虚构验收内容🙂，用于测试可靠保存。')
  await page.keyboard.press('Control+s'); await saved()
  const id = new URL(page.url()).searchParams.get('entry'); firstWrite = writes.find(item => item.entryId === id && item.expectedRevision === 0)
  assert.ok(firstWrite); let current = (await api('/entries/' + id)).body.data
  assert.equal(current.body, firstWrite.body); assert.equal(current.coveredFrom, '2026-10-01'); const created = current.createdAt
  report.checks.push('实际创建、日期范围、Unicode、提交确认')
  await page.getByRole('button', { name: '新建日记', exact: true }).click(); await editor().fill('同一天的另一篇，日期留空'); await page.keyboard.press('Control+s'); await saved()
  assert.notEqual(new URL(page.url()).searchParams.get('entry'), id)
  await page.locator('.entry-list button').filter({ hasText: title }).click(); await page.locator('.title-field input').filter({ visible: true }).waitFor()
  await page.waitForFunction(id => new URL(location.href).searchParams.get('entry') === id, id)
  await page.waitForFunction(title => document.querySelector('.title-field input')?.value === title, title)
  await editor().fill('尚未保存的离开保护'); await page.getByRole('button', { name: '新建日记', exact: true }).click()
  await page.getByRole('dialog').filter({ hasText: '还有内容或操作未获确认' }).waitFor(); await page.getByRole('button', { name: '继续编辑', exact: true }).click()
  assert.equal(new URL(page.url()).searchParams.get('entry'), id); assert.ok((await editor().innerText()).includes('离开保护')); await page.keyboard.press('Control+s'); await saved()
  report.checks.push('同日多篇、日期留空、列表切换、未保存离开保护')
  current = (await api('/entries/' + id)).body.data
  assert.equal((await api('/entries/' + id, 'PUT', { entryId: id, mutationId: randomUUID(), expectedRevision: current.revision, title, body: '另一标签页的虚构修改', coveredFrom: current.coveredFrom, coveredTo: current.coveredTo })).status, 200)
  await editor().fill('本地保留的冲突内容'); await page.getByRole('button', { name: '保存 / 重试', exact: true }).click()
  await page.getByText('存在版本冲突', { exact: true }).waitFor(); await page.getByRole('button', { name: '查看服务器内容', exact: true }).click(); await page.locator('.conflict pre').filter({ hasText: '另一标签页' }).waitFor()
  await page.getByRole('button', { name: '确认以本地内容保存新版本', exact: true }).click(); await confirm(); await saved()
  assert.equal((await api('/entries/' + id)).body.data.body, '本地保留的冲突内容'); report.checks.push('真实版本冲突、比较、本地明确选择后保存')
  let dropped = false
  await page.route('**/api/diary/auth/entries/' + id, async route => { if (!dropped && route.request().method() === 'PUT' && route.request().postData()?.includes('ack-loss-fixture')) { dropped = true; assert.equal((await route.fetch()).status(), 200); await route.abort('failed') } else await route.continue() })
  const beforeLoss = (await api('/entries/' + id)).body.data.revision
  await editor().fill('ack-loss-fixture'); await page.keyboard.press('Control+s'); await saved(); assert.ok(dropped)
  const retries = writes.filter(item => item.body === 'ack-loss-fixture'); assert.ok(retries.length >= 2); assert.deepEqual(retries[0], retries[1]); assert.equal((await api('/entries/' + id)).body.data.revision, beforeLoss + 1)
  report.checks.push('数据库提交后丢ACK、原mutation重试、不重复保存')
  await editor().fill('安全预览\n\n![阻止的图片](https://diary-preview-blocked.invalid/private.png)\n\n<img src="https://diary-preview-blocked.invalid/raw.png" onerror="window.__diaryXss=true"><script>window.__diaryXss=true</script><svg onload="window.__diaryXss=true"></svg>')
  await page.keyboard.press('Control+s'); await saved(); await page.getByRole('button', { name: '安全预览', exact: true }).click(); await page.locator('#private-diary-preview').waitFor()
  assert.equal(await page.locator('#private-diary-preview img,#private-diary-preview svg,#private-diary-preview script,#private-diary-preview iframe').count(), 0)
  assert.equal(await page.evaluate(() => window.__diaryXss), undefined); assert.equal(report.externalRequests.filter(url => url.includes('diary-preview-blocked.invalid')).length, 0)
  report.checks.push('安全预览不执行HTML/SVG、外部图片请求为零')
  await page.getByRole('button', { name: '修改历史', exact: true }).click(); await page.locator('.history article').first().waitFor()
  await page.locator('.history article').last().getByRole('button', { name: '恢复为新版本' }).click(); await Promise.all([page.waitForResponse(response => response.url().endsWith('/restore') && response.request().method() === 'POST' && response.status() === 200), confirm()]); await saved()
  current = (await api('/entries/' + id)).body.data; assert.equal(current.body, firstWrite.body); assert.equal(current.coveredFrom, firstWrite.coveredFrom); assert.equal(current.createdAt, created)
  await page.getByRole('button', { name: '删除日记', exact: true }).click(); await confirm(); await page.getByText('这篇日记已删除，可在修改历史中选择版本恢复。', { exact: true }).waitFor(); assert.ok((await api('/entries/' + id)).body.data.deletedAt)
  await page.locator('.history article').last().getByRole('button', { name: '恢复为新版本' }).click(); await Promise.all([page.waitForResponse(response => response.url().endsWith('/restore') && response.request().method() === 'POST' && response.status() === 200), confirm()]); await saved(); assert.ok((await api('/entries/' + id)).body.data.deletedAt == null)
  report.checks.push('真实历史恢复、日期快照、删除墓碑及原ID恢复')
  await page.getByRole('button', { name: '写日记', exact: true }).click()
  const beforeInvalid = (await api('/entries/' + id)).body.data.revision
  await page.locator('.coverage input').nth(0).fill('2026-10-16'); await page.keyboard.press('Control+s')
  await page.locator('.save-message').filter({hasText:'请完整填写起止日期，开始日期不能晚于结束日期'}).waitFor()
  assert.equal((await api('/entries/' + id)).body.data.revision, beforeInvalid)
  await page.locator('.coverage input').nth(0).fill('2026-10-01'); await page.keyboard.press('Control+s'); await saved()
  await page.locator('.diary-browser input[type=month]').fill('2024-02'); await page.locator('.diary-browser input[type=month]').dispatchEvent('change')
  assert.equal(await page.locator('.calendar-grid button').count(), 29)
  await page.locator('.diary-filters select').selectOption('covered')
  await page.locator('.diary-filters input[type=search]').fill(title)
  await page.locator('.diary-filters input[type=date]').nth(0).fill('2026-10-10'); await page.locator('.diary-filters input[type=date]').nth(1).fill('2026-10-12')
  await Promise.all([page.waitForResponse(response => response.url().endsWith('/entries/search') && response.status() === 200), page.getByRole('button', { name: '应用筛选', exact: true }).click()])
  await page.waitForFunction(() => document.querySelectorAll('.entry-list li').length === 1)
  const longTitle = '跨日补写🙂'.repeat(20)
  await page.locator('.title-field input').fill(longTitle); await page.keyboard.press('Control+s'); await saved()
  assert.equal((await api('/entries/' + id)).body.data.title, longTitle)
  await page.locator('.title-field input').fill(title); await page.keyboard.press('Control+s'); await saved()
  let rejectSession = true
  await page.route('**/api/diary/auth/entries/' + id, route => { if (rejectSession && route.request().method() === 'PUT') { rejectSession = false; return route.fulfill({status:401,contentType:'application/json',body:JSON.stringify({code:401,msg:'fixture expired'})}) } return route.fallback() })
  await editor().fill('session-recovery-fixture'); await page.keyboard.press('Control+s'); await page.locator('.diary-gate').waitFor()
  assert.equal(await page.locator('.diary-editor').count(), 0)
  await page.getByRole('button', { name: '重新确认登录与存储状态' }).click(); await editor().waitFor(); assert.ok((await editor().innerText()).includes('session-recovery-fixture'))
  await page.keyboard.press('Control+s'); await saved()
  let offline = true
  await page.route('**/api/diary/auth/entries/' + id, route => offline && route.request().method() === 'PUT' ? route.abort('internetdisconnected') : route.fallback())
  const offlineRevision = (await api('/entries/' + id)).body.data.revision
  await editor().fill('offline-retry-fixture'); await page.keyboard.press('Control+s'); await page.getByText('保存未确认', { exact: true }).waitFor()
  assert.equal((await api('/entries/' + id)).body.data.revision, offlineRevision)
  offline = false; await page.keyboard.press('Control+s'); await saved(); assert.equal((await api('/entries/' + id)).body.data.body, 'offline-retry-fixture')
  report.checks.push('真实界面非法日期拒绝、闰年29天、区间重叠筛选、长中文emoji标题、401锁定同账号恢复及离线原请求重试')
  for (const width of [360, 390, 430, 1280]) {
    await page.setViewportSize({ width, height: 900 }); await page.evaluate(() => window.scrollTo(0, 0)); await page.screenshot({ path: join(output, 'diary-' + width + '.png'), fullPage: true }); report.screenshots.push('diary-' + width + '.png')
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), '横向溢出: ' + width)
  }
  report.checks.push('桌面与360/390/430px无横向溢出')
  const forbidden = await api('/entries/' + id, 'GET', undefined, 'other-admin-fixture'); assert.equal(forbidden.status, 403); assert.equal(forbidden.cache, 'private, no-store'); assert.ok(!JSON.stringify(forbidden.body).includes(firstWrite.body))
  const other = await context('other-admin-fixture'), otherPage = await other.newPage(); await otherPage.goto(base + '/diary'); await otherPage.getByText('当前账号无权访问私密日记', { exact: true }).waitFor(); assert.equal(await otherPage.locator('.diary-editor').count(), 0)
  const persistence = await page.evaluate(() => ({ values: [...Object.values(localStorage), ...Object.values(sessionStorage)], url: location.href, robots: document.querySelector('meta[name=robots]')?.content }))
  assert.ok(!persistence.values.some(value => value.includes(firstWrite.body))); assert.ok(!persistence.url.includes(firstWrite.body)); assert.ok(persistence.robots.includes('noindex'))
  report.checks.push('其他管理员不可访问、正文不写浏览器持久存储或URL、页面禁索引')
  assert.equal(report.pageErrors.length, 0); report.status = 'passed'; report.entryId = id; report.originalWrite = firstWrite; report.current = (await api('/entries/' + id)).body.data
} catch (error) { report.status = 'failed'; report.error = String(error); if (page) await page.screenshot({ path: join(output, 'failure.png'), fullPage: true }).catch(() => {}); throw error }
finally { await writeFile(join(output, 'report.json'), JSON.stringify(report, null, 2)); await browser.close(); console.log(JSON.stringify({ status: report.status, checks: report.checks, output, error: report.error })) }
