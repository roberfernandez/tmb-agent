import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';

const page = readFileSync(new URL('../public/proves/servei/index.html', import.meta.url), 'utf8');

function isolated(source, globals) {
  const start = page.indexOf(source);
  if (start < 0) throw new Error('Missing code: ' + source);
  return {start, ctx:vm.createContext(globals)};
}

test('manual corporate watch passes train, selected station and via to the official-only app', () => {
  const {start,ctx} = isolated('function openAndroidEye(params){', {
    selectedAndroidSource:()=> 'corporate',
    rememberAndroidLaunch:()=>{},
    location:{href:''}
  });
  vm.runInContext(page.slice(start, page.indexOf('\nif(isAndroid){',start)),ctx);
  ctx.openAndroidEye(new URLSearchParams({mode:'manual',train:'418',stop:'424',via:'2'}));
  assert.match(ctx.location.href,/^intent:\/\/watch\?/);
  assert.match(ctx.location.href,/mode=manual/);
  assert.match(ctx.location.href,/train=418/);
  assert.match(ctx.location.href,/stop=424/);
  assert.match(ctx.location.href,/via=2/);
  assert.match(ctx.location.href,/#Intent;scheme=miralincorp;package=cat\.tmbagent\.miralincorporatiu\.servei;end$/);
});

test('existing classic eye remains available as a separate source', () => {
  const {start,ctx} = isolated('function openAndroidEye(params){', {
    selectedAndroidSource:()=> 'classic',
    rememberAndroidLaunch:()=>{},
    location:{href:''}
  });
  vm.runInContext(page.slice(start, page.indexOf('\nif(isAndroid){',start)),ctx);
  ctx.openAndroidEye(new URLSearchParams({mode:'manual',train:'406',stop:'413'}));
  assert.match(ctx.location.href,/#Intent;scheme=tmbservei;package=com\.tmbagent\.traineye;end$/);
});

test('Stop targets the last Android app actually opened, not the currently selected source',async()=>{
  const start=page.indexOf('async function stopNativeEye(){'),end=page.indexOf("$('startServiceEye').onclick",start);
  assert.ok(start>0&&end>start);
  const ctx=vm.createContext({isAndroid:true,isIOS:false,
    activeAndroidSourceKey:'active',selectedAndroidSource:()=> 'classic',
    localStorage:{getItem:key=>key==='active'?'corporate':null},
    location:{href:''},setTimeout:resolve=>resolve(),Promise});
  vm.runInContext(page.slice(start,end),ctx);
  await ctx.stopNativeEye();
  assert.equal(ctx.location.href,'miralincorp://stop');
});

test('automatic mode hands off the existing schedule without image data to the corporate app',async()=>{
  const start=page.indexOf("$('startServiceEye').onclick=async()=>{"),end=page.indexOf("$('stopServiceEye').onclick",start);
  const elements=new Map(),values=new Map([['tmb-servei-android-eye-source-v1','corporate']]);
  const ctx=vm.createContext({
    isAndroid:true,isIOS:false,enabled:true,serviceLaunchBusy:false,remoteAlertsActive:false,alertSettingsDirty:false,
    rows:[{type:'CON',from:'434',to:'413',a:1409,b:1469,train:'405',via:'2',out:'RET'}],
    state:{photoPending:false,date:'2026-11-17',photoData:null,margin:5,manualRowIndex:null,manualSince:null},
    localStorage:{getItem:k=>values.get(k)??null,setItem:(k,v)=>values.set(k,v)},
    $:id=>{if(!elements.has(id))elements.set(id,{});return elements.get(id)},
    stopTrainPushWatch:async()=>true,stopLegacyServiceAlerts:async()=>{},
    refreshServiceFollowControls:()=>{},showServiceEyeError:()=>{},
    serviceScheduleParam:()=> '1409,1469,CON,434,413,405,2,RET',
    serviceRowBoundsParam:()=> 'private image metadata',prepareNativeServiceImage:async()=>{throw Error('must not copy image')},
    URLSearchParams,Date,render:()=>{},persist:()=>{},showFollowMode:()=>{}
  });
  let result;ctx.openAndroidEye=params=>{result=params};
  vm.runInContext(page.slice(start,end),ctx);
  await ctx.$('startServiceEye').onclick();
  assert.equal(result.get('mode'),'service');
  assert.equal(result.get('schedule'),'1409,1469,CON,434,413,405,2,RET');
  assert.equal(result.get('image'),null);
  assert.equal(result.get('rowBounds'),null);
  assert.equal(result.get('alerts'),'0');
});
