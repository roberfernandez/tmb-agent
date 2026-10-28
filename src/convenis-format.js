// Layout ranges always refer to the untouched source text used by search.
export function documentBlocks(text) {
  if (!text) return [];
  const boundaries = new Map([[0, 'paragraph']]);
  const add = (offset, type = 'paragraph') => {
    if (offset >= 0 && offset < text.length && (type !== 'paragraph' || !boundaries.has(offset))) boundaries.set(offset, type);
  };
  const headingRanges = [];
  const insideHeading = offset => headingRanges.some(([start,end]) => offset >= start && offset < end);
  const bodyStart = /\s+(?=(?:[LSD][’']|(?:Les|Las|Los|Els|El|La|Aquest|Aquesta|Aquests|Aquestes|Este|Esta|Estos|Estas|En|Es|Se|Són|Son|Per|Para|Quan|Cuando|Tots|Totes|Todos|Todas|De conformitat|De conformidad|Durant|Durante|Amb|Con|A partir|A la|A data|A fecha|Cada|Queda|Queden|Quedan|Serà|Será|E1)(?![\p{L}])))/gu;
  function headingEnd(start, markerEnd, limit) {
    const tail = text.slice(markerEnd, limit);
    const listStart = /\s+(?=(?:\d{1,2}[.)]\s+|[•●▪\uF0B7\uF02D]\s+))/u.exec(tail);
    if (listStart && listStart.index < 140 && !/[.!?;]/u.test(tail.slice(0,listStart.index))) return markerEnd + listStart.index + listStart[0].length;
    // Old agreements often separate the title with ".-".
    const explicit = /\.-\s+(?=\p{Lu})/u.exec(tail);
    if (explicit && explicit.index < 160) return markerEnd + explicit.index + explicit[0].length;
    bodyStart.lastIndex = 0;
    const body = bodyStart.exec(tail);
    if (body && body.index > 1 && body.index < 180) return markerEnd + body.index + body[0].length;
    // All-capital titles end where sentence case resumes.
    const words = [...tail.matchAll(/\S+/gu)];
    let capitals = 0;
    for (const word of words) {
      const letters = word[0].replace(/[^\p{L}]/gu, '');
      if (letters.length > 1 && letters === letters.toUpperCase()) capitals++;
      else if (letters && capitals >= 2) return markerEnd + word.index;
      else if (letters) break;
    }
    // If the title cannot be recovered confidently, emphasize only its label.
    if (tail.trim().length < 160 && !/[.!?;]/u.test(tail)) return limit;
    return markerEnd;
  }
  const headings = /(?<!\S)(?:(?:Article|ARTICLE|Artículo|ARTÍCULO|Articulo|ARTICULO|Art\.)\s+(?:\d+(?:\s*(?:bis|ter))?|PRIMERO|SEGUNDO|TERCERO|CUARTO|QUINTO|SEXTO|SÉPTIMO|OCTAVO|NOVENO|DÉCIMO)\b[.ºª:\-–]*\s*|(?:Capítol|CAPÍTOL|Capítulo|CAPÍTULO|CAPITULO|Capitulo|TÍTOL|TÍTULO|SECCIÓ|SECCIÓN)\s+[\dIVXL|]+[.\-–]*\s*)/gu;
  const found = [...text.matchAll(headings)];
  for (let i = 0; i < found.length; i++) {
    const match = found[i], start = match.index, markerEnd = start + match[0].length;
    const limit = found[i + 1]?.index ?? text.length;
    add(start, /^(?:Cap|CAP|TÍT|SECC)/u.test(match[0]) ? 'chapter' : 'heading');
    const end = headingEnd(start, markerEnd, limit);
    headingRanges.push([start, end]);
    add(end);
  }
  // Named agreement clauses retain their literal numbering and punctuation.
  for (const m of text.matchAll(/(?<!\S)(?:PRIMERO|SEGUNDO|TERCERO|CUARTO|QUINTO|SEXTO|SÉPTIMO|OCTAVO|NOVENO|DÉCIMO|Primer|Primero|Segon|Segundo|Tercer|Tercero)[.:](?:-)?\s*/gu)) {
    if (insideHeading(m.index)) continue;
    add(m.index, 'heading');
    add(m.index + m[0].length);
  }
  for (const m of text.matchAll(/(?<!\S)(?:ACUERDAN|ACORDEN|ACUERDOS|MANIFIESTAN|EXPOSEN|RESOLC|Resolc):?\s+/gu)) {
    add(m.index, 'heading'); add(m.index + m[0].length);
  }
  // Numbered section titles (plans and agreements): separate only short labels
  // followed by a recognizable sentence start, not numbered prose or figures.
  for (const m of text.matchAll(/(?<!\S)(?:\d{1,2}\.|\d{1,2}(?:\.\d{1,2})+\.?)\s+(?=\p{Lu})/gu)) {
    if (insideHeading(m.index)) continue;
    const tail = text.slice(m.index + m[0].length, m.index + m[0].length + 160);
    if (/^(?:Les|Las|El|La|Els|Los|Es|Se|Un|Una|Disposar)(?![\p{L}])/u.test(tail)) continue;
    const split = /\s+(?=(?:[LSD][’']|Barcelona,|Acció positiva:|(?:Les|Las|El|La|Els|Los|Aquest|Aquesta|Es|Se|Per|Para)(?![\p{L}])))/u.exec(tail);
    if (split && split.index > 1 && split.index < 80 && !/[.;:!?]/u.test(tail.slice(0,split.index))) {
      const end = m.index + m[0].length + split.index + split[0].length;
      add(m.index, 'heading'); add(end); headingRanges.push([m.index,end]);
    }
  }
  // Explicit bullets and enumerations only; do not interpret dates or amounts.
  for (const m of text.matchAll(/(?<!\S)(?:[•●▪\uF0B7\uF02D]\s+|[-–]\s+(?=\p{L})|[a-z]\)\s+|\d{1,2}(?:\.\d{1,2})*\.-\s*|\d{1,2}[.)]\s+(?=\p{Lu}))/gu)) {
    const prefix = text.slice(0,m.index).trimEnd();
    if (/^\d/u.test(m[0]) && prefix && /[\p{L}\p{N}]/u.test(prefix.at(-1))) continue;
    if (!insideHeading(m.index) && !boundaries.has(m.index)) add(m.index, 'item');
  }
  for (const m of text.matchAll(/\n\s*\n\s*/gu)) add(m.index + m[0].length);
  // Reflow long flat passages at sentence endings, never inside source text.
  const initial = [...boundaries.keys()].sort((a,b) => a-b);
  for (let i = 0; i < initial.length; i++) {
    let start = initial[i];
    if (['heading', 'chapter'].includes(boundaries.get(start))) continue;
    const end = initial[i+1] ?? text.length;
    const passage = text.slice(start, end);
    for (const m of passage.matchAll(/[.!?;]\s+(?=[\p{Lu}¿¡])/gu)) {
      const offset = initial[i] + m.index + m[0].length;
      const before = text.slice(Math.max(start, offset - 18), offset).trim();
      if (offset-start >= 260 && !/(?:\b(?:Art|Sr|Sra|Dr|núm|pàg|Ptas)|S\.A|S\.A\.|S\.P\.M)\.$/iu.test(before)) {
        add(offset); start = offset;
      }
    }
  }
  const starts = [...boundaries.keys()].sort((a,b) => a-b);
  return starts.map((start,i) => ({ start, end: starts[i+1] ?? text.length, type: boundaries.get(start) }));
}
