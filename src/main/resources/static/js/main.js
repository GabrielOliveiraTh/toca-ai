import { applyIcons, guard } from './dom.js';
import { toast, init as initToast } from './toast.js';
import { init as initAvatar } from './avatar.js';
import { init as initPlayer } from './player.js';
import { init as initSidebar, setCollapsed, loadList } from './sidebar.js';
import { init as initWorkspace, openPlaylist } from './workspace.js';
import { init as initSuggestions } from './suggestions.js';
import { init as initSources } from './sources.js';
import { init as initExport } from './export.js';
import { init as initShare } from './share.js';
import { init as initLive } from './live.js';
import { init as initPwa } from './pwa.js';
import { init as initWizard, openWizard, restoreWizard } from './wizard.js';
import { init as initUndo } from './undo.js';
import { init as initSort } from './sort.js';
import { spotifyStatus } from './spotify.js';

async function start() {
  applyIcons();
  [initToast, initAvatar, initPlayer, initSidebar, initWorkspace, initSuggestions, initSources, initExport,
    initShare, initLive, initPwa, initWizard, initUndo, initSort].forEach((init) => init());

  try {
    setCollapsed(localStorage.getItem('sidebarCollapsed') === '1');
  } catch {
    setCollapsed(false);
  }
  const returned = new URLSearchParams(location.search).get('spotify');
  if (returned) {
    history.replaceState(null, '', '/');
    toast(returned === 'ok' ? 'Spotify connected.' : 'Could not connect to Spotify.', returned !== 'ok');
  }
  await guard(spotifyStatus);
  const list = (await guard(loadList)) || [];

  let saved = null;
  try {
    saved = JSON.parse(sessionStorage.getItem('wiz') || 'null');
  } catch {
    // no saved progress
  }
  if (returned && saved) {
    restoreWizard(saved);
    return openWizard(true);
  }

  if (list.length === 0) return openWizard();
  let last = null;
  try {
    last = localStorage.getItem('lastPlaylist');
  } catch {
    // no saved playlist
  }
  const target = list.find((p) => String(p.id) === last) || list[0];
  await guard(() => openPlaylist(target.id));
}

start();
