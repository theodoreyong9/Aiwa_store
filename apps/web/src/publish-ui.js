// The publish sheet: ONE screen that does one thing, sign what the author is looking at and send it to the registry as a
// pull request on GitHub, after saying what is about to be sent. It opens when the Android app's dictation module hands
// over an app (the ▦ button), or from the author's own list of apps to refresh a ranking. There is no publish form.
//
// The hand-off is the address #publish=<kind>;<name>;<code> where <kind> is `code` (one HTML file) or `aiwa` (several files,
// as {"files":[{"path","content"}]}) and <code> is that text, raw-deflated then base64url-encoded. A URL fragment never leaves
// the browser. Nothing is signed or sent until the author presses Publish.

import { filesProblem, BUNDLE_LIMITS } from 'aiwa-registry/bundle';
import { config } from './config.js';
import { session, onSession, connected, catalog } from './session.js';
import { $, flash, switchView } from './ui.js';
import { openViewer } from './viewer.js';
import { assembleHtml } from './assemble.js';
import { hostAvailable, hostPost } from './host.js';
import { ensureToken, forgetToken } from './github.js';
import { slug, nextVersion, evidenceFor, buildSubmission, buildRefresh, sendSubmission, submissionFileName } from './publish.js';

const MAX_CHARS = 1_000_000;

let job = null;       // what the sheet is for: { mode: 'publish', app } or { mode: 'refresh', entry }
let manual = null;    // the submission, when this page cannot send it itself
let busy = false;     // signing or sending
let recheck = null;   // while the sheet is open and nothing can be published yet, look again now and then

async function unpack(b64url) {
  if (typeof DecompressionStream !== 'function') throw new Error('this browser cannot unpack it (DecompressionStream)');
  const bytes = Uint8Array.from(atob(b64url.replace(/-/g, '+').replace(/_/g, '/')), (c) => c.charCodeAt(0));
  let text;
  try { text = await new Response(new Blob([bytes]).stream().pipeThrough(new DecompressionStream('deflate-raw'))).text(); }
  catch { throw new Error('the data is damaged'); }
  if (text.length > MAX_CHARS) throw new Error('it is too big');
  return text;
}

const describe = (html) => /<meta\s+name=["']description["']\s+content=["']([^"']{0,280})["']/i.exec(html)?.[1] ?? '';

/** An app from what the hand-off carries, or throws why it cannot be one. */
function appFrom(kind, name, text) {
  const id = slug(name);
  if (!id) throw new Error('the name has no letter or digit');
  if (kind === 'aiwa') {
    let files;
    try { files = JSON.parse(text).files; } catch { throw new Error('the files are not readable'); }
    const problem = filesProblem(files);
    if (problem) throw new Error(problem);
    return { kind, id, name, files, description: describe(files.find((f) => f.path === BUNDLE_LIMITS.entry).content) };
  }
  return { kind: 'code', id, name, html: text, description: describe(text) };
}

const htmlOf = (app) => (app.kind === 'aiwa' ? assembleHtml(Object.fromEntries(app.files.map((f) => [f.path, f.content]))) : app.html);
const sizeOf = (app) => (app.kind === 'aiwa' ? app.files.reduce((n, f) => n + f.content.length, 0) : app.html.length);

// ---------- the sheet ----------

const STARTING = 'Starting your wallet…';
const say = (text) => { $('sheet-status').textContent = text; };
const warn = (text) => { $('sheet-warning').hidden = !text; $('sheet-warning').textContent = text ?? ''; };

function stopRechecking() {
  clearInterval(recheck);
  recheck = null;
}

function close() {
  $('sheet').hidden = true;
  stopRechecking();
  job = null;
  manual = null;
}

// A new app is only listed for an author who has something claimable: said here, before anything is signed. The wallet keeps
// mining while the sheet is open, so it is looked at again.
async function ready() {
  if (busy) return;
  if (!connected()) { warn(''); $('sheet-go').disabled = true; say(STARTING); return; }
  const mining = await session.aiwa.mining();
  if (busy) return;
  const publishable = job?.mode === 'refresh' || (mining && Number(mining.claimable) > 0);
  warn(publishable ? '' : 'Nothing to publish with yet. The Store lists the apps of authors who have mined: burn some SOL in the Wallet tab and leave the app open for a while.');
  if ($('sheet-status').textContent === STARTING) say('');
  $('sheet-go').disabled = !publishable;
}

function openSheet({ title, summary, fields, go }) {
  $('sheet-title').textContent = title;
  $('sheet-summary').textContent = summary;
  $('sheet-fields').hidden = !fields;
  $('sheet-try').hidden = job.mode !== 'publish';
  $('sheet-go').textContent = go;
  $('sheet-go').hidden = false;
  $('sheet-code').hidden = true;
  $('sheet-manual').hidden = true;
  $('sheet-result').hidden = true;
  say('');
  $('sheet').hidden = false;
  stopRechecking();
  recheck = setInterval(ready, 3000);
  return ready();
}

function openPublish(app) {
  const mine = session.aiwa?.address;
  job = { mode: 'publish', app };
  $('app-name').value = app.name;
  $('app-id').value = app.id;
  $('app-version').value = mine ? nextVersion(catalog.apps, { id: app.id, author: mine }) : '1.0.0';
  $('app-description').value = app.description;
  const kb = Math.max(1, Math.round(sizeOf(app) / 1024));
  openSheet({
    title: 'Publish this app',
    summary: `${app.kind === 'aiwa' ? `${app.files.length} files, published through Aiwa` : 'One HTML file'} (${kb} KB). It is signed with your wallet and sent to the Store's registry as a pull request on your GitHub account. `
      + 'Anyone can open it from the Store, in a sandbox; it is ranked by what you have mined.',
    fields: true, go: 'Publish',
  });
}

/** From the author's own list: a signed request to re-read the ranking figure of one app. */
export function openRefresh(entry) {
  job = { mode: 'refresh', entry };
  openSheet({ title: `Refresh ${entry.name}`, summary: 'Re-reads what you can claim and the epochs since your last action, for this app. Same pull request, no new content.', fields: false, go: 'Refresh' });
}

function showCode({ userCode, verificationUri }) {
  $('sheet-code').hidden = false;
  $('sheet-code-text').textContent = userCode;
  $('sheet-code-uri').textContent = verificationUri;
  say('Waiting for you on GitHub…');
}

async function go() {
  $('sheet-go').disabled = true;
  warn('');
  busy = true;
  try {
    const aiwa = session.aiwa;
    say('Signing with your wallet…');
    const evidence = await evidenceFor(aiwa, { registryUrl: config.registryUrl });
    let submission;
    if (job.mode === 'refresh') {
      submission = await buildRefresh(aiwa, job.entry.id, evidence);
    } else {
      const app = { ...job.app, id: $('app-id').value.trim(), name: $('app-name').value.trim(), version: $('app-version').value.trim() || '1.0.0', description: $('app-description').value.trim() };
      submission = await buildSubmission(aiwa, app, evidence);
    }
    if (!hostAvailable()) { showManual(submission); stopRechecking(); return; }
    const token = await ensureToken({ clientId: config.github?.clientId, onCode: showCode });
    $('sheet-code').hidden = true;
    const pull = await sendSubmission({ token, repository: config.repository, submission, onStep: say });
    say('Sent. The registry checks it within a minute or two and answers on the pull request; once it accepts, the app is listed.');
    const link = $('sheet-result');
    link.hidden = false;
    link.href = pull.url;
    $('sheet-go').hidden = true;
    stopRechecking();
  } catch (err) {
    if (err.status === 401) await forgetToken().catch(() => {});
    $('sheet-code').hidden = true;
    say('');
    warn(String(err.message ?? err));
    $('sheet-go').disabled = false;
  } finally {
    busy = false;
  }
}

// A browser (not the Android app) cannot sign in to GitHub: it hands over the file, for the author to add themself.
function showManual(submission) {
  manual = { text: JSON.stringify(submission), name: submissionFileName(submission) };
  say(`Signed. Signing in to GitHub needs the Android app: add this file as submissions/${manual.name} in a pull request to ${config.repository}.`);
  $('sheet-manual').hidden = false;
  $('link-pr').href = `https://github.com/${config.repository}/new/main?filename=${encodeURIComponent(`submissions/${manual.name}`)}`;
  $('sheet-go').disabled = false;
}

function download() {
  const url = URL.createObjectURL(new Blob([manual.text], { type: 'application/json' }));
  const a = document.createElement('a');
  a.href = url;
  a.download = manual.name;
  document.body.append(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 10_000);
}

// ---------- hand-off ----------

async function handoff() {
  const match = /^#publish=(code|aiwa);([A-Za-z0-9_.-]{1,60});([A-Za-z0-9_-]+)$/.exec(location.hash);
  if (!match) return;
  try { history.replaceState(null, '', location.pathname + location.search); } catch { /* cosmetic */ }
  switchView('store');
  try {
    openPublish(appFrom(match[1], match[2], await unpack(match[3])));
  } catch (err) {
    $('store-status').textContent = `An app was received, but not opened: ${err.message}.`;
  }
}

export function initPublish() {
  onSession(() => { if (job && !$('sheet').hidden) ready(); });
  $('app-name').addEventListener('input', () => { if (!$('app-id').dataset.touched) $('app-id').value = slug($('app-name').value); });
  $('app-id').addEventListener('input', () => { $('app-id').dataset.touched = '1'; });
  $('sheet-try').addEventListener('click', () => {
    if (job?.mode === 'publish') openViewer({ name: $('app-name').value.trim() || 'Preview', meta: 'preview, not published', html: htmlOf(job.app) });
  });
  $('sheet-go').addEventListener('click', go);
  $('sheet-close').addEventListener('click', close);
  $('sheet-copy-code').addEventListener('click', async () => {
    try { await navigator.clipboard.writeText($('sheet-code-text').textContent); flash($('sheet-copy-code'), 'Copied'); } catch { /* the app copied it already */ }
  });
  $('btn-download').addEventListener('click', () => {
    if (hostAvailable()) hostPost({ cmd: 'save', name: manual.name, mime: 'application/json', text: manual.text });
    else download();
  });
  $('btn-copy-submission').addEventListener('click', async () => {
    try { await navigator.clipboard.writeText(manual.text); flash($('btn-copy-submission'), 'Copied'); } catch { warn('The browser refused the clipboard: use Download.'); }
  });
  handoff();
  // The Android app opens the same page again on a new address when the Store is already open: only the fragment changes.
  window.addEventListener('hashchange', handoff);
}

export const sheetIsOpen = () => !$('sheet').hidden;
export const closeSheet = close;
