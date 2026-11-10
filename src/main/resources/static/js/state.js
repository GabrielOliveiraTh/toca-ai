export const GENRE_OPTIONS = [
  ['sertanejo', 'Sertanejo'],
  ['funk', 'Funk'],
  ['pagode', 'Pagode and samba'],
  ['forro', 'Forró'],
  ['mpb', 'MPB'],
  ['pop', 'Pop'],
  ['rock', 'Rock and metal'],
  ['rap', 'Rap and hip hop'],
  ['electronic', 'Electronic'],
  ['reggae', 'Reggae'],
  ['gospel', 'Gospel'],
  ['rnb', 'R&B and soul'],
  ['jazz', 'Jazz and blues'],
  ['classical', 'Classical'],
  ['latin', 'Latin'],
];

export const state = {
  current: null,
  playingId: null,
  lastResults: [],
  lastSuggestions: [],
  commonList: [],
  diffData: null,
  activeTab: 'suggestions',
  spotify: { configured: false, connected: false },
  playlistCount: 0,
  styleSources: [],
  styleDirty: false,
  undoStack: [],
  bpmById: {},
  dislikedList: [],
  byFilterName: null,
  lastRev: null,
  shareInfo: { token: null, addresses: [], port: 8080, configured: null },
  filters: { avoidLive: false, avoidRemix: false, popular: false, excludeGenres: [] },
};

try {
  const saved = JSON.parse(localStorage.getItem('sugFilters') || '{}');
  const valid = new Set(GENRE_OPTIONS.map(([key]) => key));
  if (Array.isArray(saved.excludeGenres)) {
    saved.excludeGenres = saved.excludeGenres.filter((key) => valid.has(key));
  }
  state.filters = { ...state.filters, ...saved };
} catch {
  // keep defaults
}
