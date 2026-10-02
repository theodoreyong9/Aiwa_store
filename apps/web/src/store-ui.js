import { loadIndex, loadApp, filterApps, shortAddress, figureOf } from './store.js';
import { openCache } from './kv.js';
import { config } from './config.js';
import { $ } from './ui.js';
import { setCatalog } from './session.js';
import { openViewer } from './viewer.js';

const cache = openCache('aiwa-store-cache');
let apps = [];

function render() {
  const shown = filterApps(apps, $('store-search').value);
  const list = $('store-list');
  list.replaceChildren();
  $('store-empty').hidden = apps.length > 0;
  for (const app of shown.slice(0, 200)) {
    const row = document.createElement('div');
    row.className = 'app';
    row.dataset.id = app.id;

    const rank = document.createElement('div');
    rank.className = 'rank';
    rank.textContent = String(apps.indexOf(app) + 1);

    const body = document.createElement('div');
    body.className = 'body';
    const title = document.createElement('div');
    title.className = 'title';
    title.textContent = app.name;
    const ver = document.createElement('span');
    ver.className = 'ver';
    ver.textContent = `v${app.version}`;
    title.append(ver);
    const desc = document.createElement('div');
    desc.className = 'desc';
    desc.textContent = app.description;
    const meta = document.createElement('div');
    meta.className = 'meta';
    meta.textContent = `${shortAddress(app.author)}${app.kind === 'aiwa' ? ' · Aiwa' : ''} · score/laps ${figureOf(app)} (${Number(app.score).toPrecision(3)} / ${app.laps})`;
    body.append(title, desc, meta);

    const open = document.createElement('button');
    open.type = 'button';
    open.className = 'primary';
    open.textContent = 'Open';
    open.addEventListener('click', () => openApp(app, open));

    row.append(rank, body, open);
    list.append(row);
  }
}

async function openApp(entry, button) {
  const was = button.textContent;
  button.disabled = true;
  button.textContent = '…';
  try {
    const { html } = await loadApp({ entry, baseUrl: config.registryUrl, cache });
    openViewer({ name: entry.name, meta: `v${entry.version} · ${shortAddress(entry.author)}`, html });
  } catch (err) {
    $('store-status').textContent = String(err.message ?? err);
  } finally {
    button.disabled = false;
    button.textContent = was;
  }
}

export async function refreshStore() {
  $('store-status').textContent = 'Loading…';
  try {
    const result = await loadIndex({ baseUrl: config.registryUrl, cache });
    apps = result.apps;
    setCatalog(apps);
    $('store-status').textContent = result.source === 'cache' ? `Offline: the last list seen (${result.error}).` : '';
  } catch (err) {
    apps = [];
    setCatalog(apps);
    $('store-status').textContent = `The registry could not be read: ${err.message}`;
  }
  render();
}

export function initStore() {
  $('store-search').addEventListener('input', render);
  $('store-refresh').addEventListener('click', refreshStore);
  return refreshStore();
}
