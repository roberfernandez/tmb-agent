import {
  CQUADRE_URL, AVISOS_URL,
  getComplements, getAvisos, markComplementRead, markAvisoRead,
} from './cquadre.js?v=cquadre-manual-v1';

function node(tag, className = '', text = '') {
  const element = document.createElement(tag);
  if (className) element.className = className;
  if (text) element.textContent = text;
  return element;
}
function formatDate(value) {
  if (!value) return '';
  const [year, month, day] = value.split('-').map(Number);
  if (!year || !month || !day) return value;
  return new Intl.DateTimeFormat('ca-ES', {
    day: '2-digit', month: '2-digit', year: 'numeric',
  }).format(new Date(year, month - 1, day));
}
export function renderPendingSection({
  kind, title, items, markReadItem, onUnreadChange, allUrl, allLabel,
}) {
  const section = node('section', 'pending-section');
  const heading = node('div', 'pending-section-heading');
  heading.append(node('h2', '', title));
  const summary = node('p', 'cquadre-summary');
  summary.setAttribute('aria-live', 'polite');
  heading.append(summary);
  section.append(heading);

  const toolbar = node('div', 'cquadre-toolbar');
  const markSelected = node('button', 'button cquadre-mark-selected', 'Marca com a llegits');
  markSelected.type = 'button';
  toolbar.append(markSelected);

  const status = node('p', 'cquadre-status');
  status.setAttribute('role', 'status');
  const list = node('div', 'cquadre-list');
  section.append(toolbar, status, list);

  const rows = new Map();
  const selected = new Set();
  let busy = false;
  const pendingReads = new Map();

  function refresh(notify = false) {
    const remaining = rows.size;
    summary.className = remaining ? 'cquadre-summary' : 'cquadre-summary cquadre-ok';
    summary.textContent = remaining
      ? `${remaining} pendent${remaining === 1 ? '' : 's'} de llegir`
      : '✓ Estàs al dia. No tens pendents.';

    toolbar.hidden = remaining === 0 || selected.size === 0;
    markSelected.disabled = busy || selected.size === 0;

    for (const [id, entry] of rows) {
      const chosen = selected.has(id);
      entry.action.textContent = chosen ? '☑' : '☐';
      entry.action.disabled = busy;
      entry.action.setAttribute('aria-label',
        `${chosen ? 'Desselecciona' : 'Selecciona'}: ${entry.title}`);
      entry.action.setAttribute('aria-pressed', chosen ? 'true' : 'false');
      entry.row.classList.toggle('cquadre-selected', chosen);
    }

    if (notify) onUnreadChange(kind, remaining);
  }

  function removeRead(id) {
    const entry = rows.get(id);
    if (!entry) return;
    entry.row.remove();
    rows.delete(id);
    selected.delete(id);
    refresh(true);
  }

  function saveRead(id) {
    if (pendingReads.has(id)) return pendingReads.get(id);
    if (!rows.has(id)) return Promise.resolve();
    const saving = Promise.resolve()
      .then(() => markReadItem(Number(id)))
      .then(() => removeRead(id))
      .finally(() => pendingReads.delete(id));
    pendingReads.set(id, saving);
    return saving;
  }

  async function readOnOpen(id) {
    status.textContent = '';
    try {
      await saveRead(id);
    } catch {
      status.textContent = 'No s’ha pogut marcar com a llegit. Torna-ho a provar.';
    }
  }

  markSelected.addEventListener('click', async () => {
    if (busy || !selected.size) return;
    const ids = [...selected];
    status.textContent = '';
    busy = true;
    refresh();

    let failed = 0;
    for (const id of ids) {
      try {
        await saveRead(id);
      } catch {
        failed += 1;
      }
    }

    busy = false;
    if (failed) {
      status.textContent = `No s’han pogut marcar ${failed} element${failed === 1 ? '' : 's'}. Torna-ho a provar.`;
    }
    refresh();
  });

  items.filter(item => !item.llegit).forEach(item => {
    const id = String(item.id);
    const row = node('div', 'cquadre-item');

    const action = node('button', 'cquadre-left-action', '☐');
    action.type = 'button';
    action.addEventListener('click', () => {
      if (busy) return;
      if (selected.has(id)) selected.delete(id);
      else selected.add(id);
      status.textContent = '';
      refresh();
    });

    const link = node('a', 'cquadre-link');
    link.href = item.enlace;
    link.target = '_blank';
    link.rel = 'noopener noreferrer';
    link.addEventListener('click', () => readOnOpen(id));
    link.addEventListener('auxclick', event => {
      if (event.button === 1) return readOnOpen(id);
    });
    link.setAttribute('aria-label', `Visualitza document: ${item.titulo}`);

    const copy = node('span', 'cquadre-item-copy');
    copy.append(node('strong', '', item.titulo));
    const meta = [];
    if (item.fecha) meta.push(formatDate(item.fecha));
    if (kind === 'avisos' && item.interessa) meta.push(item.interessa);
    if (meta.length) copy.append(node('span', 'cquadre-date', meta.join(' · ')));
    link.append(copy, node('span', 'cquadre-arrow', '↗'));

    row.append(action, link);
    list.append(row);
    rows.set(id, { row, action, title: item.titulo });
  });

  refresh();

  const all = node('a', 'button cquadre-all', allLabel);
  all.href = allUrl;
  all.target = '_blank';
  all.rel = 'noopener noreferrer';
  section.append(all);
  return section;
}

export async function renderCQuadre(onUnreadChange = () => {}, view = 'all') {
  const container = node('section', 'cquadre');
  container.append(node('p', 'notice', 'Carregant C.Quadre i Avisos…'));
  try {
    const [complements, avisos] = await Promise.all([
      view === 'avisos' ? Promise.resolve([]) : getComplements(),
      view === 'cquadre' ? Promise.resolve([]) : getAvisos(),
    ]);
    container.replaceChildren();
    if (view !== 'avisos') container.append(renderPendingSection({
      kind: 'cquadre', title: 'C.Quadre', items: complements,
      markReadItem: markComplementRead, onUnreadChange,
      allUrl: CQUADRE_URL, allLabel: 'Veure tots els Complements de Quadre ↗',
    }));
    if (view !== 'cquadre') container.append(renderPendingSection({
      kind: 'avisos', title: 'Avisos', items: avisos,
      markReadItem: markAvisoRead, onUnreadChange,
      allUrl: AVISOS_URL, allLabel: 'Veure tots els Avisos ↗',
    }));
  } catch {
    container.replaceChildren(node('p', 'notice',
      'No s’han pogut carregar els Complements de Quadre i Avisos.'));
  }
  return container;
}
