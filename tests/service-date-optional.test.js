import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';

const page=readFileSync(new URL('../public/proves/servei/index.html', import.meta.url),'utf8');
const start=page.indexOf('function localServiceDate(parsed, instant=');
const end=page.indexOf('\nlet state=',start);
assert.ok(start>=0&&end>start,'Automatic local operational date helper exists');
const ctx={Date};
vm.runInContext(page.slice(start,end),vm.createContext(ctx));
const rows=[{a:1126,b:1146},{a:1146,b:1226},{a:1226,b:1298},{a:1298,b:1409},{a:1409,b:1469},{a:1469,b:1490}];

test('a screenshot requires no visible date field and uses its original clock times',()=>{
 assert.doesNotMatch(page, /<input type="date" id="date">/);
 assert.doesNotMatch(page, /Tria una data\./);
 assert.match(page,/state.date=localServiceDate\(parsed\)/);
 assert.match(page,/const operational=localServiceDate\(rows\)/);
});

test('old evening shift is anchored to current local date regardless of screenshot date',()=>{
 assert.equal(ctx.localServiceDate(rows,new Date(2026,10,19,19,0)),'2026-11-19');
 assert.equal(ctx.localServiceDate(rows,new Date(2026,10,19,23,35)),'2026-11-19');
});

test('an ongoing shift after midnight still belongs to the previous operational day',()=>{
 assert.equal(ctx.localServiceDate(rows,new Date(2026,10,20,0,15)),'2026-11-19');
 assert.equal(ctx.localServiceDate(rows,new Date(2026,10,20,0,49)),'2026-11-19');
 assert.equal(ctx.localServiceDate(rows,new Date(2026,10,20,0,55)),'2026-11-20');
});

test('a normal daytime shift does not erroneously become yesterday',()=>{
 const day=[{a:420,b:480},{a:480,b:560}];
 assert.equal(ctx.localServiceDate(day,new Date(2026,10,20,0,15)),'2026-11-20');
 assert.equal(ctx.localServiceDate(day,new Date(2026,10,20,10,0)),'2026-11-20');
});

test('an out-of-hours native preview is labeled, not shown as actual late pickup',()=>{
 assert.match(page,/liveDuty\(now\)\?\.preview/);
 assert.match(page,/PROVA FORA D’HORARI/);
 assert.match(page,/preview:status.preview===true/);
});
