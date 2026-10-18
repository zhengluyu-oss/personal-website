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
await context.addInitScript(() => { if (sessionStorage.getItem('module-anonymous')) localStorage.removeItem('Token'); else localStorage.setItem('Token', JSON.stringify({ token: 'workspace-ui-fixture', expire: new Date(Date.now() + 3600000).toISOString() })) })
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

// Module-sharing regression runs the real shell and reader against controlled, non-production fixtures.
let sharedAllowed = true, manages = false, holdDetail = false, releaseDetail, capabilityFailure = false
const viewCalls = []
await context.route('**/api/site-modules/access', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 200, data: ['home','experience','blog','shares','photos','about','diary'].map(key=>({key,name:key,allowed:key==='diary'?sharedAllowed:true,publicAccess:true})) }) }))
await context.route('**/api/diary/view/**', async route => {
 const path=new URL(route.request().url()).pathname.slice('/api/diary/view'.length)
 viewCalls.push({path,authorization:route.request().headers().authorization})
 const ok=data=>route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({code:200,data})})
 if(path==='/capabilities' && capabilityFailure)return route.fulfill({status:503,contentType:'application/json',body:JSON.stringify({code:503,msg:'虚构探针故障'})})
 if(path==='/capabilities')return ok({allowed:sharedAllowed,available:sharedAllowed,canManage:manages})
 if(!sharedAllowed)return route.fulfill({status:403,contentType:'application/json',body:JSON.stringify({code:403,msg:'没有查看日记的权限'})})
 if(path==='/entries/search')return ok({records:[entry],total:1,page:1,pageSize:20})
 if(path==='/entries/'+id){if(holdDetail)await new Promise(resolve=>{releaseDetail=resolve});return ok(entry)}
 if(path==='/calendar')return ok([{date:'2026-10-16',count:1}])
 if(/^\/reports\/(ai|period)$/.test(path))return ok({records:[{id:historicalId,kind:path.endsWith('period')?'PERIOD':'QUESTION',createdAt:now,model:'fictional-ui-model'}],total:1,page:1,pageSize:20})
 if(path.startsWith('/reports/ai/'))return ok({...makeReport(historicalId,'QUESTION'),result})
 if(path.startsWith('/reports/period/'))return ok({id:historicalId,kind:'PERIOD',result:{coverage:'虚构期间复盘',currentGoals:[],observations:[{text:'合成观察',quotes:[{sourceId:'D1',text:entry.body}]}],feedback:[],hypotheses:[],suggestions:[],limitations:[]},sources:[{sourceId:'D1',title:'日记原文',text:entry.body}],model:'fixture',createdAt:now})
 if(path.startsWith('/coaching/'))return ok({records:[{id,content:{title:'合成目标',state:'ACTIVE',outcome:'持续阅读'},updatedAt:now}],total:1,page:1,pageSize:20})
 evidence.unmatchedApi.push(path);return ok([])
})
const page=await context.newPage()
page.on('pageerror',error=>evidence.pageErrors.push(error.message))
try {
 await page.setViewportSize({width:1440,height:1000})
 await page.goto(base+'/diary')
 await page.getByText('只读浏览',{exact:true}).waitFor()
 await page.locator('.reader-row').first().click()
 await page.getByRole('heading',{name:entry.title,exact:true}).waitFor()
 assert.equal(await page.locator('.md-editor-input-wrapper,textarea,[contenteditable=true]').count(),0)
 assert.equal(await page.getByRole('button',{name:'新建日记',exact:true}).count(),0)
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true)
 await page.screenshot({path:join(output,'shared-desktop.png')})
 await page.getByRole('button',{name:'问答与复盘',exact:true}).click()
 await page.getByRole('button',{name:/历史问答/}).click()
 await page.getByRole('heading',{name:'AI 回答',exact:true}).waitFor()
 assert.equal(await page.getByRole('button',{name:/生成|采纳|删除|保存/}).count(),0)
 await page.getByText('引用来源',{exact:false}).last().click()
 await page.getByRole('button',{name:new RegExp('S1 ·')}).click()
 await page.getByRole('heading',{name:entry.title,exact:true}).waitFor()
 await page.getByRole('button',{name:'期间复盘',exact:true}).click()
 await page.getByRole('button',{name:/期间复盘.*2026/}).click()
 await page.getByText('虚构期间复盘',{exact:true}).waitFor()
 await page.getByRole('button',{name:'目标',exact:true}).click()
 await page.getByText('合成目标',{exact:true}).waitFor()
 evidence.checks.push('authorized reader: diary, saved answers, citation navigation, period report and goals; no editor/management/generation')
 for(const width of [390,360]){
  await page.setViewportSize({width,height:950})
  await page.getByRole('button',{name:'日记',exact:true}).click()
  await page.locator('.reader-row').first().click()
  await page.getByRole('heading',{name:entry.title,exact:true}).waitFor()
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true)
  await page.screenshot({path:join(output,'shared-mobile-'+width+'.png')})
 }
 await page.evaluate(()=>sessionStorage.setItem('module-anonymous','1'))
 await page.reload()
 await page.getByText('只读浏览',{exact:true}).waitFor()
 await page.locator('.reader-row').first().click()
 await page.getByRole('heading',{name:entry.title,exact:true}).waitFor()
 assert.equal(viewCalls.at(-1).authorization,undefined)
 evidence.checks.push('anonymous public reading and mobile 360/390 without horizontal overflow')
 // An in-flight detail response cannot repopulate a reader after revocation.
 holdDetail=true
 await page.locator('.reader-row').first().click()
 await page.waitForFunction(()=>document.querySelector('.reader-detail')?.getAttribute('aria-busy')==='true')
 sharedAllowed=false
 await page.evaluate(()=>window.dispatchEvent(new Event('focus')))
 await page.waitForURL(/access-denied/)
 releaseDetail?.();await page.waitForTimeout(200)
 assert.equal(await page.locator('.diary-reader').count(),0)
 assert.ok(!(await page.content()).includes(entry.body))
 await page.screenshot({path:join(output,'shared-denied.png')})
 assert.ok(await page.locator('.mobile-diary-link').isVisible())
 evidence.checks.push('revocation clears reader/report contents; late response ignored; diary nav stays visible')
 holdDetail=false;sharedAllowed=true;manages=true
 await page.evaluate(()=>sessionStorage.removeItem('module-anonymous'))
 await page.goto(base+'/diary?entry='+id)
 await page.getByRole('button',{name:'新建日记',exact:true}).waitFor()
 assert.ok(await page.locator('.diary-workspace').count() || await page.locator('.diary-page').count() || await page.getByRole('button',{name:'新建日记',exact:true}).isVisible())
 evidence.checks.push('author still reaches original editing workspace')
 await page.getByRole('navigation',{name:'日记工作区',exact:true}).getByRole('button',{name:/目标行动/}).click()
 await page.getByRole('button',{name:'新目标',exact:true}).click()
 await page.getByLabel('目标标题',{exact:true}).fill('虚构的未保存草稿')
 capabilityFailure=true
 await page.evaluate(()=>window.dispatchEvent(new Event('focus')))
 await page.locator('.diary-access-warning').waitFor()
 assert.equal(await page.getByLabel('目标标题',{exact:true}).inputValue(),'虚构的未保存草稿')
 assert.match(await page.locator('meta[name=robots]').getAttribute('content'),/noindex/)
 evidence.checks.push('transient access-probe failure preserves author in-memory draft; diary shell keeps noindex')
 assert.equal(submissions.length,0);assert.equal(indexStarts,0)
 assert.deepEqual(evidence.pageErrors,[]);assert.deepEqual(evidence.unmatchedApi,[])
 evidence.status='passed'
} catch(error){evidence.status='failed';evidence.error=String(error);await page.screenshot({path:join(output,'failure.png')});throw error}
finally{await writeFile(join(output,'report.json'),JSON.stringify(evidence,null,2));await browser.close()}
