// A new key for signing the site's releases: node scripts/site-key.mjs
// Prints the public key (deployment.json: "siteKey", and the APK is rebuilt with it) and the secret seed (the repository secret
// SITE_SIGNING_KEY: Settings → Secrets and variables → Actions). Anyone who has the seed can sign a page that the Android app will run.
import { newSiteKey } from '../apps/web/release.mjs';

const { seed, publicKey } = newSiteKey();
console.log(`public key (deployment.json, "siteKey"): ${publicKey}`);
console.log(`secret seed (the repository secret SITE_SIGNING_KEY): ${seed}`);
