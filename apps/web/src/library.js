// The library: apps that live in their authors' OWN GitHub repositories. There is no registry for them, no wallet, no signature and no ranking:
// the user lists GitHub accounts, the Store reads what those accounts publish and nothing else, and the code is read from GitHub as it is now,
// so its author edits it freely. (The frozen and ranked apps are the Aiwa mode's, through the registry.)
//
// An entry of the list is
//   login          the repository login/aiwa-apps, whose root holds the apps (name.app.html)
//   login/repo     the folder aiwa-apps/ of that repository (where a Claude Code session puts an app)

const ENTRY = /^([A-Za-z0-9](?:[A-Za-z0-9-]{0,38}))(?:\/([A-Za-z0-9._-]{1,100}))?$/;
const APP_FILE = /^([a-z0-9](?:[a-z0-9-]{0,38}[a-z0-9])?)\.app\.html$/;
const TTL_MS = 10 * 60 * 1000;
const MAX_CHARS = 512 * 1024;

/** A listed account as { owner, repo, dir } (null when it is not one). */
export function parseAccount(text) {
  const m = ENTRY.exec(String(text ?? '').trim().replace(/^https?:\/\/github\.com\//, '').replace(/\/+$/, ''));
  if (!m) return null;
  return m[2] ? { owner: m[1], repo: m[2], dir: 'aiwa-apps' } : { owner: m[1], repo: 'aiwa-apps', dir: '' };
}

export const accountKey = ({ owner, repo, dir }) => (dir ? `${owner}/${repo}` : owner);

/**
 * The apps an account publishes: the name.app.html files of its folder, newest listing (one GitHub API request, cached ten minutes; the last
 * answer is used when GitHub cannot be reached or its limit — 60 requests an hour for each address — is spent).
 * @returns {Promise<{ apps: object[], source: 'network'|'cache', error?: string }>}
 */
export async function listAccount(account, { fetchFn = fetch, store = null, now = Date.now() } = {}) {
  const key = `library:${accountKey(account)}`;
  const kept = store?.get(key) ?? null;
  if (kept && now - kept.at < TTL_MS) return { apps: kept.apps, source: 'cache' };
  try {
    const response = await fetchFn(`https://api.github.com/repos/${account.owner}/${account.repo}/contents/${account.dir}`, { headers: { Accept: 'application/vnd.github+json' } });
    if (response.status === 404) throw new Error(`${accountKey(account)}: no public ${account.dir ? 'aiwa-apps folder in' : 'repository aiwa-apps for'} this account`);
    if (!response.ok) throw new Error(`GitHub answered ${response.status} for ${accountKey(account)}${response.status === 403 ? ' (its limit of requests is spent for now)' : ''}`);
    const files = await response.json();
    const apps = (Array.isArray(files) ? files : []).filter((f) => f.type === 'file' && APP_FILE.test(f.name) && f.size <= MAX_CHARS).map((f) => ({
      id: `${accountKey(account)}/${APP_FILE.exec(f.name)[1]}`, name: APP_FILE.exec(f.name)[1], kind: 'link', owner: account.owner,
      repo: `${account.owner}/${account.repo}`, file: f.path, ref: 'HEAD',
    }));
    store?.set(key, { at: now, apps });
    return { apps, source: 'network' };
  } catch (error) {
    if (kept) return { apps: kept.apps, source: 'cache', error: error.message };
    throw error;
  }
}

/** The apps of every listed account, each failure kept apart (one account that fails does not hide the others). */
export async function loadLibrary(accounts, options) {
  const apps = [];
  const problems = [];
  await Promise.all(accounts.map(async (account) => {
    try { apps.push(...(await listAccount(account, options)).apps); } catch (error) { problems.push(error.message); }
  }));
  apps.sort((a, b) => (a.id < b.id ? -1 : a.id > b.id ? 1 : 0));
  return { apps, problems };
}

/** Library apps whose name or account contains every word of `query`. */
export function filterLibrary(apps, query) {
  const words = String(query ?? '').toLowerCase().split(/\s+/).filter(Boolean);
  return apps.filter((a) => words.every((w) => `${a.name} ${a.repo}`.toLowerCase().includes(w)));
}

/** The code of a library app, read from GitHub as it is NOW: nothing signed, nothing pinned. */
export async function loadLink({ entry, fetchFn = fetch }) {
  const response = await fetchFn(`https://raw.githubusercontent.com/${entry.repo}/${entry.ref}/${entry.file}`, { cache: 'no-store' }).catch(() => null);
  if (!response?.ok) throw new Error(`Not opened: ${entry.repo} does not serve ${entry.file} (it may be private, moved or deleted)`);
  const html = await response.text();
  if (html.length > MAX_CHARS) throw new Error('Not opened: the app file is larger than 512 KB');
  return { html, files: { [entry.file]: html } };
}

// The list of accounts, in this browser only (a convenience: nothing of it leaves the phone).
const LIST_KEY = 'aiwa-library-accounts';
export function savedAccounts() {
  try { return (JSON.parse(localStorage.getItem(LIST_KEY)) ?? []).map(parseAccount).filter(Boolean); } catch { return []; }
}
export function saveAccounts(accounts) {
  try { localStorage.setItem(LIST_KEY, JSON.stringify(accounts.map(accountKey))); } catch { /* private window: the list lasts until the page closes */ }
}
/** A small synchronous store over localStorage for the cached listings. */
export const localStore = {
  get: (k) => { try { return JSON.parse(localStorage.getItem(k)); } catch { return null; } },
  set: (k, v) => { try { localStorage.setItem(k, JSON.stringify(v)); } catch { /* cache only */ } },
};
