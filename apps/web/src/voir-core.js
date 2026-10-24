// « Voir », the parts that need no page: the map cell, the cleaning of a stranger's links, the check of a signed entry.
import { verifyHex } from 'aiwa-core';

export const NEAR_LENGTH = 8;                          // a geohash of 8 characters is a cell of about 38 x 19 m: « near » means about 50 m
const BASE32 = '0123456789bcdefghjkmnpqrstuvwxyz';
const KINDS = new Set(['docs', 'media', 'app', 'page', 'contract']);
const MAX_LINKS = 6, MAX_URL = 300, FRESH_MS = 10 * 60_000;
const BLOCKED = 'aiwa-voir-blocked';
export const PERIOD_MS = 30_000;
export const utf8 = new TextEncoder();

/** The bounds of a geohash cell. */
export function bounds(hash) {
  let latLo = -90, latHi = 90, lonLo = -180, lonHi = 180, even = true;
  for (const ch of hash) {
    const v = BASE32.indexOf(ch);
    for (let b = 4; b >= 0; b--) {
      const bit = (v >> b) & 1;
      if (even) { const mid = (lonLo + lonHi) / 2; if (bit) lonLo = mid; else lonHi = mid; } else { const mid = (latLo + latHi) / 2; if (bit) latLo = mid; else latHi = mid; }
      even = !even;
    }
  }
  return { latLo, latHi, lonLo, lonHi };
}

/** The cell of a position and the (up to) three neighbours toward which it is nearest to the edge: the people within about 50 m are in one of these. */
export function cellsAround(lat, lon, length = NEAR_LENGTH) {
  const own = geohash(lat, lon, length), b = bounds(own);
  const cLat = (b.latLo + b.latHi) / 2, cLon = (b.lonLo + b.lonHi) / 2, sLat = b.latHi - b.latLo, sLon = b.lonHi - b.lonLo;
  const dy = lat >= cLat ? 1 : -1, dx = lon >= cLon ? 1 : -1;
  return [...new Set([own, geohash(cLat + dy * sLat, cLon, length), geohash(cLat, cLon + dx * sLon, length), geohash(cLat + dy * sLat, cLon + dx * sLon, length)])];
}

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
export function checkEntry(entry, cells, now = Date.now()) {
  const allowed = Array.isArray(cells) ? cells : [cells];
  if (!entry || entry.v !== 1 || !allowed.includes(entry.cell) || typeof entry.pub !== 'string' || typeof entry.sig !== 'string') return null;
  if (typeof entry.ts !== 'number' || Math.abs(now - entry.ts) > FRESH_MS) return null;
  const links = cleanLinks(entry.links);
  const repo = typeof entry.repo === 'string' && /^[\w.-]{1,60}\/[\w.-]{1,100}$/.test(entry.repo) ? entry.repo : '';
  const score = Number.isFinite(entry.score) ? entry.score : 0, laps = Number.isFinite(entry.laps) ? entry.laps : 0;
  const clean = { v: 1, cell: entry.cell, repo, links, score, laps, ts: entry.ts, pub: entry.pub };
  if (!verifyHex(messageOf(entry), entry.sig, entry.pub)) return null;
  return { ...clean, sig: entry.sig };
}


/** A card read from a QR code or a pasted text: { repo, links }, links cleaned; or a single https link; or null. Unsigned: it is shown as « from a code », never as a ranked person. */
export function parseCard(text) {
  const raw = String(text ?? '').trim();
  if (!raw || raw.length > 1500) return null;
  if (raw.startsWith('{')) {
    try {
      const card = JSON.parse(raw);
      const links = cleanLinks(card.links);
      const repo = typeof card.repo === 'string' && /^[\w.-]{1,60}\/[\w.-]{1,100}$/.test(card.repo) ? card.repo : '';
      return links.length ? { repo, links } : null;
    } catch { return null; }
  }
  try {
    const url = new URL(raw);
    if (url.protocol !== 'https:') return null;
    return { repo: '', links: [{ kind: 'page', url: url.href }] };
  } catch { return null; }
}

/** The text of my QR code: my repository and my links, short. */
export const cardText = (repo, links) => JSON.stringify({ v: 1, repo, links: cleanLinks(links) });
