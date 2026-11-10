import { state } from './state.js';
import { $, ICON } from './dom.js';
import { toast } from './toast.js';

const audio = new Audio();
const player = { queue: [], index: -1, auto: true, failures: 0 };

try {
  player.auto = localStorage.getItem('plAuto') !== '0';
} catch {
  // keep default
}

const formatTime = (seconds) => `${Math.floor(seconds / 60)}:${String(Math.floor(seconds % 60)).padStart(2, '0')}`;

export function itemFromRow(li) {
  return {
    id: Number(li.dataset.id),
    title: li.querySelector('.info b')?.textContent || '',
    artist: (li.querySelector('.info span')?.textContent || '').split(' · ')[0],
    cover: li.querySelector('.cover')?.style.backgroundImage || '',
  };
}

export const rowsOf = (ul) => [...ul.querySelectorAll('.track')].map(itemFromRow);

export function mark() {
  const paused = audio.paused;
  document.querySelectorAll('.track').forEach((el) => {
    const on = Number(el.dataset.id) === state.playingId;
    el.classList.toggle('playing', on);
    el.classList.toggle('paused', on && paused);
  });
  $('plPlay').innerHTML = paused ? ICON.play : ICON.pause;
  $('plPlay').setAttribute('aria-label', paused ? 'Play' : 'Pause');
}

function playFromList(ul, id) {
  if (state.playingId === id) return toggleAudio();
  const items = rowsOf(ul);
  startQueue(items, Math.max(0, items.findIndex((item) => item.id === id)));
}

function startQueue(items, index) {
  if (!items.length) return toast('There are no tracks to play in this list.', true);
  player.queue = items;
  player.index = index;
  player.failures = 0;
  playCurrent();
}

function playCurrent() {
  const item = player.queue[player.index];
  if (!item) return stopPlayer();
  state.playingId = item.id;
  audio.src = '/api/preview/' + item.id;
  audio.play().catch(() => skipUnplayable());
  $('player').hidden = false;
  document.body.classList.add('has-player');
  $('plTitle').textContent = item.title;
  $('plArtist').textContent = item.artist;
  $('plCover').style.backgroundImage = item.cover;
  $('plSeek').value = 0;
  $('plTime').textContent = '0:00';
  mark();
}

function skipUnplayable() {
  player.failures += 1;
  if (player.failures === 1) toast('Some tracks have no preview; skipping them.', true);
  if (player.failures >= player.queue.length) return stopPlayer();
  nextTrack();
}

function nextTrack() {
  if (player.index + 1 < player.queue.length) {
    player.index++;
    playCurrent();
  } else {
    stopPlayer();
  }
}

function prevTrack() {
  if (audio.currentTime > 3 || player.index === 0) {
    audio.currentTime = 0;
    return;
  }
  player.index--;
  playCurrent();
}

function toggleAudio() {
  if (!audio.src || player.index < 0) return;
  if (audio.paused) audio.play().catch(() => skipUnplayable());
  else audio.pause();
}

function stopPlayer() {
  audio.pause();
  audio.removeAttribute('src');
  state.playingId = null;
  player.queue = [];
  player.index = -1;
  $('player').hidden = true;
  document.body.classList.remove('has-player');
  mark();
}

export function init() {
  audio.addEventListener('play', () => {
    player.failures = 0;
    mark();
  });
  audio.addEventListener('pause', mark);
  audio.addEventListener('ended', () => {
    if (player.auto) {
      nextTrack();
    } else {
      audio.currentTime = 0;
      mark();
    }
  });
  audio.addEventListener('error', () => {
    if (state.playingId) skipUnplayable();
  });
  audio.addEventListener('timeupdate', () => {
    if (!audio.duration) return;
    $('plSeek').value = Math.round((audio.currentTime / audio.duration) * 1000);
    $('plTime').textContent = `${formatTime(audio.currentTime)} / ${formatTime(audio.duration)}`;
  });

  $('plPrev').innerHTML = ICON.prev;
  $('plNext').innerHTML = ICON.next;
  $('plClose').innerHTML = ICON.x;
  $('plPlay').innerHTML = ICON.play;
  $('plPrev').addEventListener('click', prevTrack);
  $('plNext').addEventListener('click', nextTrack);
  $('plPlay').addEventListener('click', toggleAudio);
  $('plClose').addEventListener('click', stopPlayer);
  $('plSeek').addEventListener('input', () => {
    if (audio.duration) audio.currentTime = (audio.duration * $('plSeek').value) / 1000;
  });
  $('plAuto').setAttribute('aria-pressed', String(player.auto));
  $('plAuto').addEventListener('click', () => {
    player.auto = !player.auto;
    $('plAuto').setAttribute('aria-pressed', String(player.auto));
    try {
      localStorage.setItem('plAuto', player.auto ? '1' : '0');
    } catch {
      // preference is not persisted
    }
  });

  $('listenMine').addEventListener('click', () => startQueue(rowsOf($('tracks')), 0));
  $('listenSug').addEventListener('click', () => startQueue(rowsOf($('suggestions')), 0));

  document.addEventListener('keydown', (e) => {
    if ((e.key === 'Enter' || e.key === ' ') && e.target.matches('.cover')) {
      e.preventDefault();
      playFromList(e.target.closest('ul'), Number(e.target.dataset.play));
      return;
    }
    if ($('player').hidden || e.ctrlKey || e.metaKey || e.altKey) return;
    const active = document.activeElement;
    if (/INPUT|TEXTAREA|SELECT|BUTTON/.test(active.tagName) || active.isContentEditable) return;
    if (document.querySelector('dialog[open]')) return;
    if (e.key === ' ') {
      e.preventDefault();
      toggleAudio();
    } else if (e.key === 'ArrowRight') {
      e.preventDefault();
      nextTrack();
    } else if (e.key === 'ArrowLeft') {
      e.preventDefault();
      prevTrack();
    }
  });

  document.addEventListener('click', (e) => {
    const play = e.target.closest('[data-play]');
    if (play) playFromList(play.closest('ul'), Number(play.dataset.play));
  });
}
