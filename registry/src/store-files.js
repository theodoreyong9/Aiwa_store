// The registry's files, in the repository:
//
//   store/index.json                 what the store lists, best first (public)
//   store/apps/<id>/<version>.json   each published package, never rewritten (public)
//   store/apps/<id>/<version>.bundle.json   for an app of kind aiwa: the signed events of its bundle (public)
//   store/baselines/<author>.json    what this registry derived from an author's events: the wallet state it validated, kept
//                                    to continue from, and what an author's wallet restores from on a new phone (public)
//   store/state/witnesses.json       what other wallets hold of each author (internal, not deployed)

import { readFileSync, writeFileSync, mkdirSync, existsSync, readdirSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { emptyStore } from './validate.js';
import { rankApps } from './rank.js';

const readJson = (file, fallback) => {
  if (!existsSync(file)) return fallback;
  return JSON.parse(readFileSync(file, 'utf8'));
};
const writeJson = (file, value, pretty = true) => {
  mkdirSync(dirname(file), { recursive: true });
  writeFileSync(file, `${JSON.stringify(value, null, pretty ? 2 : 0)}\n`);
};

function readBaselines(dir) {
  const baselines = {};
  const folder = join(dir, 'baselines');
  if (!existsSync(folder)) return baselines;
  for (const file of readdirSync(folder)) if (file.endsWith('.json')) baselines[file.slice(0, -5)] = readJson(join(folder, file));
  return baselines;
}

export function readStore(dir) {
  const fresh = emptyStore();
  return {
    index: readJson(join(dir, 'index.json'), fresh.index),
    baselines: readBaselines(dir),
    witnesses: readJson(join(dir, 'state', 'witnesses.json'), fresh.witnesses),
  };
}

/** Writes the store after `applyAccepted`: the index, the baseline of the author concerned, and the package of a publication under apps/. */
export function writeStore(dir, store, accepted = null) {
  const index = { ...store.index, apps: rankApps(store.index.apps) };
  writeJson(join(dir, 'index.json'), index);
  if (accepted?.baseline) writeJson(join(dir, 'baselines', `${accepted.author}.json`), store.baselines[accepted.author], false);
  writeJson(join(dir, 'state', 'witnesses.json'), store.witnesses);
  if (accepted && accepted.kind === 'publish') {
    writeJson(join(dir, accepted.entry.path), accepted.package, false);
    if (accepted.bundle) writeJson(join(dir, accepted.entry.bundlePath), accepted.bundle, false);
  }
}
