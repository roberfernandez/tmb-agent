import { imageGestures } from './image-gestures.js';

const el=(tag,cls,text)=>{const n=document.createElement(tag);if(cls)n.className=cls;if(text!==undefined)n.textContent=text;return n;};
const norm=s=>String(s).normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase();
const link=(text,href)=>{const n=el('a','mini-link',text);n.href=href;n.target='_blank';n.rel='noopener noreferrer';return n;};
function shell(title,subtitle){const n=el('section','mini-app');n.append(el('h1','',title),el('p','mini-intro',subtitle));return n;}
function card(title,paragraphs){const n=el('article','mini-card');n.append(el('h2','',title));for(const p of paragraphs)n.append(el('p','',p));return n;}
const docs='./assets/tramits/';
export const procedures=[
 {title:'Plus d’extraradi',text:['Qüestionari i sol·licitud d’abonament del plus d’extraradi.','Emplena les dades de l’adreça habitual, centre i horari de treball, mitjà de transport, tipus de bitllet i preu. El formulari permet canviar de camp amb el tabulador.'],files:[['Descarrega el qüestionari','extraradi.pdf']]},
 {title:'Excedències',text:[],url:'https://tmbbcn.sharepoint.com/sites/Tramitsempleats/SitePages/Exced%C3%A8ncies.aspx'},
 {title:'Abonament de receptes',text:['Adjunta les receptes i el tiquet de farmàcia amb l’aportació abonada.','Per als familiars, només s’abonen les receptes de les persones que consten al número de la Seguretat Social de l’empleat/da.'],files:[['Descarrega el formulari','receptes.pdf']]},
 {title:'Fons d’Assistència Social · FAS',text:['Sol·licituds d’ajut mensual, ajut puntual i préstec. Presenta la petició signada i la documentació que correspongui al tipus d’ajut.','Consulta la normativa per conèixer les condicions i els justificants necessaris.'],files:[['Petició FAS','peticio-fas.pdf'],['Normativa FAS','normativa-fas.pdf']]},
 {title:'Préstec per a habitatge',text:['El formulari indica un màxim de 25.000 € i un termini de devolució de 6 anys, amb modalitats de 12 o 16 pagaments anuals.','Documentació: escriptura original, certificat bancari del saldo pendent i certificat del compte per a la transferència. Consulta totes les condicions al document.'],files:[['Descarrega el formulari','prestec-habitatge.pdf']]},
 {title:'Préstec per a obres',text:['Per a obres a l’habitatge habitual: màxim de 6.500 €. Devolució en 2 anys (32 pagaments) o 2 anys i mig (40 pagaments), amb les pagues extraordinàries incloses.','Adjunta el certificat del compte bancari i un pressupost segellat amb nom i cognoms, adreça, detall de les obres i CIF de l’empresa o professional.'],files:[['Descarrega el formulari','prestec-obres.pdf']]},
 {title:'Trencament d’ulleres',text:[],url:'https://tmbbcn.sharepoint.com/sites/Cont_Econ_Comp_Inf_Corp/SitePages/AjutsMilloresSocials_TrencamentUlleres.aspx'},
 {title:'Permís per familiar amb malaltia oncològica',text:['Formulari per presentar als serveis mèdics, amb les dades del treballador/a i del familiar.','La informació del facultatiu no cal emplenar-la si ja consta als informes assistencials adjunts. El document inclou les signatures i el consentiment corresponents.'],files:[['Descarrega la sol·licitud','permis-oncologic.pdf']]},
];
export function renderTramits(){
 const root=shell('Tràmits i ajuts','Formularis i documents per preparar les teves gestions.');
 const portal=card('Tots els tràmits de TMB',['Accedeix al portal corporatiu per consultar aquests tràmits i altres sol·licituds.']);
 portal.classList.add('mini-highlight');portal.append(link('Obre el portal de tràmits ↗','https://tmbbcn.sharepoint.com/sites/Tramitsempleats/SitePages/Sollicituds.aspx'));root.append(portal);
 const input=el('input','mini-input');input.type='search';input.placeholder='Cerca un tràmit…';input.setAttribute('aria-label','Cerca un tràmit');
 const list=el('div','mini-list');const status=el('p','mini-status');status.setAttribute('aria-live','polite');
 function render(){const found=procedures.filter(p=>norm(p.title+' '+p.text.join(' ')).includes(norm(input.value.trim())));list.replaceChildren();status.textContent=found.length?`${found.length} tràmits`:'No s’ha trobat cap tràmit.';for(const p of found){const c=card(p.title,p.text);if(p.url){c.replaceChildren(link(p.title+' ↗',p.url));}else{const actions=el('div','mini-actions');for(const [label,file]of p.files)actions.append(link(label,docs+file));c.append(actions);}list.append(c);}}
 input.addEventListener('input',render);root.append(input,status,list);render();return root;
}

export const payments=[
 {month:0,title:'Gratificació de vacances',when:'Amb la nòmina de gener'},
 {month:1,title:'Complement de formació',when:'Amb la nòmina de febrer'},
 {month:2,title:'Paga extraordinària',when:'A finals de març'},
 {month:5,title:'Paga extraordinària',when:'A finals de juny'},
 {month:8,title:'Paga extraordinària',when:'A finals de setembre'},
 {month:9,title:'Prima de productivitat',when:'Amb la nòmina d’octubre'},
 {month:11,title:'Paga extraordinària de Nadal',when:'A mitjan desembre'},
];
const monthNames=['Gener','Febrer','Març','Abril','Maig','Juny','Juliol','Agost','Setembre','Octubre','Novembre','Desembre'];
export function upcomingPayment(date=new Date()){
 const month=Number(new Intl.DateTimeFormat('en',{month:'numeric',timeZone:'Europe/Madrid'}).format(date))-1;
 const year=Number(new Intl.DateTimeFormat('en',{year:'numeric',timeZone:'Europe/Madrid'}).format(date));
 return {...(payments.find(p=>p.month>=month)||payments[0]),year:payments.some(p=>p.month>=month)?year:year+1};
}
export function renderCobraments(){
 const root=shell('Dates de cobrament','Els cobraments complementaris al llarg de l’any.');
 const next=upcomingPayment();const hero=card('Pròxim període de cobrament',[`${monthNames[next.month]} · ${next.year}`,next.title,next.when]);hero.classList.add('mini-highlight');root.append(hero);
 root.append(el('p','mini-intro','Els períodes són orientatius: el document no fixa un dia bancari exacte.'));
 const list=el('div','mini-list');for(const p of payments){const c=card(p.title,[p.when]);c.prepend(el('span','mini-month',monthNames[p.month]));list.append(c);}root.append(list);
 root.append(el('h2','','Bestretes'));
 root.append(card('Bestreta mensual',['Sol·licita-la abans del dia 8. Abonament el dia 15.','Es descompta de la nòmina del mateix mes.']),card('Bestreta especial',['Sol·licita-la abans del dia 8. Abonament el dia 15.','Es descompta en 10 mensualitats.']));return root;
}

// Exact source-pixel regions: exclude proposal headers and handwriting.
const calendars={
 '2026':[{id:'general',name:'General · CGT',file:'2026-general.png',width:1057,height:1518}],
 '2027':[
  {id:'general',name:'General',file:'2027-general.jpg',width:905,height:1280,box:[88,176,730,1025]},
  {id:'k',name:'Lletra K',file:'2027-k.jpg',width:1280,height:905,box:[180,175,920,585]},
  {id:'l',name:'Lletra L',file:'2027-l.jpg',width:1280,height:905,box:[130,105,1035,590]},
  {id:'m',name:'Lletra M',file:'2027-m.jpg',width:1280,height:905,box:[170,180,935,590]},
  {id:'n',name:'Lletra N',file:'2027-n.jpg',width:1280,height:905,box:[170,180,930,590]},
 ]};
export function renderCalendaris(){
 const root=shell('Calendaris','Festes AAC, lletres i miniperíodes.');
 const controls=el('div','mini-actions'),year=el('select','mini-input'),type=el('select','mini-input');year.setAttribute('aria-label','Any');type.setAttribute('aria-label','Calendari');for(const y of Object.keys(calendars))year.append(new Option(y,y));year.value='2027';
 const heading=el('h2'),viewer=el('div','mini-calendar-viewport'),toolbar=el('div','mini-actions'),note=el('p','mini-intro');viewer.tabIndex=0;viewer.setAttribute('aria-label','Calendari ampliable');
 let zoom=1,graphic;const size=()=>{if(graphic){graphic.style.width=`${zoom*100}%`;graphic.style.maxWidth='none';}};
 for(const [text,action]of [['−',()=>{zoom=Math.max(1,zoom-.5);size();}],['Ajusta',()=>{zoom=1;size();}],['+',()=>{zoom=Math.min(6,zoom+.5);size();}]]){const b=el('button','mini-button',text);b.type='button';b.setAttribute('aria-label',text==='+'?'Amplia':text==='−'?'Redueix':'Ajusta a l’amplada');b.onclick=action;toolbar.append(b);}
 function draw(){const c=calendars[year.value].find(c=>c.id===type.value);heading.textContent=`${year.value} · ${c.name}`;viewer.replaceChildren();zoom=1;const ns='http://www.w3.org/2000/svg';graphic=document.createElementNS(ns,'svg');graphic.setAttribute('viewBox',(c.box||[0,0,c.width,c.height]).join(' '));graphic.setAttribute('role','img');graphic.setAttribute('aria-label',heading.textContent);const image=document.createElementNS(ns,'image');image.setAttribute('href','./assets/calendaris/'+c.file);image.setAttribute('width',c.width);image.setAttribute('height',c.height);graphic.append(image);viewer.append(graphic);size();viewer.scrollTop=viewer.scrollLeft=0;note.textContent=c.id==='l'&&year.value==='2027'?'Miniperíode addicional el 22/11 (pont de desembre).':c.id==='general'&&year.value==='2027'?'El col·lectiu AAC a temps complet que ampliï la jornada els dies de Non Stop tindrà, com a mínim, un dia més de mini, sempre que no sol·liciti cobrar aquest excés com a extraordinari.':'';}
 function update(){type.replaceChildren();for(const c of calendars[year.value])type.append(new Option(c.name,c.id));draw();}
 year.onchange=update;type.onchange=draw;controls.append(year,type);root.append(controls,heading,toolbar,viewer,note);imageGestures(viewer,viewer,()=>zoom,value=>{zoom=Math.max(1,Math.min(6,value));size();});update();return root;
}

function highlight(text,query){const n=el('span');if(!query){n.textContent=text;return n;}let start=0,index;const normalized=norm(text),needle=norm(query);while((index=normalized.indexOf(needle,start))>=0){n.append(document.createTextNode(text.slice(start,index)),el('mark','',text.slice(index,index+query.length)));start=index+query.length;}n.append(document.createTextNode(text.slice(start)));return n;}
export function renderRC(){
 const root=shell('Reglament de Circulació','RC, instruccions i documentació inclosa al recull.');root.append(link('Obre el PDF complet ↗','./assets/rc/reglament.pdf'));
 const input=el('input','mini-input');input.type='search';input.placeholder='Cerca un article, una paraula o una expressió…';input.setAttribute('aria-label','Cerca al Reglament de Circulació');
 const nav=el('select','mini-input');nav.setAttribute('aria-label','Capítol o article');const status=el('p','mini-status','Carregant el reglament…');status.setAttribute('aria-live','polite');const results=el('div','mini-list');root.append(input,nav,status,results);
 fetch('./assets/rc/articles.json').then(r=>{if(!r.ok)throw Error('load');return r.json();}).then(data=>{
  nav.append(new Option('Tots els capítols i articles','all'));
  const chapters={'1':'Normes generals','2':'Senyals','3':'Circulació ferroviària','4':'Sistemes de seguretat i blocatge','5':'Incidències i emergències'};
  let previous='';for(const a of data.nav){const ch=a.number[0];if(ch!==previous){nav.append(new Option(`${ch} · ${chapters[ch]||'Capítol '+ch}`,`chapter:${ch}`));previous=ch;}nav.append(new Option(`${a.number} · ${a.title}`,String(a.page)));}
  nav.append(new Option('IRC i annexos','appendices'));
  function render(){const query=input.value.trim(),choice=nav.value;let pages=data.pages;if(choice==='appendices')pages=pages.filter(p=>p.page>=39);else if(choice.startsWith('chapter:')){const ch=choice.split(':')[1],first=data.nav.find(a=>a.number.startsWith(ch)),next=data.nav.find(a=>Number(a.number[0])>Number(ch));pages=pages.filter(p=>p.page>=first.page&&p.page<(next?.page||37));}else if(choice!=='all')pages=pages.filter(p=>p.page===Number(choice));
   if(query)pages=pages.filter(p=>norm(p.paragraphs.join(' ')).includes(norm(query)));results.replaceChildren();status.textContent=query?`${pages.length} pàgines amb coincidències`:`${pages.length} pàgines · PDF complet de ${data.totalPages} pàgines`;
   for(const p of pages){const c=el('article','mini-card rc-page');c.append(el('h2','',`Pàgina ${p.page}`));for(const a of data.nav.filter(a=>a.page===p.page))c.append(el('h3','',`${a.number} · ${a.title}`));for(const text of p.paragraphs){const paragraph=el('p');paragraph.append(highlight(text,query));c.append(paragraph);}c.append(link('Consulta aquesta pàgina al PDF ↗',`./assets/rc/reglament.pdf#page=${p.page}`));results.append(c);}
  }input.oninput=render;nav.onchange=render;render();
 }).catch(()=>{status.textContent='No s’ha pogut carregar el text. Pots consultar el PDF complet.';const retry=el('button','mini-button','Torna-ho a provar');retry.onclick=()=>root.replaceWith(renderRC());results.append(retry);});return root;
}
