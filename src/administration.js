import { modules } from './modules.js?v=administracio-v1';
import { SESSION_KEY } from './session.js';
const ENDPOINT = 'https://hhenkvendzengggrgook.supabase.co/functions/v1/tmb-agent-admin';
export async function callAdmin(payload, request = fetch, storage = localStorage) {
  const session = JSON.parse(storage.getItem(SESSION_KEY) || 'null');
  if (!session?.access_token) throw new Error('Sessió necessària');
  const response = await request(ENDPOINT, { method: 'POST', cache: 'no-store', keepalive: true,
    signal: AbortSignal.timeout(12000), headers: { apikey: 'sb_publishable_QSPDTmh3fd0FH-VvAjH5KQ_Rfvzr2x_', Authorization: `Bearer ${session.access_token}`, 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
  const data = await response.json();
  if (!response.ok || !data.ok) throw new Error(data.error || 'Servei no disponible');
  return data;
}
const tab = globalThis.crypto?.randomUUID();
export function launcherHeartbeat(visible) {
  return callAdmin({ action: visible ? 'heartbeat' : 'leave', tab }).catch(() => {});
}
export function recordOpen(module) { return callAdmin({ action: 'open', module }).catch(() => {}); }
const node = (tag, text) => { const n = document.createElement(tag); n.textContent = text; return n; };
let selectedDays = 7;
const moduleName = id => modules.find(module => module.id === id)?.name || id;
function table(headers, rows) {
  const result = node('table', ''); result.style.width = '100%';
  const head = node('tr', '');
  headers.forEach(label => head.append(node('th', label)));
  const thead = node('thead', ''); thead.append(head); result.append(thead);
  const body = node('tbody', '');
  for (const row of rows) {
    const tr = node('tr', '');
    for (const value of row) {
      const td = node('td', ''); td.style.padding = '10px 8px';
      if (value && typeof value === 'object') td.append(value);
      else td.textContent = value ?? '—';
      tr.append(td);
    }
    body.append(tr);
  }
  result.append(body); return result;
}
const date = value => value ? new Date(value).toLocaleString('ca-ES') : '—';
export function renderAdministration() {
  const panel = node('section', ''); panel.className = 'panel';
  panel.append(node('h1', 'Administració'), node('p', 'Només activitat del launcher. Les obertures són clics d’accés; no acrediten ús ni presència dins les MiniApps independents. No es recullen continguts privats.'));
  const select = document.createElement('select');
  select.setAttribute('aria-label', 'Període d’activitat');
  for (const days of [7,30,90]) {
    const option = node('option', `Últims ${days} dies`); option.value = days; select.append(option);
  }
  select.value = String(selectedDays);
  const refresh = node('button', 'Actualitzar'); refresh.className = 'button';
  const result = node('div', ''); result.style.overflowX = 'auto';
  let generation = 0;
  async function load() {
    const current = ++generation;
    result.replaceChildren(node('p','Carregant…'));
    try {
      const data = await callAdmin({ action:'dashboard', days:Number(select.value) });
      if (current !== generation || !panel.isConnected) return;
      const activity = data.activity || [];
      const summary = node('dl', '');
      Object.assign(summary.style, {display:'flex', flexWrap:'wrap', gap:'12px', margin:'24px 0'});
      for (const [label, value] of [
        ['Amb presència recent al launcher', data.users.filter(user => user.online).length],
        ['Obertures en el període', activity.reduce((total,user) => total + Number(user.opens),0)],
        ['Usuaris amb obertures en el període', activity.length],
      ]) {
        const card = node('div', '');
        Object.assign(card.style, {flex:'1 1 150px', padding:'16px', border:'1px solid #ffffff20', borderRadius:'14px', background:'#172436'});
        const count = node('dd', String(value));
        Object.assign(count.style, {fontSize:'32px', fontWeight:'700', margin:'8px 0 0'});
        card.append(node('dt',label),count); summary.append(card);
      }
      const rows = activity.map(user => {
        const detail = node('details', '');
        detail.append(node('summary', `${user.modules.length} MiniApps · Veure detall`));
        detail.append(table(['MiniApp','Obertures','Última obertura'],user.modules.map(module => [moduleName(module.module), module.opens, date(module.last_open)])));
        const events = data.events.filter(event => event.email === user.email);
        if (events.length) {
          const history = node('details','');
          history.append(node('summary','Hores de les últimes obertures disponibles'),node('p','Només registres inclosos en les 100 obertures més recents del període. Els totals de dalt inclouen tot el període.'),table(['MiniApp','Data'],events.map(event => [moduleName(event.module),date(event.opened_at)])));
          detail.append(history);
        }
        return [user.email, user.opens, detail, date(user.last_open)];
      });
      const presence = node('p', `Verificat: ${date(data.server_time)}. Presència recent = heartbeat rebut en els últims 75 segons d’una pestanya visible del launcher. Pot trigar fins a 75 segons a detectar una desconnexió. Actualitza per renovar la verificació.`);
      result.replaceChildren(summary, presence, node('h2','Activitat per usuari'),
        rows.length ? table(['Correu','Obertures','MiniApps','Última obertura'],rows) : node('p','Encara no hi ha obertures en aquest període.'),
        node('h2','Llista d’usuaris'), node('p','Dades d’accés i autorització. L’últim login correspon a l’inici de sessió, no a l’última activitat.'),
        table(['Correu','Alta','Autorització','Últim login','Presència al launcher','Últim heartbeat'], data.users.map(user=>[user.email,date(user.created_at),user.authorization,date(user.last_sign_in_at),user.online ? 'Presència recent al launcher' : 'Sense presència recent',date(user.last_seen)])),
        node('p','Retenció: obertures durant 90 dies; presència durant 1 dia.'));
    } catch (error) { if(current === generation) result.replaceChildren(node('p',error.message)); }
  }
  select.addEventListener('change',() => { selectedDays = Number(select.value); load(); });
  refresh.addEventListener('click',load);
  panel.append(select,refresh,result); queueMicrotask(load); return panel;
}
