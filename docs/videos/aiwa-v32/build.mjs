#!/usr/bin/env node
// v32: the plan (video-kit/conductor) and the places (hdri.py: RGBE light + the picture + the anchors) go into the page of the shader world.
//   node docs/videos/aiwa-v32/build.mjs <plan.json> <hdri-dir> <out-dir>
import { readFileSync, writeFileSync, mkdirSync, cpSync, existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
const here = dirname(fileURLToPath(import.meta.url));
const [planFile, hdriDir, out] = process.argv.slice(2);
const plan = JSON.parse(readFileSync(planFile, 'utf8'));
const LABEL = { aarfontein_dawn_2: 'AARFONTEIN · AUBE', colorful_studio: 'STUDIO COULEUR', blaubeuren_night: 'PARC · NUIT', little_paris_eiffel_tower: 'PARIS · SOLEIL LEVANT', venice_sunset: 'VENISE · COUCHANT', tv_studio: 'STUDIO TV', kiara_1_dawn: 'KIARA · AUBE' };
const places = {};
for (const name of new Set(plan.beats.map((b) => b.place))) {
  const j = JSON.parse(readFileSync(join(hdriDir, `${name}.json`), 'utf8'));
  places[name] = { env: 'data:image/png;base64,' + readFileSync(join(hdriDir, `${name}.png`)).toString('base64'), bg: 'data:image/jpeg;base64,' + readFileSync(join(hdriDir, `${name}.jpg`)).toString('base64'), anchors: j.anchors, features: j.features, key: j.key, label: LABEL[name] || name.toUpperCase(), yaw: 0 };
}
mkdirSync(out, { recursive: true });
cpSync(join(here, 'fonts'), join(out, 'fonts'), { recursive: true });
writeFileSync(join(out, 'index.html'), readFileSync(join(here, 'stage.html'), 'utf8').replace('__PLAN__', () => JSON.stringify(plan)).replace('__PLACES__', () => JSON.stringify(places)));
console.log('page:', join(out, 'index.html'));
