// 仅用于当前任务拥有的固定本机fixture，绝不读取应用/生产连接配置。
import { execFileSync } from 'node:child_process'
import { readFile, writeFile } from 'node:fs/promises'
import { join, resolve } from 'node:path'
import { randomBytes, createCipheriv, createDecipheriv } from 'node:crypto'
import assert from 'node:assert/strict'
const output = process.env.DIARY_QA_OUTPUT
if (!output) throw new Error('DIARY_QA_OUTPUT required')
const report = JSON.parse(await readFile(join(output, 'report.json'), 'utf8'))
assert.equal(report.status, 'passed')
const container = 'diary-pg-java-qa', restore = 'diary_restore_java_qa', base = 'http://127.0.0.1:18002'
const docker = (...args) => execFileSync('docker', args, { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024, windowsHide: true }).trim()
const identity = JSON.parse(docker('inspect', container))[0]
assert.equal(identity.Config.Labels['codex.change'], 'add-private-diary-postgres')
assert.equal(identity.HostConfig.PortBindings['5432/tcp'][0].HostIp, '127.0.0.1')
assert.equal(identity.HostConfig.PortBindings['5432/tcp'][0].HostPort, '15432')
const id = identity.Id
const sql = (db, query) => docker('exec', id, 'psql', '-X', '-U', 'postgres', '-d', db, '-v', 'ON_ERROR_STOP=1', '-Atc', query)
async function api(path, method = 'GET', body, token = 'owner-fixture') {
  const response = await fetch(base + '/diary/auth' + path, { method, signal: AbortSignal.timeout(15000), headers: { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' }, ...(body ? { body: JSON.stringify(body) } : {}) })
  return { status: response.status, body: await response.json() }
}
const snapshot = db => Object.fromEntries(['entry', 'entry_revision', 'mutation', 'schema_version'].map(table => [table, sql(db, `SELECT count(*)||':'||md5(coalesce(string_agg(md5(row_to_json(t)::text),'' ORDER BY row_to_json(t)::text),'')) FROM diary.${table} t`)]))
if (process.argv[2] === 'prepare') {
  assert.deepEqual((await api('/entries/' + report.entryId)).body.data, report.current)
  docker('stop', id)
  try {
    assert.equal((await api('/entries/' + report.entryId)).status, 503)
    assert.equal((await fetch(base + '/websiteInfo/front')).status, 200)
    const mysql = JSON.parse(docker('inspect', 'diary-mysql-java-qa'))[0]
    assert.equal(mysql.Config.Labels['codex.change'], 'add-private-diary-postgres')
    assert.equal(docker('exec', '-e', 'MYSQL_PWD=isolated-mysql-admin', mysql.Id, 'mysql', '-uroot', '-Nse', 'SELECT 1'), '1')
  } finally {docker('start', id)}
  let ready = false
  for (let attempt=0;attempt<30;attempt++) {
    try {ready = (await api('/capabilities')).body.data.available; if (ready) break} catch {}
    await new Promise(resolve => setTimeout(resolve, 500))
  }
  assert.ok(ready); assert.deepEqual((await api('/entries/' + report.entryId)).body.data, report.current)
  assert.equal(sql('postgres', `SELECT count(*) FROM pg_database WHERE datname='${restore}'`), '0', '恢复库已存在，拒绝覆盖')
  const before = snapshot('diary_qa')
  docker('exec', id, 'pg_dump', '-U', 'postgres', '-Fc', '-f', '/tmp/diary-recovery.dump', 'diary_qa')
  const plainPath = resolve(output, 'synthetic-diary.dump')
  docker('cp', id + ':/tmp/diary-recovery.dump', plainPath)
  const plain = await readFile(plainPath), key = randomBytes(32), iv = randomBytes(12)
  const cipher = createCipheriv('aes-256-gcm', key, iv)
  const encrypted = Buffer.concat([cipher.update(plain), cipher.final()]), tag = cipher.getAuthTag()
  await writeFile(join(output, 'synthetic-diary.dump.aesgcm'), Buffer.concat([iv, tag, encrypted]))
  const decrypt = value => {const instance = createDecipheriv('aes-256-gcm', key, iv);instance.setAuthTag(tag);return Buffer.concat([instance.update(value), instance.final()])}
  assert.deepEqual(decrypt(encrypted), plain)
  const damaged = Buffer.from(encrypted); damaged[0] ^= 1; assert.throws(() => decrypt(damaged)); key.fill(0)
  sql('postgres', `CREATE DATABASE ${restore} OWNER diary_migrator`)
  const started = performance.now()
  docker('exec', id, 'pg_restore', '-U', 'postgres', '--exit-on-error', '--single-transaction', '-d', restore, '/tmp/diary-recovery.dump')
  const seconds = (performance.now()-started)/1000
  assert.deepEqual(snapshot(restore), before)
  docker('cp', resolve('scripts/diary/001-private-diary.sql'), id + ':/tmp/diary-recheck.sql')
  docker('exec', id, 'psql', '-X', '-U', 'postgres', '-d', restore, '-v', 'ON_ERROR_STOP=1', '-f', '/tmp/diary-recheck.sql')
  assert.equal(sql(restore, "SET ROLE diary_runtime; SELECT count(*) FROM diary.entry"), 'SET\n0')
  await writeFile(join(output, 'recovery.json'), JSON.stringify({status:'restored-awaiting-app-restart',seconds,snapshot:before,checks:['PG停止HTTP503且公开探针和MySQL可用','PG重启原文/版本完整','AES256GCM往返与篡改拒绝','新库pg_restore全表摘要一致','版本迁移复核通过','恢复后运行角色缺owner不可读']},null,2))
  console.log(JSON.stringify({status:'prepared',seconds}))
} else if (process.argv[2] === 'verify') {
  const recovery = JSON.parse(await readFile(join(output,'recovery.json'),'utf8'))
  assert.deepEqual((await api('/entries/' + report.entryId)).body.data,report.current)
  const ack=await api('/entries','POST',report.originalWrite)
  assert.equal(ack.status,200);assert.equal(ack.body.data.revision,1);assert.equal(ack.body.data.mutationId,report.originalWrite.mutationId)
  assert.deepEqual((await api('/entries/' + report.entryId)).body.data,report.current)
  assert.equal((await api('/entries/' + report.entryId,'GET',undefined,'other-admin-fixture')).status,403)
  assert.deepEqual(snapshot(restore),recovery.snapshot)
  recovery.status='passed';recovery.checks.push('应用重启连接恢复库读取完整','原创建请求返回旧ACK且未覆盖新版本','恢复库其他管理员403');await writeFile(join(output,'recovery.json'),JSON.stringify(recovery,null,2));console.log(JSON.stringify(recovery))
} else throw new Error('Use prepare or verify')
