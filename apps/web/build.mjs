// Builds the web app into dist/: one ES module (app.js, with a lazily loaded chunk for @solana/web3.js and the QR
// library), the page, its style and its icons; and the Aiwa SDK as one module (lib/aiwa.js) for the apps published through Aiwa.
//
//   node build.mjs [--out dist] [--deployment ../../deployment.json]
//
// `--deployment` lets a test build use other parameters; the deployment's file is what '@deployment' resolves to.
import { build } from 'esbuild';
import { cpSync, mkdirSync, rmSync, readdirSync, statSync, copyFileSync } from 'node:fs';
import { resolve, dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const args = Object.fromEntries(process.argv.slice(2).reduce((pairs, a, i, all) => (a.startsWith('--') ? [...pairs, [a.slice(2), all[i + 1]]] : pairs), []));
const out = resolve(args.out ?? join(here, 'dist'));
const deployment = resolve(args.deployment ?? join(here, '../../deployment.json'));

rmSync(out, { recursive: true, force: true });
mkdirSync(out, { recursive: true });

await build({
  entryPoints: [join(here, 'src/app.js')],
  outdir: out,
  bundle: true,
  splitting: true,
  format: 'esm',
  platform: 'browser',
  target: 'es2022',
  minify: true,
  legalComments: 'none',
  logLevel: 'warning',
  alias: { '@deployment': deployment },
  define: { 'process.env.NODE_ENV': '"production"', global: 'globalThis' },
});

// The SDK is a separate build: an app imports it by its address, so it must stand alone.
await build({
  entryPoints: { aiwa: join(here, 'lib-entry.js') },
  outdir: join(out, 'lib'),
  bundle: true,
  format: 'esm',
  platform: 'browser',
  target: 'es2022',
  minify: true,
  legalComments: 'none',
  logLevel: 'warning',
  define: { 'process.env.NODE_ENV': '"production"', global: 'globalThis' },
});

copyFileSync(join(here, 'src/index.html'), join(out, 'index.html'));
copyFileSync(join(here, 'src/style.css'), join(out, 'style.css'));
cpSync(join(here, 'public'), out, { recursive: true });

let bytes = 0;
const walk = (dir) => { for (const f of readdirSync(dir)) { const p = join(dir, f); statSync(p).isDirectory() ? walk(p) : (bytes += statSync(p).size); } };
walk(out);
console.log(`${out}: ${Math.round(bytes / 1024)} KB`);
