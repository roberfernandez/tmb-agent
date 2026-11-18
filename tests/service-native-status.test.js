import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';

const page=readFileSync(new URL('../public/proves/servei/index.html',import.meta.url),'utf8');
const statusStart=page.indexOf('async function syncNativeEyeState(){');
const statusEnd=page.indexOf('if(isAndroid){\n // On return from Android',statusStart);
assert.ok(statusStart>=0&&statusEnd>statusStart,'native status helper is present');
const statusSource=page.slice(statusStart,statusEnd);

function statusHarness({response,followMode='service',seen=false}={}){
 const data=new Map(),changes=[],elements=new Map();
 if(seen)data.set('seen','true');
 const ctx={
  isAndroid:true,
  document:{hidden:false},
  nativeStatusChecking:false,nativeStatusErrors:0,nativeStatusGraceUntil:0,
  nativeStatusUrl:'http://127.0.0.1:18764/status',
  nativeStatusSeenKey:'seen',nativeInstalledKey:'installed',nativeAuthKey:'auth',nativeServiceKey:'schedule',
  followMode,
  localStorage:{
   getItem:k=>data.get(k)??null,
   setItem:(k,v)=>data.set(k,v),
   removeItem:k=>data.delete(k)
  },
  Date,AbortSignal,
  $:key=>{if(!elements.has(key))elements.set(key,{hidden:true});return elements.get(key)},
  fetch:async()=>{if(response instanceof Error)throw response;return {ok:true,json:async()=>response};},
  showFollowMode:mode=>{ctx.followMode=mode;changes.push(mode)},
  render:()=>{},refreshAndroidDownload:()=>{}
 };
 vm.runInContext(statusSource,vm.createContext(ctx));
 return {ctx,data,changes,elements};
}

test('a running corporate manual eye replaces stale automatic UI state',async()=>{
 const {ctx,data,changes}=statusHarness({response:{active:true,mode:'manual'}});
 data.set('schedule','old');
 await ctx.syncNativeEyeState();
 assert.equal(ctx.followMode,'manual');
 assert.deepEqual(changes,['manual']);
 assert.equal(data.get('seen'),'true');
 assert.equal(data.get('installed'),'true');
 assert.equal(data.has('schedule'),false);
});

test('closing the floating X clears Atura from an explicit OFF response on the first successful check',async()=>{
 const {ctx,data,changes,elements}=statusHarness({response:{active:false,mode:'off'},seen:false,followMode:'manual'});
 data.set('schedule','old');
 await ctx.syncNativeEyeState();
 assert.equal(ctx.followMode,'');
 assert.deepEqual(changes,['']);
 assert.equal(data.get('seen'),'true');
 assert.equal(data.has('schedule'),false);
 assert.equal(elements.get('nativeStatusRecovery').hidden,true);
});

test('loopback failure does not invent a stopped eye, but offers verified native fallback',async()=>{
 const {ctx,changes,elements}=statusHarness({response:new Error('ERR_CONNECTION_REFUSED'),seen:false});
 await ctx.syncNativeEyeState();
 assert.equal(ctx.followMode,'service');
 await ctx.syncNativeEyeState();
 assert.equal(ctx.followMode,'service');
 assert.deepEqual(changes,[]);
 assert.equal(elements.get('nativeStatusRecovery').hidden,false);
});

test('even after a prior check a blocked loopback never reports fake OFF',async()=>{
 const {ctx,changes,elements}=statusHarness({response:new Error('Access to local network denied'),seen:true});
 await ctx.syncNativeEyeState();
 await ctx.syncNativeEyeState();
 assert.equal(ctx.followMode,'service');
 assert.deepEqual(changes,[]);
 assert.equal(elements.get('nativeStatusRecovery').hidden,false);
});

test('manual Stop is never visible in automatic mode and manual Start is hidden',()=>{
 const section=page.slice(page.indexOf('function refreshManualWatchControls(){'),page.indexOf('function showServiceEyeError('));
 const elements=new Map();
 function $(id){if(!elements.has(id))elements.set(id,{});return elements.get(id)}
 const ctx={
  followMode:'service',watchEditing:false,watchActionBusy:false,
  watchTrain:{},watchStop:{value:'434'},watchVia:{},$: $,validManualStop:()=>true,terminalManualStop:()=>true
 };
 vm.runInContext(section,vm.createContext(ctx));
 ctx.refreshManualWatchControls();
 assert.equal($('stopTrainWatch').hidden,true);
 assert.equal($('startTrainWatch').hidden,true);
 ctx.followMode='manual';
 ctx.refreshManualWatchControls();
 assert.equal($('stopTrainWatch').hidden,false);
 assert.equal($('startTrainWatch').hidden,true);
 ctx.followMode='';
 ctx.refreshManualWatchControls();
 assert.equal($('stopTrainWatch').hidden,true);
 assert.equal($('startTrainWatch').hidden,false);
});
