
const HEADERS=['HI','EI','VI','TE','HF','EF','VF'];
const normalize=s=>String(s).toUpperCase().replace(/[^A-Z0-9]/g,'');
const headerName=s=>normalize(String(s).replace(/\|/g,'I'));
const center=w=>({x:(w.bbox.x0+w.bbox.x1)/2,y:(w.bbox.y0+w.bbox.y1)/2});
const number=s=>String(s).toUpperCase().replace(/[OQ]/g,'0').replace(/[IL|]/g,'1').replace(/\s/g,'');
const aliases={HI:['HI','H1','HL'],EI:['EI','EL','E1'],VI:['VI','VL','V1'],TE:['TE','TIE','T1E'],HF:['HF'],EF:['EF'],VF:['VF']};
function lineGroup(words){const top=Math.min(...words.map(w=>w.bbox.y0)),bottom=Math.max(...words.map(w=>w.bbox.y1));return {words,y:words.reduce((n,w)=>n+center(w).y,0)/words.length,h:bottom-top,top,bottom}}
function findHeader(words,groups){
 const anchors=words.filter(w=>HEADERS.some(k=>aliases[k].includes(headerName(w.text))));
 const candidates=[...groups,...anchors.map(w=>lineGroup(anchors.filter(a=>Math.abs(center(a).y-center(w).y)<Math.max(16,(w.bbox.y1-w.bbox.y0)*2.5))))];
 let best=null;
 for(const group of candidates){
  const hits=HEADERS.map(k=>group.words.find(w=>aliases[k].includes(headerName(w.text))));
  const known=hits.map((w,i)=>w?{i,w,x:center(w).x}:null).filter(Boolean);
  if(known.length<5)continue;
  const n=known.length,meanI=known.reduce((s,p)=>s+p.i,0)/n,meanX=known.reduce((s,p)=>s+p.x,0)/n;
  const step=known.reduce((s,p)=>s+(p.i-meanI)*(p.x-meanX),0)/known.reduce((s,p)=>s+(p.i-meanI)**2,0);
  const left=meanX-step*meanI;
  if(step<12||known.some(p=>Math.abs(p.x-left-step*p.i)>step*.35))continue;
  if(best&&best.score>=n)continue;
  best={score:n,y:known.reduce((s,p)=>s+center(p.w).y,0)/n,bottom:Math.max(...known.map(p=>p.w.bbox.y1)),x:HEADERS.map((_,i)=>hits[i]?center(hits[i]).x:left+step*i)};
 }
 return best;
}
const time=value=>{
 const compact=String(value).toUpperCase().replace(/\s/g,'').replace(/[OQ]/g,'0').replace(/[IL|]/g,'1');
 const match=compact.match(/^(\d{1,2})[:.,;](\d{2})$/);
 let hour,minute;
 if(match){hour=Number(match[1]);minute=Number(match[2])}
 else{
  const digits=compact.replace(/\D/g,'');
  if(digits.length!==3&&digits.length!==4)return '';
  hour=Number(digits.slice(0,-2));minute=Number(digits.slice(-2));
 }
 return hour<=47&&minute<60?String(hour).padStart(2,'0')+':'+String(minute).padStart(2,'0'):'';
};
export function interpretSchedule(data,imageSize={},overrides={}){
 const words=(data.words||data.blocks?.flatMap(b=>b.paragraphs?.flatMap(p=>p.lines?.flatMap(l=>l.words||[])||[])||[])||[]).filter(w=>w.text?.trim()&&w.bbox);
 if(!words.length)throw Error('No s’han reconegut les columnes. Prova una captura més nítida.');
 const groups=[];
 for(const w of [...words].sort((a,b)=>(a.bbox.y0+a.bbox.y1)-(b.bbox.y0+b.bbox.y1))){
  const cy=(w.bbox.y0+w.bbox.y1)/2,h=w.bbox.y1-w.bbox.y0;
  let row=groups.find(g=>Math.abs(g.y-cy)<Math.max(8,Math.min(g.h,h)*.5));
  if(!row){row={y:cy,h,words:[]};groups.push(row)}row.words.push(w);
 }
 const recognizedLines=(data.lines||data.blocks?.flatMap(b=>b.paragraphs?.flatMap(p=>p.lines||[])||[])||[]).filter(l=>l.words?.length).map(l=>lineGroup(l.words.filter(w=>w.text?.trim()&&w.bbox))).filter(l=>l.words.length);
 const header=findHeader(words,[...recognizedLines,...groups]);
 if(!header)throw Error('No he pogut llegir la capçalera de la foto. Prova una foto més frontal i sense reflexos, o una captura de pantalla.');
 const width=Number(imageSize.width)||Math.max(...words.map(w=>w.bbox.x1));
 const height=Number(imageSize.height)||Math.max(...words.map(w=>w.bbox.y1));
 const columnFor=x=>header.x.reduce((best,v,i)=>Math.abs(x-v)<Math.abs(x-header.x[best])?i:best,0);
 const tableLines=recognizedLines.filter(g=>g.y>header.bottom&&new Set(g.words.map(w=>columnFor(center(w).x))).size>=3&&g.words.some(w=>[0,4].includes(columnFor(center(w).x))&&time(w.text)));
 // Tesseract's line segmentation follows a sloping photograph; equal Y bands do not.
 const bodyGroups=tableLines.length>=2?tableLines:groups.filter(g=>g.y>header.bottom);
 const result=[],issues=[],rowGeometry=[],repairs=[],cellBounds=[],timings=[];
 for(const g of bodyGroups){
  const cells=HEADERS.map(()=>[]);
  for(const w of g.words){const x=(w.bbox.x0+w.bbox.x1)/2;let i=0;for(let j=1;j<7;j++)if(Math.abs(x-header.x[j])<Math.abs(x-header.x[i]))i=j;cells[i].push(w)}
  const c=cells.map((ws,col)=>overrides[result.length]?.[col]??ws.sort((a,b)=>a.bbox.x0-b.bbox.x0).map(w=>w.text).join(''));
  const start=time(c[0]),end=time(c[4]);
  if(!start&&!end)continue;
  const from=number(c[1]),to=number(c[5]),te=normalize(c[3]),via=number(c[2]),out=normalize(c[6]);
  let type='',train='';
  if(te==='TRA')type='TRA';
  else if(/^\d{1,2}$/.test(te)&&+te>0){type='CON';train=String(400+Number(te))}
  else if(/^4(?:0[1-9]|[1-9]\d)$/.test(te)){type='CON';train=te}
  else if(!te&&from===to&&!via&&!out)type='EST';
  const line=[start,end,type,from,to,train,via,out].join('|');
  const validStations=/^4\d{2}$/.test(from)&&+from>=413&&+from<=434&&/^4\d{2}$/.test(to)&&+to>=413&&+to<=434;
  if(!start||!end||!type||!validStations||(type==='CON'&&(!['1','2'].includes(via)||!['1','2','RET'].includes(out))))issues.push('Fila '+(result.length+1)+': revisa hores, estacions, tren o vies.');
  if(cells.flat().some(w=>Number(w.confidence)<70))issues.push('Fila '+(result.length+1)+': lectura de baixa confiança.');
  const valid=[!!start,/^4(1[3-9]|2\d|3[0-4])$/.test(from),type!=='CON'||['1','2'].includes(via),!!type,!!end,/^4(1[3-9]|2\d|3[0-4])$/.test(to),type!=='CON'||['1','2','RET'].includes(out)];
  const boxes=[];
  for(let col=0;col<7;col++){
   const ws=cells[col],step=col?header.x[col]-header.x[col-1]:header.x[1]-header.x[0];
   const x0=ws.length?Math.min(...ws.map(w=>w.bbox.x0)):header.x[col]-step*.42;
   const x1=ws.length?Math.max(...ws.map(w=>w.bbox.x1)):header.x[col]+step*.42;
   const y0=ws.length?Math.min(...ws.map(w=>w.bbox.y0)):g.top??g.y-g.h/2;
   const y1=ws.length?Math.max(...ws.map(w=>w.bbox.y1)):g.bottom??g.y+g.h/2;
   const box={row:result.length,col,rect:{x:Math.max(0,x0-5),y:Math.max(0,y0-5),width:Math.min(width,x1+5)-Math.max(0,x0-5),height:Math.min(height,y1+5)-Math.max(0,y0-5)},whitelist:[0,4].includes(col)?'0123456789:':col===3?'0123456789TRA':col===6?'12RET':'0123456789'};
   boxes.push(box);if(!valid[col])repairs.push(box);
  }
  cellBounds.push(boxes);timings.push({start,end});
  rowGeometry.push({center:g.y,height:g.h});
  result.push(line);
 }
 if(!result.length)throw Error('No s’han pogut identificar files d’activitat.');
 const mins=s=>s?Number(s.slice(0,2))*60+Number(s.slice(3)):null;
 for(let i=0;i<timings.length;i++){
  const a=mins(timings[i].start),b=mins(timings[i].end),previous=i?mins(timings[i-1].end):null;
  if(a!==null&&b!==null&&b<=a)repairs.push(cellBounds[i][0],cellBounds[i][4]);
  if(a!==null&&previous!==null&&a<previous)repairs.push(cellBounds[i-1][4],cellBounds[i][0]);
 }
 const text=data.text||'';
 const months={enero:1,febrero:2,marzo:3,abril:4,mayo:5,junio:6,julio:7,agosto:8,septiembre:9,octubre:10,noviembre:11,diciembre:12};
 const dateMatch=text.toLowerCase().match(/(\d{1,2})\s+de\s+([a-z]+)\s+de\s+(\d{4})/);
 const date=dateMatch&&months[dateMatch[2]]?dateMatch[3]+'-'+String(months[dateMatch[2]]).padStart(2,'0')+'-'+dateMatch[1].padStart(2,'0'):null;
 const columnStep=header.x[1]-header.x[0];
 const rowLeft=Math.max(0,header.x[0]-columnStep/2);
 const rowRight=Math.min(width,header.x[6]+(header.x[6]-header.x[5])/2);
 const rowBounds=rowGeometry.map((row,i)=>{
  const previous=rowGeometry[i-1],next=rowGeometry[i+1];
  const top=previous?(previous.center+row.center)/2:next?row.center-(next.center-row.center)/2:row.center-row.height;
  const bottom=next?(row.center+next.center)/2:previous?row.center+(row.center-previous.center)/2:row.center+row.height;
  const y=Math.max(0,Math.floor(top)),end=Math.min(height,Math.ceil(bottom));
  return {x:Math.round(rowLeft),y,width:Math.max(1,Math.round(rowRight-rowLeft)),height:Math.max(1,end-y)};
 });
 return {text:result.join('\n'),issues:[...new Set(issues)],count:result.length,date,rowBounds,imageSize:{width,height},repairs:[...new Map(repairs.map(r=>[r.row+':'+r.col,r])).values()]};
}

