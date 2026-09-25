import { SESSION_KEY } from './session.js';

const URL = 'https://hhenkvendzengggrgook.supabase.co';
const PUBLIC_KEY = 'sb_publishable_QSPDTmh3fd0FH-VvAjH5KQ_Rfvzr2x_';
export const CQUADRE_URL = 'https://tmbbcn.sharepoint.com/sites/CQuadre';

function session() {
  try {
    const value = JSON.parse(localStorage.getItem(SESSION_KEY));
    if (value?.access_token && value?.user?.id) return value;
  } catch {}
  return null;
}

function headers(accessToken, extra = {}) {
  return {
    apikey: PUBLIC_KEY,
    Authorization: `Bearer ${accessToken}`,
    ...extra,
  };
}

async function request(path, options = {}) {
  const current = session();
  if (!current) throw new Error('Sessió no disponible');

  const response = await fetch(`${URL}/rest/v1/${path}`, {
    ...options,
    cache: 'no-store',
    headers: headers(current.access_token, options.headers),
  });

  if (!response.ok) throw new Error(`Supabase ${response.status}`);
  if (response.status === 204) return null;
  return response.json();
}

export async function getComplements() {
  const current = session();
  if (!current) throw new Error('Sessió no disponible');

  const [catalog, readRows] = await Promise.all([
    request('complements_quadre?select=id,titulo,enlace,fecha&order=fecha.desc.nullslast,id.desc'),
    request(`complements_quadre_llegits?select=complement_id&user_id=eq.${encodeURIComponent(current.user.id)}`),
  ]);

  const read = new Set(readRows.map(row => Number(row.complement_id)));

  return catalog.map(item => ({
    ...item,
    llegit: read.has(Number(item.id)),
  }));
}

export async function getUnreadCount() {
  const complements = await getComplements();
  return complements.reduce((total, item) => total + (item.llegit ? 0 : 1), 0);
}

export async function markComplementRead(id) {
  const current = session();
  if (!current) throw new Error('Sessió no disponible');

  const response = await fetch(`${URL}/rest/v1/complements_quadre_llegits`, {
    method: 'POST',
    cache: 'no-store',
    headers: headers(current.access_token, {
      'Content-Type': 'application/json',
      Prefer: 'resolution=ignore-duplicates,return=minimal',
    }),
    body: JSON.stringify({
      user_id: current.user.id,
      complement_id: Number(id),
    }),
  });

  if (!response.ok) throw new Error(`Supabase ${response.status}`);
}

function node(tag, className, text) {
  const result = document.createElement(tag);
  if (className) result.className = className;
  if (text) result.textContent = text;
  return result;
}

function formatDate(value) {
  if (!value) return '';
  const [year, month, day] = value.split('-').map(Number);
  if (!year || !month || !day) return value;
  return new Intl.DateTimeFormat('ca-ES', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  }).format(new Date(year, month - 1, day));
}

export async function renderCQuadre(onUnreadChange = () => {}) {
  const container = node('section', 'cquadre');
  const loading = node('p', 'notice', 'Carregant complements…');
  container.append(loading);

  try {
    const complements = await getComplements();
    container.replaceChildren();

    const unread = complements.filter(item => !item.llegit);

    const summary = node(
      'p',
      unread.length ? 'cquadre-summary' : 'cquadre-summary cquadre-ok',
      unread.length
        ? `${unread.length} complement${unread.length === 1 ? '' : 's'} pendent${unread.length === 1 ? '' : 's'} de llegir`
        : '✓ Estàs al dia. No tens complements pendents.'
    );
    container.append(summary);

    const list = node('div', 'cquadre-list');

    unread.forEach(item => {
      const link = node('a', 'cquadre-item');
      link.href = item.enlace;
      link.target = '_blank';
      link.rel = 'noopener noreferrer';

      const copy = node('span', 'cquadre-item-copy');
      copy.append(node('strong', '', item.titulo));
      if (item.fecha) copy.append(node('span', 'cquadre-date', formatDate(item.fecha)));

      link.append(copy, node('span', 'cquadre-arrow', '↗'));

      link.addEventListener('click', async () => {
        link.classList.add('cquadre-reading');
        try {
          await markComplementRead(item.id);
          link.remove();
          const remaining = list.querySelectorAll('.cquadre-item').length;
          onUnreadChange(remaining);

          if (remaining === 0) {
            summary.className = 'cquadre-summary cquadre-ok';
            summary.textContent = '✓ Estàs al dia. No tens complements pendents.';
          } else {
            summary.className = 'cquadre-summary';
            summary.textContent =
              `${remaining} complement${remaining === 1 ? '' : 's'} ` +
              `pendent${remaining === 1 ? '' : 's'} de llegir`;
          }
        } catch {
          link.classList.remove('cquadre-reading');
        }
      });

      list.append(link);
    });

    container.append(list);

    const all = node('a', 'button cquadre-all', 'Veure tots els Complements de Quadre ↗');
    all.href = CQUADRE_URL;
    all.target = '_blank';
    all.rel = 'noopener noreferrer';
    container.append(all);
  } catch {
    container.replaceChildren(
      node('p', 'notice', 'No s’han pogut carregar els Complements de Quadre.')
    );
  }

  return container;
}
