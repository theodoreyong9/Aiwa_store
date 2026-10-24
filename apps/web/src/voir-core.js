// « Voir », the parts that need no page: the map cell, the cleaning of a stranger's links, the check of a signed entry.
import { verifyHex } from 'aiwa-core';

export const PRECISION = [7, 6, 5, 4];                // geohash length for 100 m, 1 km, 10 km, the town
export const REACH = ['100 m', '1 km', '10 km', 'la ville'];
const BASE32 = '0123456789bcdefghjkmnpqrstuvwxyz';
const KINDS = new Set(['docs', 'media', 'app', 'page', 'contract']);
const MAX_LINKS = 6, MAX_URL = 300, FRESH_MS = 10 * 60_000;
const BLOCKED = 'aiwa-voir-blocked';
export const PERIOD_MS = 30_000;
export const utf8 = new TextEncoder();

/** A geohash of `length` characters for a position: a map cell, the coarsest thing this page ever sends. */
export function geohash(lat, lon, length) {
  let latLo = -90, latHi = 90, lonLo = -180, lonHi = 180, bit = 0, ch = 0, even = true, out = '';
  while (out.length < length) {
    if (even) { const mid = (lonLo + lonHi) / 2; if (lon >= mid) { ch = (ch << 1) | 1; lonLo = mid; } else { ch <<= 1; lonHi = mid; } }
    else { const mid = (latLo + latHi) / 2; if (lat >= mid) { ch = (ch << 1) | 1; latLo = mid; } else { ch <<= 1; latHi = mid; } }
    even = !even;
    if (++bit === 5) { out += BASE32[ch]; bit = 0; ch = 0; }
  }
  return out;
}

export const readBlocked = () => { try { return new Set(JSON.parse(localStorage.getItem(BLOCKED) ?? '[]')); } catch { return new Set(); } };
export const writeBlocked = (set) => { try { localStorage.setItem(BLOCKED, JSON.stringify([...set])); } catch { /* it lasts until the page closes */ } };

/** Only https links of a known kind, short, at most MAX_LINKS: what a stranger sends is never trusted further than that. */
export function cleanLinks(raw) {
  if (!Array.isArray(raw)) return [];
  const out = [];
  for (const item of raw.slice(0, MAX_LINKS)) {
    if (!item || typeof item.kind !== 'string' || !KINDS.has(item.kind) || typeof item.url !== 'string' || item.url.length > MAX_URL) continue;
    let url;
    try { url = new URL(item.url); } catch { continue; }
    if (url.protocol !== 'https:') continue;
    out.push({ kind: item.kind, url: url.href });
  }
  return out;
}

/** The bytes that are signed: a fixed order of fields, so that the sender and the receiver read the same message. */
export const messageOf = (e) => utf8.encode(JSON.stringify({ v: 1, cell: e.cell, repo: e.repo, links: e.links, score: e.score, laps: e.laps, ts: e.ts, pub: e.pub }));

/** An entry received: valid, signed by the key it names, fresh, links cleaned; or null. */
export function checkEntry(entry, cell, now = Date.now()) {
  if (!entry || entry.v !== 1 || entry.cell !== cell || typeof entry.pub !== 'string' || typeof entry.sig !== 'string') return null;
  if (typeof entry.ts !== 'number' || Math.abs(now - entry.ts) > FRESH_MS) return null;
  const links = cleanLinks(entry.links);
  const repo = typeof entry.repo === 'string' && /^[\w.-]{1,60}\/[\w.-]{1,100}$/.test(entry.repo) ? entry.repo : '';
  const score = Number.isFinite(entry.score) ? entry.score : 0, laps = Number.isFinite(entry.laps) ? entry.laps : 0;
  const clean = { v: 1, cell, repo, links, score, laps, ts: entry.ts, pub: entry.pub };
  if (!verifyHex(messageOf(entry), entry.sig, entry.pub)) return null;
  return { ...clean, sig: entry.sig };
}

