// Actual browser -> local Spring security -> isolated PG -> fictional loopback model.
import { createRequire } from 'node:module'
import { mkdir, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { randomUUID } from 'node:crypto'
import assert from 'node:assert/strict'
const require = createRequire(import.meta.url), { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright')
const base = 'http://127.0.0.1:15173', output = process.env.DIARY_QA_OUTPUT
assert.ok(output, 'DIARY_QA_OUTPUT required'); await mkdir(output, { recursive: true })
assert.equal((await (await fetch(base + '/api/websiteInfo/front')).json()).data.websiteName, '本地日记AI验收')
const browser = await chromium.launch({ headless: true, executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE })
const result = { checks: [], pageErrors: [], external: [] }
const ctx = await browser.newContext({ viewport: { width: 1360, height: 1000 } })
await ctx.route('**/*', route => { if (new URL(route.request().url()).origin === base) return route.continue(); result.external.push(route.request().url()); return route.abort() })
await ctx.addInitScript(() => localStorage.setItem('Token', JSON.stringify({ token: 'owner-fixture', expire: new Date(Date.now() + 3600000).toISOString() })))
const page = await ctx.newPage(); page.on('pageerror', e => result.pageErrors.push(e.message))
async function api(path, method = 'GET', data, token = 'owner-fixture') {
  const response = await fetch(base + '/api/diary/auth' + path, { method, headers: { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' }, ...(data ? { body: JSON.stringify(data) } : {}) })
  return { status: response.status, cache: response.headers.get('cache-control'), body: await response.json() }
}
const panel = page.locator('.coaching'), editor = panel.locator('.editor')
try {
  const entry = randomUUID(), suffix = randomUUID().slice(0, 8)
  const today = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date())
  assert.equal((await api('/entries', 'POST', { entryId: entry, mutationId: randomUUID(), expectedRevision: 0, title: 'Fictional coaching browser ' + suffix, body: 'Fictional private diary. No actual personal information.', coveredFrom: today, coveredTo: today })).status, 200)
  for (const token of ['', 'other-admin-fixture']) {
    const response = await api('/coaching/goals', 'GET', undefined, token)
    assert.ok([401, 403].includes(response.status)); assert.equal(response.cache, 'private, no-store')
  }
  await page.goto(base + '/diary?entry=' + entry); await page.getByRole('navigation', { name: '日记工作区' }).getByRole('button', { name: /目标行动/ }).click(); await panel.getByRole('button', { name: '新目标', exact: true }).waitFor()
  await panel.getByRole('button', { name: '新目标', exact: true }).click()
  await editor.getByLabel('目标标题').fill('Fictional watercolor goal ' + suffix)
  await editor.getByLabel('怎样判断有帮助').fill('Use my actual feedback, not planned actions.')
  // The server commits but the browser loses the ACK. Retry must reuse mutation ID.
  let lostMutation
  await page.route('**/api/diary/auth/coaching/goals', async route => {
    if (route.request().method() !== 'POST' || lostMutation) return route.continue()
    lostMutation = route.request().postDataJSON().mutationId; await route.fetch(); await route.abort()
  })
  await editor.getByRole('button', { name: '确认保存', exact: true }).click()
  await editor.getByRole('button', { name: '用原请求再次确认', exact: true }).waitFor()
  await editor.getByRole('button', { name: '用原请求再次确认', exact: true }).click()
  await editor.waitFor({ state: 'hidden' }); assert.ok(lostMutation)
  const goals = (await api('/coaching/goals?size=100')).body.data.records.filter(r => r.content.title.endsWith(suffix)); assert.equal(goals.length, 1)
  const goal = goals[0]; result.checks.push('lost ACK retry produces one goal')
  await panel.getByRole('button', { name: goal.content.title, exact: true }).click()
  await panel.getByRole('button', { name: '新行动', exact: true }).click(); await editor.getByLabel('准备尝试的具体行动').fill('Fictional practice ' + suffix)
  await editor.getByRole('button', { name: '确认保存', exact: true }).click(); await editor.waitFor({ state: 'hidden' })
  await panel.getByRole('button', { name: '记录反馈', exact: true }).click(); await editor.getByLabel('实际执行', { exact: true }).selectOption('COMPLETED')
  await editor.getByLabel('本人感受到的效果').selectOption('HELPFUL'); await editor.getByLabel('实际发生日期（不知道就留空）').fill(today)
  await editor.getByLabel('说明', { exact: true }).fill('Fictional first-hand feedback ' + suffix)
  await editor.getByLabel('同时把这项行动明确标为已完成', { exact: false }).check()
  await editor.getByRole('button', { name: '确认保存', exact: true }).click(); await editor.waitFor({ state: 'hidden' })
  const action = (await api('/coaching/actions?parent=' + goal.id)).body.data.records[0]; assert.equal(action.content.state, 'COMPLETED')
  result.checks.push('goal -> action -> dated helpful feedback -> explicit action completion')
  // Two-tab version conflict preserves the local draft and forbids force-overwrite.
  await panel.locator('.practice-column').first().locator('article').filter({ has: page.getByRole('button', { name: goal.content.title, exact: true }) }).getByRole('button', { name: '修改与状态', exact: true }).click()
  await editor.getByLabel('目标标题').fill('Local unsaved conflict ' + suffix)
  const current = (await api('/coaching/goals/' + goal.id)).body.data
  assert.equal((await api('/coaching/goals/' + goal.id, 'PUT', { id: goal.id, mutationId: randomUUID(), expectedRevision: current.revision, content: { ...current.content, state: 'PAUSED' } })).status, 200)
  await editor.getByRole('button', { name: '确认保存', exact: true }).click(); await editor.getByText('服务端版本已变化', { exact: false }).waitFor()
  assert.equal(await editor.getByLabel('目标标题').inputValue(), 'Local unsaved conflict ' + suffix)
  await editor.getByRole('button', { name: '取消编辑' }).click(); await page.getByRole('button', { name: '放弃并离开', exact: true }).click()
  result.checks.push('409 preserves local draft')
  await page.getByRole('navigation', { name: '日记工作区' }).getByRole('button', { name: /做复盘/ }).click(); await page.getByRole('button', { name: /周／月回顾 结合目标/ }).click()
  for (const check of await panel.locator('.period-form fieldset input[type=checkbox]').all()) if (await check.isChecked()) await check.uncheck()
  const chosen = panel.locator('fieldset input[type=checkbox][value="' + goal.id + '"]'); await chosen.check()
  await panel.getByRole('button', { name: '查看范围与真实汇总', exact: true }).click(); await panel.getByText('已尝试 0 · 已完成 1', { exact: false }).waitFor()
  await panel.getByLabel('我已阅读上方模型与数据处理说明', { exact: false }).check(); await panel.getByRole('button', { name: '生成本次回顾', exact: true }).click()
  await panel.locator('.report').waitFor({ timeout: 30000 }); assert.equal(await panel.locator('.report img').count(), 0)
  await panel.getByRole('button', { name: '核验原文', exact: false }).first().click(); await panel.locator('.source-highlight mark').waitFor()
  result.checks.push('period preview -> single worker -> typed plaintext evidence and source verification')
  await panel.getByRole('button', { name: '打开可编辑的行动草稿', exact: true }).first().click(); await editor.waitFor()
  const before = (await api('/coaching/actions?parent=' + goal.id)).body.data.total
  await editor.getByRole('button', { name: '取消编辑' }).click(); await page.getByRole('button', { name: '放弃并离开', exact: true }).click()
  assert.equal((await api('/coaching/actions?parent=' + goal.id)).body.data.total, before); result.checks.push('canceling suggestion draft creates no action')
  // Re-open suggestion and explicitly adopt.
  await page.getByRole('navigation', { name: '日记工作区' }).getByRole('button', { name: /做复盘/ }).click(); await page.getByRole('button', { name: /周／月回顾 结合目标/ }).click(); await panel.getByRole('button', { name: '打开可编辑的行动草稿', exact: true }).first().click()
  await editor.getByLabel('准备尝试的具体行动').fill('Fictional confirmed adoption ' + suffix)
  await editor.getByRole('button', { name: '确认保存', exact: true }).click(); await editor.waitFor({ state: 'hidden' })
  assert.equal((await api('/coaching/actions?parent=' + goal.id)).body.data.total, before + 1); result.checks.push('explicit edited adoption creates exactly one action')
  for (const width of [360, 390, 430]) {
    await page.setViewportSize({ width, height: 900 }); await panel.scrollIntoViewIfNeeded()
    assert.equal(await panel.evaluate(el => el.scrollWidth <= el.clientWidth + 1), true, 'coaching panel overflow at ' + width)
    await page.screenshot({ path: join(output, 'coaching-' + width + '.png'), fullPage: true })
  }
  result.checks.push('360/390/430px no horizontal panel overflow')
  await page.setViewportSize({ width: 1360, height: 1000 }); await panel.getByRole('button', { name: '新目标', exact: true }).click(); await editor.getByLabel('目标标题').fill('must disappear after account switch ' + suffix)
  await page.evaluate(async () => { localStorage.setItem('Token', JSON.stringify({ token: 'other-admin-fixture', expire: new Date(Date.now() + 3600000).toISOString() })); window.dispatchEvent(new CustomEvent('diary-session-invalid', { detail: 403 })) })
  await panel.waitFor({ state: 'hidden' }); assert.equal(await page.getByText('must disappear after account switch ' + suffix, { exact: true }).count(), 0)
  result.checks.push('other admin revocation unmounts private panel and draft')
  assert.deepEqual(result.pageErrors, []); assert.ok(!result.external.some(url => url.includes('coaching-probe.invalid')))
  result.status = 'passed'
} catch (error) { result.status = 'failed'; result.error = String(error); await page.screenshot({ path: join(output, 'coaching-failed.png'), fullPage: true }); throw error }
finally { await writeFile(join(output, 'browser.json'), JSON.stringify(result, null, 2)); await browser.close() }
