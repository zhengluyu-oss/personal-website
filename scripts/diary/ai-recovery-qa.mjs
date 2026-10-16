import { execFileSync } from 'node:child_process'
import { readFile, writeFile, mkdir } from 'node:fs/promises'
import { randomBytes, createCipheriv, createDecipheriv } from 'node:crypto'
import { join } from 'node:path'
import assert from 'node:assert/strict'
const output=process.env.DIARY_QA_OUTPUT
if(!output)throw new Error('DIARY_QA_OUTPUT required')
await mkdir(output,{recursive:true})
const container='luyu-diary-ai-qa-20261015'
const docker=(...args)=>execFileSync('docker',args,{maxBuffer:128*1024*1024,windowsHide:true})
const meta=JSON.parse(docker('inspect',container))[0]
assert.equal(meta.Config.Labels['codex.change'],'add-diary-rag-and-ai-review')
assert.equal(meta.HostConfig.PortBindings['5432/tcp'][0].HostIp,'127.0.0.1')
const id=meta.Id, db='diary_ai_restore_'+Date.now(), sql=(database,q)=>docker('exec',id,'psql','-X','-U','postgres','-d',database,'-v','ON_ERROR_STOP=1','-Atc',q).toString().trim()
const tables=['diary.entry','diary.entry_revision','diary.mutation','diary.schema_version',...['embedding_profile','owner_settings','entry_index','chunk','job','worker_slot','report','report_source','usage_budget','schema_version'].map(t=>'diary_ai.'+t)]
const snapshot=database=>Object.fromEntries(tables.map(t=>[t,sql(database,`SELECT count(*)||':'||md5(coalesce(string_agg(md5(row_to_json(t)::text),'' ORDER BY row_to_json(t)::text),'')) FROM ${t} t`)]))
// Pause local worker before running this verifier; no changes are made to source database.
const before=snapshot('diary_qa'), started=performance.now()
const dump=docker('exec',id,'pg_dump','-U','postgres','-Fc','diary_qa')
const key=randomBytes(32),iv=randomBytes(12),cipher=createCipheriv('aes-256-gcm',key,iv)
const encrypted=Buffer.concat([cipher.update(dump),cipher.final()]),tag=cipher.getAuthTag()
await writeFile(join(output,'fictional-diary-ai.dump.aesgcm'),Buffer.concat([iv,tag,encrypted]))
const sealed=await readFile(join(output,'fictional-diary-ai.dump.aesgcm'))
const open=payload=>{const dec=createDecipheriv('aes-256-gcm',key,payload.subarray(0,12));dec.setAuthTag(payload.subarray(12,28));return Buffer.concat([dec.update(payload.subarray(28)),dec.final()])}
const tampered=Buffer.from(sealed);tampered[tampered.length-1]^=1;assert.throws(()=>open(tampered))
const plaintext=open(sealed);assert.deepEqual(plaintext,dump)
sql('postgres',`CREATE DATABASE ${db} OWNER diary_migrator`)
execFileSync('docker',['exec','-i',id,'pg_restore','-U','postgres','--exit-on-error','--single-transaction','-d',db],{input:plaintext,maxBuffer:128*1024*1024,windowsHide:true})
assert.deepEqual(snapshot(db),before)
for(const filename of ['001-private-diary.sql','002-private-diary-ai.sql'])execFileSync('docker',['exec','-i',id,'psql','-X','-U','postgres','-d',db,'-v','ON_ERROR_STOP=1'],{input:await readFile(new URL(filename,import.meta.url)),maxBuffer:8*1024*1024,windowsHide:true})
assert.equal(sql(db,"SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='diary_ai' AND c.relkind='r' AND c.relrowsecurity AND c.relforcerowsecurity"),'9')
assert.equal(sql(db,"BEGIN;SET LOCAL ROLE diary_runtime;SELECT count(*) FROM diary_ai.report;ROLLBACK").split('\n').filter(l=>/^\d+$/.test(l)).at(-1),'0')
assert.equal(sql(db,"SELECT count(*) FROM diary_ai.chunk c JOIN diary_ai.embedding_profile p ON p.owner_id=c.owner_id AND p.id=c.profile_id WHERE diary_vector.vector_dims(c.embedding)<>p.dimensions"),'0')
assert.equal(sql(db,"SELECT count(*) FROM diary_ai.report_source s JOIN diary_ai.report r ON r.owner_id=s.owner_id AND r.id=s.report_id LEFT JOIN diary.entry e ON e.id=s.entry_id AND e.owner_id=s.owner_id WHERE r.state='SUCCEEDED' AND (e.id IS NULL OR e.deleted_at IS NOT NULL OR e.revision<>s.source_revision)"),'0')
key.fill(0);plaintext.fill(0)
const result={status:'passed',database:db,seconds:(performance.now()-started)/1000,tableDigests:before,checks:['encrypted dump decrypted from disk','tamper rejected','all 14 tables equal','old/new migration fingerprints valid','9 FORCE RLS tables','no-owner read denied','profile dimensions and successful citations consistent'],note:'Fictional local AES-GCM QA only; not a production age restore or automatic offsite backup.'}
await writeFile(join(output,'recovery.json'),JSON.stringify(result,null,2));console.log(JSON.stringify({status:result.status,seconds:result.seconds,tables:tables.length}))
