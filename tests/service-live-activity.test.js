import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';

const page=readFileSync(new URL('../public/proves/servei/index.html',import.meta.url),'utf8');
const begin=page.indexOf('function serviceScheduleKey(){');
const end=page.indexOf('function render(){',begin);
assert.ok(begin>=0 && end>begin,'Live duty calculation is present');

const rows=[
 {a:1126,b:1146,type:'TRA',from:'413',to:'422',train:'',i:0},
 {a:1146,b:1226,type:'EST',from:'422',to:'422',train:'',i:1},
 {a:1226,b:1298,type:'CON',from:'422',to:'434',train:'414',via:'2',out:'RET',i:2},
 {a:1298,b:1409,type:'EST',from:'434',to:'434',train:'',i:3},
 {a:1409,b:1469,type:'CON',from:'434',to:'413',train:'405',via:'2',start:'23:29',out:'RET',i:4},
 {a:1469,b:1490,type:'EST',from:'413',to:'413',train:'',i:5}
];

function harness(){
 const now=1421*60000; // 23:41, twelve minutes after the planned pickup
 const ctx={isAndroid:true,followMode:'service',state:{date:'2026-11-17',photoPending:false,taken:[]},
  rows,manualRowIndex:null,manualSince:0,nativeDuty:null,when:m=>m*60000,
  serviceScheduleParam:()=>rows.map(r=>[r.a,r.b,r.type,r.from,r.to,r.train||'',r.via||'',r.out||''].join(',')).join(';')
 };
 vm.runInContext(page.slice(begin,end),vm.createContext(ctx));
 return {ctx,now};
}

test('a late train at Via Júlia keeps station highlighted after 23:29',()=>{
 const {ctx,now}=harness();
 assert.equal(ctx.pendingPickup(now).i,4);
 assert.equal(ctx.activeServiceRow(now).i,3);
});

test('a confirmed corporate arrival advances to the correct driving row',()=>{
 const {ctx,now}=harness();
 ctx.nativeDuty={plan:ctx.serviceScheduleKey(),row:4,pickup:4,waiting:false,source:'corporate',at:now};
 assert.equal(ctx.pendingPickup(now),null);
 assert.equal(ctx.activeServiceRow(now).i,4);
});

test('a waiting native snapshot does not override a manual boarding confirmation',()=>{
 const {ctx,now}=harness();
 ctx.nativeDuty={plan:ctx.serviceScheduleKey(),row:3,pickup:4,waiting:true,at:now};
 assert.equal(ctx.activeServiceRow(now).i,3);
 ctx.state.taken.push(4);
 assert.equal(ctx.pendingPickup(now),null);
 assert.equal(ctx.activeServiceRow(now).i,4);
});

test('stale or wrong-plan native row cannot jump the service to driving',()=>{
 const {ctx,now}=harness();
 ctx.nativeDuty={plan:'unrelated',row:4,pickup:4,waiting:false,at:now};
 assert.equal(ctx.activeServiceRow(now).i,3);
 ctx.nativeDuty={plan:ctx.serviceScheduleKey(),row:4,pickup:4,waiting:false,at:now-30000};
 assert.equal(ctx.activeServiceRow(now).i,3);
});

test('touching a driving row sends its position without ever stopping automatic supervision',()=>{
 const {ctx,now}=harness();
 let sent,stops=0;
 Object.assign(ctx,{
  Date:class extends Date{static now(){return now}},
  stations:{413:'La Pau',434:'Trinitat Nova'},
  URLSearchParams,
  nativeStatusGraceUntil:0,
  nativeIntent:(host,params)=>{sent={host,params}},
  stopNativeEye:()=>{stops++},
  showFollowMode:()=>{throw Error('Unexpected mode change')},
  persist:()=>{},render:()=>{}
 });
 const start=page.indexOf('function selectServiceRow(index){');
 const finish=page.indexOf("$('photoRowHits').addEventListener('click'",start);
 assert.ok(start>=0&&finish>start);
 vm.runInContext(page.slice(start,finish),vm.createContext(ctx));
 ctx.selectServiceRow(4);
 assert.equal(ctx.followMode,'service');
 assert.equal(ctx.manualRowIndex,4);
 assert.equal(ctx.state.location.label,'A bord del 405');
 assert.equal(stops,0);
 assert.equal(sent.host,'row');
 assert.equal(sent.params.get('row'),'4');
 assert.equal(sent.params.get('plan'),ctx.serviceScheduleKey());
 assert.equal(ctx.state.taken.length,0,'native status is the authority for boarding');
});

test('touching station row keeps automatic monitoring and reports waiting activity',()=>{
 const {ctx,now}=harness();
 let sent;
 Object.assign(ctx,{
  Date:class extends Date{static now(){return now}},
  stations:{413:'La Pau',434:'Trinitat Nova'},
  URLSearchParams,nativeStatusGraceUntil:0,
  nativeIntent:(host,params)=>{sent={host,params}},
  persist:()=>{},render:()=>{}
 });
 const start=page.indexOf('function selectServiceRow(index){');
 const finish=page.indexOf("$('photoRowHits').addEventListener('click'",start);
 vm.runInContext(page.slice(start,finish),vm.createContext(ctx));
 ctx.selectServiceRow(3);
 assert.equal(ctx.manualRowIndex,3);
 assert.equal(ctx.activeServiceRow(now).i,3);
 assert.equal(sent.host,'row');
 assert.equal(sent.params.get('row'),'3');
 assert.equal(ctx.followMode,'service');
});

test('redundant He agafat el tren actions are absent',()=>{
 assert.doesNotMatch(page,/id="taken"/);
 assert.doesNotMatch(page,/id="confirmPickup"/);
});

test('the accepted service date, not today, anchors cross-midnight times',()=>{
 const start=page.indexOf('function when(m){'),end=page.indexOf('function desc(r){',start);
 assert.ok(start>=0&&end>start);
 const ctx={state:{date:'2026-11-17'},Date};
 vm.runInContext(page.slice(start,end),vm.createContext(ctx));
 assert.equal(ctx.when(1470),new Date(2026,10,18,0,30).getTime());
});
