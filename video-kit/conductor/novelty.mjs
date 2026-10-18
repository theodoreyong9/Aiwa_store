#!/usr/bin/env node
// novelty <concept.json> [--record]: a film must be NEW. Checks its concept against the registry of what the earlier films used.
//   concept.json: { id, language, metaphor, objects: [], places: [], transitions: [], tracking: "" }
// Errors (the film is not made): no metaphor; the language of one of the last five films; an object, a place or a transition also used by one of the last three films; the tracking style of one of the last two.
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
const here = dirname(fileURLToPath(import.meta.url));
const regFile = join(here, 'registry.json');
const reg = JSON.parse(readFileSync(regFile, 'utf8'));
const [file, ...flags] = process.argv.slice(2);
const c = JSON.parse(readFileSync(file, 'utf8'));
const norm = (s) => String(s).toLowerCase().trim();
const errors = [];
if (!c.metaphor || String(c.metaphor).trim().length < 40) errors.push('no metaphor: say in a sentence how the STRUCTURE of the subject becomes the structure of the pictures (not a theme laid on top)');
for (const k of ['language', 'tracking']) if (!c[k]) errors.push(`no ${k}`);
for (const k of ['objects', 'places', 'transitions']) if (!Array.isArray(c[k]) || !c[k].length) errors.push(`no ${k}`);
const others = reg.films.filter((f) => f.id !== c.id);
const last = (n) => others.slice(-n);
for (const f of last(5)) if (norm(f.language) === norm(c.language || '')) errors.push(`the language "${c.language}" is the one of ${f.id}`);
for (const f of last(3)) for (const k of ['objects', 'places', 'transitions']) {
  const used = new Set((f[k] || []).map(norm));
  for (const x of c[k] || []) if (used.has(norm(x))) errors.push(`${k}: "${x}" was already used by ${f.id}`);
}
for (const f of last(2)) if (norm(f.tracking) === norm(c.tracking || '')) errors.push(`the tracking style "${c.tracking}" is the one of ${f.id}`);
if (errors.length) { for (const e of errors) console.log('NOT NEW:', e); process.exit(1); }
console.log('new: ok');
if (flags.includes('--record')) { reg.films = others.concat([{ id: c.id, language: c.language, objects: c.objects, places: c.places, transitions: c.transitions, tracking: c.tracking }]); writeFileSync(regFile, JSON.stringify(reg, null, 1)); console.log('recorded', c.id); }
