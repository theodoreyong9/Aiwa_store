// Describes the built web app: node scripts/describe-site.mjs apps/web/dist
// Writes release.json (every file with its SHA-256, a version, a date) next to the files. The Pages workflow does it for the site,
// the Android workflow for the copy inside the APK, so that the app can tell which of the two is the newer.
import { writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { buildRelease, RELEASE_FILE } from '../apps/web/release.mjs';

const dir = process.argv[2];
if (!dir) { console.error('usage: node scripts/describe-site.mjs <dir>'); process.exit(2); }
const root = resolve(dir);
const version = `${new Date().toISOString().slice(0, 19)}Z-${(process.env.GITHUB_SHA ?? 'local').slice(0, 7)}`;
const { release, bytes } = buildRelease(root, { version });
writeFileSync(join(root, RELEASE_FILE), bytes);
console.log(`${version}: ${Object.keys(release.files).length} files described`);
