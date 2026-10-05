// The web app in a real browser (Chromium), against a registry that a real run of aiwa-registry produced, a Solana
// stand-in, a stand-in for the Android app's host channel and one for GitHub. What it checks: the store lists apps of both
// kinds ranked by score / laps; an app opens in a sandbox that cannot reach the page; a package the host tampered with is
// not opened; the last list and opened apps work offline; the wallet starts by itself, comes back by itself, and its
// history comes back from the registry on a new phone; the wallet says what a burn does with the creator fee before
// signing; and what the publish sheet sends as a pull request is what the registry accepts.
import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { mkdtempSync, mkdirSync, writeFileSync, readFileSync, existsSync, statSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, extname, resolve, dirname } from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright';
import QRCode from 'qrcode';
import { deflateRawSync } from 'node:zlib';
import { buildAppPackage, buildBundle, validateSubmission, applyAccepted, emptyStore, writeStore, rankApps } from 'aiwa-registry';
import { CREATOR, deployment as testDeployment, fakeSolana, minedWallet } from '../../../registry/support/helpers.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const root = resolve(here, '../../..');
const tmp = mkdtempSync(join(tmpdir(), 'aiwa-web-'));
const site = join(tmp, 'site');
const MIME = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.png': 'image/png', '.webmanifest': 'application/manifest+json' };

let server, base, browser;
globalThis.cameraText = `duel1.${Buffer.from(Array.from({ length: 450 }, (_, i) => (i * 53 + 7) % 251)).toString('base64url')}`;   // what the fake camera shows
const control = { down: false, tamper: null };     // what a flaky or dishonest host does

const html = (title, script = '') => `<!doctype html><html><head><meta charset="utf-8"><title>${title}</title></head><body><h1>${title}</h1><p id="r">…</p><script>${script}</script></body></html>`;
const GAMMA_FILES = [
  { path: 'index.html', content: '<!doctype html><html><head><meta charset="utf-8"><link rel="stylesheet" href="g.css"></head><body><h1>Gamma</h1><p id="r">…</p><script src="g.js"></script></body></html>' },
  { path: 'g.css', content: 'h1 { color: #7c5cff; }' },
  { path: 'g.js', content: 'document.getElementById("r").textContent = "from a file";' },
];
const ESCAPE_PROBE = `try { window.parent.document.title; document.getElementById('r').textContent = 'ESCAPED'; } catch (e) { document.getElementById('r').textContent = 'isolated'; }`;

before(async () => {
  // 1. the deployment the test build uses: the repository's, with the stand-in creator address and a fast tick
  const deployment = JSON.parse(readFileSync(join(root, 'deployment.json'), 'utf8'));
  deployment.registryUrl = './store';
  deployment.rpc = 'http://127.0.0.1:1';
  deployment.progress = { intervalMs: 150 };
  deployment.github = { clientId: 'test-client' };
  deployment.nearby = { iceServers: [] };                // two phones in the same room need no outside help
  deployment.rewardParams = { ...deployment.rewardParams, epochIterations: 150 };
  deployment.creatorFee = { address: CREATOR, rateOfT: 0.001 };
  mkdirSync(tmp, { recursive: true });
  writeFileSync(join(tmp, 'deployment.json'), JSON.stringify(deployment));
  execFileSync('node', [join(here, '..', 'build.mjs'), '--out', site, '--deployment', join(tmp, 'deployment.json')], { stdio: 'pipe' });

  // 2. a registry, made the way the real one is: wallets that burned and mined, packages they signed, submissions validated
  const connection = fakeSolana();
  let store = emptyStore();
  let at = Date.now();
  const alice = await minedWallet(connection, { epochs: 6 });
  const bob = await minedWallet(connection, { epochs: 2 });
  const carol = await minedWallet(connection, { epochs: 4 });
  globalThis.alicePhrase = alice.recoveryPhrase;
  globalThis.aliceAddress = alice.address;
  for (const [wallet, fields] of [
    [alice, { id: 'alpha', name: 'Alpha', description: 'tries to reach the wallet', html: html('Alpha', ESCAPE_PROBE) }],
    [bob, { id: 'beta', name: 'Beta', description: 'says hello', html: html('Beta') }],
    [carol, { id: 'gamma', name: 'Gamma', description: 'code through Aiwa', files: GAMMA_FILES }],
  ]) {
    const { files, ...fields2 } = fields;
    let pkg;
    let bundle;
    if (files) {
      const built = await buildBundle(wallet.identity, { name: fields2.name, version: '1.0.0', files });
      bundle = built.bundle;
      pkg = await buildAppPackage(wallet.identity, { version: '1.0.0', ...fields2, manifestId: built.manifestId });
    } else {
      pkg = await buildAppPackage(wallet.identity, { version: '1.0.0', ...fields2 });
    }
    const submission = { format: 'aiwa-submission/1', kind: 'publish', package: pkg, ...(bundle ? { bundle } : {}), evidence: await wallet.submissionEvidence() };
    const result = await validateSubmission({ submission, store, deployment: testDeployment, connection, now: at += 1000 });
    assert.equal(result.ok, true, result.reason);
    writeStore(join(site, 'store'), (store = applyAccepted(store, result.accepted)), result.accepted);
  }
  globalThis.expectedOrder = rankApps(store.index.apps).map((a) => a.name);

  // 3. a host
  server = createServer((req, res) => {
    const url = new URL(req.url, 'http://x');
    let path = decodeURIComponent(url.pathname);
    if (path.endsWith('/')) path += 'index.html';
    const file = join(site, path);
    if (!file.startsWith(site) || !existsSync(file) || !statSync(file).isFile()) { res.writeHead(404).end('not found'); return; }
    if (control.down && path.startsWith('/store/')) { res.writeHead(503).end('down'); return; }
    let body = readFileSync(file);
    if (control.tamper && path === `/store/${control.tamper.path}`) {
      const pkg = JSON.parse(body.toString());
      body = Buffer.from(JSON.stringify({ ...pkg, html: pkg.html.replace('Beta', 'EVIL') }));
    }
    // the SDK is imported by an app in the sandbox (an opaque origin), which a module can only be fetched for with CORS
    const cors = path === '/lib/aiwa.js' ? { 'access-control-allow-origin': '*' } : {};
    res.writeHead(200, { 'content-type': MIME[extname(file)] ?? 'application/octet-stream', 'cache-control': 'no-store', ...cors }).end(body);
  }).listen(0, '127.0.0.1');
  await new Promise((r) => server.once('listening', r));
  base = `http://127.0.0.1:${server.address().port}`;

  // A camera that films a QR code (a .y4m video: one Y plane of black and white, flat colour planes), for the scan
  const { size, data } = QRCode.create(globalThis.cameraText, { errorCorrectionLevel: 'L' }).modules;
  const [W, H] = [640, 480];
  const scale = Math.floor(440 / (size + 8));
  const frame = Buffer.alloc(W * H * 3 / 2, 128);
  frame.fill(235, 0, W * H);
  for (let row = 0; row < size; row++) for (let col = 0; col < size; col++) {
    if (!data[row * size + col]) continue;
    for (let y = 0; y < scale; y++) for (let x = 0; x < scale; x++) frame[(((H - size * scale) >> 1) + row * scale + y) * W + (((W - size * scale) >> 1) + col * scale + x)] = 16;
  }
  const video = join(tmp, 'qr.y4m');
  writeFileSync(video, Buffer.concat([Buffer.from(`YUV4MPEG2 W${W} H${H} F10:1 Ip A1:1 C420jpeg\n`), ...Array.from({ length: 5 }, () => Buffer.concat([Buffer.from('FRAME\n'), frame]))]));

  // WebRTC between two pages of one browser (the click duel): real addresses, not mDNS names, and loopback allowed
  browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || undefined, args: ['--disable-features=WebRtcHideLocalIpsWithMdns', '--allow-loopback-in-peer-connection', '--use-fake-ui-for-media-stream', '--use-fake-device-for-media-stream', `--use-file-for-fake-video-capture=${video}`] });
});

after(async () => { await browser?.close(); server?.close(); });

async function openPage(extra = async () => {}) {
  const context = await browser.newContext({ viewport: { width: 420, height: 860 } });
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', (e) => errors.push(e.message));
  await extra(page);
  await page.goto(`${base}/index.html`);
  return { page, errors, context };
}

// A Solana that decodes the transaction the wallet built, as aiwa-lib's own tests do. It lives in the page, unless a `chain` is
// given: then the transactions are kept in the test, so that two pages (two phones) see each other's burns, as on one network.
const injectSolana = async (page, chain = null) => {
  if (chain) {
    await page.exposeFunction('__chainPut', (signature, tx) => { chain.set(signature, tx); });
    await page.exposeFunction('__chainGet', (signature) => chain.get(signature) ?? null);
  }
  await page.addInitScript((shared) => {
    const transactions = {};
    let count = 0;
    const put = async (signature, tx) => { if (shared) await window.__chainPut(signature, tx); else transactions[signature] = tx; };
    const get = async (signature) => (shared ? window.__chainGet(signature) : (transactions[signature] ?? null));
    window.__aiwaTest = {
      connection: {
        transactions,
        getLatestBlockhash: async () => ({ blockhash: '11111111111111111111111111111111', lastValidBlockHeight: 1 }),
        sendRawTransaction: async (raw) => {
          const w3 = window.solanaWeb3;
          const tx = w3.Transaction.from(raw);
          const keys = [tx.feePayer.toBase58()];
          const transfers = tx.instructions.map((ix) => {
            const d = w3.SystemInstruction.decodeTransfer(ix);
            return { from: d.fromPubkey.toBase58(), to: d.toPubkey.toBase58(), lamports: Number(d.lamports) };
          });
          for (const t of transfers) for (const k of [t.from, t.to]) if (!keys.includes(k)) keys.push(k);
          const pre = keys.map((_, i) => (i === 0 ? 50e9 : 0));
          const post = [...pre];
          for (const t of transfers) { post[keys.indexOf(t.from)] -= t.lamports; post[keys.indexOf(t.to)] += t.lamports; }
          post[0] -= 5000;
          const signature = `browsersig${Math.random().toString(36).slice(2, 8)}${++count}`;
          await put(signature, { slot: 10, transaction: { message: { accountKeys: keys } }, meta: { err: null, fee: 5000, preBalances: pre, postBalances: post } });
          return signature;
        },
        confirmTransaction: async () => ({}),
        getTransaction: get,
        getBalance: async () => 50e9,
      },
    };
  }, !!chain);
};

test('the store lists the apps ranked by score / laps, and search narrows the list', async () => {
  const { page, errors, context } = await openPage();
  await page.waitForSelector('#store-list .app');
  const names = await page.locator('#store-list .app .title').evaluateAll((els) => els.map((e) => e.firstChild.textContent));
  assert.deepEqual(names, globalThis.expectedOrder);
  const ranks = await page.locator('#store-list .app .rank').allTextContents();
  assert.deepEqual(ranks, ['1', '2', '3']);
  assert.match(await page.locator('#store-list .app .meta').first().textContent(), /score\/laps [\d.e+-]+ \(/);

  await page.fill('#store-search', 'hello');
  assert.deepEqual(await page.locator('#store-list .app .title').evaluateAll((els) => els.map((e) => e.firstChild.textContent)), ['Beta']);
  await page.fill('#store-search', 'nothing like this');
  assert.equal(await page.locator('#store-list .app').count(), 0);
  assert.deepEqual(errors, []);
  await context.close();
});

test('an app opens in a sandbox that cannot reach the page: opaque origin, no same-origin access', async () => {
  const { page, context } = await openPage();
  await page.waitForSelector('#store-list .app');
  await page.locator('#store-list .app[data-id="alpha"] button').click();
  const iframe = page.locator('#viewer iframe');
  await iframe.waitFor();
  assert.equal(await iframe.getAttribute('sandbox'), 'allow-scripts', 'allow-scripts and nothing else, never allow-same-origin');
  const frame = page.frameLocator('#viewer iframe');
  await frame.locator('h1').waitFor();
  assert.equal(await frame.locator('h1').textContent(), 'Alpha');
  assert.equal(await frame.locator('#r').textContent(), 'isolated', 'the app tried parent.document and was refused by the browser');
  assert.equal(await page.evaluate(() => document.querySelector('#viewer iframe').contentDocument), null, 'and the page cannot read the app either');
  await page.click('#viewer-close');
  assert.equal(await page.locator('#viewer').isHidden(), true);
  await context.close();
});

test('a host that changes a package is caught: the app is not opened, and the reason is shown', async () => {
  const { page, context } = await openPage();
  await page.waitForSelector('#store-list .app');
  control.tamper = { path: 'apps/beta/1.0.0.json' };
  try {
    await page.locator('#store-list .app[data-id="beta"] button').click();
    await page.waitForFunction(() => /Not opened/.test(document.getElementById('store-status').textContent));
    assert.match(await page.locator('#store-status').textContent(), /hash does not match the content/);
    assert.equal(await page.locator('#viewer').isHidden(), true);
  } finally { control.tamper = null; }
  await context.close();
});

test('offline: the last list is shown and an app already opened still opens', async () => {
  const { page, context } = await openPage();
  await page.waitForSelector('#store-list .app');
  await page.locator('#store-list .app[data-id="beta"] button').click();           // opened once: kept by its hash
  await page.locator('#viewer iframe').waitFor();
  await page.click('#viewer-close');

  control.down = true;
  try {
    await page.click('#store-refresh');
    await page.waitForFunction(() => /Offline/.test(document.getElementById('store-status').textContent));
    assert.equal(await page.locator('#store-list .app').count(), 3, 'the last list seen');
    await page.locator('#store-list .app[data-id="beta"] button').click();
    await page.locator('#viewer iframe').waitFor();
    assert.equal(await page.frameLocator('#viewer iframe').locator('h1').textContent(), 'Beta');
    await page.click('#viewer-close');
    await page.locator('#store-list .app[data-id="alpha"] button').click();         // never opened: needs the host
    await page.waitForFunction(() => /does not serve this app/.test(document.getElementById('store-status').textContent));
  } finally { control.down = false; }
  await context.close();
});


// ---------- the Android host and GitHub, as stand-ins ----------

// The host channel the Android app gives the page, with a keystore that outlives a reload (as the phone's does).
const injectHost = (page, { loginDelayMs = 120 } = {}) => page.addInitScript(({ loginDelayMs }) => {
  const key = '__fake_keystore';
  const read = () => JSON.parse(localStorage.getItem(key) ?? '{}');
  window.__posted = [];
  const reply = (id, body) => setTimeout(() => window.AiwaHost.onmessage?.({ data: JSON.stringify({ id, ...body }) }), 0);
  window.AiwaHost = {
    onmessage: null,
    postMessage(raw) {
      const m = JSON.parse(raw);
      window.__posted.push(m);
      if (m.cmd === 'secret-get') reply(m.id, { result: { value: read()[m.key] ?? null } });
      else if (m.cmd === 'secret-set') { localStorage.setItem(key, JSON.stringify({ ...read(), [m.key]: m.value })); reply(m.id, { result: {} }); }
      else if (m.cmd === 'secret-delete') { const all = read(); delete all[m.key]; localStorage.setItem(key, JSON.stringify(all)); reply(m.id, { result: {} }); }
      else if (m.cmd === 'github-login') {
        reply(m.id, { progress: { userCode: 'WXYZ-1234', verificationUri: 'https://github.com/login/device' } });
        setTimeout(() => reply(m.id, { result: { token: 'gho_test' } }), loginDelayMs);
      }
    },
  };
}, { loginDelayMs });

// GitHub's API, for what a submission needs: the account, a fork, a branch, a file, a pull request. It keeps what it is given.
async function fakeGitHub(context, { login = 'author' } = {}) {
  const seen = { files: {}, pulls: [], tokens: new Set(), valid: new Set(['gho_test']) };
  const cors = { 'access-control-allow-origin': '*', 'access-control-allow-headers': '*', 'access-control-allow-methods': '*', 'content-type': 'application/json' };
  await context.route('https://api.github.com/**', async (route) => {
    const request = route.request();
    if (request.method() === 'OPTIONS') return route.fulfill({ status: 204, headers: cors });
    const path = new URL(request.url()).pathname;
    const token = (request.headers().authorization ?? '').replace('Bearer ', '');
    seen.tokens.add(token);
    const send = (body, status = 200) => route.fulfill({ status, headers: cors, body: JSON.stringify(body) });
    if (!seen.valid.has(token)) return send({ message: 'Bad credentials' }, 401);
    const data = request.postData() ? JSON.parse(request.postData()) : null;
    if (path === '/user') return send({ login });
    if (path === `/repos/${REPO}` && request.method() === 'GET') return send({ default_branch: 'main' });
    if (path === `/repos/${REPO}/git/ref/heads/main`) return send({ object: { sha: 'base-sha' } });
    if (path === `/repos/${REPO}/forks`) return send({ full_name: `${login}/Aiwa_store` }, 202);
    if (path === `/repos/${login}/Aiwa_store` && request.method() === 'GET') return send({});
    if (path.endsWith('/git/refs')) return send({}, 201);
    if (path.includes('/contents/')) { seen.files[decodeURIComponent(path.split('/contents/')[1])] = Buffer.from(data.content, 'base64').toString('utf8'); return send({}, 201); }
    if (path === `/repos/${REPO}/pulls`) { seen.pulls.push(data); return send({ html_url: `https://github.com/${REPO}/pull/${seen.pulls.length}`, number: seen.pulls.length }, 201); }
    return send({ message: `unexpected ${request.method()} ${path}` }, 500);
  });
  return seen;
}
const REPO = 'theodoreyong9/Aiwa_store';
const SDK_URL = 'https://theodoreyong9.github.io/Aiwa_store/lib/aiwa.js';

const pack = (text) => deflateRawSync(Buffer.from(text, 'utf8')).toString('base64url');
const handoff = (page, kind, name, text) => page.evaluate((hash) => { location.hash = hash; }, `#publish=${kind};${name};${pack(text)}`);

/** A wallet that burned and mined in the page, ready to publish. */
async function mineInPage(page, { epoch = 3, timeout = 40000 } = {}) {
  await page.click('#app-nav [data-view="wallet"]');
  await page.click('#btn-create');
  await page.waitForSelector('#wallet-section:not([hidden])');
  await page.fill('#burn-amount', '1');
  await page.click('#btn-burn');
  await page.waitForFunction(() => /Burned and committed/.test(document.getElementById('burn-result').textContent));
  await page.waitForFunction((n) => new RegExp(`epoch ${n}|epoch [${n}-9]`).test(document.getElementById('out-mining').textContent), epoch, { timeout })
    .catch(async (error) => { throw new Error(`the wallet did not reach epoch ${epoch}; it shows: ${await page.locator('#out-mining').textContent()}`, { cause: error }); });
}

/** What the registry says about the submission a pull request carried. */
async function registryVerdict(page, seen, file) {
  const transactions = await page.evaluate(() => window.__aiwaTest.connection.transactions);
  const connection = { getTransaction: async (signature) => transactions[signature] ?? null };
  const submission = JSON.parse(seen.files[file]);
  return { submission, result: await validateSubmission({ submission, store: emptyStore(), deployment: testDeployment, connection, now: Date.now() }) };
}

// ---------- the wallet ----------

test('the wallet starts by itself: one tap the first time, never again; the 12 words are shown once and can be shown again', async () => {
  const { page, errors, context } = await openPage(async (p) => { await injectSolana(p); await injectHost(p); });
  await page.click('#app-nav [data-view="wallet"]');
  await page.waitForSelector('#welcome:not([hidden])');
  assert.equal(await page.locator('#wallet-section').isHidden(), true, 'a phone with no wallet is asked once');
  await page.click('#btn-create');
  await page.waitForSelector('#wallet-section:not([hidden])');
  const phrase = (await page.locator('#phrase-words').textContent()).trim();
  assert.equal(phrase.split(' ').length, 12);
  assert.equal(await page.locator('#phrase-notice').isVisible(), true);
  const address = await page.locator('#out-address').getAttribute('data-full');
  assert.equal((await page.evaluate(() => window.__posted.filter((m) => m.cmd === 'secret-set'))).length, 1, 'the phrase went to the phone\'s keystore');
  assert.equal(await page.evaluate(() => localStorage.getItem('aiwa-store:phrase')), null, 'and not to the page\'s own storage');

  await page.click('#btn-phrase-done');
  await page.reload();
  await page.click('#app-nav [data-view="wallet"]');
  await page.waitForSelector('#wallet-section:not([hidden])');
  assert.equal(await page.locator('#welcome').isHidden(), true, 'no question the second time');
  assert.equal(await page.locator('#out-address').getAttribute('data-full'), address, 'the same wallet came back by itself');
  assert.equal(await page.locator('#phrase-notice').isHidden(), true, 'the words are not pushed again once acknowledged');
  assert.equal(await page.locator('#btn-disconnect').count(), 0, 'there is nothing to connect or disconnect');
  assert.equal(await page.locator('text=Restore from the nodes').count(), 0);

  await page.click('#recovery-section summary');
  await page.click('#btn-show-phrase');
  assert.equal((await page.locator('#out-phrase').textContent()).trim(), phrase);
  assert.deepEqual(errors, []);
  await context.close();
});

test('on a new phone the 12 words bring the wallet back, and the registry brings its mining back by itself', async () => {
  const { page, errors, context } = await openPage(async (p) => { await injectSolana(p); await injectHost(p); });
  await page.click('#app-nav [data-view="wallet"]');
  await page.waitForSelector('#welcome:not([hidden])');
  await page.click('#welcome summary');
  await page.fill('#mnemonic', globalThis.alicePhrase);
  await page.click('#btn-restore');
  await page.waitForSelector('#wallet-section:not([hidden])');
  assert.equal(await page.locator('#out-address').getAttribute('data-full'), globalThis.aliceAddress);
  await page.waitForFunction(() => /Mining 1 SOL/.test(document.getElementById('out-mining').textContent), null, { timeout: 40000 });
  assert.match(await page.locator('#restore-note').textContent(), /came back from the registry: epoch [1-9]/);
  await page.waitForSelector('#my-apps-section:not([hidden])');
  assert.match(await page.locator('#my-apps-list').textContent(), /Alpha v1\.0\.0/, 'and the author\'s own app is listed');
  assert.deepEqual(errors, []);
  await context.close();

  // a phrase that is not one is refused, and the page stays as it was
  const second = await openPage(async (p) => { await injectSolana(p); await injectHost(p); });
  await second.page.click('#app-nav [data-view="wallet"]');
  await second.page.click('#welcome summary');
  await second.page.fill('#mnemonic', 'these are not twelve real words');
  await second.page.click('#btn-restore');
  await second.page.waitForFunction(() => document.getElementById('connect-error').textContent.length > 0);
  assert.equal(await second.page.locator('#wallet-section').isHidden(), true);
  await second.context.close();
});

test('the wallet says what a burn does with the creator fee before signing, burns, and mines', async () => {
  const { page, errors, context } = await openPage(injectSolana);
  await page.click('#app-nav [data-view="wallet"]');
  await page.click('#btn-create');
  await page.waitForSelector('#wallet-section:not([hidden])');
  assert.match(await page.locator('#out-mining').textContent(), /No burn yet/);

  await page.fill('#burn-amount', '1');
  await page.fill('#burn-t', '40');
  const preview = await page.locator('#burn-preview').textContent();
  assert.match(preview, /Counts as 0\.6 SOL of capital/);
  assert.match(preview, /0\.0004 SOL of the burn goes to the creator/);
  assert.ok(preview.includes(`${CREATOR.slice(0, 8)}…`), 'it names the creator address');

  await page.click('#btn-burn');
  await page.waitForFunction(() => /Burned and committed/.test(document.getElementById('burn-result').textContent));
  const transfers = await page.evaluate(() => {
    const [tx] = Object.values(window.__aiwaTest.connection.transactions);
    const keys = tx.transaction.message.accountKeys;
    return keys.map((k, i) => ({ key: k, delta: tx.meta.postBalances[i] - tx.meta.preBalances[i] }));
  });
  assert.equal(transfers.find((t) => t.key === CREATOR).delta, 400_000, 'the creator received 0.0004 SOL in the same transaction');
  await page.waitForFunction(() => /Mining 0\.6 SOL · T 40 %/.test(document.getElementById('out-mining').textContent));
  await page.waitForFunction(() => /epoch [2-9]/.test(document.getElementById('out-mining').textContent), null, { timeout: 40000 });
  assert.deepEqual(errors, []);
  await context.close();
});

// ---------- the publish sheet ----------

const SAMPLE_APP = `<!doctype html><html><head><meta charset="utf-8"><meta name="description" content="counts taps"><title>Taps</title></head><body><h1>Taps</h1></body></html>`;

test('publishing from the widget\'s hand-off: one sheet, a GitHub login once, a pull request the registry accepts', async () => {
  const { page, errors, context } = await openPage(async (p) => { await injectSolana(p); await injectHost(p); });
  const github = await fakeGitHub(context);
  await mineInPage(page);

  await handoff(page, 'code', 'Taps', SAMPLE_APP);
  await page.waitForSelector('#sheet:not([hidden])');
  assert.equal(await page.inputValue('#app-name'), 'Taps');
  assert.equal(await page.inputValue('#app-id'), 'taps');
  assert.equal(await page.inputValue('#app-version'), '1.0.0');
  assert.equal(await page.inputValue('#app-description'), 'counts taps', 'taken from the app\'s own description');
  await page.waitForFunction(() => !document.getElementById('sheet-go').disabled);

  await page.click('#sheet-go');
  await page.waitForFunction(() => document.getElementById('sheet-code-text').textContent === 'WXYZ-1234', null, { timeout: 5000 });
  assert.match(await page.locator('#sheet-code-uri').textContent(), /github\.com\/login\/device/);
  await page.waitForSelector('#sheet-result:not([hidden])', { timeout: 10000 });
  assert.match(await page.locator('#sheet-result').getAttribute('href'), new RegExp(`github\\.com/${REPO}/pull/1`));
  assert.deepEqual(Object.keys(github.files), ['submissions/taps-1.0.0.json']);
  assert.equal(github.pulls[0].head.split(':')[0], 'author');
  assert.deepEqual([...github.tokens], ['gho_test']);

  const { submission, result } = await registryVerdict(page, github, 'submissions/taps-1.0.0.json');
  assert.equal(submission.package.kind, 'code');
  assert.equal(result.ok, true, result.reason);
  assert.equal(result.accepted.entry.id, 'taps');
  assert.deepEqual(errors, []);
  await context.close();
});

test('an app published through Aiwa: the pull request carries a pointer and the signed bundle, never the code', async () => {
  const { page, errors, context } = await openPage(async (p) => { await injectSolana(p); await injectHost(p); });
  const github = await fakeGitHub(context);
  await mineInPage(page);

  const contract = '<!doctype html><html><head><meta charset="utf-8"><meta name="description" content="a tally"><title>Tally</title></head><body><h1>Tally</h1><script>document.title="Tally"</script></body></html>';
  await handoff(page, 'aiwa', 'Tally', contract);
  await page.waitForSelector('#sheet:not([hidden])');
  assert.match(await page.locator('#sheet-summary').textContent(), /published through Aiwa/);
  await page.click('#sheet-go');
  await page.waitForSelector('#sheet-result:not([hidden])', { timeout: 15000 });

  const { submission, result } = await registryVerdict(page, github, 'submissions/tally-1.0.0.json');
  assert.equal(submission.package.kind, 'aiwa');
  assert.equal(submission.package.html, undefined, 'GitHub holds a pointer, not code');
  assert.equal(submission.bundle.events.length, 2, 'the file and the manifest that pins it');
  assert.equal(result.ok, true, result.reason);
  assert.equal(result.accepted.entry.kind, 'aiwa');
  assert.equal(result.accepted.entry.manifestId, submission.package.manifestId);
  assert.deepEqual(errors, []);
  await context.close();
});

test('the GitHub login is asked once: a second publication reuses the token, and a token GitHub refuses is asked for again', async () => {
  const { page, context } = await openPage(async (p) => { await injectSolana(p); await injectHost(p); });
  const github = await fakeGitHub(context);
  await mineInPage(page);
  const logins = () => page.evaluate(() => window.__posted.filter((m) => m.cmd === 'github-login').length);

  await handoff(page, 'code', 'First', SAMPLE_APP);
  await page.waitForSelector('#sheet:not([hidden])');
  await page.click('#sheet-go');
  await page.waitForSelector('#sheet-result:not([hidden])', { timeout: 10000 });
  assert.equal(await logins(), 1);
  await page.click('#sheet-close');

  await handoff(page, 'code', 'Second', SAMPLE_APP);
  await page.waitForSelector('#sheet:not([hidden])');
  await page.waitForFunction(() => !document.getElementById('sheet-go').disabled);
  await page.click('#sheet-go');
  await page.waitForSelector('#sheet-result:not([hidden])', { timeout: 10000 });
  assert.equal(await logins(), 1, 'no second login');
  assert.equal(github.pulls.length, 2);
  await page.click('#sheet-close');

  github.valid.delete('gho_test');                       // GitHub no longer accepts it
  github.valid.add('gho_second');
  await handoff(page, 'code', 'Third', SAMPLE_APP);
  await page.waitForSelector('#sheet:not([hidden])');
  await page.waitForFunction(() => !document.getElementById('sheet-go').disabled);
  await page.click('#sheet-go');
  await page.waitForFunction(() => window.__posted.filter((m) => m.cmd === 'github-login').length === 2, null, { timeout: 5000 });
  assert.equal(await logins(), 2, 'asked again, once');
  await context.close();
});

test('the sheet says plainly when there is nothing to publish with yet, and shows an update as the next version', async () => {
  const { page, context } = await openPage(async (p) => { await injectSolana(p); await injectHost(p); });
  await fakeGitHub(context);
  await page.click('#app-nav [data-view="wallet"]');
  await page.click('#btn-create');
  await page.waitForSelector('#wallet-section:not([hidden])');
  await handoff(page, 'code', 'Taps', SAMPLE_APP);
  await page.waitForSelector('#sheet:not([hidden])');
  await page.waitForFunction(() => document.getElementById('sheet-warning').textContent.length > 0);
  assert.match(await page.locator('#sheet-warning').textContent(), /Nothing to publish with yet.*burn some SOL/);
  assert.equal(await page.locator('#sheet-go').isDisabled(), true);
  await context.close();

  // an author with a listed app: the same name is the next patch
  const author = await openPage(async (p) => { await injectSolana(p); await injectHost(p); });
  await author.page.click('#app-nav [data-view="wallet"]');
  await author.page.click('#welcome summary');
  await author.page.fill('#mnemonic', globalThis.alicePhrase);
  await author.page.click('#btn-restore');
  await author.page.waitForSelector('#my-apps-section:not([hidden])');
  await handoff(author.page, 'code', 'Alpha', SAMPLE_APP);
  await author.page.waitForSelector('#sheet:not([hidden])');
  assert.equal(await author.page.inputValue('#app-version'), '1.0.1');
  await author.context.close();
});

test('in a plain browser the page cannot sign in to GitHub: it signs and hands over the file, which the registry accepts', async () => {
  const { page, errors, context } = await openPage(injectSolana);
  await mineInPage(page);
  await handoff(page, 'code', 'Taps', SAMPLE_APP);
  await page.waitForSelector('#sheet:not([hidden])');
  await page.click('#sheet-go');
  await page.waitForSelector('#sheet-manual:not([hidden])');
  assert.match(await page.locator('#link-pr').getAttribute('href'), /github\.com\/theodoreyong9\/Aiwa_store\/new\/main\?filename=submissions%2Ftaps-1\.0\.0\.json/);
  const download = page.waitForEvent('download');
  await page.click('#btn-download');
  const file = join(tmp, 'from-browser.json');
  await (await download).saveAs(file);
  const transactions = await page.evaluate(() => window.__aiwaTest.connection.transactions);
  const result = await validateSubmission({ submission: JSON.parse(readFileSync(file, 'utf8')), store: emptyStore(), deployment: testDeployment, connection: { getTransaction: async (s) => transactions[s] ?? null }, now: Date.now() });
  assert.equal(result.ok, true, result.reason);
  assert.deepEqual(errors, []);
  await context.close();
});

test('refreshing a ranking, from the wallet\'s list of the author\'s apps, is the same sheet and a signed request', async () => {
  const { page, context } = await openPage(async (p) => { await injectSolana(p); await injectHost(p); });
  const github = await fakeGitHub(context);
  await page.click('#app-nav [data-view="wallet"]');
  await page.click('#welcome summary');
  await page.fill('#mnemonic', globalThis.alicePhrase);
  await page.click('#btn-restore');
  await page.waitForSelector('#my-apps-section:not([hidden])');
  await page.waitForFunction(() => /Mining 1 SOL/.test(document.getElementById('out-mining').textContent), null, { timeout: 40000 });
  await page.click('#my-apps-list button');
  await page.waitForSelector('#sheet:not([hidden])');
  assert.equal(await page.locator('#sheet-fields').isHidden(), true, 'nothing to fill in');
  assert.equal(await page.locator('#sheet-try').isHidden(), true);
  await page.waitForFunction(() => !document.getElementById('sheet-go').disabled);
  await page.click('#sheet-go');
  await page.waitForSelector('#sheet-result:not([hidden])', { timeout: 15000 });
  const [name] = Object.keys(github.files);
  assert.match(name, /^submissions\/alpha-refresh-\d+\.json$/);
  assert.equal(JSON.parse(github.files[name]).kind, 'refresh');
  await context.close();
});

// ---------- inside the Android app ----------

test('inside the Android app: the Dictate tab appears, back closes the sheet or the app on top, then leaves the tab', async () => {
  const { page, context } = await openPage(async (p) => { await injectSolana(p); await injectHost(p); });
  assert.equal(await page.locator('#tab-dictate').isVisible(), true);
  await page.click('#tab-dictate');
  assert.equal(await page.locator('#view-store').isVisible(), true, 'the Dictate tab opens the screen, the page stays where it was');
  assert.deepEqual(await page.evaluate(() => window.__posted.filter((m) => m.cmd === 'dictation').map((m) => m.cmd)), ['dictation']);

  await page.waitForSelector('#store-list .app');
  await page.locator('#store-list .app[data-id="beta"] button').click();
  await page.locator('#viewer iframe').waitFor();
  assert.equal(await page.evaluate(() => window.aiwaHostBack()), true);
  assert.equal(await page.locator('#viewer').isHidden(), true);

  await handoff(page, 'code', 'Taps', SAMPLE_APP);
  await page.waitForSelector('#sheet:not([hidden])');
  assert.equal(await page.evaluate(() => window.aiwaHostBack()), true, 'the sheet closes first');
  assert.equal(await page.locator('#sheet').isHidden(), true);

  await page.click('#app-nav [data-view="wallet"]');
  assert.equal(await page.evaluate(() => window.aiwaHostBack()), true);
  assert.equal(await page.locator('#view-store').isVisible(), true);
  assert.equal(await page.evaluate(() => window.aiwaHostBack()), false, 'nothing left to close: the app may leave');
  await context.close();
});

test('an app of kind aiwa opens from the store: its files are assembled and run in the same sandbox', async () => {
  const { page, errors, context } = await openPage();
  await page.waitForSelector('#store-list .app');
  assert.match(await page.locator('#store-list .app[data-id="gamma"] .meta').textContent(), /· Aiwa ·/);
  await page.locator('#store-list .app[data-id="gamma"] button').click();
  const frame = page.frameLocator('#viewer iframe');
  await frame.locator('h1').waitFor();
  assert.equal(await frame.locator('#r').textContent(), 'from a file', 'the script that lives in another file of the bundle ran');
  assert.equal(await page.locator('#viewer iframe').getAttribute('sandbox'), 'allow-scripts');
  assert.deepEqual(errors, []);
  await context.close();
});

test('the hand-off packed by the Android app (Java\'s Deflater, base64url) opens the sheet with the app byte for byte', async (t) => {
  const code = readFileSync(join(root, 'docs/store-app-example.html'), 'utf8');
  const file = join(tmp, 'handoff.html');
  writeFileSync(file, code);
  const { spawnSync } = await import('node:child_process');
  const java = spawnSync('java', [join(here, 'Pack.java'), file], { encoding: 'utf8' });
  if (java.error || java.status !== 0) { t.skip('no Java here: the packing of the Android app is not run'); return; }
  const { page, context } = await openPage();
  await page.goto(`${base}/index.html#publish=code;split-the-bill;${java.stdout.trim()}`);
  await page.waitForSelector('#sheet:not([hidden])');
  assert.equal(await page.inputValue('#app-name'), 'split-the-bill');
  await page.click('#sheet-try');
  const frame = page.frameLocator('#viewer iframe');
  await frame.locator('#out').waitFor();
  assert.match(await frame.locator('#out').textContent(), /Each pays 23\.10/);
  await frame.locator('#people').fill('3');
  assert.match(await frame.locator('#out').textContent(), /Each pays 30\.80/);
  await context.close();
});

test('a hand-off that is damaged or too big is not opened, and says so', async () => {
  const { page, context } = await openPage();
  await page.waitForSelector('#store-list .app');
  await page.evaluate(() => { location.hash = '#publish=code;Taps;AAAA'; });
  await page.waitForFunction(() => /not opened/.test(document.getElementById('store-status').textContent));
  assert.equal(await page.locator('#sheet').isHidden(), true);
  await handoff(page, 'aiwa', 'Empty', ' ');
  await page.waitForFunction(() => /not opened: index\.html is empty/.test(document.getElementById('store-status').textContent));
  await context.close();
});

test('an app that uses the Aiwa SDK runs in the sandbox: it imports the module by its address and counts one vote per identity', async () => {
  const example = readFileSync(join(root, 'docs/aiwa-app-example.html'), 'utf8');
  assert.equal(SDK_URL, `${JSON.parse(readFileSync(join(root, 'deployment.json'), 'utf8')).siteUrl}lib/aiwa.js`, "the deployed site's address of the SDK");
  assert.ok(example.includes(`from '${SDK_URL}'`), 'the example imports the SDK by the deployed site\'s address');
  // Here the same module is the build under test, on the test's own server: the address is the only thing changed. (Answering the
  // deployed address with the browser's request interception was unreliable: a frame of the sandbox sometimes asked the network first.)
  const code = example.replace(`from '${SDK_URL}'`, `from '${base}/lib/aiwa.js'`);
  const { page, errors, context } = await openPage();
  await handoff(page, 'aiwa', 'show-of-hands', code);
  await page.waitForSelector('#sheet:not([hidden])');
  assert.equal(await page.inputValue('#app-description'), 'A vote that anyone can replay from its signed events: one vote per identity, proved by a signature inside the action.');
  await page.click('#sheet-try');
  const frame = page.frameLocator('#viewer iframe');
  await frame.locator('#tally').filter({ hasText: 'Yes 0 · No 0' }).waitFor({ timeout: 15000 });
  await frame.locator('#yes').click();
  await frame.locator('#tally').filter({ hasText: 'Yes 1 · No 0' }).waitFor();
  await frame.locator('#no').click();
  await page.waitForTimeout(300);
  assert.equal(await frame.locator('#tally').textContent(), 'Yes 1 · No 0', 'the same identity cannot vote again');
  await frame.locator('#another').click();
  await frame.locator('#no').click();
  await frame.locator('#tally').filter({ hasText: 'Yes 1 · No 1' }).waitFor();
  assert.deepEqual(errors, []);
  await context.close();
});

// ---------- an app that uses the wallet ----------

/** A wallet that mined and claimed in the page: it has something to spend. */
async function fundInPage(page, { epoch = 4 } = {}) {
  await mineInPage(page, { epoch, timeout: 120000 });
  await page.click('#btn-claim');
  await page.waitForFunction(() => Number(document.getElementById('out-spendable').textContent) > 0, null, { timeout: 30000 });
}

/** Opens an app in the viewer the way the publish sheet's "Try it" does, and returns its frame. */
async function tryApp(page, name, html) {
  await handoff(page, 'code', name, html);
  await page.waitForSelector('#sheet:not([hidden])');
  await page.click('#sheet-try');
  await page.locator('#viewer iframe').waitFor();
  return page.frameLocator('#viewer iframe');
}

async function until(read, { ms = 15000, every = 100, what = 'something' } = {}) {
  const end = Date.now() + ms;
  for (;;) {
    try { const value = await read(); if (value) return value; } catch { /* not there yet */ }
    if (Date.now() > end) throw new Error(`timed out waiting for ${what}`);
    await new Promise((r) => setTimeout(r, every));
  }
}

const doorProbe = (declared, script) => `<!doctype html><html><head><meta charset="utf-8">${declared ? '<meta name="aiwa-wallet" content="pay">' : ''}<title>Probe</title></head><body><p id="r">…</p><script>
  let n = 0; const wait = new Map();
  addEventListener('message', (e) => { const m = e.data; if (m && m.aiwa === 1 && wait.has(m.id)) { wait.get(m.id)(m); wait.delete(m.id); } });
  const door = (cmd, args = {}) => new Promise((resolve) => { const id = ++n; wait.set(id, resolve); parent.postMessage({ aiwa: 1, id, cmd, args }, '*'); });
  const out = (v) => { document.getElementById('r').textContent = typeof v === 'string' ? v : JSON.stringify(v); };
  setTimeout(() => { if (document.getElementById('r').textContent === '…') out('no answer'); }, 2500);
  (async () => { ${script} })();
</script></body></html>`;

test('an app that says it uses the wallet gets a banner and a door; one that does not gets neither', async () => {
  const { page, errors, context } = await openPage(async (p) => { await injectSolana(p); await injectHost(p); });
  await fundInPage(page);
  const identity = await page.locator('#out-identity-id').getAttribute('data-full');
  const before = Number(await page.locator('#out-spendable').textContent());
  const stranger = 'ab'.repeat(32);

  // it does not say so: no banner, and the door does not answer
  let frame = await tryApp(page, 'Quiet', doorProbe(false, "out(await door('whoami'));"));
  assert.equal(await page.locator('#viewer-flag').isHidden(), true);
  assert.equal(await until(() => frame.locator('#r').textContent().then((t) => t !== '…' && t), { what: 'the quiet app to give up' }), 'no answer');
  await page.click('#viewer-close');
  await page.click('#sheet-close').catch(() => {});

  // it says so: the banner, and the door answers: who the player is, a payment, a refusal
  frame = await tryApp(page, 'Loud', doorProbe(true, `
    const who = await door('whoami');
    const paid = await door('pay', { to: '${stranger}', amount: '0.0001' });
    const again = await door('pay', { to: '${stranger}', amount: '0.0001' });
    const bad = await door('pay', { to: 'nobody', amount: '1' });
    const unknown = await door('format-the-phone');
    out({ who, paid: !!(paid.result && paid.result.blob), paidError: paid.error, again: !!(again.result && again.result.blob), againError: again.error, bad: bad.error, unknown: unknown.error });`));
  assert.equal(await page.locator('#viewer-flag').isVisible(), true);
  assert.match(await page.locator('#viewer-flag').textContent(), /uses your wallet/);
  const seen = JSON.parse(await until(() => frame.locator('#r').textContent().then((t) => t.startsWith('{') && t), { what: 'the door to answer' }));
  assert.equal(seen.who.result.id, identity);
  assert.equal(seen.paid, true, `a payment came back as a code (${seen.paidError}; spendable ${before})`);
  assert.equal(seen.again, true, `and a second one, through the same channel (${seen.againError})`);
  assert.match(seen.bad, /identity id/);
  assert.match(seen.unknown, /unknown command/);
  await page.click('#viewer-close');
  assert.equal(await page.locator('#viewer-flag').isHidden(), true);
  await until(async () => Math.abs(Number(await page.locator('#out-spendable').textContent()) - (before - 0.0002)) < 1e-9, { what: `the wallet to show what the app paid (${before} - 0.0002)` });
  assert.deepEqual(errors, []);
  await context.close();
});

test('two phones link by two codes, exchange what each holds, and each shows where the other stands', { timeout: 300000 }, async () => {
  const chain = new Map();                                // one Solana for both phones
  // no camera here (the fake one films a code that is not ours): the codes are pasted, which is what a phone without a camera does
  const noCamera = (p) => p.addInitScript(() => { navigator.mediaDevices.getUserMedia = () => Promise.reject(new DOMException('no camera', 'NotAllowedError')); });
  const A = await openPage(async (p) => { await injectSolana(p, chain); await injectHost(p); await noCamera(p); });
  const B = await openPage(async (p) => { await injectSolana(p, chain); await injectHost(p); await noCamera(p); });
  await mineInPage(A.page, { epoch: 3, timeout: 120000 });
  await mineInPage(B.page, { epoch: 3, timeout: 120000 });
  const idOf = (page) => page.locator('#out-identity-id').getAttribute('data-full');
  const [idA, idB] = [await idOf(A.page), await idOf(B.page)];
  const shortOf = (id) => `${id.slice(0, 8)}…${id.slice(-6)}`;
  assert.match(await A.page.locator('#standing-list').textContent(), /Nobody yet/);

  // A starts: a code on A's screen; B reads it (here: pastes it) and shows an answer; A reads that
  await A.page.click('#btn-link-start');
  await A.page.waitForFunction(() => !document.getElementById('door-show').hidden && document.getElementById('door-text').value.startsWith('link1.'));
  const offer = await A.page.inputValue('#door-text');
  await B.page.click('#btn-link-join');
  await B.page.waitForSelector('#door-scan:not([hidden])').catch(async (error) => { throw new Error(`B's link line says: ${await B.page.locator('#link-status').textContent()} | page errors: ${JSON.stringify([A.errors, B.errors])}`, { cause: error }); });
  await B.page.fill('#door-paste', offer);
  await B.page.click('#door-use');
  await B.page.waitForFunction(() => !document.getElementById('door-show').hidden && document.getElementById('door-text').value.startsWith('link1.'));
  const answer = await B.page.inputValue('#door-text');
  await A.page.click('#door-next');
  await A.page.waitForSelector('#door-scan:not([hidden])');
  await A.page.fill('#door-paste', answer);
  await A.page.click('#door-use');
  await B.page.click('#door-next');

  // linked, and each phone holds the other's events and has signed for them
  for (const [name, P] of [['A', A], ['B', B]]) {
    await P.page.waitForFunction(() => /Linked with 1 phone · \d+ events received/.test(document.getElementById('link-status').textContent), null, { timeout: 60000 })
      .catch(async (error) => { throw new Error(`${name} never linked; its link line says: ${await P.page.locator('#link-status').textContent()} | page errors: ${JSON.stringify([A.errors, B.errors])}`, { cause: error }); });
    await P.page.locator('#standing-section summary').click();
  }
  const row = async (P, id) => until(async () => {
    const text = await P.page.locator('#standing-list .idrow').first().innerText();
    return text.includes(shortOf(id)) && /epoch \d+/.test(text) && text;
  }, { ms: 60000, what: 'the other phone to show in the list' });
  const seenByA = await row(A, idB);
  const seenByB = await row(B, idA);
  assert.match(seenByA, /proven ≥ [1-9]/, 'what A proves of B comes from B\'s own signed events');
  assert.match(seenByB, /proven ≥ [1-9]/);
  assert.doesNotMatch(seenByA + seenByB, /signed two different histories/, 'two honest phones');
  assert.deepEqual([A.errors, B.errors], [[], []]);
});

test('click duel: two phones link by two codes, click for 20 seconds, and the one who clicked less pays what they clicked', { timeout: 300000 }, async () => {
  const duel = readFileSync(join(root, 'docs/demo-apps/click-duel.html'), 'utf8');
  const chain = new Map();                                // one Solana for both phones
  const A = await openPage(async (p) => { await injectSolana(p, chain); await injectHost(p); });
  const B = await openPage(async (p) => { await injectSolana(p, chain); await injectHost(p); });
  await fundInPage(A.page);
  await fundInPage(B.page);
  const spendable = async (page) => Number(await page.locator('#out-spendable').textContent());
  const [a0, b0] = [await spendable(A.page), await spendable(B.page)];
  const RATE = 0.00002;

  const a = await tryApp(A.page, 'Duel', duel);
  const b = await tryApp(B.page, 'Duel', duel);
  assert.equal(await A.page.locator('#viewer-flag').isVisible(), true, 'the Store says the duel uses the wallet');

  // A challenges: a code on A's screen; B scans it (here: pastes it) and shows an answer; A scans that
  await a.locator('#rate').fill(String(RATE));
  await a.locator('#challenge').click();
  await A.page.waitForSelector('#door-show:not([hidden])');
  const offer = await A.page.inputValue('#door-text');
  assert.match(offer, /^duel1\./);
  await b.locator('#join').click();
  await B.page.waitForSelector('#door-scan:not([hidden])');
  await B.page.fill('#door-paste', offer);
  await B.page.click('#door-use');
  await B.page.waitForFunction(() => !document.getElementById('door-show').hidden && document.getElementById('door-text').value.startsWith('duel1.'));
  const answer = await B.page.inputValue('#door-text');
  await A.page.click('#door-next');
  await A.page.waitForSelector('#door-scan:not([hidden])');
  await A.page.fill('#door-paste', answer);
  await A.page.click('#door-use');

  // linked: B is told the price of a click and accepts
  await b.locator('#accept').waitFor({ state: 'visible', timeout: 40000 });
  assert.match(await b.locator('#status').textContent(), /0\.00002 AIWA/);
  await b.locator('#accept').click();

  // 20 seconds: A clicks 12 times, B clicks 5 times
  await until(async () => (await a.locator('#disc').isEnabled()) && (await b.locator('#disc').isEnabled()), { ms: 15000, what: 'the countdown to end' });
  for (let i = 0; i < 12; i++) { await a.locator('#disc').click(); if (i < 5) await b.locator('#disc').click(); }
  assert.equal(await a.locator('#mine').textContent(), '12');
  await until(async () => (await b.locator('#theirs').textContent()) === '12', { ms: 5000, what: 'B to see A\'s clicks' });   // B sees A's clicks live

  const shows = async (frame) => (await frame.locator('body').innerText()).replace(/\s+/g, ' ');
  for (const [name, frame] of [['A', a], ['B', b]]) {
    // The winner's phone checks everything the loser's payment carries, the history of a wallet that has been mining every 150 ms: some 30 s of work here.
    await frame.locator('#end').waitFor({ state: 'visible', timeout: 120000 }).catch(async (error) => {
      throw new Error(`${name} never reached the end of the duel. A shows: ${await shows(a)} | B shows: ${await shows(b)} | page errors: ${JSON.stringify([A.errors, B.errors])}`, { cause: error });
    });
  }
  assert.match(await a.locator('#result').textContent(), /You won 0\.0001 AIWA/);
  assert.match(await b.locator('#result').textContent(), /You lost 0\.0001 AIWA/);

  // and the wallets agree
  const cost = 5 * RATE;
  assert.ok(Math.abs((await until(async () => { const v = await spendable(A.page); return v > a0 && v; }, { what: 'A\'s wallet to show the payment' })) - (a0 + cost)) < 1e-9, 'the winner received what the loser clicked');
  assert.ok(Math.abs((await spendable(B.page)) - (b0 - cost)) < 1e-9, 'and the loser paid it');
  await A.context.close();
  await B.context.close();
});

test('scanning: the page opens the camera, reads the code it shows, and hands the text to the app', async () => {
  const { page, errors, context } = await openPage(async (p) => { await injectSolana(p); await injectHost(p); });
  await mineInPage(page, { epoch: 1 });                  // the sheet that opens an app needs a wallet, not money
  const frame = await tryApp(page, 'Scanner', doorProbe(true, "const read = await door('scanCode', { title: 'Scan it' }); out(read.error ? 'error: ' + read.error : read.result);"));
  await page.waitForSelector('#door-scan:not([hidden])');
  const text = await until(() => frame.locator('#r').textContent().then((t) => t !== '…' && t), { ms: 20000, what: 'the camera to be read' });
  assert.equal(text, globalThis.cameraText);
  assert.equal(await page.locator('#door-sheet').isHidden(), true, 'the sheet closes by itself once a code is read');
  assert.deepEqual(errors, []);
  await context.close();
});
