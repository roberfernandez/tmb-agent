import { topics, normalize, matchingPages, excerpt, pageUrl } from './convenis-search.js';
const KEY = 'tmb-agent-convenis-v1';
const sources = [
  ['TMB · Acords laborals', 'https://transparencia.tmb.cat/ca/organitzacio-i-empleats/recursos-humans/acords-funcionarials-laborals-o-sindicals'],
  ['CGT · Convenis', 'https://cgtmetro.org/convenios/'],
  ['CCOO · Convenis', 'https://ccoo.app/metro-barcelona/convenis/'],
  ['CIM · Arxiu documental', 'https://cimmetro.taplink.ws/p/dca7b0/'],
  ['UGT · Convenis', 'https://www.fesmcugt.cat/convenis-col%C2%B7lectius/transports/carreteres-i-urbans/'],
  ['Solidaritat Obrera · Metro Barcelona', 'https://www.solidaridadobrera.org/confederal/category/metro-de-barcelona/'],
];
export function renderConvenis() {
  const root = document.createElement('section'); root.className = 'cv'; root.lang = 'ca';
  let state = { tab:'documents', query:'', kind:'', left:'c2016', right:'c2023', limit:20 };
  try { const old = JSON.parse(sessionStorage.getItem(KEY)); if(old && typeof old === 'object') state = {...state,...old}; } catch {}
  if(state.kind==='Desats')state.kind='';
  delete state.saved;
  let docs = [];
  const el = (tag, cls, text) => { const e = document.createElement(tag); if(cls)e.className=cls; if(text)e.textContent=text; return e; };
  const button = (text, fn, cls='cv-button') => { const b=el('button',cls,text); b.type='button'; b.onclick=fn; return b; };
  const link = (text, url, cls='cv-link') => {const a=el('a',cls,text); a.href=url; a.target='_blank'; a.rel='noopener noreferrer'; return a;};
  const save = () => {try{sessionStorage.setItem(KEY,JSON.stringify(state));}catch{}};
  const expandedQuery = () => topics.find(([label])=>normalize(label)===normalize(state.query))?.[1] || state.query;
  const heading=el('header','cv-heading'), icon=el('img'); icon.src='./assets/icons/convenis-256.webp';icon.alt='';icon.width=80;icon.height=80;
  const title=el('div');title.append(el('h1','','Convenis'),el('p','','Troba el document. Compara el text.'));heading.append(icon,title);
  const tabs=el('nav','cv-tabs');tabs.setAttribute('aria-label','Apartats de Convenis');
  const body=el('div','cv-body');root.append(heading,tabs,body);
  function snippet(doc,page){
    const box=el('article','cv-fragment');box.append(el('p','cv-meta',`${doc.source} · pàgina ${page.page}`),el('p','cv-quote',excerpt(page.text,expandedQuery())),link(`Obre el PDF · pàg. ${page.page} ↗`,pageUrl(doc,page.page)));
    return box;
  }
  function docCard(doc){
    const card=el('article','cv-card'), top=el('div','cv-card-top'); top.append(el('span','cv-tag',doc.kind));
    card.append(top,el('h2','',doc.title),el('p','cv-meta',`${doc.source}${doc.pageCount?' · '+doc.pageCount+' pàgines':''}`));
    if(doc.note)card.append(el('p','cv-note',doc.note));
    if(!doc.pages.length)card.append(el('p','cv-note','PDF escanejat: cerca de text no disponible.'));
    card.append(link('Consulta el document ↗',doc.fileUrl || doc.url));if(doc.fileUrl)card.append(link('Font original · '+doc.source+' ↗',doc.url,'cv-source-original'));return card;
  }
  function searchBox(){
    const form=el('form','cv-search'),label=el('label','','Paraula o expressió');label.htmlFor='cv-query';
    const row=el('div','cv-input-row'),input=el('input');input.id='cv-query';input.type='search';input.value=state.query;input.placeholder='Ex.: jornada, vacances, excedència';input.maxLength=150;
    input.oninput=()=>{state.query=input.value;save();};
    const submit=el('button','cv-button','Cerca');submit.type='submit';row.append(input,submit);form.append(label,row);
    form.onsubmit=e=>{e.preventDefault();state.query=input.value.trim();state.limit=20;save();draw();};
    const chips=el('div','cv-topics');for(const [label] of topics)chips.append(button(label,()=>{state.query=label;state.limit=20;save();draw();},'cv-chip'));
    form.append(chips);body.append(form);
  }
  function draw(){
    tabs.replaceChildren();body.replaceChildren();
    for(const [id,label] of [['documents','Documents'],['cerca','On està escrit?'],['compara','Compara'],['cronologia','Cronologia'],['fonts','Fonts']]){
      const b=button(label,()=>{state.tab=id;state.limit=20;save();draw();},'cv-tab');b.setAttribute('aria-current',state.tab===id?'page':'false');tabs.append(b);
    }
    if(state.tab==='documents'){
      body.append(el('p','cv-intro','Convenis, pròrrogues i acords de Metro Barcelona (FMB).'));
      const label=el('label','cv-filter','Tipus de document '), select=el('select');
      for(const kind of ['',...new Set(docs.map(d=>d.kind))]){const o=el('option','',kind||'Tots');o.value=kind;select.append(o);}select.value=state.kind;
      select.onchange=()=>{state.kind=select.value;save();draw();};label.append(select);body.append(label);
      const grid=el('div','cv-grid');const filtered=docs.filter(d=>!state.kind||d.kind===state.kind).sort((a,b)=>Number(b.id==='c2023')-Number(a.id==='c2023'));
      for(const doc of filtered)grid.append(docCard(doc));body.append(grid);if(!filtered.length)body.append(el('p','cv-empty','No hi ha documents d’aquest tipus.'));
    }else if(state.tab==='cerca'){
      body.append(el('h2','','On està escrit?'),el('p','cv-intro','Cerca dins dels documents amb text disponible. Pots cercar en català o castellà; els temes suggerits inclouen totes dues llengües.'));searchBox();
      if(state.query){
        const results=docs.flatMap(doc=>matchingPages(doc,expandedQuery()).map(page=>({doc,page})));
        body.append(el('p','cv-meta',`${results.length} pàgines amb coincidències · ${docs.filter(d=>d.pages.length).length} documents amb text`));
        for(const {doc,page} of results.slice(0,state.limit)){const group=el('div','cv-result');group.append(el('h3','',doc.title),snippet(doc,page));body.append(group);}
        if(!results.length)body.append(el('p','cv-empty','No s’han trobat coincidències. Prova una altra expressió. Els PDF escanejats s’han de consultar directament.'));
        if(results.length>state.limit)body.append(button('Mostra més resultats',()=>{state.limit+=20;save();draw();}));
      }
      const scanned=docs.filter(d=>!d.pages.length);if(scanned.length){const details=el('details','cv-card');details.append(el('summary','',`${scanned.length} PDF sense cerca de text`));for(const d of scanned)details.append(link(d.title+' ↗',d.url));body.append(details);}
    }else if(state.tab==='compara'){
      body.append(el('h2','','El mateix tema, en dos documents'),el('p','cv-intro','Consulta els fragments originals costat a costat. Les diferències de text no determinen, per si soles, quina regla és aplicable avui.'));searchBox();
      const columns=el('div','cv-compare');
      for(const [key,label] of [['left','Primer document'],['right','Segon document']]){
        const column=el('section','cv-column'),field=el('label','cv-filter',label),select=el('select');
        for(const doc of docs.filter(d=>d.kind!=='Inventari')){const o=el('option','',doc.title);o.value=doc.id;select.append(o);}select.value=state[key];
        select.onchange=()=>{state[key]=select.value;save();draw();};field.append(select);column.append(field);
        const doc=docs.find(d=>d.id===state[key]);if(doc){column.append(link('Obre el document complet ↗',doc.fileUrl || doc.url));
          if(!doc.pages.length)column.append(el('p','cv-empty','Aquest PDF és un escaneig. Obre’l per comparar-lo visualment.'));
          else if(!state.query)column.append(el('p','cv-empty','Tria un tema o escriu una expressió per començar.'));
          else{const matches=matchingPages(doc,expandedQuery());column.append(el('p','cv-meta',`${matches.length} pàgines amb coincidències`));const passages=el('div','cv-passages');passages.tabIndex=0;passages.setAttribute('aria-label','Fragments de '+doc.title);for(const page of matches.slice(0,state.limit))passages.append(snippet(doc,page));if(matches.length>state.limit)passages.append(button('Mostra més fragments',()=>{state.limit+=20;save();draw();}));if(!matches.length)passages.append(el('p','cv-empty','Sense coincidències textuals. Això no acredita que el dret no existís.'));column.append(passages);}
        }columns.append(column);
      }body.append(columns);
    }else if(state.tab==='cronologia'){
      body.append(el('h2','','Cronologia documental'),el('p','cv-intro','Els períodes identifiquen els documents. No equivalen a una validació de la vigència de cada clàusula.'));
      for(const doc of [...docs].filter(d=>d.kind!=='Inventari').sort((a,b)=>a.year-b.year)){const row=el('article','cv-timeline');row.append(el('strong','',String(doc.year)),link(doc.title+' ↗',doc.fileUrl || doc.url),el('small','',doc.kind));body.append(row);}
      const old=el('details','cv-card');old.append(el('summary','','Antecedents des de 1961 i altres pactes'));
      old.append(el('p','','L’inventari d’abril de 2020 recull convenis, normes de compliment obligatori, el laude de 1979, l’arbitratge de 1981 i acords específics sobre organització, vacances, contractació, Non Stop i jubilació parcial. Són referències històriques: no tots els textos individuals estan disponibles.'));
      old.append(link('Consulta la secció FMB · pàg. 34 ↗',pageUrl(docs.find(d=>d.id==='inventari2020'),34)));body.append(old);
    }else{
      body.append(el('h2','','Fonts i abast'),el('p','','Consulta documental de FMB. Fonts revisades el 18/10/2026; no hi ha sincronització automàtica de canvis.'),el('p','','Els fragments són transcripcions extretes dels PDF i poden tenir salts de línia o errors de lectura. Contrasta sempre el document original. La pàgina indicada correspon al PDF; alguns visors poden obrir-lo a l’inici.'));
      for(const [label,url] of sources)body.append(link(label+' ↗',url,'cv-source'));
      body.append(el('p','cv-note','Solidaritat Obrera: publicacions consultades, sense un arxiu equivalent de convenis complets localitzat. El pla d’igualtat disponible correspon a 2022–2024.'));
      body.append(link('Consulta pràctica dels permisos →','#/permisos'));body.lastChild.removeAttribute('target');
    }
  }
  body.append(el('p','cv-empty','Carregant l’arxiu de convenis…'));
  async function load(){try{const response=await fetch('./assets/convenis/index.json');if(!response.ok)throw Error();docs=await response.json();draw();}catch{body.replaceChildren(el('p','cv-empty','No s’ha pogut carregar l’arxiu. Comprova la connexió.'),button('Torna-ho a provar',load));}}
  load();return root;
}
