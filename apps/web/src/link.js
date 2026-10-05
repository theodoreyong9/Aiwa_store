// Link this phone with another one, directly (WebRTC), so that the two exchange what each holds and each signs what it received.
//
// No server and no account: the two phones swap two codes, as in the click duel (a code on one screen, read by the other phone's
// camera or pasted, then the answer back). Once the link is open, aiwa-lib's replicator (yellow paper §13.6) sends each phone the events
// the other lacks, and the wallet answers what it received with a signed reception (§8), which is what makes a phone's view of
// another one provable (§19).
//
// What goes across is the wallet's history of signed events, which is no secret (a payment carries as much); never the 12 words.
import { WebrtcTransport } from 'aiwa-lib';
import { config } from './config.js';
import { showCode, scanCode, closeSheet } from './app-door.js';

const toB64 = (bytes) => btoa(String.fromCharCode(...bytes)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
const fromB64 = (text) => Uint8Array.from(atob(text.replace(/-/g, '+').replace(/_/g, '/')), (c) => c.charCodeAt(0));
async function through(stream, bytes) {
  const writer = stream.writable.getWriter();
  writer.write(bytes); writer.close();
  return new Uint8Array(await new Response(stream.readable).arrayBuffer());
}

const PREFIX = 'link1.';
/** A signal of the transport (an offer or an answer), squeezed into something a QR code can hold. */
export const pack = async (blob) => PREFIX + toB64(await through(new CompressionStream('deflate-raw'), new TextEncoder().encode(blob)));
/** Back from a code; throws a sentence a person can read when it is not one of ours. */
export async function unpack(code) {
  const text = String(code ?? '').trim();
  if (!text.startsWith(PREFIX)) throw new Error('this is not a link code');
  try { return new TextDecoder().decode(await through(new DecompressionStream('deflate-raw'), fromB64(text.slice(PREFIX.length)))); }
  catch { throw new Error('this link code is damaged: read or paste it again'); }
}

const transports = new WeakMap();      // wallet -> its one transport: any number of phones can be linked through it

/** The wallet's transport, made and joined to the wallet's replicator the first time. */
async function networkOf(aiwa) {
  let transport = transports.get(aiwa);
  if (!transport) {
    transport = new WebrtcTransport({ iceServers: config.nearby?.iceServers ?? [{ urls: 'stun:stun.l.google.com:19302' }] });
    transports.set(aiwa, transport);
    await aiwa.joinNetwork(transport);
  }
  return transport;
}

/**
 * Tells `onChange({ phones, received })` when a phone links or leaves, and when events arrive from one. `phones` is how many are linked
 * now, `received` how many events came in over the links so far.
 */
export async function watchLinks(aiwa, onChange) {
  const transport = await networkOf(aiwa);
  let received = 0;
  const tell = () => onChange({ phones: transport.peers().length, received });
  transport.onPeerJoin(tell);
  transport.onPeerLeave(tell);
  aiwa.replicator.onSync(({ receivedCount }) => { if (receivedCount > 0) { received += receivedCount; tell(); } });
  tell();
}

/** This phone starts: shows a code, then reads the answer of the other. Resolves once the answer is given (the link opens a moment later). */
export async function startLink(aiwa) {
  const transport = await networkOf(aiwa);
  const peer = crypto.randomUUID().slice(0, 8);
  try {
    const offer = await transport.createOfferFor(peer);
    await showCode({ text: await pack(offer), title: 'Show this code to the other phone', action: 'Next: read its answer' });
    const answer = await scanCode({ title: 'Read the answer of the other phone' });
    await transport.completeConnection(peer, await unpack(answer));
  } catch (err) {
    transport.closePeer(peer);
    closeSheet();
    throw err;
  }
}

/** This phone answers: reads the code of the other, shows its own answer. */
export async function joinLink(aiwa) {
  const transport = await networkOf(aiwa);
  const peer = crypto.randomUUID().slice(0, 8);      // our own name for this link: trying again never meets a half-made one
  try {
    const offer = await scanCode({ title: 'Read the code of the other phone' });
    const answer = await transport.acceptOffer(await unpack(offer), { peerId: peer });
    await showCode({ text: await pack(answer), title: 'Show this answer to the other phone', action: 'Done' });
  } catch (err) {
    transport.closePeer(peer);
    closeSheet();
    throw err;
  }
}
