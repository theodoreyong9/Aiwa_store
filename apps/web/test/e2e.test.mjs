// The web app in a real browser (Chromium), against a registry that a real run of aiwa-registry produced, and a Solana
// stand-in. What it checks: the store lists apps ranked by score / laps; an app opens in a sandbox that cannot reach the
// page; a package the host tampered with is not opened; the last list and opened apps work offline; the wallet shows
// what a burn does with the creator fee before signing, burns, and mines; and what the browser's wallet submits is what
// the registry accepts.
import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { mkdtempSync, mkdirSync, writeFileSync, readFileSync, existsSync, statSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, extname, resolve, dirname } from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright';
import { buildAppPackage, validateSubmission, applyAccepted, emptyStore, writeStore, rankApps } from 'aiwa-registry';
import { CREATOR, deployment as testDeployment, fakeSolana, minedWallet } from '../../../registry/support/helpers.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const root = resolve(here, '../../..');
const tmp = mkdtempSync(join(tmpdir(), 'aiwa-web-'));
const site = join(tmp, 'site');
const MIME = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.png': 'image/png', '.webmanifest': 'application/manifest+json' };

let server, base, browser;
const control = { down: false, tamper: null };     // what a flaky or dishonest host does

const html = (title, script = '') => `<!doctype html><html><head><meta charset="utf-8"><title>${title}</title></head><body><h1>${title}</h1><p id="r">…</p><script>${script}</script></body></html>`;
const ESCAPE_PROBE = `try { window.parent.document.title; document.getElementById('r').textContent = 'ESCAPED'; } catch (e) { document.getElementById('r').textContent = 'isolated'; }`;

before(async () => {
  // 1. the deployment the test build uses: the repository's, with the stand-in creator address and a fast tick
  const deployment = JSON.parse(readFileSync(join(root, 'deployment.json'), 'utf8'));
  deployment.registryUrl = './store';
  deployment.rpc = 'http://127.0.0.1:1';
  deployment.progress = { intervalMs: 150 };
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
  for (const [wallet, fields] of [
    [alice, { id: 'alpha', name: 'Alpha', description: 'tries to reach the wallet', html: html('Alpha', ESCAPE_PROBE) }],
    [bob, { id: 'beta', name: 'Beta', description: 'says hello', html: html('Beta') }],
  ]) {
    const submission = {
      format: 'aiwa-submission/1', kind: 'publish',
      package: await buildAppPackage(wallet.identity, { version: '1.0.0', ...fields }),
      evidence: await wallet.submissionEvidence(),
    };
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
    res.writeHead(200, { 'content-type': MIME[extname(file)] ?? 'application/octet-stream', 'cache-control': 'no-store' }).end(body);
  }).listen(0, '127.0.0.1');
  await new Promise((r) => server.once('listening', r));
  base = `http://127.0.0.1:${server.address().port}`;

  browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || undefined });
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

// A Solana that decodes the transaction the wallet built, as aiwa-lib's own tests do; it lives in the page.
const injectSolana = (page) => page.addInitScript(() => {
  const transactions = {};
  let count = 0;
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
        const signature = `browsersig${++count}`;
        transactions[signature] = { slot: 10, transaction: { message: { accountKeys: keys } }, meta: { err: null, fee: 5000, preBalances: pre, postBalances: post } };
        return signature;
      },
      confirmTransaction: async () => ({}),
      getTransaction: async (signature) => transactions[signature] ?? null,
      getBalance: async () => 50e9,
    },
  };
});

test('the store lists the apps ranked by score / laps, and search narrows the list', async () => {
  const { page, errors, context } = await openPage();
  await page.waitForSelector('#store-list .app');
  const names = await page.locator('#store-list .app .title').evaluateAll((els) => els.map((e) => e.firstChild.textContent));
  assert.deepEqual(names, globalThis.expectedOrder);
  const ranks = await page.locator('#store-list .app .rank').allTextContents();
  assert.deepEqual(ranks, ['1', '2']);
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
    assert.equal(await page.locator('#store-list .app').count(), 2, 'the last list seen');
    await page.locator('#store-list .app[data-id="beta"] button').click();
    await page.locator('#viewer iframe').waitFor();
    assert.equal(await page.frameLocator('#viewer iframe').locator('h1').textContent(), 'Beta');
    await page.click('#viewer-close');
    await page.locator('#store-list .app[data-id="alpha"] button').click();         // never opened: needs the host
    await page.waitForFunction(() => /does not serve this app/.test(document.getElementById('store-status').textContent));
  } finally { control.down = false; }
  await context.close();
});

test('the wallet says what a burn does with the creator fee before signing, burns, and mines; what it submits, the registry accepts', async () => {
  const { page, errors, context } = await openPage(injectSolana);
  await page.click('#app-nav [data-view="wallet"]');
  await page.click('#btn-connect');
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
  await page.waitForFunction(() => /epoch [2-9]/.test(document.getElementById('out-mining').textContent), null, { timeout: 15000 });

  // Publish: sign, with the mining evidence, and give the file to the registry
  await page.click('#app-nav [data-view="publish"]');
  await page.fill('#app-name', 'Taps');
  await page.fill('#app-description', 'counts taps');
  await page.click('#btn-prepare');
  await page.waitForSelector('#publish-next:not([hidden])');
  const download = page.waitForEvent('download');
  await page.click('#btn-download');
  const file = join(tmp, 'from-browser.json');
  await (await download).saveAs(file);
  assert.match(await page.locator('#link-pr').getAttribute('href'), /github\.com\/theodoreyong9\/Aiwa_store\/new\/main\?filename=submissions%2Ftaps-1\.0\.0\.json/);

  const browserTransactions = await page.evaluate(() => window.__aiwaTest.connection.transactions);
  const connection = { getTransaction: async (signature) => browserTransactions[signature] ?? null };
  const submission = JSON.parse(readFileSync(file, 'utf8'));
  const result = await validateSubmission({ submission, store: emptyStore(), deployment: testDeployment, connection, now: Date.now() });
  assert.equal(result.ok, true, result.reason);
  assert.equal(result.accepted.entry.id, 'taps');
  assert.equal(result.accepted.entry.score > 0, true);
  assert.deepEqual(errors, []);
  await context.close();
});
