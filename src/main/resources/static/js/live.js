import { state } from './state.js';
import { $, guard } from './dom.js';
import { api } from './api.js';
import { toast } from './toast.js';
import { loadList } from './sidebar.js';
import { openPlaylist, update } from './workspace.js';
import { openWizard } from './wizard.js';

let polling = false;

function announceNewTracks(oldTracks, fresh) {
  const had = new Set(oldTracks.map((t) => t.deezerId));
  const added = fresh.filter((t) => !had.has(t.deezerId) && t.addedById !== 'owner');
  if (!added.length) return;
  const first = added[0];
  const more = added.length > 1 ? ` and ${added.length - 1} more` : '';
  toast(`${first.addedBy} added "${first.title}"${more}`);
}

const signature = (p) => p.tracks.map((t) => `${t.deezerId}|${t.addedBy}`).join(',') + '|' + p.name + '|' + p.ownerName;

async function pollTick() {
  if (polling || !state.current || $('workspace').hidden || document.hidden) return;
  polling = true;
  try {
    const id = state.current.id;
    const { rev } = await api(`/playlists/${id}/rev`);
    if (rev === state.lastRev) return;
    const fresh = await api(`/playlists/${id}`);
    if (!state.current || state.current.id !== id) return;
    state.lastRev = rev;
    if (signature(fresh) === signature(state.current)) return;
    announceNewTracks(state.current.tracks, fresh.tracks);
    state.undoStack = [];
    update(fresh);
  } catch (e) {
    if (e.status === 404) {
      state.current = null;
      try {
        localStorage.removeItem('lastPlaylist');
      } catch {
        // nothing to clean up
      }
      $('workspace').hidden = true;
      const list = (await guard(loadList)) || [];
      if (list.length) openPlaylist(list[0].id);
      else openWizard();
      toast('This playlist was deleted.');
    }
  } finally {
    polling = false;
  }
}

export function init() {
  setInterval(pollTick, 4000);
  document.addEventListener('visibilitychange', () => {
    if (!document.hidden) pollTick();
  });
}
