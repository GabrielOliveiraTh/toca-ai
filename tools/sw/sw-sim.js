const fs = require('fs');
const vm = require('vm');
const assert = require('assert');

const code = fs.readFileSync(process.argv[2], 'utf8');
const CACHE_NAME = (code.match(/const CACHE = '([^']+)'/) || [])[1];
if (!CACHE_NAME) {
  console.error('FAILED: could not find `const CACHE = ...` in sw.js');
  process.exit(1);
}

const ORIGIN = 'http://127.0.0.1:8080';
const handlers = {};
const store = new Map();
let online = true;
const calls = { fetch: [], deleted: [], claimed: false, skipped: false };

class FakeResponse {
  constructor(body, init = {}) {
    this.body = body;
    this.status = init.status ?? 200;
    this.headers = init.headers || {};
    this.ok = this.status >= 200 && this.status < 300;
  }

  clone() {
    return new FakeResponse(this.body, { status: this.status, headers: this.headers });
  }

  static error() {
    const response = new FakeResponse('', { status: 0 });
    response.type = 'error';
    return response;
  }
}

const keyOf = (request, opts) => {
  const url = new URL(typeof request === 'string' ? request : request.url, ORIGIN);
  return opts && opts.ignoreSearch ? url.pathname : url.pathname + url.search;
};

const caches = {
  open: async (name) => {
    if (!store.has(name)) store.set(name, new Map());
    const entries = store.get(name);
    return {
      add: async (url) => {
        if (url === '/icons/icon-512.png' && caches.failIcon) throw new Error('404');
        entries.set(keyOf(url), new FakeResponse('cached:' + url));
      },
      put: async (key, response) => {
        entries.set(keyOf(key), response);
      },
    };
  },
  keys: async () => [...store.keys()],
  delete: async (name) => {
    calls.deleted.push(name);
    return store.delete(name);
  },
  match: async (key, opts) => {
    for (const entries of store.values()) {
      const hit = entries.get(keyOf(key, opts));
      if (hit) return hit;
    }
    return undefined;
  },
};

const self = {
  location: { origin: ORIGIN },
  addEventListener: (type, fn) => {
    handlers[type] = fn;
  },
  skipWaiting: async () => {
    calls.skipped = true;
  },
  clients: {
    claim: async () => {
      calls.claimed = true;
    },
  },
};

const context = {
  self,
  caches,
  URL,
  Response: FakeResponse,
  Promise,
  console,
  fetch: async (request) => {
    calls.fetch.push(request.url);
    if (!online) throw new TypeError('offline');
    return new FakeResponse('net:' + request.url);
  },
};
vm.createContext(context);
vm.runInContext(code, context);

const req = (path, over = {}) => {
  const names = over.headers || [];
  return {
    method: 'GET',
    url: ORIGIN + path,
    mode: 'cors',
    destination: '',
    ...over,
    headers: { has: (header) => names.includes(header) },
  };
};

async function fire(request) {
  let responded = null;
  await handlers.fetch({ request, respondWith: (value) => { responded = value; } });
  return { intercepted: responded !== null, response: responded ? await responded : null };
}

let passed = 0;
const ok = (message) => {
  passed++;
  console.log('ok -', message);
};
const wait = () => new Promise((resolve) => setTimeout(resolve, 10));

(async () => {
  caches.failIcon = true;
  let waited;
  await handlers.install({ waitUntil: (p) => { waited = p; } });
  await waited;
  const shell = store.get(CACHE_NAME);
  assert(calls.skipped);
  assert(shell.has('/js/main.js') && shell.has('/js/workspace.js'));
  assert(!shell.has('/icons/icon-512.png'));
  ok('installs even with a shell file missing and takes control (skipWaiting)');
  caches.failIcon = false;

  store.set('old-cache-v0', new Map());
  await handlers.activate({ waitUntil: (p) => { waited = p; } });
  await waited;
  assert(calls.deleted.includes('old-cache-v0') && !calls.deleted.includes(CACHE_NAME) && calls.claimed);
  ok('activation deletes old shells, keeps the current one and claims open tabs');

  const before = calls.fetch.length;
  const bypassed = [
    ['/api/playlists', req('/api/playlists')],
    ['/api/guest/abc/info', req('/api/guest/abc/info')],
    ['/auth/spotify/callback', req('/auth/spotify/callback')],
    ['/join/TOKEN', req('/join/TOKEN', { mode: 'navigate' })],
    ['/guest/main.js', req('/guest/main.js')],
    ['POST /js/main.js', req('/js/main.js', { method: 'POST' })],
    ['audio Range', req('/shell.mp3', { headers: ['range'] })],
    ['audio destination', req('/x', { destination: 'audio' })],
    ['other origin', { ...req('/js/main.js'), url: 'https://fonts.googleapis.com/css2' }],
    ['file outside the shell', req('/anything.png')],
  ];
  for (const [label, request] of bypassed) {
    const result = await fire(request);
    assert(!result.intercepted, label + ' was intercepted');
  }
  assert.strictEqual(calls.fetch.length, before);
  ok('API, sign-in, guest pages, POST, audio/Range, other origins and non-shell files pass through');

  let result = await fire(req('/js/main.js'));
  assert(result.intercepted && result.response.body === 'net:' + ORIGIN + '/js/main.js');
  await wait();
  assert(store.get(CACHE_NAME).get('/js/main.js').body.startsWith('net:'));
  ok('online: serves the fresh version and updates the cache');

  result = await fire(req('/some/route', { mode: 'navigate' }));
  assert(result.intercepted && result.response.body.startsWith('net:'));
  await wait();
  assert(store.get(CACHE_NAME).get('/').body.includes('/some/route'));
  ok('navigation: served from the network and the shell is stored at "/"');

  online = false;
  result = await fire(req('/js/main.js'));
  assert(result.intercepted && result.response.body.startsWith('net:' + ORIGIN + '/js/main.js'));
  ok('offline: shell file comes from the cache');
  result = await fire(req('/', { mode: 'navigate' }));
  assert(result.intercepted && result.response.status === 200);
  ok('offline: opening the app shows the cached shell');
  store.get(CACHE_NAME).delete('/');
  store.get(CACHE_NAME).delete('/index.html');
  result = await fire(req('/', { mode: 'navigate' }));
  assert(result.response.status === 503 && result.response.body.includes('needs the computer'));
  ok('offline with no cache: shows the offline page');
  store.get(CACHE_NAME).delete('/style.css');
  result = await fire(req('/style.css'));
  assert(result.response.type === 'error');
  ok('offline with no cache: a plain resource fails as a network error');
  const apiOffline = await fire(req('/api/playlists'));
  assert(!apiOffline.intercepted);
  ok('offline: the API is still not intercepted');

  console.log(`\n${passed} checks passed`);
})().catch((error) => {
  console.error('FAILED:', error.message);
  process.exit(1);
});
