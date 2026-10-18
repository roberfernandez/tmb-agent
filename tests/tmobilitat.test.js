import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { cases, filterCases, expiryDate, guides, source } from '../src/tmobilitat-data.js';
import { modules } from '../src/modules.js';

test('all source table entries and all substantive pages have accessible cases',()=>{
  assert.equal(cases.filter(c=>c.kind==='table').length,79);
  assert.equal(cases.length,98);
  assert.equal(new Set(cases.map(c=>c.id)).size,cases.length);
  for(let page=2;page<=22;page++)assert.ok(cases.some(c=>c.page===page),`Missing page ${page}`);
  for(const c of cases){assert.ok(c.title&&c.text&&c.type&&c.solution);assert.ok(c.page>=2&&c.page<=22);for(const id of c.related)assert.ok(cases.some(t=>t.id===id),`${c.id}: ${id}`);}
  for(const id of ['p14-2','p14-5'])assert.equal(cases.find(c=>c.id===id).original,cases.find(c=>c.id==='p14-1').original);
  for(const id of ['p14-7','p14-15'])assert.equal(cases.find(c=>c.id===id).original,cases.find(c=>c.id==='p14-6').original);
});
test('search accepts accents, punctuation, order and context and filters by area',()=>{
  assert.ok(filterCases({query:'mobil valida'}).some(c=>c.id==='circuit-mobil'));
  assert.ok(filterCases({query:'sol suport'}).some(c=>c.id==='p15-3'));
  assert.ok(filterCases({query:'30 dies',category:'bescanvis'}).some(c=>c.id==='p20-3'));
  assert.ok(filterCases({category:'contactes'}).every(c=>c.category==='contactes'));
  assert.equal(filterCases({query:'xyz-no-existeix'}).length,0);
});
test('expiry counts calendar days inclusively across leap years and daylight-saving changes',()=>{
  assert.equal(expiryDate('2026-10-17'), '2026-11-15');
  assert.equal(expiryDate('2024-02-01'), '2024-03-01');
  assert.equal(expiryDate('2026-03-15'), '2026-04-13');
  assert.equal(expiryDate('2026-12-20'), '2027-01-18');
  assert.equal(expiryDate('2026-02-30'),null);assert.equal(expiryDate(''),null);
  assert.equal(cases.filter(c=>c.calculator).length,1);
  assert.equal(cases.find(c=>c.calculator).id,'caducitat-provisional');
});
test('guided paths keep individual mobile eligibility and validated purchases distinct',()=>{
  const m=guides.mobil.nodes;
  assert.equal(m.pas.options[1][1],'global');
  assert.equal(m.global.options[1][1],'valid');
  assert.equal(m.valid.options[1][1],'cap');assert.match(m.cap.result,/No donar bescanvi/);
  assert.match(m['bescanvi-global'].result,/404/);assert.match(m.bescanvi.result,/número de suport/);
  assert.equal(guides.compra.nodes.validat.options[0][1],'qrs');assert.match(guides.compra.nodes.qrs.result,/No donar bescanvi/);
  for(const g of Object.values(guides)){
    const seen=new Set();const visit=id=>{assert.ok(g.nodes[id]);if(seen.has(id))return;seen.add(id);for(const [,to] of g.nodes[id].options||[])visit(to);};visit(g.start);assert.equal(seen.size,Object.keys(g.nodes).length);
  }
});
test('corporate Consultes remains available within internal module and shell cache',async()=>{
  const module=modules.find(m=>m.id==='t-mobilitat');assert.equal(module.name,'T-mobilitat');assert.equal(module.externalUrl,undefined);
  assert.equal(new URL(source.consultes).hostname,'aplicacions.tmb.cat');
  const sw=await readFile(new URL('../public/sw.js',import.meta.url),'utf8');
  for(const name of ['tmobilitat.js?v=image-gestures-v1','tmobilitat-data.js','tmobilitat-cases.js','tmobilitat.css?v=image-gestures-v1'])assert.ok(sw.includes(name));
});
