import { existsSync, readFileSync } from 'node:fs'
import { resolve, sep } from 'node:path'
import { gzipSync } from 'node:zlib'

const distDir = resolve(process.cwd(), 'dist')
const htmlPath = resolve(distDir, 'index.html')
if (!existsSync(htmlPath)) throw new Error(`找不到管理后台构建入口：${htmlPath}`)

const html = readFileSync(htmlPath, 'utf8')
const tags = [...html.matchAll(/<(?:script|link)\b[^>]*>/g)].map(match => match[0])
const resource = tag => tag.match(/\b(?:src|href)="([^"]+)"/)?.[1]
const entry = tags.find(tag => tag.includes('type="module"') && resource(tag)?.includes('/assets/'))
if (!entry) throw new Error('管理后台入口缺少模块脚本')
const base = new URL(resource(entry), 'https://local.invalid').pathname.split('/assets/')[0] + '/'
const preloads = tags.filter(tag => /\brel="modulepreload"/.test(tag) && resource(tag)?.endsWith('.js'))
const paths = [...new Set(tags.map(resource).filter(path => path && /\.(?:js|css)(?:[?#]|$)/.test(path)))]
const report = paths.map(path => {
  const pathname = new URL(path, 'https://local.invalid').pathname
  if (!pathname.startsWith(base)) throw new Error(`入口资源不在部署路径下：${path}`)
  const absolute = resolve(distDir, pathname.slice(base.length))
  if (!absolute.startsWith(`${distDir}${sep}`) || !existsSync(absolute)) throw new Error(`找不到入口资源：${path}`)
  const bytes = readFileSync(absolute)
  return { path: pathname, type: pathname.endsWith('.js') ? 'js' : 'css', gzipBytes: gzipSync(bytes).byteLength }
})
const jsGzip = report.filter(item => item.type === 'js').reduce((total, item) => total + item.gzipBytes, 0)
const editorPreloads = preloads.map(resource).filter(path => /(?:^|\/)(?:MdEditor|apl|brainfuck|javascript|typescript|python|sql)-[^/]*\.js$/i.test(path))

console.table(report.map(item => ({ resource: item.path, gzipKB: (item.gzipBytes / 1024).toFixed(1) })))
console.log(`入口预加载脚本 ${preloads.length} 个；初始 JS ${(jsGzip / 1024).toFixed(1)} KB gzip`)
const failures = [
  ...(preloads.length > 10 ? [`预加载脚本 ${preloads.length} 个 > 10 个`] : []),
  ...(jsGzip > 850 * 1024 ? [`初始 JS ${(jsGzip / 1024).toFixed(1)} KB > 850 KB`] : []),
  ...(editorPreloads.length ? [`编辑器或语言包被提前加载：${editorPreloads.join(', ')}`] : []),
]
if (failures.length) throw new Error(`管理后台入口资源超限：\n- ${failures.join('\n- ')}`)
