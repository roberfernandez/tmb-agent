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
export function excerpt(text, query, length = 520) {
  const first = query.split('|').map(normalize).filter(Boolean).map(q => q.split(' ')[0]);
  const plain = normalize(text);
  const positions = first.map(word => plain.indexOf(word)).filter(i => i >= 0);
  // Normalization may alter offsets: the excerpt is only a reading aid; the PDF is authoritative.
  const start = Math.max(0, (positions.length ? Math.min(...positions) : 0) - 100);
  return `${start ? '…' : ''}${text.slice(start, start + length)}${start + length < text.length ? '…' : ''}`;
}
export function pageUrl(doc, page) {
  const base = typeof document !== 'undefined' ? document.baseURI : 'https://roberfernandez.github.io/tmb-agent/';
  const url = new URL(doc.fileUrl || doc.url, base); url.hash = `page=${page}`; return url.href;
}
