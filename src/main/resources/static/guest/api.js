export const token = decodeURIComponent(location.pathname.split('/')[2] || '');

const STORE_KEY = 'mixtape.guest.' + token;

export const state = {
  guest: null,
  info: null,
  filterBy: null,
  results: [],
  suggestions: null,
  suggestionsLoading: false,
  inflight: 0,
  dead: false,
  pollTimer: null,
};

try {
  state.guest = JSON.parse(localStorage.getItem(STORE_KEY) || 'null');
} catch {
  state.guest = null;
}
if (state.guest && !(state.guest.id && state.guest.name)) state.guest = null;

export function saveGuest() {
  try {
    localStorage.setItem(STORE_KEY, JSON.stringify(state.guest));
  } catch {
    // identity lasts only for this visit
  }
}

export function forgetGuest() {
  state.guest = null;
  try {
    localStorage.removeItem(STORE_KEY);
  } catch {
    // nothing to clean up
  }
}

export function newId() {
  const bytes = new Uint8Array(18);
  crypto.getRandomValues(bytes);
  return btoa(String.fromCharCode(...bytes)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

export async function api(path, opts = {}) {
  const headers = { Accept: 'application/json' };
  if (state.guest) headers['X-Guest-Id'] = state.guest.id;
  if (opts.body !== undefined) headers['Content-Type'] = 'application/json';
  let res;
  try {
    res = await fetch('/api/guest/' + encodeURIComponent(token) + path, {
      method: opts.method || 'GET',
      headers,
      body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
    });
  } catch {
    const error = new Error('Cannot reach the server. Check that you are still on the same Wi-Fi network.');
    error.status = 0;
    throw error;
  }
  const text = await res.text();
  let data = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = null;
  }
  if (!res.ok) {
    const error = new Error((data && data.error) || 'Something went wrong (' + res.status + ').');
    error.status = res.status;
    throw error;
  }
  return data;
}
