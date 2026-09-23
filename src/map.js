// Visor de la imatge original: zoom sense retallar ni modificar el fitxer.
export function renderMap() {
  const section = document.createElement('section');
  section.className = 'map-panel';
  const title = document.createElement('h1'); title.textContent = 'Mapa Metro';
  const toolbar = document.createElement('div'); toolbar.className = 'map-toolbar';
  toolbar.setAttribute('role', 'group'); toolbar.setAttribute('aria-label', 'Ampliació del mapa');
  const viewport = document.createElement('div'); viewport.className = 'map-viewport';
  viewport.tabIndex = 0; viewport.setAttribute('role', 'region'); viewport.setAttribute('aria-label', 'Mapa ampliable; desplaça’t per veure’n els detalls');
  const img = document.createElement('img');
  img.src = './assets/mapa-metro-barcelona-2026.png';
  img.alt = 'Mapa del metro de Barcelona, gener de 2026'; img.draggable = false;
  const value = document.createElement('output'); value.setAttribute('aria-live', 'polite');
  let zoom = 1;
  function setZoom(next) {
    const before = zoom;
    zoom = Math.min(8, Math.max(1, next));
    const x = (viewport.scrollLeft + viewport.clientWidth / 2) / before;
    const y = (viewport.scrollTop + viewport.clientHeight / 2) / before;
    img.style.width = `${zoom * 100}%`;
    value.textContent = `${Math.round(zoom * 100)} %`;
    minus.disabled = zoom === 1; plus.disabled = zoom === 8;
    viewport.scrollLeft = x * zoom - viewport.clientWidth / 2;
    viewport.scrollTop = y * zoom - viewport.clientHeight / 2;
  }
  function button(text, label, action) {
    const b = document.createElement('button'); b.type = 'button'; b.textContent = text;
    b.setAttribute('aria-label', label); b.addEventListener('click', action); return b;
  }
  const minus = button('−', 'Redueix el mapa', () => setZoom(zoom / 1.5));
  const plus = button('+', 'Amplia el mapa', () => setZoom(zoom * 1.5));
  const fit = button('Ajusta', 'Ajusta el mapa a l’amplada', () => setZoom(1));
  toolbar.append(minus, value, plus, fit);
  viewport.addEventListener('keydown', event => {
    if (event.key === '+' || event.key === '=') { event.preventDefault(); setZoom(zoom * 1.5); }
    if (event.key === '-') { event.preventDefault(); setZoom(zoom / 1.5); }
  });
  viewport.append(img);
  const hint = document.createElement('p'); hint.className = 'notice';
  hint.textContent = 'Amplia amb + i desplaça el mapa per consultar les estacions.';
  const original = document.createElement('a'); original.className = 'back';
  original.href = img.src; original.target = '_blank'; original.rel = 'noopener noreferrer';
  original.textContent = 'Obre la imatge original ↗';
  section.append(title, toolbar, hint, viewport, original); setZoom(1);
  return section;
}
