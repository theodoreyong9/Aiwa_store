import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync, readFileSync, existsSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { tmpdir } from 'node:os';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import deployment from '../../../deployment.json' with { type: 'json' };
import { buildRelease, parseRelease, checkFile, listFiles } from '../release.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const utf8 = (text) => new TextEncoder().encode(text);

function site() {
  const dir = mkdtempSync(join(tmpdir(), 'aiwa-site-'));
  mkdirSync(join(dir, 'lib'));
  writeFileSync(join(dir, 'index.html'), '<!doctype html><title>Aiwa</title>');
  writeFileSync(join(dir, 'app.js'), 'console.log("app")');
  writeFileSync(join(dir, 'lib/aiwa.js'), 'export const sdk = 1;');
  return dir;
}

test('a release lists every file with its hash, and reads back', () => {
  const dir = site();
  const { bytes } = buildRelease(dir, { version: 'v1', createdAt: 1790000000000 });
  const release = parseRelease(bytes);
  assert.deepEqual(Object.keys(release.files), ['app.js', 'index.html', 'lib/aiwa.js']);
  assert.deepEqual(listFiles(dir), ['app.js', 'index.html', 'lib/aiwa.js']);
  assert.equal(release.createdAt, 1790000000000);
  checkFile(release, 'app.js', utf8('console.log("app")'));
});

test('a file that is not the one the release lists is refused', () => {
  const { bytes } = buildRelease(site(), { version: 'v1' });
  const release = parseRelease(bytes);
  assert.throws(() => checkFile(release, 'app.js', utf8('console.log("evil")')), /not what the release says/);
  assert.throws(() => checkFile(release, 'other.js', new Uint8Array()), /not in the release/);
});

test('a release with a path that leaves the folder, a bad hash, or no index.html is refused', () => {
  const sha = 'a'.repeat(64);
  const release = (files, extra = {}) => utf8(JSON.stringify({ format: 'aiwa-site/1', version: 'x', createdAt: 1, files, ...extra }));
  assert.throws(() => parseRelease(release({ 'index.html': sha, '../evil.js': sha })), /not allowed/);
  assert.throws(() => parseRelease(release({ 'index.html': sha, '/etc/passwd': sha })), /not allowed/);
  assert.throws(() => parseRelease(release({ 'app.js': sha })), /index\.html/);
  assert.throws(() => parseRelease(release({ 'index.html': 'zz' })), /SHA-256/);
  assert.throws(() => parseRelease(release({ 'index.html': sha }, { format: 'other/1' })), /not aiwa-site/);
  assert.throws(() => parseRelease(release({ 'index.html': sha }, { createdAt: 'yesterday' })), /version or date/);
});

// The Android app checks releases in Kotlin (SiteRelease.kt). Its tests carry a release; this is the same one, read from the Kotlin
// file, so that the two sides cannot drift apart: what Kotlin accepts is what this side accepts.
test('the release in the Android tests is one this side accepts', () => {
  const kotlin = readFileSync(join(here, '../../../android/bridge/src/test/java/com/aiwa/bridge/SiteReleaseTest.kt'), 'utf8');
  const constant = (name) => {
    const m = kotlin.match(new RegExp(`private const val ${name} =\\s*"((?:[^"\\\\]|\\\\.)*)"`));
    assert.ok(m, `${name} is in the Kotlin test`);
    return m[1].replace(/\\"/g, '"');
  };
  const release = parseRelease(utf8(constant('NODE_RELEASE')));
  checkFile(release, 'index.html', utf8(constant('NODE_INDEX')));
  checkFile(release, 'app.js', utf8(constant('NODE_APP')));
});

test('the site is an https address, and the script that describes a build writes release.json', () => {
  assert.match(deployment.siteUrl, /^https:\/\/.*\/$/);
  const dir = site();
  const run = spawnSync(process.execPath, [join(here, '../../../scripts/describe-site.mjs'), dir], { encoding: 'utf8' });
  assert.equal(run.status, 0, run.stderr);
  assert.ok(existsSync(join(dir, 'release.json')));
  const release = parseRelease(readFileSync(join(dir, 'release.json')));
  assert.deepEqual(Object.keys(release.files), ['app.js', 'index.html', 'lib/aiwa.js']);
  assert.equal(spawnSync(process.execPath, [join(here, '../../../scripts/describe-site.mjs')], { encoding: 'utf8' }).status, 2);
});
