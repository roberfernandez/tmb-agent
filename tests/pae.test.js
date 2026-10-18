import test from 'node:test';
import assert from 'node:assert/strict';
import {stations,vestibuleLabel} from '../src/pae-data.js';
test('PAE preserves L4 order, 38 links including user-confirmed persiana assignments, and one unavailable access',()=>{
 assert.deepEqual(stations.map(s=>s[0]),Array.from({length:22},(_,i)=>413+i));
 const accesses=stations.flatMap(s=>s[2]);assert.equal(accesses.length,39);assert.equal(accesses.filter(a=>a.url).length,38);
 for(const a of accesses.filter(a=>a.url)){const u=new URL(a.url);assert.equal(u.host,'tmbbcn.sharepoint.com');assert.ok(u.pathname.startsWith('/sites/SEEpublic/SEEDocuments/PAES/'));assert.ok(u.pathname.endsWith('.pdf'));}
 assert.deepEqual(stations.flatMap(s=>s[2].filter(a=>!a.url).map(a=>[s[0],a.name])),[[431,'Ronda Guinardó']]);
 assert.equal(stations[0][2][0].url,stations[1][2][0].url);
 assert.equal(stations[1][2][1].url,stations[1][2][0].url);
 for(const i of [0,1])assert.equal(vestibuleLabel(414,2,i),'');
 assert.equal(vestibuleLabel(415,2,0),'V.0 · ');assert.equal(vestibuleLabel(415,2,1),'V.1 · ');
 assert.equal(vestibuleLabel(416,3,0),'');
 assert.equal(stations.find(s=>s[0]===415)[2][0].name,'Ferrer Bassa');
 assert.equal(stations.find(s=>s[0]===415)[2][1].name,'Jaume Fabré');
});
