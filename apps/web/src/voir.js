// « Voir »: the people near, and what they publish. NOT a transfer of anything: each person publishes the web links of the Files of her current
// repository (docs, media, app, page, contract) and the others see them, ranked by the Aiwa score the publisher signs. Meeting point: one Trystero
// room per map cell (a geohash; the relay sees only that someone looks in this cell, and never an exact place). The page runs while it is open.
//   ?page=voir&r=0..3 (100 m, 1 km, 10 km, the town)&pub=1 (publish my links too)&repo=owner/name&links=[{"kind","url"}]
import { joinRoom } from '@trystero-p2p/nostr';
import { config } from './config.js';
import { session, connected, onSession } from './session.js';
import { $ } from './ui.js';

import { PRECISION, REACH, geohash, cleanLinks, checkEntry, messageOf, readBlocked, writeBlocked, utf8, PERIOD_MS } from './voir-core.js';

const kindName = { docs: 'Docs', media: 'Média', app: 'App', page: 'Page', contract: 'Contrat' };

function render(entries, blocked) {
  const list = $('voir-list');
  list.textContent = '';
  const rows = [...entries.values()].filter((e) => !blocked.has(e.pub)).sort((a, b) => (b.score / Math.max(1, b.laps)) - (a.score / Math.max(1, a.laps)));
  $('voir-empty').hidden = rows.length > 0;
  for (const e of rows) {
    const li = document.createElement('li');
    const who = document.createElement('div'); who.className = 'who';
    const name = document.createElement('span'); name.textContent = e.repo || `${e.pub.slice(0, 8)}…`;
    const score = document.createElement('span'); score.className = 'score'; score.textContent = `score ${e.score.toPrecision(3)} · tours ${e.laps}`;
    who.append(name, score); li.append(who);
    for (const link of e.links) {
      const a = document.createElement('a');
      a.href = link.url; a.rel = 'noopener noreferrer'; a.target = '_blank';
      a.textContent = `${kindName[link.kind]} · ${new URL(link.url).host}`;
      li.append(a);
    }
    const block = document.createElement('button');
    block.type = 'button'; block.textContent = 'Bloquer';
    block.addEventListener('click', () => { const set = readBlocked(); set.add(e.pub); writeBlocked(set); render(entries, set); });
    li.append(block);
    list.append(li);
  }
}

export function initVoirPage(params) {
  const reach = Math.min(3, Math.max(0, Number(params.get('r')) || 0));
  const publish = params.get('pub') === '1';
  const repo = params.get('repo') ?? '';
  let mine = [];
  try { mine = cleanLinks(JSON.parse(params.get('links') ?? '[]')); } catch { mine = []; }
  const status = (text) => { $('voir-status').textContent = text; };
  $('voir-mine').textContent = publish
    ? (mine.length ? `Tu publies ${mine.length} lien(s) de ${repo || 'ton dépôt'} : ${mine.map((l) => kindName[l.kind]).join(', ')}.` : 'Tu es visible, mais ton dépôt n\'a encore rien à montrer (ou il est privé).')
    : 'Tu regardes sans te montrer : active « Visible » dans le widget pour publier tes liens.';
  if (!navigator.geolocation) { status('Ce téléphone ne donne pas la position : « Voir » ne peut pas trouver les personnes proches.'); return; }
  status(`Recherche de ta zone (portée : ${REACH[reach]})…`);
  navigator.geolocation.getCurrentPosition(async (pos) => {
    const cell = geohash(pos.coords.latitude, pos.coords.longitude, PRECISION[reach]);
    status(`Zone ${cell} (portée : ${REACH[reach]}). Tu es dans un salon avec les personnes de la même zone.`);
    const entries = new Map();
    const blocked = readBlocked();
    const iceServers = config.nearby?.iceServers ?? [{ urls: 'stun:stun.l.google.com:19302' }];
    const room = joinRoom({ appId: 'aiwa-voir-v1', rtcConfig: { iceServers } }, `voir:${cell}`);
    const action = room.makeAction('entry');
    const decoder = new TextDecoder();
    action.onMessage = (data) => {
      let entry; try { entry = JSON.parse(decoder.decode(data instanceof Uint8Array ? data : new Uint8Array(data))); } catch { return; }
      const ok = checkEntry(entry, cell);
      if (ok && ok.pub !== session.aiwa?.identity?.publicKey) { entries.set(ok.pub, ok); render(entries, blocked); }
    };
    const mineEntry = async () => {
      if (!publish || !connected() || mine.length === 0) return null;
      let figure = { score: 0, laps: 0 };
      try { figure = (await session.aiwa.ranking()) ?? figure; } catch { /* no burn yet: zero */ }
      const e = { v: 1, cell, repo, links: mine, score: Number(figure.score) || 0, laps: Number(figure.laps) || 0, ts: Date.now(), pub: session.aiwa.identity.publicKey };
      e.sig = await session.aiwa.identity.sign(messageOf(e));
      return e;
    };
    const announce = async (peerId) => { const e = await mineEntry(); if (e) await action.send(utf8.encode(JSON.stringify(e)), peerId ? { target: peerId } : {}); };
    room.onPeerJoin = (peerId) => { announce(peerId).catch(() => {}); };
    setInterval(() => { announce().catch(() => {}); }, PERIOD_MS);
    onSession(() => { announce().catch(() => {}); });
    announce().catch(() => {});
    render(entries, blocked);
  }, (err) => status(err.code === 1 ? 'Sans l\'autorisation de position, « Voir » ne peut pas trouver les personnes proches.' : `Position indisponible (${err.message}).`), { enableHighAccuracy: false, maximumAge: 5 * 60_000, timeout: 20_000 });
}
