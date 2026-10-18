// Isolated UI regression: real Vue workspace, fictional same-origin API fixtures only.
// No production endpoint, database, credential, or cloud-model call is used.
import { createRequire } from 'node:module'
import { mkdir, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { randomUUID } from 'node:crypto'
import { pathToFileURL } from 'node:url'
import assert from 'node:assert/strict'

const require = createRequire(import.meta.url)
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright')
const fixturePort = Number(process.env.DIARY_UI_PORT || 15173)
assert.ok([15173, 15174].includes(fixturePort), 'Only dedicated loopback dev/preview ports are allowed')
const base = `http://127.0.0.1:${fixturePort}`
const output = process.env.DIARY_QA_OUTPUT
assert.ok(output, 'DIARY_QA_OUTPUT must point to a dedicated local evidence directory')
await mkdir(output, { recursive: true })
assert.equal(new URL(base).hostname, '127.0.0.1')
const auditPort = Number(process.env.DIARY_UI_AUDIT_PORT || 0)
assert.ok(!auditPort || Number.isInteger(auditPort) && auditPort >= 1024 && auditPort <= 65535)
const browser = await chromium.launch({ headless: true, args: auditPort ? [`--remote-debugging-port=${auditPort}`, '--remote-debugging-address=127.0.0.1'] : [], ...(process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE ? { executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE } : {}) })
const context = await browser.newContext({ viewport: { width: 1440, height: 1050 }, reducedMotion: 'reduce' })
const evidence = { status: 'running', checks: [], pageErrors: [], unmatchedApi: [], outboundBlocked: [] }
const id = randomUUID(), now = '2026-10-16T08:30:00Z'
let entry = { id, title: '阅读开始变得容易的一周（虚构示例）', body: '【虚构验收资料】我把书放在桌面，每天先读十分钟。关掉通知之后，更容易开始阅读。', coveredFrom: '2026-10-05', coveredTo: '2026-10-11', revision: 1, createdAt: now, updatedAt: now }
const statement = { text: '你记录了两种有帮助的做法：把书放在容易拿到的位置，以及先关掉通知、只读十分钟。它们降低了开始阅读的阻力。', evidenceIds: ['S1'], quotes: [{ sourceId: 'S1', text: '我把书放在桌面，每天先读十分钟。关掉通知之后，更容易开始阅读。' }] }
const result = { answer: statement, summary: null, observations: [{ ...statement, text: '这篇记录提到了环境准备与短时间开始。' }], hypotheses: [{ ...statement, text: '减少打断可能有助于开始，但一篇记录不足以确认因果。' }], suggestions: [{ action: '下次阅读前先把手机通知关闭十分钟', reason: '可以把这次提到的做法再试一次，并记录真实感受。', evidenceIds: ['S1'], effort: '约十分钟', reviewAfter: '下一次阅读结束后', general: false }], limitations: ['这是虚构的界面验收资料，不代表真实经历。', '记录覆盖多天，不能据此推算每天都有执行。'], insufficientEvidence: false }
const reports = new Map()
function makeReport(reportId, kind, value = result) {
  return { id: reportId, kind, result: JSON.stringify(value), model: 'fictional-ui-model', createdAt: now, partial: false, sources: [{ sourceId: 'S1', entryId: id, revision: entry.revision, title: entry.title, start: 0, end: Array.from(entry.body).length, text: entry.body, coveredFrom: entry.coveredFrom, coveredTo: entry.coveredTo }] }
}
const historicalId = randomUUID()
reports.set(historicalId, makeReport(historicalId, 'QUESTION', { ...result, answer: { ...statement, text: '这是一份历史问答的回答，原问题不在旧报告协议中。' } }))
const jobs = new Map(), submissions = [], byRequest = new Map()
let dropFirstSubmission = true, invalidSource = false, indexStarts = 0, nextQuestionFailure
await context.addInitScript(() => localStorage.setItem('Token', JSON.stringify({ token: 'workspace-ui-fixture', expire: new Date(Date.now() + 3600000).toISOString() })))
await context.route('**/*', async route => {
  const request = route.request(), url = new URL(request.url())
  if (url.origin !== base) { evidence.outboundBlocked.push(url.origin); return route.abort() }
  if (!url.pathname.startsWith('/api/')) return route.continue()
  const path = url.pathname.slice(4), method = request.method()
  const ok = data => route.fulfill({ status: 200, contentType: 'application/json', headers: { 'cache-control': 'private, no-store' }, body: JSON.stringify({ code: 200, data }) })
  if (path === '/user/auth/info') return ok({ id: 7, username: 'owner-fixture', nickname: '本地虚构账号', roles: ['ROLE_ADMIN'], avatar: '', email: 'fixture@example.invalid', registerType: 0 })
  if (path === '/websiteInfo/front') return ok({ websiteName: '日记工作区本地验收', webmasterAvatar: '', recordNumber: '' })
  if (path === '/site-modules/access') return ok(['home', 'experience', 'blog', 'shares', 'photos', 'about', 'diary'].map(key => ({ key, name: key, allowed: true, publicAccess: true })))
  if (['/category/list', '/category/front/list', '/banners/list'].includes(path)) return ok([])
  if (path === '/diary/view/capabilities') return ok({ allowed: true, available: true, canManage: true })
  if (path === '/diary/auth/capabilities') return ok({ allowed: true, available: true })
  if (path === '/diary/auth/entries/search') return ok({ records: [entry], total: 1, page: 1, pageSize: 20 })
  if (path === '/diary/auth/calendar') return ok([{ date: '2026-10-16', count: 1 }])
  if (path === '/diary/auth/entries/' + id && method === 'GET') return ok(invalidSource ? { ...entry, revision: entry.revision + 1, body: '来源已修改（虚构）' } : entry)
  if (path === '/diary/auth/entries/' + id && method === 'PUT') {
    const body = request.postDataJSON(); entry = { ...entry, ...body, id, revision: entry.revision + 1, updatedAt: now }
    return ok({ entryId: id, mutationId: body.mutationId, revision: entry.revision, savedAt: now })
  }
  if (path === '/diary/auth/ai/capabilities') return ok({ enabled: true, available: true, message: '虚构服务数据说明：不会连接任何真实模型。' })
  if (path === '/diary/auth/ai/index' && method === 'GET') return ok({ syncing: true, coverage: { total: 16, indexed: 16, failed: 0 }, remainingBlocks: 0, message: '虚构索引已完成。' })
  if (path === '/diary/auth/ai/index' && method === 'POST') { indexStarts++; return ok({ id: randomUUID(), kind: 'INDEX_START', state: 'SUCCEEDED' }) }
  if (path === '/diary/auth/ai/reports') return ok({ records: [...reports.values()].map(r => ({ id: r.id, kind: r.kind, state: 'SUCCEEDED', entries: 1, model: r.model, createdAt: r.createdAt })), total: reports.size })
  if (path.startsWith('/diary/auth/ai/reports/')) return ok(reports.get(path.split('/').at(-1)))
  if (path === '/diary/auth/ai/reviews/preview') return ok({ entries: 1, characters: 300, snapshotDigest: 'a'.repeat(64), records: [{ id, title: entry.title }], limitation: '跨日篇目不等于逐日都有执行。' })
  if (['/diary/auth/ai/questions', '/diary/auth/ai/reviews'].includes(path)) {
    const body = request.postDataJSON(), kind = path.endsWith('questions') ? 'QUESTION' : 'REVIEW'
    submissions.push(body)
    let job = byRequest.get(body.requestId)
    if (!job) {
      job = { id: randomUUID(), kind, state: 'QUEUED', message: '请求已排队', possiblyCharged: false }
      if (kind === 'QUESTION' && nextQuestionFailure) {
        jobs.set(job.id, { ...job, ...nextQuestionFailure, possiblyCharged: true });nextQuestionFailure = undefined
      } else {
        const reportId = randomUUID(), value = kind === 'QUESTION' ? result : { ...result, answer: null, summary: { ...statement, text: '这一段记录围绕阅读习惯展开，环境准备和减少通知是主要线索。' } }
        reports.set(reportId, makeReport(reportId, kind, value)); jobs.set(job.id, { ...job, reportId })
      }
      byRequest.set(body.requestId, job)
    }
    if (dropFirstSubmission && kind === 'QUESTION') { dropFirstSubmission = false; return route.abort('failed') }
    return route.fulfill({ status: 202, contentType: 'application/json', body: JSON.stringify({ code: 200, data: job }) })
  }
  if (path.startsWith('/diary/auth/ai/jobs/')) {
    const job = jobs.get(path.split('/').at(-1))
    return ok(['FAILED', 'UNCERTAIN'].includes(job.state) ? job : { ...job, state: 'SUCCEEDED', message: '已完成' })
  }
  if (path === '/diary/auth/coaching/capabilities') return ok({ enabled: true, available: true, aiAvailable: true, message: '虚构目标反馈资料，仅用于本地验收。' })
  if (/^\/diary\/auth\/coaching\/(goals|actions|feedback)$/.test(path)) return ok({ records: [], total: 0 })
  if (path === '/diary/auth/coaching/periods/reports') return ok({ records: [], total: 0 })
  evidence.unmatchedApi.push(path)
  return ok([])
})
const page = await context.newPage()
page.on('pageerror', e => evidence.pageErrors.push(e.message))
const nav = () => page.getByRole('navigation', { name: '日记工作区', exact: true })
const openSection = name => nav().getByRole('button', { name: new RegExp(name) }).click()
async function screenshot(name) { await page.evaluate(() => window.scrollTo(0, 0)); await page.screenshot({ path: join(output, name + '.png'), fullPage: true }) }
try {
  await page.goto(base + '/diary?entry=' + id)
  await page.locator('.title-field input').waitFor()
  assert.equal(await nav().getByRole('button').count(), 4)
  assert.equal(await page.locator('.diary-ai').isVisible(), false)
  await screenshot('writing-desktop')
  // A dirty diary stays mounted across workspace changes; AI never analyzes its draft.
  await page.locator('.title-field input').fill('未保存的虚构标题')
  await openSection('问日记')
  await page.locator('.diary-ai .ai-error').filter({ hasText: '请先在“写日记”中保存' }).waitFor()
  assert.equal(await page.getByRole('button', { name: '查找依据并回答', exact: true }).isDisabled(), true)
  await openSection('写日记')
  assert.equal(await page.locator('.title-field input').inputValue(), '未保存的虚构标题')
  await page.getByRole('button', { name: '保存 / 重试', exact: true }).click()
  await page.getByText('已保存', { exact: true }).waitFor()
  evidence.checks.push('four sections; diary draft retained during switching; dirty draft blocks AI')
  await openSection('问日记')
  assert.equal(await page.locator('.ai-settings').getAttribute('open'), null)
  assert.equal(await page.locator('.ai-reports').getAttribute('open'), null)
  await page.locator('.ai-settings>summary').click()
  assert.equal(await page.getByRole('button', { name: '建立 / 继续索引', exact: true }).isDisabled(), true)
  await page.getByLabel('我已了解云端数据流，允许建立索引').check()
  await page.getByRole('button', { name: '建立 / 继续索引', exact: true }).click()
  assert.equal(indexStarts, 1)
  await page.locator('.ai-settings>summary').click()
  const originalQuestion = '这段记录中，哪些做法帮助我开始阅读？'
  await page.getByLabel('问题', { exact: true }).fill(originalQuestion)
  await page.getByRole('button', { name: '查找依据并回答', exact: true }).click()
  await page.getByRole('button', { name: '重试原请求', exact: true }).waitFor()
  await page.getByRole('button', { name: '重试原请求', exact: true }).click()
  await page.getByRole('heading', { name: 'AI 回答', exact: true }).waitFor({ timeout: 12000 })
  assert.deepEqual(submissions[0], submissions[1])
  assert.equal(await page.locator('.answer-question p').innerText(), originalQuestion)
  await screenshot('answer-desktop')
  await page.screenshot({ path: join(output, 'answer-overview.png'), fullPage: false })
  await page.getByLabel('问题', { exact: true }).fill('这是还没有提交的新问题')
  assert.equal(await page.locator('.answer-question p').innerText(), originalQuestion)
  assert.equal(await page.locator('.answer-main>p').innerText(), statement.text)
  assert.equal(await page.locator('.answer-main>details').getAttribute('open'), null)
  assert.equal(await page.locator('.ai-result img').count(), 0)
  evidence.checks.push('acknowledgment loss retries the identical request; answer keeps original question after input editing; quotes collapsed')
  await page.locator('.source-links').locator('..').locator('summary').click()
  await page.locator('.source-links button').first().click()
  await page.locator('.ai-source mark').waitFor()
  assert.equal(await page.locator('.ai-source mark').innerText(), reports.get([...reports.keys()].at(-1)).sources[0].text)
  await page.getByRole('button', { name: '关闭原文', exact: true }).click()
  await page.locator('.ai-reports>summary').click()
  await page.locator('.ai-report-row').first().getByRole('button', { name: '查看', exact: true }).click()
  await page.getByText('正在查看过去生成的回答。这份历史报告没有附带原问题。', { exact: true }).waitFor()
  assert.ok(!(await page.locator('.answer-question').innerText()).includes('这是还没有提交的新问题'))
  evidence.checks.push('source verification and highlighting; historical report never borrows the current question')
  await openSection('做复盘')
  assert.equal(await page.getByRole('heading', { name: 'AI 回答', exact: true }).isVisible(), false)
  await page.getByRole('button', { name: '预览完整范围', exact: true }).click()
  await page.locator('.ai-preview').waitFor()
  await page.getByRole('button', { name: '确认范围并手动生成', exact: true }).click()
  await page.getByRole('heading', { name: 'AI 复盘', exact: true }).waitFor({ timeout: 12000 })
  await screenshot('review-desktop')
  await page.getByRole('button', { name: /周／月回顾 结合目标/ }).click()
  await page.getByRole('heading', { name: '周／月回顾', exact: true }).waitFor()
  await openSection('目标行动')
  await page.getByRole('heading', { name: '我的目标', exact: false }).waitFor()
  await page.getByRole('button', { name: '新目标', exact: true }).click()
  await page.getByLabel('目标标题', { exact: true }).fill('仅存在内存的虚构目标')
  await openSection('问日记'); await openSection('目标行动')
  assert.equal(await page.getByLabel('目标标题', { exact: true }).inputValue(), '仅存在内存的虚构目标')
  await screenshot('goals-desktop')
  evidence.checks.push('diary review isolated from questions; period review discoverable; goal draft survives section switching')
  await openSection('问日记')
  // Reopen the current answer, then make its source stale.
  if (await page.locator('.ai-reports').getAttribute('open') === null) await page.locator('.ai-reports>summary').click()
  await page.locator('.ai-report-row').nth(1).getByRole('button', { name: '查看', exact: true }).click()
  invalidSource = true
  await page.locator('.source-links').locator('..').locator('summary').click()
  await page.locator('.source-links button').first().click()
  await page.locator('.ai-error').filter({ hasText: '来源已修改或删除' }).waitFor()
  assert.equal(await page.locator('.ai-result').count(), 0)
  evidence.checks.push('changed source hides the whole old answer without showing a misleading success panel')
  invalidSource = false
  await page.locator('.ai-report-row').nth(1).getByRole('button', { name: '查看', exact: true }).click()
  await page.locator('.ai-reports>summary').click()
  // Viewports and themes use the existing site theme attribute; all data remains fictional.
  for (const dark of [false, true]) {
    await page.evaluate(dark => document.documentElement.classList.toggle('dark', dark), dark)
    const primary = await page.getByRole('button', { name: '查找依据并回答', exact: true }).evaluate(el => ({ foreground: getComputedStyle(el).color, background: getComputedStyle(el).backgroundColor }))
    const luminance = color => color.match(/[\d.]+/g).slice(0, 3).map(Number).map(c => c / 255).map(c => c <= .04045 ? c / 12.92 : ((c + .055) / 1.055) ** 2.4).reduce((sum, c, i) => sum + c * [.2126, .7152, .0722][i], 0)
    const luminances = [luminance(primary.foreground), luminance(primary.background)].sort((a, b) => b - a)
    const contrast = (luminances[0] + .05) / (luminances[1] + .05)
    assert.ok(contrast >= 4.5, `primary CTA contrast ${contrast}: ${JSON.stringify(primary)}`)
    evidence.checks.push(`${dark ? 'dark' : 'light'} primary CTA measured contrast ${contrast.toFixed(2)}:1`)
    for (const width of [1440, 768, 390, 360]) {
      await page.setViewportSize({ width, height: width < 768 ? 900 : 1050 })
      await page.locator('.response-zone').scrollIntoViewIfNeeded()
      const layout = await page.evaluate(() => ({ width: innerWidth, scroll: document.documentElement.scrollWidth }))
      assert.ok(layout.scroll <= layout.width + 1, `overflow: ${JSON.stringify({ dark, ...layout })}`)
      for (const selector of ['.workspace-nav', '.ai-composer', '.ai-result']) assert.equal(await page.locator(selector).evaluate(el => el.scrollWidth <= el.clientWidth + 1), true, `${selector} clips content at ${width}px`)
      await screenshot(`answer-${dark ? 'dark' : 'light'}-${width}`)
    }
  }
  evidence.checks.push('1440/768/390/360px layouts in both themes without horizontal page overflow')
  if (process.env.DIARY_LIGHTHOUSE_MODULE) {
    assert.ok(auditPort && process.env.DIARY_PUPPETEER_MODULE, 'Optional audit requires isolated debugging port and local tool packages')
    const { startFlow } = await import(pathToFileURL(process.env.DIARY_LIGHTHOUSE_MODULE).href)
    const puppeteer = require(process.env.DIARY_PUPPETEER_MODULE)
    const auditPage = await context.newPage(), auditUrl = base + '/diary?entry=' + id + '&uiAudit=true'
    await auditPage.setViewportSize({ width: 1440, height: 1050 })
    await auditPage.goto(auditUrl)
    await auditPage.locator('.title-field input').waitFor()
    const connection = await puppeteer.connect({ browserURL: `http://127.0.0.1:${auditPort}`, defaultViewport: null })
    try {
      const target = (await connection.pages()).find(p => p.url() === auditUrl)
      assert.ok(target)
      const flow = await startFlow(target, { name: 'Private diary workspace with fictional API fixtures', config: { extends: 'lighthouse:default', settings: { onlyCategories: ['performance', 'accessibility', 'best-practices'], disableStorageReset: true, formFactor: 'desktop', screenEmulation: { disabled: true }, throttlingMethod: 'provided', maxWaitForLoad: 15000 } } })
      await flow.navigate(auditUrl, { stepName: 'Writing workspace navigation' })
      await auditPage.getByRole('navigation', { name: '日记工作区' }).getByRole('button', { name: /问日记/ }).click()
      await auditPage.locator('.ai-reports>summary').click()
      await auditPage.locator('.ai-report-row').nth(1).getByRole('button', { name: '查看', exact: true }).click()
      await auditPage.getByRole('heading', { name: 'AI 回答', exact: true }).waitFor()
      await flow.snapshot({ stepName: 'Answer reading and collapsed citations' })
      const audit = await flow.createFlowResult()
      const summary = audit.steps.map(step => ({ name: step.name, scores: Object.fromEntries(Object.entries(step.lhr.categories).map(([key, value]) => [key, value.score])), metrics: Object.fromEntries(['first-contentful-paint', 'largest-contentful-paint', 'cumulative-layout-shift', 'total-blocking-time'].filter(key => step.lhr.audits[key]).map(key => [key, step.lhr.audits[key].numericValue])), findings: Object.entries(step.lhr.audits).filter(([, value]) => value.score !== null && value.score < 1).map(([key, value]) => ({ id: key, title: value.title, score: value.score, description: value.displayValue || '', details: value.details })) }))
      await writeFile(join(output, 'lighthouse.json'), JSON.stringify(summary, null, 2))
      await writeFile(join(output, 'lighthouse.html'), await flow.generateReport())
      evidence.checks.push('Lighthouse navigation and rendered-answer snapshot completed with fictional data; see lighthouse.json for scores and findings')
    } finally { connection.disconnect(); await auditPage.close() }
  }
  const failureStart = submissions.length
  const failures = [
    ['FAILED', '内容生成阶段：模型输出达到上限而被截断，没有展示不完整结果。'],
    ['UNCERTAIN', '检索准备（问题向量）阶段：网络连接异常，不会自动重发。'],
    ['FAILED', '内容生成阶段：模型没有返回有效正文。'],
    ['FAILED', '结果校验未通过：模型的结构或原文引用不符合要求。'],
    ['FAILED', '历史生成响应未通过检查，原记录不足以区分截断、空回答或结构异常。'],
    ['FAILED', '内容生成阶段：供应商拒绝认证，请检查后端配置。'],
  ]
  for (const [state, message] of failures) {
    nextQuestionFailure = { state, message }
    const input = '虚构的新问题 ' + (submissions.length - failureStart)
    await page.getByLabel('问题', { exact: true }).fill(input)
    const before = submissions.length
    await page.getByRole('button', { name: '查找依据并回答', exact: true }).click()
    await page.locator('.ai-job').getByText(message, { exact: true }).waitFor({ timeout: 12000 })
    assert.equal(await page.locator('.ai-result').count(), 0)
    assert.ok((await page.locator('.retry-guidance').innerText()).includes('创建新任务'))
    assert.equal(submissions.at(-1).question, input)
    await page.waitForTimeout(2300)
    assert.equal(submissions.length, before + 1, 'failure and polling must not resend a paid request')
  }
  assert.equal(new Set(submissions.slice(failureStart).map(s => s.requestId)).size, failures.length)
  evidence.checks.push('stage-specific failures and legacy fallback; no partial answer; manual terminal retry gets a new requestId; no automatic resubmission')
  await screenshot('failure-guidance-mobile-dark')
  await page.evaluate(() => window.dispatchEvent(new CustomEvent('diary-session-invalid', { detail: 403 })))
  await page.locator('.diary-gate').waitFor()
  assert.equal(await page.locator('.diary-ai').count(), 0)
  assert.equal(await page.locator('.coaching').count(), 0)
  assert.ok(!(await page.content()).includes('仅存在内存的虚构目标'))
  assert.deepEqual(evidence.pageErrors, [])
  assert.deepEqual(evidence.unmatchedApi, [])
  evidence.checks.push('session rejection unmounts all private panels, including hidden drafts')
  evidence.status = 'passed'
} catch (error) {
  evidence.status = 'failed'; evidence.error = String(error)
  await screenshot('failure')
  throw error
} finally {
  await writeFile(join(output, 'report.json'), JSON.stringify(evidence, null, 2))
  await browser.close()
}
