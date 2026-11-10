const CACHE = 'tocaai-shell-v2';
const SHELL = [
  '/',
  '/index.html',
  '/style.css',
  '/manifest.json',
  '/icons/icon-192.png',
  '/icons/icon-512.png',
  '/js/api.js',
  '/js/avatar.js',
  '/js/dom.js',
  '/js/export.js',
  '/js/links.js',
  '/js/live.js',
  '/js/main.js',
  '/js/player.js',
  '/js/pwa.js',
  '/js/share.js',
  '/js/sidebar.js',
  '/js/sort.js',
  '/js/sources.js',
  '/js/spotify.js',
  '/js/state.js',
  '/js/suggestions.js',
  '/js/time.js',
  '/js/toast.js',
  '/js/tracks.js',
  '/js/undo.js',
  '/js/wizard.js',
  '/js/workspace.js',
];

const OFFLINE_PAGE = '<!doctype html><html lang="en"><meta charset="utf-8">'
  + '<meta name="viewport" content="width=device-width,initial-scale=1">'
  + '<title>Offline</title><body style="font:16px system-ui,sans-serif;background:#f5f5f5;color:#0a0a0a;'
  + 'display:grid;place-items:center;min-height:100vh;margin:0">'
  + '<main style="max-width:360px;padding:24px;text-align:center">'
  + '<h1 style="font-size:24px;letter-spacing:-.6px">Offline</h1>'
  + '<p style="color:#737373">Toca Aí needs the computer it runs on. Turn it on and reload the page.</p></main>';

// allSettled so one missing file cannot block installation
self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE)
      .then((cache) => Promise.allSettled(SHELL.map((url) => cache.add(url))))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

const isShellPath = (path) => SHELL.includes(path) || path.startsWith('/icons/') || path.startsWith('/js/');

function mustBypass(req, url) {
  if (req.method !== 'GET' || url.origin !== self.location.origin) return true;
  const path = url.pathname;
  const guestPath = path.startsWith('/join/') || path.startsWith('/join.') || path.startsWith('/guest/');
  if (path.startsWith('/api/') || path.startsWith('/auth/') || guestPath) return true;
  if (req.headers.has('range') || req.destination === 'audio' || req.destination === 'video') return true;
  return false;
}

// network first; the cache is only used when the network fails
self.addEventListener('fetch', (event) => {
  const req = event.request;
  const url = new URL(req.url);
  if (mustBypass(req, url)) return;

  const navigation = req.mode === 'navigate';
  if (!navigation && !isShellPath(url.pathname)) return;

  event.respondWith((async () => {
    try {
      const fresh = await fetch(req);
      if (fresh.ok) {
        const copy = fresh.clone();
        caches.open(CACHE).then((c) => c.put(navigation ? '/' : req, copy)).catch(() => {});
      }
      return fresh;
    } catch (err) {
      const cached = await caches.match(navigation ? '/' : req, { ignoreSearch: true });
      if (cached) return cached;
      if (navigation) {
        return new Response(OFFLINE_PAGE, { status: 503, headers: { 'Content-Type': 'text/html; charset=utf-8' } });
      }
      return Response.error();
    }
  })());
});
