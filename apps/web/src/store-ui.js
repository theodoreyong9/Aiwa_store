import { loadIndex, loadApp, filterApps, shortAddress, figureOf } from './store.js';
import { openCache } from './kv.js';
import { config } from './config.js';
import { $, switchView } from './ui.js';
import { setCatalog, session, onSession, connected } from './session.js';
import { openViewer } from './viewer.js';
import { knownLogin, ensureToken, forgetToken } from './github.js';
import { buildSubmission, evidenceFor, sendSubmission, slug } from './publish.js';
import { hostAvailable } from './host.js';
import { parseAccount, accountKey, listAccount, loadLibrary, filterLibrary, loadLink, savedAccounts, saveAccounts, localStore, savedFavorites, saveFavorites, isFavorite, toggleFavorite } from './library.js';

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
    const by = document.createElement('a');
    by.href = '#';
    by.className = 'by';
    by.textContent = shortAddress(app.author);
    by.title = 'Show every app of this author';
    by.addEventListener('click', (e) => { e.preventDefault(); $('store-search').value = app.author; render(); });
    meta.append(by, `${app.kind === 'aiwa' ? ' · Aiwa' : ''} · score/laps ${figureOf(app)} (${Number(app.score).toPrecision(3)} / ${app.laps})`);
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

    row.append(rank, body, open, code, starButton(app), shareButton(app));
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

function fillCode(entries) {
  shownFiles = entries;
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
}

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
    fillCode(Object.entries(files));
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
  renderMine();
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
  window.addEventListener('hashchange', () => { handoffCode(); handoffApp(); handoffQuery(); handoffTab(); handoffComment(); });
  return refreshStore().then(handoffCode).then(handoffApp).then(handoffQuery).then(handoffTab).then(handoffComment);
}



// ---------- the Aiwa address of an app: opens it directly ----------
// #app=<id> is a registry app; #link=<login>/<name> or <login>/<repo>/<name> is a library app (its file is derived from the address, no list needed).
export const appAddress = (entry) => `${config.siteUrl ?? ''}#${entry.kind === 'link' ? 'link' : 'app'}=${entry.id}`;

function shareButton(entry) {
  const share = document.createElement('button');
  share.type = 'button';
  share.className = 'star';
  share.textContent = '🔗';
  share.title = 'Copy the Aiwa address of this app';
  share.setAttribute('aria-label', `Copy the address of ${entry.name}`);
  share.addEventListener('click', async () => {
    try { await navigator.clipboard.writeText(appAddress(entry)); share.textContent = '✓'; } catch { prompt('Address', appAddress(entry)); }
    setTimeout(() => { share.textContent = '🔗'; }, 1500);
  });
  return share;
}

/** The library entry an address names, or null. */
export function linkEntryOf(id) {
  const m = /^([A-Za-z0-9][A-Za-z0-9-]{0,38})(?:\/([A-Za-z0-9._-]{1,100}))?\/([a-z0-9][a-z0-9-]{0,38}[a-z0-9]?)$/.exec(id);
  if (!m) return null;
  const [, owner, repo, name] = m;
  return { id, name, kind: 'link', owner, repo: `${owner}/${repo ?? 'aiwa-apps'}`, file: `${repo ? 'aiwa-apps/' : ''}${name}.app.html`, ref: 'HEAD' };
}

const ADDRESS_HASH = /^#(app|link)=([A-Za-z0-9._/-]{1,200})$/;
async function handoffApp() {
  const match = ADDRESS_HASH.exec(location.hash);
  if (!match) return;
  try { history.replaceState(null, '', location.pathname + location.search); } catch { /* cosmetic */ }
  switchView('store');
  const button = document.createElement('button');
  if (match[1] === 'link') {
    const entry = linkEntryOf(match[2]);
    if (entry) await openLink(entry, button, false); else $('store-status').textContent = 'This address is not an app address.';
    return;
  }
  const entry = apps.find((app) => app.id === match[2]);
  if (entry) await openApp(entry, button);
  else if (apps.length > 0) $('store-status').textContent = `"${match[2]}" is not in the registry (yet).`;
}

// ---------- one search for everything: an address, an id, an author, a GitHub account ----------
/** What the widget's field sends (#q=…): opens an address, else filters the Aiwa apps, else follows the GitHub account named. */
export async function unifiedSearch(raw) {
  const text = String(raw ?? '').trim();
  if (!text) return;
  switchView('store');
  const address = /#(app|link)=([A-Za-z0-9._/-]{1,200})$/.exec(text);
  if (address) { location.hash = `#${address[1]}=${address[2]}`; return; }
  if (filterApps(apps, text).length > 0 || !parseAccount(text)) {
    showStoreTab('aiwa');
    $('store-search').value = text;
    render();
    return;
  }
  showStoreTab('library');
  followAccount(text);
}

// The widget's « own » and « faves » buttons: #tab=mine / #tab=favorites open that tab.
const TAB_HASH = /^#tab=(mine|favorites)$/;
function handoffComment() {
  if (location.hash !== '#comment') return;
  try { history.replaceState(null, '', location.pathname + location.search); } catch { /* cosmetic */ }
  openCommentSheet();
}

function handoffTab() {
  const match = TAB_HASH.exec(location.hash);
  if (!match) return;
  try { history.replaceState(null, '', location.pathname + location.search); } catch { /* cosmetic */ }
  switchView('store');
  showStoreTab(match[1]);
}

// The widget's comment button: #comment opens the sheet. The field only appears once the wallet is connected: a comment is signed with it.
function paintCommentSheet() {
  if ($('commentsheet').hidden) return;
  const gate = $('comment-gate');
  const ready = connected();
  const checking = !ready && session.starting;
  gate.hidden = ready;
  gate.textContent = checking ? 'Looking for your wallet…'
    : !hostAvailable() ? 'Sending a comment needs the Android app (it signs in to GitHub).'
    : 'No wallet is connected. A comment is signed with your wallet, and ranked by what you have mined: connect it first.';
  $('comment-text').hidden = !ready;
  $('comment-send').hidden = !ready;
  $('comment-wallet').hidden = ready || checking;
}

function openCommentSheet() {
  switchView('store');
  $('comment-status').textContent = '';
  $('commentsheet').hidden = false;
  paintCommentSheet();
  if (connected()) $('comment-text').focus();
}

export function initComments() {
  $('comment-close').addEventListener('click', () => { $('commentsheet').hidden = true; });
  $('comment-wallet').addEventListener('click', () => { $('commentsheet').hidden = true; switchView('wallet'); });
  onSession(paintCommentSheet);
  $('comment-send').addEventListener('click', async () => {
    const button = $('comment-send');
    const say = (text) => { $('comment-status').textContent = text; };
    const text = $('comment-text').value.trim();
    if (!text) return say('The comment is empty.');
    const aiwa = session.aiwa;
    if (!aiwa?.identity) return say('A comment is signed with your wallet: open the Wallet tab first.');
    if (!hostAvailable()) return say('Sending a comment needs the Android app (it signs in to GitHub).');
    button.disabled = true;
    try {
      say('Signing with your wallet…');
      const evidence = await evidenceFor(aiwa, { registryUrl: config.registryUrl });
      const id = slug(`c-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 6)}`);
      const submission = await buildSubmission(aiwa, { kind: 'comment', id, name: text.replace(/\s+/g, ' ').slice(0, 40), version: '1.0.0', text }, evidence);
      const token = await ensureToken({ clientId: config.github?.clientId, onCode: ({ userCode, verificationUri }) => say(`Type ${userCode} at ${verificationUri}`) });
      await sendSubmission({ token, repository: config.repository, submission, onStep: say });
      $('comment-text').value = '';
      say('Sent. The registry checks it within a minute or two: once accepted, it is ranked by what you have mined and the critic reads the best ranked at its next turn.');
    } catch (error) {
      if (error.status === 401) await forgetToken().catch(() => {});
      say(String(error.message ?? error));
    } finally {
      button.disabled = false;
    }
  });
}

const QUERY_HASH = /^#q=(.{1,300})$/;
function handoffQuery() {
  const match = QUERY_HASH.exec(location.hash);
  if (!match) return;
  try { history.replaceState(null, '', location.pathname + location.search); } catch { /* cosmetic */ }
  let text = match[1];
  try { text = decodeURIComponent(text); } catch { /* kept as it came */ }
  return unifiedSearch(text);
}

// ---------- my apps: those published with this wallet, each with its address ----------
let ownLinks = null;   // the library apps of the person's own GitHub account (when they are signed in), read once per page

async function loadOwnLinks() {
  if (ownLinks) return ownLinks;
  ownLinks = [];
  const login = await knownLogin();
  if (!login) return ownLinks;
  try { ownLinks = (await listAccount({ owner: login, repo: 'aiwa-apps', dir: '' }, { store: localStore })).apps; } catch { /* no aiwa-apps repository: none */ }
  return ownLinks;
}

function mineRow(entry) {
  const row = document.createElement('div');
  row.className = 'app';
  row.dataset.id = entry.id;
  const body = document.createElement('div');
  body.className = 'body';
  const title = document.createElement('div');
  title.className = 'title';
  title.textContent = entry.name;
  const meta = document.createElement('div');
  meta.className = 'meta';
  meta.textContent = `${entry.kind === 'link' ? 'Library' : `Aiwa v${entry.version}`} · ${appAddress(entry)}`;
  body.append(title, meta);
  const open = document.createElement('button');
  open.type = 'button';
  open.className = 'primary';
  open.textContent = 'Open';
  open.addEventListener('click', () => (entry.kind === 'link' ? openLink(entry, open, false) : openApp(entry, open)));
  row.append(body, open, shareButton(entry));
  return row;
}

async function renderMine() {
  const list = $('mine-list');
  const aiwa = session.aiwa;
  const mine = aiwa?.identity ? apps.filter((app) => app.author === aiwa.address) : [];
  const links = await loadOwnLinks();
  list.replaceChildren(...mine.map(mineRow), ...links.map(mineRow));
  $('mine-status').textContent = mine.length || links.length ? '' : 'Nothing of yours yet: the Aiwa apps published with your wallet, and the apps of your own GitHub account (its aiwa-apps repository, when you are signed in), appear here.';
}

// ---------- favourites: the apps starred, from either tab ----------
let favorites = savedFavorites();

function starButton(entry) {
  const star = document.createElement('button');
  star.type = 'button';
  star.className = 'star';
  const paint = () => {
    const on = isFavorite(favorites, entry);
    star.textContent = on ? '★' : '☆';
    star.title = on ? 'Remove from the favourites' : 'Add to the favourites';
    star.setAttribute('aria-label', `${on ? 'Remove' : 'Add'} ${entry.name} ${on ? 'from' : 'to'} the favourites`);
  };
  paint();
  star.addEventListener('click', () => { favorites = toggleFavorite(favorites, entry); saveFavorites(favorites); paint(); renderFavorites(); });
  return star;
}

function renderFavorites() {
  const list = $('favorites-list');
  list.replaceChildren();
  $('favorites-status').textContent = favorites.length ? '' : 'No favourite yet. Press ☆ on an app of the Aiwa or Library tab.';
  for (const { entry } of favorites) {
    if (!filterLibrary([{ ...entry, repo: entry.repo ?? '' }], $('store-search').value).length) continue;
    const link = entry.kind === 'link';
    const row = document.createElement('div');
    row.className = 'app';
    row.dataset.id = entry.id;
    const body = document.createElement('div');
    body.className = 'body';
    const title = document.createElement('div');
    title.className = 'title';
    title.textContent = entry.name;
    const meta = document.createElement('div');
    meta.className = 'meta';
    meta.textContent = link ? `Library · github.com/${entry.repo}` : `Aiwa · ${shortAddress(entry.author)} · v${entry.version}`;
    body.append(title, meta);
    const open = document.createElement('button');
    open.type = 'button';
    open.className = 'primary';
    open.textContent = 'Open';
    open.addEventListener('click', () => (link ? openLink(entry, open, false) : openApp(entry, open)));
    const code = document.createElement('button');
    code.type = 'button';
    code.className = 'icon';
    code.textContent = '</>';
    code.setAttribute('aria-label', `Read the code of ${entry.name}`);
    code.addEventListener('click', () => (link ? openLink(entry, code, true) : showCode(entry, code)));
    row.append(body, open, code, starButton(entry), shareButton(entry));
    list.append(row);
  }
}

// ---------- the library: the GitHub accounts the user follows ----------

let accounts = savedAccounts();
let libraryApps = [];

function renderAccounts() {
  const box = $('library-accounts');
  box.replaceChildren();
  for (const account of accounts) {
    const chip = document.createElement('span');
    chip.className = 'chip';
    chip.append(accountKey(account));
    const x = document.createElement('button');
    x.type = 'button';
    x.textContent = '✕';
    x.setAttribute('aria-label', `Stop following ${accountKey(account)}`);
    x.addEventListener('click', () => { accounts = accounts.filter((a) => accountKey(a) !== accountKey(account)); saveAccounts(accounts); refreshLibrary(); });
    chip.append(x);
    box.append(chip);
  }
}

function renderLibrary() {
  const list = $('library-list');
  list.replaceChildren();
  for (const entry of filterLibrary(libraryApps, $('store-search').value)) {
    const row = document.createElement('div');
    row.className = 'app';
    row.dataset.id = entry.id;
    const body = document.createElement('div');
    body.className = 'body';
    const title = document.createElement('div');
    title.className = 'title';
    title.textContent = entry.name;
    const meta = document.createElement('div');
    meta.className = 'meta';
    meta.textContent = `github.com/${entry.repo} · can change at any time`;
    body.append(title, meta);
    const open = document.createElement('button');
    open.type = 'button';
    open.className = 'primary';
    open.textContent = 'Open';
    open.addEventListener('click', () => openLink(entry, open, false));
    const code = document.createElement('button');
    code.type = 'button';
    code.className = 'icon';
    code.textContent = '</>';
    code.title = 'Read the code in its repository';
    code.setAttribute('aria-label', `Read the code of ${entry.name}`);
    code.addEventListener('click', () => openLink(entry, code, true));
    row.append(body, open, code, starButton(entry), shareButton(entry));
    list.append(row);
  }
}

async function openLink(entry, button, readOnly) {
  const was = button.textContent;
  button.disabled = true;
  button.textContent = '…';
  try {
    const { html, files } = await loadLink({ entry });
    if (readOnly) {
      $('code-title').textContent = entry.name;
      $('code-meta').textContent = `In github.com/${entry.repo} · its author can change this code at any time (the frozen mode is the Aiwa contract).`;
      $('code-proof').textContent = 'Read live from GitHub. Nothing is signed or pinned here: it is whatever the author has there now.';
      fillCode(Object.entries(files));
    } else {
      openViewer({ name: entry.name, meta: `github.com/${entry.repo}`, html });
    }
  } catch (err) {
    $('store-status').textContent = String(err.message ?? err);
  } finally {
    button.disabled = false;
    button.textContent = was;
  }
}

export async function refreshLibrary() {
  renderAccounts();
  $('library-status').textContent = accounts.length ? 'Loading…' : 'No account followed yet.';
  const { apps, problems } = await loadLibrary(accounts, { store: localStore });
  libraryApps = apps;
  $('library-status').textContent = problems.join(' · ') || (accounts.length && apps.length === 0 ? 'No app found in these accounts.' : '');
  renderLibrary();
}

/** Follows an account (`login` or `login/repo`); false when the text is not one. */
export function followAccount(text) {
  const account = parseAccount(text);
  if (!account) return false;
  if (!accounts.some((a) => accountKey(a) === accountKey(account))) { accounts = [...accounts, account]; saveAccounts(accounts); }
  refreshLibrary();
  return true;
}

/** The Store has two tabs: the Aiwa apps (ranked, from the registry) and the Library (the GitHub accounts followed). */
export function showStoreTab(name) {
  for (const tab of ['aiwa', 'library', 'favorites', 'mine']) {
    $(`pane-${tab}`).hidden = tab !== name;
    $(`tab-${tab}`).classList.toggle('active', tab === name);
    $(`tab-${tab}`).setAttribute('aria-selected', String(tab === name));
  }
  $('store-search').placeholder = name === 'library' ? 'Search the library or a GitHub account' : name === 'favorites' ? 'Search the favourites' : name === 'mine' ? 'Search my apps' : 'Name, id or author’s Aiwa address';
  if (name === 'favorites') renderFavorites();
  if (name === 'mine') renderMine();
}

export function initLibrary() {
  $('tab-aiwa').addEventListener('click', () => showStoreTab('aiwa'));
  $('tab-library').addEventListener('click', () => showStoreTab('library'));
  $('tab-favorites').addEventListener('click', () => showStoreTab('favorites'));
  $('tab-mine').addEventListener('click', () => showStoreTab('mine'));
  onSession(renderMine);
  const add = () => {
    if (followAccount($('library-add').value)) $('library-add').value = '';
    else $('library-status').textContent = 'A GitHub login, or login/repo.';
  };
  $('library-add-go').addEventListener('click', add);
  $('library-add').addEventListener('keydown', (e) => { if (e.key === 'Enter') add(); });
  $('store-search').addEventListener('input', () => { renderLibrary(); renderFavorites(); });
  return refreshLibrary();
}
