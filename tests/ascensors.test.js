import test from 'node:test';
import assert from 'node:assert/strict';
import {stations,procedures,stationIndex,visibleFloors,procedureLocation} from '../src/ascensors-data.js';
test('confirmed locations distinguish exterior from platforms only at the three confirmed stations',()=>{
 for(const code of [428,429,433]){assert.equal(procedureLocation(code,'EE2'),'Exterior');assert.equal(procedureLocation(code,'EE'),'Andanes');}
 assert.equal(procedureLocation(431,'EE2'),'');
});
test('L4 keeps every station and only documented procedures',()=>{
 assert.deepEqual(stations.map(s=>s[0]),Array.from({length:22},(_,i)=>413+i));
 for(const [,,models] of stations)for(const m of models){assert.ok(procedures[m]);const u=new URL(procedures[m].url);assert.equal(u.host,'tmbbcn.sharepoint.com');assert.ok(u.pathname.endsWith('.pdf'));}
 assert.deepEqual(stations[stationIndex(424)][2],[]);
 assert.deepEqual(stations[stationIndex(427)][2],[]);
});
test('distinct procedures remain available instead of being merged by drive type',()=>{
 for(const code of [428,429,433])assert.deepEqual(stations[stationIndex(code)][2],['EE2','EE']);
 assert.deepEqual(stations[stationIndex(431)][2],['EE2','EHM','EE7']);
 assert.equal(new Set(Object.values(procedures).map(p=>p.url)).size,6);
});
test('five floors stay visible around selected station including endpoints',()=>{
 for(let i=0;i<22;i++){const {low,high}=visibleFloors(i);assert.equal(high-low,4);assert.ok(low>=0&&high<22&&i>=low&&i<=high);}
 assert.equal(stationIndex('broken'),0);assert.equal(stationIndex('434'),21);
});
