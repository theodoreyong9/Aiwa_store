import { test } from 'node:test';
import assert from 'node:assert/strict';

// github.js talks to the page's host channel only to log in; the pull request itself is plain REST, tested here against a
// stand-in for GitHub that keeps what it is given.
globalThis.window = {};
const { openPullRequest } = await import('../src/github.js');
const { nextVersion, slug, sendSubmission } = await import('../src/publish.js');

function fakeGitHub({ login = 'alice', forkReadyAfter = 0 } = {}) {
  const calls = [];
  const files = {};
  let forkChecks = 0;
  const fetchFn = async (url, { method = 'GET', body, headers } = {}) => {
    const path = new URL(url).pathname;
    const data = body ? JSON.parse(body) : null;
    calls.push({ method, path, data, auth: headers.Authorization });
    const reply = (value, status = 200) => ({ ok: status < 400, status, json: async () => value });
    if (path === '/user') return reply({ login });
    if (path === '/repos/org/store' && method === 'GET') return reply({ default_branch: 'main' });
    if (path === '/repos/org/store/git/ref/heads/main') return reply({ object: { sha: 'base-sha' } });
    if (path === '/repos/org/store/forks') return reply({ full_name: `${login}/store` }, 202);
    if (path === `/repos/${login}/store` && method === 'GET') return ++forkChecks > forkReadyAfter ? reply({}) : reply({ message: 'Not Found' }, 404);
    if (path.endsWith('/git/refs')) return reply({}, 201);
    if (path.includes('/contents/')) { files[decodeURIComponent(path.split('/contents/')[1])] = Buffer.from(data.content, 'base64').toString('utf8'); return reply({}, 201); }
    if (path === '/repos/org/store/pulls') return reply({ html_url: 'https://github.com/org/store/pull/7', number: 7 }, 201);
    return reply({ message: `unexpected ${method} ${path}` }, 500);
  };
  return { fetchFn, calls, files };
}

test('a pull request from the author\'s fork: forked, branched from the registry\'s main, the file committed, the pull request opened', async () => {
  const gh = fakeGitHub({ forkReadyAfter: 2 });
  const steps = [];
  const pull = await openPullRequest({
    token: 't0ken', upstream: 'org/store', path: 'submissions/a-1.0.0.json', text: '{"é":"ü"}', title: 'Publish a', body: 'b',
    fetchFn: gh.fetchFn, wait: async () => {}, onStep: (s) => steps.push(s),
  });
  assert.deepEqual(pull, { url: 'https://github.com/org/store/pull/7', number: 7 });
  assert.ok(gh.calls.every((c) => c.auth === 'Bearer t0ken'));
  const refs = gh.calls.find((c) => c.path === '/repos/alice/store/git/refs');
  assert.equal(refs.data.sha, 'base-sha', 'the branch starts from the registry\'s main, not from a possibly stale fork');
  assert.match(refs.data.ref, /^refs\/heads\/submit\//);
  assert.equal(gh.files['submissions/a-1.0.0.json'], '{"é":"ü"}', 'the file arrives byte for byte, accents included');
  const opened = gh.calls.find((c) => c.path === '/repos/org/store/pulls').data;
  assert.equal(opened.head, `alice:${refs.data.ref.slice('refs/heads/'.length)}`);
  assert.equal(opened.base, 'main');
  assert.equal(gh.calls.filter((c) => c.path === '/repos/alice/store').length, 3, 'it waited for the fork, which is made in the background');
  assert.deepEqual(steps, ['Copying the registry to your GitHub account', 'Preparing the pull request', 'Opening the pull request']);
});

test('the owner of the registry needs no fork: the branch goes on the repository itself', async () => {
  const gh = fakeGitHub({ login: 'org' });
  await openPullRequest({ token: 't', upstream: 'org/store', path: 'submissions/x.json', text: '{}', title: 't', body: 'b', fetchFn: gh.fetchFn, wait: async () => {} });
  assert.ok(!gh.calls.some((c) => c.path.endsWith('/forks')));
  assert.ok(gh.calls.some((c) => c.path === '/repos/org/store/git/refs'));
});

test('a refusal from GitHub carries its reason and status', async () => {
  const fetchFn = async () => ({ ok: false, status: 403, json: async () => ({ message: 'Resource not accessible' }) });
  await assert.rejects(openPullRequest({ token: 't', upstream: 'org/store', path: 'p', text: '', title: 't', body: 'b', fetchFn }), (error) => error.status === 403 && /Resource not accessible/.test(error.message));
});

test('a submission is one file under submissions/, named after the app and version (the workflow takes exactly one)', async () => {
  const gh = fakeGitHub({ login: 'org' });
  const submission = { format: 'aiwa-submission/1', kind: 'publish', package: { id: 'tally', version: '1.2.3' }, evidence: {} };
  await sendSubmission({ token: 't', repository: 'org/store', submission, fetchFn: gh.fetchFn });
  assert.deepEqual(Object.keys(gh.files), ['submissions/tally-1.2.3.json']);
  assert.equal(gh.calls.find((c) => c.path === '/repos/org/store/pulls').data.title, 'Publish tally 1.2.3');
});

test('the next version is the author\'s own next patch, 1.0.0 for a new app; ids are slugs', () => {
  const apps = [{ id: 'tally', author: 'ME', version: '1.4.9' }, { id: 'tally', author: 'OTHER', version: '9.0.0' }];
  assert.equal(nextVersion(apps, { id: 'tally', author: 'ME' }), '1.4.10');
  assert.equal(nextVersion(apps, { id: 'fresh', author: 'ME' }), '1.0.0');
  assert.equal(slug('  Split the Bill! '), 'split-the-bill');
  assert.equal(slug('é'), '');
});
