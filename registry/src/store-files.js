// The registry's files, in the repository:
//
//   store/index.json                 what the store lists, best first (public)
//   store/apps/<id>/<version>.json   each published package, never rewritten (public)
//   store/state/baselines.json       what this registry derived from each author's events (internal, not deployed)
//   store/state/witnesses.json       what other wallets hold of each author (internal, not deployed)

import { readFileSync, writeFileSync, mkdirSync, existsSync } from 'node:fs';
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

export function readStore(dir) {
  const fresh = emptyStore();
  return {
    index: readJson(join(dir, 'index.json'), fresh.index),
    baselines: readJson(join(dir, 'state', 'baselines.json'), fresh.baselines),
    witnesses: readJson(join(dir, 'state', 'witnesses.json'), fresh.witnesses),
  };
}

/** Writes the store after `applyAccepted`, and the package of a publication under apps/. */
export function writeStore(dir, store, accepted = null) {
  const index = { ...store.index, apps: rankApps(store.index.apps) };
  writeJson(join(dir, 'index.json'), index);
  writeJson(join(dir, 'state', 'baselines.json'), store.baselines, false);
  writeJson(join(dir, 'state', 'witnesses.json'), store.witnesses);
  if (accepted && accepted.kind === 'publish') writeJson(join(dir, accepted.entry.path), accepted.package, false);
}
