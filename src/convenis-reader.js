import { documentBlocks } from './convenis-format.js';

export function documentMatches(text, query) {
  function fold(value) {
    let text='', offsets=[];let position=0;
    for(const char of value){
      const normalized=char.normalize('NFD').replace(/\p{M}/gu,'').toLowerCase();
      if(!normalized&&offsets.length)offsets[offsets.length-1][1]=position+char.length;
      for(const c of normalized){const next=/\s/u.test(c)?' ':c;
        if(next===' '&&text.endsWith(' ')){offsets[offsets.length-1][1]=position+char.length;continue;}
        text+=next;offsets.push([position,position+char.length]);}
      position+=char.length;
    }return {text,offsets};
  }
  const haystack=fold(text),needle=fold(query.trim()).text;
  if(!needle)return [];
  const ranges=[];let i=0;
  while((i=haystack.text.indexOf(needle,i))!==-1){ranges.push([haystack.offsets[i][0],haystack.offsets[i+needle.length-1][1]]);i+=needle.length;}
  return ranges;
}
export function openConvenisReader(doc,initialPage=1,initialQuery='') {
  const el=(tag,cls,text)=>{const node=document.createElement(tag);if(cls)node.className=cls;if(text)node.textContent=text;return node;};
  const button=(label,action)=>{const b=el('button','cv-button',label);b.type='button';b.onclick=action;return b;};
  const opener=document.activeElement,dialog=el('dialog','cv cv-reader');dialog.setAttribute('aria-label',doc.title);
  const header=el('div','cv-reader-header');header.append(el('h2','',doc.title),button('Tancar',()=>dialog.close()));
  const form=el('form','cv-reader-search'),label=el('label','','Cercar al document'),input=el('input');input.type='search';input.placeholder='Paraula o expressió';input.maxLength=150;input.value=initialQuery;label.append(input);
  const status=el('output','cv-meta');status.setAttribute('aria-live','polite');
  const previous=button('←',()=>move(-1)),next=button('→',()=>move(1));previous.setAttribute('aria-label','Coincidència anterior');next.setAttribute('aria-label','Coincidència següent');
  const clear=button('Neteja',()=>{input.value='';search();input.focus();});form.append(label,previous,status,next,clear);
  const content=el('div','cv-reader-content');content.tabIndex=0;
  const note=el('p','cv-ocr-note','Lectura del text del document. Els escanejos poden contenir errors d’OCR i pàgines sense text; comprova el PDF original.');
  const original=el('a','cv-link','Obre el PDF original ↗');original.href=doc.fileUrl||doc.url;original.target='_blank';original.rel='noopener noreferrer';
  const pages=(doc.pages||[]).map(page=>{const box=el('section','cv-reader-page');box.append(el('h3','',`Pàgina ${page.page}`));const text=el('div','cv-reader-text');box.append(text);content.append(box);return {page,box,text,blocks:documentBlocks(page.text)};});
  if(!pages.length)content.append(el('p','cv-empty','Aquest document no té text disponible. Consulta el PDF original.'));
  let matches=[],index=-1;
  function update(){previous.disabled=next.disabled=!matches.length;status.textContent=matches.length?`${index+1} de ${matches.length}`:input.value.trim()?'Cap coincidència':'Escriu per cercar';}
  function activate(value,scroll=true){if(!matches.length){index=-1;update();return;}matches[index]?.forEach(mark=>mark.classList.remove('cv-reader-current'));index=(value+matches.length)%matches.length;const group=matches[index];group.forEach(mark=>mark.classList.add('cv-reader-current'));const mark=group[0];update();if(scroll)mark.scrollIntoView({block:'center',behavior:'auto'});}
  function move(delta){activate(index+delta);}
  function search(){matches=[];index=-1;
    for(const {page,text,blocks} of pages){
      text.replaceChildren();
      const ranges=documentMatches(page.text,input.value);
      const groups=ranges.map(()=>[]);
      let firstRange=0;
      for(const block of blocks){
        const tag=block.type==='chapter'?'h4':block.type==='heading'?'h5':'p';
        const paragraph=el(tag,'cv-reader-block cv-reader-'+block.type);
        let cursor=block.start;
        while(firstRange<ranges.length && ranges[firstRange][1]<=block.start)firstRange++;
        for(let i=firstRange;i<ranges.length && ranges[i][0]<block.end;i++){
          const start=Math.max(block.start,ranges[i][0]),end=Math.min(block.end,ranges[i][1]);
          paragraph.append(document.createTextNode(page.text.slice(cursor,start)));
          const mark=el('mark','',page.text.slice(start,end));
          paragraph.append(mark);groups[i].push(mark);cursor=end;
        }
        paragraph.append(document.createTextNode(page.text.slice(cursor,block.end)));
        text.append(paragraph);
      }
      matches.push(...groups);
    }activate(0);update();
  }
  input.addEventListener('input',search);form.onsubmit=e=>{e.preventDefault();move(e.shiftKey?-1:1);};
  input.addEventListener('keydown',e=>{if(e.key==='Enter'){e.preventDefault();move(e.shiftKey?-1:1);}});
  dialog.append(header,form,note,original,content);document.body.append(dialog);
  dialog.addEventListener('close',()=>{dialog.remove();if(opener?.isConnected)opener.focus();},{once:true});
  dialog.showModal();search();if(!initialQuery)pages.find(p=>p.page.page===initialPage)?.box.scrollIntoView({block:'start'});input.focus({preventScroll:true});
}
