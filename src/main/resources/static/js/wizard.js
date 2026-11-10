import { state } from './state.js';
import { $, esc, busy } from './dom.js';
import { api } from './api.js';
import { toast } from './toast.js';
import { linkProblem, sourceLabel } from './links.js';
import { connectSpotify } from './spotify.js';
import { loadList } from './sidebar.js';
import { openPlaylist } from './workspace.js';

const LAST_STEP = 5;
const STEP_FIELD = { 1: 'wNext', 2: 'wName', 3: 'wStyle', 4: 'wRef', 5: 'wSrc' };

export const savedOwner = () => {
  try {
    return localStorage.getItem('ownerName') || '';
  } catch {
    return '';
  }
};

const newWizard = () => ({ step: 1, owner: savedOwner(), name: '', style: '', ref: '', sources: [] });

let wiz = newWizard();

const saveWizard = () => {
  try {
    sessionStorage.setItem('wiz', JSON.stringify(wiz));
  } catch {
    // progress is not persisted
  }
};

export function restoreWizard(saved) {
  wiz = saved;
}

function focusStep(step) {
  const id = step === 2 && !$('wOwner').value.trim() ? 'wOwner' : STEP_FIELD[step];
  setTimeout(() => {
    const el = $(id);
    if (el && !el.disabled) el.focus();
  }, 30);
}

function renderWizard() {
  const needsSpotify = !state.spotify.connected;
  document.querySelectorAll('.wstep').forEach((el) => (el.hidden = Number(el.dataset.step) !== wiz.step));
  document.querySelectorAll('.steps li').forEach((li) => {
    const n = Number(li.dataset.step);
    li.classList.toggle('current', n === wiz.step);
    li.classList.toggle('done', n < wiz.step);
    if (n === wiz.step) li.setAttribute('aria-current', 'step');
    else li.removeAttribute('aria-current');
  });
  $('wWelcome').hidden = state.playlistCount > 0;
  $('wCancel').hidden = state.playlistCount === 0;
  $('wBack').hidden = wiz.step === 1;
  $('wSkip').hidden = wiz.step < 3;
  $('wNext').textContent = wiz.step === LAST_STEP ? 'Create playlist'
    : wiz.step === 1 && needsSpotify && state.spotify.configured ? 'Continue without connecting' : 'Continue';

  const box = $('wSpotify');
  if (state.spotify.connected) {
    box.innerHTML = '<span class="dot on"></span> Spotify connected. All set';
  } else if (!state.spotify.configured) {
    box.innerHTML = 'Spotify is not configured: set <code>SPOTIFY_CLIENT_ID</code> (see the README) and restart the app.';
  } else {
    box.innerHTML = '<span class="dot"></span> Spotify disconnected <button type="button" id="wConnect" class="primary">Connect Spotify</button>';
  }

  document.querySelectorAll('[data-needs-spotify]').forEach((el) => (el.hidden = !needsSpotify));
  $('wStyleHint').textContent = state.styleSources.length ? `Search sources: ${state.styleSources.join(', ')}.` : '';
  $('wSrcList').innerHTML = wiz.sources.map((url, i) =>
    `<li><span class="t"><span class="badge">${sourceLabel(url)}</span> ${esc(url)}</span><button type="button" class="ghost danger" data-wdel="${i}">Remove</button></li>`).join('');

  const reference = wiz.ref
    ? `<span class="t"><span class="badge">${sourceLabel(wiz.ref)}</span> ${esc(wiz.ref)}</span><button type="button" class="ghost danger" data-wclearref>Remove</button>`
    : '<b>None</b>';
  const comparison = wiz.sources.length
    ? wiz.sources.length + (wiz.sources.length > 1 ? ' playlists' : ' playlist')
    : 'None';
  $('wSummary').innerHTML = `<div><span class="muted">You</span><b>${esc(wiz.owner || '—')}</b></div>
    <div><span class="muted">Playlist</span><b>${esc(wiz.name || '—')}</b></div>
    <div><span class="muted">Style</span><b>${wiz.style ? esc(wiz.style) : 'None'}</b></div>
    <div><span class="muted">Reference</span>${reference}</div>
    <div><span class="muted">Comparison</span><b>${comparison}</b></div>`;
}

function goToStep(step) {
  wiz.step = step;
  renderWizard();
  saveWizard();
  focusStep(step);
}

export function openWizard(restore) {
  if (!restore) wiz = newWizard();
  wiz.style = wiz.style || '';
  wiz.owner = wiz.owner || savedOwner();
  $('workspace').hidden = true;
  $('wizard').hidden = false;
  document.body.classList.add('wizard-mode');
  $('wOwner').value = wiz.owner;
  $('wName').value = wiz.name;
  $('wStyle').value = wiz.style;
  $('wRef').value = wiz.ref;
  $('wSrc').value = '';
  renderWizard();
  saveWizard();
  focusStep(wiz.step);
}

function addSource() {
  const value = $('wSrc').value.trim();
  if (!value) return false;
  const problem = linkProblem(value);
  if (problem) {
    toast(problem, true);
    return true;
  }
  if (!wiz.sources.includes(value)) wiz.sources.push(value);
  $('wSrc').value = '';
  renderWizard();
  saveWizard();
  return true;
}

async function finish() {
  const button = $('wNext');
  const issues = [];
  await busy(button, 'Creating…', async () => {
    const playlist = await api('/playlists', { method: 'POST', body: { name: wiz.name, style: wiz.style, ownerName: wiz.owner } });
    if (wiz.ref) {
      button.textContent = 'Importing reference…';
      try {
        await api(`/playlists/${playlist.id}/reference`, { method: 'PUT', body: { url: wiz.ref } });
      } catch (e) {
        issues.push(e.message);
      }
    }
    for (let i = 0; i < wiz.sources.length; i++) {
      button.textContent = `Importing comparison ${i + 1}/${wiz.sources.length}…`;
      try {
        await api(`/playlists/${playlist.id}/sources`, { method: 'POST', body: { url: wiz.sources[i] } });
      } catch (e) {
        issues.push(e.message);
      }
    }
    try {
      sessionStorage.removeItem('wiz');
    } catch {
      // nothing to clean up
    }
    await openPlaylist(playlist.id);
    if (issues.length) {
      const total = (wiz.ref ? 1 : 0) + wiz.sources.length;
      toast(`Playlist created, but ${issues.length} of ${total} imports failed. ${[...new Set(issues)].join(' ')}`, true);
    } else {
      toast('Playlist created.');
    }
  });
}

function submitStep(e) {
  e.preventDefault();
  if (wiz.step === 1) return goToStep(2);
  if (wiz.step === 2) {
    wiz.owner = $('wOwner').value.trim();
    wiz.name = $('wName').value.trim();
    if (!wiz.owner) {
      toast('Enter your name: it appears on the tracks you add.', true);
      $('wOwner').focus();
      return;
    }
    if (!wiz.name) {
      toast('Give the playlist a name.', true);
      $('wName').focus();
      return;
    }
    try {
      localStorage.setItem('ownerName', wiz.owner);
    } catch {
      // name is not remembered
    }
    return goToStep(3);
  }
  if (wiz.step === 3) {
    wiz.style = $('wStyle').value.trim();
    return goToStep(4);
  }
  if (wiz.step === 4) {
    wiz.ref = $('wRef').value.trim();
    const problem = wiz.ref && linkProblem(wiz.ref);
    if (problem) {
      toast(problem, true);
      return;
    }
    return goToStep(5);
  }
  if (addSource()) return;
  finish();
}

export function init() {
  $('wForm').addEventListener('submit', submitStep);
  $('wBack').addEventListener('click', () => goToStep(wiz.step - 1));
  $('wSkip').addEventListener('click', () => {
    if (wiz.step === 3) {
      wiz.style = '';
      $('wStyle').value = '';
      goToStep(4);
    } else if (wiz.step === 4) {
      wiz.ref = '';
      $('wRef').value = '';
      goToStep(5);
    } else {
      wiz.sources = [];
      renderWizard();
      finish();
    }
  });
  $('wForm').addEventListener('click', (e) => {
    const goto = e.target.closest('[data-goto]');
    if (goto) goToStep(Number(goto.dataset.goto));
    if (e.target.closest('[data-wclearref]')) {
      wiz.ref = '';
      $('wRef').value = '';
      renderWizard();
      saveWizard();
    }
  });
  $('wCancel').addEventListener('click', () => {
    $('wizard').hidden = true;
    document.body.classList.remove('wizard-mode');
    if (state.current) $('workspace').hidden = false;
    else loadList().then((list) => list.length && openPlaylist(list[0].id));
  });
  $('wSrcAdd').addEventListener('click', addSource);
  $('wName').addEventListener('input', () => {
    wiz.name = $('wName').value;
    saveWizard();
  });
  $('wOwner').addEventListener('input', () => {
    wiz.owner = $('wOwner').value;
    saveWizard();
  });
  $('wStyle').addEventListener('input', () => {
    wiz.style = $('wStyle').value;
    saveWizard();
  });
  $('wSrcList').addEventListener('click', (e) => {
    const remove = e.target.closest('[data-wdel]');
    if (remove) {
      wiz.sources.splice(Number(remove.dataset.wdel), 1);
      renderWizard();
      saveWizard();
    }
  });
  $('wSpotify').addEventListener('click', (e) => {
    if (e.target.closest('#wConnect')) {
      wiz.step = 2;
      wiz.owner = $('wOwner').value.trim();
      wiz.name = $('wName').value.trim();
      saveWizard();
      connectSpotify();
    }
  });
  $('newBtn').addEventListener('click', () => openWizard());
}
