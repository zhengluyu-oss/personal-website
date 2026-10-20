import test from 'node:test'
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
const req=createRequire(new URL('../../blog-frontend/kuailemao-blog/package.json',import.meta.url))
const admin=createRequire(new URL('../../blog-frontend/kuailemao-admin/package.json',import.meta.url))
const {JSDOM}=admin('jsdom'),dom=new JSDOM('<html><body></body></html>',{url:'https://fixture.invalid/knowledge'})
for(const key of ['window','document','Element','HTMLElement','SVGElement','Node','navigator'])Object.defineProperty(globalThis,key,{configurable:true,value:dom.window[key]})
window.confirm=()=>true
const Vue=req('vue'),{parse,compileScript}=req('vue/compiler-sfc'),{build}=createRequire(req.resolve('vite/package.json'))('esbuild')
const user=Vue.reactive({token:'fixture'}),calls=[]
let canManage=true,allowed=true,saveError,readReply
const id='11111111-1111-4111-8111-111111111111'
const content={kind:'NOTE',title:'合成笔记',body:'合成正文',sourceUrl:'',categoryId:null,tagIds:[]}
const entry={id,revision:1,content,fileVersion:null,updatedAt:'2026-10-20T00:00:00Z',deletedAt:null}
globalThis.__knowledgeFixture={user,request:async(method,path,data)=>{
  calls.push({method,path,data})
  if(path==='/view/capabilities')return{allowed,available:true,canManage,filesAvailable:false,message:'fixture'}
  if(path==='/view/search')return{records:[{entry,excerpt:'合成'}],total:1,page:1,pageSize:20}
  if(path==='/view/labels')return[]
  if(path==='/view/entries/'+id)return readReply||structuredClone(entry)
  if(path==='/manage/entries'){if(saveError)throw saveError;return{id:data.entryId,revision:1,savedAt:'2026-10-20T00:00:00Z',deleted:false}}
  throw new Error('Unexpected fixture request '+path)
}}
const root=fileURLToPath(new URL('../../blog-frontend/kuailemao-blog/',import.meta.url)),filename=root+'/src/views/Knowledge/Workspace.vue'
const {descriptor}=parse(readFileSync(filename,'utf8'),{filename})
const source=compileScript(descriptor,{id:'knowledge-fixture',inlineTemplate:true}).content
const built=await build({stdin:{contents:source,loader:'ts',resolveDir:root},bundle:true,write:false,platform:'node',format:'cjs',external:['vue'],plugins:[{name:'fixture',setup(b){
  b.onResolve({filter:/^(vue-router|md-editor-v3|@\/|\.\/(Connections|Draft)\.vue)/},a=>({path:a.path,namespace:'fixture'}))
  b.onLoad({filter:/.*/,namespace:'fixture'},a=>({loader:'js',contents:a.path.includes('knowledge-api')?'export const knowledgeRequest=globalThis.__knowledgeFixture.request;':a.path.includes('store/modules/user')?'export default ()=>globalThis.__knowledgeFixture.user;':a.path==='vue-router'?'export const onBeforeRouteLeave=()=>{};':a.path.includes('private-workspace')?'export const registerPrivateWorkspaceGuard=()=>()=>{};':a.path.includes('diary-preview')?'export const sanitizePrivateDiary=x=>x;':a.path==='md-editor-v3'?'export const MdPreview={props:["modelValue"],template:"<article>{{modelValue}}</article>"};':a.path.includes('Connections.vue')?'export default {props:["entry","candidates","canManage"],template:"<section>知识关联</section>"};':a.path.includes('Draft.vue')?'export default {props:["selected","canManage"],template:"<section>整理为博客草稿</section>"};':''}))
}}]})
const module={exports:{}};new Function('require','module','exports',built.outputFiles[0].text)(req,module,module.exports)
async function flush(){await new Promise(r=>setImmediate(r));await Vue.nextTick()}
async function mount(manage=true){canManage=manage;allowed=true;saveError=undefined;readReply=undefined;calls.length=0;user.token='fixture';const host=document.createElement('div');document.body.append(host);const app=Vue.createApp(module.exports.default);app.config.warnHandler=()=>{};app.mount(host);await flush();return{host,close(){app.unmount();host.remove()}}}
function button(host,label){const b=[...host.querySelectorAll('button')].find(b=>b.textContent.includes(label));assert.ok(b,label);return b}
function fill(host,label,value){const el=[...host.querySelectorAll('label')].find(l=>l.textContent.startsWith(label))?.querySelector('input,textarea');assert.ok(el,label);el.value=value;el.dispatchEvent(new window.Event('input',{bubbles:true}))}
test('new note saves with a stable mutation and entry id after an uncertain response',async()=>{const f=await mount();try{
  button(f.host,'新建条目').click();await flush();fill(f.host,'标题','新的笔记');fill(f.host,'Markdown 正文','需要保留的正文');await flush()
  saveError=new Error('请求未获确认');button(f.host,'保存条目').click();await flush();assert.match(f.host.textContent,/尚未确认保存/)
  saveError=undefined;button(f.host,'保存条目').click();await flush();const writes=calls.filter(c=>c.path==='/manage/entries');assert.equal(writes.length,2);assert.deepEqual(writes[0].data,writes[1].data);assert.match(f.host.textContent,/已可靠保存/)
}finally{f.close()}})
test('readers can view but cannot create or edit',async()=>{const f=await mount(false);try{assert.equal([...f.host.querySelectorAll('button')].some(b=>b.textContent.includes('新建条目')),false);button(f.host,'合成笔记').click();await flush();assert.match(f.host.textContent,/合成正文/);assert.equal(f.host.querySelector('textarea'),null)}finally{f.close()}})
test('revocation clears content and ignores a late detail response',async()=>{const f=await mount();let finish;try{readReply=new Promise(r=>{finish=r});button(f.host,'合成笔记').click();await flush();window.dispatchEvent(new window.CustomEvent('knowledge-access-invalid'));await flush();finish(entry);await flush();assert.equal(f.host.querySelector('textarea'),null);assert.doesNotMatch(f.host.textContent,/合成正文/)}finally{finish?.(entry);f.close()}})
