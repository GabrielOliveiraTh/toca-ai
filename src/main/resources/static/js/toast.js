import { $, esc, guard } from './dom.js';

let action = null;
let timer = null;

export function toast(message, isError, actionSpec) {
  const el = $('toast');
  el.innerHTML = esc(message)
    + (actionSpec ? ` <button type="button" class="toast-action">${esc(actionSpec.label)}</button>` : '');
  el.className = isError ? 'err' : '';
  el.hidden = false;
  action = actionSpec ? actionSpec.fn : null;
  clearTimeout(timer);
  timer = setTimeout(() => (el.hidden = true), isError ? 10000 : actionSpec ? 8000 : 5000);
}

export function init() {
  $('toast').addEventListener('click', (e) => {
    if (!e.target.closest('.toast-action') || !action) return;
    const fn = action;
    action = null;
    $('toast').hidden = true;
    guard(fn);
  });
}
