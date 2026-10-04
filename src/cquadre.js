import { SESSION_KEY } from './session.js';

const URL = 'https://hhenkvendzengggrgook.supabase.co';
const PUBLIC_KEY = 'sb_publishable_QSPDTmh3fd0FH-VvAjH5KQ_Rfvzr2x_';

export const CQUADRE_URL = 'https://tmbbcn.sharepoint.com/sites/CQuadre';
export const AVISOS_URL = 'https://tmbbcn.sharepoint.com/sites/UltimaHora/Avisos/Forms/AllItems.aspx';

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

export async function getAvisos() {
  const current = session();
  if (!current) throw new Error('Sessió no disponible');

  const [catalog, readRows] = await Promise.all([
    request('avisos_tmb?select=id,titulo,enlace,fecha,fecha_iso,archivo,interessa&order=fecha_iso.desc.nullslast,id.desc'),
    request(`avisos_tmb_llegits?select=aviso_id&user_id=eq.${encodeURIComponent(current.user.id)}`),
  ]);

  const read = new Set(readRows.map(row => Number(row.aviso_id)));

  return catalog.map(item => ({
    ...item,
    llegit: read.has(Number(item.id)),
  }));
}

export async function getUnreadCounts() {
  const [complements, avisos] = await Promise.all([getComplements(), getAvisos()]);
  return {
    cquadre: complements.reduce((total, item) => total + (item.llegit ? 0 : 1), 0),
    avisos: avisos.reduce((total, item) => total + (item.llegit ? 0 : 1), 0),
  };
}

async function markRead(table, field, id) {
  const current = session();
  if (!current) throw new Error('Sessió no disponible');

  const response = await fetch(`${URL}/rest/v1/${table}`, {
    method: 'POST',
    cache: 'no-store',
    headers: headers(current.access_token, {
      'Content-Type': 'application/json',
      Prefer: 'resolution=ignore-duplicates,return=minimal',
    }),
    body: JSON.stringify({
      user_id: current.user.id,
      [field]: Number(id),
    }),
  });

  if (!response.ok) throw new Error(`Supabase ${response.status}`);
}

export function markComplementRead(id) {
  return markRead('complements_quadre_llegits', 'complement_id', id);
}

export function markAvisoRead(id) {
  return markRead('avisos_tmb_llegits', 'aviso_id', id);
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

function renderPendingSection({
  kind,
  title,
  items,
  markReadItem,
  onUnreadChange,
  allUrl,
  allLabel,
}) {
  const section = node('section', 'pending-section');
  const heading = node('div', 'pending-section-heading');
  heading.append(node('h2', '', title));

  const unread = items.filter(item => !item.llegit);
  const summary = node(
    'p',
    unread.length ? 'cquadre-summary' : 'cquadre-summary cquadre-ok',
    unread.length
      ? `${unread.length} pendent${unread.length === 1 ? '' : 's'} de llegir`
      : '✓ Estàs al dia. No tens pendents.'
  );
  heading.append(summary);
  section.append(heading);

  const list = node('div', 'cquadre-list');

  unread.forEach(item => {
    const link = node('a', 'cquadre-item');
    link.href = item.enlace;
    link.target = '_blank';
    link.rel = 'noopener noreferrer';

    const copy = node('span', 'cquadre-item-copy');
    copy.append(node('strong', '', item.titulo));

    const meta = [];
    if (item.fecha) meta.push(formatDate(item.fecha));
    if (kind === 'avisos' && item.interessa) meta.push(item.interessa);
    if (meta.length) copy.append(node('span', 'cquadre-date', meta.join(' · ')));

    link.append(copy, node('span', 'cquadre-arrow', '↗'));

    link.addEventListener('click', async () => {
      link.classList.add('cquadre-reading');
      try {
        await markReadItem(item.id);
        link.remove();
        const remaining = list.querySelectorAll('.cquadre-item').length;
        onUnreadChange(kind, remaining);

        if (remaining === 0) {
          summary.className = 'cquadre-summary cquadre-ok';
          summary.textContent = '✓ Estàs al dia. No tens pendents.';
        } else {
          summary.className = 'cquadre-summary';
          summary.textContent =
            `${remaining} pendent${remaining === 1 ? '' : 's'} de llegir`;
        }
      } catch {
        link.classList.remove('cquadre-reading');
      }
    });

    list.append(link);
  });

  section.append(list);

  const all = node('a', 'button cquadre-all', allLabel);
  all.href = allUrl;
  all.target = '_blank';
  all.rel = 'noopener noreferrer';
  section.append(all);

  return section;
}

export async function renderCQuadre(onUnreadChange = () => {}, view = 'all') {
  const container = node('section', 'cquadre');
  const loadingText = view === 'cquadre'
    ? 'Carregant C.Quadre…'
    : view === 'avisos'
      ? 'Carregant Avisos…'
      : 'Carregant C.Quadre i Avisos…';
  const loading = node('p', 'notice', loadingText);
  container.append(loading);

  try {
    const [complements, avisos] = await Promise.all([
      view === 'avisos' ? Promise.resolve([]) : getComplements(),
      view === 'cquadre' ? Promise.resolve([]) : getAvisos(),
    ]);
    container.replaceChildren();

    if (view !== 'avisos') {
      container.append(
        renderPendingSection({
          kind: 'cquadre',
          title: 'C.Quadre',
          items: complements,
          markReadItem: markComplementRead,
          onUnreadChange,
          allUrl: CQUADRE_URL,
          allLabel: 'Veure tots els Complements de Quadre ↗',
        }),
      );
    }

    if (view !== 'cquadre') {
      container.append(
        renderPendingSection({
          kind: 'avisos',
          title: 'Avisos',
          items: avisos,
          markReadItem: markAvisoRead,
          onUnreadChange,
          allUrl: AVISOS_URL,
          allLabel: 'Veure tots els Avisos ↗',
        }),
      );
    }
  } catch {
    const label = view === 'cquadre'
      ? 'C.Quadre'
      : view === 'avisos'
        ? 'Avisos'
        : 'C.Quadre i Avisos';
    container.replaceChildren(
      node('p', 'notice', `No s’han pogut carregar ${label}.`)
    );
  }

  return container;
}
