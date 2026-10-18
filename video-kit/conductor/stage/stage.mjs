#!/usr/bin/env node
// stage <plan.json> <workdir>: builds <workdir>/stage/index.html from the generic stage, for `video-kit render`. The clips are read from <workdir>/footage/<plate>.mp4 and cut into pictures in
// <workdir>/plates/<plate>/; a clip that is missing is NOT replaced by anything: the script says which ones to fetch (the `Footage` workflow, items "<name>=<spec>") and stops.
import { readFileSync, writeFileSync, mkdirSync, existsSync, copyFileSync, readdirSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { join, dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
const here = dirname(fileURLToPath(import.meta.url));
const [planFile, workArg, filmFile] = process.argv.slice(2);
const work = resolve(workArg);
const plan = JSON.parse(readFileSync(planFile, 'utf8'));
const film = filmFile && existsSync(filmFile) ? JSON.parse(readFileSync(filmFile, 'utf8')) : { worlds: plan.worlds };
const worlds = {}, missing = [], credits = [];
for (const [id, w] of Object.entries(plan.worlds)) {
  if (w.kind !== 'plate') continue;
  const mp4 = join(work, 'footage', `${w.plate}.mp4`), dir = join(work, 'plates', w.plate);
  if (!existsSync(mp4)) { missing.push(`${w.plate}=${w.spec || '?'}`); continue; }
  if (!existsSync(join(dir, '00001.jpg'))) {
    mkdirSync(dir, { recursive: true });
    const r = spawnSync('ffmpeg', ['-v', 'error', '-i', mp4, '-vf', 'scale=-2:900,crop=1600:900', '-q:v', '3', join(dir, '%05d.jpg')]);
    if (r.status) { console.error('ffmpeg failed for', w.plate); process.exit(1); }
  }
  const first = Math.min(...plan.beats.filter((b) => b.world === id).map((b) => b.t0));
  worlds[id] = { dir: 'file://' + dir, n: readdirSync(dir).filter((f) => f.endsWith('.jpg')).length, fps: 30, from: Math.max(0, first - 1.6) };
  const j = join(work, 'footage', `${w.plate}.json`);
  if (existsSync(j)) { try { const c = (JSON.parse(readFileSync(j, 'utf8')).credit || '').replace(' · Wikimedia Commons', ''); if (c && !credits.includes(c)) credits.push(c); } catch {} }
}
if (missing.length) { console.error('MISSING CLIPS: run the Footage workflow (mode fetch) with items:\n' + missing.join(' ')); process.exit(2); }
const cards = {};
for (const b of plan.beats) if (b.image && existsSync(b.image)) cards[b.image] = 'data:image/' + (b.image.split('.').pop().replace('jpg', 'jpeg')) + ';base64,' + readFileSync(b.image).toString('base64');
const out = join(work, 'stage'); mkdirSync(join(out, 'fonts'), { recursive: true });
for (const f of ['SpaceGrotesk.ttf', 'Fraunces-Italic.ttf', 'JetBrainsMono.ttf']) copyFileSync(join(here, 'fonts', f), join(out, 'fonts', f));
let html = readFileSync(join(here, 'stage.html'), 'utf8');
html = html.replace('__PLAN__', () => JSON.stringify(plan)).replace('__WORLDS__', () => JSON.stringify(worlds)).replace('__CARDS__', () => JSON.stringify(cards)).replace('__CREDITS__', () => JSON.stringify(credits));
writeFileSync(join(out, 'index.html'), html); console.log('stage:', join(out, 'index.html'));
