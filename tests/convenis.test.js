import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {matchingPages, normalize, excerpt, pageUrl, topics} from '../src/convenis-search.js';
const docs=JSON.parse(await readFile(new URL('../public/assets/convenis/index.json',import.meta.url),'utf8'));
test('convenis search matches accents and topic translations, keeps PDF page provenance',()=>{
 const current=docs.find(d=>d.id==='c2023');
 assert.ok(matchingPages(current,'jornada').length>0);
 const vacations=matchingPages(current,topics.find(([name])=>name==='Vacances')[1]);
 assert.ok(vacations.length>0);assert.ok(vacations.every(p=>Number.isInteger(p.page)&&p.page>0));
 assert.equal(normalize('Jubilació PARCIAL'),'jubilacio parcial');
 assert.equal(matchingPages(current,'zzzinexistentzzz').length,0);
 assert.ok(pageUrl(current,vacations[0].page).endsWith('#page='+vacations[0].page));
});
test('scanned PDFs cannot produce invented search results and historical index is limited to FMB',()=>{
 const scanned=docs.filter(d=>!d.pages.length);assert.equal(scanned.length,6);
 for(const d of scanned)assert.deepEqual(matchingPages(d,'jornada'),[]);
 const inventory=docs.find(d=>d.kind==='Inventari');assert.deepEqual(inventory.pages.map(p=>p.page),[34,35,36,37,38]);
 assert.equal(docs.length,16);assert.equal(new Set(docs.map(d=>d.id)).size,docs.length);
 for(const d of docs){assert.equal(new URL(d.url).protocol,'https:');for(const p of d.pages)assert.ok(p.page<=d.pageCount);}
});
test('empty and punctuation-only queries yield no results; excerpts remain verbatim substrings',()=>{
 const d={pages:[{page:1,text:'La jornada anual es regula en aquest article.'}]};
 assert.deepEqual(matchingPages(d,''),[]);assert.deepEqual(matchingPages(d,'!!!'),[]);
 assert.ok(excerpt(d.pages[0].text,'jornada').includes('La jornada anual'));
});
