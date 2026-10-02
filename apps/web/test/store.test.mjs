import { test } from 'node:test';
import assert from 'node:assert/strict';
import { generateIdentity } from 'aiwa-core';
import { buildAppPackage } from 'aiwa-registry';
import { loadIndex, loadApp, filterApps, rankApps } from '../src/store.js';

const html = (t) => `<!doctype html><title>${t}</title><h1>${t}</h1>`;
const entryOf = (pkg, extra = {}) => ({ id: pkg.id, name: pkg.name, version: pkg.version, description: pkg.description, author: pkg.author, bundleHash: pkg.bundleHash, path: `apps/${pkg.id}/${pkg.version}.json`, score: 1, laps: 1, publishedAt: 1, updatedAt: 1, ...extra });
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

  cache.m.set(`pkg:${entry.bundleHash}`, { ...pkg, html: html('tampered in the cache') });
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
