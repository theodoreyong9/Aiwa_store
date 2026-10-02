// Documentation checks that nothing else would catch:
//   1. every ```mermaid block of the README and docs/*.md renders (a diagram with a syntax error shows as a red box on GitHub);
//   2. every "§N.M" of the documents, and every "yellow paper §N.M" of the source comments, names a heading of the yellow paper.
// Run: node scripts/check-diagrams.mjs   (needs the Chromium of Playwright: npx -w aiwa-store-web playwright install chromium)

import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
// Playwright is the web app's dev dependency (its tests drive Chromium); resolve it from there.
const { chromium } = createRequire(join(root, 'apps/web/package.json'))('playwright');
const docs = ['README.md', ...readdirSync(join(root, 'docs')).filter((f) => f.endsWith('.md')).map((f) => `docs/${f}`)];
let failures = 0;
const fail = (message) => { failures++; console.log(`FAIL  ${message}`); };

// ---- 2. section references ----------------------------------------------------------------------------------------
const paper = readFileSync(join(root, 'docs/YELLOWPAPER.md'), 'utf8');
const headings = new Set([...paper.matchAll(/^#{2,4} (\d+(?:\.\d+)?)[. ]/gm)].map((m) => m[1]));
const appendices = new Set([...paper.matchAll(/^## Appendix ([A-Z])\./gm)].map((m) => m[1]));

function checkReferences(file, text, pattern) {
  text.split('\n').forEach((line, i) => {
    if (/^#{1,6} /.test(line) && file.endsWith('YELLOWPAPER.md')) return;
    for (const m of line.matchAll(pattern)) {
      if (!headings.has(m[1])) fail(`${file}:${i + 1} refers to §${m[1]}, which is not a heading of the yellow paper`);
    }
  });
}
for (const file of docs) {
  const text = readFileSync(join(root, file), 'utf8');
  // inside the yellow paper, any §N; elsewhere, only the ones said to be the yellow paper's
  checkReferences(file, text, file.endsWith('YELLOWPAPER.md') ? /§(\d+(?:\.\d+)?)/g : /yellow ?paper(?:\.md\]\([^)]*\))?,? §(\d+(?:\.\d+)?)/gi);
}
function* sources(dir) {
  for (const name of readdirSync(dir)) {
    if (['node_modules', '.git', 'dist', 'build'].includes(name)) continue;
    const path = join(dir, name);
    if (statSync(path).isDirectory()) yield* sources(path);
    else if (/\.(js|mjs|rs|kt|py|md)$/.test(name) && !['docs'].includes(relative(root, dir)) && name !== 'README.md') yield path;
  }
}
for (const path of sources(root)) {
  checkReferences(relative(root, path), readFileSync(path, 'utf8'), /yellow ?paper,? §(\d+(?:\.\d+)?)/gi);
}
for (const m of paper.matchAll(/Appendix ([A-Z])\b/g)) if (!appendices.has(m[1])) fail(`the yellow paper refers to Appendix ${m[1]}, which does not exist`);

// ---- 1. diagrams ---------------------------------------------------------------------------------------------------
const blocks = [];
for (const file of docs) {
  const text = readFileSync(join(root, file), 'utf8');
  for (const m of text.matchAll(/```mermaid\n([\s\S]*?)```/g)) {
    blocks.push({ file, line: text.slice(0, m.index).split('\n').length, source: m[1] });
  }
}

const browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || undefined });
const page = await browser.newPage();
await page.setContent('<!doctype html><body></body>');
await page.addScriptTag({ path: join(root, 'node_modules/mermaid/dist/mermaid.min.js') });
await page.evaluate(() => window.mermaid.initialize({ startOnLoad: false, securityLevel: 'strict' }));
for (const [i, block] of blocks.entries()) {
  const error = await page.evaluate(async ([id, source]) => {
    try { await window.mermaid.render(`d${id}`, source); return null; } catch (e) { return String(e.message || e).split('\n').slice(0, 3).join(' | '); }
  }, [i, block.source]);
  if (error) fail(`${block.file}:${block.line} the diagram does not render (${block.source.split('\n')[0].trim()}): ${error}`);
}
await browser.close();

console.log(`${blocks.length} diagrams, ${headings.size} yellow paper sections: ${failures === 0 ? 'all good' : `${failures} problem(s)`}`);
process.exit(failures === 0 ? 0 : 1);
