import { toast } from './toast.js';

export const $ = (id) => document.getElementById(id);

const svg = (d) => `<svg class="i" viewBox="0 0 24 24" aria-hidden="true">${d}</svg>`;

export const ICON = {
  plus: svg('<path d="M12 5v14M5 12h14"/>'),
  check: svg('<path d="M20 6 9 17l-5-5"/>'),
  up: svg('<path d="m5 12 7-7 7 7M12 19V5"/>'),
  down: svg('<path d="m19 12-7 7-7-7M12 5v14"/>'),
  trash: svg('<path d="M3 6h18M8 6V4a1 1 0 0 1 1-1h6a1 1 0 0 1 1 1v2m3 0v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6"/><path d="M10 11v6M14 11v6"/>'),
  panel: svg('<rect x="3" y="3" width="18" height="18" rx="2"/><path d="M9 3v18"/>'),
  grip: svg('<circle cx="9" cy="6" r="1"/><circle cx="15" cy="6" r="1"/><circle cx="9" cy="12" r="1"/><circle cx="15" cy="12" r="1"/><circle cx="9" cy="18" r="1"/><circle cx="15" cy="18" r="1"/>'),
  undo: svg('<path d="M3 7v6h6"/><path d="M21 17a9 9 0 0 0-9-9 9 9 0 0 0-6 2.3L3 13"/>'),
  sort: svg('<path d="m21 16-4 4-4-4"/><path d="M17 20V4"/><path d="m3 8 4-4 4 4"/><path d="M7 4v16"/>'),
  play: svg('<path d="m6 3 14 9-14 9z"/>'),
  pause: svg('<rect x="6" y="4" width="4" height="16"/><rect x="14" y="4" width="4" height="16"/>'),
  next: svg('<path d="m5 4 10 8-10 8z"/><path d="M19 5v14"/>'),
  prev: svg('<path d="m19 20-10-8 10-8z"/><path d="M5 19V5"/>'),
  thumbDown: svg('<path d="M17 14V2"/><path d="M9 18.12 10 14H4.17a2 2 0 0 1-1.92-2.56l2.33-8A2 2 0 0 1 6.5 2H20a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-2.76a2 2 0 0 0-1.79 1.11L12 22a3.13 3.13 0 0 1-3-3.88Z"/>'),
  x: svg('<path d="M18 6 6 18M6 6l12 12"/>'),
  restore: svg('<path d="M3 12a9 9 0 1 0 3-6.7L3 8"/><path d="M3 3v5h5"/>'),
  share: svg('<circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/><path d="m8.6 13.5 6.8 4M15.4 6.5l-6.8 4"/>'),
};

const HTML_ESCAPES = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };

export const esc = (value) => String(value ?? '').replace(/[&<>"']/g, (c) => HTML_ESCAPES[c]);

export function applyIcons() {
  document.querySelectorAll('[data-icon]').forEach((el) => {
    el.innerHTML = ICON[el.dataset.icon] || '';
  });
}

export async function guard(fn) {
  try {
    return await fn();
  } catch (e) {
    toast(e.message, true);
  }
}

export async function busy(btn, label, fn) {
  if (btn.disabled) return;
  const original = btn.innerHTML;
  btn.disabled = true;
  btn.textContent = label;
  try {
    return await guard(fn);
  } finally {
    btn.disabled = false;
    btn.innerHTML = original;
  }
}
