import assert from 'node:assert/strict'
import { once } from 'node:events'
import { readFile, access } from 'node:fs/promises'
import { createServer as createHttpServer } from 'node:http'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import test from 'node:test'
import { createServer, loadConfigFromFile, mergeConfig, preview } from 'vite'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const read = path => readFile(resolve(root, path), 'utf8')

test('direct Vite commands preserve the existing static deployment model', async () => {
  const pkg = JSON.parse(await read('package.json'))
  assert.equal(pkg.scripts.dev, 'vite')
  assert.equal(pkg.scripts.build, 'vue-tsc --noEmit && vite build')
  assert.equal(pkg.scripts.preview, 'vite preview')
  assert.ok(!pkg.devDependencies['@mistjs/cli'])
  assert.ok(!pkg.devDependencies.nitropack)
  const { config } = await loadConfigFromFile({ command: 'build', mode: 'production' }, resolve(root, 'vite.config.ts'))
  assert.equal(config.build.outDir, 'dist')
  assert.deepEqual(config.build.target, ['es2020', 'edge88', 'firefox78', 'chrome87', 'safari14'])
  assert.ok(config.resolve.alias.some(alias => alias.find === '~' && alias.replacement === resolve(root, 'src')))
  assert.ok(config.plugins.flat().some(plugin => plugin.name === 'vite:vue'))
})

test('production preview serves deep links and every HTML asset under its base', async () => {
  const html = await read('dist/index.html')
  const references = [...html.matchAll(/(?:src|href)="([^"#]+)"/g)].map(match => match[1]).filter(path => path.startsWith('/'))
  const entry = references.find(path => /\/assets\/index-[^/]+\.js$/.test(path))
  assert.ok(entry, 'compiled entry script exists')
  const base = entry.slice(0, entry.indexOf('assets/'))
  assert.ok(references.includes(`${base}_app.config.js`), 'runtime config uses the same deployment base')
  for (const path of references) {
    assert.ok(path.startsWith(base), `asset outside base: ${path}`)
    await access(resolve(root, 'dist', decodeURI(path.slice(base.length))))
  }
  // configFile:false prevents the production API proxy from being used by this test.
  const server = await preview({ root, configFile: false, base, logLevel: 'silent',
    preview: { host: '127.0.0.1', port: 0, open: false, proxy: {} } })
  try {
    const origin = `http://127.0.0.1:${server.httpServer.address().port}`
    for (const route of ['', 'login', 'blog/experience', 'blog/website-share']) {
      const response = await fetch(origin + base + route)
      assert.equal(response.status, 200)
      assert.match(response.headers.get('content-type'), /text\/html/)
      assert.match(await response.text(), /id="app"/)
    }
    for (const path of references) {
      const response = await fetch(origin + path)
      assert.equal(response.status, 200, path)
      assert.doesNotMatch(response.headers.get('content-type'), /text\/html/, path)
      await response.arrayBuffer()
    }
  } finally { await new Promise((resolve, reject) => server.httpServer.close(error => error ? reject(error) : resolve())) }
})

test('development proxy and Vue transforms work without Mist or real backend access', async () => {
  const backend = createHttpServer((request, response) => {
    response.setHeader('Content-Type', 'application/json')
    response.end(JSON.stringify({ path: request.url, method: request.method }))
  })
  backend.listen(0, '127.0.0.1')
  await once(backend, 'listening')
  const keys = ['VITE_APP_BASE', 'VITE_APP_BASE_API', 'VITE_APP_BASE_URL']
  const previous = Object.fromEntries(keys.map(key => [key, process.env[key]]))
  process.env.VITE_APP_BASE = '/admin/'
  process.env.VITE_APP_BASE_API = '/api'
  process.env.VITE_APP_BASE_URL = `http://127.0.0.1:${backend.address().port}`
  let server
  try {
    const { config } = await loadConfigFromFile({ command: 'serve', mode: 'development' }, resolve(root, 'vite.config.ts'))
    const proxy = config.server.proxy['/api']
    assert.equal(proxy.target, process.env.VITE_APP_BASE_URL)
    assert.equal(proxy.rewrite('/api/qa?test=1'), '/qa?test=1')
    server = await createServer(mergeConfig(config, { root, configFile: false, logLevel: 'silent',
      server: { host: '127.0.0.1', port: 0, open: false } }))
    await server.listen()
    const origin = `http://127.0.0.1:${server.httpServer.address().port}`
    const response = await fetch(origin + '/api/qa?test=1', { method: 'POST', body: 'synthetic' })
    assert.deepEqual(await response.json(), { path: '/qa?test=1', method: 'POST' })
    const page = await fetch(origin + '/admin/login')
    assert.equal(page.status, 200)
    assert.match(await page.text(), /@vite\/client/)
    const component = await fetch(origin + '/admin/src/pages/common/login.vue')
    assert.equal(component.status, 200)
    assert.match(await component.text(), /defineComponent/)
    const routes = await fetch(origin + '/admin/src/router/router-modules.ts')
    assert.equal(routes.status, 200)
    assert.match(await routes.text(), /\/src\/pages\/blog\/experience\/index\.vue/)
  } finally {
    if (server) await server.close()
    await new Promise(resolve => backend.close(resolve))
    for (const key of keys) previous[key] === undefined ? delete process.env[key] : process.env[key] = previous[key]
  }
})
