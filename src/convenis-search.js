export const topics = [
  ['Jornada', 'jornada'], ['Vacances', 'vacances|vacaciones'],
  ['Descansos', 'descans|descanso'], ['Excedències', 'excedència|excedencia'],
  ['Promoció', 'promoció|promoción'], ['Jubilació parcial', 'jubilació parcial|jubilación parcial'],
  ['Permisos', 'permís|permiso|permisos'], ['Non Stop', 'non stop|nonstop'],
];
export function normalize(value) {
  return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim();
}
export function matchingPages(doc, query) {
  const alternatives = query.split('|').map(normalize).filter(Boolean);
  if (!alternatives.length) return [];
  return (doc.pages || []).filter(page => {
    const text = normalize(page.text);
    return alternatives.some(term => term.split(' ').every(word => text.includes(word)));
  });
}
export function highlightedParts(text, query) {
  const terms=[...new Set(query.split('|').flatMap(q=>normalize(q).split(' ')).filter(Boolean))];
  // Map normalized characters back to the original, including decomposed accents.
  let folded='';const offsets=[];let originalOffset=0;
  for(const char of text){
    if(/^\p{M}$/u.test(char)){if(offsets.length)offsets[offsets.length-1][1]=originalOffset+char.length;originalOffset+=char.length;continue;}
    const normalized=normalize(char)||' ';
    for(const part of normalized){folded+=part;offsets.push([originalOffset,originalOffset+char.length]);}
    originalOffset+=char.length;
  }
  const ranges=[];
  for(const term of terms){let index=0;while((index=folded.indexOf(term,index))!==-1){ranges.push([offsets[index][0],offsets[index+term.length-1][1]]);index+=term.length;}}
  ranges.sort((a,b)=>a[0]-b[0]);const merged=[];
  for(const range of ranges){const last=merged[merged.length-1];if(last&&range[0]<=last[1])last[1]=Math.max(last[1],range[1]);else merged.push([...range]);}
  const parts=[];let start=0;
  for(const [a,b] of merged){if(a>start)parts.push({text:text.slice(start,a),match:false});parts.push({text:text.slice(a,b),match:true});start=b;}
  if(start<text.length)parts.push({text:text.slice(start),match:false});return parts;
}
export function excerpt(text, query, length = 520) {
  const first = query.split('|').map(normalize).filter(Boolean).map(q => q.split(' ')[0]);
  const plain = text.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
  const positions = first.map(word => plain.indexOf(word)).filter(i => i >= 0);
  // Keep punctuation and whitespace so the snippet starts beside the actual match.
  const start = Math.max(0, (positions.length ? Math.min(...positions) : 0) - 100);
  return `${start ? '…' : ''}${text.slice(start, start + length)}${start + length < text.length ? '…' : ''}`;
}
export function pageUrl(doc, page) {
  const base = typeof document !== 'undefined' ? document.baseURI : 'https://roberfernandez.github.io/tmb-agent/';
  const url = new URL(doc.fileUrl || doc.url, base); url.hash = `page=${page}`; return url.href;
}
