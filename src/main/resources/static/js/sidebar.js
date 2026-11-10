import { state } from './state.js';
import { $, ICON, esc, guard } from './dom.js';
import { api } from './api.js';
import { toast } from './toast.js';
import { initial, gsvg, centerGlyphs } from './avatar.js';
import { openPlaylist } from './workspace.js';
import { openWizard } from './wizard.js';

export function setCollapsed(on) {
  $('sidebar').classList.toggle('collapsed', on);
  const label = on ? 'Expand menu' : 'Collapse menu';
  $('collapseBtn').title = label;
  $('collapseBtn').setAttribute('aria-label', label);
  $('collapseBtn').setAttribute('aria-expanded', String(!on));
  try {
    localStorage.setItem('sidebarCollapsed', on ? '1' : '0');
  } catch {
    // preference is not persisted
  }
}

export async function loadList() {
  const list = await api('/playlists');
  state.playlistCount = list.length;
  $('playlistList').innerHTML = list.length === 0
    ? '<li class="none">No playlists yet.</li>'
    : list.map((p) =>
      `<li data-open="${p.id}" class="${state.current && state.current.id === p.id ? 'active' : ''}" title="${esc(p.name)}">
         <span class="avatar">${gsvg(initial(p.name), 26, 12)}</span>
         <span class="name">${esc(p.name)}</span><span class="badge">${p.count}</span>
         <button class="icon danger" data-delplaylist="${p.id}" data-name="${esc(p.name)}" title="Delete playlist">${ICON.trash}</button></li>`).join('');
  centerGlyphs();
  return list;
}

export async function deletePlaylist(id, name) {
  if (!confirm(`Delete the playlist "${name}"? This cannot be undone.`)) return;
  await api('/playlists/' + id, { method: 'DELETE' });
  if (state.current && state.current.id === Number(id)) {
    state.current = null;
    try {
      localStorage.removeItem('lastPlaylist');
    } catch {
      // nothing to clean up
    }
    $('workspace').hidden = true;
  }
  const list = await loadList();
  toast('Playlist deleted.');
  if (!state.current) {
    if (list.length) openPlaylist(list[0].id);
    else openWizard();
  }
}

export function init() {
  $('collapseBtn').innerHTML = ICON.panel;
  $('newIcon').innerHTML = ICON.plus;
  $('collapseBtn').addEventListener('click', () => setCollapsed(!$('sidebar').classList.contains('collapsed')));
  $('playlistList').addEventListener('click', (e) => {
    const del = e.target.closest('[data-delplaylist]');
    if (del) return guard(() => deletePlaylist(del.dataset.delplaylist, del.dataset.name));
    const li = e.target.closest('[data-open]');
    if (li) guard(() => openPlaylist(li.dataset.open));
  });
  $('deleteBtn').addEventListener('click', () => guard(() => deletePlaylist(state.current.id, state.current.name)));
}
