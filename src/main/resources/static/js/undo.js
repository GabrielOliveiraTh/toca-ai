import { state } from './state.js';
import { $, guard } from './dom.js';
import { api } from './api.js';
import { toast } from './toast.js';
import { update } from './workspace.js';

const sameOrder = (a, b) => a.length === b.length && a.every((t, i) => t.deezerId === b[i].deezerId);

export async function mutateTracks(fn) {
  const before = state.current.tracks;
  const playlist = await fn();
  if (!sameOrder(before, playlist.tracks)) {
    state.undoStack.push(before);
    if (state.undoStack.length > 30) state.undoStack.shift();
  }
  update(playlist);
}

export async function undo() {
  const previous = state.undoStack.pop();
  if (!previous) return toast('Nothing to undo.');
  update(await api(`/playlists/${state.current.id}/tracks`, { method: 'PUT', body: previous }));
  toast('Change undone.');
}

export function init() {
  $('undoBtn').addEventListener('click', () => guard(undo));
  document.addEventListener('keydown', (e) => {
    if (!(e.ctrlKey || e.metaKey) || e.shiftKey || e.key.toLowerCase() !== 'z') return;
    if (!state.current || $('workspace').hidden || /INPUT|TEXTAREA|SELECT/.test(document.activeElement.tagName)) return;
    e.preventDefault();
    guard(undo);
  });
}
