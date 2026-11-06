import { spawnSync } from 'node:child_process'
import { existsSync } from 'node:fs'
import { resolve } from 'node:path'

// The admin app is a subdirectory of the main repository. Husky only installs
// hooks when this directory itself is checked out as a standalone Git repository.
if (process.env.HUSKY === '0' || !existsSync(resolve('.git'))) {
  console.log('Skipping Husky install for nested checkout')
} else {
  const husky = resolve('node_modules/husky/lib/bin.js')
  const result = spawnSync(process.execPath, [husky, 'install'], { stdio: 'inherit' })
  if (result.error) throw result.error
  if (result.status !== 0) process.exit(result.status || 1)
}
