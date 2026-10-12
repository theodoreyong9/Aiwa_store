// Publishing, from the pieces: build what the registry takes (a signed package of either kind, with the author's mining
// evidence), and send it as a pull request. Nothing here decides anything: the registry's workflow does, on its own.
import { buildAppPackage, buildRefreshAuthorization } from 'aiwa-registry/app-package';
import { buildBundle } from 'aiwa-registry/bundle';
import { openPullRequest } from './github.js';

export const SUBMISSION_FORMAT = 'aiwa-submission/1';

export const slug = (text) => text.trim().toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 40).replace(/-+$/g, '');

/** The next patch version above the highest one this author already published under this id; 1.0.0 for a new app. */
export function nextVersion(apps, { id, author }) {
  const mine = apps.find((app) => app.id === id && app.author === author);
  if (!mine) return '1.0.0';
  const [major, minor, patch] = mine.version.split('.').map(Number);
  return `${major}.${minor}.${patch + 1}`;
}

/** What a wallet's mining evidence has to carry: only what the registry does not already hold, when it holds a baseline. */
export async function evidenceFor(aiwa, { registryUrl, fetchFn = fetch }) {
  try {
    const response = await fetchFn(`${registryUrl}/baselines/${aiwa.address}.json`, { cache: 'no-cache' });
    if (response.ok) {
      const baseline = await response.json();
      if (baseline?.domain === aiwa.identity.id && Number.isInteger(baseline.epoch)) return aiwa.submissionEvidence({ afterEpoch: baseline.epoch, after: baseline.head });
    }
  } catch { /* no baseline reachable: the whole history is the evidence */ }
  return aiwa.submissionEvidence();
}

/**
 * The submission for an app. `app` is { kind: 'code', id, name, version, description, html } or
 * { kind: 'aiwa', id, name, version, description, files: [{ path, content }] }.
 */
export async function buildSubmission(aiwa, app, evidence) {
  const { id, name, version, description } = app;
  if (app.kind === 'aiwa') {
    const { manifestId, bundle } = await buildBundle(aiwa.identity, { name, version, files: app.files });
    const pkg = await buildAppPackage(aiwa.identity, { id, name, version, description, manifestId });
    return { format: SUBMISSION_FORMAT, kind: 'publish', package: pkg, bundle, evidence };
  }
  const pkg = await buildAppPackage(aiwa.identity, { id, name, version, description, html: app.html });
  return { format: SUBMISSION_FORMAT, kind: 'publish', package: pkg, evidence };
}

export async function buildRefresh(aiwa, id, evidence) {
  return { format: SUBMISSION_FORMAT, kind: 'refresh', appId: id, authorization: await buildRefreshAuthorization(aiwa.identity, { id }), evidence };
}

/** The file name a submission gets in the pull request: one file, as the registry's workflow requires. */
export const submissionFileName = (submission) => (submission.kind === 'refresh'
  ? `${submission.appId}-refresh-${Date.now()}.json`
  : `${submission.package.id}-${submission.package.version}.json`);

/** Sends a submission to the registry's repository as a pull request. @returns {Promise<{ url: string }>} */
export function sendSubmission({ token, repository, submission, fetchFn, onStep }) {
  const name = submissionFileName(submission);
  const what = submission.kind === 'refresh' ? `Refresh ${submission.appId}` : `Publish ${submission.package.id} ${submission.package.version}`;
  return openPullRequest({
    token, upstream: repository, path: `submissions/${name}`, text: JSON.stringify(submission), fetchFn, onStep,
    title: what, body: `${what}, sent from the Aiwa Store. The registry's workflow checks it and answers here.`,
  });
}
