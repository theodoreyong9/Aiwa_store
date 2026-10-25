import test from 'node:test';
import assert from 'node:assert/strict';
import { ed25519 } from '@noble/curves/ed25519.js';
import { geohash, bounds, cellsAround, cleanLinks, checkEntry, parseCard, cardText, parseRepo, repoLinks } from '../src/voir-core.js';

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

test('the cells around a position: its own and the neighbours toward the nearest edges, 8 characters (about 38 x 19 m)', () => {
  const cells = cellsAround(46.5197, 6.6323);
  assert.ok(cells.length >= 2 && cells.length <= 4);
  assert.ok(cells.every((c) => c.length === 8));
  assert.equal(cells[0], geohash(46.5197, 6.6323, 8));
  const b = bounds(cells[0]);
  assert.ok(b.latLo <= 46.5197 && 46.5197 <= b.latHi && b.lonLo <= 6.6323 && 6.6323 <= b.lonHi);
  assert.ok(b.latHi - b.latLo < 0.0003 && b.lonHi - b.lonLo < 0.0005);
});

test('a card from a QR code or a pasted text: https links only, nothing else', () => {
  const links = [{ kind: 'page', url: 'https://a.github.io/b/' }, { kind: 'docs', url: 'https://github.com/a/b/tree/HEAD/docs' }];
  const card = parseCard(cardText('a/b', links));
  assert.equal(card.repo, 'a/b');
  assert.equal(card.links.length, 2);
  assert.equal(parseCard('https://a.github.io/b/').links[0].kind, 'page');
  assert.equal(parseCard('http://insecure.example/'), null);
  assert.equal(parseCard('javascript:alert(1)'), null);
  assert.equal(parseCard('{"links":[{"kind":"page","url":"http://x"}]}'), null);
  assert.equal(parseCard('x'.repeat(2000)), null);
});

test('a repository from the GitHub name and the name, or from a pasted address', () => {
  assert.equal(parseRepo('theodoreyong9', 'Aiwa_store'), 'theodoreyong9/Aiwa_store');
  assert.equal(parseRepo('theodoreyong9/Aiwa_store'), 'theodoreyong9/Aiwa_store');
  assert.equal(parseRepo('@jdacunha', 'mon-depot'), 'jdacunha/mon-depot');
  assert.equal(parseRepo('https://github.com/a/b/tree/main/docs'), 'a/b');
  assert.equal(parseRepo('https://github.com/a/b.git'), 'a/b');
  assert.equal(parseRepo('seulement'), null);
  assert.equal(parseRepo('', ''), null);
  assert.equal(parseRepo('a b', 'c'), null);
});

test('the links of the Files of a repository are the five public ones, all https', () => {
  const links = repoLinks('a/b');
  assert.deepEqual(links.map((l) => l.kind), ['docs', 'media', 'app', 'page', 'contract']);
  assert.equal(links[3].url, 'https://a.github.io/b/');
  assert.equal(repoLinks('a/a.github.io')[3].url, 'https://a.github.io/');
  assert.deepEqual(cleanLinks(links), links);
});
