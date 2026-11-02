import test from 'node:test'
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
const req=createRequire(new URL('../../blog-frontend/luyu-blog/package.json',import.meta.url))
const {build}=createRequire(req.resolve('vite/package.json'))('esbuild')
const calls=[]
globalThis.location={href:'https://fixture.invalid/knowledge',origin:'https://fixture.invalid'}
globalThis.__knowledgeTransport={defaults:{baseURL:'/api'},request:async value=>{calls.push(value);return {data:{code:200,data:{fixture:true}}}}}
const built=await build({entryPoints:[fileURLToPath(new URL('../../blog-frontend/luyu-blog/src/utils/knowledge-api.ts',import.meta.url))],bundle:true,write:false,platform:'node',format:'cjs',define:{'import.meta.env.VITE_APP_BASE_API':'"/api"'},plugins:[{name:'transport',setup(b){
 b.onResolve({filter:/^(axios|@\/)/},a=>({path:a.path,namespace:'fixture'}))
 b.onLoad({filter:/.*/,namespace:'fixture'},a=>({contents:a.path==='axios'?'export default {create:()=>globalThis.__knowledgeTransport}':a.path.includes('/auth')?'export const GET_TOKEN=()=>"fixture-token"':'export const Jwt_Prefix="Bearer ";',loader:'js'}))
}}]})
const module={exports:{}};new Function('module','exports',built.outputFiles[0].text)(module,module.exports)
const {knowledgeRequest}=module.exports
test('real reader request policy permits tag numbers without granting writes',async()=>{
 calls.length=0
 assert.deepEqual(await knowledgeRequest('POST','/view/tag-numbers',['fixture-id']),{fixture:true})
 assert.equal(calls[0].url,'/knowledge/view/tag-numbers');assert.deepEqual(calls[0].data,['fixture-id'])
 for(const [method,path] of [['POST','/manage/tags/tag/order'],['POST','/view/unknown'],['DELETE','/view/tag-numbers'],['POST','/view/tag-numbers/extra']])await assert.rejects(knowledgeRequest(method,path),/仅支持阅读/)
 assert.equal(calls.length,1)
})
