import { $ } from './dom.js';

let installPrompt = null;

export function init() {
  window.addEventListener('beforeinstallprompt', (e) => {
    e.preventDefault();
    installPrompt = async () => {
      e.prompt();
      const choice = await e.userChoice;
      installPrompt = null;
      $('installBtn').hidden = true;
      return choice.outcome;
    };
    $('installBtn').hidden = false;
  });
  window.addEventListener('appinstalled', () => {
    installPrompt = null;
    $('installBtn').hidden = true;
  });
  $('installBtn').addEventListener('click', () => installPrompt && installPrompt());

  if ('serviceWorker' in navigator && window.isSecureContext) {
    window.addEventListener('load', () => {
      navigator.serviceWorker.register('/sw.js').catch(() => {});
    });
  }
}
