// The Comments tab: the comments of the person and those of the whole network. A comment is a package the registry has verified (signed by a wallet that showed its mining) and
// listed in its index (index.json, "comments"), ranked by what its author mines: the best ranked first. Nothing is read with a token, the registry is public.
import { config } from './config.js';
import { $, short } from './ui.js';
import { session, onSession } from './session.js';
import { openCommentSheet } from './store-ui.js';
import { rankedComments } from './comments-rank.js';

const MAX_SHOWN = 40;

async function getJson(url) {
  const response = await fetch(url, { cache: 'no-cache' });
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  return response.json();
}

function row(entry, text, mine) {
  const li = document.createElement('li');
  li.className = 'app';
  const body = document.createElement('div');
  body.className = 'body';
  const quote = document.createElement('div');
  quote.className = 'title';
  quote.style.whiteSpace = 'pre-wrap';
  quote.textContent = text;
  const meta = document.createElement('div');
  meta.className = 'hint';
  const when = Number(entry.updatedAt) ? new Date(Number(entry.updatedAt)).toLocaleDateString() : '';
  meta.textContent = `${mine ? 'You' : short(entry.author)} · weight ${entry.weight.toFixed(2)}${when ? ` · ${when}` : ''}`;
  body.append(quote, meta);
  li.append(body);
  return li;
}

let loading = false;
export async function refreshComments() {
  if (loading) return;
  loading = true;
  $('comments-status').textContent = 'Loading…';
  try {
    const index = await getJson(`${config.registryUrl}/index.json`);
    const ranked = rankedComments(index);
    const me = session.aiwa?.identity ? session.aiwa.address : null;
    const shown = [];
    const mineOnes = me ? ranked.filter((e) => e.author === me) : [];
    for (const entry of [...mineOnes, ...ranked.filter((e) => e.author !== me)].slice(0, MAX_SHOWN)) shown.push(entry);
    const texts = await Promise.all(shown.map(async (entry) => {
      try {
        const pkg = await getJson(`${config.registryUrl}/${entry.path}`);
        return typeof pkg.text === 'string' && pkg.author === entry.author ? pkg.text : null;   // the text must be signed by the author the index names
      } catch { return null; }
    }));
    const mineRows = []; const networkRows = [];
    shown.forEach((entry, i) => {
      if (texts[i] === null) return;
      const isMine = me !== null && entry.author === me;
      (isMine ? mineRows : networkRows).push(row(entry, texts[i], isMine));
    });
    $('comments-mine').replaceChildren(...mineRows);
    $('comments-network').replaceChildren(...networkRows);
    $('comments-mine-title').hidden = false;
    $('comments-mine-empty').hidden = mineRows.length > 0;
    $('comments-mine-empty').textContent = me ? 'None of yours is in the registry yet: a comment is listed once its pull request was accepted.' : 'Start your wallet to see your own comments here.';
    $('comments-network-empty').hidden = networkRows.length > 0;
    $('comments-status').textContent = ranked.length ? `${ranked.length} comment${ranked.length > 1 ? 's' : ''} in the registry (the ${Math.min(ranked.length, MAX_SHOWN)} best ranked shown).` : 'The registry holds no comment yet.';
  } catch (error) {
    $('comments-status').textContent = `The registry could not be read: ${error.message}`;
  } finally {
    loading = false;
  }
}

export function initCommentsTab() {
  $('comments-refresh').addEventListener('click', refreshComments);
  $('comments-write').addEventListener('click', () => openCommentSheet({ stay: true }));
  for (const btn of document.querySelectorAll('#app-nav button[data-view="comments"]')) btn.addEventListener('click', refreshComments);
  onSession(() => { if (!$('view-comments').hidden) refreshComments(); });
  if (!$('view-comments').hidden) refreshComments();
  window.addEventListener('hashchange', () => { if (!$('view-comments').hidden) refreshComments(); });
}
