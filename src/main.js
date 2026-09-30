import { renderMap } from './map.js?v=ca-mapa-v1';
import { modules } from './modules.js?v=cquadre-acces-v2';
import { checkSession, loginUrl, SESSION_KEY } from './session.js';
import { getUnreadCount, renderCQuadre } from './cquadre.js';

const content = document.querySelector('main');
const MODULE_ORDER_KEY = 'tmb-agent-module-order-v1';

function orderedModules() {
  let saved = [];
  try {
    const parsed = JSON.parse(localStorage.getItem(MODULE_ORDER_KEY) || '[]');
    if (Array.isArray(parsed)) saved = parsed.filter(id => typeof id === 'string');
  } catch {}

  const byId = new Map(modules.map(module => [module.id, module]));
  const ordered = saved.map(id => byId.get(id)).filter(Boolean);
  const known = new Set(ordered.map(module => module.id));
  return [...ordered, ...modules.filter(module => !known.has(module.id))];
}

function saveModuleOrder(grid) {
  const order = [...grid.querySelectorAll('.card[data-module]')].map(card => card.dataset.module);
  localStorage.setItem(MODULE_ORDER_KEY, JSON.stringify(order));
}

function enableModuleReorder(grid) {
  let timer = 0;
  let active = null;
  let pointerId = null;
  let startX = 0;
  let startY = 0;
  let suppressClick = false;

  const clearTimer = () => {
    if (timer) window.clearTimeout(timer);
    timer = 0;
  };

  const finish = () => {
    clearTimer();
    if (!active) return;
    saveModuleOrder(grid);
    active.classList.remove('card-dragging');
    grid.classList.remove('reordering');
    active = null;
    pointerId = null;
    window.setTimeout(() => { suppressClick = false; }, 80);
  };

  grid.addEventListener('pointerdown', event => {
    if (event.button !== undefined && event.button !== 0) return;
    const card = event.target.closest('.card[data-module]');
    if (!card) return;

    clearTimer();
    active = null;
    pointerId = event.pointerId;
    startX = event.clientX;
    startY = event.clientY;

    timer = window.setTimeout(() => {
      active = card;
      suppressClick = true;
      grid.classList.add('reordering');
      card.classList.add('card-dragging');
      try { card.setPointerCapture(pointerId); } catch {}
      if (navigator.vibrate) navigator.vibrate(25);
    }, 500);
  });

  grid.addEventListener('pointermove', event => {
    if (event.pointerId !== pointerId) return;

    if (!active) {
      if (Math.hypot(event.clientX - startX, event.clientY - startY) > 10) {
        clearTimer();
        pointerId = null;
      }
      return;
    }

    event.preventDefault();
    const target = document.elementFromPoint(event.clientX, event.clientY)?.closest('.card[data-module]');
    if (!target || target === active || target.parentElement !== grid) return;

    const rect = target.getBoundingClientRect();
    const before = event.clientY < rect.top + rect.height / 2 ||
      (Math.abs(event.clientY - (rect.top + rect.height / 2)) < rect.height * 0.25 &&
       event.clientX < rect.left + rect.width / 2);

    grid.insertBefore(active, before ? target : target.nextSibling);
  });

  grid.addEventListener('pointerup', event => {
    if (event.pointerId !== pointerId) return;
    if (active) finish();
    else {
      clearTimer();
      pointerId = null;
    }
  });

  grid.addEventListener('pointercancel', () => {
    if (active) finish();
    else {
      clearTimer();
      pointerId = null;
    }
  });

  grid.addEventListener('click', event => {
    if (!suppressClick) return;
    event.preventDefault();
    event.stopPropagation();
  }, true);

  grid.addEventListener('dragstart', event => event.preventDefault());
}
function element(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text) node.textContent = text;
  return node;
}
function icon(module) {
  if (module.icon) {
    const img = element('img', 'module-icon');
    img.src = module.icon;
    img.alt = '';
    return img;
  }
  const placeholder = element('span', 'module-icon placeholder', module.initials);
  placeholder.setAttribute('aria-hidden', 'true');
  return placeholder;
}
function setCQuadreBadge(count) {
  const card = document.querySelector('[data-module="cquadre"]');
  if (!card) return;
  card.querySelector('.unread-badge')?.remove();
  if (count > 0) {
    const badge = element('span', 'unread-badge', count > 99 ? '99+' : String(count));
    badge.setAttribute('aria-label', `${count} complements pendents`);
    card.append(badge);
  }
}

async function refreshCQuadreBadge() {
  try { setCQuadreBadge(await getUnreadCount()); } catch {}
}

function render() {
  const route = location.hash.slice(1) || '/';
  const shortcutModule = modules.find(module => route === `/${module.id}`);
  const destination = shortcutModule?.appUrl || shortcutModule?.externalUrl;
  if (destination) { location.replace(destination); return; }
  document.body.classList.toggle('home', route === '/' || location.hash === '#content');
  content.replaceChildren();
  if (route === '/' || location.hash === '#content') {
    document.title = 'TMB Agent';
    const grid = element('nav', 'grid');
    grid.setAttribute('aria-label', 'Mòduls');
    orderedModules().forEach(module => {
      const card = element('a', 'card');
      card.dataset.module = module.id;
      card.href = module.appUrl || module.externalUrl || `#/${module.id}`;
      if (module.externalUrl) { card.target = '_blank'; card.rel = 'noopener noreferrer'; }
      const copy = element('div', 'card-copy');
      copy.append(element('h2', '', module.name));
      card.append(icon(module), copy);
      grid.append(card);
    });
    const hero = element('div', 'metro-hero');
    hero.setAttribute('aria-hidden', 'true');
    const lines = element('img', 'metro-lines');
    lines.src = './assets/metro-lines.svg'; lines.alt = '';
    hero.append(lines);
    content.append(hero, grid);
    enableModuleReorder(grid);
    refreshCQuadreBadge();
  } else {
    const module = modules.find(item => route === `/${item.id}`);
    if (module?.id === 'cquadre') {
      document.title = 'Complements de Quadre � TMB Agent';
      const panel = element('section', 'panel cquadre-panel');
      panel.append(icon(module), element('p', 'eyebrow', 'TMB AGENT'), element('h1', '', module.name), element('p', 'subtitle', module.description));
      content.append(panel);
      renderCQuadre(setCQuadreBadge).then(view => panel.append(view));
      return;
    }
    if (module?.id === 'mapa-metro') {
      document.title = 'Mapa Metro · TMB Agent';
      content.append(renderMap());
      return;
    }
    const panel = element('section', 'panel');
    document.title = `${module?.name || 'Pàgina no trobada'} · TMB Agent`;
    if (module) {
      panel.append(icon(module), element('p', 'eyebrow', 'TMB AGENT'), element('h1', '', module.name), element('p', 'subtitle', module.description));
      let external;
      try { const url = new URL(module.externalUrl); if (url.protocol === 'https:') external = url.href; } catch {}
      if (external) {
        const link = element('a', 'button', `Obrir ${module.name} ↗`);
        link.href = external; link.target = '_blank'; link.rel = 'noopener noreferrer';
        panel.append(link);
      } else {
        panel.append(element('span', 'badge', 'Properament'), element('p', 'notice', 'Aquest espai està preparat. El contingut estarà disponible més endavant.'));
      }
    } else {
      panel.append(element('h1', '', 'Pàgina no trobada'), element('p', 'subtitle', 'Torna a l’inici per triar un mòdul.'));
    }
    content.append(back, panel);
  }
}

let checking = 0;
async function authorizedRender() {
  const requestId = ++checking;
  content.replaceChildren(element('p', 'notice', 'Comprovant el teu accés…'));
  try {
    const state = await checkSession();
    if (requestId !== checking) return;
    if (state !== 'approved') {
      location.replace(loginUrl(location));
      return;
    }
    render();
  } catch {
    if (requestId !== checking) return;
    const retry = element('button', 'button', 'Tornar-ho a provar');
    retry.addEventListener('click', authorizedRender);
    content.replaceChildren(element('p', 'notice', 'No s’ha pogut comprovar la sessió. Revisa la connexió i torna-ho a provar.'), retry);
  }
}

function startApp() {
  authorizedRender();
  window.addEventListener('hashchange', () => { authorizedRender(); content.focus(); window.scrollTo(0, 0); });
  window.addEventListener('storage', event => { if (event.key === SESSION_KEY || event.key === null) authorizedRender(); });
  window.addEventListener('pageshow', event => { if (event.persisted) authorizedRender(); });
  window.addEventListener('pagehide', () => { ++checking; content.replaceChildren(); });
  document.addEventListener('visibilitychange', () => {
    if (document.hidden) { ++checking; content.replaceChildren(); }
    else authorizedRender();
  });
  setInterval(() => { if (!document.hidden) authorizedRender(); }, 60000);
  if ('serviceWorker' in navigator) {
    window.addEventListener('load', () => {
      navigator.serviceWorker.register('./sw.js').catch(() => {
        document.querySelector('footer').append(element('p', 'notice', 'El mode sense connexió no està disponible en aquest navegador.'));
      });
    });
  }
}
startApp();


