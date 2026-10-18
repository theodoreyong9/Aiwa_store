#!/usr/bin/env node
// ARVT film: the plan (video-kit/conductor) and the places (v32/hdri.py) go into the page of the plate. node build.mjs <plan.json> <hdri-dir> <out-dir>
import { readFileSync, writeFileSync, mkdirSync, cpSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
const here = dirname(fileURLToPath(import.meta.url));
const [planFile, hdriDir, out] = process.argv.slice(2);
const plan = JSON.parse(readFileSync(planFile, 'utf8'));
// the stage of each beat (camera, fields, place) sits beside its idea in film.json; the page reads it flat
plan.beats = plan.beats.map((b) => ({ ...b, ...(b.stage || {}) }));
const places = {};
for (const name of new Set(plan.beats.map((b) => b.place))) {
  const j = JSON.parse(readFileSync(join(hdriDir, `${name}.json`), 'utf8'));
  places[name] = { bg: 'data:image/jpeg;base64,' + readFileSync(join(hdriDir, `${name}.jpg`)).toString('base64'), anchors: j.anchors };
}
mkdirSync(out, { recursive: true }); cpSync(join(here, 'fonts'), join(out, 'fonts'), { recursive: true });
writeFileSync(join(out, 'index.html'), readFileSync(join(here, 'stage.html'), 'utf8').replace('__PLAN__', () => JSON.stringify(plan)).replace('__PLACES__', () => JSON.stringify(places)));
console.log('page:', join(out, 'index.html'));
