import { state } from './state.js';

export const mainSource = () => state.current.sources.find((s) => s.main);

export const otherSources = () => state.current.sources.filter((s) => !s.main);

const looksLikePlaylist = (value) => /playlist[/:]|[?&]list=|^[A-Za-z0-9]{22}$/.test(value.trim());

const isSpotifyLink = (value) => !/deezer\.|youtube\.com|youtu\.be/i.test(value);

export function linkProblem(value) {
  if (!looksLikePlaylist(value)) return 'Paste the link to a Spotify, Deezer or YouTube playlist.';
  if (isSpotifyLink(value) && !state.spotify.connected) {
    return 'To use a Spotify link, connect your account (step 1). Deezer and YouTube links work without connecting.';
  }
  return null;
}

export const sourceLabel = (value) =>
  (/deezer\./i.test(value) ? 'Deezer' : /youtube\.com|youtu\.be/i.test(value) ? 'YouTube' : 'Spotify');
