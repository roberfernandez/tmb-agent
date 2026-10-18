import test from 'node:test';
import assert from 'node:assert/strict';
import {stations} from '../src/dossiers-data.js';
test('22 L4 dossiers map each station to its own observed PDF without zone selection',()=>{
 assert.deepEqual(stations.map(s=>s[0]),Array.from({length:22},(_,i)=>413+i));
 assert.equal(new Set(stations.map(s=>s[2])).size,22);
 for(const [code,name,url] of stations){const u=new URL(url);assert.equal(u.host,'tmbbcn.sharepoint.com');const path=decodeURIComponent(u.pathname);assert.ok(path.includes(`/Planos L04/${code} `));assert.ok(path.split('/').at(-1).startsWith(code+'_2025'));assert.ok(path.endsWith('.pdf'));assert.ok(name);}
 assert.ok(decodeURIComponent(stations[0][2]).includes('emergencia  la Pau.pdf'));
 assert.ok(decodeURIComponent(stations[13][2]).includes('emegencia Girona.pdf'));
});
