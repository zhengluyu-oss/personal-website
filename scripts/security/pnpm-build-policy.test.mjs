import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync, existsSync } from 'node:fs'

const read = path => readFileSync(new URL(path, import.meta.url), 'utf8')

test('both frontend packages have pinned pnpm and one lockfile', () => {
  for (const project of ['kuailemao-blog', 'kuailemao-admin']) {
    const root = `../../blog-frontend/${project}/`
    const manifest = JSON.parse(read(`${root}package.json`))
    assert.match(manifest.packageManager, /^pnpm@\d+\.\d+\.\d+$/)
    assert.ok(existsSync(new URL(`${root}pnpm-lock.yaml`, import.meta.url)))
    assert.ok(!existsSync(new URL(`${root}package-lock.json`, import.meta.url)))
  }
})

test('deployment uses pinned pnpm with frozen installs, including existing node_modules', () => {
  const script = read('../deploy-to-server.ps1')
  for (const project of ['BlogDir', 'AdminDir']) {
    assert.ok(script.includes(`Invoke-Native "corepack" @("pnpm", "install", "--frozen-lockfile") $${project}`))
    assert.ok(script.includes(`Invoke-Native "corepack" @("pnpm", "build") $${project}`))
  }
  assert.ok(!/Test-Path[^\n]+node_modules/.test(script))
  assert.match(script, /finally\s*\{\s*\$env:HUSKY = \$previousHusky\s*\}/)
})
