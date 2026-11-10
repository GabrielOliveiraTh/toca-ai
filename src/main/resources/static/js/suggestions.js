import { state, GENRE_OPTIONS } from './state.js';
import { $, ICON, esc, guard, busy } from './dom.js';
import { api } from './api.js';
import { toast } from './toast.js';
import { fill, addBtn, dislikeBtn } from './tracks.js';
import { renderAll, update } from './workspace.js';

let genreTimer;

export function renderSuggestions() {
  fill('suggestions', state.lastSuggestions, (t) => dislikeBtn(t) + addBtn(t));
}

export function syncFilterButtons() {
  document.querySelectorAll('[data-filter]').forEach((b) =>
    b.setAttribute('aria-pressed', String(!!state.filters[b.dataset.filter])));
}

export async function loadSuggestions() {
  const { current } = state;
  const id = current.id;
  const forStyle = current.style ? ` for "${esc(current.style)}"` : '';
  const inSources = current.style && state.styleSources.length ? ' in ' + state.styleSources.join(', ') : '';
  $('suggestions').innerHTML = `<li class="muted" style="padding:12px 8px">Finding suggestions${forStyle}${inSources}…</li>`;
  const query = new URLSearchParams({
    avoidLive: state.filters.avoidLive,
    avoidRemix: state.filters.avoidRemix,
    popular: state.filters.popular,
    excludeGenres: (state.filters.excludeGenres || []).join(','),
  });
  const suggestions = await guard(() => api(`/playlists/${id}/suggestions?${query}`));
  if (!state.current || state.current.id !== id) return;
  state.lastSuggestions = suggestions || [];
  renderSuggestions();
}

function blockedArtists() {
  const counts = {};
  state.dislikedList.forEach((d) => {
    if (d.artistId) counts[d.artistId] = (counts[d.artistId] || 0) + 1;
  });
  return new Set(Object.keys(counts).filter((k) => counts[k] >= 2).map(Number));
}

async function dislike(id) {
  const track = state.lastSuggestions.find((x) => x.deezerId === id);
  if (!track) return;
  state.lastSuggestions = state.lastSuggestions.filter((x) => x.deezerId !== id);
  renderSuggestions();
  state.current = await api(`/playlists/${state.current.id}/dislikes`, { method: 'POST', body: track });
  state.dislikedList = await api(`/playlists/${state.current.id}/dislikes`);
  const blocked = blockedArtists();
  const artistBlocked = blocked.has(track.artistId);
  if (artistBlocked) state.lastSuggestions = state.lastSuggestions.filter((x) => !blocked.has(x.artistId));
  renderAll();
  const message = artistBlocked
    ? `We will avoid ${track.artist}: you dismissed 2 of their tracks.`
    : `We won't suggest "${track.title}" again.`;
  toast(message, false, {
    label: 'Undo',
    fn: async () => {
      state.current = await api(`/playlists/${state.current.id}/dislikes/${id}`, { method: 'DELETE' });
      state.dislikedList = await api(`/playlists/${state.current.id}/dislikes`);
      state.lastSuggestions = [track, ...state.lastSuggestions.filter((x) => x.deezerId !== id)];
      renderAll();
    },
  });
}

async function openHidden() {
  state.dislikedList = await api(`/playlists/${state.current.id}/dislikes`);
  fill('hiddenList', state.dislikedList, (t) => `<button class="ghost" data-restore="${t.deezerId}">${ICON.restore} Restore</button>`);
  if (!$('hiddenDialog').open) $('hiddenDialog').showModal();
}

const saveFilters = () => {
  try {
    localStorage.setItem('sugFilters', JSON.stringify(state.filters));
  } catch {
    // preference is not persisted
  }
};

function renderGenreMenu() {
  const on = new Set(state.filters.excludeGenres || []);
  $('genrePop').innerHTML = GENRE_OPTIONS.map(([key, label]) =>
    `<label><input type="checkbox" value="${key}"${on.has(key) ? ' checked' : ''}> ${esc(label)}</label>`).join('');
  $('genreSummary').textContent = on.size ? `Exclude genres (${on.size})` : 'Exclude genres';
  $('genreSummary').setAttribute('aria-pressed', String(on.size > 0));
}

export function init() {
  document.addEventListener('click', (e) => {
    const button = e.target.closest('[data-dislike]');
    if (button) guard(() => dislike(Number(button.dataset.dislike)));
    if (!e.target.closest('.genre-menu')) $('genreMenu').open = false;
  });

  $('hiddenBtn').addEventListener('click', () => guard(openHidden));
  $('hiddenClose').addEventListener('click', () => $('hiddenDialog').close());
  $('hiddenDialog').addEventListener('click', (e) => {
    if (e.target === $('hiddenDialog')) $('hiddenDialog').close();
    const restore = e.target.closest('[data-restore]');
    if (!restore) return;
    guard(async () => {
      state.current = await api(`/playlists/${state.current.id}/dislikes/${restore.dataset.restore}`, { method: 'DELETE' });
      await openHidden();
      renderAll();
      toast('Track can appear in suggestions again.');
    });
  });

  document.querySelectorAll('[data-filter]').forEach((b) => b.addEventListener('click', () => {
    state.filters[b.dataset.filter] = !state.filters[b.dataset.filter];
    saveFilters();
    syncFilterButtons();
    guard(loadSuggestions);
  }));

  renderGenreMenu();
  $('genrePop').addEventListener('change', () => {
    state.filters.excludeGenres = [...$('genrePop').querySelectorAll('input:checked')].map((i) => i.value);
    saveFilters();
    renderGenreMenu();
    clearTimeout(genreTimer);
    genreTimer = setTimeout(() => guard(loadSuggestions), 700);
  });

  $('refreshSug').addEventListener('click', loadSuggestions);
  $('styleInput').addEventListener('input', () => {
    state.styleDirty = true;
  });
  $('styleForm').addEventListener('submit', (e) => {
    e.preventDefault();
    busy(e.target.querySelector('button'), 'Applying…', async () => {
      const playlist = await api('/playlists/' + state.current.id, { method: 'PATCH', body: { style: $('styleInput').value } });
      state.styleDirty = false;
      update(playlist);
      await loadSuggestions();
    });
  });
}
