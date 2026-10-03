// A release of the web app, as the Android app takes it from the site: `release.json` lists every file with its SHA-256, and
// `release.sig` is an Ed25519 signature of the exact bytes of `release.json`. The phone checks the signature with a public key
// that is in the APK, then each file against its hash, and only then serves the files itself: it never runs a page it has not
// verified. (android/bridge/.../SiteRelease.kt does the same checks in Kotlin; the two are tested against the same vectors.)
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { ed25519 } from '@noble/curves/ed25519.js';
import { sha256 } from '@noble/hashes/sha2.js';
import { bytesToHex, hexToBytes } from '@noble/hashes/utils.js';

export const RELEASE_FORMAT = 'aiwa-site/1';
export const RELEASE_FILE = 'release.json';
export const SIGNATURE_FILE = 'release.sig';
export const LIMITS = { files: 400, fileBytes: 5 * 1024 * 1024, totalBytes: 24 * 1024 * 1024 };

const SAFE_PATH = /^[A-Za-z0-9][A-Za-z0-9._@-]*(\/[A-Za-z0-9][A-Za-z0-9._@-]*)*$/;
const utf8 = (text) => new TextEncoder().encode(text);

/** The files of a directory, as sorted relative paths with `/` (the release's own files are not part of it). */
export function listFiles(dir, base = '') {
  const found = [];
  for (const name of readdirSync(join(dir, base)).sort()) {
    const relative = base ? `${base}/${name}` : name;
    if (statSync(join(dir, relative)).isDirectory()) found.push(...listFiles(dir, relative));
    else if (relative !== RELEASE_FILE && relative !== SIGNATURE_FILE) found.push(relative);
  }
  return found;
}

/** The release of `dir`: the bytes of release.json (keys in a fixed order), and the same as an object. */
export function buildRelease(dir, { version, createdAt = Date.now() }) {
  const files = {};
  for (const path of listFiles(dir)) files[path] = bytesToHex(sha256(readFileSync(join(dir, path))));
  const release = { format: RELEASE_FORMAT, version, createdAt, files };
  return { release, bytes: utf8(JSON.stringify(release, null, 1)) };
}

export const signRelease = (bytes, seedHex) => bytesToHex(ed25519.sign(bytes, hexToBytes(seedHex)));

/** A new key pair, as hex: `seed` is the secret, `publicKey` goes in deployment.json and the APK. */
export function newSiteKey() {
  const seed = ed25519.utils.randomSecretKey();
  return { seed: bytesToHex(seed), publicKey: bytesToHex(ed25519.getPublicKey(seed)) };
}

/** The release in `bytes` if `signatureHex` is the signature of those bytes by `publicKeyHex`, and its description is well formed; else throws. */
export function verifyRelease(bytes, signatureHex, publicKeyHex) {
  let valid = false;
  try { valid = ed25519.verify(hexToBytes(signatureHex), bytes, hexToBytes(publicKeyHex)); } catch { /* not hex, wrong size */ }
  if (!valid) throw new Error('the release is not signed by the site key');
  const release = JSON.parse(new TextDecoder().decode(bytes));
  if (release.format !== RELEASE_FORMAT) throw new Error(`the release is not ${RELEASE_FORMAT}`);
  if (typeof release.version !== 'string' || !Number.isSafeInteger(release.createdAt)) throw new Error('the release has no version or date');
  const paths = Object.keys(release.files ?? {});
  if (paths.length === 0 || paths.length > LIMITS.files) throw new Error(`a release has between 1 and ${LIMITS.files} files`);
  for (const path of paths) {
    if (!SAFE_PATH.test(path) || path.split('/').includes('..')) throw new Error(`the file name ${JSON.stringify(path)} is not allowed`);
    if (!/^[0-9a-f]{64}$/.test(release.files[path])) throw new Error(`the hash of ${path} is not a SHA-256`);
  }
  if (!paths.includes('index.html')) throw new Error('a release has an index.html');
  return release;
}

/** Throws unless `content` (bytes) is exactly the file `path` of `release`. */
export function checkFile(release, path, content) {
  const wanted = release.files[path];
  if (!wanted) throw new Error(`${path} is not in the release`);
  if (content.length > LIMITS.fileBytes) throw new Error(`${path} is too large`);
  if (bytesToHex(sha256(content)) !== wanted) throw new Error(`${path} is not what the release says`);
}
