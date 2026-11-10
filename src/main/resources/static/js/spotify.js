import { state } from './state.js';
import { $, guard } from './dom.js';
import { api } from './api.js';
import { toast } from './toast.js';

export function connectSpotify() {
  guard(async () => {
    location.href = (await api('/spotify/login')).url;
  });
}

export async function spotifyStatus() {
  state.spotify = await api('/spotify/status');
  try {
    state.styleSources = await api('/style/sources');
  } catch {
    state.styleSources = [];
  }
  const box = $('spotifyBox');
  if (state.spotify.connected) {
    box.innerHTML = '<div class="status" title="Spotify connected"><span class="dot on"></span><span class="txt">Spotify connected</span></div>'
      + '<button id="disconnect" class="ghost full">Disconnect</button>';
  } else if (!state.spotify.configured) {
    box.innerHTML = '<span class="txt">Spotify is not configured. Set <code>SPOTIFY_CLIENT_ID</code> (see the README).</span>';
  } else {
    box.innerHTML = '<div class="status" title="Spotify disconnected"><span class="dot"></span><span class="txt">Spotify disconnected</span></div>'
      + '<button id="connect" class="primary full">Connect Spotify</button>';
  }
  const connect = $('connect');
  if (connect) connect.onclick = connectSpotify;
  const disconnect = $('disconnect');
  if (disconnect) {
    disconnect.onclick = () => guard(async () => {
      if (!confirm('Disconnect Spotify? You will need to sign in again to import or export.')) return;
      await api('/spotify/logout', { method: 'POST' });
      await spotifyStatus();
      toast('Spotify disconnected.');
    });
  }
}
