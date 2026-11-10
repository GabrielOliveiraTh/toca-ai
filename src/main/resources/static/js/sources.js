import { state } from './state.js';
import { $, esc, guard, busy } from './dom.js';
import { api } from './api.js';
import { toast } from './toast.js';
import { mainSource } from './links.js';
import { fill, addBtn, setBadge } from './tracks.js';
import { update } from './workspace.js';
import { loadSuggestions } from './suggestions.js';
import { mutateTracks, undo } from './undo.js';

export function renderCommon() {
  setBadge('commonN', state.commonList.length);
  $('common').dataset.empty = state.current && state.current.sources.length < 2
    ? 'Add 2 or more playlists (the reference counts) to see what they have in common.'
    : 'No track appears in more than one playlist.';
  fill('common', state.commonList.map((c) => ({ ...c.track, badge: `in ${c.count}`, hint: c.sources.join(' • ') })), addBtn);
}

export async function loadCommon() {
  const id = state.current.id;
  const common = state.current.sources.length >= 2 ? await api(`/playlists/${id}/common`) : [];
  if (state.current && state.current.id === id) {
    state.commonList = common;
    renderCommon();
  }
}

function fillDiffSelect() {
  const select = $('diffAgainst');
  const previous = select.value;
  select.innerHTML = state.current.sources.map((s) =>
    `<option value="${s.id}">${s.main ? '★ ' : ''}${esc(s.name)}</option>`).join('');
  if (previous && state.current.sources.some((s) => String(s.id) === previous)) select.value = previous;
}

export async function loadDiff() {
  const { current } = state;
  if (current.sources.length === 0) {
    $('diffDialog').close();
    return;
  }
  fillDiffSelect();
  state.diffData = await api(`/playlists/${current.id}/diff?against=${$('diffAgainst').value}`);
  const diff = state.diffData;
  $('diffBothN').textContent = diff.both.length;
  $('diffMineN').textContent = diff.onlyMine.length;
  $('diffSrcN').textContent = diff.onlySource.length;
  fill('diffBoth', diff.both, () => '');
  fill('diffMine', diff.onlyMine, () => '');
  fill('diffSrc', diff.onlySource, addBtn);
}

export function init() {
  $('diffBtn').addEventListener('click', () => guard(async () => {
    await loadDiff();
    if (!$('diffDialog').open) $('diffDialog').showModal();
  }));
  $('diffClose').addEventListener('click', () => $('diffDialog').close());
  $('diffDialog').addEventListener('click', (e) => {
    if (e.target === $('diffDialog')) $('diffDialog').close();
  });
  $('diffAgainst').addEventListener('change', () => guard(loadDiff));

  $('refForm').addEventListener('submit', (e) => {
    e.preventDefault();
    busy(e.target.querySelector('button'), 'Importing…', async () => {
      update(await api(`/playlists/${state.current.id}/reference`, { method: 'PUT', body: { url: $('refUrl').value } }));
      toast(`Reference loaded: ${mainSource()?.tracks.length ?? 0} tracks.`);
      loadSuggestions();
    });
  });

  $('refClear').addEventListener('click', () => guard(async () => {
    update(await api(`/playlists/${state.current.id}/reference`, { method: 'PUT', body: { url: '' } }));
    loadSuggestions();
  }));

  $('srcForm').addEventListener('submit', (e) => {
    e.preventDefault();
    if (!$('srcUrl').value.trim()) return;
    busy(e.target.querySelector('button'), 'Importing…', async () => {
      update(await api(`/playlists/${state.current.id}/sources`, { method: 'POST', body: { url: $('srcUrl').value } }));
      $('srcUrl').value = '';
      toast('Comparison playlist added.');
      loadSuggestions();
    });
  });

  $('addAllRef').addEventListener('click', (e) => busy(e.currentTarget, 'Adding…', async () => {
    await mutateTracks(async () => {
      let playlist = state.current;
      for (const track of mainSource().tracks) {
        const has = playlist.tracks.some((x) => x.deezerId === track.deezerId || (track.isrc && x.isrc === track.isrc));
        if (!has) playlist = await api(`/playlists/${state.current.id}/tracks`, { method: 'POST', body: track });
      }
      return playlist;
    });
    toast('Reference tracks added.', false, { label: 'Undo', fn: undo });
  }));

  document.addEventListener('click', (e) => {
    const remove = e.target.closest('[data-delsource]');
    if (remove) {
      guard(async () => update(await api(`/playlists/${state.current.id}/sources/${remove.dataset.delsource}`, { method: 'DELETE' })));
    }
  });
}
