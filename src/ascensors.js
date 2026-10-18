import {template} from './ascensors-template.js';
import {stations,procedures,sourceUrl,stationIndex,visibleFloors,procedureLocation} from './ascensors-data.js';
const KEY='tmb-agent-ascensors-v1';
export function renderAscensors(){
 const wrapper=document.createElement('div');wrapper.innerHTML=template;
 const root=wrapper.firstElementChild;root.lang='ca';const q=id=>root.querySelector('#'+id);
 let saved={};try{saved=JSON.parse(sessionStorage.getItem(KEY))||{};}catch{}
 let index=stationIndex(saved.station),previous=index,confirmed=saved.confirmed===true;
 const shaft=root.querySelector('.lf-shaft'),slider=q('lf-slider');
 for(let i=0;i<stations.length;i++){
  const [code,name]=stations[i],option=document.createElement('option');option.value=i;option.textContent=code+' · '+name;q('lf-picker').append(option);
  const row=document.createElement('div');row.className='lf-floor';
  const number=document.createElement('span'),label=document.createElement('small');number.textContent=code;label.textContent=name;row.append(number,label);q('lf-floors').append(row);
 }
 function save(){try{sessionStorage.setItem(KEY,JSON.stringify({station:stations[index][0],confirmed}));}catch{}}
 function link(text,url,className){const a=document.createElement('a');a.textContent=text;a.href=url;a.target='_blank';a.rel='noopener noreferrer';a.className=className;return a;}
 function detail(){
  const box=q('lf-selection');box.replaceChildren();box.hidden=!confirmed;if(!confirmed)return;
  const [code,name,models]=stations[index],heading=document.createElement('h2');heading.textContent=code+' · '+name;box.append(heading);
  if(models.length>1){const note=document.createElement('p');note.textContent='Selecciona el procediment que correspon al model d’ascensor.';box.append(note);}
  if(!models.length){const note=document.createElement('p');note.textContent='La taula de L4 no enllaça cap procediment per a aquesta estació.';box.append(note);}
  for(const model of models){const p=procedures[model],a=link('',p.url,'lf-procedure'),title=document.createElement('strong'),action=document.createElement('span'),location=procedureLocation(code,model);title.textContent=(location?location+' · ':'')+p.name;action.textContent='Obre el procediment de rescat ↗';a.append(title,action);box.append(a);}
  box.append(link('Consulta la font oficial de TMB ↗',sourceUrl+'#línia-4','lf-source'));
 }
 function draw(){
  q('lf-code').textContent=stations[index][0];q('lf-name').textContent=stations[index][1];slider.value=index;slider.setAttribute('aria-valuetext',stations[index].slice(0,2).join(' · '));q('lf-picker').value=index;
  q('lf-up').disabled=index===stations.length-1;q('lf-down').disabled=index===0;q('lf-direction').textContent=index>previous?'▲ PUJANT':index<previous?'▼ BAIXANT':'ESTACIÓ';
  const {low,high}=visibleFloors(index);q('lf-floors').querySelectorAll('.lf-floor').forEach((row,i)=>{row.hidden=i<low||i>high;row.style.top=((high-i)/4*100)+'%';row.classList.toggle('active',i===index)});
  q('lf-cabin').style.top=((high-index)/4*Math.max(0,shaft.clientHeight-68))+'px';
 }
 function set(value){previous=index;index=Math.max(0,Math.min(stations.length-1,Number(value)||0));confirmed=false;draw();detail();save();}
 let drag=null;
 slider.addEventListener('pointerdown',e=>{e.preventDefault();drag={y:e.clientY,index};slider.setPointerCapture(e.pointerId);slider.focus()});
 slider.addEventListener('pointermove',e=>{if(drag)set(drag.index+Math.round((drag.y-e.clientY)/36))});
 slider.addEventListener('pointerup',()=>drag=null);slider.addEventListener('pointercancel',()=>drag=null);
 slider.addEventListener('input',e=>{if(!drag)set(e.target.value)});
 q('lf-picker').addEventListener('change',e=>set(e.target.value));q('lf-up').onclick=()=>set(index+1);q('lf-down').onclick=()=>set(index-1);
 q('lf-confirm').onclick=()=>{confirmed=true;detail();save();q('lf-selection').scrollIntoView({block:'nearest',behavior:'instant'});};
 // Calculate after mounting; a single frame avoids retaining observers on route changes.
 draw();detail();requestAnimationFrame(()=>{if(root.isConnected)draw()});return root;
}
