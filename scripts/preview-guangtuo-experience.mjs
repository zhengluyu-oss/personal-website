/** Local, read-only content preview. No credentials, database connection or write forwarding. */
import { createRequire } from 'node:module'
import { fileURLToPath, pathToFileURL } from 'node:url'
import path from 'node:path'
import { loadContent, mergeExperience, mergeProject } from './guangtuo-experience-content.mjs'

const root = fileURLToPath(new URL('../blog-frontend/kuailemao-blog/', import.meta.url))
const require = createRequire(path.join(root, 'package.json'))
const { createServer } = await import(pathToFileURL(path.join(path.dirname(require.resolve('vite/package.json')), 'dist/node/index.js')).href)
const manifest = await loadContent()
const origin = 'https://www.zhengluyu.com'
const cache = new Map()
const publicPath = /^\/api\/(?:experience\/(?:list|\d+(?:\/projects(?:\/\d+)?)?)|websiteInfo\/front|banners\/list|category\/list)$/

process.chdir(root)
const server = await createServer({
  root,
  mode: 'experience-preview',
  define: { 'import.meta.env.VITE_APP_BASE_API': JSON.stringify('/api'), 'import.meta.env.VITE_ENABLE_DEV_TOOLSBLOCKER': JSON.stringify('false'), 'import.meta.env.VITE_MUSIC_SERVE': JSON.stringify('') },
  plugins: [{
    name: 'read-only-experience-preview',
    enforce: 'pre',
    configResolved(config) { config.server.proxy = {} },
    configureServer(vite) {
      vite.middlewares.use(async (req, res, next) => {
        const url = new URL(req.url || '/', 'http://127.0.0.1')
        if (!url.pathname.startsWith('/api/') && !url.pathname.startsWith('/wapi/')) return next()
        res.setHeader('Content-Type', 'application/json; charset=utf-8')
        res.setHeader('Cache-Control', 'no-store')
        if (req.method === 'GET' && url.pathname === '/api/user/auth/info') {
          // The shared header asks for a profile even for guests. Never forward this request.
          return res.end(JSON.stringify({ code: 204, msg: '本地匿名预览', data: null }))
        }
        if (req.method !== 'GET' || !publicPath.test(url.pathname)) {
          res.statusCode = 403
          return res.end(JSON.stringify({ code: 403, msg: '本地内容预览仅开放指定的公开只读接口', data: null }))
        }
        try {
          if (!cache.has(url.pathname)) {
            const upstream = await fetch(origin + url.pathname, { signal: AbortSignal.timeout(15000), redirect: 'error' })
            if (!upstream.ok) throw new Error('Public content unavailable')
            const body = await upstream.json()
            if (body.code !== 200) throw new Error('Invalid public content response')
            cache.set(url.pathname, body)
          }
          const body = structuredClone(cache.get(url.pathname))
          if (url.pathname === '/api/experience/list') body.data = body.data.map(x => mergeExperience(x, manifest))
          else if (url.pathname === '/api/experience/2') body.data = mergeExperience(body.data, manifest)
          else if (url.pathname === '/api/experience/2/projects') body.data = body.data.map(x => mergeProject(x, manifest))
          else if (/^\/api\/experience\/2\/projects\/\d+$/.test(url.pathname)) body.data = mergeProject(body.data, manifest)
          res.end(JSON.stringify(body))
        } catch {
          res.statusCode = 502
          res.end(JSON.stringify({ code: 502, msg: '公开内容暂时无法读取，请稍后刷新', data: null }))
        }
      })
    },
  }],
  server: { host: '127.0.0.1', port: 4178, strictPort: true, open: false },
})
await server.listen()
console.log('Read-only preview: http://127.0.0.1:4178/experience/2')
for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, async () => { await server.close(); process.exit(0) })
