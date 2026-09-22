import { modules } from './modules.js';

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
function render() {
  const route = location.hash.slice(1) || '/';
  document.body.classList.toggle('home', route === '/' || location.hash === '#content');
  content.replaceChildren();
  if (route === '/' || location.hash === '#content') {
    document.title = 'TMB Agent';
    const grid = element('nav', 'grid');
    grid.setAttribute('aria-label', 'Módulos');
    modules.forEach(module => {
      const card = element('a', 'card');
      card.href = `#/${module.id}`;
      const copy = element('div', 'card-copy');
      copy.append(element('h2', '', module.name));
      card.append(icon(module), copy);
      grid.append(card);
    });
    content.append(grid);
  } else {
    const module = modules.find(item => route === `/${item.id}`);
    const back = element('a', 'back', '← Inicio');
    back.href = '#/';
    const panel = element('section', 'panel');
    document.title = `${module?.name || 'Página no encontrada'} · TMB Agent`;
    if (module) {
      panel.append(icon(module), element('p', 'eyebrow', 'TMB AGENT'), element('h1', '', module.name), element('p', 'subtitle', module.description));
      let external;
      try { const url = new URL(module.externalUrl); if (url.protocol === 'https:') external = url.href; } catch {}
      if (external) {
        const link = element('a', 'button', `Abrir ${module.name} ↗`);
        link.href = external; link.target = '_blank'; link.rel = 'noopener noreferrer';
        panel.append(link);
      } else {
        panel.append(element('span', 'badge', 'Próximamente'), element('p', 'notice', 'Este espacio está preparado. Su contenido estará disponible más adelante.'));
      }
    } else {
      panel.append(element('h1', '', 'Página no encontrada'), element('p', 'subtitle', 'Vuelve al inicio para elegir un módulo.'));
    }
    content.append(back, panel);
  }
}

// Entrada única de la aplicación. Un futuro login podrá preceder a startApp().
// Hoy no hay autenticación, almacenamiento de perfiles ni permisos.
function startApp() {
  render();
  window.addEventListener('hashchange', () => { render(); content.focus(); window.scrollTo(0, 0); });
  if ('serviceWorker' in navigator) {
    window.addEventListener('load', () => {
      navigator.serviceWorker.register('./sw.js').catch(() => {
        document.querySelector('footer').append(element('p', 'notice', 'El modo sin conexión no está disponible en este navegador.'));
      });
    });
  }
}
startApp();
