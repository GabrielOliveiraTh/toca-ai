import { state } from './state.js';
import { $, ICON, esc } from './dom.js';
import { byName, initial, gsvg, centerGlyphs } from './avatar.js';
import { fullDate, ago } from './time.js';
import { mark } from './player.js';

function trackRow(track, buttons, draggable) {
  const background = track.cover ? `style="background-image:url('${esc(track.cover)}')"` : '';
  const badge = track.badge
    ? `<em class="badge solid" title="${esc(track.hint || '')}">${esc(track.badge)}</em>`
    : '';
  const who = byName(track);
  const author = who
    ? `<span class="by ${track.addedById === 'owner' ? 'me' : ''}" title="Added by ${esc(who)} on ${esc(fullDate(track.addedAt))}">
         <i class="av">${gsvg(initial(who), 20, 11)}</i><span class="by-name">${esc(who)}</span><small>${esc(ago(track.addedAt))}</small></span>`
    : '';
  const bpm = state.bpmById[track.deezerId] ? ' · ' + Math.round(state.bpmById[track.deezerId]) + ' bpm' : '';
  return `<li class="track" data-id="${track.deezerId}" ${draggable ? 'draggable="true"' : ''}>
    ${draggable ? `<span class="grip" title="Drag to reorder">${ICON.grip}</span>` : ''}
    <div class="cover" ${background} data-play="${track.deezerId}" role="button" tabindex="0" aria-label="Play preview of ${esc(track.title)}"></div>
    <div class="info"><b title="${esc(track.title)}">${esc(track.title)}</b><span>${esc(track.artist)}${track.album ? ' · ' + esc(track.album) : ''}${bpm}</span></div>
    ${author}${badge}<div class="actions">${buttons}</div></li>`;
}

export function fill(listId, tracks, buttonsFor, draggable) {
  $(listId).innerHTML = tracks.map((t, i) => trackRow(t, buttonsFor(t, i), draggable)).join('');
  mark();
  centerGlyphs();
}

export const isMine = (track) =>
  state.current.tracks.some((x) => x.deezerId === track.deezerId || (track.isrc && x.isrc === track.isrc));

export function addBtn(track) {
  const has = isMine(track);
  return `<button class="icon add" data-add="${track.deezerId}" title="${has ? 'Already in the playlist' : 'Add to playlist'}" ${has ? 'disabled' : ''}>${has ? ICON.check : ICON.plus}</button>`;
}

export function known() {
  return [
    ...state.lastResults,
    ...state.lastSuggestions,
    ...state.commonList.map((c) => c.track),
    ...state.current.sources.flatMap((s) => s.tracks),
    ...(state.diffData ? state.diffData.onlySource : []),
  ];
}

export function setBadge(id, count) {
  $(id).textContent = count;
  $(id).hidden = !count;
}

export const dislikeBtn = (track) =>
  `<button class="icon danger" data-dislike="${track.deezerId}" title="Don't suggest again">${ICON.thumbDown}</button>`;
