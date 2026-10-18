import test from 'node:test';
import assert from 'node:assert/strict';
import { documentMatches } from '../src/convenis-reader.js';
test('document search preserves exact source ranges across accents and line breaks',()=>{
 const text='Jubilació parcial. JUBILACIO\n parcial. jubilacio\u0301 parcial';
 const ranges=documentMatches(text,'jubilacio parcial');
 assert.equal(ranges.length,3);
 assert.deepEqual(ranges.map(([a,b])=>text.slice(a,b)),['Jubilació parcial','JUBILACIO\n parcial','jubilacio\u0301 parcial']);
 assert.deepEqual(documentMatches(text,'  '),[]);
 assert.deepEqual(documentMatches('a+b a.b','a+b'),[[0,3]]);
 assert.deepEqual(documentMatches('vacances vacances','vacances'),[[0,8],[9,17]]);
});
