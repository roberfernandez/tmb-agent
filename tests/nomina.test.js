import test from 'node:test';
import assert from 'node:assert/strict';
import {modules} from '../src/modules.js';
import {readFile} from 'node:fs/promises';
test('Nòmina is linked with its real icon; original destinations and first cards remain',async()=>{
  assert.deepEqual(modules.slice(0,2).map(m=>m.id),['computo','incidencias']);
  const n=modules.find(m=>m.id==='nomina');
  assert.equal(n.appUrl,'https://roberfernandez.github.io/nomina/');
  assert.ok((await readFile(new URL('../public/'+n.icon,import.meta.url))).length>0);
  assert.equal(modules.length,10);
  assert.ok(modules.some(m=>m.id==='cquadre'));
  assert.equal(modules.find(m=>m.id==='cinta-metrica').appUrl,'https://roberfernandez.github.io/cinta-metrica/');
  for(const [id,path] of [['computo','computo-aac'],['incidencias','incidencias-l4'],['dea','dea-codi-l4'],['bobines','bobines']])assert.equal(modules.find(m=>m.id===id).appUrl,`https://roberfernandez.github.io/${path}/`);
  assert.equal(modules.find(m=>m.id==='miralin').externalUrl,'https://aplicacions.tmb.cat/miralin/routes/4/stops/413?dswid=4465');
  assert.equal(modules.find(m=>m.id==='t-mobilitat').externalUrl,'https://aplicacions.tmb.cat/consulta-tmobilitat/index?dswid=-2952');
});
