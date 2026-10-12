// An app of the store, as its author signs it and the registry keeps it. Two kinds, both submitted on GitHub:
//
//   code   the app is ONE self-contained HTML file (it loads what it needs from the network itself), carried by the package.
//   aiwa   the package carries no code, only a POINTER: the id of a signed manifest published through Aiwa, which pins
//          every file of the app by hash (aiwa-platform's bundle). The store resolves the pointer through Aiwa and
//          verifies it (bundle.js): the code is immutable, and exactly what its author published, whoever serves it.
//
// What is signed is not the content but its hash, so the registry, the store and anyone with the package can check that
// what runs is exactly what the author published:
//
//   package = {
//     format: 'aiwa-app/1', kind,
//     id, name, version, description,               the metadata
//     html | manifestId,                            the content (code), or its pointer (aiwa)
//     author,                                       the author's Solana address (the key that signed)
//     bundleHash,                                   sha256 of the content, below
//     authorization,                                a signedAction by the author's key over (action, appId, version, bundleHash)
//   }
//
// The authorization is aiwa-lib's own signedAction/verifySignedAction: the same embedded-signature scheme every
// contract in this repository uses, so this file adds no signature code of its own.

import { sha256 } from '@noble/hashes/sha2.js';
import { bytesToHex } from '@noble/hashes/utils.js';
import { base58Encode, deriveId } from 'aiwa-core';
import { signedAction, verifySignedAction } from 'aiwa-lib';

export const APP_FORMAT = 'aiwa-app/1';
export const APP_KINDS = ['code', 'aiwa'];

export const APP_LIMITS = {
  htmlBytes: 512 * 1024,
  nameChars: 60,
  descriptionChars: 280,
  idPattern: /^[a-z0-9](?:[a-z0-9-]{0,38}[a-z0-9])?$/,
  versionPattern: /^\d{1,6}\.\d{1,6}\.\d{1,6}$/,
  manifestIdPattern: /^[0-9a-f]{64}$/,
};

const hexToBytes = (hex) => Uint8Array.from(hex.match(/.{2}/g) ?? [], (b) => parseInt(b, 16));
const utf8 = (text) => new TextEncoder().encode(text);

/** The content a package stands for: the HTML (code) or the manifest id (aiwa). */
export const contentOf = (pkg) => (pkg.kind === 'aiwa' ? pkg.manifestId : pkg.html);

/** The hash that identifies an app's content: sha256 of the JSON array [id, name, version, description, kind, content]. */
export function bundleHashOf(pkg) {
  const { id, name, version, description } = pkg;
  return bytesToHex(sha256(utf8(JSON.stringify([id, name, version, description, pkg.kind, contentOf(pkg)]))));
}

/** Compares two "major.minor.patch" strings: negative, 0 or positive. */
export function compareVersions(a, b) {
  const pa = a.split('.').map(Number);
  const pb = b.split('.').map(Number);
  for (let i = 0; i < 3; i++) if (pa[i] !== pb[i]) return pa[i] - pb[i];
  return 0;
}

/**
 * The shape and size of a package: what can be said without any key. Returns the reason it is refused, or null.
 * (The hash and the signature are `verifyAppPackage`'s.)
 */
export function appPackageProblem(pkg) {
  if (!pkg || typeof pkg !== 'object') return 'The package is not an object';
  if (pkg.format !== APP_FORMAT) return `The package format must be ${APP_FORMAT}`;
  if (!APP_KINDS.includes(pkg.kind)) return `The package kind is ${APP_KINDS.join(' or ')}`;
  for (const field of ['id', 'name', 'version', 'description', 'author', 'bundleHash']) {
    if (typeof pkg[field] !== 'string') return `The package has no ${field}`;
  }
  if (!APP_LIMITS.idPattern.test(pkg.id)) return 'The id is 1 to 40 characters: lowercase letters, digits and hyphens, not starting or ending with a hyphen';
  if (!APP_LIMITS.versionPattern.test(pkg.version)) return 'The version is major.minor.patch, numbers only';
  if (pkg.name.trim().length === 0 || pkg.name.length > APP_LIMITS.nameChars) return `The name is 1 to ${APP_LIMITS.nameChars} characters`;
  if (pkg.description.length > APP_LIMITS.descriptionChars) return `The description is at most ${APP_LIMITS.descriptionChars} characters`;
  if (pkg.kind === 'code') {
    if (typeof pkg.html !== 'string') return 'The package has no html';
    if (pkg.html.trim().length === 0) return 'The app is empty';
    if (utf8(pkg.html).length > APP_LIMITS.htmlBytes) return `The app is larger than ${APP_LIMITS.htmlBytes / 1024} KB`;
  } else if (typeof pkg.manifestId !== 'string' || !APP_LIMITS.manifestIdPattern.test(pkg.manifestId)) {
    return 'The pointer is the id of a manifest: 64 hexadecimal characters';
  }
  if (!pkg.authorization || typeof pkg.authorization !== 'object') return 'The package has no authorization';
  return null;
}

/**
 * Builds and signs a package with `identity` (an aiwa-core identity holding its secret key). `html` makes a code app,
 * `manifestId` an aiwa one.
 */
export async function buildAppPackage(identity, { id, name, version, description = '', html, manifestId }, { now = Date.now() } = {}) {
  const content = manifestId ? { kind: 'aiwa', manifestId } : { kind: 'code', html };
  const base = { id, name, version, description, ...content };
  const bundleHash = bundleHashOf(base);
  const authorization = await signedAction(identity, { from: identity.id, action: 'publish-app', appId: id, version, bundleHash }, { now });
  const pkg = { format: APP_FORMAT, kind: content.kind, id, name, version, description, ...(manifestId ? { manifestId } : { html }), author: base58Encode(hexToBytes(identity.publicKey)), bundleHash, authorization };
  const problem = appPackageProblem(pkg);
  if (problem) throw new Error(problem);
  return pkg;
}

/** Signs the request to refresh the ranking figure of one of the author's apps (no new content). */
export async function buildRefreshAuthorization(identity, { id }, { now = Date.now() } = {}) {
  return signedAction(identity, { from: identity.id, action: 'refresh-score', appId: id }, { now });
}

/**
 * Checks that the package is what its author signed: the hash is that of the content, the authorization is a valid
 * signature over (publish-app, id, version, hash) by the key whose Solana address is `author`.
 * @returns {Promise<{ ok: boolean, reason: string, domain: string|null }>} the author's Aiwa domain when ok
 */
export async function verifyAppPackage(pkg) {
  const problem = appPackageProblem(pkg);
  if (problem) return { ok: false, reason: problem, domain: null };
  if (bundleHashOf(pkg) !== pkg.bundleHash) return { ok: false, reason: 'The hash does not match the content', domain: null };
  const a = pkg.authorization;
  if (a.action !== 'publish-app' || a.appId !== pkg.id || a.version !== pkg.version || a.bundleHash !== pkg.bundleHash) {
    return { ok: false, reason: 'The authorization is not for this app, this version and this content', domain: null };
  }
  if (!(await verifySignedAction(a))) return { ok: false, reason: 'The author\'s signature is not valid', domain: null };
  const signer = base58Encode(hexToBytes(a.signerPubkey));
  if (signer !== pkg.author) return { ok: false, reason: 'The signing key is not the author\'s address', domain: null };
  return { ok: true, reason: 'ok', domain: await deriveId(hexToBytes(a.signerPubkey)) };
}

/** Same for a refresh authorization: valid, for this app, by the key whose address is `author`. */
export async function verifyRefreshAuthorization(authorization, { id, author }) {
  const a = authorization;
  if (!a || a.action !== 'refresh-score' || a.appId !== id) return { ok: false, reason: 'The authorization is not a refresh for this app', domain: null };
  if (!(await verifySignedAction(a))) return { ok: false, reason: 'The author\'s signature is not valid', domain: null };
  if (base58Encode(hexToBytes(a.signerPubkey)) !== author) return { ok: false, reason: 'The signing key is not the app\'s author', domain: null };
  return { ok: true, reason: 'ok', domain: await deriveId(hexToBytes(a.signerPubkey)) };
}
