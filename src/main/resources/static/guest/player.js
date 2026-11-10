import { toast } from './dom.js';

const audio = new Audio();
let playingId = null;

export function markPlaying() {
  document.querySelectorAll('.track').forEach((el) => {
    const on = Number(el.dataset.id) === playingId;
    el.classList.toggle('playing', on);
    el.classList.toggle('paused', on && audio.paused);
  });
}

function previewFailed() {
  toast('No preview available for this track.', true);
  playingId = null;
  markPlaying();
}

export function playPreview(id) {
  if (playingId === id) {
    if (audio.paused) audio.play().catch(() => {});
    else audio.pause();
    return markPlaying();
  }
  playingId = id;
  audio.src = '/api/preview/' + id;
  audio.play().catch(previewFailed);
  markPlaying();
}

export function init() {
  audio.addEventListener('play', markPlaying);
  audio.addEventListener('pause', markPlaying);
  audio.addEventListener('ended', () => {
    playingId = null;
    markPlaying();
  });
  audio.addEventListener('error', () => {
    if (playingId) previewFailed();
  });
}
