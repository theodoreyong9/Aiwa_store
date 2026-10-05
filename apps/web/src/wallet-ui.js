// The wallet tab: balances, burn (with what it does shown first), claim, send and receive (a signed code, carried by QR,
// copy or share), the author's apps, history, and the recovery phrase. All on aiwa-lib; nothing here decides what is valid.
// The wallet starts by itself (wallet.js): this page only shows it, and asks for something when there is nothing to start from.

import { encodeOfflineBundle, decodeOfflineBundle, fromUnits } from 'aiwa-lib';
import { config } from './config.js';
import { session, setSession, connected, onSession, onCatalog, catalog } from './session.js';
import { keptPhrase, startWallet, restoreHistory, keepRunning, solanaConnection } from './wallet.js';
import { secretsAreSafe } from './keys.js';
import { $, short, setId, showError, flash } from './ui.js';
import { openRefresh } from './publish-ui.js';
import { drawQr, scanQr } from './qr.js';
import { startLink, joinLink, watchLinks } from './link.js';

let displayRefreshTimer = null;
let lastOfflineBlob = null;
export { solanaConnection };

// ---------- what is visible depends on one fact: is a wallet running ----------

function updateGates() {
  const on = connected();
  $('welcome').hidden = on || starting;
  $('wallet-section').hidden = !on;
}
let starting = true;

async function refreshLocalState() {
  const aiwa = session.aiwa;
  if (!aiwa || !aiwa.identity) return;
  setId('out-address', aiwa.address);
  setId('out-identity-id', aiwa.identity.id);
  $('out-spendable').textContent = await aiwa.spendableBalance();
  $('out-claimable').textContent = await aiwa.claimable();
  $('out-balance').textContent = await aiwa.balance();
  const mining = await aiwa.mining();
  $('out-mining').textContent = mining
    ? `Mining ${mining.capital} SOL · T ${Math.round(mining.T * 100)} % · epoch ${mining.epoch} (${mining.sinceLastAction} since your last action)`
    : 'No burn yet';
}

// ---------- history: read from the event log, never a separate ledger ----------

function historyRow({ badge, badgeClass, label, detail, when }) {
  const row = document.createElement('div');
  row.className = 'history-item';
  const main = document.createElement('div');
  main.className = 'hist-main';
  const labelEl = document.createElement('div');
  labelEl.className = 'hist-label';
  const badgeEl = document.createElement('span');
  badgeEl.className = `hist-badge ${badgeClass}`;
  badgeEl.textContent = badge;
  labelEl.append(badgeEl, document.createTextNode(label));
  const detailEl = document.createElement('div');
  detailEl.className = 'hist-detail';
  detailEl.textContent = detail;
  main.append(labelEl, detailEl);
  const whenEl = document.createElement('div');
  whenEl.className = 'hist-when';
  whenEl.textContent = when ? new Date(when).toLocaleString() : '';
  row.append(main, whenEl);
  return row;
}

async function renderHistory() {
  const aiwa = session.aiwa;
  if (!aiwa || !aiwa.identity || !$('history-section').open) return;
  const list = $('history-list');
  const myId = aiwa.identity.id;

  const events = [];
  for await (const event of aiwa.log.since([])) events.push(event);
  events.sort((a, b) => b.createdAt - a.createdAt);

  const state = await aiwa.walletState();
  const amountFor = (claimId) => fromUnits(state.conservation.claims[claimId]?.amount ?? 0n);

  const rows = [];
  for (const event of events) {
    const p = event.payload;
    if (!p) continue;
    if (event.type === 'accrual' && p.domain === myId) {
      rows.push({ badge: 'burn', badgeClass: 'hist-neutral', label: 'Committed', detail: `+${p.b} SOL`, when: event.createdAt });
    } else if (event.type === 'claim' && p.domain === myId) {
      rows.push({ badge: 'claim', badgeClass: 'hist-neutral', label: 'Claimed', detail: `${p.amount} AIWA`, when: event.createdAt });
    } else if (event.type === 'transfer' && (p.from === myId || p.to === myId)) {
      const out = p.from === myId;
      rows.push({
        badge: out ? 'sent' : 'received', badgeClass: out ? 'hist-out' : 'hist-in',
        label: out ? 'Sent' : 'Received',
        detail: `${amountFor(p.claimId)} AIWA ${out ? 'to ' + short(p.to) : 'from ' + short(p.from)}`,
        when: event.createdAt,
      });
    }
  }

  list.replaceChildren();
  if (rows.length === 0) {
    const hint = document.createElement('p');
    hint.className = 'hint';
    hint.textContent = 'Nothing yet.';
    list.append(hint);
    return;
  }
  for (const row of rows) list.append(historyRow(row));
}

// ---------- burn ----------

// T is chosen at the burn, for what follows. T % of the burn does not count as capital; a fixed part of it goes to the
// creator when the deployment has a creator address (yellow paper §11). The wallet says so before anything is signed.
function burnTerms() {
  const sol = Number($('burn-amount').value);
  const percent = $('burn-t').value.trim() === '' ? 0 : Number($('burn-t').value);
  return { sol, percent, T: percent / 100 };
}
const sol = (lamports) => `${lamports / 1e9} SOL`;

function previewBurn() {
  const { sol: amount, percent, T } = burnTerms();
  const el = $('burn-preview');
  if (!(amount > 0 && percent >= 0 && percent <= 40) || !session.aiwa) { el.textContent = ''; return; }
  const quote = session.aiwa.burnQuote(Math.round(amount * 1e9), T);
  const creator = config.rewardParams.creatorFee;
  const parts = [`Counts as ${sol(quote.capital)} of capital.`];
  if (percent > 0) {
    parts.push(creator
      ? `${sol(quote.toCreator)} of the burn goes to the creator (${short(creator.address)}), the rest of the burn is destroyed.`
      : 'The T share of the burn is destroyed without counting (this deployment has no creator fee).');
  }
  parts.push('It replaces your current position, and pays what it accrued.');
  el.textContent = parts.join(' ');
}

async function burn() {
  const aiwa = session.aiwa;
  const { sol: amount, percent, T } = burnTerms();
  if (!(amount > 0)) { $('burn-result').textContent = 'Enter an amount in SOL.'; return; }
  if (!(percent >= 0 && percent <= 40)) { $('burn-result').textContent = 'T is between 0 and 40 %.'; return; }
  try {
    const signature = await aiwa.burn(Math.round(amount * 1e9), await solanaConnection(), { T });
    await refreshLocalState();
    $('burn-result').textContent = `Burned and committed: ${signature}`;
    renderHistory();
  } catch (err) {
    $('burn-result').textContent = `error: ${err.message}`;
  }
}

// ---------- send / receive ----------

const renderQr = (text) => drawQr($('qr'), text);

async function send() {
  const to = $('send-to').value.trim();
  const amount = $('send-amount').value.trim();
  if (!to || !amount) { $('send-result').textContent = 'Enter a recipient and an amount.'; return; }
  try {
    // Final the moment this resolves; drawing its QR is a separate step that must never make a real send look failed.
    const bundle = await session.aiwa.sendOfflineBundle(to, amount);
    lastOfflineBlob = encodeOfflineBundle(bundle);
    $('send-share').hidden = false;
    await refreshLocalState();
    $('send-result').textContent = 'Ready: show the QR, or share the code.';
    renderHistory();
    try { await renderQr(lastOfflineBlob); } catch { $('send-result').textContent = 'Ready: no QR image here, use Share or Copy.'; }
  } catch (err) {
    $('send-result').textContent = `error: ${err.message}`;
  }
}

async function receive() {
  try {
    let bundle;
    try { bundle = decodeOfflineBundle($('receive-blob').value.trim()); } catch { throw new Error('this is not an Aiwa code'); }
    await session.aiwa.receiveOfflineBundle(bundle);
    $('receive-blob').value = '';
    await refreshLocalState();
    $('receive-result').textContent = 'Received and verified.';
    renderHistory();
    warnIfAccused(session.aiwa, bundle);
  } catch (err) {
    $('receive-result').textContent = `Rejected: ${err.message}`;
  }
}

// A sender whose own signatures show two different histories (a fork) is something this wallet can prove, not suspect. It is looked for
// after the answer, because it replays what the log holds, and only for the identities in what was just received.
async function warnIfAccused(aiwa, bundle) {
  try {
    await aiwa.settled();                      // the reception it signs for what just arrived is part of the evidence
    renderStandings();
    const inBundle = new Set(bundle.events.flatMap((event) => [event.author, event.payload?.domain]));
    const accused = (await aiwa.accusations()).filter((a) => inBundle.has(a.domain));
    if (accused.length === 0 || session.aiwa !== aiwa) return;
    $('receive-result').textContent = `Received and verified. Warning: ${accused.map((a) => short(a.domain)).join(', ')} signed two different histories (a fork). Be careful with what it sends.`;
  } catch { /* nothing proven */ }
}

// ---------- who this phone has seen ----------

// What the wallet can say of another identity: where it stands (never below what is proven), what is proven, the vote of the
// observers it knows, and how fast it progresses compared with this wallet. Reported, never applied: it changes nobody's earnings.
function standingRow(s) {
  const row = document.createElement('div');
  row.className = 'idrow';
  const who = document.createElement('code');
  who.textContent = short(s.domain);
  const text = document.createElement('span');
  const parts = [s.epoch === null ? 'no progress seen' : `epoch ${s.epoch}`];
  if (s.provenAtLeast !== null) parts.push(`proven ≥ ${s.provenAtLeast}`);
  if (s.median !== null && s.median !== s.epoch) parts.push(`vote ${s.median}`);
  if (s.witnesses > 0) parts.push(`${s.witnesses} witness${s.witnesses === 1 ? '' : 'es'}`);
  if (s.pace) parts.push(`pace ×${Number(s.pace.ratio.toFixed(2))} yours`);
  if (s.forked) parts.push('⚠ signed two different histories');
  text.textContent = parts.join(' · ');
  row.append(who, text);
  return row;
}

async function renderStandings() {
  const aiwa = session.aiwa;
  if (!aiwa || !aiwa.identity || !$('standing-section').open) return;
  const list = $('standing-list');
  let all;
  try { all = await aiwa.standings(); } catch { return; }
  if (session.aiwa !== aiwa) return;
  list.replaceChildren();
  if (all.length === 0) {
    const hint = document.createElement('p');
    hint.className = 'hint';
    hint.textContent = 'Nobody yet. Receive a payment or link another phone.';
    list.append(hint);
    return;
  }
  for (const s of all) list.append(standingRow(s));
}

// ---------- link with another phone ----------

const linkMessage = (err) => err.message === 'cancelled' ? 'Cancelled.' : `error: ${err.message}`;
let watching = new WeakSet();

async function link(how) {
  const aiwa = session.aiwa;
  if (!aiwa || !aiwa.identity) return;
  try {
    if (!watching.has(aiwa)) {
      watching.add(aiwa);
      await watchLinks(aiwa, async ({ phones, received }) => {
        if (session.aiwa !== aiwa) return;
        $('link-status').textContent = phones === 0 ? 'No phone linked.' : `Linked with ${phones} phone${phones === 1 ? '' : 's'}${received > 0 ? ` · ${received} events received` : ''}.`;
        if (received > 0) {
          await aiwa.settled();                     // what it signs for what just arrived is part of the evidence
          await refreshLocalState();
          renderHistory();
          renderStandings();
        }
      });
    }
    $('link-status').textContent = how === 'start' ? 'Waiting for the other phone…' : 'Reading the other phone…';
    await (how === 'start' ? startLink(aiwa) : joinLink(aiwa));
    if (!/^Linked/.test($('link-status').textContent)) $('link-status').textContent = 'Connecting…';    // the link may already be up
  } catch (err) {
    $('link-status').textContent = linkMessage(err);
  }
}

// ---------- the author's apps ----------

/** The apps of the store whose author is this wallet. */
function showMyApps(apps) {
  const aiwa = session.aiwa;
  const mine = aiwa?.identity ? apps.filter((app) => app.author === aiwa.address) : [];
  $('my-apps-section').hidden = mine.length === 0;
  const list = $('my-apps-list');
  list.replaceChildren();
  for (const app of mine) {
    const row = document.createElement('div');
    row.className = 'idrow';
    const text = document.createElement('span');
    text.textContent = `${app.name} v${app.version} · score/laps ${(app.score / Math.max(1, app.laps)).toPrecision(3)}`;
    const refresh = document.createElement('button');
    refresh.type = 'button';
    refresh.className = 'icon';
    refresh.textContent = 'Refresh ranking';
    refresh.addEventListener('click', () => openRefresh(app));
    row.append(text, refresh);
    list.append(row);
  }
}

// ---------- the recovery phrase ----------

// A new wallet shows its 12 words once, and asks for them to be written down: on another phone they are the only way back.
const ACK = 'aiwa-store:phrase-acknowledged';
const acknowledged = () => { try { return localStorage.getItem(ACK) === '1'; } catch { return true; } };

function showPhraseNotice() {
  const notice = $('phrase-notice');
  notice.hidden = acknowledged();
  if (!notice.hidden) $('phrase-words').textContent = session.aiwa.recoveryPhrase;
}

function recoverySection() {
  $('recovery-where').textContent = secretsAreSafe()
    ? 'Kept in this phone\'s keystore.'
    : 'Kept in this browser\'s storage: anyone who can read this browser profile can read it. The Android app keeps it in the keystore.';
  $('btn-show-phrase').addEventListener('click', () => {
    const out = $('out-phrase');
    out.hidden = !out.hidden;
    out.textContent = out.hidden ? '' : session.aiwa.recoveryPhrase;
    $('btn-show-phrase').textContent = out.hidden ? 'Show my 12 words' : 'Hide';
  });
  $('btn-phrase-done').addEventListener('click', () => {
    try { localStorage.setItem(ACK, '1'); } catch { /* shown again next time */ }
    $('phrase-notice').hidden = true;
  });
  $('btn-replace').addEventListener('click', async () => {
    const mnemonic = $('replace-phrase').value.trim();
    if (!mnemonic) return;
    if (!$('replace-confirm').hidden) { await switchWallet(mnemonic, $('replace-result')); return; }
    $('replace-confirm').hidden = false;
    $('btn-replace').textContent = 'Yes, replace it';
  });
}

// ---------- start ----------

function trackProgress(aiwa) {
  // A real backlog since the last checkpoint is folded on the first balance read; a handful of events stays silent.
  aiwa.onMaterializeProgress = (current, total) => {
    if (total <= 5) { $('sync-progress').hidden = true; return; }
    $('sync-progress').hidden = false;
    $('sync-progress-label').textContent = `${current} / ${total}`;
    $('sync-progress-fill').style.width = `${Math.round((current / total) * 100)}%`;
    if (current >= total) setTimeout(() => { $('sync-progress').hidden = true; }, 500);
  };
}

async function run(aiwa) {
  setSession(aiwa);
  trackProgress(aiwa);
  await refreshLocalState();     // before it is shown: never an empty wallet on screen
  starting = false;
  updateGates();
  const restored = await restoreHistory(aiwa);
  $('restore-note').hidden = !restored;
  if (restored) {
    $('restore-note').textContent = `Your history came back from ${restored.source === 'archive' ? 'your archive node' : 'the registry'}: epoch ${restored.epoch}.`;
    await refreshLocalState();
  }
  keepRunning(aiwa);     // after the restore: working epochs on an empty log would fork the history that was about to come back
  showPhraseNotice();
  displayRefreshTimer = setInterval(() => { if (session.aiwa) refreshLocalState(); }, 5000);
}

async function stop() {
  clearInterval(displayRefreshTimer);
  displayRefreshTimer = null;
  const aiwa = session.aiwa;
  setSession(null);
  await aiwa?.disconnect();
}

/** Makes (no phrase) or restores (a phrase) the wallet; shows the problem in `errorEl` if it cannot. */
async function switchWallet(mnemonic, errorEl) {
  showError(errorEl, '');
  try {
    const aiwa = await startWallet({ mnemonic });
    await stop();
    try { localStorage.removeItem(ACK); } catch { /* cosmetic */ }
    $('replace-phrase').value = '';
    $('replace-confirm').hidden = true;
    $('btn-replace').textContent = 'Use this phrase';
    await run(aiwa);
  } catch (err) {
    showError(errorEl, err.message);
  }
}

export async function initWallet() {
  onCatalog(showMyApps);
  onSession(() => showMyApps(catalog.apps));
  $('btn-create').addEventListener('click', () => switchWallet(undefined, $('connect-error')));
  $('btn-restore').addEventListener('click', () => {
    const mnemonic = $('mnemonic').value.trim();
    if (!mnemonic) { showError($('connect-error'), 'Type your 12 words.'); return; }
    switchWallet(mnemonic, $('connect-error'));
  });
  $('history-section').addEventListener('toggle', renderHistory);
  $('standing-section').addEventListener('toggle', renderStandings);
  $('btn-link-start').addEventListener('click', () => link('start'));
  $('btn-link-join').addEventListener('click', () => link('join'));
  $('burn-amount').addEventListener('input', previewBurn);
  $('burn-t').addEventListener('input', previewBurn);
  $('btn-burn').addEventListener('click', burn);
  $('btn-claim').addEventListener('click', async () => {
    const claimable = await session.aiwa.claimable();
    if (Number(claimable) <= 0) return;
    await session.aiwa.claim(claimable);
    await refreshLocalState();
    renderHistory();
  });
  $('btn-refresh-sol').addEventListener('click', async () => {
    try {
      const lamports = await session.aiwa.solBalance(await solanaConnection());
      $('out-sol-balance').textContent = `${lamports / 1e9} SOL`;
    } catch (err) {
      $('out-sol-balance').textContent = `error: ${err.message}`;
    }
  });
  $('btn-send').addEventListener('click', send);
  $('btn-share').addEventListener('click', async () => {
    if (navigator.share) await navigator.share({ text: lastOfflineBlob, title: 'Aiwa payment' });
    else $('send-result').textContent = 'Sharing is not supported here, use Copy.';
  });
  $('btn-copy-blob').addEventListener('click', async () => {
    try { await navigator.clipboard.writeText(lastOfflineBlob); flash($('btn-copy-blob'), 'Copied'); }
    catch { $('send-result').textContent = 'The browser refused the clipboard.'; }
  });
  $('btn-receive').addEventListener('click', receive);
  $('btn-scan').addEventListener('click', async () => {
    const text = await scanQr($('scan-video'));
    if (text === null) $('receive-result').textContent = 'No camera scanning in this browser: paste the code.';
    else $('receive-blob').value = text;
  });
  recoverySection();
  // an app that uses the wallet (app-door.js) moved some AIWA: show it
  window.addEventListener('aiwa:wallet-changed', () => { refreshLocalState().then(renderHistory).catch(() => {}); });

  // The wallet starts by itself when this phone already has one; a phone that has none makes the person choose once.
  try {
    const phrase = await keptPhrase();
    if (phrase) await run(await startWallet({ mnemonic: phrase }));
  } catch (err) {
    showError($('connect-error'), err.message);
  }
  starting = false;
  updateGates();
}
