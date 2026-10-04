import { renderMap } from './map.js?v=ca-mapa-v1';
import { modules } from './modules.js?v=avisos-v1';
import { checkSession, loginUrl, SESSION_KEY } from './session.js';
import { getUnreadCounts, renderCQuadre } from './cquadre.js?v=avisos-v2';
import { renderBustia } from './bustia.js?v=bustia-v1';

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
  let startX = 0;
  let startY = 0;
  let suppressClick = false;

  const clearTimer = () => {
    if (timer) window.clearTimeout(timer);
    timer = 0;
  };

  const begin = (card) => {
    active = card;
    suppressClick = true;
    grid.classList.add('reordering');
    card.classList.add('card-dragging');
    if (navigator.vibrate) navigator.vibrate(25);
  };

  const moveActive = (x, y) => {
    if (!active) return;
    const target = document.elementFromPoint(x, y)?.closest('.card[data-module]');
    if (!target || target === active || target.parentElement !== grid) return;

    const rect = target.getBoundingClientRect();
    const before = y < rect.top + rect.height / 2 ||
      (Math.abs(y - (rect.top + rect.height / 2)) < rect.height * 0.25 &&
       x < rect.left + rect.width / 2);

    grid.insertBefore(active, before ? target : target.nextSibling);
  };

  const finish = () => {
    clearTimer();
    if (!active) return;
    saveModuleOrder(grid);
    active.classList.remove('card-dragging');
    grid.classList.remove('reordering');
    active = null;
    window.setTimeout(() => { suppressClick = false; }, 120);
  };

  // Mòbil: Touch Events permeten mantenir l'scroll normal fins que
  // la pulsació llarga activa realment el mode de reordenació.
  grid.addEventListener('touchstart', event => {
    if (event.touches.length !== 1) return;
    const card = event.target.closest('.card[data-module]');
    if (!card) return;

    clearTimer();
    active = null;
    const touch = event.touches[0];
    startX = touch.clientX;
    startY = touch.clientY;
    timer = window.setTimeout(() => begin(card), 500);
  }, { passive: true });

  grid.addEventListener('touchmove', event => {
    if (event.touches.length !== 1) return;
    const touch = event.touches[0];

    if (!active) {
      if (Math.hypot(touch.clientX - startX, touch.clientY - startY) > 12) clearTimer();
      return;
    }

    event.preventDefault();
    moveActive(touch.clientX, touch.clientY);
  }, { passive: false });

  grid.addEventListener('touchend', () => {
    if (active) finish();
    else clearTimer();
  }, { passive: true });

  grid.addEventListener('touchcancel', () => {
    if (active) finish();
    else clearTimer();
  }, { passive: true });

  // PC: ratolí amb el mateix gest de pulsació mantinguda.
  grid.addEventListener('pointerdown', event => {
    if (event.pointerType === 'touch' || event.button !== 0) return;
    const card = event.target.closest('.card[data-module]');
    if (!card) return;

    clearTimer();
    active = null;
    startX = event.clientX;
    startY = event.clientY;
    timer = window.setTimeout(() => begin(card), 350);
  });

  grid.addEventListener('pointermove', event => {
    if (event.pointerType === 'touch') return;
    if (!active) {
      if (timer && Math.hypot(event.clientX - startX, event.clientY - startY) > 10) clearTimer();
      return;
    }
    event.preventDefault();
    moveActive(event.clientX, event.clientY);
  });

  grid.addEventListener('pointerup', event => {
    if (event.pointerType === 'touch') return;
    if (active) finish();
    else clearTimer();
  });

  grid.addEventListener('click', event => {
    if (!suppressClick) return;
    event.preventDefault();
    event.stopPropagation();
  }, true);

  grid.addEventListener('dragstart', event => event.preventDefault());
  grid.addEventListener('contextmenu', event => {
    if (active || timer) event.preventDefault();
  });
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
function setCQuadreBadge(kind, count) {
  const card = document.querySelector('[data-module="cquadre"]');
  if (!card) return;

  let group = card.querySelector('.unread-badges');
  if (!group) {
    group = element('span', 'unread-badges');
    card.append(group);
  }

  group.querySelector(`.unread-badge[data-kind="${kind}"]`)?.remove();

  if (count > 0) {
    const label = kind === 'cquadre' ? 'C.Quadre' : 'Avisos';
    const badge = element('a', `unread-badge unread-badge-${kind}`);
    badge.dataset.kind = kind;
    badge.href = kind === 'cquadre' ? '#/cquadre/cquadre' : '#/cquadre/avisos';
    badge.setAttribute('aria-label', `Obrir ${label}: ${count} pendents`);
    badge.append(
      element('span', 'unread-badge-label', label),
      element('strong', 'unread-badge-count', count > 99 ? '99+' : String(count)),
    );
    group.append(badge);
  }

  if (!group.children.length) group.remove();
}

function setCQuadreBadges(counts) {
  setCQuadreBadge('cquadre', Number(counts?.cquadre) || 0);
  setCQuadreBadge('avisos', Number(counts?.avisos) || 0);
}

async function refreshCQuadreBadges() {
  try { setCQuadreBadges(await getUnreadCounts()); } catch {}
}

function render() {
  const route = location.hash.slice(1) || '/';
  const cquadreView = route === '/cquadre/cquadre'
    ? 'cquadre'
    : route === '/cquadre/avisos'
      ? 'avisos'
      : 'all';
  const moduleRoute = route.startsWith('/cquadre/') ? '/cquadre' : route;
  const shortcutModule = modules.find(module => moduleRoute === `/${module.id}`);
  const destination = shortcutModule?.appUrl || shortcutModule?.externalUrl;
  if (destination) { location.replace(destination); return; }
  document.body.classList.toggle('home', route === '/' || location.hash === '#content');
  content.replaceChildren();
  if (route === '/' || location.hash === '#content') {
    document.title = 'TMB Agent';
    const grid = element('nav', 'grid');
    grid.setAttribute('aria-label', 'Mòduls');
    orderedModules().forEach(module => {
      const isCQuadre = module.id === 'cquadre';
      const card = element(isCQuadre ? 'div' : 'a', isCQuadre ? 'card card-cquadre' : 'card');
      card.dataset.module = module.id;

      const cardLink = isCQuadre ? element('a', 'card-main-link') : card;
      cardLink.href = module.appUrl || module.externalUrl || `#/${module.id}`;
      if (module.externalUrl) {
        cardLink.target = '_blank';
        cardLink.rel = 'noopener noreferrer';
      }

      const copy = element('div', 'card-copy');
      copy.append(element('h2', '', module.name));
      cardLink.append(icon(module), copy);

      if (isCQuadre) card.append(cardLink);
      grid.append(card);
    });
    const hero = element('div', 'metro-hero');
    hero.setAttribute('aria-hidden', 'true');
    const lines = element('img', 'metro-lines');
    lines.src = './assets/metro-lines.svg'; lines.alt = '';
    hero.append(lines);
    content.append(hero, grid);
    enableModuleReorder(grid);
    refreshCQuadreBadges();
  } else {
    const module = modules.find(item => moduleRoute === `/${item.id}`);
    if (module?.id === 'cquadre') {
      const title = cquadreView === 'cquadre'
        ? 'C.Quadre'
        : cquadreView === 'avisos'
          ? 'Avisos'
          : module.name;
      const subtitle = cquadreView === 'cquadre'
        ? 'Complements de Quadre pendents de llegir'
        : cquadreView === 'avisos'
          ? 'Avisos pendents de llegir'
          : module.description;

      document.title = `${title} · TMB Agent`;
      const panel = element('section', 'panel cquadre-panel');
      panel.append(
        icon(module),
        element('p', 'eyebrow', 'TMB AGENT'),
        element('h1', '', title),
        element('p', 'subtitle', subtitle),
      );
      content.append(panel);
      renderCQuadre(setCQuadreBadge, cquadreView).then(view => panel.append(view));
      return;
    }
    if (module?.id === 'mapa-metro') {
      document.title = 'Mapa Metro · TMB Agent';
      content.append(renderMap());
      return;
    }
    if (module?.id === 'bustia') {
      document.title = 'Bústia · TMB Agent';
      const panel = element('section', 'panel bustia-panel');
      panel.append(
        icon(module),
        element('p', 'eyebrow', 'TMB AGENT'),
        element('h1', '', module.name),
        element('p', 'subtitle', module.description),
        renderBustia(),
      );
      content.append(panel);
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


