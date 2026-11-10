import { state } from './state.js';
import { $, ICON, esc, guard, busy } from './dom.js';
import { api } from './api.js';
import { toast } from './toast.js';
import { byName } from './avatar.js';
import { mainSource, otherSources } from './links.js';
import { fill, addBtn, known, setBadge } from './tracks.js';
import { loadList } from './sidebar.js';
import { renderExportBtn, showExportInfo } from './export.js';
import { renderSuggestions, loadSuggestions, syncFilterButtons } from './suggestions.js';
import { renderCommon, loadCommon, loadDiff } from './sources.js';
import { renderShare } from './share.js';
import { mutateTracks, undo } from './undo.js';
import { savedOwner } from './wizard.js';

let dragFrom = null;

export function contributors() {
  const counts = new Map();
  state.current.tracks.forEach((t) => {
    const who = byName(t);
    if (who) counts.set(who, (counts.get(who) || 0) + 1);
  });
  return counts;
}

function renderContributors() {
  const counts = contributors();
  if (state.byFilterName && !counts.has(state.byFilterName)) state.byFilterName = null;
  const box = $('byFilter');
  box.hidden = counts.size < 2;
  if (counts.size < 2) {
    state.byFilterName = null;
    return;
  }
  const all = `<button type="button" class="toggle" data-by="" aria-pressed="${!state.byFilterName}">All (${state.current.tracks.length})</button>`;
  const people = [...counts].map(([who, count]) =>
    `<button type="button" class="toggle" data-by="${esc(who)}" aria-pressed="${state.byFilterName === who}">${esc(who)} (${count})</button>`);
  box.innerHTML = all + people.join('');
}

export function renderAll() {
  const { current } = state;
  const main = mainSource();
  $('title').value = current.name;
  $('count').textContent = current.tracks.length;
  $('undoBtn').disabled = state.undoStack.length === 0;
  $('hiddenN').textContent = current.dislikeCount || 0;
  renderExportBtn();
  syncFilterButtons();
  $('refUrl').value = main ? main.url : '';
  $('refName').textContent = main ? `${main.name} · ${main.tracks.length} tracks` : '';
  $('addAllRef').hidden = !main;
  $('diffBtn').disabled = current.sources.length === 0;
  $('diffBtn').title = current.sources.length === 0
    ? 'Add a reference playlist to compare'
    : 'Compare your playlist with the reference';
  setBadge('refN', main ? main.tracks.length : 0);
  setBadge('cmpN', otherSources().length);

  renderContributors();
  const filtered = !!state.byFilterName;
  const visible = filtered ? current.tracks.filter((t) => byName(t) === state.byFilterName) : current.tracks;
  $('tracks').dataset.empty = filtered
    ? 'No tracks from this person.'
    : 'Empty for now. Add tracks from the Search and Suggestions tabs, then drag to reorder.';
  fill('tracks', visible, (t) => {
    const i = current.tracks.indexOf(t);
    const reorder = filtered ? '' :
      `<button class="icon" data-up="${i}" title="Move up" ${i === 0 ? 'disabled' : ''}>${ICON.up}</button>
       <button class="icon" data-down="${i}" title="Move down" ${i === current.tracks.length - 1 ? 'disabled' : ''}>${ICON.down}</button>`;
    return reorder + `<button class="icon danger" data-del="${t.deezerId}" title="Remove from playlist">${ICON.trash}</button>`;
  }, !filtered);
  fill('results', state.lastResults, addBtn);
  if (!state.styleDirty) $('styleInput').value = current.style || '';
  const sources = state.styleSources.length ? ' · sources: ' + state.styleSources.join(', ') : '';
  $('sugHint').textContent = current.style
    ? `Following the style you entered${sources}.`
    : 'Set a style to guide the suggestions.';
  $('suggestions').dataset.empty = current.tracks.length === 0 && current.sources.length === 0 && !current.style
    ? 'Describe a style above, or add tracks or a reference playlist, to get suggestions.'
    : 'No suggestions right now. Try another style, turn off a filter or press "New suggestions".';
  renderSuggestions();
  fill('reference', main ? main.tracks : [], addBtn);
  renderCommon();
  $('sourcesList').innerHTML = otherSources().map((s) =>
    `<li><span class="t"><a href="${esc(s.url)}" target="_blank" rel="noopener">${esc(s.name)}</a></span>
       <span class="badge">${s.tracks.length} tracks</span>
       <button class="ghost danger" data-delsource="${s.id}" title="Remove this comparison playlist">Remove</button></li>`).join('');
}

export function setTab(name) {
  state.activeTab = name;
  document.querySelectorAll('.tabs [data-tab]').forEach((b) => {
    const on = b.dataset.tab === name;
    b.classList.toggle('active', on);
    b.setAttribute('aria-selected', String(on));
  });
  document.querySelectorAll('.tabpanel').forEach((p) => (p.hidden = p.dataset.panel !== name));
  if (name === 'search' && matchMedia('(hover: hover)').matches) $('q').focus();
}

export function update(playlist) {
  state.current = playlist;
  renderAll();
  loadList();
  guard(loadCommon);
  if ($('diffDialog').open) guard(loadDiff);
  if ($('shareDialog').open) renderShare();
}

export async function openPlaylist(id) {
  state.current = await api('/playlists/' + id);
  state.styleDirty = false;
  state.lastResults = [];
  state.lastSuggestions = [];
  state.commonList = [];
  state.diffData = null;
  state.undoStack = [];
  state.bpmById = {};
  state.dislikedList = [];
  state.byFilterName = null;
  state.lastRev = null;
  if (!state.current.ownerName && savedOwner()) {
    try {
      state.current = await api('/playlists/' + id, { method: 'PATCH', body: { ownerName: savedOwner() } });
    } catch {
      // continue without an owner name
    }
  }
  api(`/playlists/${id}/rev`)
    .then((r) => {
      if (state.current && state.current.id === id && state.lastRev === null) state.lastRev = r.rev;
    })
    .catch(() => {});
  try {
    localStorage.setItem('lastPlaylist', id);
  } catch {
    // preference is not persisted
  }
  $('wizard').hidden = true;
  document.body.classList.remove('wizard-mode');
  $('workspace').hidden = false;
  showExportInfo();
  renderAll();
  const { current } = state;
  setTab(current.tracks.length === 0 && current.sources.length === 0 && !current.style ? 'search' : 'suggestions');
  loadList();
  guard(loadCommon);
  loadSuggestions();
}

function move(from, to) {
  const { current } = state;
  if (from === to || to < 0 || to >= current.tracks.length) return;
  const ids = current.tracks.map((t) => t.deezerId);
  ids.splice(to, 0, ids.splice(from, 1)[0]);
  guard(() => mutateTracks(() => api(`/playlists/${current.id}/order`, { method: 'PUT', body: ids })));
}

const clearOver = () =>
  document.querySelectorAll('#tracks .over, #tracks .dragging').forEach((el) => el.classList.remove('over', 'dragging'));

function initDrag() {
  $('tracks').addEventListener('dragstart', (e) => {
    const li = e.target.closest('.track');
    if (!li) return;
    dragFrom = [...$('tracks').children].indexOf(li);
    e.dataTransfer.effectAllowed = 'move';
    e.dataTransfer.setData('text/plain', String(dragFrom));
    li.classList.add('dragging');
  });
  $('tracks').addEventListener('dragover', (e) => {
    if (dragFrom === null) return;
    e.preventDefault();
    document.querySelectorAll('#tracks .over').forEach((el) => el.classList.remove('over'));
    e.target.closest('.track')?.classList.add('over');
  });
  $('tracks').addEventListener('drop', (e) => {
    if (dragFrom === null) return;
    e.preventDefault();
    const li = e.target.closest('.track');
    const to = li ? [...$('tracks').children].indexOf(li) : state.current.tracks.length - 1;
    const from = dragFrom;
    dragFrom = null;
    clearOver();
    move(from, to);
  });
  $('tracks').addEventListener('dragend', () => {
    dragFrom = null;
    clearOver();
  });
}

export function init() {
  $('byFilter').addEventListener('click', (e) => {
    const button = e.target.closest('[data-by]');
    if (!button) return;
    state.byFilterName = button.dataset.by || null;
    renderAll();
  });

  document.querySelector('.tabs').addEventListener('click', (e) => {
    const tab = e.target.closest('[data-tab]');
    if (tab) setTab(tab.dataset.tab);
  });
  document.addEventListener('keydown', (e) => {
    const typing = /INPUT|TEXTAREA|SELECT/.test(document.activeElement.tagName);
    if (e.key === '/' && state.current && !typing && !$('workspace').hidden) {
      e.preventDefault();
      setTab('search');
      $('q').focus();
    }
  });

  $('title').addEventListener('change', () => guard(async () => {
    if (!$('title').value.trim()) {
      $('title').value = state.current.name;
      return;
    }
    update(await api('/playlists/' + state.current.id, { method: 'PATCH', body: { name: $('title').value } }));
  }));
  $('title').addEventListener('keydown', (e) => {
    if (e.key === 'Enter') e.target.blur();
  });

  document.addEventListener('click', (e) => {
    const add = e.target.closest('[data-add]');
    if (add && !add.disabled) {
      const id = Number(add.dataset.add);
      const track = known().find((x) => x.deezerId === id);
      return guard(async () => {
        state.lastSuggestions = state.lastSuggestions.filter((x) => x.deezerId !== id);
        await mutateTracks(() => api(`/playlists/${state.current.id}/tracks`, { method: 'POST', body: track }));
      });
    }
    const del = e.target.closest('[data-del]');
    if (del) {
      return guard(async () => {
        const track = state.current.tracks.find((x) => x.deezerId === Number(del.dataset.del));
        await mutateTracks(() => api(`/playlists/${state.current.id}/tracks/${del.dataset.del}`, { method: 'DELETE' }));
        toast(`"${track ? track.title : 'Track'}" removed.`, false, { label: 'Undo', fn: undo });
      });
    }
    const up = e.target.closest('[data-up]');
    const down = e.target.closest('[data-down]');
    if (up || down) {
      const index = Number((up || down).dataset[up ? 'up' : 'down']);
      move(index, up ? index - 1 : index + 1);
    }
  });

  initDrag();

  $('searchForm').addEventListener('submit', (e) => {
    e.preventDefault();
    if (!$('q').value.trim()) return;
    busy(e.target.querySelector('button'), 'Searching…', async () => {
      state.lastResults = await api('/search?q=' + encodeURIComponent($('q').value));
      $('results').dataset.empty = 'Nothing found. Try another term.';
      fill('results', state.lastResults, addBtn);
    });
  });
}
