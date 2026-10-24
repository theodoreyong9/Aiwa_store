import test from 'node:test';
import assert from 'node:assert/strict';
import { ed25519 } from '@noble/curves/ed25519.js';
import { geohash, cleanLinks, checkEntry } from '../src/voir-core.js';

// voir-core.js: the parts of « Voir » that need no page
const utf8 = new TextEncoder();
const hex = (b) => Buffer.from(b).toString('hex');
const sign = (e, secret) => hex(ed25519.sign(utf8.encode(JSON.stringify({ v: 1, cell: e.cell, repo: e.repo, links: e.links, score: e.score, laps: e.laps, ts: e.ts, pub: e.pub })), secret));

test('a geohash is the known cell of a known place', () => {
  assert.equal(geohash(57.64911, 10.40744, 11), 'u4pruydqqvj');
  assert.equal(geohash(48.8584, 2.2945, 4), 'u09t');
});

test('only https links of a known kind are kept, short and few', () => {
  const links = cleanLinks([
    { kind: 'page', url: 'https://a.github.io/x/' }, { kind: 'page', url: 'http://insecure.example/' }, { kind: 'script', url: 'https://x.example/' },
    { kind: 'docs', url: 'javascript:alert(1)' }, { kind: 'app', url: 'https://github.com/a/b/releases' }, 'nope',
  ]);
  assert.deepEqual(links.map((l) => l.kind), ['page', 'app']);
  assert.deepEqual(cleanLinks('x'), []);
});

test('an entry is accepted only if it is signed by the key it names, fresh, for this cell', () => {
  const secret = ed25519.utils.randomSecretKey(); const pub = hex(ed25519.getPublicKey(secret));
  const now = Date.now();
  const entry = { v: 1, cell: 'u09t', repo: 'a/b', links: [{ kind: 'page', url: 'https://a.github.io/b/' }], score: 12.5, laps: 3, ts: now, pub };
  entry.sig = sign(entry, secret);
  assert.equal(checkEntry(entry, 'u09t', now)?.score, 12.5);
  assert.equal(checkEntry({ ...entry, score: 999 }, 'u09t', now), null, 'a changed score breaks the signature');
  assert.equal(checkEntry(entry, 'u09u', now), null, 'another cell');
  assert.equal(checkEntry(entry, 'u09t', now + 11 * 60_000), null, 'too old');
  assert.equal(checkEntry({ ...entry, links: [{ kind: 'page', url: 'https://evil.example/' }] }, 'u09t', now), null, 'links are signed too');
  assert.equal(checkEntry(null, 'u09t', now), null);
});
