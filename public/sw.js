const CACHE = 'tmb-agent-__VERSION__';
const FILES = ['.', 'index.html', 'src/main.js', 'src/modules.js', 'src/styles.css', 'manifest.webmanifest', 'assets/icons/placeholder-192.png', 'assets/icons/placeholder-512.png'];
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
