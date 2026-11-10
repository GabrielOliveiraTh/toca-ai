import { state } from './api.js';
import { showDead } from './view.js';

export const $ = (id) => document.getElementById(id);

const HTML_ESCAPES = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };

export const esc = (value) => String(value ?? '').replace(/[&<>"']/g, (c) => HTML_ESCAPES[c]);

const svg = (d) => `<svg class="i" viewBox="0 0 24 24" aria-hidden="true">${d}</svg>`;

export const ICON = {
  plus: svg('<path d="M12 5v14M5 12h14"/>'),
  check: svg('<path d="M20 6 9 17l-5-5"/>'),
  trash: svg('<path d="M3 6h18M8 6V4a1 1 0 0 1 1-1h6a1 1 0 0 1 1 1v2m3 0v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6"/><path d="M10 11v6M14 11v6"/>'),
};

let toastTimer = null;

export function toast(message, isError) {
  const el = $('toast');
  el.textContent = message;
  el.className = isError ? 'err' : '';
  el.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => {
    el.hidden = true;
  }, isError ? 7000 : 4500);
}

export async function act(btn, fn) {
  if (btn && btn.disabled) return;
  if (btn) btn.disabled = true;
  state.inflight++;
  try {
    return await fn();
  } catch (e) {
    if (e.status === 404 && !/track/i.test(e.message)) showDead();
    else toast(e.message, true);
  } finally {
    state.inflight--;
    if (btn && btn.isConnected) btn.disabled = false;
  }
}
