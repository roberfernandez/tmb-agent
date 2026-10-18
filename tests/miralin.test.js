import { test } from 'node:test';
import assert from 'node:assert/strict';
import { stations, readOrigin, saveOrigin, originUrl } from '../src/miralin.js';
const storage = () => { const values = new Map(); return {getItem:k=>values.get(k)??null,setItem:(k,v)=>values.set(k,v)}; };
test('each device remembers only its selected base and builds the corresponding Miralín URL', () => {
  const a=storage(), b=storage(); assert.equal(readOrigin(a),'');
  for (const [id] of stations) { assert.equal(saveOrigin(id,a),true); assert.equal(readOrigin(a),id); assert.equal(originUrl(id),`https://aplicacions.tmb.cat/miralin/routes/4/stops/${id}?dswid=4465`); }
  assert.equal(readOrigin(b),''); assert.throws(()=>saveOrigin('999',a)); assert.equal(readOrigin(a),'434');
});
test('unavailable browser storage does not pretend the choice was saved', () => {
 const denied={getItem(){throw Error();},setItem(){throw Error();}};
 assert.equal(readOrigin(denied),''); assert.equal(saveOrigin('413',denied),false);
});
