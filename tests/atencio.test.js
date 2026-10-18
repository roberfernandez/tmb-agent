import test from 'node:test';
import assert from 'node:assert/strict';
import {articles,items,searchArticles,matchingParagraphs,highlightedParts} from '../src/atencio-data.js';
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


test('bicicletes prioritizes its annex and exposes matching paragraphs with exact highlights',()=>{
 const matches=searchArticles('bicicletes');
 assert.equal(matches[0].id,'bicicletes');
 assert.deepEqual(new Set(matches.map(a=>a.id)),new Set(['bicicletes','4','11','12']));
 const paragraphs=matchingParagraphs(matches[0],'bicicletes');
 assert.ok(paragraphs[0].includes('20.30'));
 assert.ok(paragraphs[0].includes('juliol i agost'));
 assert.ok(paragraphs.every(p=>p.toLowerCase().includes('bicicletes')));
 const parts=highlightedParts(paragraphs[0],'BICICLETES');
 assert.equal(parts.map(p=>p.text).join(''),paragraphs[0]);
 assert.ok(parts.some(p=>p.match&&p.text.toLowerCase()==='bicicletes'));
});
test('accent insensitive highlights preserve source text and empty search keeps article order',()=>{
 const text='Els gossos han de dur morrió i anar lligats.';
 const parts=highlightedParts(text,'morrio');
 assert.equal(parts.filter(p=>p.match)[0].text,'morrió');
 assert.equal(parts.map(p=>p.text).join(''),text);
 assert.deepEqual(searchArticles('').map(a=>a.id),articles.map(a=>a.id));
 assert.deepEqual(highlightedParts('<text>','zzzz'),[{text:'<text>',match:false}]);
});
