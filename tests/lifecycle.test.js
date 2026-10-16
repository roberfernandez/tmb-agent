import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import vm from 'node:vm';
const source=await readFile(new URL('../src/main.js',import.meta.url),'utf8');
const flush=()=>new Promise(resolve=>setImmediate(resolve));
function setup(){
 const events={},intervals=new Map(); let clears=0,renders=0,scroll=450,draft='consulta en curs',state='approved',redirect='';
 const context=vm.createContext({content:{replaceChildren(){clears++;scroll=0;draft='';},focus(){}},element:()=>({addEventListener(){}}),checkSession:async()=>{if(state==='offline')throw Error('offline');return state;},callAdmin:async()=>({can_manage:false}),launcherHeartbeat(){},render(){renders++;},canAdmin:false,SESSION_KEY:'session',loginUrl:()=>'/auth/',location:{hash:'#/bustia',replace(url){redirect=url;}},navigator:{},document:{hidden:false,addEventListener:(key,fn)=>events[key]=fn},window:{addEventListener:(key,fn)=>events[key]=fn,scrollTo(){scroll=0;}},setInterval:(fn,ms)=>intervals.set(ms,fn)});
 vm.runInContext(source.slice(source.indexOf('let checking = 0;')),context);
 return {context,events,intervals,setState(value){state=value;},edit(){scroll=450;draft='consulta en curs';},snapshot:()=>({clears,renders,scroll,draft,redirect})};
}
test('hourly access check preserves the live consultation, scroll and draft',async()=>{
 const app=setup();await flush();app.edit();const before=app.snapshot();
 assert.equal(app.intervals.has(60000),false);assert.ok(app.intervals.has(3600000));
 app.intervals.get(3600000)();await flush();assert.deepEqual(app.snapshot(),before);
 app.context.document.hidden=true;app.events.visibilitychange();
 app.context.document.hidden=false;app.events.visibilitychange();await flush();assert.deepEqual(app.snapshot(),before);
 app.events.pagehide();app.events.pageshow({persisted:true});await flush();assert.deepEqual(app.snapshot(),before);
});
test('temporary connection failure preserves consultation; rejected access clears and redirects',async()=>{
 const app=setup();await flush();app.edit();const before=app.snapshot();
 app.setState('offline');app.intervals.get(3600000)();await flush();assert.deepEqual(app.snapshot(),before);
 app.setState('login');app.intervals.get(3600000)();await flush();assert.equal(app.snapshot().redirect,'/auth/');assert.equal(app.snapshot().draft,'');
});
test('intentional route changes still render the destination',async()=>{
 const app=setup();await flush();app.context.location.hash='#/permisos';app.events.hashchange();await flush();assert.equal(app.snapshot().renders,2);
});
