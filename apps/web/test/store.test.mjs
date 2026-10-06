import { test } from 'node:test';
import assert from 'node:assert/strict';
import { generateIdentity } from 'aiwa-core';
import { buildAppPackage, buildBundle } from 'aiwa-registry';
import { loadIndex, loadApp, filterApps, rankApps } from '../src/store.js';

const html = (t) => `<!doctype html><title>${t}</title><h1>${t}</h1>`;
const entryOf = (pkg, extra = {}) => ({
  id: pkg.id, name: pkg.name, version: pkg.version, description: pkg.description, kind: pkg.kind, author: pkg.author, bundleHash: pkg.bundleHash,
  path: `apps/${pkg.id}/${pkg.version}.json`, ...(pkg.kind === 'aiwa' ? { manifestId: pkg.manifestId, bundlePath: `apps/${pkg.id}/${pkg.version}.bundle.json` } : {}),
  score: 1, laps: 1, publishedAt: 1, updatedAt: 1, ...extra,
});
const json = (value, ok = true, status = 200) => ({ ok, status, json: async () => value });
const memoryCache = () => { const m = new Map(); return { get: async (k) => m.get(k), set: async (k, v) => { m.set(k, v); }, m }; };

test('the index is read, malformed entries dropped, and apps ranked by score / laps', async () => {
  const author = await generateIdentity();
  const a = await buildAppPackage(author, { id: 'a', name: 'A', version: '1.0.0', html: html('a') });
  const b = await buildAppPackage(author, { id: 'b', name: 'B', version: '1.0.0', html: html('b') });
  const index = { format: 'aiwa-store-index/1', apps: [entryOf(a, { score: 2, laps: 4 }), { id: 'broken' }, entryOf(b, { score: 9, laps: 3 })] };
  const { apps, source } = await loadIndex({ baseUrl: 'x', fetchFn: async () => json(index) });
  assert.equal(source, 'network');
  assert.deepEqual(apps.map((e) => e.id), ['b', 'a']);
});

test('offline, the last index seen is used and the failure is reported; with nothing seen, the failure is thrown', async () => {
  const author = await generateIdentity();
  const a = await buildAppPackage(author, { id: 'a', name: 'A', version: '1.0.0', html: html('a') });
  const cache = memoryCache();
  await loadIndex({ baseUrl: 'x', cache, fetchFn: async () => json({ format: 'aiwa-store-index/1', apps: [entryOf(a)] }) });
  const down = async () => { throw new Error('network down'); };
  const offline = await loadIndex({ baseUrl: 'x', cache, fetchFn: down });
  assert.equal(offline.source, 'cache');
  assert.equal(offline.error, 'network down');
  assert.equal(offline.apps.length, 1);
  await assert.rejects(loadIndex({ baseUrl: 'x', cache: memoryCache(), fetchFn: down }), /network down/);
  await assert.rejects(loadIndex({ baseUrl: 'x', fetchFn: async () => json({ format: 'other' }) }), /not in a format/);
});

test('an app is opened only if it is what its author signed and what the registry lists', async () => {
  const author = await generateIdentity();
  const pkg = await buildAppPackage(author, { id: 'hello', name: 'Hello', version: '1.0.0', html: html('hello') });
  const entry = entryOf(pkg);
  const serve = (p) => async () => json(p);

  const ok = await loadApp({ entry, baseUrl: 'x', fetchFn: serve(pkg) });
  assert.equal(ok.html, pkg.html);
  assert.deepEqual(ok.files, { 'index.html': pkg.html }, 'a plain app is its one file, for reading');
  assert.equal(ok.source, 'network');

  await assert.rejects(loadApp({ entry, baseUrl: 'x', fetchFn: serve({ ...pkg, html: html('evil') }) }), /hash does not match/);
  const other = await buildAppPackage(await generateIdentity(), { id: 'hello', name: 'Hello', version: '1.0.0', html: html('hello') });
  await assert.rejects(loadApp({ entry, baseUrl: 'x', fetchFn: serve(other) }), /not the one the registry lists/, 'a valid package of someone else under the same id');
  await assert.rejects(loadApp({ entry, baseUrl: 'x', fetchFn: async () => json({}, false, 404) }), /does not serve this app \(404\)/);
});

test('a package already opened is kept by its hash, checked again, and opens with no network', async () => {
  const author = await generateIdentity();
  const pkg = await buildAppPackage(author, { id: 'hello', name: 'Hello', version: '1.0.0', html: html('hello') });
  const entry = entryOf(pkg);
  const cache = memoryCache();
  await loadApp({ entry, baseUrl: 'x', cache, fetchFn: async () => json(pkg) });
  const again = await loadApp({ entry, baseUrl: 'x', cache, fetchFn: async () => { throw new Error('no network'); } });
  assert.equal(again.source, 'cache');

  cache.m.set(`pkg:${entry.bundleHash}`, { pkg: { ...pkg, html: html('tampered in the cache') }, bundle: null });
  await assert.rejects(loadApp({ entry, baseUrl: 'x', cache, fetchFn: async () => { throw new Error('no network'); } }), /hash does not match/);
});

test('search needs every word, in the id, name, description or author', () => {
  const apps = [
    { id: 'tip-jar', name: 'Tip jar', description: 'send a coffee', author: 'AAA111' },
    { id: 'notes', name: 'Notes', description: 'plain notes', author: 'BBB222' },
  ];
  assert.deepEqual(filterApps(apps, 'coffee').map((a) => a.id), ['tip-jar']);
  assert.deepEqual(filterApps(apps, 'NOTES plain').map((a) => a.id), ['notes']);
  assert.deepEqual(filterApps(apps, 'bbb222 coffee'), []);
  assert.equal(filterApps(apps, '  ').length, 2);
  assert.deepEqual(rankApps([]), []);
});

// --- the second kind: code published through Aiwa -------------------------------------------------------------------

const FILES = [
  { path: 'index.html', content: '<!doctype html><html><head><link rel="stylesheet" href="style.css"></head><body><h1>Tally</h1><script src="./app.js"></script></body></html>' },
  { path: 'style.css', content: 'h1 { color: red; }' },
  { path: 'app.js', content: 'document.title = "</script>";' },
];
async function aiwaApp() {
  const author = await generateIdentity();
  const { manifestId, bundle } = await buildBundle(author, { name: 'Tally', version: '1.0.0', files: FILES });
  const pkg = await buildAppPackage(author, { id: 'tally', name: 'Tally', version: '1.0.0', manifestId });
  return { author, pkg, bundle, entry: entryOf(pkg) };
}
const serveBoth = (pkg, bundle) => async (url) => json(url.endsWith('.bundle.json') ? bundle : pkg);

test('an app of kind aiwa is opened from its pointer: the bundle is verified by Aiwa against the signed manifest, then assembled', async () => {
  const { pkg, bundle, entry } = await aiwaApp();
  const { html, files, source } = await loadApp({ entry, baseUrl: 'x', fetchFn: serveBoth(pkg, bundle) });
  assert.equal(source, 'network');
  assert.deepEqual(Object.keys(files).sort(), FILES.map((f) => f.path).sort(), 'every file of the contract is there to be read, as signed');
  assert.equal(files['app.js'], FILES.find((f) => f.path === 'app.js').content);
  assert.match(html, /<style>h1 \{ color: red; \}<\/style>/, 'the stylesheet is inside');
  assert.match(html, /<script>document\.title = "<\\\/script>";<\/script>/, 'the script is inside, its closing tag defused');
  assert.ok(!html.includes('href="style.css"') && !html.includes('src="./app.js"'));
});

test('an app of kind aiwa is refused when the host changed a file, swapped the bundle, or moved the pointer', async () => {
  const { pkg, bundle, entry, author } = await aiwaApp();
  const evil = structuredClone(bundle);
  evil.events.find((e) => e.payload.path === 'app.js').payload.content = 'steal()';
  await assert.rejects(loadApp({ entry, baseUrl: 'x', fetchFn: serveBoth(pkg, evil) }), /Aiwa refuses these events/);

  const other = await buildBundle(author, { name: 'Tally', version: '1.0.0', files: [...FILES.slice(0, 2), { path: 'app.js', content: 'other()' }] });
  await assert.rejects(loadApp({ entry, baseUrl: 'x', fetchFn: serveBoth(pkg, other.bundle) }), /pinned manifest is not in the bundle/);

  await assert.rejects(loadApp({ entry: { ...entry, manifestId: 'a'.repeat(64) }, baseUrl: 'x', fetchFn: serveBoth(pkg, bundle) }), /another manifest than the registry lists/);
  await assert.rejects(loadApp({ entry, baseUrl: 'x', fetchFn: async (url) => (url.endsWith('.bundle.json') ? json({}, false, 404) : json(pkg)) }), /does not serve this app \(404\)/);
});

test('the index lists apps of both kinds, and drops an aiwa entry that has no pointer', async () => {
  const { entry } = await aiwaApp();
  const author = await generateIdentity();
  const code = await buildAppPackage(author, { id: 'a', name: 'A', version: '1.0.0', html: html('a') });
  const index = { format: 'aiwa-store-index/1', apps: [entry, entryOf(code), { ...entry, id: 'x', manifestId: undefined }, { ...entryOf(code), kind: undefined, id: 'y' }] };
  const { apps } = await loadIndex({ baseUrl: 'x', fetchFn: async () => json(index) });
  assert.deepEqual(apps.map((e) => e.id).sort(), ['a', 'tally']);
});
