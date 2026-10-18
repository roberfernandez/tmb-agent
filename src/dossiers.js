import {template} from './dossiers-template.js';
import {stations} from './dossiers-data.js';
const KEY='tmb-agent-dossiers-v1';

export function renderDossiers(){const wrapper=document.createElement('div');wrapper.innerHTML=template;const root=wrapper.firstElementChild,q=id=>root.querySelector('#'+id);
let index=0,opened=false,gesture=null,suppressClick=false;
for(let i=0;i<stations.length;i++){const option=document.createElement('option');option.value=i;option.textContent=stations[i].slice(0,2).join(' · ');q('df-picker').append(option)}
function save(){try{sessionStorage.setItem(KEY,JSON.stringify({privateContent:{index,opened}}))}catch{}}
function draw(){q('df-code').textContent=stations[index][0];q('df-name').textContent=stations[index][1];q('df-picker').value=index;q('df-prev').disabled=index===0;q('df-next').disabled=index===21;const low=Math.max(0,Math.min(17,index-2));q('df-tabs').replaceChildren();for(let i=low;i<low+5;i++){const b=document.createElement('button');b.type='button';b.className='df-tab cursor-interaction';b.setAttribute('aria-pressed',String(i===index));const n=document.createElement('strong'),name=document.createElement('span');n.textContent=stations[i][0];name.textContent=stations[i][1];b.append(n,name);b.onclick=()=>{if(!suppressClick)set(i)};q('df-tabs').append(b)}q('df-cover').classList.toggle('open',opened);q('df-open').href=stations[index][2];q('df-open').setAttribute('aria-label','Obre el dossier de '+stations[index][1]);}
function set(i){index=Math.max(0,Math.min(21,Number(i)||0));opened=false;draw();save()}
q('df-prev').onclick=()=>set(index-1);q('df-next').onclick=()=>set(index+1);q('df-picker').onchange=e=>set(e.target.value);q('df-open').onclick=()=>{opened=true;draw();save()};
const stack=q('df-stack');stack.addEventListener('pointerdown',e=>{gesture={y:e.clientY,index,moved:false}});stack.addEventListener('pointermove',e=>{if(!gesture)return;const d=gesture.y-e.clientY;if(Math.abs(d)>12){stack.setPointerCapture(e.pointerId);gesture.moved=true;suppressClick=true;set(gesture.index+Math.round(d/40))}});function end(){gesture=null;setTimeout(()=>suppressClick=false,0)}stack.addEventListener('pointerup',end);stack.addEventListener('pointercancel',end);
function restore(s){const p=s?.privateContent;if(!p||!Number.isInteger(p.index)||p.index<0||p.index>21)return;index=p.index;opened=p.opened===true;draw()}
draw();try{restore(JSON.parse(sessionStorage.getItem(KEY)))}catch{}return root;}
