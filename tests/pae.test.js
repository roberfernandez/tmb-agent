import test from 'node:test';
import assert from 'node:assert/strict';
import {stations} from '../src/pae-data.js';
test('PAE preserves L4 station and access order, with 36 official PDF links and three unavailable accesses',()=>{
 assert.deepEqual(stations.map(s=>s[0]),Array.from({length:22},(_,i)=>413+i));
 const accesses=stations.flatMap(s=>s[2]);assert.equal(accesses.length,39);assert.equal(accesses.filter(a=>a.url).length,36);
 for(const a of accesses.filter(a=>a.url)){const u=new URL(a.url);assert.equal(u.host,'tmbbcn.sharepoint.com');assert.ok(u.pathname.startsWith('/sites/SEEpublic/SEEDocuments/PAES/'));assert.ok(u.pathname.endsWith('.pdf'));}
 assert.deepEqual(stations.flatMap(s=>s[2].filter(a=>!a.url).map(a=>[s[0],a.name])),[[413,'Rambla Guipúscoa / Ca n’Oliva'],[414,'TRAM Besòs'],[431,'Ronda Guinardó']]);
 assert.equal(stations.find(s=>s[0]===415)[2][0].name,'Ferrer Bassa');
 assert.equal(stations.find(s=>s[0]===415)[2][1].name,'Jaume Fabré');
});
