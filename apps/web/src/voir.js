// « Voir »: what the people near publish, and what comes by a link, by a QR code or from the favourites. NOT a transfer of anything: a person publishes the web links of the Files of her current repository.
// Four ways in (?page=voir&m=lien|proche|qr|favoris): by link (a pasted link or card), near (about 50 m: a Trystero room per map cell of 38 x 19 m, her own and the neighbours toward the nearest edges; the relay sees only that
// someone looks in a cell), by QR code (mine to show, another's to scan), the favourites (kept on this phone). The people near are ranked by the Aiwa score they sign.
//   &pub=1 publish my links to the people near   &repo=owner/name   &links=[{"kind","url"}]
import { joinRoom } from '@trystero-p2p/nostr';
import { config } from './config.js';
import { session, connected, onSession } from './session.js';
import { $ } from './ui.js';
import { hostAvailable, hostPost } from './host.js';
import { drawQr, scanQr } from './qr.js';
import { cellsAround, cleanLinks, checkEntry, messageOf, parseCard, cardText, parseRepo, repoLinks, readBlocked, writeBlocked, utf8, PERIOD_MS } from './voir-core.js';

const kindName = { docs: 'Docs', media: 'Média', app: 'App', page: 'Page', contract: 'Contrat' };
const FAVS = 'aiwa-voir-favs';
const readFavs = () => { try { const v = JSON.parse(localStorage.getItem(FAVS) ?? '[]'); return Array.isArray(v) ? v.slice(0, 200) : []; } catch { return []; } };
const writeFavs = (list) => { try { localStorage.setItem(FAVS, JSON.stringify(list)); } catch { /* it lasts until the page closes */ } };
const hostOf = (url) => { try { return new URL(url).host; } catch { return url; } };

/** One card (a person, a pasted link, a favourite): her name, her links (they open in the browser), and the buttons given. */
function cardItem({ repo, pub, score, laps, links }, buttons) {
  const li = document.createElement('li');
  const who = document.createElement('div'); who.className = 'who';
  const name = document.createElement('span'); name.textContent = repo || (pub ? `${pub.slice(0, 8)}…` : hostOf(links[0]?.url ?? ''));
  who.append(name);
  if (Number.isFinite(score)) { const s = document.createElement('span'); s.className = 'score'; s.textContent = `score ${score.toPrecision(3)} · tours ${laps ?? 0}`; who.append(s); }
  li.append(who);
  for (const link of links) {
    const a = document.createElement('a');
    a.href = link.url; a.rel = 'noopener noreferrer'; a.target = '_blank'; a.textContent = `${kindName[link.kind] ?? 'Lien'} · ${hostOf(link.url)}`;
    li.append(a);
  }
  for (const [label, fn] of buttons) { const b = document.createElement('button'); b.type = 'button'; b.textContent = label; b.addEventListener('click', fn); li.append(b); }
  return li;
}

/** Opens the profiles of a list full screen on the phone (the app reads each `Profile/index.html` from its repository and swipes from one to the next); in a browser, the folder of the one chosen opens on GitHub. */
function openProfiles(repos, index) {
  if (hostAvailable()) hostPost({ cmd: 'profile', repos, index });
  else window.open(`https://github.com/${repos[index]}/tree/HEAD/Profile`, '_blank', 'noopener');
}
const profileButton = (repos, repo) => (repo && repos.includes(repo) ? [['Profil', () => openProfiles(repos, repos.indexOf(repo))]] : []);

function addFavourite(card) {
  const list = readFavs();
  const key = card.links.map((l) => l.url).join('|');
  if (!list.some((f) => f.links.map((l) => l.url).join('|') === key)) { list.unshift({ repo: card.repo ?? '', links: card.links, at: Date.now() }); writeFavs(list); }
}

function showResult(card, message = 'Ce n\'est ni un lien https ni la carte d\'une personne.') {
  $('voir-result').hidden = false;
  const ul = $('voir-result-list'); ul.textContent = '';
  if (!card) { const li = document.createElement('li'); li.textContent = message; ul.append(li); return; }
  ul.append(cardItem(card, [...profileButton(card.repo ? [card.repo] : [], card.repo), ['Garder en favori', () => { addFavourite(card); showResult(card); }]]));
}

function renderFavourites() {
  $('voir-list-card').hidden = false; $('voir-list-title').textContent = 'Mes favoris';
  const ul = $('voir-list'); ul.textContent = ''; const list = readFavs();
  $('voir-empty').hidden = list.length > 0; $('voir-empty').textContent = 'Aucun favori : garde un lien, une carte scannée ou une personne proche.';
  const favRepos = list.map((f) => f.repo).filter(Boolean);
  for (const fav of list) ul.append(cardItem(fav, [...profileButton(favRepos, fav.repo), ['Retirer', () => { writeFavs(readFavs().filter((f) => f.at !== fav.at)); renderFavourites(); }]]));
}

function renderNear(entries, blocked, mode = 'proche') {
  const ul = $('voir-list'); ul.textContent = '';
  const rows = [...entries.values()].filter((e) => !blocked.has(e.pub)).sort((a, b) => (b.score / Math.max(1, b.laps)) - (a.score / Math.max(1, a.laps)));
  $('voir-empty').hidden = rows.length > 0; $('voir-empty').textContent = 'Personne pour l\'instant : la liste se remplit quand d\'autres personnes à moins de 50 m ouvrent « Voir ».';
  const repos = rows.map((e) => e.repo).filter(Boolean);
  for (const e of rows) ul.append(cardItem(e, [...profileButton(repos, e.repo), ['Garder en favori', () => addFavourite({ repo: e.repo, links: e.links })], ['Bloquer', () => { const set = readBlocked(); set.add(e.pub); writeBlocked(set); renderNear(entries, set, mode); }]]));
}

export function initVoirPage(params) {
  const mode = ['lien', 'proche', 'qr', 'favoris'].includes(params.get('m')) ? params.get('m') : 'proche';
  const publish = params.get('pub') === '1';
  const repo = params.get('repo') ?? '';
  let mine = [];
  try { mine = cleanLinks(JSON.parse(params.get('links') ?? '[]')); } catch { mine = []; }
  const status = (text) => { $('voir-status').textContent = text; };
  $('voir-title').textContent = { lien: 'Voir un dépôt', proche: 'Voir à proximité (50 m)', qr: 'Voir par QR code', favoris: 'Mes favoris' }[mode];
  $('voir-mine').textContent = mine.length ? `Tes liens (${repo || 'ton dépôt'}) : ${mine.map((l) => kindName[l.kind]).join(', ')}.` : 'Ton dépôt n\'a encore rien à montrer (ou il est privé).';

  if (mode === 'favoris') { status('Gardés sur ce téléphone.'); renderFavourites(); return; }

  if (mode === 'lien') {
    $('voir-lien').hidden = false; status('Le nom GitHub et le dépôt.');
    const go = async () => {
      const repoName = parseRepo($('voir-owner').value, $('voir-repo').value);
      if (!repoName) { showResult(null, 'Il faut le nom GitHub et le dépôt (ou « nom/dépôt »).'); return; }
      status(`Je regarde ${repoName}…`);
      // what exists, read without any key (GitHub's public API): the links of what is not there are left out; if GitHub cannot be reached, all five are shown
      let links = repoLinks(repoName);
      try {
        const get = async (path) => (await fetch(`https://api.github.com/repos/${repoName}${path}`, { headers: { Accept: 'application/vnd.github+json' } })).ok;
        if (!(await get(''))) { showResult(null, `${repoName} : introuvable, ou privé (Aiwa ne lit que les dépôts publics).`); status(''); return; }
        const [docs, contract, releases] = await Promise.all([get('/contents/docs'), get('/contents/aiwa-apps'), get('/releases')]);
        links = links.filter((l) => (l.kind === 'docs' ? docs : l.kind === 'contract' ? contract : l.kind === 'app' || l.kind === 'media' ? releases : true));
      } catch { /* the five links are shown, unchecked */ }
      status(''); showResult({ repo: repoName, links });
    };
    $('voir-go').addEventListener('click', go);
    for (const id of ['voir-owner', 'voir-repo']) $(id).addEventListener('keydown', (e) => { if (e.key === 'Enter') go(); });
    navigator.clipboard?.readText?.().then((text) => { const r = parseRepo(text); if (r && !$('voir-owner').value) { $('voir-owner').value = r; go(); } }).catch(() => {});
    return;
  }

  if (mode === 'qr') {
    $('voir-qr').hidden = false; status('Un code à montrer, un code à scanner.');
    if (mine.length) drawQr($('voir-qr-draw'), cardText(repo, mine), 280).catch(() => {}); else $('voir-qr-note').textContent = 'Ton dépôt n\'a rien à montrer : pas de code à afficher. Tu peux quand même scanner celui d\'une autre personne.';
    $('voir-scan-btn').addEventListener('click', async () => {
      const video = $('voir-scan'); video.hidden = false; status('Vise le code…');
      const text = await scanQr(video); video.hidden = true;
      status(text ? 'Code lu.' : 'Aucun code lu (caméra refusée ou fermée).'); if (text) showResult(parseCard(text));
    });
    return;
  }

  // near (about 50 m) or the world: the same rooms, a different place to meet
  $('voir-list-card').hidden = false; $('voir-list-title').textContent = 'À moins de 50 m, classés par score Aiwa';
  $('voir-empty').textContent = '';
  const meet = (cells) => {
    const own = cells[0];
    status(publish ? `Tu es visible dans la zone ${own}.` : 'Tu regardes sans te montrer. Active « Visible » dans le widget pour publier tes liens.');
    const entries = new Map(), blocked = readBlocked(), decoder = new TextDecoder();
    const iceServers = config.nearby?.iceServers ?? [{ urls: 'stun:stun.l.google.com:19302' }];
    const actions = cells.map((cell) => {
      const room = joinRoom({ appId: 'aiwa-voir-v1', rtcConfig: { iceServers } }, `voir:${cell}`);
      const action = room.makeAction('entry');
      action.onMessage = (data) => {
        let entry; try { entry = JSON.parse(decoder.decode(data instanceof Uint8Array ? data : new Uint8Array(data))); } catch { return; }
        const ok = checkEntry(entry, cells);
        if (ok && ok.pub !== session.aiwa?.identity?.publicKey) { entries.set(ok.pub, ok); renderNear(entries, blocked, mode); }
      };
      return { room, action };
    });
    const mineEntry = async () => {
      if (!publish || !connected() || mine.length === 0) return null;
      let figure = { score: 0, laps: 0 };
      try { figure = (await session.aiwa.ranking()) ?? figure; } catch { /* no burn yet: zero */ }
      const e = { v: 1, cell: own, repo, links: mine, score: Number(figure.score) || 0, laps: Number(figure.laps) || 0, ts: Date.now(), pub: session.aiwa.identity.publicKey };
      e.sig = await session.aiwa.identity.sign(messageOf(e));
      return e;
    };
    const announce = async (action, peerId) => { const e = await mineEntry(); if (e) await action.send(utf8.encode(JSON.stringify(e)), peerId ? { target: peerId } : {}); };
    for (const { room, action } of actions) room.onPeerJoin = (peerId) => { announce(action, peerId).catch(() => {}); };
    const all = () => Promise.all(actions.map(({ action }) => announce(action).catch(() => {})));
    setInterval(all, PERIOD_MS); onSession(() => { all(); }); all();
    renderNear(entries, blocked, mode);
  };
  if (!navigator.geolocation) { status('Ce téléphone ne donne pas la position : « Voir » ne peut pas trouver les personnes proches.'); return; }
  status('Recherche de ta zone…');
  navigator.geolocation.getCurrentPosition((pos) => meet(cellsAround(pos.coords.latitude, pos.coords.longitude)),
    (err) => status(err.code === 1 ? 'Sans l\'autorisation de position, « Voir » ne peut pas trouver les personnes proches.' : `Position indisponible (${err.message}).`), { enableHighAccuracy: true, maximumAge: 60_000, timeout: 20_000 });
}
