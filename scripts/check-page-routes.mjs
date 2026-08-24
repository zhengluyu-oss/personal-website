import { execFileSync } from 'node:child_process'
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'

const root = new URL('..', import.meta.url).pathname.replace(/^\/(.:)/, '$1')
const publicPaths = readFileSync(join(root, 'blog-frontend/kuailemao-blog/src/router/paths.ts'), 'utf8')
const adminPaths = readFileSync(join(root, 'blog-frontend/kuailemao-admin/src/router/paths.ts'), 'utf8')
const values = text => [...text.matchAll(/:\s*'([^']+)'/g)].map(match => match[1])
const depth = path => path.split('/').filter(Boolean).length

const publicValues = values(publicPaths)
const adminValues = values(adminPaths)
const duplicate = list => list.filter((item, index) => list.indexOf(item) !== index)
if (duplicate(publicValues).length || duplicate(adminValues).length) throw new Error('存在重复的规范页面路径')
for (const path of publicValues) if (depth(path) > 3) throw new Error(`前台路由超过三级: ${path}`)
for (const path of adminValues) if (depth(path) + 1 > 3) throw new Error(`后台路由超过三级: /admin${path}`)
if ([...publicValues, ...adminValues].some(path => path.includes('?'))) throw new Error('规范路由不得使用可选身份参数')
if (!publicValues.includes('/blog/:slug')) throw new Error('缺少语义化博客分类路由: /blog/:slug')
if (publicValues.some(path => path === '/blog/categories' || path.startsWith('/blog/categories/'))) throw new Error('仍注册旧分类页面路由')

const walk = dir => readdirSync(dir).flatMap(name => {
  const path = join(dir, name)
  return statSync(path).isDirectory() ? walk(path) : [path]
})
const publicSource = join(root, 'blog-frontend/kuailemao-blog/src')
const forbidden = [/['"`]\/category(?:\/|['"`])/g, /['"`]\/article(?:\/|['"`])/g, /['"`]\/tags(?:\/|['"`])/g, /['"`]\/timeline['"`]/g, /['"`]\/photo['"`]/g, /['"`]\/setting['"`]/g, /['"`]\/welcome['"`]/g, /\/blog\/categories(?:\/|['"`])/g]
for (const file of walk(publicSource).filter(file => /\.(ts|vue)$/.test(file) && !file.includes(`${join('src', 'apis')}`) && !file.endsWith(join('utils', 'http.ts')))) {
  const text = readFileSync(file, 'utf8')
  if (forbidden.some(pattern => (pattern.lastIndex = 0, pattern.test(text)))) throw new Error(`仍引用前台旧页面路径: ${relative(root, file)}`)
}

const changed = execFileSync('git', ['diff', '--name-only', '--', 'blog-backend', 'blog-frontend/kuailemao-blog/src/apis', 'blog-frontend/kuailemao-admin/src/api'], { cwd: root, encoding: 'utf8' }).trim()
if (changed) throw new Error(`检测到 API 范围改动:\n${changed}`)
console.log(`路由审计通过：前台 ${publicValues.length} 条，后台 ${adminValues.length} 条，全部不超过三级；API 基线未变化。`)
