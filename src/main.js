import { renderMap } from './map.js?v=ca-mapa-v1';
import { modules } from './modules.js?v=ca-mapa-v1';
import { checkSession, loginUrl, SESSION_KEY } from './session.js';
import { getUnreadCount, renderCQuadre } from './cquadre.js';

const content = document.querySelector('main');
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
  const destination = modules.find(module => route === `/${module.id}`)?.appUrl;
  if (destination) { location.replace(destination); return; }
  document.body.classList.toggle('home', route === '/' || location.hash === '#content');
  content.replaceChildren();
  if (route === '/' || location.hash === '#content') {
    document.title = 'TMB Agent';
    const grid = element('nav', 'grid');
    grid.setAttribute('aria-label', 'MÃ²duls');
    modules.forEach(module => {
      const card = element('a', 'card');
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
    refreshCQuadreBadge();
  } else {
    const module = modules.find(item => route === `/${item.id}`);
    const back = element('a', 'back', 'â† Inici');
    back.href = '#/';
    if (module?.id === 'cquadre') {
      document.title = 'Complements de Quadre · TMB Agent';
      const panel = element('section', 'panel cquadre-panel');
      panel.append(icon(module), element('p', 'eyebrow', 'TMB AGENT'), element('h1', '', module.name), element('p', 'subtitle', module.description));
      content.append(back, panel);
      renderCQuadre(setCQuadreBadge).then(view => panel.append(view));
      return;
    }
    if (module?.id === 'mapa-metro') {
      document.title = 'Mapa Metro Â· TMB Agent';
      content.append(back, renderMap());
      return;
    }
    const panel = element('section', 'panel');
    document.title = `${module?.name || 'PÃ gina no trobada'} Â· TMB Agent`;
    if (module) {
      panel.append(icon(module), element('p', 'eyebrow', 'TMB AGENT'), element('h1', '', module.name), element('p', 'subtitle', module.description));
      let external;
      try { const url = new URL(module.externalUrl); if (url.protocol === 'https:') external = url.href; } catch {}
      if (external) {
        const link = element('a', 'button', `Obrir ${module.name} â†—`);
        link.href = external; link.target = '_blank'; link.rel = 'noopener noreferrer';
        panel.append(link);
      } else {
        panel.append(element('span', 'badge', 'Properament'), element('p', 'notice', 'Aquest espai estÃ  preparat. El contingut estarÃ  disponible mÃ©s endavant.'));
      }
    } else {
      panel.append(element('h1', '', 'PÃ gina no trobada'), element('p', 'subtitle', 'Torna a lâ€™inici per triar un mÃ²dul.'));
    }
    content.append(back, panel);
  }
}

let checking = 0;
async function authorizedRender() {
  const requestId = ++checking;
  content.replaceChildren(element('p', 'notice', 'Comprovant el teu accÃ©sâ€¦'));
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
    content.replaceChildren(element('p', 'notice', 'No sâ€™ha pogut comprovar la sessiÃ³. Revisa la connexiÃ³ i torna-ho a provar.'), retry);
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
        document.querySelector('footer').append(element('p', 'notice', 'El mode sense connexiÃ³ no estÃ  disponible en aquest navegador.'));
      });
    });
  }
}
startApp();

