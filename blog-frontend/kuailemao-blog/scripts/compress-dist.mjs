import { existsSync, readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs'
import { extname, join, resolve } from 'node:path'
import { gunzipSync, gzipSync } from 'node:zlib'

const distDir = resolve(process.cwd(), 'dist')
const compressible = new Set(['.js', '.css', '.svg', '.json'])
const threshold = 1024
const outputs = []

function walk(directory) {
  for (const name of readdirSync(directory)) {
    const absolutePath = join(directory, name)
    const info = statSync(absolutePath)
    if (info.isDirectory()) walk(absolutePath)
    else if (compressible.has(extname(name)) && info.size >= threshold) {
      const source = readFileSync(absolutePath)
      const gzipPath = `${absolutePath}.gz`
      const compressed = gzipSync(source, { level: 9 })
      writeFileSync(gzipPath, compressed)
      if (!gunzipSync(readFileSync(gzipPath)).equals(source)) throw new Error(`gzip 校验失败：${absolutePath}`)
      outputs.push({ file: absolutePath.slice(distDir.length + 1), raw: source.byteLength, gzip: compressed.byteLength })
    }
  }
}

if (!existsSync(distDir)) throw new Error(`找不到构建目录：${distDir}`)
walk(distDir)
console.log(`已生成并校验 ${outputs.length} 个相邻 gzip 文件。`)
