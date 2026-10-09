import { config } from './config.js';
import { $, switchView } from './ui.js';
import { initStore } from './store-ui.js';
import { initWallet } from './wallet-ui.js';
import { initPublish, sheetIsOpen, closeSheet } from './publish-ui.js';
import { closeViewer } from './viewer.js';
import { setBackHandler } from './host.js';

// An error nobody caught (a handler without its own try/catch, a timer tick) is shown, never silent.
window.addEventListener('unhandledrejection', (event) => {
  console.error('unhandled:', event.reason);
  $('store-status').textContent = `Unexpected error: ${event.reason?.message ?? event.reason}`;
});

for (const btn of document.querySelectorAll('#app-nav button[data-view]')) btn.addEventListener('click', () => switchView(btn.dataset.view));

$('link-source').href = `https://github.com/${config.repository}`;
$('link-yellowpaper').href = `https://github.com/${config.repository}/blob/main/docs/YELLOWPAPER.md`;
for (const link of document.querySelectorAll('#view-papers a[data-doc]')) link.href = `https://github.com/${config.repository}/${link.dataset.doc}`;
// the video is a file of the repository (docs/aiwa-promo.mp4); it is only fetched when played (preload="none")
$('papers-video').src = $('papers-video-link').href = `https://github.com/${config.repository}/raw/main/docs/aiwa-promo.mp4`;

initPublish();
initStore();
initWallet();

// Inside the Android app: a back button that closes what is open first, then leaves the tab.
setBackHandler(() => {
  if (!$('viewer').hidden) { closeViewer(); return true; }
  if (sheetIsOpen()) { closeSheet(); return true; }
  if (!$('codesheet').hidden) { $('codesheet').hidden = true; return true; }
  const view = document.querySelector('#app-nav button.active')?.dataset.view;
  if (view && view !== 'store') { switchView('store'); return true; }
  return false;
});
