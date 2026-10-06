// The wallet's network: ONE transport made of the two ways it meets other wallets, which feed the same log.
//   by itself: the wallets that are open join a room (Trystero, through a public relay that sees only that someone looks for
//              peers) and a direct connection opens to each of them: nobody swaps a code, there is no server of ours;
//   by hand:   two phones side by side swap two codes (link.js), for when no relay can be reached.
// Persistence is another matter (a wallet that nobody holds is gone with its phone): it is for machines that stay connected.
import { WebrtcTransport, TrysteroTransport, CompositeTransport } from 'aiwa-lib';
import { joinRoom, selfId } from '@trystero-p2p/nostr';
import { config } from './config.js';

const PREFERENCE = 'aiwa-network';      // 'off' once the person turned it off
const RELAYS = 'aiwa-relays';           // a JSON list of relay addresses, instead of the built-in public ones (a relay of one's own, a test)

const read = (key) => { try { return localStorage.getItem(key); } catch { return null; } };

export const wantsNetwork = () => read(PREFERENCE) !== 'off';

function relays() {
  try {
    const list = JSON.parse(read(RELAYS));
    return Array.isArray(list) && list.length > 0 && list.every((u) => /^wss?:\/\//.test(u)) ? list : null;
  } catch { return null; }
}

const networks = new WeakMap();      // wallet -> { manual, lobby, all }

/** The wallet's transports, made and joined to its replicator the first time. */
export async function networkOf(aiwa) {
  let net = networks.get(aiwa);
  if (!net) {
    const iceServers = config.nearby?.iceServers ?? [{ urls: 'stun:stun.l.google.com:19302' }];
    const manual = new WebrtcTransport({ iceServers });
    const lobby = new TrysteroTransport({
      joinRoom, selfId, appId: 'aiwa-store-v1', roomId: `lobby:${config.cluster ?? 'devnet'}`,
      relayUrls: relays(), rtcConfig: { iceServers }, enabled: wantsNetwork(),
    });
    net = { manual, lobby, all: new CompositeTransport([manual, lobby]) };
    networks.set(aiwa, net);
    await aiwa.joinNetwork(net.all);
  }
  return net;
}

/** Turns the lobby on or off, and remembers it. */
export async function setNetwork(aiwa, on) {
  try { if (on) localStorage.removeItem(PREFERENCE); else localStorage.setItem(PREFERENCE, 'off'); } catch { /* it lasts until the page closes */ }
  const { lobby } = await networkOf(aiwa);
  lobby.enabled = on;
  if (on) await lobby.connect(); else await lobby.disconnect();
}

/**
 * Tells `onChange({ peers, received })` when a wallet joins or leaves, and when events arrive from one. `peers` is how many are
 * connected now (found by itself or by hand), `received` how many events came in over those links so far.
 */
export async function watchNetwork(aiwa, onChange) {
  const { all } = await networkOf(aiwa);
  let received = 0;
  const tell = () => onChange({ peers: all.peers().length, received, wanted: wantsNetwork() });
  all.onPeerJoin(tell);
  all.onPeerLeave(tell);
  aiwa.replicator.onSync(({ receivedCount }) => { if (receivedCount > 0) { received += receivedCount; tell(); } });
  tell();
  return tell;
}
