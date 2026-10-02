// GitHub, for one thing: sending a submission to the registry as a pull request, from the author's own account.
//
// Logging in is GitHub's device flow, done by the Android app (GitHub's login endpoints answer no browser page): the page
// shows a short code, the author types it once on github.com, and the app keeps the token in the phone's keystore. The
// rest is GitHub's REST API, which a page may call: fork the registry's repository, put the file on a branch of the fork,
// open the pull request. Nothing here is the registry's decision: its workflow reads the file and answers on the pull request.
import { hostCall } from './host.js';
import { loadSecret, saveSecret, deleteSecret } from './keys.js';

const API = 'https://api.github.com';
const TOKEN = 'github-token';

async function api(token, path, { method = 'GET', body, fetchFn = fetch } = {}) {
  const response = await fetchFn(`${API}${path}`, {
    method,
    headers: { Accept: 'application/vnd.github+json', Authorization: `Bearer ${token}`, 'X-GitHub-Api-Version': '2022-11-28', ...(body ? { 'Content-Type': 'application/json' } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const error = new Error(`GitHub refused (${response.status}): ${data.message ?? 'no reason given'}`);
    error.status = response.status;
    throw error;
  }
  return data;
}

/** The login the token belongs to, or null if GitHub no longer accepts it. */
export async function userOf(token, { fetchFn } = {}) {
  try { return (await api(token, '/user', { fetchFn })).login; } catch (error) { if (error.status === 401) return null; throw error; }
}

/**
 * A token GitHub accepts: the one kept, or one from a new login. `onCode({ userCode, verificationUri })` is called
 * when the author has to type the code; the promise resolves once they have.
 */
export async function ensureToken({ clientId, onCode, fetchFn } = {}) {
  const kept = await loadSecret(TOKEN);
  if (kept && await userOf(kept, { fetchFn })) return kept;
  if (kept) await deleteSecret(TOKEN);
  if (!clientId) throw new Error('This deployment has no GitHub application to sign in with (deployment.json, github.clientId).');
  const { token } = await hostCall('github-login', { clientId, scope: 'public_repo' }, { onProgress: onCode });
  await saveSecret(TOKEN, token);
  return token;
}

export const forgetToken = () => deleteSecret(TOKEN);

function toBase64(text) {
  const bytes = new TextEncoder().encode(text);
  let binary = '';
  for (let i = 0; i < bytes.length; i += 0x8000) binary += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
  return btoa(binary);
}

const pause = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/**
 * Puts `text` at `path` on a new branch of the author's fork of `upstream` and opens a pull request for it.
 * @returns {Promise<{ url: string, number: number }>} the pull request
 */
export async function openPullRequest({ token, upstream, path, text, title, body, fetchFn, wait = pause, onStep = () => {} }) {
  const call = (route, options) => api(token, route, { ...options, fetchFn });
  const me = await call('/user').then((u) => u.login);
  const [owner] = upstream.split('/');

  let fork = upstream;
  if (me.toLowerCase() !== owner.toLowerCase()) {
    onStep('Copying the registry to your GitHub account');
    fork = (await call(`/repos/${upstream}/forks`, { method: 'POST', body: {} })).full_name;
    // a fork is made in the background: wait until it answers
    for (let tries = 0; ; tries++) {
      try { await call(`/repos/${fork}`); break; } catch (error) { if (error.status !== 404 || tries >= 40) throw error; await wait(1500); }
    }
  }

  onStep('Preparing the pull request');
  const base = (await call(`/repos/${upstream}`)).default_branch;
  const sha = (await call(`/repos/${upstream}/git/ref/heads/${base}`)).object.sha;
  const branch = `submit/${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 7)}`;
  await call(`/repos/${fork}/git/refs`, { method: 'POST', body: { ref: `refs/heads/${branch}`, sha } });
  await call(`/repos/${fork}/contents/${path}`, { method: 'PUT', body: { message: title, content: toBase64(text), branch } });

  onStep('Opening the pull request');
  const pull = await call(`/repos/${upstream}/pulls`, { method: 'POST', body: { title, body, head: `${fork.split('/')[0]}:${branch}`, base } });
  return { url: pull.html_url, number: pull.number };
}
