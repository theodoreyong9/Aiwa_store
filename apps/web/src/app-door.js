// The door between the Store and an app in its frame. An app is untrusted code with an opaque origin: by itself it cannot
// reach the wallet (yellow paper §17.2). An app that DECLARES it uses the wallet (<meta name="aiwa-wallet" content="pay">)
// gets this door instead: messages {aiwa: 1, id, cmd, args} to the page, answered {aiwa: 1, id, result | error}.
//
// What it is, and is not. The door does what the app asks: it pays, with no question asked, and has no cap or expiry. The Store
// signals it (a banner, shown as the app opens, says the app can move AIWA) and does nothing more: a hostile app that declares
// the wallet can spend it. That is a known gap of the sandbox, and not this file's to close.
//
// The commands are the small set a game between two phones needs:
//   whoami                  { id, address, balance, spendable }       what the app may know of the player
//   pay { to, amount }      { blob }                                  a payment of `amount` AIWA to the identity `to`, as an
//                                                                      offline bundle, signed by the channel's session key
//   receive { blob }        { balance, pending }                      appends a payment received from someone; `pending` is how many
//                                                                      burns it depends on still wait to be confirmed against Solana
//   showCode { text, title, action }  {}                              a code on screen (QR and text) until hideCode or Close; with
//                                                                      `action` (a button label) it answers only when that is pressed
//   hideCode                {}
//   scanCode { title }      text                                      a code read by the camera, or pasted
//   config                  { iceServers }                            how to reach a phone nearby (the deployment's)
import { encodeOfflineBundle, decodeOfflineBundle } from 'aiwa-lib';
import { config } from './config.js';
import { session, connected } from './session.js';
import { drawQr, scanQr } from './qr.js';
import { $ } from './ui.js';

const DECLARATION = /<meta\b[^>]*\bname\s*=\s*["']aiwa-wallet["']/i;

/** Does this app say it uses the wallet? */
export const declaresWallet = (html) => DECLARATION.test(html);

const AMOUNT = /^\d{1,12}(\.\d{1,18})?$/;
const IDENTITY = /^[0-9a-f]{64}$/;

// ---------- the sheet an app asks for: a code to show, a code to read ----------

let waiting = null;                  // a scanCode in progress: { resolve, reject, abort }

function openSheet(title, mode) {
  $('door-title').textContent = title;
  $('door-show').hidden = mode !== 'show';
  $('door-scan').hidden = mode !== 'scan';
  $('door-sheet').hidden = false;
}

export function closeSheet() {
  waiting?.abort.abort();
  waiting?.reject(new Error('cancelled'));
  waiting = null;
  $('door-sheet').hidden = true;
}

export async function showCode({ text, title = 'Show this code', action = null }) {
  if (typeof text !== 'string' || text.length === 0 || text.length > 8000) throw new Error('a code is some text, up to 8000 characters');
  closeSheet();
  openSheet(title, 'show');
  $('door-text').value = text;
  $('door-next').hidden = typeof action !== 'string' || action === '';
  $('door-next').textContent = action ?? '';
  try { await drawQr($('door-qr'), text, 280); } catch { $('door-qr').replaceChildren(); }   // too long for a QR: the text is still there
  if ($('door-next').hidden) return {};
  return new Promise((resolve, reject) => {
    waiting = { resolve, reject, abort: new AbortController(), done: () => { waiting = null; $('door-sheet').hidden = true; resolve({}); } };
  });
}

export function scanCode({ title = 'Scan the code' } = {}) {
  closeSheet();
  return new Promise((resolve, reject) => {
    const abort = new AbortController();
    waiting = { resolve, reject, abort };
    openSheet(title, 'scan');
    $('door-paste').value = '';
    const done = (text) => { if (waiting?.resolve !== resolve) return; waiting = null; abort.abort(); $('door-sheet').hidden = true; resolve(text); };
    waiting.done = done;
    scanQr($('door-video'), abort.signal).then((text) => { if (text) done(text); });
  });
}

$('door-next').addEventListener('click', () => waiting?.done?.());
$('door-use').addEventListener('click', () => { const text = $('door-paste').value.trim(); if (text) waiting?.done?.(text); });
$('door-close').addEventListener('click', closeSheet);
$('door-copy').addEventListener('click', async () => {
  try { await navigator.clipboard.writeText($('door-text').value); $('door-copy').textContent = 'Copied'; setTimeout(() => { $('door-copy').textContent = 'Copy'; }, 1200); } catch { $('door-text').select(); }
});

// ---------- the wallet side ----------

const channels = new Map();          // `${me}:${peer}` -> the channel opened toward that peer: signed once, then used

function wallet() {
  if (!connected()) throw new Error('there is no wallet yet: open the Wallet tab once');
  return session.aiwa;
}

const walletChanged = () => window.dispatchEvent(new CustomEvent('aiwa:wallet-changed'));

const commands = {
  async whoami() {
    const aiwa = wallet();
    return { id: aiwa.identity.id, address: aiwa.address, balance: String(await aiwa.balance()), spendable: String(await aiwa.spendableBalance()) };
  },
  async config() {
    return { iceServers: config.nearby?.iceServers ?? [{ urls: 'stun:stun.l.google.com:19302' }] };
  },
  async pay({ to, amount }) {
    const aiwa = wallet();
    if (!IDENTITY.test(String(to))) throw new Error('to: an identity id (64 hex characters)');
    if (!AMOUNT.test(String(amount)) || Number(amount) <= 0) throw new Error('amount: a positive number of AIWA');
    const key = `${aiwa.identity.id}:${to}`;
    // "Sign once, click many times": the first payment to a peer signs one delegation with the wallet's key; this one and every
    // later one is signed by the channel's session key, with no further use of the wallet's own key.
    if (!channels.has(key)) channels.set(key, await aiwa.openChannel(to, { requireNetwork: false }));
    const bundle = await channels.get(key).sendOfflineBundle(String(amount));
    walletChanged();
    return { blob: encodeOfflineBundle(bundle) };
  },
  async receive({ blob }) {
    const aiwa = wallet();
    let bundle;
    try { bundle = decodeOfflineBundle(String(blob)); } catch { throw new Error('this is not an Aiwa payment'); }
    await aiwa.receiveOfflineBundle(bundle);
    // AIWA counts for its receiver only once the burn it was created from is confirmed against Solana (§9.2): that is the one thing
    // a received payment needs from outside, and it is asked once per origin. Do it now, rather than when the wallet gets to it. With
    // no way to reach Solana the payment is received but stays uncounted: `pending` says how many burns still wait.
    let pending = null;
    if (aiwa.connection) {
      const confirmation = await aiwa.confirmBurns(aiwa.connection).catch(() => null);
      pending = confirmation ? confirmation.pending.length : null;
    }
    walletChanged();
    return { balance: String(await aiwa.balance()), pending };
  },
  showCode,
  async hideCode() { if (!waiting) $('door-sheet').hidden = true; return {}; },
  scanCode,
};

/** Answers the messages of the app in `frame`, and only that frame's. Returns the function that stops. */
export function attachDoor(frame) {
  const onMessage = async (event) => {
    const m = event.data;
    if (event.source !== frame.contentWindow || !m || m.aiwa !== 1 || !Number.isInteger(m.id)) return;
    const reply = (body) => frame.contentWindow?.postMessage({ aiwa: 1, id: m.id, ...body }, '*');   // an opaque origin takes no other target
    if (typeof m.cmd !== 'string' || !Object.hasOwn(commands, m.cmd)) return reply({ error: `unknown command ${String(m.cmd).slice(0, 40)}` });
    try { reply({ result: await commands[m.cmd](m.args ?? {}) }); } catch (error) { reply({ error: error.message || String(error) }); }
  };
  window.addEventListener('message', onMessage);
  return () => { window.removeEventListener('message', onMessage); closeSheet(); };
}
