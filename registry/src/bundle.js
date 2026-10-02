// The code of an `aiwa` app: a bundle published through Aiwa (aiwa-platform's bundle.js), that is signed events, every file
// by its hash, and a signed manifest that pins them all. The package points at the manifest's id. This file builds a bundle
// from files, and VERIFIES one: the registry before it keeps it, and the store before it runs it, each on its own and
// never trusting whoever served the events.

import { EventLog, createMemoryBackend } from 'aiwa-core';
import { publishBundle, readBundle } from 'aiwa-platform';

export const BUNDLE_FORMAT = 'aiwa-bundle/1';
// Where the events of an app's bundle live in Aiwa: one domain for all of them.
export const BUNDLE_DOMAIN = 'aiwa-app';

export const BUNDLE_LIMITS = {
  files: 40,
  bytes: 1024 * 1024,                         // the files' content, all together
  pathPattern: /^[A-Za-z0-9][A-Za-z0-9._/-]{0,99}$/,
  entry: 'index.html',
};

const utf8Length = (text) => new TextEncoder().encode(text).length;

/** The refusal reason for a set of files, or null: what can be said before anything is signed. */
export function filesProblem(files) {
  if (!Array.isArray(files) || files.length === 0) return 'The app has no files';
  if (files.length > BUNDLE_LIMITS.files) return `An app has at most ${BUNDLE_LIMITS.files} files`;
  const seen = new Set();
  let bytes = 0;
  for (const file of files) {
    if (!file || typeof file.path !== 'string' || typeof file.content !== 'string') return 'A file is { path, content } as text';
    if (!BUNDLE_LIMITS.pathPattern.test(file.path) || file.path.includes('..') || file.path.includes('//') || file.path.endsWith('/')) return `The file name ${JSON.stringify(file.path)} is not allowed`;
    if (seen.has(file.path)) return `The file ${file.path} appears twice`;
    seen.add(file.path);
    bytes += utf8Length(file.content);
  }
  if (!seen.has(BUNDLE_LIMITS.entry)) return `The app has no ${BUNDLE_LIMITS.entry}`;
  if (files.find((f) => f.path === BUNDLE_LIMITS.entry).content.trim() === '') return `${BUNDLE_LIMITS.entry} is empty`;
  if (bytes > BUNDLE_LIMITS.bytes) return `The files are larger than ${BUNDLE_LIMITS.bytes / 1024} KB`;
  return null;
}

/**
 * Publishes `files` as a bundle signed by `identity`, in a log of its own (so that the bundle stands alone: the manifest
 * cites only its files, never the author's other history). Returns what a submission carries.
 * @returns {Promise<{ manifestId: string, bundle: { format: string, manifestId: string, events: object[] } }>}
 */
export async function buildBundle(identity, { name, version, files }) {
  const problem = filesProblem(files);
  if (problem) throw new Error(problem);
  const log = new EventLog(createMemoryBackend());
  const { manifestEventId } = await publishBundle(identity, log, BUNDLE_DOMAIN, { name, version, files });
  const events = [];
  for await (const event of log.since([])) events.push(event);
  return { manifestId: manifestEventId, bundle: { format: BUNDLE_FORMAT, manifestId: manifestEventId, events } };
}

/**
 * Verifies a bundle against what the package pins. Every event is checked by Aiwa itself (id, signature, parents); then:
 * the manifest is the pinned one, signed by the author's domain, naming exactly this app and version; every file it
 * lists is signed by the same domain; nothing else is in the bundle; the files are within the limits.
 * @param {object} bundle { format, manifestId, events }
 * @param {{ manifestId: string, domain: string, name: string, version: string }} pin from the package and its signature
 * @returns {Promise<{ ok: boolean, reason: string, files?: Record<string, string> }>}
 */
export async function verifyBundle(bundle, { manifestId, domain, name, version }) {
  if (!bundle || bundle.format !== BUNDLE_FORMAT || !Array.isArray(bundle.events)) return { ok: false, reason: `Not an Aiwa bundle (format ${BUNDLE_FORMAT})` };
  if (bundle.events.length > BUNDLE_LIMITS.files + 1) return { ok: false, reason: 'The bundle holds too many events' };
  const log = new EventLog(createMemoryBackend());
  try {
    await log.appendMany(bundle.events);
  } catch (err) {
    return { ok: false, reason: `Aiwa refuses these events: ${err.message}` };
  }
  const manifest = await log.get(manifestId);
  if (!manifest || manifest.type !== 'bundle.manifest') return { ok: false, reason: 'The pinned manifest is not in the bundle' };
  if (manifest.author !== domain) return { ok: false, reason: 'The manifest is not signed by the app\'s author' };
  if (manifest.payload.name !== name || manifest.payload.version !== version) return { ok: false, reason: 'The manifest is for another name or version than the package' };
  const read = await readBundle(log, manifestId);
  if (!read) return { ok: false, reason: 'A file the manifest lists is not in the bundle' };
  for (const id of Object.values(manifest.payload.files)) {
    if ((await log.get(id)).author !== domain) return { ok: false, reason: 'A file is not signed by the app\'s author' };
  }
  if (bundle.events.length !== Object.keys(read.files).length + 1) return { ok: false, reason: 'The bundle holds events the manifest does not list' };
  const files = Object.entries(read.files).map(([path, content]) => ({ path, content }));
  const problem = filesProblem(files);
  if (problem) return { ok: false, reason: problem };
  return { ok: true, reason: 'ok', files: read.files };
}
