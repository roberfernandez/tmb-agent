const CACHE = 'tmb-agent-__VERSION__';
const FILES = ['assets/icons/permisos-192.webp', 'src/permisos.js?v=permisos-v1', 'src/permisos-data.js', 'src/permisos.css?v=permisos-v1', 'assets/icons/nomina-192.png', '.', 'index.html', 'src/main.js?v=permisos-v1', 'src/modules.js?v=permisos-v1', 'src/bustia.js?v=administracio-v1', 'src/map.js?v=ca-mapa-v1', 'assets/mapa-icono.png', 'assets/consultes.png', 'assets/mapa-metro-barcelona-2026.png', 'src/administration.js?v=activity-v2', 'src/session.js', 'src/cquadre.js?v=cquadre-manual-v1', 'src/cquadre-view.js?v=cquadre-simple-v2', 'src/cquadre-view.css?v=cquadre-simple-v2', 'src/styles.css?v=return-v1', 'assets/metro-9000.jpg', 'assets/metro-lines.svg', 'manifest.webmanifest', 'assets/icons/tmb-agent-192.png', 'assets/icons/tmb-agent-512.png', 'assets/icons/incidencias-192.png', 'assets/icons/dea-192.png', 'assets/icons/bobines-192.png', 'assets/icons/computo-192.png', 'assets/icons/miralin-192.png', 'assets/icons/complements-quadre-192.png', 'assets/icons/bustia-192.png'];
self.addEventListener('install', event => {
  event.waitUntil(caches.open(CACHE).then(cache => cache.addAll(FILES.map(file => new URL(file, self.registration.scope).href))));
});
self.addEventListener('activate', event => {
  event.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(key => key.startsWith('tmb-agent-') && key !== CACHE).map(key => caches.delete(key)))).then(() => self.clients.claim()));
});
self.addEventListener('fetch', event => {
  if (event.request.method !== 'GET' || !event.request.url.startsWith(self.registration.scope)) return;
  event.respondWith(fetch(event.request).catch(async () => {
    const cached = await caches.match(event.request);
    if (cached) return cached;
    if (event.request.mode === 'navigate') return caches.match(new URL('index.html', self.registration.scope).href);
    return Response.error();
  }));
});
