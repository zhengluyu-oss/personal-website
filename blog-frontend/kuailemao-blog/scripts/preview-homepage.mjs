// Local-only production-build preview. API proxy is GET-only and sends no credentials.
// Use an SSH loopback tunnel to port 18001, or --fixtures for deterministic UI checks.
import { createServer } from 'node:http'
import { readFile, stat } from 'node:fs/promises'
import { resolve, extname, sep } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('../dist/', import.meta.url))
const fixtures = process.argv.includes('--fixtures')
const port = fixtures ? 4174 : 4173
const attempts = new Map()
const count = key => { const value = (attempts.get(key) || 0) + 1; attempts.set(key, value); return value }
const mime = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml', '.png': 'image/png', '.jpg': 'image/jpeg', '.woff2': 'font/woff2', '.woff': 'font/woff', '.json': 'application/json' }
const article = id => ({ id, articleTitle: '[测试] 技术实践记录 ' + id, articleContent: '这是仅用于本地验收的文章示例，用来检查内容层级与独立加载，不会上传到线上。', articleCover: '/qa-missing-cover.png', createTime: '2026-09-09', categoryName: '测试分类' })
const publicPaths = ['/websiteInfo/front', '/banners/list', '/article/list', '/article/recommend', '/experience/list', '/category/list', '/article/search/init/title', '/tag/list']

createServer(async (req, res) => {
  res.setHeader('Cache-Control', 'no-store')
  try {
    if (req.method !== 'GET' && req.method !== 'HEAD') { res.writeHead(405).end(); return }
    const url = new URL(req.url, 'http://127.0.0.1')
    if (url.pathname.startsWith('/api/')) {
      const apiPath = url.pathname.slice(4)
      if (fixtures) {
        const scenario = new URL(req.headers.referer || 'http://127.0.0.1').searchParams.get('qa') || 'partial'
        const attempt = count(scenario + apiPath)
        const contentPath = ['/article/list', '/article/recommend', '/experience/list', '/banners/list'].includes(apiPath)
        if (contentPath && (scenario === 'all-error' || (scenario === 'partial' && ['/article/recommend', '/banners/list'].includes(apiPath) && attempt === 1))) {
          res.writeHead(503).end('Fixture failure'); return
        }
        if (scenario === 'slow' && apiPath === '/experience/list') await new Promise(resolve => setTimeout(resolve, 15000))
        let data = []
        if (apiPath === '/websiteInfo/front') data = { websiteName: '首页测试站', heroTitle: '欢迎来到陆屿的小世界', heroSubtitle: '仅用于本地验证的首页内容', heroPrimaryText: '查看工作经历', heroPrimaryUrl: '/experience', heroSecondaryText: '阅读博客', heroSecondaryUrl: '/blog', heroAsideLabel: '当前方向', heroAsideText: '后端开发与技术写作', lastUpdateTime: '2026-09-09', startTime: '2024-01-01' }
        if (apiPath === '/article/list') data = { page: scenario === 'empty' ? [] : [1, 2, 3, 4, 5, 6].map(article), total: scenario === 'empty' ? 0 : 30 }
        if (apiPath === '/article/recommend') data = scenario === 'empty' ? [] : [article(90)]
        if (apiPath === '/banners/list') data = scenario === 'empty' ? [] : ['/blog-icon.svg']
        if (apiPath === '/experience/list') data = scenario === 'empty' ? [] : [{ id: 1, company: '测试公司', roleTitle: '开发实习生', projectSummary: '项目开发与维护', highlights: '项目开发与维护\n接口联调', startDate: '2026-04-01', isCurrent: 1, orderNum: 1, techStack: 'Java,Vue' }]
        res.setHeader('Content-Type', 'application/json'); res.end(JSON.stringify({ code: 200, data })); return
      }
      if (!publicPaths.includes(apiPath)) { res.writeHead(404).end(); return }
      const upstream = await fetch('http://127.0.0.1:18001' + apiPath + url.search, { signal: AbortSignal.timeout(15000) })
      res.writeHead(upstream.status, { 'Content-Type': upstream.headers.get('content-type') || 'application/json' })
      res.end(Buffer.from(await upstream.arrayBuffer())); return
    }
    if (url.pathname === '/qa-missing-cover.png' && fixtures) {
      if (count('cover') === 1) { res.writeHead(404).end(); return }
      res.setHeader('Content-Type', 'image/svg+xml'); res.end(await readFile(resolve(root, 'blog-icon.svg'))); return
    }
    let file = resolve(root, '.' + decodeURIComponent(url.pathname))
    if (file !== resolve(root) && !file.startsWith(resolve(root) + sep)) { res.writeHead(403).end(); return }
    try { if (!(await stat(file)).isFile()) file = resolve(root, 'index.html') }
    catch { if (extname(file)) { res.writeHead(404).end(); return }; file = resolve(root, 'index.html') }
    const compressed = /gzip/.test(req.headers['accept-encoding'] || '') && await stat(file + '.gz').then(() => true, () => false)
    res.setHeader('Content-Type', mime[extname(file)] || 'application/octet-stream')
    if (compressed) { res.setHeader('Content-Encoding', 'gzip'); res.setHeader('Vary', 'Accept-Encoding') }
    res.end(await readFile(file + (compressed ? '.gz' : '')))
  }
  catch { res.writeHead(502).end('Preview upstream unavailable') }
}).listen(port, '127.0.0.1', () => console.log('Homepage preview: http://127.0.0.1:' + port + (fixtures ? '/?qa=partial (LOCAL FIXTURES)' : ' (public GET proxy)')))
