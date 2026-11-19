import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';

const page=readFileSync(new URL('../public/proves/servei/index.html',import.meta.url),'utf8');
function runPart(startText,endText,context){
 const start=page.indexOf(startText),end=page.indexOf(endText,start);
 assert.ok(start>=0&&end>start,'Bridge helper segment present');
 vm.runInContext(page.slice(start,end),vm.createContext(context));
 return context;
}
test('El meu servei has one native engine and no permanently visible corporate sign-in',()=>{
 assert.doesNotMatch(page,/id="tmbIdentify"/);
 assert.doesNotMatch(page,/id="corporateIdentity"/);
 assert.doesNotMatch(page,/id="androidEyeSource"/);
 assert.doesNotMatch(page,/selectedAndroidSource/);
 assert.match(page,/id="startServiceEye"/);
 assert.match(page,/function openAndroidEye\(params\)/);
});

test('all eye actions use the hidden corporate package and app-local installation fallback',()=>{
 const ctx={location:{origin:'https://roberfernandez.github.io',pathname:'/tmb-agent/proves/servei/',href:''},
   nativePackage:'cat.tmbagent.elmeuservei.motor',URLSearchParams};
 runPart('function nativeIntent(host,params=', 'function rememberAndroidLaunch(',ctx);
 ctx.nativeIntent('watch',new URLSearchParams({mode:'manual',train:'418',stop:'424',via:'2'}));
 assert.match(ctx.location.href,/^intent:\/\/watch\?/);
 assert.match(ctx.location.href,/train=418/);
 assert.match(ctx.location.href,/stop=424/);
 assert.match(ctx.location.href,/via=2/);
 assert.match(ctx.location.href,/package=cat\.tmbagent\.elmeuservei\.motor;/);
 assert.match(ctx.location.href,/S\.browser_fallback_url=/);
 assert.doesNotMatch(ctx.location.href,/tmbservei;package=com\.tmbagent/);
});

test('manual mode sends requested train/station/via entirely from the PWA',()=>{
 let call;
 const ctx={rememberAndroidLaunch:()=>{},nativeIntent:(host,params)=>{call={host,params}}};
 runPart('function openAndroidEye(params){','\nlet state=',ctx);
 ctx.openAndroidEye(new URLSearchParams({mode:'manual',train:'418',stop:'424',via:'2'}));
 assert.equal(call.host,'watch');
 assert.equal(call.params.get('train'),'418');
 assert.equal(call.params.get('stop'),'424');
 assert.equal(call.params.get('via'),'2');
});

test('eye Stop always targets the same corporate helper',async()=>{
 let action;
 const ctx={isAndroid:true,isIOS:false,nativeIntent:host=>{action=host},
   setTimeout:(resolve)=>resolve(),Promise};
 runPart('async function stopNativeEye(){',"$('startServiceEye').onclick",ctx);
 await ctx.stopNativeEye();
 assert.equal(action,'stop');
});

test('automatic follow transfers schedule, alerts and margin without credentials or photo',async()=>{
 const start=page.indexOf("$('startServiceEye').onclick=async()=>{"),end=page.indexOf("$('stopServiceEye').onclick",start);
 assert.ok(start>0&&end>start);
 const elements=new Map();
 const ctx={isAndroid:true,isIOS:false,followMode:'',enabled:true,serviceLaunchBusy:false,alertSettingsDirty:false,
  rows:[{type:'CON',from:'434',to:'413',a:1409,b:1469,train:'405',via:'2',out:'RET'}],
  state:{photoPending:false,date:'2026-11-17',photoData:null,margin:5},
  $:id=>{if(!elements.has(id))elements.set(id,{});return elements.get(id)},
  stopTrainPushWatch:async()=>true,refreshServiceFollowControls:()=>{},
  serviceScheduleParam:()=> '1409,1469,CON,434,413,405,2,RET',
  showServiceEyeError:()=>{},persist:()=>{},showFollowMode:()=>{},render:()=>{},
  URLSearchParams};
 let result;ctx.openAndroidEye=params=>{result=params};
 vm.runInContext(page.slice(start,end),vm.createContext(ctx));
 await ctx.$('startServiceEye').onclick();
 assert.equal(result.get('mode'),'service');
 assert.equal(result.get('schedule'),'1409,1469,CON,434,413,405,2,RET');
 assert.equal(result.get('image'),null);
 assert.equal(result.get('rowBounds'),null);
 assert.equal(result.get('password'),null);
 assert.equal(result.get('alerts'),'1');
 assert.equal(result.get('margin'),'5');
});
