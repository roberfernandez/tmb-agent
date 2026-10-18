import test from 'node:test';
import assert from 'node:assert/strict';
import {articles,items,searchArticles} from '../src/atencio-data.js';
import {readFileSync,existsSync} from 'node:fs';
test('reglament includes complete official sections and full-text accent-insensitive search',()=>{
 assert.equal(articles.length,20);
 assert.deepEqual(articles.slice(0,17).map(a=>a.id),Array.from({length:17},(_,i)=>String(i+1)));
 assert.ok(searchArticles('morrio').some(a=>a.id==='animals'));
 assert.ok(searchArticles('patinets').some(a=>a.id==='12'));
 assert.ok(articles.every(a=>a.paragraphs.length && !a.paragraphs.join('').includes('Segueix les xarxes socials')));
 assert.deepEqual(searchArticles('zzzz-no-result'),[]);
});
test('public passenger destinations and all assets are bundled for offline use',()=>{
 const sw=readFileSync(new URL('../public/sw.js',import.meta.url),'utf8');
 for(const item of items){assert.ok(['dogc.gencat.cat','www.tmb.cat'].includes(new URL(item.url).hostname));for(const path of [item.image,item.qr]){assert.ok(existsSync(new URL('../public/'+path.slice(2),import.meta.url)));assert.ok(sw.includes(path.slice(2)));}}
});
