import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
const req = createRequire(new URL('../../blog-frontend/luyu-admin/package.json', import.meta.url))
const { JSDOM } = req('jsdom')
const dom = new JSDOM('<html><body></body></html>', { url: 'https://fixture.invalid/admin/login' })
for (const key of ['window','document','Element','HTMLElement','SVGElement','Node','navigator'])
  Object.defineProperty(globalThis,key,{configurable:true,value:dom.window[key]})
const Vue = req('vue')
const { parse, compileScript } = req('vue/compiler-sfc')
const { build } = req('esbuild')
const root = fileURLToPath(new URL('../../blog-frontend/luyu-admin/', import.meta.url))
const calls=[], notices=[], token=Vue.ref(null)
let result, resendError
const fixture = globalThis.__adminLoginReasons = { Vue,
  login: async () => { calls.push('login'); if (result instanceof Error || typeof result === 'string') throw result; return result },
  resend: async () => { calls.push('resend'); throw resendError },
  verify: async (params) => { calls.push(['verify',params]); return { data: { token:'synthetic',expire:'2099-01-01' } } },
  token, notices, calls,
}
const modules = {
  '@ant-design/icons-vue': 'export const LockOutlined={}, MailOutlined={}, UserOutlined={};',
  '@v-c/utils': 'export const delayTimer = async () => {};',
  '~/api/common/login': 'export const loginApi = () => globalThis.__adminLoginReasons.login(); export const resendAdminLoginApi = () => globalThis.__adminLoginReasons.resend(); export const verifyAdminLoginApi = p => globalThis.__adminLoginReasons.verify(p);',
  '~/api/common/email': 'export const confirmEmailDelivery = async () => {}; export class EmailDeliveryError extends Error {}',
  '~/utils/tools': 'export const getQueryParam = () => "/";',
  '@/utils/page-bubble': 'export default { init(){}, removeListeners(){} };',
}
const filename = `${root}/src/pages/common/login.vue`
const source = readFileSync(filename,'utf8').replace('</script>', 'defineExpose({challenge,clearChallenge});\n</script>')
const { descriptor } = parse(source,{filename})
const content = compileScript(descriptor,{id:'admin-login-reasons',inlineTemplate:true}).content
const bundle = await build({stdin:{contents:content,loader:'ts',resolveDir:root},bundle:true,write:false,platform:'node',format:'cjs',external:['vue','axios'],
  plugins:[{name:'fixtures',setup(b){
    b.onResolve({filter:/^(@|~)/},a=>({path:a.path,namespace:'fixture'}))
    b.onLoad({filter:/.*/,namespace:'fixture'},a=>({loader:'js',contents:modules[a.path] ?? 'export default "";'}))
  }}]})
const prelude = `const {Vue,notices,token,calls}=globalThis.__adminLoginReasons;
const {reactive,ref,shallowRef,computed,onMounted,onBeforeUnmount,unref}=Vue;
const useMessage=()=>Object.fromEntries(['success','error','warning'].map(k=>[k,m=>notices.push(m)]));
const useNotification=()=>({success:m=>notices.push(m.description),error:m=>notices.push(m.description)});
const useAppStore=()=>({layoutSetting:Vue.ref({theme:'light'})}); const storeToRefs=s=>s;
const useRouter=()=>({addRoute(){},push:async()=>calls.push('navigate')});
const useAuthorization=()=>token; const useI18nLocale=()=>({t:key=>key});
const useInterval=()=>({pause(){}}); const useUserStore=()=>({getUserInfo:async()=>{},generateDynamicRoutes:async()=>({})});`
const mod={exports:{}}
new Function('require','module','exports',prelude+bundle.outputFiles[0].text)(req,mod,mod.exports)
const Container={setup:(_, {slots})=>()=>Vue.h('div',slots.default?.())}
const Form={setup:(_, {slots,expose})=>{expose({validate:async()=>{}});return()=>Vue.h('div',slots.default?.())}}
const Input={props:['value'],emits:['update:value'],setup:(p,{emit})=>()=>Vue.h('input',{value:p.value,onInput:e=>emit('update:value',e.target.value)})}
const Button={props:['disabled','loading'],emits:['click'],setup:(p,{emit,slots})=>()=>Vue.h('button',{disabled:p.disabled||p.loading,onClick:()=>emit('click')},slots.default?.())}
const Alert={props:['message'],setup:p=>()=>Vue.h('div',{role:'alert'},p.message)}
async function flush(){await new Promise(r=>setImmediate(r));await Vue.nextTick()}
function mount(){
 calls.length=0;notices.length=0;token.value=null
 const host=document.createElement('div');document.body.append(host);const app=Vue.createApp(mod.exports.default);app.config.warnHandler=()=>{}
 app.component('a-form',Form);app.component('a-input',Input);app.component('a-input-password',Input);app.component('a-button',Button);app.component('a-alert',Alert)
 for(const name of ['a-divider','a-tabs','a-tab-pane','a-form-item','a-checkbox'])app.component(name,Container)
 const vm=app.mount(host);return {host,vm,cleanup:()=>{app.unmount();host.remove()}}
}
function click(host,label){const b=[...host.querySelectorAll('button')].find(b=>b.textContent.trim()===label);assert.ok(b,label);b.click()}
test('admin displays controlled and legacy credential errors once with no misleading static alert', async()=>{
 const {host,cleanup}=mount()
 try {
  for(const text of ['验证码发送过于频繁，请等待 42 秒后重试','验证码发送服务暂不可用，请稍后重试','用户名或密码错误']){
   result=text;click(host,'pages.login.submit');await flush()
   assert.equal(host.querySelector('[role="alert"]').textContent,text)
   assert.deepEqual(notices,[]);assert.equal(token.value,null);assert.ok(!calls.includes('navigate'))
  }
 }finally{cleanup()}
})
test('resend cooldown and send failure preserve original challenge and permit code verification',async()=>{
 result={data:{secondFactorRequired:true,challengeId:'original',maskedEmail:'f***@example.invalid',expiresIn:300,resendAfter:60}}
 const {host,vm,cleanup}=mount()
 try {
  click(host,'pages.login.submit');await flush();notices.length=0
  for(const text of ['验证码发送过于频繁，请等待 5 秒后重试','验证码发送服务暂不可用，请稍后重试']){
   vm.challenge.resendAfter=0;await flush();resendError=text;click(host,'重新发送');await flush()
   assert.equal(vm.challenge.id,'original');assert.equal(host.querySelectorAll('[role="alert"]')[1].textContent,text)
   assert.deepEqual(notices,[]);assert.equal(token.value,null)
  }
  const input=host.querySelector('input');input.value='123456';input.dispatchEvent(new window.Event('input'));await flush()
  click(host,'验证并登录');await flush()
  assert.ok(calls.some(x=>Array.isArray(x)&&x[0]==='verify'&&x[1].challengeId==='original'))
  assert.ok(calls.includes('navigate'));assert.equal(JSON.parse(token.value).token,'synthetic')
 }finally{cleanup()}
})
test('returning from code step then logging in during cooldown remains on password form',async()=>{
 result={data:{secondFactorRequired:true,challengeId:'original',expiresIn:300,resendAfter:60}}
 const {host,cleanup}=mount()
 try {
  click(host,'pages.login.submit');await flush();click(host,'返回重新登录');await flush()
  result='验证码发送过于频繁，请等待 30 秒后重试';notices.length=0
  click(host,'pages.login.submit');await flush()
  assert.equal(host.querySelector('[role="alert"]').textContent,result);assert.equal(token.value,null)
  assert.ok(!host.textContent.includes('验证并登录'));assert.deepEqual(notices,[])
 }finally{cleanup()}
})
