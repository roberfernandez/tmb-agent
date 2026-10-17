import { permits, filterPermits, relations, sources } from './permisos-data.js';

const state = {query:'',category:'',pay:'',relation:'',travel:''};
const opened = new Set();
const node = (tag,cls,text) => { const el=document.createElement(tag); if(cls) el.className=cls; if(text) el.textContent=text; return el; };
function section(parent,title,text) { parent.append(node('h3','',title),node('p','',text)); }
function sourceLinks(parent,keys) {
  keys.forEach(key=>{ const source=sources[key]; if(!source.url){parent.append(node('p','perm-small',source.label));return;} const a=node('a','perm-source',source.label+' ↗'); a.href=source.url; a.target='_blank'; a.rel='noopener noreferrer'; parent.append(a); });
}
export function renderPermisos() {
  const root=node('section','panel permisos'); root.lang='ca';
  const heading=node('div','perm-heading'); const image=node('img','perm-icon'); image.src='./assets/icons/permisos-192.webp'; image.alt=''; image.width=72; image.height=72;
  const copy=node('div'); copy.append(node('p','eyebrow','GUIA DE CONSULTA · FMB'),node('h1','','Permisos TMB')); heading.append(image,copy);
  root.append(heading,node('p','subtitle','Consulta la durada, els justificants i les condicions de cada permís.'));
  const warning=node('div','perm-warning');
  warning.append(node('p','','Base: la taula més recent disponible, contrastada amb el conveni i la normativa. Les qüestions no resoltes s’indiquen a cada fitxa. Els filtres orienten la consulta.'));
  root.append(warning);
  const form=node('form','perm-filters'); form.setAttribute('role','search'); form.addEventListener('submit',e=>e.preventDefault());
  const searchLabel=node('label','perm-search','Cerca per motiu o paraula');
  const input=node('input'); input.type='search'; input.placeholder='Hospitalització, naixement, domicili…'; input.value=state.query; searchLabel.append(input); form.append(searchLabel);
  function select(key,label,values) {
    const wrap=node('label','',label), el=node('select');
    for(const [value,text] of [['','Tots'],...values]) {const opt=node('option','',text); opt.value=value; el.append(opt);}
    el.value=state[key]; el.addEventListener('change',()=>{state[key]=el.value; update();}); wrap.append(el); form.append(wrap); return el;
  }
  const controls=[select('category','Motiu',[...new Set(permits.map(p=>p.category))].map(v=>[v,v])),select('pay','Tipus de permís',[...new Set(permits.map(p=>p.pay))].map(v=>[v,v])),select('relation','Parentiu o persona',Object.entries(relations)),select('travel','Desplaçament',[['yes','Amb particularitats territorials']])];
  const reset=node('button','perm-reset','Neteja els filtres'); reset.type='button'; reset.addEventListener('click',()=>{Object.keys(state).forEach(k=>state[k]=''); input.value=''; controls.forEach(c=>c.value=''); update(); input.focus();}); form.append(reset);
  input.addEventListener('input',()=>{state.query=input.value;update();});
  const count=node('p','perm-count'); count.setAttribute('role','status'); count.setAttribute('aria-live','polite');
  const list=node('div','perm-list');
  function update() {
    const results=filterPermits(state); count.textContent=`${results.length} de ${permits.length} fitxes · obre’n una per consultar-ne els detalls`;
    list.replaceChildren();
    if(!results.length) list.append(node('p','notice','No hi ha fitxes amb aquests filtres. Prova un altre motiu o neteja la cerca. L’absència de resultats no implica absència de dret.'));
    results.forEach(p=>{
      const card=node('details','perm-card'); card.open=opened.has(p.id);
      card.addEventListener('toggle',()=>card.open?opened.add(p.id):opened.delete(p.id));
      const summary=node('summary'); summary.append(node('span','perm-meta',p.category+' · '+p.pay),node('h2','',p.title),node('span','perm-status',p.controversy?'Controvèrsia':p.pending?'Amb punts per confirmar':'Contrast documental'));
      const body=node('div','perm-body');
      section(body,'Durada',p.reference.duration);
      section(body,'Documentació',p.reference.documents);
      section(body,'Condicions i particularitats',p.reference.notes);
      const proof=node('div','perm-proof'); section(proof,'Contrast i fonts',p.verified);
      sourceLinks(proof,p.refs); body.append(proof);
      if(p.controversy) {const dispute=node('div','perm-warning'); section(dispute,'Controvèrsia',p.controversy);body.append(dispute);}
      if(p.pending) section(body,'Punt pendent',p.pending);
      body.append(node('p','perm-small','Les condicions atribuïdes a la taula i els punts pendents no es presenten com a verificació completa de vigència.'));
      card.append(summary,body); list.append(card);
    });
  }
  root.append(form,count,list);
  const kin=node('details','perm-card'); kin.append(node('summary','','Com interpretar els parentius'));
  const kinBody=node('div','perm-body');
  section(kinBody,'Graus orientatius','Primer grau: pares i fills; per afinitat, sogres i gendre/nora. Segon: avis, nets i germans; cunyats per afinitat. Oncles i nebots: tercer grau. Cosins: quart.');
  section(kinBody,'Parella, convivència i dependència','Es consulten per separat. No s’equiparen automàticament al parentiu. El filtre mostra possibles fitxes de consulta; comprova les condicions i l’abast personal de cadascuna.');
  kin.append(kinBody); root.append(kin);
  const counting=node('details','perm-card');counting.append(node('summary','','Dies laborables, descansos i parella convivent'));
  const countingBody=node('div','perm-body');
  section(countingBody,'Comptar els dies','En un permís de dies laborables, els dies de descans no consumeixen aquests dies. Exemple de còmput: si treballes dilluns i dimarts, descanses dimecres i treballes dijous, dijous és el tercer dia laborable. Això no resol per si sol el termini de gaudi ni la persistència de la causa. Els dies naturals i els addicionals territorials mantenen el seu còmput propi.');
  section(countingBody,'Parella convivent','Urgències i proves amb sedació o amniocentesi: l’art. 23 del conveni 2016–2019 inclou expressament la parella afectiva convivent, sense termini mínim de convivència en aquest apartat. No s’extrapola automàticament a matrimoni o defunció.');
  section(countingBody,'Parella estable a Catalunya','L’art. 234-1 del Codi civil reconeix la parella estable per més de dos anys de convivència ininterrompuda, fill comú durant la convivència o escriptura pública, amb els requisits personals de l’art. 234-2. La condició civil i l’acreditació per a cada permís laboral són qüestions diferents.');
  sourceLinks(countingBody,['conveni2016','civil']);counting.append(countingBody);root.append(counting);
  const refs=node('details','perm-card'); refs.append(node('summary','','Fonts i criteri de revisió'));
  const refsBody=node('div','perm-body');
  section(refsBody,'Àmbit','Personal FMB / Metro de Barcelona. No traslladar automàticament les condicions a altres societats de TMB.');
  section(refsBody,'Criteri','Prioritat a la versió més recent disponible per a cada matèria, contrastada amb conveni i legislació. Les versions antigues són antecedents. La taula CGT de referència no té data d’edició visible; el contingut de naixement és posterior a la versió de 16 setmanes.');
  section(refsBody,'Fonts complementàries','CIM Barcelona, comunicacions de CCOO i CPTC, acords i actes FMB. No s’ha localitzat una taula pròpia de Solidaritat Obrera a l’arxiu revisat. Les discrepàncies no resoltes queden obertes amb l’usuari.');
  section(refsBody,'Tall temporal: 16 d’octubre de 2026','No s’aplica anticipadament la modificació de l’art. 37 amb entrada en vigor el 23/10/2026. Cal revisar aquesta edició per a fets posteriors.');
  sourceLinks(refsBody,Object.keys(sources)); refs.append(refsBody); root.append(refs);
  update(); return root;
}
