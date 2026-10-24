// « Voir »: what the people near publish, and what comes by a link, by a QR code or from the favourites. NOT a transfer of anything: a person publishes the web links of the Files of her current repository.
// Four ways in (?page=voir&m=lien|proche|qr|favoris): by link (a pasted link or card), near (about 50 m: a Trystero room per map cell of 38 x 19 m, her own and the neighbours toward the nearest edges; the relay sees only that
// someone looks in a cell), by QR code (mine to show, another's to scan), the favourites (kept on this phone). The people near are ranked by the Aiwa score they sign.
//   &pub=1 publish my links to the people near   &repo=owner/name   &links=[{"kind","url"}]
import { joinRoom } from '@trystero-p2p/nostr';
import { config } from './config.js';
import { session, connected, onSession } from './session.js';
import { $ } from './ui.js';
import { drawQr, scanQr } from './qr.js';
import { cellsAround, cleanLinks, checkEntry, messageOf, parseCard, cardText, readBlocked, writeBlocked, utf8, PERIOD_MS } from './voir-core.js';

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

function addFavourite(card) {
  const list = readFavs();
  const key = card.links.map((l) => l.url).join('|');
  if (!list.some((f) => f.links.map((l) => l.url).join('|') === key)) { list.unshift({ repo: card.repo ?? '', links: card.links, at: Date.now() }); writeFavs(list); }
}

function showResult(card) {
  $('voir-result').hidden = false;
  const ul = $('voir-result-list'); ul.textContent = '';
  if (!card) { const li = document.createElement('li'); li.textContent = 'Ce n\'est ni un lien https ni la carte d\'une personne.'; ul.append(li); return; }
  ul.append(cardItem(card, [['Garder en favori', () => { addFavourite(card); showResult(card); }]]));
}

function renderFavourites() {
  $('voir-list-card').hidden = false; $('voir-list-title').textContent = 'Mes favoris';
  const ul = $('voir-list'); ul.textContent = ''; const list = readFavs();
  $('voir-empty').hidden = list.length > 0; $('voir-empty').textContent = 'Aucun favori : garde un lien, une carte scannée ou une personne proche.';
  for (const fav of list) ul.append(cardItem(fav, [['Retirer', () => { writeFavs(readFavs().filter((f) => f.at !== fav.at)); renderFavourites(); }]]));
}

function renderNear(entries, blocked) {
  const ul = $('voir-list'); ul.textContent = '';
  const rows = [...entries.values()].filter((e) => !blocked.has(e.pub)).sort((a, b) => (b.score / Math.max(1, b.laps)) - (a.score / Math.max(1, a.laps)));
  $('voir-empty').hidden = rows.length > 0; $('voir-empty').textContent = 'Personne pour l\'instant : la liste se remplit quand d\'autres personnes à moins de 50 m ouvrent « Voir ».';
  for (const e of rows) ul.append(cardItem(e, [['Garder en favori', () => addFavourite({ repo: e.repo, links: e.links })], ['Bloquer', () => { const set = readBlocked(); set.add(e.pub); writeBlocked(set); renderNear(entries, set); }]]));
}

export function initVoirPage(params) {
  const mode = ['lien', 'proche', 'qr', 'favoris'].includes(params.get('m')) ? params.get('m') : 'proche';
  const publish = params.get('pub') === '1';
  const repo = params.get('repo') ?? '';
  let mine = [];
  try { mine = cleanLinks(JSON.parse(params.get('links') ?? '[]')); } catch { mine = []; }
  const status = (text) => { $('voir-status').textContent = text; };
  $('voir-title').textContent = { lien: 'Voir par lien', proche: 'Voir à proximité (50 m)', qr: 'Voir par QR code', favoris: 'Mes favoris' }[mode];
  $('voir-mine').textContent = mine.length ? `Tes liens (${repo || 'ton dépôt'}) : ${mine.map((l) => kindName[l.kind]).join(', ')}.` : 'Ton dépôt n\'a encore rien à montrer (ou il est privé).';

  if (mode === 'favoris') { status('Gardés sur ce téléphone.'); renderFavourites(); return; }

  if (mode === 'lien') {
    $('voir-lien').hidden = false; status('Colle un lien ou la carte d\'une personne.');
    const go = () => showResult(parseCard($('voir-input').value));
    $('voir-go').addEventListener('click', go);
    $('voir-input').addEventListener('keydown', (e) => { if (e.key === 'Enter') go(); });
    navigator.clipboard?.readText?.().then((text) => { if (text && parseCard(text)) { $('voir-input').value = text; go(); } }).catch(() => {});
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

  // near: about 50 m
  $('voir-list-card').hidden = false; $('voir-list-title').textContent = 'À moins de 50 m, classés par score Aiwa';
  $('voir-empty').textContent = ''; 
  if (!navigator.geolocation) { status('Ce téléphone ne donne pas la position : « Voir » ne peut pas trouver les personnes proches.'); return; }
  status('Recherche de ta zone…');
  navigator.geolocation.getCurrentPosition((pos) => {
    const cells = cellsAround(pos.coords.latitude, pos.coords.longitude), own = cells[0];
    status(publish ? `Tu es visible dans la zone ${own}.` : `Tu regardes sans te montrer (zone ${own}). Active « Visible » dans le widget pour publier tes liens.`);
    const entries = new Map(), blocked = readBlocked(), decoder = new TextDecoder();
    const iceServers = config.nearby?.iceServers ?? [{ urls: 'stun:stun.l.google.com:19302' }];
    const actions = cells.map((cell) => {
      const room = joinRoom({ appId: 'aiwa-voir-v1', rtcConfig: { iceServers } }, `voir:${cell}`);
      const action = room.makeAction('entry');
      action.onMessage = (data) => {
        let entry; try { entry = JSON.parse(decoder.decode(data instanceof Uint8Array ? data : new Uint8Array(data))); } catch { return; }
        const ok = checkEntry(entry, cells);
        if (ok && ok.pub !== session.aiwa?.identity?.publicKey) { entries.set(ok.pub, ok); renderNear(entries, blocked); }
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
    renderNear(entries, blocked);
  }, (err) => status(err.code === 1 ? 'Sans l\'autorisation de position, « Voir » ne peut pas trouver les personnes proches.' : `Position indisponible (${err.message}).`), { enableHighAccuracy: true, maximumAge: 60_000, timeout: 20_000 });
}
