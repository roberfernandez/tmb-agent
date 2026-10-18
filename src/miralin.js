export const stations = [
  ['413', 'La Pau'], ['417', 'Selva de Mar'],
  ['428', 'Joanic'], ['434', 'Trinitat Nova'],
];
const KEY = 'tmb-agent-miralin-origin-v1';
export function readOrigin(storage = localStorage) {
  try { const value = storage.getItem(KEY); return stations.some(([id]) => id === value) ? value : ''; }
  catch { return ''; }
}
export function originUrl(id) {
  if (!stations.some(([code]) => code === id)) throw new Error('Estació no vàlida');
  return `https://aplicacions.tmb.cat/miralin/routes/4/stops/${id}?dswid=4465`;
}
export function saveOrigin(id, storage = localStorage) {
  originUrl(id);
  try { storage.setItem(KEY, id); return storage.getItem(KEY) === id; } catch { return false; }
}
export function renderMiralinCard(module, image) {
  const card = document.createElement('div'); card.className = 'card card-miralin'; card.dataset.module = module.id;
  const visual = document.createElement('div'); visual.className = 'miralin-visual';
  const center = document.createElement('a'); center.className = 'miralin-center';
  const setCenter = () => { const id = readOrigin() || '413'; center.href = originUrl(id); center.setAttribute('aria-label', `Obrir Miralín · ${stations.find(([code]) => code === id)[1]}`); };
  center.target = '_blank'; center.rel = 'noopener noreferrer'; setCenter(); center.append(image);
  center.addEventListener('click', () => { const id = readOrigin() || '413'; saveOrigin(id); setCenter(); });
  visual.append(center);
  for (const [id, name] of stations) {
    const link = document.createElement('a'); link.className = `miralin-station miralin-station-${id}`;
    link.href = originUrl(id); link.target = '_blank'; link.rel = 'noopener noreferrer'; link.textContent = id;
    link.setAttribute('aria-label', `Obrir Miralín des de ${name} (${id})`); link.title = name;
    link.addEventListener('click', () => { saveOrigin(id); setCenter(); }); visual.append(link);
  }
  const copy = document.createElement('a'); copy.className = 'card-copy'; copy.target = '_blank'; copy.rel = 'noopener noreferrer';
  const title = document.createElement('h2'); title.textContent = module.name; copy.append(title);
  const syncTitle = () => { copy.href = originUrl(readOrigin() || '413'); }; syncTitle();
  copy.addEventListener('click', () => { saveOrigin(readOrigin() || '413'); syncTitle(); });
  for (const link of visual.querySelectorAll('a')) link.addEventListener('click', syncTitle);
  card.append(visual, copy); return card;
}
