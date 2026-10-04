// Draws every Mermaid diagram of the documents as a PNG and puts the picture in the document, with the diagram's source folded under it.
//
// Why: Mermaid is drawn by the reader's viewer. GitHub's web page does it; a phone's file viewer, an editor's preview or a Markdown app
// often does not, and shows nothing (or the source). A picture shows everywhere.
//
//   node scripts/render-diagrams.mjs            redraws what changed, rewrites the documents, removes pictures no document uses
//   node scripts/render-diagrams.mjs --check    changes nothing; fails if a diagram has no picture, or its picture is of an older source
//
// A picture's name carries a hash of its source (yellowpaper-07-3fa91c2b.png), so a diagram that was edited has no picture until this runs.
// Needs the Chromium of Playwright (npx -w aiwa-store-web playwright install chromium), like check-diagrams.mjs.

import { readFileSync, writeFileSync, readdirSync, existsSync, mkdirSync, unlinkSync } from 'node:fs';
import { join, relative, dirname, basename, posix } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';
import { createRequire } from 'node:module';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const check = process.argv.includes('--check');
const IMG_DIR = join(root, 'docs/img/diagrams');

const documents = [
  'README.md',
  ...readdirSync(join(root, 'docs')).filter((f) => f.endsWith('.md')).map((f) => `docs/${f}`),
  ...readdirSync(join(root, 'packages')).map((d) => `packages/${d}/README.md`).filter((f) => existsSync(join(root, f))),
];
const slugOf = (file) => (file.startsWith('packages/') ? `pkg-${file.split('/')[1]}` : basename(file, '.md').toLowerCase());

const WRAPPED = /<!-- diagram:[^\n]*-->\n!\[[^\n]*\]\([^)\n]*\)\n\n<details>\n<summary>[^\n]*<\/summary>\n\n(```mermaid\n[\s\S]*?\n```)\n\n<\/details>\n<!-- \/diagram -->/g;
const BARE = /```mermaid\n([\s\S]*?)\n```/g;
const hash = (source) => createHash('sha256').update(source.trim()).digest('hex').slice(0, 8);

/** The document with every diagram back to a bare Mermaid block, and the list of the diagrams (source, nearest heading above). */
function readDiagrams(text) {
  const bare = text.replace(WRAPPED, '$1');
  const diagrams = [];
  const headings = [...bare.matchAll(/^#{1,4} (.+)$/gm)].map((m) => ({ at: m.index, title: m[1].replace(/[`*_\[\]]/g, '').trim() }));
  for (const m of bare.matchAll(BARE)) {
    const above = headings.filter((h) => h.at < m.index).pop();
    diagrams.push({ source: m[1], title: above ? above.title : 'Diagram' });
  }
  return { bare, diagrams };
}

/** Where the picture of a diagram is, seen from the document that shows it. */
function plan(file, diagrams) {
  const slug = slugOf(file);
  const toImages = posix.relative(posix.dirname(file), 'docs/img/diagrams');
  return diagrams.map((d, i) => {
    const name = `${slug}-${String(i + 1).padStart(2, '0')}-${hash(d.source)}.png`;
    return { ...d, name, path: join(IMG_DIR, name), href: `${toImages}/${name}` };
  });
}

function wrap(bare, planned) {
  let i = 0;
  return bare.replace(BARE, (block) => {
    const d = planned[i++];
    const alt = `Diagram: ${d.title}`.replace(/[\[\]]/g, '');
    return `<!-- diagram: ${d.name} -->\n![${alt}](${d.href})\n\n<details>\n<summary>The source of this diagram (Mermaid)</summary>\n\n${block}\n\n</details>\n<!-- /diagram -->`;
  });
}

const work = documents.map((file) => {
  const { bare, diagrams } = readDiagrams(readFileSync(join(root, file), 'utf8'));
  return { file, bare, planned: plan(file, diagrams) };
});

// ---- --check: no browser, nothing written -------------------------------------------------------------------------
if (check) {
  let failures = 0;
  for (const { file, planned } of work) {
    const text = readFileSync(join(root, file), 'utf8');
    for (const d of planned) {
      if (!existsSync(d.path)) { failures++; console.log(`FAIL  ${file}: "${d.title}" has no picture (${d.name}): run node scripts/render-diagrams.mjs`); continue; }
      if (!text.includes(`<!-- diagram: ${d.name} -->`)) { failures++; console.log(`FAIL  ${file}: "${d.title}" is not shown as its picture: run node scripts/render-diagrams.mjs`); }
    }
  }
  const used = new Set(work.flatMap(({ planned }) => planned.map((d) => d.name)));
  const extra = existsSync(IMG_DIR) ? readdirSync(IMG_DIR).filter((f) => f.endsWith('.png') && !used.has(f)) : [];
  for (const f of extra) { failures++; console.log(`FAIL  docs/img/diagrams/${f} belongs to no diagram: run node scripts/render-diagrams.mjs`); }
  console.log(`${work.reduce((n, w) => n + w.planned.length, 0)} diagrams, each with its picture: ${failures === 0 ? 'all good' : `${failures} problem(s)`}`);
  process.exit(failures === 0 ? 0 : 1);
}

// ---- draw what is missing -----------------------------------------------------------------------------------------
mkdirSync(IMG_DIR, { recursive: true });
const missing = work.flatMap(({ planned }) => planned).filter((d) => !existsSync(d.path));
if (missing.length) {
  const { chromium } = createRequire(join(root, 'apps/web/package.json'))('playwright');
  const browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || undefined });
  const context = await browser.newContext({ viewport: { width: 1600, height: 1000 }, deviceScaleFactor: 2 });
  const page = await context.newPage();
  await page.setContent('<!doctype html><body style="margin:0;background:#fff"><div id="stage" style="display:inline-block;padding:24px;background:#fff"></div></body>');
  await page.addScriptTag({ path: join(root, 'node_modules/mermaid/dist/mermaid.min.js') });
  await page.evaluate(() => window.mermaid.initialize({
    startOnLoad: false, securityLevel: 'strict', theme: 'default',
    flowchart: { useMaxWidth: false }, sequence: { useMaxWidth: false }, state: { useMaxWidth: false },
  }));
  let n = 0;
  for (const d of missing) {
    const error = await page.evaluate(async ([id, source]) => {
      try {
        const { svg } = await window.mermaid.render(`g${id}`, source);
        document.getElementById('stage').innerHTML = svg;
        return null;
      } catch (e) { return String(e.message || e).split('\n')[0]; }
    }, [n++, d.source]);
    if (error) { console.error(`FAIL  "${d.title}" does not render: ${error}`); process.exit(1); }
    await page.locator('#stage').screenshot({ path: d.path });
    console.log(`drawn ${d.name}`);
  }
  await browser.close();
}

// ---- put the pictures in the documents, and remove the pictures no document uses ---------------------------------
let changed = 0;
for (const { file, bare, planned } of work) {
  const next = planned.length ? wrap(bare, planned) : bare;
  if (next !== readFileSync(join(root, file), 'utf8')) { writeFileSync(join(root, file), next); changed++; }
}
const used = new Set(work.flatMap(({ planned }) => planned.map((d) => d.name)));
for (const f of readdirSync(IMG_DIR)) if (f.endsWith('.png') && !used.has(f)) { unlinkSync(join(IMG_DIR, f)); console.log(`removed ${f}`); }
console.log(`${used.size} diagrams, ${missing.length} drawn, ${changed} document(s) rewritten`);
