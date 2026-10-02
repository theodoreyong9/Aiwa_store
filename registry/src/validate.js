// Validating a submission to the store. Two kinds:
//
//   publish   a new app, or a higher version of the author's own: the package (app-package.js) and the author's mining
//             evidence (aiwa-lib's wallet.submissionEvidence()).
//   refresh   no new content: the author's signed request to re-read the ranking figure of one of its apps from fresh
//             evidence.
//
// Everything the protocol can check is aiwa-core's: assessSubmission verifies the evidence, confirms the burns
// against Solana ITSELF (the creator fee included, when the deployment has one), continues from the baseline this
// registry kept, and requires the witnesses other wallets hold. What stays here is the store's policy: who may publish
// what, and the permission ratio.
//
// The permission ratio (the one YourMine's registry applied): a NEW app is accepted only if the author's current
// score / laps is not below that of the author's last publication — what an author gets to publish does not shrink
// with what the author has contributed. A first publication is free of it. An update of an app needs only ownership.
//
// Pure with respect to files: it reads `store` ({ index, baselines, witnesses }), returns what to keep, and writes
// nothing (store-files.js does).

import { assessSubmission, ingestWitnesses, mergeWitnessStore, domainOfAddress } from 'aiwa-core';
import { verifyAppPackage, verifyRefreshAuthorization, compareVersions } from './app-package.js';
import { ratioOf } from './rank.js';

export const POLICY = {
  maxAuthorizationAgeMs: 24 * 60 * 60 * 1000,   // a signed package older than this is refused (a stale signature is not replayed)
  minGapBetweenNewAppsMs: 5 * 60 * 1000,        // one author, one new app per five minutes
  maxAppsPerAuthor: 20,
  maxSubmissionBytes: 40 * 1024 * 1024,
};

export const emptyStore = () => ({ index: { format: 'aiwa-store-index/1', apps: [] }, baselines: {}, witnesses: {} });

/** domain -> the epoch this registry already validated for it, from baselines kept by author address. */
export function baselineEpochOf(baselines) {
  const byDomain = {};
  for (const entry of Object.values(baselines ?? {})) if (entry && entry.domain) byDomain[entry.domain] = entry.epoch || 0;
  return (domain) => byDomain[domain] || 0;
}

const refuse = (reason) => ({ ok: false, reason });

async function assess({ deployment, evidence, author, domain, store, connection }) {
  if (!evidence) return { problem: 'No mining evidence with this submission' };
  const assessed = await assessSubmission({
    rewardParams: deployment.rewardParams, evidence, domain,
    baseline: store.baselines[author] ?? null, witnessed: store.witnesses[domain] ?? [], connection,
  });
  if (!assessed.ok) return { problem: assessed.reason };
  if (!assessed.mining) return { problem: 'No position: the burn is not confirmed, or nothing was committed' };
  return { assessed };
}

async function witnessesFrom(evidence, domain, store) {
  return ingestWitnesses({ witnesses: evidence?.witnesses, ownDomain: domain, baselineEpochOf: baselineEpochOf(store.baselines) });
}

/**
 * @param {object} args
 * @param {object} args.submission { format: 'aiwa-submission/1', kind: 'publish'|'refresh', package|appId+authorization, evidence }
 * @param {{ index: object, baselines: object, witnesses: object }} args.store
 * @param {object} args.deployment as loadDeployment() returns it
 * @param {{ getTransaction: Function }} args.connection Solana
 * @param {number} [args.now] the registry's clock, ms
 * @returns {Promise<{ ok: boolean, reason: string, accepted?: object }>} `accepted`: { kind, entry, package?, baseline, author, witnesses }
 */
export async function validateSubmission({ submission, store, deployment, connection, now = Date.now() }) {
  if (!submission || typeof submission !== 'object' || submission.format !== 'aiwa-submission/1') return refuse('Not a store submission (format aiwa-submission/1)');
  if (submission.kind === 'publish') return validatePublish({ submission, store, deployment, connection, now });
  if (submission.kind === 'refresh') return validateRefresh({ submission, store, deployment, connection, now });
  return refuse('The submission kind is publish or refresh');
}

async function validatePublish({ submission, store, deployment, connection, now }) {
  const pkg = submission.package;
  const verified = await verifyAppPackage(pkg);
  if (!verified.ok) return refuse(verified.reason);
  const domain = verified.domain;
  if (domain !== await domainOfAddress(pkg.author)) return refuse('The author\'s address and domain do not match');
  if (Math.abs(now - pkg.authorization.timestamp) > POLICY.maxAuthorizationAgeMs) return refuse('The author\'s signature is too old or dated in the future: sign again');

  const existing = store.index.apps.find((app) => app.id === pkg.id) ?? null;
  const mine = store.index.apps.filter((app) => app.author === pkg.author);
  if (existing) {
    if (existing.author !== pkg.author) return refuse(`The id ${pkg.id} belongs to another author`);
    if (compareVersions(pkg.version, existing.version) <= 0) return refuse(`The version must be higher than ${existing.version}`);
  } else {
    if (mine.length >= POLICY.maxAppsPerAuthor) return refuse(`An author has at most ${POLICY.maxAppsPerAuthor} apps`);
    const last = [...mine].sort((a, b) => b.publishedAt - a.publishedAt)[0];
    if (last && now - last.publishedAt < POLICY.minGapBetweenNewAppsMs) return refuse('One new app per author every five minutes');
  }

  // A new app needs evidence of mining (something claimable); an update may bring it, to refresh the figure.
  let ranking = existing ? { score: existing.score, laps: existing.laps } : null;
  let baseline = null;
  let witnesses = { accepted: [], ignored: 0 };
  if (!existing || submission.evidence) {
    const { problem, assessed } = await assess({ deployment, evidence: submission.evidence, author: pkg.author, domain, store, connection });
    if (problem) return refuse(problem);
    ranking = { score: Number(assessed.mining.claimable), laps: Math.max(1, assessed.mining.sinceLastAction) };
    baseline = assessed.baseline;
    witnesses = await witnessesFrom(submission.evidence, domain, store);
    if (!existing) {
      if (!(ranking.score > 0) || !Number.isFinite(ranking.score)) return refuse('Nothing claimable yet: mine for a while before publishing');
      const last = [...mine].sort((a, b) => b.publishedAt - a.publishedAt)[0];
      if (last && last.score > 0 && ratioOf(ranking) < ratioOf(last)) {
        return refuse(`Claimable per lap too low to publish a new app (${ratioOf(ranking).toFixed(6)} < ${ratioOf(last).toFixed(6)}, the last publication's)`);
      }
    }
  }

  const entry = {
    id: pkg.id, name: pkg.name, version: pkg.version, description: pkg.description,
    author: pkg.author, domain, bundleHash: pkg.bundleHash, path: `apps/${pkg.id}/${pkg.version}.json`,
    score: ranking.score, laps: ranking.laps,
    publishedAt: existing ? existing.publishedAt : now, updatedAt: now,
  };
  return { ok: true, reason: 'ok', accepted: { kind: 'publish', entry, package: pkg, baseline, author: pkg.author, domain, witnesses: witnesses.accepted } };
}

async function validateRefresh({ submission, store, deployment, connection, now }) {
  const existing = store.index.apps.find((app) => app.id === submission.appId) ?? null;
  if (!existing) return refuse('No such app');
  const verified = await verifyRefreshAuthorization(submission.authorization, { id: existing.id, author: existing.author });
  if (!verified.ok) return refuse(verified.reason);
  if (Math.abs(now - submission.authorization.timestamp) > POLICY.maxAuthorizationAgeMs) return refuse('The author\'s signature is too old or dated in the future: sign again');
  const { problem, assessed } = await assess({ deployment, evidence: submission.evidence, author: existing.author, domain: verified.domain, store, connection });
  if (problem) return refuse(problem);
  const entry = { ...existing, score: Number(assessed.mining.claimable), laps: Math.max(1, assessed.mining.sinceLastAction), updatedAt: now };
  const witnesses = await witnessesFrom(submission.evidence, verified.domain, store);
  return { ok: true, reason: 'ok', accepted: { kind: 'refresh', entry, baseline: assessed.baseline, author: existing.author, domain: verified.domain, witnesses: witnesses.accepted } };
}

/** The store after an accepted submission (pure): the entry replaced or added, the baseline and witnesses kept. */
export function applyAccepted(store, accepted) {
  const apps = store.index.apps.filter((app) => app.id !== accepted.entry.id);
  apps.push(accepted.entry);
  const baselines = { ...store.baselines };
  if (accepted.baseline) baselines[accepted.author] = accepted.baseline;
  const witnesses = mergeWitnessStore(store.witnesses, accepted.witnesses ?? [], baselineEpochOf(baselines));
  return { index: { ...store.index, apps }, baselines, witnesses };
}
