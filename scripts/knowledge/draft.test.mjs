import test from 'node:test'
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const admin=createRequire(new URL('../../blog-frontend/kuailemao-admin/package.json',import.meta.url))
const req=admin
const {JSDOM}=admin('jsdom'),dom=new JSDOM('<html><body></body></html>',{url:'https://fixture.invalid/knowledge'})
for(const key of ['window','document','Document','Element','HTMLElement','SVGElement','Node','navigator','File'])
  Object.defineProperty(globalThis,key,{configurable:true,value:dom.window[key]})
window.confirm=()=>true
const Vue=req('vue'),{parse,compileScript}=req('vue/compiler-sfc'),{build}=createRequire(req.resolve('vite/package.json'))('esbuild')
const calls=[]
let createFails=false
const entryId='11111111-1111-4111-8111-111111111111'
const selected=Vue.reactive({id:entryId,revision:1,content:{kind:'NOTE',title:'知识笔记',body:'私密资料',sourceUrl:'',categoryId:null,tagIds:[]},fileVersion:null,deletedAt:null})
globalThis.__knowledgeDraftFixture={
  selected,
  request:async(method,path,data)=>{
    calls.push({method,path,data:data===undefined?undefined:JSON.parse(JSON.stringify(data))})
    if(path.endsWith('/history'))return{records:[{revision:1,content:{title:'知识笔记'}}],total:1}
    if(path==='/manage/exports/prepare')return{exportId:data.exportId,state:'PREPARED',articleId:null,snapshot:{sources:[{source:data.sources[0],title:'知识笔记',kind:'NOTE',text:'私密资料'}]}}
    if(path==='/manage/exports/create'){
      if(createFails)throw new Error('响应未确认')
      return{exportId:data.exportId,state:'CREATED',articleId:42,snapshot:{sources:[{source:data.sources[0],title:'知识笔记',kind:'NOTE',text:'私密资料'}]}}
    }
    throw Error('Unexpected request '+path)
  },
  options:async path=>path==='/category/list'?[{id:2,categoryName:'技术'}]:[{id:3,tagName:'知识'}],
  cover:async()=> 'https://example.com/cover.png',
}
const root=fileURLToPath(new URL('../../blog-frontend/kuailemao-admin/',import.meta.url)),filename=root+'/src/pages/blog/knowledge/Draft.vue'
const {descriptor}=parse(readFileSync(filename,'utf8'),{filename})
const source=compileScript(descriptor,{id:'knowledge-draft-fixture',inlineTemplate:true}).content
const built=await build({stdin:{contents:source,loader:'ts',resolveDir:root},bundle:true,write:false,platform:'node',format:'cjs',external:['vue'],plugins:[{name:'fixture',setup(b){
  b.onResolve({filter:/^(md-editor-v3|~\/)/},a=>({path:a.path,namespace:'fixture'}))
  b.onLoad({filter:/.*/,namespace:'fixture'},a=>({loader:'js',contents:a.path.includes('knowledge-api')?'export const knowledgeRequest=globalThis.__knowledgeDraftFixture.request; export const knowledgeArticleOptions=globalThis.__knowledgeDraftFixture.options; export const knowledgeUploadArticleCover=globalThis.__knowledgeDraftFixture.cover;':a.path.includes('knowledge-preview')?'export const sanitizePrivateDiary=x=>x;':'export const MdPreview={props:["modelValue"],template:"<article>{{modelValue}}</article>"};'}))
}}]})
const module={exports:{}};new Function('require','module','exports',built.outputFiles[0].text)(req,module,module.exports)
async function flush(){await new Promise(r=>setImmediate(r));await Vue.nextTick()}
function click(host,label){const button=[...host.querySelectorAll('button')].find(b=>b.textContent.includes(label));assert.ok(button,label);button.click()}
function fill(host,label,value){const input=[...host.querySelectorAll('label')].find(l=>l.textContent.startsWith(label))?.querySelector('input,textarea');assert.ok(input,label);input.value=value;input.dispatchEvent(new window.Event('input',{bubbles:true}))}
function select(host,label,value){const input=[...host.querySelectorAll('label')].find(l=>l.textContent.startsWith(label))?.querySelector('select');assert.ok(input,label);input.value=value;input.dispatchEvent(new window.Event('change',{bubbles:true}))}
async function mount(){calls.length=0;createFails=false;const host=document.createElement('div');document.body.append(host);const app=Vue.createApp(module.exports.default,{selected,canManage:true});app.config.warnHandler=()=>{};app.mount(host);await flush();return{host,close(){app.unmount();host.remove()}}}

test('owner confirms fixed source and creates an unpublished draft with original admin link',async()=>{const f=await mount();try{
  click(f.host,'整理为博客草稿');await flush();click(f.host,'加入来源');await flush()
  fill(f.host,'文章标题','知识整理');fill(f.host,'人工整理的 Markdown 正文','自己写的正文')
  select(f.host,'分类','2');select(f.host,'标签','3')
  const cover=[...f.host.querySelectorAll('input[type=file]')][0]
  Object.defineProperty(cover,'files',{configurable:true,value:[new File(['cover'],'cover.png',{type:'image/png'})]})
  cover.dispatchEvent(new window.Event('change',{bubbles:true}));await flush()
  click(f.host,'确认来源与预览');await flush();assert.match(f.host.textContent,/来源快照已确认/)
  click(f.host,'创建未公开草稿');await flush()
  const create=calls.find(call=>call.path==='/manage/exports/create')
  assert.equal(create.data.sources.length,1);assert.equal(create.data.body,'自己写的正文')
  assert.equal(f.host.querySelector('a[href="/admin/articles/42"]')?.textContent,'在后台编辑草稿')
  assert.doesNotMatch(f.host.textContent,/knowledge\/private\/objects/)
}finally{f.close()}})

test('uncertain create response keeps the same export id for manual retry',async()=>{const f=await mount();try{
  click(f.host,'整理为博客草稿');await flush();click(f.host,'加入来源');await flush()
  fill(f.host,'人工整理的 Markdown 正文','人工内容');select(f.host,'分类','2');select(f.host,'标签','3')
  const cover=f.host.querySelector('input[type=file]');Object.defineProperty(cover,'files',{configurable:true,value:[new File(['x'],'cover.png',{type:'image/png'})]})
  cover.dispatchEvent(new window.Event('change',{bubbles:true}));await flush()
  click(f.host,'确认来源与预览');await flush()
  createFails=true;click(f.host,'创建未公开草稿');await flush();assert.match(f.host.textContent,/保留本页/)
  createFails=false;click(f.host,'创建未公开草稿');await flush()
  const requests=calls.filter(call=>call.path==='/manage/exports/create')
  assert.equal(requests.length,2);assert.deepEqual(requests[0].data,requests[1].data)
}finally{f.close()}})
