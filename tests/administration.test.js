import test from 'node:test';
import assert from 'node:assert/strict';
import { authorize, validPeriod, MODULE_IDS } from '../supabase/functions/tmb-agent-admin/policy.mjs';
import { callAdmin } from '../src/administration.js';
import { modules } from '../src/modules.js';
test('administrative authorization denies unapproved and user-editable claims', () => {
  assert.equal(authorize(null,true,'dashboard'),401);
  for (const action of ['capabilities','heartbeat','leave','open','dashboard']) assert.equal(authorize({id:'x',app_metadata:{bustia_admin:true}},false,action),403);
  for (const user of [{id:'x'}, {id:'x',user_metadata:{bustia_admin:true}}, {id:'x',app_metadata:{bustia_admin:'true'}}]) assert.equal(authorize(user,true,'dashboard'),403);
  assert.equal(authorize({id:'x',app_metadata:{bustia_admin:true}},true,'dashboard'),0);
});
test('periods and module allowlist exclude arbitrary input and private administration', () => {
  for(const value of [0,-1,91,'30',null,{},Infinity]) assert.equal(validPeriod(value),null);
  for(const value of [7,30,90]) assert.equal(validPeriod(value),value);
  assert.deepEqual(MODULE_IDS,[...modules.filter(m=>!m.adminOnly).map(m=>m.id)]);
});
test('requests use session JWT and propagate denied access without trusting response data', async () => {
  const storage={ getItem:()=>JSON.stringify({access_token:'test-jwt'}) };
  await assert.rejects(callAdmin({action:'dashboard'},async(url,options)=>{
    assert.equal(options.headers.Authorization,'Bearer test-jwt');
    assert.equal(options.cache,'no-store');
    assert.deepEqual(JSON.parse(options.body),{action:'dashboard'});
    return {ok:false,json:async()=>({ok:true,error:'Accés denegat'})};
  },storage),/Accés denegat/);
});
import { readFileSync } from 'node:fs';
import { stripTypeScriptTypes } from 'node:module';
import { runInNewContext } from 'node:vm';
const edgeSource = stripTypeScriptTypes(readFileSync(new URL('../supabase/functions/tmb-agent-admin/index.ts',import.meta.url),'utf8').replace(/^import .*;\r?\n/gm,''));
async function invoke({ authenticated=true, approved=true, admin=false, authError=false, approvalError=false, rpcError=false, body={action:'dashboard',days:30}, bearer='Bearer test', method='POST' }={}) {
  let handler; const calls=[];
  const client={ auth:{getUser:async(token)=>{ calls.push(['auth',token]); return { data:{user:authenticated?{id:'server-user',app_metadata:{bustia_admin:admin},user_metadata:{bustia_admin:true}}:null},error:authError?{}:null }; }},
    from(table){ const query={select(){return this;},eq(){return this;},limit:async()=>{calls.push(['approval']);return {data:approved?[{id:1}]:[],error:approvalError?{}:null};}}; return query; },
    rpc:async(name,args)=>{calls.push(['rpc',name,args]);return {data:{users:[],events:[],stats:[]},error:rpcError?{}:null};} };
  runInNewContext(edgeSource,{createClient:()=>client,authorize,validPeriod,MODULE_IDS,Response,console,Deno:{env:{get:()=> 'test-only'},serve:fn=>{handler=fn;}}});
  const response=await handler(new Request('https://example.invalid',{method,headers:bearer?{Authorization:bearer}:{},body:method==='POST'?JSON.stringify(body):undefined}));
  return {status:response.status,data:await response.json(),calls};
}
test('real edge handler validates JWT and approval before executing privileged queries',async()=>{
  for(const options of [{bearer:''},{bearer:'invalid'},{authenticated:false},{authError:true},{approved:false,admin:true},{admin:false},{approvalError:true,admin:true}]) {
    const r=await invoke(options);assert.ok([401,403,503].includes(r.status));assert.equal(r.calls.filter(c=>c[0]==='rpc').length,0);
  }
  const r=await invoke({admin:true});assert.equal(r.status,200);assert.equal(r.calls[0][0],'auth');assert.equal(r.calls[1][0],'approval');assert.equal(r.calls[2][1],'tmb_agent_dashboard');
});
test('edge rejects invalid event/period and uses verified user ID for writes',async()=>{
  for(const body of [{action:'open',module:'unknown'},{action:'dashboard',days:365},{action:'heartbeat',tab:'bad'}]) {
    const r=await invoke({admin:true,body});assert.equal(r.status,400);assert.equal(r.calls.filter(c=>c[0]==='rpc').length,0);
  }
  const r=await invoke({body:{action:'open',module:'nomina',user_id:'forged',opened_at:'forged'}});
  assert.equal(r.status,200); assert.equal(r.calls.at(-1)[2].p_user,'server-user');assert.equal(r.calls.at(-1)[2].p_module,'nomina');
  assert.equal((await invoke({admin:true,rpcError:true})).status,503);
});
test('dashboard renders server data as text and exposes launcher-only presence limits',async()=>{
  class Element {
    children=[]; style={}; listeners={}; isConnected=true; value='';
    constructor(tag){this.tag=tag;}
    append(...children){this.children.push(...children); if(this.tag==='select' && !this.value) this.value=this.children[0].value;}
    replaceChildren(...children){this.children=children;}
    addEventListener(name,fn){this.listeners[name]=fn;}
  }
  const previous={document:globalThis.document,localStorage:globalThis.localStorage,fetch:globalThis.fetch};
  globalThis.document={createElement:tag=>new Element(tag)};
  globalThis.localStorage={getItem:()=>JSON.stringify({access_token:'test'})};
  globalThis.fetch=async()=>({ok:true,json:async()=>({ok:true,server_time:new Date().toISOString(),users:[{email:'<script>test</script>',authorization:'aprobado',online:true}],stats:[{module:'nomina',opens:2,unique_users:1}],events:[{email:'example.invalid',module:'nomina'}]})});
  try {
    const {renderAdministration}=await import('../src/administration.js');
    const panel=renderAdministration();
    await new Promise(resolve=>setImmediate(resolve));
    const flatten=n=>[n,...n.children.flatMap(flatten)];
    const nodes=flatten(panel),texts=nodes.map(n=>n.textContent||'').join(' ');
    assert.ok(texts.includes('<script>test</script>'));
    assert.ok(texts.includes('75 segons'));
    assert.ok(texts.includes('no acrediten ús ni presència'));
    assert.equal(nodes.filter(n=>n.tag==='script').length,0);
    assert.equal(nodes.filter(n=>n.tag==='table').length,3);
  } finally {Object.assign(globalThis,previous);}
});

