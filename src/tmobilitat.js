import {markText,excerpt} from './text-search.js';
import { cases, categories, source, filterCases, expiryDate, guides, guideSearchText } from './tmobilitat-data.js';
import { renderExpiry } from './tmobilitat-expiry.js?v=plastic-v1';

const KEY='tmb-agent-tmobilitat-view-v1';
const defaults={query:'',category:'',selected:'p15-9',open:false};
function readState(){try{const s=JSON.parse(sessionStorage.getItem(KEY));return {...defaults,query:typeof s?.query==='string'?s.query:'',category:categories[s?.category]?s.category:'',selected:cases.some(c=>c.id===s?.selected)?s.selected:defaults.selected,open:s?.open===true,calculatorDate:expiryDate(s?.calculatorDate||'')?s.calculatorDate:'',guides:s?.guides&&typeof s.guides==='object'?s.guides:{}};}catch{return {...defaults,guides:{}};}}
const el=(tag,cls,text)=>{const n=document.createElement(tag);if(cls)n.className=cls;if(text)n.textContent=text;return n;};
function link(label,url,cls='tm-link'){const a=el('a',cls,label);a.href=url;a.target='_blank';a.rel='noopener noreferrer';return a;}
function button(label,fn,cls='tm-button'){const b=el('button',cls,label);b.type='button';b.addEventListener('click',fn);return b;}
function sourceLink(page){return link(`${source.document} · pàgina ${page} ↗`,`${source.url}#page=${page}`,'tm-source-link');}
function paragraph(parent,title,text){parent.append(el('h3','',title),el('p','tm-text',text));}
export function renderTMobilitat(){
  const shell=el('div','tm-shell');const key='tmb-agent-tmobilitat-screen-v1';let screen='home';try{screen=sessionStorage.getItem(key)||'home';}catch{}
  function show(value){screen=['actuacions','caducitat'].includes(value)?value:'home';try{sessionStorage.setItem(key,screen);}catch{}shell.replaceChildren();
    if(screen==='home'){
      const home=el('section','panel tmobilitat tm-home-menu');home.lang='ca';home.append(el('p','eyebrow','TMB AGENT'),el('h1','','T-mobilitat'),el('p','subtitle','Què necessites consultar?'));
      const cards=el('nav','tm-entry-cards');cards.setAttribute('aria-label','Eines T-mobilitat');
      for(const [id,title,description] of [['consultes','Consultes T-mobilitat','Accedeix a l’eina corporativa'],['actuacions','Actuacions i solucions','Troba què cal fer en cada cas'],['caducitat','Caducitat de títols','Consulta i calcula les dates']]){
        const card=id==='consultes'?link('',source.consultes,'tm-entry-card'):button('',()=>show(id),'tm-entry-card');
        let image;if(id==='consultes'){image=el('img','tm-entry-image');image.src='./assets/tmobilitat/consultes.webp';image.alt='';image.width=95;image.height=95;}
        else{image=el('span',`tm-entry-art tm-entry-${id}`);image.setAttribute('aria-hidden','true');if(id==='actuacions'){image.append(el('span','tm-entry-arrow','←'),el('span','tm-entry-lcd','SUPORT\nBLOQUEJAT'),el('span','tm-entry-nfc',')))'));}else{const calendar=el('span','tm-entry-calendar');calendar.append(el('small','','CADUCITAT'),el('strong','','▦'));image.append(el('span','tm-entry-ticket','T-mobilitat\n)))'),calendar);}}
        const names={consultes:'Consultes\nT-mobilitat',actuacions:'Actuacions\ni solucions',caducitat:'Caducitat\nde títols'};
        const copy=el('span','tm-entry-copy');copy.append(el('strong','',names[id]||title),el('small','',description));card.append(image,copy,el('span','tm-entry-chevron',id==='consultes'?'↗':'›'));cards.append(card);
      }
      home.append(cards,link('Manual de títols ↗',source.manual,'tm-menu-manual'));shell.append(home);return;
    }
    const back=button('← T-mobilitat',()=>show('home'),'tm-section-back');shell.append(back);
    if(screen==='actuacions')shell.append(renderActuacions());else{const panel=el('section','panel tmobilitat');panel.lang='ca';panel.append(renderExpiry());shell.append(panel);}
  }
  show(screen);return shell;
}
function renderActuacions(){
  const state=readState();
  const root=el('section','panel tmobilitat');root.lang='ca';
  const heading=el('div','tm-heading');heading.append(el('h1','','Actuacions i solucions'),el('p','subtitle','Consulta d’actuacions per al personal TMB'));
  const form=el('form','tm-filters');form.setAttribute('role','search');form.addEventListener('submit',e=>e.preventDefault());
  const searchLabel=el('label','tm-search-label','Cerca un missatge o una gestió');const search=el('input');search.type='search';search.placeholder='PIN, suport bloquejat, devolució…';search.value=state.query;searchLabel.append(search);
  const categoryLabel=el('label','','Àmbit');const category=el('select');for(const [v,t] of [['','Tots els àmbits'],...Object.entries(categories)]){const o=el('option','',t);o.value=v;category.append(o);}category.value=state.category;categoryLabel.append(category);
  form.append(searchLabel,categoryLabel);
  const machine=el('div','tm-machine');const top=el('div','tm-machine-top');const arrow=el('span','tm-arrow','←');arrow.setAttribute('aria-hidden','true');top.append(arrow,el('span','','T-mobilitat · Consulta d’actuacions'));
  const lcd=button('',()=>{state.open=!state.open;updateDetail();save();},'tm-lcd');lcd.setAttribute('aria-controls','tm-actuacio');
  const kind=el('span','tm-kind'),message=el('strong','tm-message'),hint=el('span','tm-lcd-hint');lcd.append(kind,message,hint);
  const bottom=el('div','tm-machine-bottom');const nfc=el('span','tm-nfc',')))');nfc.setAttribute('aria-hidden','true');bottom.append(el('span','','PAR'),nfc,el('span','','Personal TMB'));machine.append(top,lcd,bottom);
  const matchPreview=el('p','tm-search-preview');
  const empty=el('p','notice','No hi ha casos amb aquests filtres. Prova una altra cerca.');
  const pager=el('div','tm-pager');const count=el('span');count.setAttribute('role','status');count.setAttribute('aria-live','polite');
  const prev=button('←',()=>move(-1));prev.setAttribute('aria-label','Cas anterior');const next=button('→',()=>move(1));next.setAttribute('aria-label','Cas següent');pager.append(prev,count,next);
  const pickerLabel=el('label','tm-case-label','Selecciona un cas');const picker=el('select');pickerLabel.append(picker);
  const reset=button('Neteja la cerca',()=>{state.query='';state.category='';search.value='';category.value='';update();search.focus();},'tm-reset');
  const detail=el('section','tm-detail');detail.id='tm-actuacio';
  const consultes=link('Consultes T-mobilitat ↗',source.consultes,'tm-consultes');
  const refs=el('details','tm-refs');refs.append(el('summary','','Fonts i documentació'));const refsBody=el('div','tm-refs-body');refsBody.append(el('p','tm-small',`Contingut basat en ${source.document}, del ${source.date}. Les notes de context i els circuits resumits s’identifiquen per separat del text transcrit.`),sourceLink(1),link('Manual de títols ↗',source.manual),link('Pàgina corporativa T-mobilitat ↗',source.intranet),el('p','tm-small','La representació del visor PAR és una forma de navegar. Les gestions i els circuits no es presenten com a missatges literals de la porta.'));refs.append(refsBody);
  root.append(heading,form,machine,matchPreview,pager,pickerLabel,empty,reset,detail,consultes,refs);
  let results=[];
  function save(){try{sessionStorage.setItem(KEY,JSON.stringify(state));}catch{}}
  function selected(){return results.find(c=>c.id===state.selected);}
  function choose(id){state.selected=id;state.open=false;picker.value=id;updateScreen();save();}
  function move(delta){const i=results.findIndex(c=>c.id===state.selected);choose(results[(i+delta+results.length)%results.length].id);}
  function jump(id){state.query='';state.category='';search.value='';category.value='';state.selected=id;state.open=true;update();lcd.focus();}
  function update(){
    results=filterCases(state);if(!results.some(c=>c.id===state.selected))state.selected=results[0]?.id||'';
    picker.replaceChildren();for(const c of results){const option=el('option','',c.title);option.value=c.id;picker.append(option);}picker.value=state.selected;
    machine.hidden=pager.hidden=pickerLabel.hidden=!results.length;empty.hidden=!!results.length;
    prev.disabled=next.disabled=results.length<2;reset.hidden=!state.query&&!state.category;
    updateScreen();save();
  }
  function updateScreen(){
    const c=selected();if(!c){detail.hidden=true;matchPreview.hidden=true;matchPreview.replaceChildren();return;}
    const index=results.indexOf(c);count.textContent=`${index+1} / ${results.length} casos`;
    kind.textContent=`${categories[c.category]} · ${c.kind==='table'?'Cas del procediment':c.kind==='error-da'?'Error a la DA':'Circuit de consulta'}`;
    message.textContent=c.title;markText(message,state.query);
    matchPreview.replaceChildren();matchPreview.hidden=!state.query.trim();
    if(state.query.trim()){const texts=[c.text,c.type,c.solution,...c.notes,c.original].filter(Boolean);const matches=texts.filter(t=>{const n=el('p','',t);markText(n,state.query);return n.querySelector('mark')});matchPreview.textContent=matches.length?excerpt(matches.join(' · '),state.query,350):guideSearchText(c.guide)?'Coincidència a la guia pas a pas. Obre l’actuació i segueix les preguntes per veure la resposta que correspon al cas.':c.title;markText(matchPreview,state.query);}
    updateDetail();
  }
  function updateDetail(){
    const c=selected();detail.replaceChildren();detail.hidden=!c||!state.open;lcd.setAttribute('aria-expanded',String(state.open));hint.textContent=state.open?'Toca per tancar l’actuació':'Toca per veure l’actuació ↗';if(!c||!state.open)return;
    const title=el('div','tm-detail-heading');title.append(el('h2','','Actuació'),button('×',()=>{state.open=false;updateDetail();save();lcd.focus();},'tm-close'));title.lastChild.setAttribute('aria-label','Tancar actuació');detail.append(title);
    const meta=el('dl','tm-meta');for(const [label,value] of [['Tipus d’actuació',c.type],['Solució',c.solution]]){const group=el('div');group.append(el('dt','',label),el('dd','',value));meta.append(group);}detail.append(meta);
    paragraph(detail,c.kind==='table'?'Actuació del personal de TMB':c.kind==='error-da'?'Resposta del procediment':'Circuit del procediment · resum',c.text);
    if(c.notes.length){const notes=el('div','tm-context');notes.append(el('h3','','Condicions i context'));for(const note of c.notes)notes.append(el('p','',note));detail.append(notes);}
    if(c.guide)detail.append(renderGuide(c.guide,state.guides?.[c.guide],path=>{state.guides||={};state.guides[c.guide]=path;save();},state.query));
    if(c.calculator)detail.append(renderCalculator(state.calculatorDate,date=>{state.calculatorDate=date;save();}));
    if(c.related.length){const related=el('div','tm-related');related.append(el('h3','','Casos relacionats'));for(const id of c.related){const target=cases.find(t=>t.id===id);if(target)related.append(button(target.title+' →',()=>jump(id),'tm-related-button'));}detail.append(related);}
    if(c.original){const original=el('details','tm-original');original.append(el('summary','','Text original del procediment'),el('p','tm-text',c.originalTitle+'\n\n'+c.original));detail.append(original);}
    detail.append(sourceLink(c.page),el('p','tm-small',`Font del ${source.date}. Còpia incorporada a l’app. Si el visor no hi salta directament, selecciona la pàgina indicada.`));markText(detail,state.query);
  }
  search.addEventListener('input',()=>{state.query=search.value;state.open=false;update();});category.addEventListener('change',()=>{state.category=category.value;state.open=false;update();});picker.addEventListener('change',()=>choose(picker.value));
  update();return root;
}
function renderGuide(id,saved,onChange,query=''){
  const guide=guides[id],box=el('details','tm-guide');box.append(el('summary','','Guia pas a pas'));const body=el('div','tm-guide-body');body.setAttribute('aria-live','polite');let path=[guide.start];
  if(Array.isArray(saved)&&saved[0]===guide.start){for(const next of saved.slice(1,12)){if(!(guide.nodes[path.at(-1)].options||[]).some(([,to])=>to===next))break;path.push(next);}if(path.length>1)box.open=true;}
  function draw(){body.replaceChildren();const current=guide.nodes[path.at(-1)];body.append(el('p','tm-small',`Circuit de la pàgina ${guide.page}`),el('p','',current.question||current.result));
    if(current.options){const options=el('div','tm-guide-options');for(const [label,to] of current.options)options.append(button(label,()=>{path.push(to);onChange([...path]);draw();}));body.append(options);}
    if(path.length>1)body.append(button('← Pas anterior',()=>{path.pop();onChange([...path]);draw();},'tm-reset'));
    body.append(link('Obrir Consultes ↗',source.consultes));markText(body,query);
  }
  box.append(body);draw();return box;
}
function renderCalculator(saved,onChange){
  const box=el('div','tm-calculator');box.append(el('h3','','Calcula els 30 dies del provisional'));
  const label=el('label','','Data d’emissió');const input=el('input');input.type='date';const now=new Date();input.value=expiryDate(saved||'')?saved:[now.getFullYear(),String(now.getMonth()+1).padStart(2,'0'),String(now.getDate()).padStart(2,'0')].join('-');label.append(input);
  const output=el('output');output.setAttribute('aria-live','polite');const update=()=>{const result=expiryDate(input.value);output.textContent=result?`Últim dia del còmput: ${result.split('-').reverse().join('/')}`:'Selecciona una data vàlida.';};input.addEventListener('input',()=>{update();onChange(input.value);});box.append(label,output);update();return box;
}
