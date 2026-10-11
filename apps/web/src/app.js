import { config } from './config.js';
import { $, switchView } from './ui.js';
import { initStore } from './store-ui.js';
import { initWallet } from './wallet-ui.js';
import { initPublish, sheetIsOpen, closeSheet } from './publish-ui.js';
import { closeViewer } from './viewer.js';
import { setBackHandler, hostAvailable, hostPost, hostCall } from './host.js';

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
$('papers-video-short').src = $('papers-video-short-link').href = `https://github.com/${config.repository}/raw/main/docs/aiwa-promo-cine-16x9.mp4`;
// The film's file name carries its version (docs/aiwa-global-v5-16x9.mp4): a new film is a new name, so a phone never shows an old copy from its cache. Read straight from raw.githubusercontent.com (no redirect).
$('papers-video-vision').src = $('papers-video-vision-link').href = `https://raw.githubusercontent.com/${config.repository}/main/docs/aiwa-global-v5-16x9.mp4`;

// The « Aiwa » tab: the screens of the Android app (wake word, dictation, videos, proposals), one tap from the Store.
$('aiwa-host-hint').hidden = hostAvailable();
for (const btn of document.querySelectorAll('#aiwa-actions button[data-screen]')) {
  btn.disabled = !hostAvailable();
  btn.addEventListener('click', () => hostPost({ cmd: 'open', screen: btn.dataset.screen }));
}

// Which build of the app is installed: an app older than this page does not know the screens above, and says so instead of doing nothing.
$('aiwa-apk-link').href = `https://github.com/${config.repository}/releases/download/android-latest/Aiwa_store.apk`;
if (hostAvailable()) {
  hostCall('app-info', {}, { timeoutMs: 4000 })
    .then((info) => {
      $('aiwa-app-info').textContent = `Application installée : version ${info.version} (build n° ${info.code}).`;
      $('aiwa-backend-info').textContent = info.backend > 0
        ? `Cerveau (Termux) : version ${info.backend}${info.backend < info.expected ? `, la ${info.expected} est attendue : touche « Mettre à jour le cerveau ».` : ' : à jour.'}`
        : "Cerveau (Termux) : ne répond pas. Touche « Mettre à jour le cerveau » ; s'il ne démarre pas, la commande à coller une fois dans Termux est dans le guide d'installation.";
    })
    .catch(() => { $('aiwa-app-info').textContent = "L'application installée est plus ancienne que cette page : elle ne connaît pas ces écrans. Installe le dernier APK (lien ci-dessous), puis rouvre le Store."; $('aiwa-apk-link').hidden = false; });
}

$('aiwa-update-backend').disabled = !hostAvailable();
$('aiwa-update-backend').addEventListener('click', async () => {
  const note = $('aiwa-backend-info');
  note.textContent = 'Mise à jour demandée à Termux… (environ 30 secondes)';
  try {
    await hostCall('update-backend', {}, { timeoutMs: 10_000 });
    setTimeout(() => hostCall('app-info', {}, { timeoutMs: 4000 }).then((info) => {
      note.textContent = info.backend >= info.expected ? `Cerveau à jour (version ${info.backend}).` : `Cerveau en version ${info.backend || '?'} (attendue : ${info.expected}). Réessaie dans un moment.`;
    }).catch(() => { note.textContent = 'Demande envoyée. Rouvre cet onglet dans une minute pour voir la version.'; }), 30_000);
  } catch (err) {
    note.textContent = `Termux n'a pas pu être lancé : ${err.message}. Il faut une fois : l'autorisation « exécuter des commandes » pour Aiwa, et allow-external-apps=true dans Termux (guide d'installation).`;
  }
});

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
