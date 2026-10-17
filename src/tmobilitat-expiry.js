import { expiryDate, source } from './tmobilitat-data.js';
export const expirySources={
  conditions:{label:'TMB · Condicions d’ús: validesa mensual i trimestral',url:'https://www.tmb.cat/ca/tarifes-metro-bus-barcelona/condicions-us-bitllets'},
  procedure:{label:'Procediment 3.0 · pàgina 20',url:source.url+'#page=20'},
  regulation:{label:'DOGC 14/01/2026 · Condicions ATM i reglaments T-16 / T-verda',url:'https://ca-sermetra.atm.cat/documents/662112/2707738/Tarifes_DOGC_ATMBarcelona_2026.pdf/0be94269-ccb1-d72a-b964-5b51bb22a379?t=1769354537414'},
};
export const expiryTitles=[
  {id:'t-usual',name:'T-usual',days:30,manual:1},
  {id:'t-jove',name:'T-jove',days:90,manual:7},
  {id:'t-familiar',name:'T-familiar',days:30,manual:11},
  {id:'t-grup',name:'T-grup',days:30,manual:12},
  {id:'rosa',name:'Targeta Rosa',social:true,manual:31},
  {id:'t-16',name:'T-16',capped:true,manual:10,page:25},
  {id:'t-verda',name:'T-verda',capped:true,manual:18,page:29},
  {id:'acompanyant',name:'Passi d’acompanyant',social:true,manual:30},
];
export const plasticTitleIds=['t-usual','t-jove','t-familiar','t-grup','t-verda'];
export function calculateExpiry({id,mode,start,originalEnd,tariffEnd}={}){
  const title=expiryTitles.find(t=>t.id===id);
  if(!title||!['original','provisional'].includes(mode))return {error:'Selecciona un títol i el tipus de consulta.'};
  const valid=date=>!!expiryDate(date||'',1);
  if(mode==='original'&&!title.days)return valid(originalEnd)?{date:originalEnd,kind:'printed'}:{error:'Introdueix la caducitat indicada al suport o a Consultes.'};
  if(!valid(start))return {error:'Selecciona una data vàlida.'};
  if(mode==='provisional'&&!title.social&&!valid(originalEnd))return {error:'Cal la caducitat del títol original per no ampliar-ne la validesa.'};
  if(mode==='provisional'&&!title.social&&valid(originalEnd)&&originalEnd<start)return {error:'El títol original ja estava caducat en la data d’emissió.'};
  let date=expiryDate(start,mode==='original'?title.days:30),limited=false;
  const cap=mode==='provisional'?(title.social?'':originalEnd):tariffEnd;
  if(cap&&!valid(cap))return {error:'La data límit no és vàlida.'};
  if(cap&&cap<start)return {error:'La data límit és anterior a la data inicial.'};
  if(cap&&cap<date){date=cap;limited=true;}
  return {date,limited,kind:mode};
}

const node=(tag,cls,text)=>{const e=document.createElement(tag);if(cls)e.className=cls;if(text)e.textContent=text;return e;};
const today=()=>{const d=new Date();return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;};
const formatted=date=>date.split('-').reverse().join('/');
export function renderExpiry(){
  const key='tmb-agent-tmobilitat-expiry-v1';let saved={};try{saved=JSON.parse(sessionStorage.getItem(key))||{};}catch{}
  let id=expiryTitles.some(t=>t.id===saved.id)?saved.id:'t-usual';let mode=['original','provisional'].includes(saved.mode)?saved.mode:'original';let start=expiryDate(saved.start||'',1)?saved.start:today();let originalEnd=saved.originalEnd||'',tariffEnd=saved.tariffEnd||'';
  const root=node('section','tm-expiry');root.append(node('h2','','Caducitat de títols'));
  const list=node('div','tm-title-list');list.setAttribute('aria-label','Selecciona un títol');
  const content=node('div','tm-expiry-content');root.append(list,content);
  const persist=()=>{try{sessionStorage.setItem(key,JSON.stringify({id,mode,start,originalEnd,tariffEnd}));}catch{}};
  const buttons=[];
  for(const t of expiryTitles){const b=node('button','tm-title-choice');b.type='button';const img=node('img');const plastic=plasticTitleIds.includes(t.id);img.src=`./assets/tmobilitat/${plastic?'plastic-verd':t.id}.webp`;img.alt='';img.width=168;img.height=105;const copy=node('span','',t.name);
    if(plastic){const visual=node('span','tm-plastic-visual');visual.setAttribute('aria-hidden','true');visual.append(img,node('span','tm-plastic-name',t.name));b.append(visual);}else b.append(img);
    b.append(copy);b.onclick=()=>{id=t.id;mode=t.days?'original':'provisional';originalEnd='';tariffEnd='';draw();persist();};list.append(b);buttons.push([t.id,b]);}
  function draw(){
    const title=expiryTitles.find(t=>t.id===id);buttons.forEach(([value,b])=>b.setAttribute('aria-pressed',String(value===id)));content.replaceChildren();
    content.append(node('h3','',title.name));
    const modeLabel=node('label','','Què vols consultar?'),select=node('select');for(const [value,label] of [['original','Títol original'],['provisional','Bescanvi provisional ferroviari']]){const o=node('option','',label);o.value=value;select.append(o);}select.value=mode;modeLabel.append(select);content.append(modeLabel);select.onchange=()=>{mode=select.value;if(mode==='provisional')start=today();draw();persist();};
    const original=mode==='original';
    const explanation=original?(title.days?`${title.days} dies des de la primera validació, comptant el dia inicial. El límit tarifari pot escurçar aquest còmput.`:'La caducitat pròpia del títol no es calcula sumant dies a avui. Consulta la data del suport o l’eina corporativa.'):(title.social?'El procediment 3.0 estableix 30 dies per al provisional de Metropolitana / acompanyant (p. 20).':title.capped?`El reglament estableix 30 dies o la caducitat del títol original, si és anterior (DOGC, p. ${title.page}).`:'El termini màxim del cartró provisional és de 30 dies. Les dates de caducitat dels títols originals continuen vigents (DOGC, p. 11–12).');
    content.append(node('p','tm-text',explanation));
    const result=node('output','tm-expiry-result');result.setAttribute('aria-live','polite');
    function field(label,value,change,required=false){const wrap=node('label','',label),input=node('input');input.type='date';input.value=value;input.required=required;wrap.append(input);input.oninput=()=>{change(input.value);update();persist();};content.append(wrap);}
    if(original&&title.days)field('Primera validació',start,v=>start=v,true);
    if(!original){if(title.days)field('Emissió del provisional',start,v=>start=v,true);else content.append(node('p','tm-small',`Emissió del provisional avui: ${formatted(today())}`));}
    if((original&&!title.days)||(!original&&!title.social))field(original?'Caducitat indicada al suport / Consultes':'Caducitat del títol original',originalEnd,v=>originalEnd=v,true);
    if(original&&title.days)field('Límit tarifari oficial, si escau (opcional)',tariffEnd,v=>tariffEnd=v);
    function update(){const value=calculateExpiry({id,mode,start:!original&&!title.days?today():start,originalEnd,tariffEnd});result.textContent=value.error||(value.kind==='printed'?`Caducitat del títol: ${formatted(value.date)}`:`Últim dia del còmput: ${formatted(value.date)}${value.limited?' · limitat per la caducitat anterior':''}`);result.classList.toggle('tm-expiry-error',!!value.error);}
    content.append(result);
    const note=original&&title.days?'Vàlid fins a la finalització del servei de l’últim dia; si acaba passada la mitjanit, fins a les 03.59 h de l’endemà segons TMB. El resultat no incorpora cap límit anual que no hagis indicat.':!original?'El bescanvi s’emet automàticament a la DA. Comprova la data emesa; aquest còmput no substitueix la lectura del suport ni n’amplia els drets.':'No s’apliquen els antics terminis de 45/60 dies al títol original.';
    content.append(node('p','tm-small',note));
    const refs=original&&title.days?[expirySources.conditions]:!original?(title.social?[expirySources.procedure]:[expirySources.regulation,expirySources.procedure]):[];
    for(const ref of [...refs,{label:'Fitxa al Manual de títols',url:`https://aplicacions.tmb.cat/manual-titols/titol/view/${title.manual}`}]){const a=node('a','tm-link',ref.label+' ↗');a.href=ref.url;a.target='_blank';a.rel='noopener noreferrer';content.append(a);}update();
  }
  draw();return root;
}
