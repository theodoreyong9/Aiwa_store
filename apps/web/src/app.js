import { config } from './config.js';
import { $, switchView } from './ui.js';
import { initStore } from './store-ui.js';
import { initWallet } from './wallet-ui.js';
import { initPublish } from './publish-ui.js';

// An error nobody caught (a handler without its own try/catch, a timer tick) is shown, never silent.
window.addEventListener('unhandledrejection', (event) => {
  console.error('unhandled:', event.reason);
  $('connect-error').textContent = `Unexpected error: ${event.reason?.message ?? event.reason}`;
});

for (const btn of document.querySelectorAll('#app-nav button')) btn.addEventListener('click', () => switchView(btn.dataset.view));

$('link-source').href = `https://github.com/${config.repository}`;
$('link-yellowpaper').href = `https://github.com/${config.repository}/blob/main/docs/YELLOWPAPER.md`;

initWallet();
initPublish();
initStore();
