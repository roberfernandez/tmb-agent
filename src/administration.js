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
export function renderAdministration() {
  const panel = node('section', ''); panel.className = 'panel';
  panel.append(node('h1', 'Administració'), node('p', 'Només activitat del launcher. Les obertures són clics d’accés; no acrediten ús ni presència dins les MiniApps independents. No es recullen continguts privats.'));
  const select = document.createElement('select');
  for (const days of [7,30,90]) { const option = node('option', `Últims ${days} dies`); option.value = days; select.append(option); }
  const refresh = node('button', 'Actualitzar'); refresh.className = 'button';
  const result = node('div', ''); result.style.overflowX = 'auto';
  let generation = 0;
  const table = (headers, rows) => { const t = document.createElement('table'); t.style.width = '100%'; const head = document.createElement('tr'); headers.forEach(h => head.append(node('th',h))); const thead = document.createElement('thead'); thead.append(head); t.append(thead); const body = document.createElement('tbody'); rows.forEach(row => { const tr = document.createElement('tr'); row.forEach(v => { const td = node('td',v ?? '—'); td.style.padding = '8px'; tr.append(td); }); body.append(tr); }); t.append(body); return t; };
  const date = value => value ? new Date(value).toLocaleString('ca-ES') : '—';
  async function load() {
    const current = ++generation;
    result.replaceChildren(node('p','Carregant…'));
    try {
      const data = await callAdmin({ action:'dashboard', days:Number(select.value) });
      if (current !== generation || !panel.isConnected) return;
      result.replaceChildren(node('p',`Verificat: ${date(data.server_time)}. «Online al launcher» = heartbeat rebut en els últims 75 segons d’una pestanya visible. Pot trigar fins a 75 segons a detectar una desconnexió. Dades d’obertures: 90 dies; presència: 1 dia. Actualitza per renovar la verificació.`),
        node('h2','Usuaris'), table(['Correu','Alta','Autorització','Últim login','Presència al launcher','Últim heartbeat'], data.users.map(u=>[u.email,date(u.created_at),u.authorization,date(u.last_sign_in_at),u.online ? 'Online al launcher (heartbeat recent)' : 'Sense presència recent',date(u.last_seen)])),
        node('h2','Obertures des del launcher'), table(['MiniApp','Obertures','Usuaris únics'], data.stats.map(s=>[s.module,s.opens,s.unique_users])),
        node('h2','Últimes obertures (màxim 100)'), table(['Correu','MiniApp','Data'],data.events.map(e=>[e.email,e.module,date(e.opened_at)])));
    } catch (error) { if(current === generation) result.replaceChildren(node('p',error.message)); }
  }
  select.addEventListener('change',load); refresh.addEventListener('click',load);
  panel.append(select,refresh,result); queueMicrotask(load); return panel;
}
