// The publish tab: turn an HTML file into a signed package with the author's mining evidence, as one file to put in a
// pull request (submissions/<name>.json). Nothing is sent from here: the author reads the app, signs, and submits.

import { buildAppPackage, buildRefreshAuthorization } from 'aiwa-registry/app-package';
import { config } from './config.js';
import { session, onSession, connected } from './session.js';
import { $, flash, switchView } from './ui.js';
import { openViewer } from './viewer.js';

const STARTER = `<!doctype html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>My app</title>
<style>body { font: 16px/1.5 system-ui, sans-serif; padding: 16px; }</style>
</head>
<body>
<h1>My app</h1>
<p>Taps: <b id="n">0</b></p>
<button id="b">Tap</button>
<script>
// Runs in a sandbox: no access to the wallet, no storage that survives. Keep state in memory, or fetch it.
let n = 0;
document.getElementById('b').onclick = () => { document.getElementById('n').textContent = ++n; };
<\/script>
</body>
</html>
`;

let lastSubmission = null;
let lastName = 'submission.json';

const slug = (text) => text.trim().toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 40).replace(/-+$/g, '');

function fields() {
  return {
    id: $('app-id').value.trim(),
    name: $('app-name').value.trim(),
    version: $('app-version').value.trim() || '1.0.0',
    description: $('app-description').value.trim(),
    html: $('app-html').value,
  };
}

function showNext(submission, name, steps) {
  lastSubmission = JSON.stringify(submission);
  lastName = name;
  $('publish-next').hidden = false;
  $('link-pr').href = `https://github.com/${config.repository}/new/main?filename=${encodeURIComponent(`submissions/${name}`)}`;
  $('publish-steps').textContent = steps;
}

async function prepare() {
  $('publish-next').hidden = true;
  const out = $('publish-result');
  if (!connected()) { out.textContent = 'Connect first: publishing signs with your identity.'; return; }
  const f = fields();
  try {
    const pkg = await buildAppPackage(session.aiwa.identity, f);
    const evidence = await session.aiwa.submissionEvidence();
    const submission = { format: 'aiwa-submission/1', kind: 'publish', package: pkg, evidence };
    const kb = Math.round(JSON.stringify(submission).length / 1024);
    out.textContent = `Signed ${pkg.id} ${pkg.version} (${kb} KB with your mining evidence).`;
    showNext(submission, `${pkg.id}-${pkg.version}.json`,
      `1. Download or copy the file. 2. Open the pull request link: GitHub proposes the change from a copy of the repository. `
      + `3. Name the file submissions/${pkg.id}-${pkg.version}.json, paste, commit, open the pull request. `
      + 'The registry checks it on its own and answers on the pull request: it needs something claimable (you must have burned and mined), and your burns are confirmed against Solana.');
  } catch (err) {
    out.textContent = `error: ${err.message}`;
  }
}

async function prepareRefresh() {
  const out = $('refresh-result');
  const id = $('refresh-id').value.trim();
  if (!connected()) { out.textContent = 'Connect first.'; return; }
  if (!id) { out.textContent = 'Enter the id of one of your apps.'; return; }
  try {
    const authorization = await buildRefreshAuthorization(session.aiwa.identity, { id });
    const evidence = await session.aiwa.submissionEvidence();
    const submission = { format: 'aiwa-submission/1', kind: 'refresh', appId: id, authorization, evidence };
    out.textContent = `Signed the refresh of ${id}.`;
    $('publish-result').textContent = '';
    showNext(submission, `${id}-refresh-${Date.now()}.json`, 'Same steps: download or copy, then the pull request link.');
  } catch (err) {
    out.textContent = `error: ${err.message}`;
  }
}

function download() {
  const url = URL.createObjectURL(new Blob([lastSubmission], { type: 'application/json' }));
  const a = document.createElement('a');
  a.href = url;
  a.download = lastName;
  document.body.append(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 10_000);
}

// ---------- hand-off from the Android app ----------
// The app's dictation module has Claude write an app (one self-contained index.html) and opens this page at
//   #publish=1;<name>;<code>
// where <code> is the file, raw-deflated then base64url-encoded. A URL fragment never leaves the browser. The Publish form
// is filled in and NOTHING is signed or sent: reading the code and pressing Prepare stay the author's.
function handoff() {
  const match = /^#publish=1;([A-Za-z0-9_.-]{1,60});([A-Za-z0-9_-]+)$/.exec(location.hash);
  if (!match) return;
  try { history.replaceState(null, '', location.pathname + location.search); } catch { /* cosmetic */ }
  const MAX_CHARS = 1_000_000;
  const note = $('handoff-note');
  note.hidden = false;

  async function unpack(b64url) {
    if (typeof DecompressionStream !== 'function') throw new Error('this browser cannot unpack it (DecompressionStream)');
    const bytes = Uint8Array.from(atob(b64url.replace(/-/g, '+').replace(/_/g, '/')), (c) => c.charCodeAt(0));
    let text;
    try { text = await new Response(new Blob([bytes]).stream().pipeThrough(new DecompressionStream('deflate-raw'))).text(); }
    catch { throw new Error('the data is damaged'); }
    if (text.length > MAX_CHARS) throw new Error('it is too big');
    return text;
  }

  (async () => {
    let code;
    try { code = await unpack(match[2]); } catch (err) { note.textContent = `An app was received, but not opened: ${err.message}.`; return; }
    const name = match[1];
    note.textContent = `App « ${name} » received: connect, it will be waiting in the Publish tab.`;
    const started = Date.now();
    const wait = setInterval(() => {
      if (Date.now() - started > 30 * 60 * 1000) { clearInterval(wait); return; }
      if ($('publish-section').hidden) return;     // not connected yet
      clearInterval(wait);
      $('app-name').value = name;
      $('app-id').value = slug(name);
      $('app-version').value = '1.0.0';
      $('app-html').value = code;
      switchView('publish');
      $('app-html').scrollIntoView({ block: 'center' });
      note.textContent = `App « ${name} » is in the Publish tab: read it, try it, then press Prepare submission.`;
    }, 400);
  })();
}

export function initPublish() {
  $('app-html').value = STARTER;
  const gate = () => { $('publish-section').hidden = !connected(); $('publish-hint').hidden = connected(); };
  onSession(gate);
  gate();
  $('app-name').addEventListener('input', () => { if (!$('app-id').dataset.touched) $('app-id').value = slug($('app-name').value); });
  $('app-id').addEventListener('input', () => { $('app-id').dataset.touched = '1'; });
  $('btn-try').addEventListener('click', () => openViewer({ name: $('app-name').value.trim() || 'Preview', meta: 'preview, not published', html: $('app-html').value }));
  $('btn-prepare').addEventListener('click', prepare);
  $('btn-refresh-prepare').addEventListener('click', prepareRefresh);
  $('btn-download').addEventListener('click', download);
  $('btn-copy-submission').addEventListener('click', async () => {
    try { await navigator.clipboard.writeText(lastSubmission); flash($('btn-copy-submission'), 'Copied'); }
    catch { $('publish-result').textContent = 'The browser refused the clipboard: use Download.'; }
  });
  handoff();
}
