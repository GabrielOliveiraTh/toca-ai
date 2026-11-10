import { state, api, saveGuest, forgetGuest, newId } from './api.js';
import { $, toast, act } from './dom.js';
import {
  show, showDead, showNameScreen, showMain, renderList, renderResults, renderSuggestions, applyInfo,
  init as initView,
} from './view.js';
import { playPreview, init as initPlayer } from './player.js';

const POLL_MS = 4000;
let polling = false;

export async function loadSuggestions() {
  state.suggestionsLoading = true;
  renderSuggestions();
  try {
    state.suggestions = await api('/suggestions');
  } catch (e) {
    state.suggestions = [];
    if (e.status === 404) return showDead();
    toast(e.message, true);
  } finally {
    state.suggestionsLoading = false;
    renderSuggestions();
  }
}

function addTrack(id, btn) {
  const track = [...state.results, ...(state.suggestions || [])].find((x) => Number(x.deezerId) === id);
  if (!track) return;
  act(btn, async () => {
    applyInfo(await api('/tracks', { method: 'POST', body: { track, name: state.guest.name } }));
    toast(`"${track.title}" was added to the playlist.`);
  });
}

function removeTrack(id, btn) {
  act(btn, async () => {
    const track = state.info.tracks.find((x) => Number(x.deezerId) === id);
    applyInfo(await api('/tracks/' + id, { method: 'DELETE' }));
    toast(track ? `"${track.title}" was removed.` : 'Track removed.');
  });
}

async function poll() {
  const hidden = document.visibilityState !== 'visible';
  if (state.dead || polling || state.inflight > 0 || hidden || !state.guest || $('screenMain').hidden) return;
  polling = true;
  try {
    const next = await api('/info');
    if (state.inflight === 0 && next.rev !== state.info.rev) {
      const fresh = applyInfo(next);
      fresh.slice(0, 2).forEach((t, i) => setTimeout(() => toast(`${t.addedBy} added "${t.title}"`), i * 2200));
    }
  } catch (e) {
    if (e.status === 404) showDead();
  } finally {
    polling = false;
  }
}

export function startPolling() {
  clearInterval(state.pollTimer);
  state.pollTimer = setInterval(poll, POLL_MS);
}

async function init() {
  show('screenLoading');
  try {
    state.info = await api('/info');
  } catch (e) {
    if (e.status === 404) return showDead();
    return showDead(e.message, true);
  }
  if (!state.guest) showNameScreen();
  else showMain();
}

function bindEvents() {
  $('suggReload').addEventListener('click', (e) => act(e.currentTarget, loadSuggestions));

  $('searchForm').addEventListener('submit', (e) => {
    e.preventDefault();
    const query = $('q').value.trim();
    if (!query) return;
    act($('searchBtn'), async () => {
      state.results = await api('/search?q=' + encodeURIComponent(query));
      $('results').dataset.empty = 'Nothing found. Try another term.';
      renderResults();
    });
  });

  document.addEventListener('click', (e) => {
    const add = e.target.closest('[data-add]');
    if (add) return addTrack(Number(add.dataset.add), add);
    const del = e.target.closest('[data-del]');
    if (del) return removeTrack(Number(del.dataset.del), del);
    const by = e.target.closest('[data-by]');
    if (by) {
      state.filterBy = by.dataset.by || null;
      renderList();
      return;
    }
    const play = e.target.closest('[data-play]');
    if (play) playPreview(Number(play.dataset.play));
  });
  document.addEventListener('keydown', (e) => {
    if ((e.key === 'Enter' || e.key === ' ') && e.target.matches && e.target.matches('.cover')) {
      e.preventDefault();
      playPreview(Number(e.target.dataset.play));
    }
  });

  $('nameForm').addEventListener('submit', (e) => {
    e.preventDefault();
    const name = $('nameInput').value.replace(/\s+/g, ' ').trim();
    if (!name) {
      toast('Enter your name to continue.', true);
      $('nameInput').focus();
      return;
    }
    state.guest = { id: state.guest ? state.guest.id : newId(), name: name.slice(0, 40) };
    saveGuest();
    showMain();
    $('meName').textContent = state.guest.name;
  });
  $('nameCancel').addEventListener('click', () => show('screenMain'));
  $('renameBtn').addEventListener('click', () => showNameScreen({ rename: true }));
  $('notMeBtn').addEventListener('click', () => {
    const name = state.guest.name;
    if (!confirm(`Not ${name}? We will ask for a new name. Only ${name} will be able to remove their tracks.`)) return;
    forgetGuest();
    showNameScreen();
  });

  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible') poll();
  });
  setInterval(() => {
    if (!state.dead && state.info && !$('screenMain').hidden && !state.inflight) renderList();
  }, 30000);
  $('deadRetry').addEventListener('click', init);
}

initView();
initPlayer();
bindEvents();
init();
