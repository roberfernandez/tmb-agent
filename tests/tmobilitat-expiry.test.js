import test from 'node:test';
import assert from 'node:assert/strict';
import { expiryTitles, calculateExpiry, plasticTitleIds } from '../src/tmobilitat-expiry.js';
import { readFile } from 'node:fs/promises';
test('five titles share the green plastic support with a label; no magnetic/carton substitute',async()=>{
  assert.deepEqual(plasticTitleIds,['t-usual','t-jove','t-familiar','t-grup','t-verda']);
  assert.ok((await readFile(new URL('../public/assets/tmobilitat/plastic-verd.webp',import.meta.url))).length>0);
});
test('the original eight titles are available with distinct validity and provisional rules',()=>{
  assert.equal(expiryTitles.length,8);assert.equal(new Set(expiryTitles.map(t=>t.id)).size,8);
  for(const [id,date] of [['t-usual','2026-11-15'],['t-familiar','2026-11-15'],['t-grup','2026-11-15'],['t-jove','2027-01-14']])assert.equal(calculateExpiry({id,mode:'original',start:'2026-10-17'}).date,date);
  for(const id of ['rosa','acompanyant'])assert.equal(calculateExpiry({id,mode:'provisional',start:'2026-10-17',originalEnd:'2020-01-01'}).date,'2026-11-15');
});
test('T-16 and T-verda provisionals require and respect original expiry',()=>{
  for(const id of ['t-16','t-verda']){
    assert.ok(calculateExpiry({id,mode:'provisional',start:'2026-10-17'}).error);
    assert.deepEqual(calculateExpiry({id,mode:'provisional',start:'2026-10-17',originalEnd:'2026-10-25'}),{date:'2026-10-25',limited:true,kind:'provisional'});
    assert.equal(calculateExpiry({id,mode:'provisional',start:'2026-10-17',originalEnd:'2027-01-01'}).date,'2026-11-15');
    assert.ok(calculateExpiry({id,mode:'provisional',start:'2026-10-17',originalEnd:'2026-10-16'}).error);
  }
});
test('original social titles use printed expiry; a provisional does not renew an existing pass',()=>{
  for(const id of ['rosa','acompanyant','t-16','t-verda']){
    assert.ok(calculateExpiry({id,mode:'original',start:'2026-10-17'}).error);
    assert.equal(calculateExpiry({id,mode:'original',originalEnd:'2027-02-01'}).date,'2027-02-01');
  }
  assert.equal(calculateExpiry({id:'t-jove',mode:'provisional',start:'2026-10-17',originalEnd:'2026-10-20'}).date,'2026-10-20');
  assert.equal(calculateExpiry({id:'t-usual',mode:'original',start:'2026-10-17',tariffEnd:'2026-10-31'}).date,'2026-10-31');
});
test('invalid dates and unsupported modes do not yield expiry dates',()=>{
  for(const args of [{id:'t-16',mode:'original',originalEnd:'2026-02-30'},{id:'t-usual',mode:'original',start:'2026-02-30'},{id:'fake',mode:'provisional',start:'2026-10-17'},{id:'t-jove',mode:'wrong',start:'2026-10-17'}])assert.ok(calculateExpiry(args).error);
});
