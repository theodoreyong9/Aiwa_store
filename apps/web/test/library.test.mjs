import { test } from 'node:test';
import assert from 'node:assert/strict';
import { parseAccount, accountKey, listAccount, loadLibrary, filterLibrary, loadLink } from '../src/library.js';

const memory = () => { const m = new Map(); return { get: (k) => m.get(k) ?? null, set: (k, v) => m.set(k, v) }; };
const file = (name, extra = {}) => ({ type: 'file', name, path: name, size: 100, ...extra });
const listing = (files) => async () => ({ ok: true, status: 200, json: async () => files });

test('an account is a login (its aiwa-apps repository) or login/repo (the aiwa-apps folder of that repository)', () => {
  assert.deepEqual(parseAccount('alice'), { owner: 'alice', repo: 'aiwa-apps', dir: '' });
  assert.deepEqual(parseAccount(' https://github.com/alice/tools/ '), { owner: 'alice', repo: 'tools', dir: 'aiwa-apps' });
  for (const bad of ['', 'a b', '-x', 'a/b/c', '../x']) assert.equal(parseAccount(bad), null, bad);
  assert.equal(accountKey(parseAccount('alice/tools')), 'alice/tools');
});

test('an account publishes its name.app.html files, read from GitHub and kept ten minutes', async () => {
  const store = memory();
  let asked = 0;
  const fetchFn = async (url) => { asked++; assert.equal(url, 'https://api.github.com/repos/alice/tools/contents/aiwa-apps'); return listing([file('tally.app.html'), file('notes.txt'), file('big.app.html', { size: 9e6 }), { type: 'dir', name: 'x.app.html' }])(); };
  const first = await listAccount(parseAccount('alice/tools'), { fetchFn, store, now: 0 });
  assert.deepEqual(first.apps.map((a) => [a.id, a.repo, a.file]), [['alice/tools/tally', 'alice/tools', 'tally.app.html']]);
  await listAccount(parseAccount('alice/tools'), { fetchFn, store, now: 60_000 });
  assert.equal(asked, 1, 'inside the ten minutes GitHub is not asked again');
  const later = await listAccount(parseAccount('alice/tools'), { fetchFn: async () => ({ ok: false, status: 403 }), store, now: 20 * 60_000 });
  assert.equal(later.source, 'cache', 'the limit spent: the last listing is kept');
  assert.match(later.error, /limit/);
});

test('one account that fails does not hide the others', async () => {
  const fetchFn = async (url) => (url.includes('/alice/') ? listing([file('a.app.html')])() : { ok: false, status: 404 });
  const { apps, problems } = await loadLibrary([parseAccount('alice'), parseAccount('ghost')], { fetchFn, store: memory() });
  assert.deepEqual(apps.map((a) => a.name), ['a']);
  assert.match(problems[0], /ghost/);
  assert.deepEqual(filterLibrary(apps, 'alice').length, 1);
});

test('the code is read as it is now', async () => {
  const entry = { repo: 'alice/tools', file: 'aiwa-apps/t.app.html', ref: 'HEAD' };
  const urls = [];
  const serve = (code) => async (u) => { urls.push(u); return { ok: true, text: async () => code }; };
  assert.equal((await loadLink({ entry, fetchFn: serve('v1') })).html, 'v1');
  assert.equal((await loadLink({ entry, fetchFn: serve('v2') })).html, 'v2');
  assert.equal(urls[0], 'https://raw.githubusercontent.com/alice/tools/HEAD/aiwa-apps/t.app.html');
  await assert.rejects(loadLink({ entry, fetchFn: async () => ({ ok: false }) }), /does not serve/);
});
