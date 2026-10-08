import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'

test('inventory performs only a read-only aggregate transaction without returning sensitive fields', () => {
  const sql = readFileSync(new URL('./audit-log-inventory.sql', import.meta.url), 'utf8').replace(/--[^\n]*/g, '').trim()
  const statements = sql.split(';').map(value => value.trim()).filter(Boolean)
  assert.equal(statements.length, 3)
  assert.equal(statements[0], 'START TRANSACTION READ ONLY')
  assert.equal(statements[2], 'ROLLBACK')
  assert.match(statements[1], /^SELECT DATE\(create_time\) AS audit_day,/)
  assert.equal(/\b(UPDATE|DELETE|INSERT|REPLACE|DROP|ALTER|INTO)\b/i.test(sql), false)
  assert.equal(/SELECT\s+\*/i.test(sql), false)
  assert.match(statements[1], /GROUP BY DATE\(create_time\)/)
})
