// The store's logic, apart from any page: read the registry's index, rank it, fetch an app's package, and refuse to hand
// over anything that is not exactly what the author signed.

import { verifyAppPackage } from 'aiwa-registry/app-package';
import { rankApps, ratioOf } from 'aiwa-registry/rank';

export { rankApps, ratioOf };

/**
 * The index, from the network; if the network fails, the last one seen (`source: 'cache'`).
 * @param {{ baseUrl: string, fetchFn?: Function, cache?: { get: Function, set: Function } }} args
 */
export async function loadIndex({ baseUrl, fetchFn = fetch, cache = null }) {
  try {
    const response = await fetchFn(`${baseUrl}/index.json`, { cache: 'no-cache' });
    if (!response.ok) throw new Error(`The registry answered ${response.status}`);
    const index = await response.json();
    if (!index || index.format !== 'aiwa-store-index/1' || !Array.isArray(index.apps)) throw new Error('The registry\'s index is not in a format this store reads');
    const apps = rankApps(index.apps.filter(isEntry));
    await cache?.set('index', { ...index, apps }).catch?.(() => {});
    return { apps, source: 'network' };
  } catch (error) {
    const cached = await cache?.get('index').catch(() => null);
    if (cached) return { apps: rankApps(cached.apps), source: 'cache', error: error.message };
    throw error;
  }
}

const isEntry = (e) => e && ['id', 'name', 'version', 'description', 'author', 'bundleHash', 'path'].every((k) => typeof e[k] === 'string') && typeof e.score === 'number' && typeof e.laps === 'number';

/** Apps whose id, name, description or author contains every word of `query` (case-insensitive). */
export function filterApps(apps, query) {
  const words = String(query ?? '').toLowerCase().split(/\s+/).filter(Boolean);
  if (words.length === 0) return apps;
  return apps.filter((app) => {
    const haystack = `${app.id} ${app.name} ${app.description} ${app.author}`.toLowerCase();
    return words.every((w) => haystack.includes(w));
  });
}

/**
 * The package of `entry`, verified. Packages are content-addressed, so a cached copy is never stale: the cache is keyed
 * by the hash the index lists, and what it holds is checked again like what the network gives.
 * @returns {Promise<{ pkg: object, html: string, source: 'network'|'cache' }>}
 * @throws if the package is not what the entry says, or is not what its author signed
 */
export async function loadApp({ entry, baseUrl, fetchFn = fetch, cache = null }) {
  const key = `pkg:${entry.bundleHash}`;
  let pkg = await cache?.get(key).catch(() => null);
  let source = 'cache';
  if (!pkg) {
    const response = await fetchFn(`${baseUrl}/${entry.path}`);
    if (!response.ok) throw new Error(`The registry does not serve this app (${response.status})`);
    pkg = await response.json();
    source = 'network';
  }
  const verified = await verifyAppPackage(pkg);
  if (!verified.ok) throw new Error(`Not opened: ${verified.reason}`);
  for (const field of ['id', 'version', 'author', 'bundleHash']) {
    if (pkg[field] !== entry[field]) throw new Error(`Not opened: the package's ${field} is not the one the registry lists`);
  }
  if (source === 'network') await cache?.set(key, pkg).catch?.(() => {});
  return { pkg, html: pkg.html, source };
}

export const shortAddress = (s) => (s && s.length > 14 ? `${s.slice(0, 6)}…${s.slice(-4)}` : (s ?? ''));

/** The ranking figure as the store shows it. */
export const figureOf = (app) => ratioOf(app).toPrecision(3);
