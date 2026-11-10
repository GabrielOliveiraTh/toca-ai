import { state } from './state.js';
import { $, esc, guard, busy } from './dom.js';
import { api } from './api.js';
import { toast } from './toast.js';
import { contributors, renderAll } from './workspace.js';

const LAN_HINT = 'Anyone on the same Wi-Fi network as this computer can open the link. '
  + 'For people on other networks, use a tunnel (for example ngrok) and choose "Other address".';

async function loadNetwork() {
  try {
    state.shareInfo = { ...state.shareInfo, ...(await api('/network')) };
  } catch {
    // no network detected
  }
}

function shareBase() {
  const selected = $('shareAddr').value;
  if (selected === '__custom') return $('shareCustom').value.trim().replace(/\/+$/, '');
  return selected ? `http://${selected}:${state.shareInfo.port}` : '';
}

function shareLink() {
  const base = shareBase();
  return base && state.shareInfo.token ? `${base}/join/${state.shareInfo.token}` : '';
}

function fillShareAddresses() {
  const select = $('shareAddr');
  const { addresses = [], configured } = state.shareInfo;
  let saved = '';
  try {
    saved = localStorage.getItem('shareAddr') || '';
  } catch {
    // no saved choice
  }
  const ips = addresses.map((a) => a.ip);
  select.innerHTML = addresses.map((a) => `<option value="${esc(a.ip)}">${esc(a.ip)} · ${esc(a.name)}</option>`).join('')
    + '<option value="__custom">Other address…</option>';
  const savedIsValid = saved && (ips.includes(saved) || saved === '__custom');
  const useConfigured = configured && saved !== '__custom' && ips.includes(configured);
  select.value = useConfigured ? configured : savedIsValid ? saved : (ips[0] || '__custom');
  $('shareCustom').hidden = select.value !== '__custom';
  try {
    $('shareCustom').value = localStorage.getItem('shareCustom') || '';
  } catch {
    // no saved address
  }
}

export function renderShare() {
  const on = !!state.shareInfo.token;
  $('shareIntro').hidden = on;
  $('shareActive').hidden = !on;
  if (!on) return;
  const link = shareLink();
  $('shareUrl').value = link;
  $('shareQr').src = link ? `/api/qr?text=${encodeURIComponent(link)}` : '';
  $('shareQr').style.visibility = link ? 'visible' : 'hidden';
  $('shareHint').textContent = shareBase().startsWith('https://')
    ? 'Secure address: on a phone you can install it as an app.'
    : LAN_HINT;
  if (document.activeElement !== $('ownerInput')) $('ownerInput').value = state.current.ownerName || '';
  const counts = contributors();
  $('shareContrib').innerHTML = counts.size
    ? `<span class="caption">Who has added tracks</span><div class="chips-inline">${
      [...counts].map(([who, count]) => `<span class="badge">${esc(who)} · ${count}</span>`).join('')}</div>`
    : '<span class="muted small">Nobody has added tracks yet.</span>';
}

async function openShare() {
  await loadNetwork();
  const share = await api(`/playlists/${state.current.id}/share`);
  state.shareInfo.token = share.token;
  fillShareAddresses();
  renderShare();
  if (!$('shareDialog').open) $('shareDialog').showModal();
}

export function init() {
  $('shareBtn').addEventListener('click', () => guard(openShare));
  $('shareClose').addEventListener('click', () => $('shareDialog').close());
  $('shareDialog').addEventListener('click', (e) => {
    if (e.target === $('shareDialog')) $('shareDialog').close();
  });
  $('shareEnable').addEventListener('click', (e) => busy(e.currentTarget, 'Generating…', async () => {
    const share = await api(`/playlists/${state.current.id}/share`, { method: 'POST' });
    state.shareInfo.token = share.token;
    state.current = { ...state.current, shared: true };
    renderShare();
  }));
  $('shareDisable').addEventListener('click', (e) => busy(e.currentTarget, 'Turning off…', async () => {
    if (!confirm('Stop sharing? The current link and QR code will stop working for everyone.')) return;
    await api(`/playlists/${state.current.id}/share`, { method: 'DELETE' });
    state.shareInfo.token = null;
    state.current = { ...state.current, shared: false };
    renderShare();
    toast('Sharing turned off.');
  }));
  $('shareAddr').addEventListener('change', () => {
    try {
      localStorage.setItem('shareAddr', $('shareAddr').value);
    } catch {
      // choice is not persisted
    }
    $('shareCustom').hidden = $('shareAddr').value !== '__custom';
    renderShare();
  });
  $('shareCustom').addEventListener('input', () => {
    try {
      localStorage.setItem('shareCustom', $('shareCustom').value);
    } catch {
      // value is not persisted
    }
    renderShare();
  });
  $('shareCopy').addEventListener('click', async () => {
    const link = shareLink();
    if (!link) return toast('Enter an address to generate the link.', true);
    try {
      await navigator.clipboard.writeText(link);
    } catch {
      $('shareUrl').select();
      document.execCommand('copy');
    }
    toast('Link copied.');
  });
  $('ownerSave').addEventListener('click', (e) => busy(e.currentTarget, 'Saving…', async () => {
    const name = $('ownerInput').value.trim();
    if (!name) return toast('Enter your name.', true);
    state.current = await api('/playlists/' + state.current.id, { method: 'PATCH', body: { ownerName: name } });
    try {
      localStorage.setItem('ownerName', name);
    } catch {
      // name is not persisted
    }
    renderAll();
    renderShare();
    toast('Name updated.');
  }));
}
