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
test('OCR covers six complete scans without renumbering blank pages; inventory remains FMB only',()=>{
 const scanned=docs.filter(d=>d.ocr);assert.equal(scanned.length,6);
 assert.equal(scanned.reduce((n,d)=>n+d.ocr.processedPages,0),93);
 assert.equal(scanned.reduce((n,d)=>n+d.pages.length,0),91);
 for(const d of scanned){assert.ok(d.pages.every(p=>p.ocr));assert.equal(d.ocr.languages.join('+'),'spa+cat');}
 assert.deepEqual(docs.find(d=>d.id==='p2013').pages.map(p=>p.page),[1,2,3]);
 assert.deepEqual(docs.find(d=>d.id==='p2014').pages.map(p=>p.page),[1,2]);
 const inventory=docs.find(d=>d.kind==='Inventari');assert.deepEqual(inventory.pages.map(p=>p.page),[34,35,36,37,38]);
 assert.equal(docs.length,16);assert.equal(new Set(docs.map(d=>d.id)).size,docs.length);
 for(const d of docs){assert.equal(new URL(d.url).protocol,'https:');for(const p of d.pages)assert.ok(p.page<=d.pageCount);}
});
test('empty and punctuation-only queries yield no results; excerpts remain verbatim substrings',()=>{
 const d={pages:[{page:1,text:'La jornada anual es regula en aquest article.'}]};
 assert.deepEqual(matchingPages(d,''),[]);assert.deepEqual(matchingPages(d,'!!!'),[]);
 assert.ok(excerpt(d.pages[0].text,'jornada').includes('La jornada anual'));
});

test('all CCOO documents open bundled unchanged PDFs, including search page links',async()=>{
 for(const id of ['c1997','c1993','c1985']){
  const d=docs.find(d=>d.id===id);
  assert.ok(d.fileUrl.startsWith('./assets/convenis/'));
  const bytes=await readFile(new URL('../public/'+d.fileUrl.slice(2),import.meta.url));
  assert.equal(bytes.subarray(0,5).toString(),'%PDF-');
  assert.ok(pageUrl(d,2).includes('/tmb-agent/assets/convenis/'));
  assert.ok(pageUrl(d,2).endsWith('#page=2'));
  assert.equal(new URL(d.url).hostname,'ccoo.app');
 }
});


test('previously scanned agreements now return real OCR page matches and retain source links',()=>{
 for(const [id,term] of [['c1999','vacaciones'],['c2002','retribuciones'],['c2008','incrementos'],['p2012','prorroga'],['p2013','vacaciones'],['p2014','prorroga']]){
  const doc=docs.find(d=>d.id===id),matches=matchingPages(doc,term);
  assert.ok(matches.length>0,id);assert.ok(matches.every(p=>p.ocr));
  assert.ok(pageUrl(doc,matches[0].page).endsWith('#page='+matches[0].page));
 }
 assert.equal(docs.find(d=>d.id==='p2013').kind,'Acord');
 assert.equal(docs.find(d=>d.id==='c2023').ocr,undefined);
 assert.equal(docs.find(d=>d.id==='p2012').pages.find(p=>p.page===9).sectionLabel,'Text annex: XXV · 2008–2011');
});
test('excerpt stays near matching text after long punctuation sequences',()=>{
 const text='x '.repeat(400)+'---- '.repeat(400)+'vacaciones pactadas';
 assert.ok(excerpt(text,'vacaciones').includes('vacaciones pactadas'));
});
