import { loadIndex, loadApp, filterApps, shortAddress, figureOf } from './store.js';
import { openCache } from './kv.js';
import { config } from './config.js';
import { $, switchView } from './ui.js';
import { setCatalog } from './session.js';
import { openViewer } from './viewer.js';

const cache = openCache('aiwa-store-cache');
let apps = [];

function render() {
  const shown = filterApps(apps, $('store-search').value);
  const list = $('store-list');
  list.replaceChildren();
  $('store-empty').hidden = apps.length > 0;
  for (const app of shown.slice(0, 200)) {
    const row = document.createElement('div');
    row.className = 'app';
    row.dataset.id = app.id;

    const rank = document.createElement('div');
    rank.className = 'rank';
    rank.textContent = String(apps.indexOf(app) + 1);

    const body = document.createElement('div');
    body.className = 'body';
    const title = document.createElement('div');
    title.className = 'title';
    title.textContent = app.name;
    const ver = document.createElement('span');
    ver.className = 'ver';
    ver.textContent = `v${app.version}`;
    title.append(ver);
    const desc = document.createElement('div');
    desc.className = 'desc';
    desc.textContent = app.description;
    const meta = document.createElement('div');
    meta.className = 'meta';
    meta.textContent = `${shortAddress(app.author)}${app.kind === 'aiwa' ? ' · Aiwa' : ''} · score/laps ${figureOf(app)} (${Number(app.score).toPrecision(3)} / ${app.laps})`;
    body.append(title, desc, meta);

    const open = document.createElement('button');
    open.type = 'button';
    open.className = 'primary';
    open.textContent = 'Open';
    open.addEventListener('click', () => openApp(app, open));

    // The code as it was published, read after the fact: what the author signed, checked again here.
    const code = document.createElement('button');
    code.type = 'button';
    code.className = 'icon';
    code.textContent = '</>';
    code.title = 'Read the published code';
    code.setAttribute('aria-label', `Read the published code of ${app.name}`);
    code.addEventListener('click', () => showCode(app, code));

    row.append(rank, body, open, code);
    list.append(row);
  }
}

async function openApp(entry, button) {
  const was = button.textContent;
  button.disabled = true;
  button.textContent = '…';
  try {
    const { html } = await loadApp({ entry, baseUrl: config.registryUrl, cache });
    openViewer({ name: entry.name, meta: `v${entry.version} · ${shortAddress(entry.author)}`, html });
  } catch (err) {
    $('store-status').textContent = String(err.message ?? err);
  } finally {
    button.disabled = false;
    button.textContent = was;
  }
}

// The published code of an app, read-only: the files of the version the registry lists, checked again before they are shown (the author's
// signature, and for an Aiwa contract every file against the signed manifest). The code of a version never changes: a new code is a new version.
let shownFiles = [];

async function showCode(entry, button) {
  const was = button?.textContent;
  if (button) { button.disabled = true; button.textContent = '…'; }
  try {
    const { pkg, files } = await loadApp({ entry, baseUrl: config.registryUrl, cache });
    $('code-title').textContent = `${entry.name} v${entry.version}`;
    $('code-meta').textContent = `Published by ${shortAddress(entry.author)} · ${entry.kind === 'aiwa' ? 'an Aiwa contract' : 'a plain app'} · this code cannot change: a new code is a new version.`;
    $('code-proof').textContent = entry.kind === 'aiwa'
      ? `Checked here: signed by its author, and every file matches the signed manifest ${String(pkg.manifestId).slice(0, 12)}…`
      : `Checked here: signed by its author, fingerprint ${String(entry.bundleHash).slice(0, 12)}…`;
    shownFiles = Object.entries(files);
    const list = $('code-files');
    list.replaceChildren();
    for (const [path, content] of shownFiles) {
      const block = document.createElement('div');
      block.className = 'codefile';
      const name = document.createElement('b');
      name.textContent = path;
      const text = document.createElement('pre');
      text.textContent = content;
      block.append(name, text);
      list.append(block);
    }
    $('codesheet').hidden = false;
  } catch (err) {
    $('store-status').textContent = String(err.message ?? err);
  } finally {
    if (button) { button.disabled = false; button.textContent = was; }
  }
}

// The Android app's </> button: #code=<id> opens the published code of that app, once the registry lists it.
const CODE_HASH = /^#code=([a-z0-9-]{1,40})$/;
async function handoffCode() {
  const match = CODE_HASH.exec(location.hash);
  if (!match) return;
  try { history.replaceState(null, '', location.pathname + location.search); } catch { /* cosmetic */ }
  switchView('store');
  const entry = apps.find((app) => app.id === match[1]);
  if (entry) { await showCode(entry, null); return; }
  if (apps.length > 0) $('store-status').textContent = `"${match[1]}" is not in the registry yet: an app is listed once its pull request was accepted. Its draft is in the publish sheet (the ▦ button of the widget).`;
}

export async function refreshStore() {
  $('store-status').textContent = 'Loading…';
  try {
    const result = await loadIndex({ baseUrl: config.registryUrl, cache });
    apps = result.apps;
    setCatalog(apps);
    $('store-status').textContent = result.source === 'cache' ? `Offline: the last list seen (${result.error}).` : '';
  } catch (err) {
    apps = [];
    setCatalog(apps);
    $('store-status').textContent = `The registry could not be read: ${err.message}`;
  }
  render();
}

export function initStore() {
  $('store-search').addEventListener('input', render);
  $('store-refresh').addEventListener('click', refreshStore);
  $('code-close').addEventListener('click', () => { $('codesheet').hidden = true; });
  $('code-copy').addEventListener('click', async () => {
    try {
      await navigator.clipboard.writeText(shownFiles.map(([path, content]) => (shownFiles.length > 1 ? `// ---- ${path} ----\n${content}` : content)).join('\n\n'));
      $('code-copy').textContent = 'Copied';
      setTimeout(() => { $('code-copy').textContent = 'Copy all'; }, 1500);
    } catch { /* the browser refused the clipboard: the text is on screen to select */ }
  });
  // Already open when the Android app asks for a code: only the fragment changes.
  window.addEventListener('hashchange', () => handoffCode());
  return refreshStore().then(handoffCode);
}
