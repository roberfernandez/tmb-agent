import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { documentBlocks } from '../src/convenis-format.js';
import { openConvenisReader, documentMatches } from '../src/convenis-reader.js';

const docs=JSON.parse(readFileSync(new URL('../public/assets/convenis/index.json',import.meta.url),'utf8'));
test('layout preserves every character and page of all 16 documents',()=>{
  for(const doc of docs) for(const page of doc.pages){
    const blocks=documentBlocks(page.text);
    assert.equal(blocks.map(b=>page.text.slice(b.start,b.end)).join(''),page.text,doc.id);
    assert.ok(blocks.every(b=>b.start<b.end));
  }
});
test('real current agreement separates complete article titles and body',()=>{
  const page=docs.find(d=>d.id==='c2023').pages[0];
  const blocks=documentBlocks(page.text).map(b=>({...b,text:page.text.slice(b.start,b.end).trim()}));
  assert.ok(blocks.some(b=>b.type==='chapter' && b.text==='Capítol 1 Disposicions Generals'));
  assert.ok(blocks.some(b=>b.type==='heading' && b.text==='Article 1. Àmbit Personal'));
  assert.ok(blocks.some(b=>b.type==='paragraph' && b.text.startsWith('Les normes contingudes')));
});
test('clauses, lists, paragraphs and amounts retain their source meaning',()=>{
  const source='PRIMERO.- Les parts acorden: a) Primer punt. b) Segon punt. 1.2.-Apartat amb 1.234,56 euros i data 01.01.2024.';
  const blocks=documentBlocks(source);
  assert.equal(blocks.filter(b=>b.type==='item').length,3);
  assert.equal(blocks.map(b=>source.slice(b.start,b.end)).join(''),source);
  assert.deepEqual(documentBlocks(''),[]);
});

class Element {
  constructor(tag,text=''){this.tag=tag;this.value='';this.children=[];this.events={};this.attributes={};this._text=text;this.classes=new Set();this.classList={add:c=>this.classes.add(c),remove:c=>this.classes.delete(c)};}
  append(...nodes){for(const n of nodes){this.children.push(n);n.parent=this;}}
  replaceChildren(...nodes){this.children=[];this._text='';this.append(...nodes);}
  set textContent(v){this.children=[];this._text=v;}
  get textContent(){return this._text+this.children.map(n=>n.textContent).join('');}
  setAttribute(k,v){this.attributes[k]=v;}
  addEventListener(k,fn){this.events[k]=fn;}
  remove(){if(this.parent)this.parent.children=this.parent.children.filter(n=>n!==this);}
  showModal(){}
  scrollIntoView(){this.scrolled=true;}
  focus(){}
}
const all=e=>[e,...e.children.flatMap(all)];
test('search spans formatted blocks, counts once, navigates and clears without losing formatting',()=>{
  const original=globalThis.document;
  const body=new Element('body');
  globalThis.document={body,createElement:t=>new Element(t),createTextNode:t=>new Element('#text',t)};
  try{
    const source='Article 1. Àmbit Personal Les normes regulen la jubilació parcial. Article 2. Àmbit Temporal La jubilació parcial continua.';
    openConvenisReader({title:'Prova',url:'https://example.org/original.pdf',pages:[{page:1,text:source}]});
    const nodes=all(body),input=nodes.find(n=>n.tag==='input'),output=nodes.find(n=>n.tag==='output');
    const content=nodes.find(n=>n.className==='cv-reader-text');
    assert.equal(content.textContent,source);
    const headings=()=>all(content).filter(n=>n.tag==='h5').length;
    assert.equal(headings(),2);
    input.value='Personal Les normes'; input.events.input();
    assert.equal(output.textContent,'1 de 1');
    assert.equal(all(content).filter(n=>n.tag==='mark').length,2);
    assert.equal(all(content).filter(n=>n.classes.has('cv-reader-current')).length,2);
    input.value='jubilacio parcial';input.events.input();
    assert.equal(output.textContent,'1 de 2');
    nodes.find(n=>n.attributes['aria-label']==='Coincidència següent').onclick();
    assert.equal(output.textContent,'2 de 2');
    nodes.find(n=>n.attributes['aria-label']==='Coincidència següent').onclick();
    assert.equal(output.textContent,'1 de 2');
    input.value='<script>';input.events.input();
    assert.equal(output.textContent,'Cap coincidència');
    nodes.find(n=>n.textContent==='Neteja').onclick();
    assert.equal(content.textContent,source);
    assert.equal(headings(),2);
    assert.equal(all(content).filter(n=>n.tag==='mark').length,0);
    assert.equal(nodes.find(n=>n.tag==='a').href,'https://example.org/original.pdf');
  }finally{globalThis.document=original;}
});

test('plan headings and embedded PDF bullets are recovered without turning page numbers into headings',()=>{
  const plan=docs.find(d=>d.id==='igualtat2022');
  for(const [index,title] of [[0,'1. Introducció'],[1,'2. Metodologia'],[2,'3. Glossari de termes']]){
    const text=plan.pages[index].text;
    assert.ok(documentBlocks(text).some(b=>b.type==='heading' && text.slice(b.start,b.end).trim()===title),title);
  }
  const current=docs.find(d=>d.id==='c2023');
  assert.ok(documentBlocks(current.pages[1].text).filter(b=>b.type==='item').length>=5);
  for(const page of current.pages) for(const b of documentBlocks(page.text)){
    if(b.type==='heading') assert.ok(!page.text.slice(b.start,b.end).startsWith('32 '));
  }
});
