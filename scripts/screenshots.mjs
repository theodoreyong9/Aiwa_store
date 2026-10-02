// The screenshots of the README (docs/img/*.png): the real web app, in a real browser at phone size, with demo apps in a
// registry that the real registry code produced and a stand-in for Solana (the same one the tests use). Nothing here is a
// mock-up of the screens: only the data is a demo.
// Run: node scripts/screenshots.mjs   (needs Chromium: npx -w aiwa-store-web playwright install chromium)

import { createServer } from 'node:http';
import { mkdtempSync, mkdirSync, writeFileSync, readFileSync, existsSync, statSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, extname, resolve, dirname } from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';
import { deflateRawSync } from 'node:zlib';
import { buildAppPackage, validateSubmission, applyAccepted, emptyStore, writeStore } from 'aiwa-registry';
import { CREATOR, deployment as testDeployment, fakeSolana, minedWallet } from '../registry/support/helpers.mjs';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const { chromium } = createRequire(join(root, 'apps/web/package.json'))('playwright');
const out = join(root, 'docs/img');
const tmp = mkdtempSync(join(tmpdir(), 'aiwa-shots-'));
const site = join(tmp, 'site');
const MIME = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.png': 'image/png', '.webmanifest': 'application/manifest+json' };
const demo = (name) => readFileSync(join(root, 'docs/demo-apps', name), 'utf8');

// 1. the site, built with a fast tick; 2. a registry made as the real one is made
const deployment = JSON.parse(readFileSync(join(root, 'deployment.json'), 'utf8'));
Object.assign(deployment, { registryUrl: './store', rpc: 'http://127.0.0.1:1', progress: { intervalMs: 150 }, github: { clientId: 'demo' } });
deployment.rewardParams = { ...deployment.rewardParams, epochIterations: 150 };
writeFileSync(join(tmp, 'deployment.json'), JSON.stringify(deployment));
execFileSync('node', [join(root, 'apps/web/build.mjs'), '--out', site, '--deployment', join(tmp, 'deployment.json')], { stdio: 'pipe' });

const connection = fakeSolana();
let store = emptyStore();
let at = Date.now();
for (const [epochs, id, name, file] of [
  [7, 'tip-split', 'Tip split', 'tip-split.html'],
  [4, 'unit-converter', 'Unit converter', 'converter.html'],
  [2, 'focus', 'Focus', 'focus-timer.html'],
]) {
  const wallet = await minedWallet(connection, { epochs });
  const html = demo(file);
  const description = html.match(/<meta name="description" content="([^"]*)"/)[1];
  const pkg = await buildAppPackage(wallet.identity, { id, name, version: '1.0.0', description, html });
  const submission = { format: 'aiwa-submission/1', kind: 'publish', package: pkg, evidence: await wallet.submissionEvidence() };
  const result = await validateSubmission({ submission, store, deployment: testDeployment, connection, now: at += 1000 });
  if (!result.ok) throw new Error(result.reason);
  writeStore(join(site, 'store'), (store = applyAccepted(store, result.accepted)), result.accepted);
}

const server = createServer((req, res) => {
  let path = decodeURIComponent(new URL(req.url, 'http://x').pathname);
  if (path.endsWith('/')) path += 'index.html';
  const file = join(site, path);
  if (!file.startsWith(site) || !existsSync(file) || !statSync(file).isFile()) { res.writeHead(404).end('not found'); return; }
  res.writeHead(200, { 'content-type': MIME[extname(file)] ?? 'application/octet-stream', 'cache-control': 'no-store' }).end(readFileSync(file));
}).listen(0, '127.0.0.1');
await new Promise((r) => server.once('listening', r));
const base = `http://127.0.0.1:${server.address().port}`;

const browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || undefined });
const context = await browser.newContext({ viewport: { width: 390, height: 780 }, deviceScaleFactor: 2 });
const page = await context.newPage();
await page.addInitScript(() => {          // a Solana that decodes the transaction the wallet builds, and the Android host's keystore
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
        const transfers = tx.instructions.map((ix) => { const d = w3.SystemInstruction.decodeTransfer(ix); return { from: d.fromPubkey.toBase58(), to: d.toPubkey.toBase58(), lamports: Number(d.lamports) }; });
        for (const t of transfers) for (const k of [t.from, t.to]) if (!keys.includes(k)) keys.push(k);
        const pre = keys.map((_, i) => (i === 0 ? 50e9 : 0));
        const post = [...pre];
        for (const t of transfers) { post[keys.indexOf(t.from)] -= t.lamports; post[keys.indexOf(t.to)] += t.lamports; }
        post[0] -= 5000;
        const signature = `demosig${++count}`;
        transactions[signature] = { slot: 10, transaction: { message: { accountKeys: keys } }, meta: { err: null, fee: 5000, preBalances: pre, postBalances: post } };
        return signature;
      },
      confirmTransaction: async () => ({}),
      getTransaction: async (signature) => transactions[signature] ?? null,
      getBalance: async () => 50e9,
    },
  };
  const key = '__fake_keystore';
  const read = () => JSON.parse(localStorage.getItem(key) ?? '{}');
  const reply = (id, body) => setTimeout(() => window.AiwaHost.onmessage?.({ data: JSON.stringify({ id, ...body }) }), 0);
  window.AiwaHost = { onmessage: null, postMessage(raw) {
    const m = JSON.parse(raw);
    if (m.cmd === 'secret-get') reply(m.id, { result: { value: read()[m.key] ?? null } });
    else if (m.cmd === 'secret-set') { localStorage.setItem(key, JSON.stringify({ ...read(), [m.key]: m.value })); reply(m.id, { result: {} }); }
    else if (m.cmd === 'secret-delete') { const all = read(); delete all[m.key]; localStorage.setItem(key, JSON.stringify(all)); reply(m.id, { result: {} }); }
    else if (m.cmd === 'github-login') { reply(m.id, { progress: { userCode: 'WXYZ-1234', verificationUri: 'https://github.com/login/device' } }); setTimeout(() => reply(m.id, { result: { token: 'gho_demo' } }), 60000); }
  } };
});
mkdirSync(out, { recursive: true });
// each screen is cut where its content ends
const shot = async (name, height) => { await page.setViewportSize({ width: 390, height }); await page.waitForTimeout(150); await page.screenshot({ path: join(out, name) }); };

// 1. the store
await page.goto(`${base}/index.html`);
await page.waitForSelector('#store-list .app');
await shot('store.png', 640);

// 2. an app, open, in its sandbox
await page.locator('#store-list .app[data-id="tip-split"] button').click();
await page.locator('#viewer iframe').waitFor();
await page.frameLocator('#viewer iframe').locator('#each').waitFor();
await page.waitForTimeout(400);
await shot('app.png', 560);
await page.click('#viewer-close');

// 3. the wallet, with what a burn does shown before anything is signed
await page.click('#app-nav [data-view="wallet"]');
await page.click('#btn-create');
await page.waitForSelector('#wallet-section:not([hidden])');
await page.click('#btn-phrase-done');
await page.fill('#burn-amount', '1');
await page.fill('#burn-t', '20');
await page.locator('#burn-preview').scrollIntoViewIfNeeded();
await shot('wallet.png', 500);

// 4. the publish sheet, opened by the widget's ▦ with the app it wrote
await page.setViewportSize({ width: 390, height: 780 });
await page.click('#btn-burn');
await page.waitForFunction(() => /Burned and committed/.test(document.getElementById('burn-result').textContent));
await page.waitForFunction(() => /epoch [3-9]/.test(document.getElementById('out-mining').textContent), null, { timeout: 120000 })
  .catch(async (e) => { console.log('mining:', await page.locator('#out-mining').textContent(), '| burn:', await page.locator('#burn-result').textContent()); throw e; });
const packed = deflateRawSync(Buffer.from(demo('coin-flip.html'), 'utf8')).toString('base64url');
await page.evaluate((hash) => { location.hash = hash; }, `#publish=code;Coin-flip;${packed}`);
await page.waitForSelector('#sheet:not([hidden])');
await page.fill('#app-name', 'Coin flip');
await page.waitForFunction(() => !document.getElementById('sheet-go').disabled);
await page.waitForTimeout(300);
await shot('publish.png', 780);

await browser.close();
server.close();
console.log('docs/img: store.png app.png wallet.png publish.png');
