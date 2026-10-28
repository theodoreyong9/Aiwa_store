import { startChambre } from './chambre.js';
import { config } from './config.js';
import { $, switchView } from './ui.js';
import { initStore, initLibrary, initComments, showStoreTab } from './store-ui.js';
import { initWallet, initWalletPage, initCoffee } from './wallet-ui.js';
import { initVoirPage } from './voir.js';
import { initCommentsTab } from './comments-ui.js';
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
// The current film is named by docs/videos/latest.json (its file name carries its version: a new film is a new name, so a phone never shows an old copy from its cache).
// Reading the name there rather than writing it here means a new film changes no file of the web app, hence no « update » on the phone. Read straight from raw.githubusercontent.com (no redirect).
{
  const raw = `https://raw.githubusercontent.com/${config.repository}/main/docs/videos/`;
  $('papers-video-vision-link').href = `https://github.com/${config.repository}/tree/main/docs/videos`;
  fetch(`${raw}latest.json?t=${Math.floor(Date.now() / 60000)}`, { cache: 'no-store' }).then((r) => r.ok ? r.json() : null).then((film) => {
    if (!film || !film.file) return;
    $('papers-video-vision').src = $('papers-video-vision-link').href = raw + film.file;
    if (film.label) $('papers-video-title').textContent = `Aiwa · ${film.label}`;
  }).catch(() => {});
}

initPublish();
initStore();
initLibrary();
initComments();
initCommentsTab();
initWallet();

// A page alone, opened by the widget's Wallet or Store button (?page=wallet | ?page=store&repo=owner/name): no bar, no tabs inside, one thing on screen.
const pageParams = new URLSearchParams(location.search);
const pageAlone = ['wallet', 'store', 'voir'].includes(pageParams.get('page')) ? pageParams.get('page') : null;
if (pageParams.get('page') === 'chambre') startChambre(pageParams);       // the chamber has no screen: it lives under the home of the application (chambre.js)
if (pageAlone) {
  document.body.dataset.page = pageAlone;
  switchView(pageAlone);
  if (pageAlone === 'store') showStoreTab('aiwa');
  else if (pageAlone === 'voir') initVoirPage(pageParams);
  else if (pageAlone === 'wallet' && pageParams.get('coffee')) initCoffee(pageParams);
  else initWalletPage(pageParams.get('repo'));
}

// Inside the Android app: a back button that closes what is open first, then leaves the tab.
setBackHandler(() => {
  if (!$('viewer').hidden) { closeViewer(); return true; }
  if (sheetIsOpen()) { closeSheet(); return true; }
  if (!$('codesheet').hidden) { $('codesheet').hidden = true; return true; }
  if (pageAlone) return false;       // a page alone: back leaves it
  const view = document.querySelector('#app-nav button.active')?.dataset.view;
  if (view && view !== 'store') { switchView('store'); return true; }
  return false;
});
