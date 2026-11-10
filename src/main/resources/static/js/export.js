import { state } from './state.js';
import { $, esc, busy } from './dom.js';
import { api } from './api.js';

export function renderExportBtn() {
  const done = state.current && state.current.spotifyUrl;
  $('exportBtn').textContent = done ? 'Update on Spotify' : 'Export to Spotify';
  $('exportBtn').title = done
    ? 'Updates the same playlist that is already on your Spotify'
    : 'Creates the playlist in your Spotify account';
}

export function showExportInfo() {
  const box = $('exportMsg');
  if (!state.current || !state.current.spotifyUrl) {
    box.hidden = true;
    return;
  }
  box.hidden = false;
  box.innerHTML = `This playlist is already on your Spotify. <a href="${esc(state.current.spotifyUrl)}" target="_blank" rel="noopener">Open</a>
    <span class="muted"> · Exporting again updates the same one without creating another.</span>`;
}

export function init() {
  $('exportBtn').addEventListener('click', async (e) => {
    const again = !!state.current.spotifyUrl;
    await busy(e.currentTarget, again ? 'Updating…' : 'Exporting…', async () => {
      const result = await api(`/playlists/${state.current.id}/export`, { method: 'POST' });
      state.current = await api('/playlists/' + state.current.id);
      const box = $('exportMsg');
      box.hidden = false;
      const notFound = result.notFound.length
        ? `<br><span class="muted">Not found on Spotify: ${result.notFound.map(esc).join('; ')}</span>`
        : '';
      box.innerHTML = `${result.updated ? 'Playlist updated on Spotify' : 'Playlist created on Spotify'}: ${result.added} tracks.
        <a href="${esc(result.url)}" target="_blank" rel="noopener">Open on Spotify</a>${notFound}`;
    });
    renderExportBtn();
  });
}
