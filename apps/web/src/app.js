import { config } from './config.js';
import { $, switchView } from './ui.js';
import { initStore, initLibrary, initComments } from './store-ui.js';
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
for (const link of document.querySelectorAll('#view-papers a[data-doc]')) link.href = `https://github.com/${config.repository}/${link.dataset.doc}`;
// The film's file name carries its version (docs/aiwa-global-v11-16x9.mp4): a new film is a new name, so a phone never shows an old copy from its cache. Read straight from raw.githubusercontent.com (no redirect).
$('papers-video-vision').src = $('papers-video-vision-link').href = `https://raw.githubusercontent.com/${config.repository}/main/docs/aiwa-global-v11-16x9.mp4`;

initPublish();
initStore();
initLibrary();
initComments();
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
