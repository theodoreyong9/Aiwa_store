// Describes the built web app and signs the description: node scripts/sign-site.mjs apps/web/dist [--unsigned]
// Writes release.json (every file with its hash) and, with the secret SITE_SIGNING_KEY in the environment, release.sig. The
// Pages workflow signs; the APK build only describes (--unsigned): the copy inside the APK is trusted by the APK's own signature.
import { writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { buildRelease, signRelease, verifyRelease, RELEASE_FILE, SIGNATURE_FILE } from '../apps/web/release.mjs';
import deployment from '../deployment.json' with { type: 'json' };

const [dir, ...flags] = process.argv.slice(2);
if (!dir) { console.error('usage: node scripts/sign-site.mjs <dir> [--unsigned]'); process.exit(2); }
const root = resolve(dir);
const version = `${new Date().toISOString().slice(0, 19)}Z-${(process.env.GITHUB_SHA ?? 'local').slice(0, 7)}`;
const { bytes } = buildRelease(root, { version });
writeFileSync(join(root, RELEASE_FILE), bytes);

if (flags.includes('--unsigned')) { console.log(`${version}: described, not signed`); process.exit(0); }
const seed = process.env.SITE_SIGNING_KEY?.trim();
if (!seed) { console.error('SITE_SIGNING_KEY is not set: the release is described but not signed'); process.exit(3); }
if (!deployment.siteKey) { console.error('deployment.json has no siteKey'); process.exit(3); }
const signature = signRelease(bytes, seed);
verifyRelease(bytes, signature, deployment.siteKey);        // a seed that is not the site key's would sign something the phone refuses
writeFileSync(join(root, SIGNATURE_FILE), signature);
console.log(`${version}: signed (${Object.keys(JSON.parse(new TextDecoder().decode(bytes)).files).length} files)`);
