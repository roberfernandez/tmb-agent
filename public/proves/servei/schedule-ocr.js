
const HEADERS=['HI','EI','VI','TE','HF','EF','VF'];
const normalize=s=>String(s).toUpperCase().replace(/[^A-Z0-9]/g,'');
const time=s=>{const m=String(s).replace(/\s/g,'').match(/^(\d{1,2})[:.](\d{2})$/);return m&&+m[1]<=47&&+m[2]<60?m[1].padStart(2,'0')+':'+m[2]:''};
export function interpretSchedule(data){
 const words=(data.words||data.blocks?.flatMap(b=>b.paragraphs?.flatMap(p=>p.lines?.flatMap(l=>l.words||[])||[])||[])||[]).filter(w=>w.text?.trim()&&w.bbox);
 if(!words.length)throw Error('No s’han reconegut les columnes. Prova una captura més nítida.');
 const groups=[];
 for(const w of [...words].sort((a,b)=>(a.bbox.y0+a.bbox.y1)-(b.bbox.y0+b.bbox.y1))){
  const cy=(w.bbox.y0+w.bbox.y1)/2,h=w.bbox.y1-w.bbox.y0;
  let row=groups.find(g=>Math.abs(g.y-cy)<Math.max(8,Math.min(g.h,h)*.5));
  if(!row){row={y:cy,h,words:[]};groups.push(row)}row.words.push(w);
 }
 let header=null;
 for(const g of groups){
  const aliases={HI:['HI','H1'],EI:['EI','EL','E1'],VI:['VI','VL','V1'],TE:['TE','TIE','T1E'],HF:['HF'],EF:['EF'],VF:['VF']};
  const hits=HEADERS.map(k=>g.words.find(w=>aliases[k].includes(normalize(w.text))));
  if(hits.every(Boolean)){header={y:g.y,x:hits.map(w=>(w.bbox.x0+w.bbox.x1)/2)};break}
 }
 if(!header)throw Error('No es veuen bé HI, EI, VI, T/E, HF, EF i VF. Inclou la capçalera sencera.');
 const result=[],issues=[];
 for(const g of groups.filter(g=>g.y>header.y+g.h)){
  const cells=HEADERS.map(()=>[]);
  for(const w of g.words){const x=(w.bbox.x0+w.bbox.x1)/2;let i=0;for(let j=1;j<7;j++)if(Math.abs(x-header.x[j])<Math.abs(x-header.x[i]))i=j;cells[i].push(w)}
  const c=cells.map(ws=>ws.sort((a,b)=>a.bbox.x0-b.bbox.x0).map(w=>w.text).join(''));
  const start=time(c[0]),end=time(c[4]);
  if(!start&&!end)continue;
  const from=c[1].trim(),to=c[5].trim(),te=normalize(c[3]),via=c[2].trim(),out=normalize(c[6]);
  let type='',train='';
  if(te==='TRA')type='TRA';
  else if(/^\d{1,2}$/.test(te)&&+te>0){type='CON';train=String(400+Number(te))}
  else if(/^4\d{2}$/.test(te)){type='CON';train=te}
  else if(!te&&from===to&&!via&&!out)type='EST';
  const line=[start,end,type,from,to,train,via,out].join('|');
  const validStations=/^4\d{2}$/.test(from)&&+from>=413&&+from<=434&&/^4\d{2}$/.test(to)&&+to>=413&&+to<=434;
  if(!start||!end||!type||!validStations||(type==='CON'&&(!['1','2'].includes(via)||!['1','2','RET'].includes(out))))issues.push('Fila '+(result.length+1)+': revisa hores, estacions, tren o vies.');
  if(cells.flat().some(w=>Number(w.confidence)<70))issues.push('Fila '+(result.length+1)+': lectura de baixa confiança.');
  result.push(line);
 }
 if(!result.length)throw Error('No s’han pogut identificar files d’activitat.');
 const text=data.text||'';
 const months={enero:1,febrero:2,marzo:3,abril:4,mayo:5,junio:6,julio:7,agosto:8,septiembre:9,octubre:10,noviembre:11,diciembre:12};
 const dateMatch=text.toLowerCase().match(/(\d{1,2})\s+de\s+([a-z]+)\s+de\s+(\d{4})/);
 const date=dateMatch&&months[dateMatch[2]]?dateMatch[3]+'-'+String(months[dateMatch[2]]).padStart(2,'0')+'-'+dateMatch[1].padStart(2,'0'):null;
 return {text:result.join('\n'),issues:[...new Set(issues)],count:result.length,date};
}
