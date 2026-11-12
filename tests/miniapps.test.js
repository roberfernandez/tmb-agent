import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile,stat} from 'node:fs/promises';
import {procedures,payments,upcomingPayment} from '../src/miniapps.js';
import {modules} from '../src/modules.js';
test('payment windows preserve the source periods and Madrid date',()=>{
 assert.deepEqual(payments.map(p=>p.month),[0,1,2,5,8,9,11]);
 assert.equal(upcomingPayment(new Date('2026-10-03T12:00:00Z')).title,'Prima de productivitat');
 assert.equal(upcomingPayment(new Date('2026-11-03T12:00:00Z')).title,'Prima de productivitat');
 assert.equal(upcomingPayment(new Date('2026-11-30T12:00:00Z')).paidMonth,10);
 assert.equal(upcomingPayment(new Date('2026-12-03T12:00:00Z')).title,'Paga extraordinària de Nadal');
 assert.equal(upcomingPayment(new Date('2026-01-03T12:00:00Z')).paidMonth,1);
 assert.equal(upcomingPayment(new Date('2026-12-31T23:30:00Z')).year,2027);
 assert.equal(upcomingPayment(new Date('2026-12-31T23:30:00Z')).month,0);
 assert.equal(upcomingPayment(new Date('2026-12-31T23:30:00Z')).paidMonth,1);
});
test('every procedure document and miniapp icon exists; direct links stay external',async()=>{
 for(const p of procedures)for(const [,f] of p.files||[])assert.ok((await stat('public/assets/tramits/'+f)).size>0);
 for(const id of ['calendaris','tramits','cobraments','rc']){const m=modules.find(m=>m.id===id);assert.ok(m);assert.ok((await stat('public/'+m.icon.replace('./',''))).size>0);}
 assert.ok(procedures.find(p=>p.title==='Excedències').url.includes('sharepoint.com'));
 assert.ok(modules.find(m=>m.id==='risc-percebut').externalUrl);
});
test('RC indexes all 69 articles and includes IRC and glossary',async()=>{
 const data=JSON.parse(await readFile('public/assets/rc/articles.json','utf8'));
 assert.equal(data.totalPages,147);assert.equal(data.nav.length,69);
 for(const [a,b]of [[101,112],[201,206],[301,322],[401,414],[501,515]])for(let i=a;i<=b;i++)assert.ok(data.nav.find(x=>x.number===String(i)),String(i));
 assert.ok(data.pages.some(p=>p.page===147));assert.ok(data.pages.some(p=>p.paragraphs.join(' ').includes('SAFECOM')));
 assert.ok(data.nav.every(a=>a.title!==`Article ${a.number}`));
});
