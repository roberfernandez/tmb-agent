import {items,articles,searchArticles} from './atencio-data.js';
const KEY='tmb-agent-atencio-v1';
export function renderAtencio(){
 const root=document.createElement('section');root.id='atencio-demo';root.lang='ca';
 root.innerHTML='<div class="av-phone"><div class="av-body"><div id="av-home"><h1>Atenció al viatger</h1><p class="av-subtitle">Què necessites consultar?</p><nav id="av-menu" aria-label="Apartats d’atenció al viatger"></nav></div><section id="av-detail" hidden></section></div></div>';
 const q=id=>root.querySelector('#'+id);let screen='home',qr=false,query='',selected='';
 function el(tag,cls,text){const e=document.createElement(tag);if(cls)e.className=cls;if(text)e.textContent=text;return e}
 function button(text,fn,cls='av-action'){const b=el('button',cls,text);b.type='button';b.onclick=fn;return b}
 function save(){try{sessionStorage.setItem(KEY,JSON.stringify({screen,qr,query,selected}))}catch{}}
 function go(id){screen=id;qr=false;query='';selected='';draw();save()}
 for(const item of items){const b=button('',()=>go(item.id),'av-entry'),img=el('img'),copy=el('span');img.src=item.image;img.alt='';copy.append(el('strong','',item.name),el('small','',item.description));b.append(img,copy,el('span','av-chevron','›'));q('av-menu').append(b)}
 function draw(){q('av-home').hidden=screen!=='home';const detail=q('av-detail');detail.hidden=screen==='home';detail.replaceChildren();if(screen==='home')return;const item=items.find(i=>i.id===screen);if(!item)return;
 detail.append(button('← Atenció al viatger',()=>go('home'),'av-back'));const heading=el('div','av-detail-heading'),image=el('img'),copy=el('div');image.src=item.image;image.alt='';copy.append(el('h2','',item.name),el('p','',item.detail));heading.append(image,copy);detail.append(heading);
 if(qr){const surface=el('div','av-qr-surface'),img=el('img');img.src=item.qr;img.alt='QR per obrir '+item.name;surface.append(el('strong','','Escaneja amb el teu mòbil'),img,el('small','',new URL(item.url).hostname));detail.append(surface,el('p','av-note','Accés públic. No cal un compte d’empleat de TMB.'),button('← Torna a les opcions',()=>{qr=false;draw();save()}));return}
 const a=el('a','av-action av-primary',item.link);a.href=item.url;a.target='_blank';a.rel='noopener noreferrer';detail.append(a,button('Mostra el QR al viatger',()=>{qr=true;draw();save()}));
 if(screen==='reglament'){
 detail.append(el('p','av-note','Text del DOGC de 24/10/2024 · CVE-DOGC-A-24291065-2024. Consulta el document oficial i el web de TMB per comprovar possibles actualitzacions, especialment les dels annexos.'));
 const label=el('label','','Cerca als articles i annexos'),input=el('input');input.id='av-search';label.htmlFor=input.id;input.type='search';input.placeholder='Morrió, bicicletes, reclamacions…';input.value=query;const count=el('p','av-result-count'),list=el('div','av-articles');list.setAttribute('aria-label','Articles i annexos');count.setAttribute('aria-live','polite');detail.append(label,input,count,list);
 function results(){const matches=searchArticles(query);count.textContent=matches.length+(matches.length===1?' apartat':' apartats');list.replaceChildren();for(const article of matches){const b=button(article.title,()=>{selected=selected===article.id?'':article.id;results();save()},'av-article');b.setAttribute('aria-expanded',String(selected===article.id));list.append(b);if(selected===article.id){const reading=el('article','av-reading');reading.append(el('h3','',article.title));for(const p of article.paragraphs)reading.append(el('p','',p));list.append(reading)}}if(!matches.length)list.append(el('p','av-note','No s’han trobat coincidències. Prova una altra paraula.'))}
 input.oninput=()=>{query=input.value;results();save()};results();
 }else detail.append(el('p','av-note',screen==='queixes'?'Mostra el QR al viatger perquè obri el formulari públic de queixes, reclamacions i suggeriments.':'Mostra el QR al viatger perquè accedeixi al formulari públic d’al·legacions de percepció mínima.'));
 }
 try{const s=JSON.parse(sessionStorage.getItem(KEY));if(s){screen=items.some(i=>i.id===s.screen)?s.screen:'home';qr=s.qr===true;query=typeof s.query==='string'?s.query:'';selected=articles.some(a=>a.id===s.selected)?s.selected:''}}catch{}
 draw();return root;
}
