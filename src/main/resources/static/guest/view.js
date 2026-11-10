import { state } from './api.js';
import { $, esc, ICON } from './dom.js';
import { markPlaying } from './player.js';
import { loadSuggestions, startPolling } from './main.js';

const SCREENS = ['screenLoading', 'screenDead', 'screenName', 'screenMain'];
const TABS = { list: 'panel-list', search: 'panel-search', sugg: 'panel-sugg' };

export function show(id) {
  SCREENS.forEach((screen) => {
    $(screen).hidden = screen !== id;
  });
}

export function showDead(message, retry) {
  state.dead = true;
  clearInterval(state.pollTimer);
  $('deadMsg').textContent = message || 'This link is no longer active. Ask the owner for a new one.';
  $('deadRetry').hidden = !retry;
  show('screenDead');
}

export function showNameScreen({ rename = false } = {}) {
  const { info, guest } = state;
  $('nameTitle').innerHTML = `Help build "${esc(info.playlistName)}"`;
  $('nameHint').textContent = rename
    ? 'Correct your name. The tracks you already added stay yours.'
    : `This playlist belongs to ${info.ownerName}. Enter your name so everyone knows who added each track.`;
  $('nameInput').value = rename && guest ? guest.name : '';
  $('nameBtn').textContent = rename ? 'Save' : 'Join';
  $('nameCancel').hidden = !(rename && guest);
  show('screenName');
  setTimeout(() => $('nameInput').focus(), 30);
}

export function showMain() {
  show('screenMain');
  renderAll();
  startPolling();
}

function ago(iso) {
  if (!iso) return '';
  const seconds = Math.max(0, (Date.now() - new Date(iso).getTime()) / 1000);
  if (seconds < 45) return 'just now';
  if (seconds < 3600) return `${Math.max(1, Math.round(seconds / 60))} min ago`;
  if (seconds < 86400) return `${Math.round(seconds / 3600)} h ago`;
  return new Date(iso).toLocaleDateString('en-US', { day: '2-digit', month: '2-digit' });
}

const fullDate = (iso) =>
  (iso ? new Date(iso).toLocaleString('en-US', { dateStyle: 'short', timeStyle: 'short' }) : '');

function row(track, actions, { mine = false } = {}) {
  const id = Number(track.deezerId);
  const background = track.cover ? ` style="background-image:url('${esc(track.cover)}')"` : '';
  const owner = track.byOwner ? ' (owner)' : '';
  const when = track.addedAt ? ' · ' + esc(ago(track.addedAt)) : '';
  const author = track.addedBy
    ? `<span class="by" title="${esc(fullDate(track.addedAt))}">by ${esc(track.addedBy)}${owner}${when}</span>`
    : '';
  return `<li class="track${mine ? ' mine' : ''}" data-id="${id}">
      <div class="cover"${background} data-play="${id}" role="button" tabindex="0" aria-label="Play preview of ${esc(track.title)}"></div>
      <div class="info"><b title="${esc(track.title)}">${esc(track.title)}</b><span>${esc(track.artist)}${track.album ? ' · ' + esc(track.album) : ''}</span>${author}</div>
      <div class="actions">${actions}</div></li>`;
}

const inPlaylist = (track) => state.info.tracks.some((x) => x.deezerId === track.deezerId);

const addBtn = (track) => (inPlaylist(track)
  ? `<button class="icon" disabled aria-label="Already in the playlist" title="Already in the playlist">${ICON.check}</button>`
  : `<button class="icon add" data-add="${Number(track.deezerId)}" aria-label="Add ${esc(track.title)}" title="Add to playlist">${ICON.plus}</button>`);

function renderHeader() {
  const { info, guest } = state;
  $('plName').textContent = info.playlistName;
  $('plStyle').textContent = info.style ? 'Style: ' + info.style : '';
  $('plStyle').hidden = !info.style;
  $('meName').textContent = guest.name;
  $('notMeBtn').textContent = 'not ' + guest.name;
  $('countBadge').textContent = info.tracks.length;
}

function renderChips() {
  const counts = new Map();
  state.info.tracks.forEach((track) => {
    const key = track.addedBy || '—';
    const entry = counts.get(key) || { n: 0, owner: false };
    entry.n++;
    if (track.byOwner) entry.owner = true;
    counts.set(key, entry);
  });
  if (state.filterBy && !counts.has(state.filterBy)) state.filterBy = null;
  const box = $('chips');
  box.hidden = counts.size < 2;
  if (counts.size < 2) {
    state.filterBy = null;
    box.innerHTML = '';
    return;
  }
  const all = `<button type="button" class="toggle" data-by="" aria-pressed="${state.filterBy === null}">All (${state.info.tracks.length})</button>`;
  const people = [...counts].map(([name, entry]) =>
    `<button type="button" class="toggle" data-by="${esc(name)}" aria-pressed="${state.filterBy === name}">${esc(name)}${entry.owner ? ' (owner)' : ''} (${entry.n})</button>`);
  box.innerHTML = all + people.join('');
}

export function renderList() {
  renderChips();
  const { info, filterBy } = state;
  const shown = filterBy ? info.tracks.filter((t) => (t.addedBy || '—') === filterBy) : info.tracks;
  $('list').innerHTML = shown.map((track) => {
    const remove = track.mine
      ? `<button class="icon danger" data-del="${Number(track.deezerId)}" aria-label="Remove ${esc(track.title)}" title="Remove (you added it)">${ICON.trash}</button>`
      : '';
    return row(track, remove, { mine: track.mine });
  }).join('');
  markPlaying();
}

export function renderResults() {
  $('results').innerHTML = state.results.map((t) => row(t, addBtn(t))).join('');
  markPlaying();
}

export function renderSuggestions() {
  const list = $('sugg');
  if (state.suggestionsLoading) {
    list.innerHTML = '<li class="loading">Finding suggestions… the first search can take a few seconds.</li>';
    return;
  }
  const items = (state.suggestions || []).filter((t) => !inPlaylist(t));
  list.innerHTML = items.map((t) => row(t, addBtn(t))).join('');
  markPlaying();
}

export function renderAll() {
  renderHeader();
  renderList();
  renderResults();
  renderSuggestions();
}

export function applyInfo(next) {
  const known = new Set((state.info ? state.info.tracks : []).map((t) => t.deezerId));
  const fresh = next.tracks.filter((t) => !known.has(t.deezerId) && !t.mine);
  state.info = next;
  if (!$('screenMain').hidden) renderAll();
  return fresh;
}

function setTab(name) {
  document.querySelectorAll('.tabs [data-tab]').forEach((b) => {
    const on = b.dataset.tab === name;
    b.classList.toggle('active', on);
    b.setAttribute('aria-selected', String(on));
  });
  Object.entries(TABS).forEach(([key, id]) => {
    $(id).hidden = key !== name;
  });
  if (name === 'search' && matchMedia('(hover: hover)').matches) $('q').focus();
  if (name === 'sugg' && state.suggestions === null && !state.suggestionsLoading) loadSuggestions();
}

export function init() {
  document.querySelector('.tabs').addEventListener('click', (e) => {
    const tab = e.target.closest('[data-tab]');
    if (tab) setTab(tab.dataset.tab);
  });
  document.querySelector('.tabs').addEventListener('keydown', (e) => {
    if (e.key !== 'ArrowRight' && e.key !== 'ArrowLeft') return;
    const names = Object.keys(TABS);
    const current = names.findIndex((n) => $('tab-' + n).getAttribute('aria-selected') === 'true');
    const next = names[(current + (e.key === 'ArrowRight' ? 1 : names.length - 1)) % names.length];
    setTab(next);
    $('tab-' + next).focus();
  });
}
