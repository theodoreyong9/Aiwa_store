// The door between the Store and an app in its frame. An app is untrusted code with an opaque origin: by itself it cannot
// reach the wallet (yellow paper §17.2). An app that DECLARES it uses the wallet (<meta name="aiwa-wallet" content="pay">)
// gets this door instead: messages {aiwa: 1, id, cmd, args} to the page, answered {aiwa: 1, id, result | error}.
//
// What it is, and is not. The door never pays without the player. The first payment an app asks for opens a sheet of the Store (not of
// the app): who asks, how much, to whom. The player refuses, allows that one payment, or allows a BUDGET until the app is closed;
// payments inside the budget go through ("sign once, click many times" still holds for a game), the first one beyond it asks again,
// and a refusal silences the app's requests for half a minute. The budget lives in memory and ends with the viewer. A banner still
// says, as the app opens, that it uses the wallet. What stays open: an app is not reviewed, so what it shows around the sheet is its
// own (the sheet itself is the Store's), and a budget the player allows is spent as the app wishes.
//
// The commands are the small set a game between two phones needs:
//   whoami                  { id, address, balance, spendable }       what the app may know of the player
//   pay { to, amount }      { blob }                                  a payment of `amount` AIWA to the identity `to`, as an
//                                                                      offline bundle, signed by the channel's session key, once the
//                                                                      player has allowed it (see above)
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
import { $, short } from './ui.js';

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


// ---------- the player's say: nothing is paid until they allow it ----------

const SCALE = 18n;
const unitsOf = (s) => { const [i, f = ''] = String(s).split('.'); return BigInt(i) * 10n ** SCALE + BigInt(f.padEnd(Number(SCALE), '0')); };
const textOf = (u) => { const i = u / 10n ** SCALE; const f = (u % 10n ** SCALE).toString().padStart(Number(SCALE), '0').replace(/0+$/, ''); return f ? `${i}.${f}` : String(i); };
const SILENCE_MS = 30000;            // after a refusal, the app's requests are refused without asking again for this long

let consent = null;                  // the sheet being shown: { resolve }

function closeConsent(answer) {
  const c = consent;
  consent = null;
  $('pay-sheet').hidden = true;
  c?.resolve(answer);
}

/** Asks the player. Resolves with the amount (in units) they allow in total, or null if they refuse. */
function askConsent({ name, to, amount }) {
  closeConsent(null);
  $('pay-title').textContent = `“${String(name).slice(0, 60)}” wants to pay`;
  $('pay-amount').textContent = textOf(amount);
  $('pay-to').textContent = short(to);
  $('pay-to').title = to;
  $('pay-budget').value = textOf(amount * 10n);
  $('pay-budget').removeAttribute('aria-invalid');
  $('pay-sheet').hidden = false;
  return new Promise((resolve) => { consent = { resolve, amount }; });
}

$('pay-once').addEventListener('click', () => consent && closeConsent(consent.amount));
$('pay-deny').addEventListener('click', () => closeConsent(null));
$('pay-budget-go').addEventListener('click', () => {
  if (!consent) return;
  const text = $('pay-budget').value.trim();
  const ok = AMOUNT.test(text) && unitsOf(text) >= consent.amount;
  $('pay-budget').toggleAttribute('aria-invalid', !ok);
  if (ok) closeConsent(unitsOf(text));
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
  async pay({ to, amount }, door) {
    const aiwa = wallet();
    if (!IDENTITY.test(String(to))) throw new Error('to: an identity id (64 hex characters)');
    if (!AMOUNT.test(String(amount)) || Number(amount) <= 0) throw new Error('amount: a positive number of AIWA');
    const units = unitsOf(amount);
    // One payment at a time: a second request waits for the answer to the first, and may then fit in the budget just allowed.
    const turn = door.queue.then(async () => {
      if (Date.now() < door.silentUntil) throw new Error('the player refused a payment a moment ago');
      if (door.allowance < units) {
        const allowed = await askConsent({ name: door.name, to: String(to), amount: units });
        if (allowed === null) { door.silentUntil = Date.now() + SILENCE_MS; throw new Error('the player refused this payment'); }
        door.allowance = allowed;
      }
      door.allowance -= units;
      try {
        const key = `${aiwa.identity.id}:${to}`;
        // "Sign once, click many times": the first payment to a peer signs one delegation with the wallet's key; this one and every
        // later one is signed by the channel's session key, with no further use of the wallet's own key.
        if (!channels.has(key)) channels.set(key, await aiwa.openChannel(to, { requireNetwork: false }));
        const bundle = await channels.get(key).sendOfflineBundle(String(amount));
        walletChanged();
        return { blob: encodeOfflineBundle(bundle) };
      } catch (error) { door.allowance += units; throw error; }       // nothing left the wallet: the budget is not spent
    });
    door.queue = turn.catch(() => {});
    return turn;
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
export function attachDoor(frame, { name = 'This app' } = {}) {
  const door = { name, allowance: 0n, silentUntil: 0, queue: Promise.resolve() };      // what the player allowed this app, until it is closed
  const onMessage = async (event) => {
    const m = event.data;
    if (event.source !== frame.contentWindow || !m || m.aiwa !== 1 || !Number.isInteger(m.id)) return;
    const reply = (body) => frame.contentWindow?.postMessage({ aiwa: 1, id: m.id, ...body }, '*');   // an opaque origin takes no other target
    if (typeof m.cmd !== 'string' || !Object.hasOwn(commands, m.cmd)) return reply({ error: `unknown command ${String(m.cmd).slice(0, 40)}` });
    try { reply({ result: await commands[m.cmd](m.args ?? {}, door) }); } catch (error) { reply({ error: error.message || String(error) }); }
  };
  window.addEventListener('message', onMessage);
  return () => { window.removeEventListener('message', onMessage); closeSheet(); closeConsent(null); };
}
