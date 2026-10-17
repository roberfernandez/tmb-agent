import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {filterPermits,permits,sources} from '../src/permisos-data.js';
test('search accepts accents, multiple words and combined filters',()=>{
  assert.deepEqual(filterPermits({query:'sedacio'}).map(p=>p.id),['sedacion']);
  assert.deepEqual(filterPermits({query:'repos cirurgia',category:'Salut',relation:'conviviente'}).map(p=>p.id),['cirugia-reposo']);
  assert.equal(filterPermits({category:'Estudis',pay:'Retribuït'}).length,0);
});
test('territorial rules are limited to relevant cards and third degree is not silently second degree',()=>{
  assert.deepEqual(filterPermits({travel:'yes'}).map(p=>p.id),['boda-familiar','fallecimiento','hospital']);
  assert.equal(filterPermits({relation:'tercer-grado'}).length,0);
  assert.equal(filterPermits({relation:'madre-padre',query:'hospitalitzacio'}).some(p=>p.id==='hospital'),true);
});
test('all conditions carry provenance, documentation and explicit unresolved scope',()=>{
  assert.equal(permits.length,21);
  assert.equal(new Set(permits.map(p=>p.id)).size,21);
  for(const p of permits) {
    assert.ok(p.reference.duration && p.reference.documents && p.reference.notes);
    if(p.verified) assert.ok(p.refs.length);
    for(const ref of p.refs) { assert.ok(sources[ref].label); if(sources[ref].url) assert.ok(sources[ref].url.startsWith('https://')); }
  }
});
test('recent birth rules, historical provenance and unresolved disputes remain distinct',()=>{
  const get=id=>permits.find(p=>p.id===id);
  assert.match(get('nacimiento').reference.duration,/19 setmanes/);
  assert.ok(get('domicilio').refs.includes('anterior'));
  assert.ok(get('hospital').controversy);
  assert.ok(get('cirugia-reposo').controversy);
  assert.equal(get('cirugia-reposo').reference.duration,'5 dies laborables.');
  assert.ok(get('urgencias').refs.includes('conveni2016'));
  assert.ok(filterPermits({relation:'tutela'}).some(p=>p.id==='oncologia'));
  assert.doesNotMatch(JSON.stringify(permits),/cartel/i);
});
test('route remains inside approved launcher and service worker includes dependencies',async()=>{
  const main=await readFile(new URL('../src/main.js',import.meta.url),'utf8');
  assert.match(main,/module\?\.id === 'permisos'/);
  assert.ok(main.indexOf("if (state !== 'approved')")<main.indexOf('render();',main.indexOf('async function authorizedRender')));
  const sw=await readFile(new URL('../public/sw.js',import.meta.url),'utf8');
  for(const f of ['src/permisos.js?v=permisos-v3','src/permisos-data.js','src/permisos.css?v=permisos-v1']) assert.ok(sw.includes(f));
});
