import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync, readFileSync, existsSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { tmpdir } from 'node:os';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import deployment from '../../../deployment.json' with { type: 'json' };
import { buildRelease, signRelease, verifyRelease, checkFile, newSiteKey, listFiles } from '../release.mjs';

function site() {
  const dir = mkdtempSync(join(tmpdir(), 'aiwa-site-'));
  mkdirSync(join(dir, 'lib'));
  writeFileSync(join(dir, 'index.html'), '<!doctype html><title>Aiwa</title>');
  writeFileSync(join(dir, 'app.js'), 'console.log("app")');
  writeFileSync(join(dir, 'lib/aiwa.js'), 'export const sdk = 1;');
  return dir;
}

test('a release is described, signed, and read back by whoever holds the public key', () => {
  const dir = site();
  const { seed, publicKey } = newSiteKey();
  const { bytes } = buildRelease(dir, { version: 'v1', createdAt: 1790000000000 });
  const release = verifyRelease(bytes, signRelease(bytes, seed), publicKey);
  assert.deepEqual(Object.keys(release.files), ['app.js', 'index.html', 'lib/aiwa.js']);
  assert.deepEqual(listFiles(dir), ['app.js', 'index.html', 'lib/aiwa.js']);
  checkFile(release, 'app.js', new TextEncoder().encode('console.log("app")'));
});

test('a release that was changed, or signed by another key, is refused', () => {
  const dir = site();
  const { seed, publicKey } = newSiteKey();
  const { bytes } = buildRelease(dir, { version: 'v1' });
  const signature = signRelease(bytes, seed);
  const changed = new TextEncoder().encode(new TextDecoder().decode(bytes).replace('v1', 'v2'));
  assert.throws(() => verifyRelease(changed, signature, publicKey), /not signed by the site key/);
  assert.throws(() => verifyRelease(bytes, signature, newSiteKey().publicKey), /not signed by the site key/);
  assert.throws(() => verifyRelease(bytes, 'not hex', publicKey), /not signed by the site key/);
});

test('a file that is not the one the release lists is refused', () => {
  const dir = site();
  const { seed, publicKey } = newSiteKey();
  const { bytes } = buildRelease(dir, { version: 'v1' });
  const release = verifyRelease(bytes, signRelease(bytes, seed), publicKey);
  assert.throws(() => checkFile(release, 'app.js', new TextEncoder().encode('console.log("evil")')), /not what the release says/);
  assert.throws(() => checkFile(release, 'other.js', new Uint8Array()), /not in the release/);
});

test('a signed release with a path that leaves the folder, or no index.html, is refused', () => {
  const { seed, publicKey } = newSiteKey();
  const sha = 'a'.repeat(64);
  const signed = (files) => { const bytes = new TextEncoder().encode(JSON.stringify({ format: 'aiwa-site/1', version: 'x', createdAt: 1, files })); return [bytes, signRelease(bytes, seed)]; };
  assert.throws(() => verifyRelease(...signed({ 'index.html': sha, '../evil.js': sha }), publicKey), /not allowed/);
  assert.throws(() => verifyRelease(...signed({ 'index.html': sha, '/etc/passwd': sha }), publicKey), /not allowed/);
  assert.throws(() => verifyRelease(...signed({ 'app.js': sha }), publicKey), /index\.html/);
  assert.throws(() => verifyRelease(...signed({ 'index.html': 'zz' }), publicKey), /SHA-256/);
});

// The Android app checks releases in Kotlin (SiteRelease.kt). Its tests carry a release signed here; this is the same one, read from the
// Kotlin file, so that the two sides cannot drift apart: what Kotlin accepts is what this side signs.
test('the release in the Android tests is one this side accepts', () => {
  const kotlin = readFileSync(join(dirname(fileURLToPath(import.meta.url)), '../../../android/bridge/src/test/java/com/aiwa/bridge/SiteReleaseTest.kt'), 'utf8');
  const constant = (name) => {
    const m = kotlin.match(new RegExp(`private const val ${name} =\\s*"((?:[^"\\\\]|\\\\.)*)"`));
    assert.ok(m, `${name} is in the Kotlin test`);
    return m[1].replace(/\\"/g, '"');
  };
  const release = verifyRelease(new TextEncoder().encode(constant('NODE_RELEASE')), constant('NODE_SIGNATURE'), constant('NODE_PUBLIC_KEY'));
  checkFile(release, 'index.html', new TextEncoder().encode(constant('NODE_INDEX')));
  checkFile(release, 'app.js', new TextEncoder().encode(constant('NODE_APP')));
});

test('the site key of deployment.json is a public key, and the signing script refuses to sign without or with the wrong secret', () => {
  assert.match(deployment.siteKey, /^[0-9a-f]{64}$/);
  assert.match(deployment.siteUrl, /^https:\/\/.*\/$/);
  const script = join(dirname(fileURLToPath(import.meta.url)), '../../../scripts/sign-site.mjs');
  const run = (dir, args, env) => spawnSync(process.execPath, [script, dir, ...args], { env: { PATH: process.env.PATH, ...env }, encoding: 'utf8' });

  const described = site();
  assert.equal(run(described, ['--unsigned'], {}).status, 0);
  assert.ok(existsSync(join(described, 'release.json')) && !existsSync(join(described, 'release.sig')));
  const release = JSON.parse(readFileSync(join(described, 'release.json'), 'utf8'));
  assert.deepEqual(Object.keys(release.files), ['app.js', 'index.html', 'lib/aiwa.js']);

  const noSecret = site();
  assert.equal(run(noSecret, [], {}).status, 3);
  assert.ok(!existsSync(join(noSecret, 'release.sig')));

  const wrongSecret = site();
  const other = newSiteKey();
  assert.notEqual(run(wrongSecret, [], { SITE_SIGNING_KEY: other.seed }).status, 0);
  assert.ok(!existsSync(join(wrongSecret, 'release.sig')));
});
