import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, writeFileSync, existsSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import {
  processSubmissionFile, buildAppPackage, buildRefreshAuthorization, verifyAppPackage, bundleHashOf, compareVersions,
  validateSubmission, applyAccepted, emptyStore, readStore, writeStore, rankApps, ratioOf, POLICY,
} from '../src/index.js';
import { CREATOR, deployment, fakeSolana, minedWallet, HTML } from '../support/helpers.mjs';

const T0 = Date.now();
const publish = async (aiwa, fields, extra = {}) => ({
  format: 'aiwa-submission/1', kind: 'publish',
  package: await buildAppPackage(aiwa.identity, { id: 'hello', name: 'Hello', version: '1.0.0', description: 'says hello', html: HTML(), ...fields }),
  evidence: await aiwa.submissionEvidence(),
  ...extra,
});
const accept = async (submission, store, connection, now) => {
  const result = await validateSubmission({ submission, store, deployment, connection, now: T0 + now });
  assert.equal(result.ok, true, result.reason);
  return { result, store: applyAccepted(store, result.accepted) };
};

test('a package is what its author signed: the hash covers the content, the signature covers (app, version, hash)', async () => {
  const connection = fakeSolana();
  const alice = await minedWallet(connection);
  const pkg = await buildAppPackage(alice.identity, { id: 'hello', name: 'Hello', version: '1.0.0', html: HTML() });
  assert.equal((await verifyAppPackage(pkg)).ok, true);
  assert.equal((await verifyAppPackage(pkg)).domain, alice.identity.id);
  assert.equal(pkg.author, alice.address);

  const swapped = { ...pkg, html: HTML('evil') };
  assert.match((await verifyAppPackage(swapped)).reason, /hash does not match/);
  const rehashed = { ...swapped, bundleHash: bundleHashOf(swapped) };
  assert.match((await verifyAppPackage(rehashed)).reason, /not for this app, this version and this content/, 'a new hash needs a new signature');

  const bob = await minedWallet(connection);
  const impostor = { ...pkg, author: bob.address };
  assert.match((await verifyAppPackage(impostor)).reason, /not the author's address/);
  assert.match((await verifyAppPackage({ ...pkg, id: 'Bad Id' })).reason, /id is 1 to 40/);
  assert.match((await verifyAppPackage({ ...pkg, html: 'x'.repeat(600 * 1024) })).reason, /larger than 512 KB/);
});

test('compareVersions orders major.minor.patch numerically', () => {
  assert.ok(compareVersions('1.10.0', '1.9.0') > 0);
  assert.ok(compareVersions('2.0.0', '10.0.0') < 0);
  assert.equal(compareVersions('1.2.3', '1.2.3'), 0);
});

test('a first publication: evidence confirmed against Solana, the figure frozen as score / laps, the package kept', async () => {
  const connection = fakeSolana();
  const alice = await minedWallet(connection, { T: 0.4 });          // the burn paid the creator in the same transaction
  const { result, store } = await accept(await publish(alice), emptyStore(), connection, 1_000);
  const entry = store.index.apps[0];
  assert.equal(entry.id, 'hello');
  assert.equal(entry.author, alice.address);
  assert.ok(entry.score > 0 && entry.laps >= 1);
  assert.equal(entry.path, 'apps/hello/1.0.0.json');
  assert.equal(entry.publishedAt, T0 + 1_000);
  assert.equal(store.baselines[alice.address].domain, alice.identity.id, 'the registry keeps what it derived, to continue from');
  assert.equal(result.accepted.package.bundleHash, entry.bundleHash);
});

test('the creator fee is enforced by the registry: a commitment at T > 0 whose burn did not pay the creator counts no position', async () => {
  const connection = fakeSolana();
  const { AIWA } = await import('aiwa-lib');
  const noFee = { ...deployment.rewardParams, creatorFee: undefined };
  const plain = new AIWA({ rewardParams: noFee });                 // a wallet that knows no creator fee
  await plain.connect();
  const signature = await plain.burn(1e9, connection, { T: 0 });  // 1 SOL, all of it to the incinerator
  const evader = async (T, b) => {
    const wallet = new AIWA({ rewardParams: noFee });
    await wallet.connect({ secretKeyBytes: plain.keypair.secretKey });
    await wallet.recordBurn(signature, connection);
    await wallet.recordCommitment({ b, T });                         // that wallet accepts it: it asks nothing of the creator
    await wallet.advanceProgress({ epochs: 3 });
    return wallet;
  };

  const control = await validateSubmission({ submission: await publish(await evader(0, 1)), store: emptyStore(), deployment, connection, now: T0 });
  assert.equal(control.ok, true, `at T = 0 nothing is owed: ${control.reason}`);

  const cheat = await validateSubmission({ submission: await publish(await evader(0.4, 0.6)), store: emptyStore(), deployment, connection, now: T0 });
  assert.equal(cheat.ok, false);
  assert.match(cheat.reason, /No position/);
});

test('an author cannot publish what it did not sign, and cannot take over another author\'s id', async () => {
  const connection = fakeSolana();
  const alice = await minedWallet(connection);
  const bob = await minedWallet(connection);
  let { store } = await accept(await publish(alice), emptyStore(), connection, 1_000);

  // Bob signs a package under Alice's id
  const taken = await validateSubmission({ submission: await publish(bob), store, deployment, connection, now: T0 + 1_000 + POLICY.minGapBetweenNewAppsMs + 1 });
  assert.equal(taken.ok, false);
  assert.match(taken.reason, /belongs to another author/);

  // Bob takes Alice's package and puts his own evidence next to it
  const stolen = { ...(await publish(alice, { version: '1.1.0' })), evidence: await bob.submissionEvidence() };
  const stolenResult = await validateSubmission({ submission: stolen, store, deployment, connection, now: T0 + 2_000 });
  assert.equal(stolenResult.ok, false);
  assert.match(stolenResult.reason, /evidence is not for this domain/, 'the evidence is not for the author of the package');
});

test('an update needs ownership and a higher version, no new evidence; with evidence it refreshes the figure', async () => {
  const connection = fakeSolana();
  const alice = await minedWallet(connection);
  let { store } = await accept(await publish(alice), emptyStore(), connection, 1_000);
  const before = store.index.apps[0];

  const same = await validateSubmission({ submission: { ...(await publish(alice)), evidence: undefined }, store, deployment, connection, now: T0 + 2_000 });
  assert.match(same.reason, /version must be higher than 1.0.0/);

  const update = { ...(await publish(alice, { version: '1.1.0', html: HTML('v2') })), evidence: undefined };
  ({ store } = await accept(update, store, connection, 3_000));
  const after = store.index.apps[0];
  assert.equal(after.version, '1.1.0');
  assert.equal(after.path, 'apps/hello/1.1.0.json');
  assert.equal(after.publishedAt, before.publishedAt, 'still the same app');
  assert.equal(after.score, before.score, 'no evidence: the figure is the one already frozen');

  await alice.advanceProgress({ epochs: 2 });
  const refreshed = await publish(alice, { version: '1.2.0' }, { evidence: await alice.submissionEvidence({ afterEpoch: store.baselines[alice.address].epoch, after: store.baselines[alice.address].head }) });
  ({ store } = await accept(refreshed, store, connection, 4_000));
  assert.ok(store.index.apps[0].laps > before.laps, 'the figure was re-read from the new evidence');
});

test('a stale or future authorization is refused (a signature is not replayed later)', async () => {
  const connection = fakeSolana();
  const alice = await minedWallet(connection);
  const submission = await publish(alice);
  const later = submission.package.authorization.timestamp + POLICY.maxAuthorizationAgeMs + 1;
  const stale = await validateSubmission({ submission, store: emptyStore(), deployment, connection, now: later });
  assert.match(stale.reason, /too old/);
});

test('a new app needs something claimable; one per author every five minutes; and a score / laps not below the last publication\'s', async () => {
  const connection = fakeSolana();
  const nothing = await minedWallet(connection, { epochs: 0 });      // burned, but no epoch mined: nothing claimable
  const refused = await validateSubmission({ submission: await publish(nothing), store: emptyStore(), deployment, connection, now: T0 + 1_000 });
  assert.equal(refused.ok, false);
  assert.match(refused.reason, /Nothing claimable yet/);

  const alice = await minedWallet(connection, { epochs: 3 });
  const { store } = await accept(await publish(alice), emptyStore(), connection, 1_000);
  const second = await publish(alice, { id: 'second', name: 'Second' });
  const later = T0 + 1_000 + POLICY.minGapBetweenNewAppsMs + 1;

  const tooSoon = await validateSubmission({ submission: second, store, deployment, connection, now: T0 + 2_000 });
  assert.match(tooSoon.reason, /One new app per author every five minutes/);

  // the last publication was ranked far above what Alice's figure is now: a new app is refused
  const high = structuredClone(store);
  high.index.apps[0].score = 1e9;
  const lowRatio = await validateSubmission({ submission: second, store: high, deployment, connection, now: later });
  assert.equal(lowRatio.ok, false);
  assert.match(lowRatio.reason, /Claimable per lap too low/);

  // below it by nothing: accepted
  const low = structuredClone(store);
  low.index.apps[0].score = 1e-12;
  const fine = await validateSubmission({ submission: second, store: low, deployment, connection, now: later });
  assert.equal(fine.ok, true, fine.reason);
});

test('a refresh is signed by the author, for one app, and brings the figure up to date', async () => {
  const connection = fakeSolana();
  const alice = await minedWallet(connection);
  let { store } = await accept(await publish(alice), emptyStore(), connection, 1_000);
  const first = store.index.apps[0];
  await alice.advanceProgress({ epochs: 4 });
  const evidence = await alice.submissionEvidence({ afterEpoch: store.baselines[alice.address].epoch, after: store.baselines[alice.address].head });
  const refresh = { format: 'aiwa-submission/1', kind: 'refresh', appId: 'hello', authorization: await buildRefreshAuthorization(alice.identity, { id: 'hello' }), evidence };
  ({ store } = await accept(refresh, store, connection, 5_000));
  assert.ok(store.index.apps[0].laps > first.laps);
  assert.equal(store.index.apps[0].updatedAt, T0 + 5_000);

  const bob = await minedWallet(connection);
  const impostor = { ...refresh, authorization: await buildRefreshAuthorization(bob.identity, { id: 'hello' }), evidence: await bob.submissionEvidence() };
  assert.match((await validateSubmission({ submission: impostor, store, deployment, connection, now: T0 + 6_000 })).reason, /not the app's author/);
  assert.match((await validateSubmission({ submission: { ...refresh, appId: 'nope' }, store, deployment, connection, now: T0 + 6_000 })).reason, /No such app/);
});

test('evidence that leaves out a witnessed event is refused: what other wallets hold of an author must appear in what it shows', async () => {
  const connection = fakeSolana();
  const alice = await minedWallet(connection, { epochs: 2 });
  const evidence = await alice.submissionEvidence();
  const lastProgress = [...evidence.events].reverse().find((e) => e.type === 'progression');
  let store = emptyStore();
  store.witnesses = { [alice.identity.id]: [{ id: 'f'.repeat(64), epoch: lastProgress.payload.epoch + 5 }] };   // someone holds a later event of Alice's
  const result = await validateSubmission({ submission: await publish(alice), store, deployment, connection, now: T0 + 1_000 });
  assert.equal(result.ok, false);
  assert.match(result.reason, /Another holder of this domain's events/);
});

test('ranking is score / laps, best first; a tie goes to the app published first', () => {
  const apps = [
    { id: 'c', score: 4, laps: 2, publishedAt: 3 },      // 2
    { id: 'a', score: 9, laps: 3, publishedAt: 2 },      // 3
    { id: 'b', score: 6, laps: 2, publishedAt: 1 },      // 3, published first
    { id: 'd', score: 0, laps: 0, publishedAt: 0 },      // laps floors at 1: 0
  ];
  assert.deepEqual(rankApps(apps).map((a) => a.id), ['b', 'a', 'c', 'd']);
  assert.equal(ratioOf({ score: 5, laps: 0 }), 5);
  assert.deepEqual(apps.map((a) => a.id), ['c', 'a', 'b', 'd'], 'the input is not changed');
});

test('the files: index (best first), the package under apps/, internal state kept; read back the same', async () => {
  const connection = fakeSolana();
  const alice = await minedWallet(connection);
  const { result, store } = await accept(await publish(alice), emptyStore(), connection, 1_000);
  const dir = mkdtempSync(join(tmpdir(), 'store-'));
  writeStore(dir, store, result.accepted);
  assert.ok(existsSync(join(dir, 'apps/hello/1.0.0.json')));
  const again = readStore(dir);
  assert.equal(again.index.apps[0].id, 'hello');
  assert.deepEqual(again.baselines, JSON.parse(JSON.stringify(store.baselines)));
  const pkg = JSON.parse(readFileSync(join(dir, 'apps/hello/1.0.0.json'), 'utf8'));
  assert.equal((await verifyAppPackage(pkg)).ok, true, 'the kept file still verifies on its own');
});

test('the command\'s function: a submission file is read, refused with a reason or accepted and written under store/', async () => {
  const connection = fakeSolana();
  const alice = await minedWallet(connection);
  const dir = mkdtempSync(join(tmpdir(), 'process-'));
  const file = join(dir, 'submission.json');
  const storeDir = join(dir, 'store');

  const { symlinkSync } = await import('node:fs');
  symlinkSync(join(dir, 'elsewhere.json'), join(dir, 'link.json'));
  assert.equal((await processSubmissionFile({ file: join(dir, 'link.json'), storeDir, deployment, connection })).code, 1, 'a symbolic link is not a submission');

  writeFileSync(file, 'not json');
  assert.deepEqual(await processSubmissionFile({ file, storeDir, deployment, connection }), { code: 1, message: 'REFUSED: the submission is not JSON' });

  const submission = await publish(alice);
  writeFileSync(file, JSON.stringify({ ...submission, package: { ...submission.package, html: HTML('swapped') } }));
  const refused = await processSubmissionFile({ file, storeDir, deployment, connection });
  assert.equal(refused.code, 1);
  assert.match(refused.message, /hash does not match/);
  assert.equal(existsSync(join(storeDir, 'index.json')), false, 'nothing written when refused');

  writeFileSync(file, JSON.stringify(submission));
  const accepted = await processSubmissionFile({ file, storeDir, deployment, connection });
  assert.equal(accepted.code, 0, accepted.message);
  assert.match(accepted.message, /^ACCEPTED: publish hello 1\.0\.0/);
  assert.ok(existsSync(join(storeDir, 'apps/hello/1.0.0.json')));
  assert.equal(JSON.parse(readFileSync(join(storeDir, 'index.json'), 'utf8')).apps[0].id, 'hello');
});

// --- the second kind of app: a pointer to code published through Aiwa ---------------------------------------------------

import { buildBundle, verifyBundle, filesProblem } from '../src/bundle.js';

const FILES = [
  { path: 'index.html', content: '<!doctype html><html><head><link rel="stylesheet" href="style.css"></head><body><h1>Tally</h1><script src="app.js"></script></body></html>' },
  { path: 'style.css', content: 'h1 { color: rebeccapurple; }' },
  { path: 'app.js', content: 'document.title = "Tally";' },
];
const publishAiwa = async (aiwa, { files = FILES, version = '1.0.0', id = 'tally' } = {}) => {
  const { manifestId, bundle } = await buildBundle(aiwa.identity, { name: 'Tally', version, files });
  return {
    format: 'aiwa-submission/1', kind: 'publish',
    package: await buildAppPackage(aiwa.identity, { id, name: 'Tally', version, description: 'counts', manifestId }),
    bundle,
    evidence: await aiwa.submissionEvidence(),
  };
};

test('an app of kind aiwa carries a pointer, not code: the package is signed over the manifest id', async () => {
  const connection = fakeSolana();
  const alice = await minedWallet(connection);
  const { package: pkg } = await publishAiwa(alice);
  assert.equal(pkg.kind, 'aiwa');
  assert.equal(pkg.html, undefined);
  assert.match(pkg.manifestId, /^[0-9a-f]{64}$/);
  assert.equal((await verifyAppPackage(pkg)).ok, true);
  const repointed = { ...pkg, manifestId: 'a'.repeat(64) };
  assert.match((await verifyAppPackage(repointed)).reason, /hash does not match/, 'the pointer cannot be moved without the author');
  assert.match((await verifyAppPackage({ ...pkg, manifestId: 'nope' })).reason, /64 hexadecimal/);
});

test('an app of kind aiwa is accepted with its bundle, and the registry keeps both', async () => {
  const connection = fakeSolana();
  const alice = await minedWallet(connection);
  const { result, store } = await accept(await publishAiwa(alice), emptyStore(), connection, 1_000);
  const entry = store.index.apps[0];
  assert.equal(entry.kind, 'aiwa');
  assert.equal(entry.manifestId, result.accepted.package.manifestId);
  assert.equal(entry.bundlePath, 'apps/tally/1.0.0.bundle.json');

  const dir = mkdtempSync(join(tmpdir(), 'store-'));
  writeStore(dir, store, result.accepted);
  const kept = JSON.parse(readFileSync(join(dir, entry.bundlePath), 'utf8'));
  const verified = await verifyBundle(kept, { manifestId: entry.manifestId, domain: alice.identity.id, name: 'Tally', version: '1.0.0' });
  assert.equal(verified.ok, true, verified.reason);
  assert.deepEqual(verified.files, Object.fromEntries(FILES.map((f) => [f.path, f.content])), 'the kept events give back exactly the files');
});

test('a bundle is checked against what the package pins: the manifest, its author, its files, and nothing else', async () => {
  const connection = fakeSolana();
  const alice = await minedWallet(connection);
  const bob = await minedWallet(connection);
  const good = await publishAiwa(alice);
  const run = (submission) => validateSubmission({ submission, store: emptyStore(), deployment, connection, now: T0 + 1_000 });

  assert.match((await run({ ...good, bundle: undefined })).reason, /Not an Aiwa bundle/);

  // another author's bundle under Alice's pointer
  const theirs = await buildBundle(bob.identity, { name: 'Tally', version: '1.0.0', files: FILES });
  assert.match((await run({ ...good, bundle: theirs.bundle })).reason, /pinned manifest is not in the bundle/);

  // Alice's pointer to a manifest Bob signed
  const pointsAtBob = { ...good, package: await buildAppPackage(alice.identity, { id: 'tally', name: 'Tally', version: '1.0.0', description: 'counts', manifestId: theirs.manifestId }), bundle: theirs.bundle };
  assert.match((await run(pointsAtBob)).reason, /not signed by the app's author/);

  // a file changed after signing: Aiwa itself refuses the event
  const events = structuredClone(good.bundle.events);
  const file = events.find((e) => e.type === 'bundle.file' && e.payload.path === 'app.js');
  file.payload.content = 'steal()';
  assert.match((await run({ ...good, bundle: { ...good.bundle, events } })).reason, /Aiwa refuses these events/);

  // an event nobody listed
  const extra = await buildBundle(alice.identity, { name: 'Tally', version: '1.0.0', files: [...FILES, { path: 'more.js', content: 'x' }] });
  const padded = { ...good.bundle, events: [...good.bundle.events, extra.bundle.events.find((e) => e.payload.path === 'more.js')] };
  assert.match((await run({ ...good, bundle: padded })).reason, /does not list/);

  // the manifest names another version than the package
  const other = await buildBundle(alice.identity, { name: 'Tally', version: '2.0.0', files: FILES });
  const mismatched = { ...good, package: await buildAppPackage(alice.identity, { id: 'tally', name: 'Tally', version: '1.0.0', description: 'counts', manifestId: other.manifestId }), bundle: other.bundle };
  assert.match((await run(mismatched)).reason, /another name or version/);
});

test('the files of an app: an index, safe names, a size', async () => {
  assert.equal(filesProblem(FILES), null);
  assert.match(filesProblem([{ path: 'a.js', content: '' }]), /no index\.html/);
  assert.match(filesProblem([...FILES, { path: '../x', content: '' }]), /not allowed/);
  assert.match(filesProblem([...FILES, { path: '/etc/passwd', content: '' }]), /not allowed/);
  assert.match(filesProblem([...FILES, FILES[0]]), /twice/);
  assert.match(filesProblem([{ path: 'index.html', content: 'x'.repeat(1100 * 1024) }]), /larger than 1024 KB/);
  assert.match(filesProblem([]), /no files/);
});
