import { state } from './state.js';
import { $, busy } from './dom.js';
import { api } from './api.js';
import { toast } from './toast.js';
import { mutateTracks, undo } from './undo.js';

const closeSortMenu = () => {
  $('sortMenu').hidden = true;
  $('sortBtn').setAttribute('aria-expanded', 'false');
};

export function init() {
  $('sortBtn').addEventListener('click', (e) => {
    e.stopPropagation();
    const opening = $('sortMenu').hidden;
    $('sortMenu').hidden = !opening;
    $('sortBtn').setAttribute('aria-expanded', String(opening));
  });
  document.addEventListener('click', (e) => {
    if (!e.target.closest('.menu-wrap')) closeSortMenu();
  });
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') closeSortMenu();
  });

  $('sortMenu').addEventListener('click', (e) => {
    const option = e.target.closest('[data-sort]');
    if (!option) return;
    closeSortMenu();
    busy($('sortBtn'), 'Sorting…', async () => {
      let result;
      await mutateTracks(async () => {
        result = await api(`/playlists/${state.current.id}/sort`, { method: 'POST', body: { mode: option.dataset.sort } });
        Object.assign(state.bpmById, result.bpm);
        return result.playlist;
      });
      const estimated = result.estimated ? ` ${result.estimated} had their tempo estimated from the preview (±3).` : '';
      const message = result.withoutBpm
        ? `Sorted by tempo.${estimated} ${result.withoutBpm} without a clear beat went to the end.`
        : `Playlist sorted by tempo.${estimated}`;
      toast(message, false, { label: 'Undo', fn: undo });
    });
  });
}
