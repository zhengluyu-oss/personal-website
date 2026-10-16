import { test } from 'node:test'
import assert from 'node:assert/strict'
import { registerPrivateWorkspaceGuard, confirmPrivateEditsBeforeLogout } from '../../blog-frontend/kuailemao-blog/src/utils/private-workspace.ts'

test('diary and coaching guards coexist; unmounting one does not remove the other', async () => {
  const calls: string[] = []
  const diary = registerPrivateWorkspaceGuard(async () => { calls.push('diary'); return true })
  let allowed = false
  const coaching = registerPrivateWorkspaceGuard(async () => { calls.push('coaching'); return allowed })
  try {
    assert.equal(await confirmPrivateEditsBeforeLogout(), false)
    assert.deepEqual(calls, ['diary', 'coaching'])
    allowed = true; calls.length = 0
    assert.equal(await confirmPrivateEditsBeforeLogout(), true)
    assert.deepEqual(calls, ['diary', 'coaching'])
    diary(); calls.length = 0
    assert.equal(await confirmPrivateEditsBeforeLogout(), true)
    assert.deepEqual(calls, ['coaching'])
  } finally { diary(); coaching() }
  assert.equal(await confirmPrivateEditsBeforeLogout(), true)
})
