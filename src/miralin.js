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
export function renderMiralin() {
  const panel = document.createElement('section'); panel.className = 'panel'; panel.lang = 'ca';
  const title = document.createElement('h1'); title.textContent = 'Origen de Miralín';
  const text = document.createElement('p'); text.className = 'subtitle';
  text.textContent = 'Tria la teva estació base. Es recordarà en aquest dispositiu per a les properes obertures.';
  const form = document.createElement('form');
  const label = document.createElement('label'); label.textContent = 'Estació d’origen';
  const select = document.createElement('select'); select.required = true; select.style.cssText = 'display:block;width:100%;margin-top:12px;padding:14px;font:inherit;border-radius:12px';
  const placeholder = new Option('Selecciona una estació', ''); placeholder.disabled = true; select.append(placeholder);
  for (const [id, name] of stations) select.append(new Option(`${name} (${id})`, id));
  select.value = readOrigin(); label.append(select);
  const submit = document.createElement('button'); submit.type = 'submit'; submit.className = 'button'; submit.textContent = 'Desar i obrir Miralín ↗';
  const status = document.createElement('p'); status.className = 'notice'; status.setAttribute('role', 'status');
  form.append(label, submit, status);
  form.addEventListener('submit', event => {
    event.preventDefault(); if (!select.value) return;
    if (!saveOrigin(select.value)) { status.textContent = 'No s’ha pogut desar la preferència. Permet l’emmagatzematge del navegador i torna-ho a provar.'; return; }
    location.replace(originUrl(select.value));
  });
  const back = document.createElement('a'); back.href = '#/'; back.className = 'back'; back.textContent = '← Inici';
  panel.append(back, title, text, form); return panel;
}
