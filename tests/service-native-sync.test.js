import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';

const page=readFileSync(new URL('../public/proves/servei/index.html',import.meta.url),'utf8');
function context(){
 const elements=new Map(),values=new Map();
 const rows=[{a:1264,b:1389,type:'EST',from:'423',to:'423',train:''},{a:1389,b:1409,type:'TRA',from:'423',to:'434',train:''},{a:1409,b:1469,type:'CON',from:'434',to:'413',train:'405',via:'2',out:'RET',start:'23:29'}];
 const ctx={isAndroid:true,isIOS:false,followMode:'service',serviceLaunchBusy:false,nativeServiceKey:'native',rows,stations:{434:'Trinitat Nova'},Date:class extends Date{static now(){return 22*60*60000+23*60000}},when:m=>m*60000,
  $:id=>{if(!elements.has(id))elements.set(id,{});return elements.get(id)},
  localStorage:{getItem:k=>values.get(k)??null,setItem:(k,v)=>values.set(k,v)},
  serviceScheduleParam:()=>rows.map(r=>[r.a,r.b,r.type,r.from,r.to,r.train||'',r.via||'',r.out||''].join(',')).join(';'),
  URLSearchParams,location:{},enabled:true,state:{date:'2026-01-01',margin:7},
  buildServiceAlertEvents:()=>[{},{}],stopLegacyServiceAlerts:async()=>{},syncWebServiceAlerts:async()=>{throw Error('Android should use native alerts')}};
 return {ctx:vm.createContext(ctx),elements,values};
}

test('replacing an active service exposes update and previews 405, then leaves only Stop after transfer',()=>{
 const {ctx,elements,values}=context();values.set('native','old service with 403');
 vm.runInContext(page.slice(page.indexOf('function refreshServiceFollowControls()'),page.indexOf('function serviceScheduleParam()')),ctx);
 ctx.refreshServiceFollowControls();
 assert.equal(elements.get('startServiceEye').hidden,false);
 assert.match(elements.get('startServiceEye').innerHTML,/Actualitza/);
 assert.match(elements.get('serviceTrainPreview').textContent,/405 · 23:29 · Trinitat Nova/);
 assert.equal(elements.get('stopServiceEye').hidden,false);
 values.set('native',ctx.serviceScheduleParam());ctx.refreshServiceFollowControls();
 assert.equal(elements.get('startServiceEye').hidden,true);
 assert.equal(elements.get('stopServiceEye').hidden,false);
});

test('activation passes the new schedule, alert switch and custom margin to Android',async()=>{
 const {ctx}=context();let captured;
 ctx.rows[2].train='422';
 Object.assign(ctx,{refreshServiceFollowControls:()=>{},stopTrainPushWatch:async()=>true,showServiceEyeError:()=>{},persist:()=>{},showFollowMode:()=>{},render:()=>{},prepareNativeServiceImage:async()=>'',serviceRowBoundsParam:()=>'',openAndroidEye:query=>captured=query});
 const start=page.indexOf("$('startServiceEye').onclick=async()=>{");
 vm.runInContext(page.slice(start,page.indexOf("$('stopServiceEye').onclick",start)),ctx);
 await ctx.$('startServiceEye').onclick();
 assert.match(captured.get('schedule'),/,422,2,RET/);
 assert.equal(captured.get('alerts'),'1');assert.equal(captured.get('margin'),'7');
});

test('native alerts do not require browser push and pause on an unapplied service replacement',async()=>{
 const {ctx,values}=context();values.set('native',ctx.serviceScheduleParam());
 const start=page.indexOf('async function syncServiceAlerts(stop=false)');
 vm.runInContext(page.slice(start,page.indexOf('let alertSyncTimer',start)),ctx);
 let result=await ctx.syncServiceAlerts(false);
 assert.equal(result.native,true);assert.match(ctx.location.href,/alerts=1/);
 values.set('native','old service');result=await ctx.syncServiceAlerts(false);
 assert.equal(result.pending,true);assert.match(ctx.location.href,/alerts=0/);
});

test('legacy migration stops both web watches once and then works without Render',async()=>{
 const {ctx}=context();let requests=0;
 ctx.navigator={serviceWorker:{getRegistration:async()=>({pushManager:{getSubscription:async()=>({toJSON:()=>({endpoint:'test'})})}})}};
 ctx.fetch=async()=>{requests++;return {ok:true}};ctx.AbortSignal=AbortSignal;
 const start=page.indexOf('async function stopLegacyServiceAlerts(){');
 vm.runInContext(page.slice(start,page.indexOf('async function syncServiceAlerts',start)),ctx);
 await ctx.stopLegacyServiceAlerts();assert.equal(requests,2);
 ctx.fetch=()=>{throw Error('Render unavailable')};
 await ctx.stopLegacyServiceAlerts();assert.equal(requests,2);
});


test('a pending capture cannot start automatic tracking from the saved service',async()=>{
 const {ctx,elements,values}=context();values.set('native','old service');ctx.state.photoPending=true;
 let stopped=0,launched=0;
 Object.assign(ctx,{stopTrainPushWatch:async()=>{stopped++;return true},openAndroidEye:()=>launched++});
 const start=page.indexOf('function refreshServiceFollowControls()');
 vm.runInContext(page.slice(start,page.indexOf('function serviceScheduleParam()',start)),ctx);
 const handler=page.indexOf("$('startServiceEye').onclick=async()=>{");
 vm.runInContext(page.slice(handler,page.indexOf("$('stopServiceEye').onclick",handler)),ctx);
 ctx.refreshServiceFollowControls();
 assert.equal(elements.get('startServiceEye').disabled,true);
 assert.equal(elements.get('stopServiceEye').hidden,false);
 assert.doesNotMatch(elements.get('serviceTrainPreview').textContent,/405/);
 await ctx.$('startServiceEye').onclick();
 assert.equal(stopped,0);
 assert.equal(launched,0);
});
