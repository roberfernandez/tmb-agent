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
  const modeButton = node('button', 'cquadre-mode-toggle', 'Selecció múltiple');
  modeButton.type = 'button';
  const bulkTools = node('div', 'cquadre-bulk-tools');
  const selectAll = node('button', 'cquadre-select-all', 'Selecciona tots');
  selectAll.type = 'button';
  const markSelected = node('button', 'button cquadre-mark-selected', 'Marca seleccionats');
  markSelected.type = 'button';
  bulkTools.append(selectAll, markSelected);
  toolbar.append(modeButton, bulkTools);
  const status = node('p', 'cquadre-status');
  status.setAttribute('role', 'status');
  const list = node('div', 'cquadre-list');
  section.append(toolbar, status, list);

  const rows = new Map(), selected = new Set();
  let busy = false, multiple = false;

  function refresh(notify = false) {
    const remaining = rows.size;
    summary.className = remaining ? 'cquadre-summary' : 'cquadre-summary cquadre-ok';
    summary.textContent = remaining
      ? `${remaining} pendent${remaining === 1 ? '' : 's'} de llegir`
      : '✓ Estàs al dia. No tens pendents.';
    toolbar.hidden = remaining === 0;
    modeButton.textContent = multiple ? 'Cancel·la la selecció' : 'Selecció múltiple';
    modeButton.disabled = busy || remaining === 0;
    bulkTools.hidden = !multiple;
    selectAll.textContent = selected.size === remaining && remaining > 0
      ? 'Desmarca tots' : 'Selecciona tots';
    selectAll.disabled = busy || !remaining;
    markSelected.textContent = selected.size
      ? `Marca seleccionats (${selected.size})` : 'Marca seleccionats';
    markSelected.disabled = busy || selected.size === 0;
    for (const [id, entry] of rows) {
      const chosen = multiple && selected.has(id);
      entry.action.textContent = multiple ? (chosen ? '☑' : '☐') : '✓';
      entry.action.disabled = busy;
      entry.action.setAttribute('aria-label', multiple
        ? `${chosen ? 'Desselecciona' : 'Selecciona'}: ${entry.title}`
        : `Marca com a llegit: ${entry.title}`);
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
    if (!rows.size) multiple = false;
    refresh(true);
  }
  async function readOne(id) {
    if (busy || !rows.has(id)) return;
    status.textContent = '';
    busy = true;
    refresh();
    try {
      await markReadItem(Number(id));
      removeRead(id);
    } catch {
      status.textContent = 'No s’ha pogut marcar com a llegit. Torna-ho a provar.';
    } finally {
      busy = false;
      refresh();
    }
  }
  modeButton.addEventListener('click', () => {
    if (busy) return;
    multiple = !multiple;
    selected.clear();
    status.textContent = multiple ? 'Selecciona els elements i prem «Marca seleccionats».' : '';
    refresh();
  });
  selectAll.addEventListener('click', () => {
    if (busy || !multiple) return;
    const clear = selected.size === rows.size;
    selected.clear();
    if (!clear) for (const id of rows.keys()) selected.add(id);
    refresh();
  });
  markSelected.addEventListener('click', async () => {
    if (busy || !multiple || !selected.size) return;
    const ids = [...selected];
    status.textContent = '';
    busy = true;
    refresh();
    let failed = 0;
    for (const id of ids) {
      try {
        await markReadItem(Number(id));
        removeRead(id);
      } catch {
        failed += 1;
      }
    }
    busy = false;
    if (!failed) {
      multiple = false;
      selected.clear();
    } else {
      status.textContent = `No s’han pogut marcar ${failed} element${failed === 1 ? '' : 's'}. Torna-ho a provar.`;
    }
    refresh();
  });

  items.filter(item => !item.llegit).forEach(item => {
    const id = String(item.id);
    const row = node('div', 'cquadre-item');
    const action = node('button', 'cquadre-left-action', '✓');
    action.type = 'button';
    action.addEventListener('click', async () => {
      if (busy) return;
      if (!multiple) return readOne(id);
      if (selected.has(id)) selected.delete(id);
      else selected.add(id);
      refresh();
    });

    const link = node('a', 'cquadre-link');
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
    link.addEventListener('click', () => { if (!multiple) void readOne(id); });
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
