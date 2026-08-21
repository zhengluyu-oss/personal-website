import { existsSync, readFileSync } from 'node:fs'
import { stat } from 'node:fs/promises'
import { resolve } from 'node:path'
import { gzipSync } from 'node:zlib'

const distDir = resolve(process.cwd(), 'dist')
const htmlPath = resolve(distDir, 'index.html')
const limits = { jsTotal: 700 * 1024, cssTotal: 180 * 1024, jsSingle: 450 * 1024 }

if (!existsSync(htmlPath)) throw new Error(`找不到构建入口：${htmlPath}`)

const html = readFileSync(htmlPath, 'utf8')
const initialFiles = [...html.matchAll(/(?:src|href)="(?!https?:|\/\/)(?:\/)?([^"?#]+\.(?:js|css))[^\"]*"/g)].map(match => match[1])
const uniqueFiles = [...new Set(initialFiles)]
const report = []

for (const file of uniqueFiles) {
  const absolutePath = resolve(distDir, file)
  const source = readFileSync(absolutePath)
  const gzipPath = `${absolutePath}.gz`
  const gzipBytes = existsSync(gzipPath) ? (await stat(gzipPath)).size : gzipSync(source).byteLength
  report.push({ file, type: file.endsWith('.css') ? 'css' : 'js', rawBytes: source.byteLength, gzipBytes })
}

const js = report.filter(item => item.type === 'js')
const css = report.filter(item => item.type === 'css')
const jsTotal = js.reduce((total, item) => total + item.gzipBytes, 0)
const cssTotal = css.reduce((total, item) => total + item.gzipBytes, 0)
const violations = [
  ...(jsTotal > limits.jsTotal ? [`初始 JS ${(jsTotal / 1024).toFixed(1)}KB > 700KB`] : []),
  ...(cssTotal > limits.cssTotal ? [`初始 CSS ${(cssTotal / 1024).toFixed(1)}KB > 180KB`] : []),
  ...js.filter(item => item.gzipBytes > limits.jsSingle).map(item => `${item.file} ${(item.gzipBytes / 1024).toFixed(1)}KB > 450KB`),
]

console.table(report.map(item => ({ resource: item.file, rawKB: (item.rawBytes / 1024).toFixed(1), gzipKB: (item.gzipBytes / 1024).toFixed(1) })))
console.log(`初始资源合计：JS ${(jsTotal / 1024).toFixed(1)}KB，CSS ${(cssTotal / 1024).toFixed(1)}KB`)
if (violations.length) throw new Error(`首屏资源预算超限：\n- ${violations.join('\n- ')}`)
