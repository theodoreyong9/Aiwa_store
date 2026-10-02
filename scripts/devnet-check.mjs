#!/usr/bin/env node
// The run nothing else in this repository does: the burn, the creator fee and the registry's verification against the REAL Solana
// network (devnet by default; the burn is of devnet SOL, worth nothing). Everywhere else Solana is a stand-in that decodes the
// real transaction the wallet builds; this checks the stand-in against the truth.
//
//   node scripts/devnet-check.mjs                          a fresh wallet, funded by the devnet faucet
//   node scripts/devnet-check.mjs --sol 0.02 --T 0.4 --epochs 2
//   node scripts/devnet-check.mjs --rpc https://my-rpc.example
//   node scripts/devnet-check.mjs --fake                    the same steps against a stand-in Solana in this process (a dry run of
//                                                           the script itself: it proves nothing about the network)
//   node scripts/devnet-check.mjs --phrase "twelve words …"   a wallet you funded yourself (when the faucet refuses)
//
// Steps: make a wallet, fund it, BURN at T with a fresh creator address (so the fee is checked on chain: the creator account
// must receive exactly the fee), mine epochs (the deployment's own work), verify the evidence the way the registry does (asking
// Solana itself for the burn), check that a verifier that does not know the creator address counts no position (fails closed),
// and put an app through the registry's validation. Exit code 0 only if every check passes.
// Also runnable from GitHub: the "Devnet check" workflow (Actions tab, Run workflow).

import * as web3 from '@solana/web3.js';
import { AIWA } from 'aiwa-lib';
import {
  assessSubmission, deriveKeypairFromBip39Mnemonic, generateBip39Mnemonic, generateIdentity, creatorFeeLamports,
  SOLANA_INCINERATOR_ADDRESS, base58Encode, burnQuote,
} from 'aiwa-core';
import { buildAppPackage, validateSubmission, emptyStore, loadDeployment } from 'aiwa-registry';

globalThis.window = { solanaWeb3: web3 };       // aiwa-core looks for it here before it would fetch it from a CDN

const args = process.argv.slice(2);
const option = (name, fallback) => { const i = args.indexOf(`--${name}`); return i >= 0 && args[i + 1] && !args[i + 1].startsWith('--') ? args[i + 1] : fallback; };
const rpc = option('rpc', loadDeployment().rpc);
const sol = Number(option('sol', 0.01));
const T = Number(option('T', 0.4));
const epochs = Math.max(1, Number(option('epochs', 2)));
const phrase = option('phrase', process.env.DEVNET_PHRASE || '');
const fake = args.includes('--fake');

// A Solana in this process: it keeps balances and decodes the transaction the wallet builds. Only for --fake.
function standIn() {
  const balances = new Map();
  const transactions = {};
  let count = 0;
  const key = (k) => (typeof k === 'string' ? k : k.toBase58());
  return {
    getVersion: async () => ({ 'solana-core': 'stand-in' }),
    getBalance: async (k) => balances.get(key(k)) ?? 0,
    requestAirdrop: async (k, amount) => { balances.set(key(k), (balances.get(key(k)) ?? 0) + amount); return `airdrop${++count}`; },
    confirmTransaction: async () => ({}),
    getLatestBlockhash: async () => ({ blockhash: '11111111111111111111111111111111', lastValidBlockHeight: 1 }),
    sendRawTransaction: async (raw) => {
      const tx = web3.Transaction.from(raw);
      const keys = [tx.feePayer.toBase58()];
      const transfers = tx.instructions.map((ix) => {
        const d = web3.SystemInstruction.decodeTransfer(ix);
        return { from: d.fromPubkey.toBase58(), to: d.toPubkey.toBase58(), lamports: Number(d.lamports) };
      });
      for (const t of transfers) for (const k of [t.from, t.to]) if (!keys.includes(k)) keys.push(k);
      const pre = keys.map((k) => balances.get(k) ?? 0);
      for (const t of transfers) { balances.set(t.from, (balances.get(t.from) ?? 0) - t.lamports); balances.set(t.to, (balances.get(t.to) ?? 0) + t.lamports); }
      balances.set(keys[0], (balances.get(keys[0]) ?? 0) - 5000);
      const post = keys.map((k) => balances.get(k) ?? 0);
      const signature = `standin${++count}`;
      transactions[signature] = { slot: 10, transaction: { message: { accountKeys: keys } }, meta: { err: null, fee: 5000, preBalances: pre, postBalances: post } };
      return signature;
    },
    getTransaction: async (signature) => transactions[signature] ?? null,
  };
}

let passed = 0;
let failed = 0;
const say = (text = '') => console.log(text);
const step = (title) => say(`\n— ${title}`);
const check = (ok, what, detail = '') => { if (ok) passed++; else failed++; say(`  ${ok ? 'PASS' : 'FAIL'}  ${what}${detail ? `  (${detail})` : ''}`); return ok; };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function main() {
  const connection = fake ? standIn() : new web3.Connection(rpc, 'confirmed');
  const deployment = loadDeployment();
  // a creator address made for this run: its balance is read on chain
  const creator = web3.Keypair.generate().publicKey.toBase58();
  const rewardParams = { ...deployment.rewardParams, creatorFee: { address: creator, rateOfT: 0.001 } };

  step(fake ? 'The network: a stand-in (--fake)' : `The network: ${rpc}`);
  const version = await connection.getVersion();
  check(!!version['solana-core'], 'the RPC answers', `solana-core ${version['solana-core']}`);

  step('A wallet, and money to burn');
  const mnemonic = phrase || await generateBip39Mnemonic();
  const aiwa = new AIWA({ rewardParams });
  await aiwa.connect({ mnemonic });
  say(`  address ${aiwa.address}${phrase ? ' (from --phrase)' : ' (fresh)'}`);
  let balance = await connection.getBalance(new web3.PublicKey(aiwa.address));
  const needed = Math.round(sol * 1e9) + 10_000;
  for (let attempt = 1; balance < needed && attempt <= 4; attempt++) {
    try {
      const signature = await connection.requestAirdrop(new web3.PublicKey(aiwa.address), Math.max(needed, 1e9));
      await connection.confirmTransaction(signature, 'confirmed');
    } catch (err) {
      say(`  faucet, attempt ${attempt}: ${String(err.message).slice(0, 120)}`);
      await sleep(4000 * attempt);
    }
    balance = await connection.getBalance(new web3.PublicKey(aiwa.address));
  }
  if (!check(balance >= needed, `the wallet has ${balance / 1e9} SOL`, balance >= needed ? '' : 'the faucet refused: fund this address and run again with --phrase')) {
    say(`  phrase to reuse: ${mnemonic}`);
    return;
  }

  step(`Burn ${sol} SOL at T = ${T}`);
  const lamports = Math.round(sol * 1e9);
  const quote = burnQuote({ lamports, T, creatorFee: rewardParams.creatorFee });
  say(`  the wallet's quote: capital ${quote.capital}, incinerator ${quote.toIncinerator}, creator ${quote.toCreator} lamports`);
  const incineratorBefore = await connection.getBalance(new web3.PublicKey(SOLANA_INCINERATOR_ADDRESS));
  const signature = await aiwa.burn(lamports, connection, { T });
  say(`  signature ${signature}`);
  const creatorGot = await connection.getBalance(new web3.PublicKey(creator), 'finalized');
  check(creatorGot === creatorFeeLamports(lamports, T, rewardParams.creatorFee) && creatorGot === quote.toCreator, 'the creator account received exactly the fee, on chain', `${creatorGot} lamports`);
  const incineratorAfter = await connection.getBalance(new web3.PublicKey(SOLANA_INCINERATOR_ADDRESS), 'finalized');
  check(incineratorAfter - incineratorBefore >= quote.toIncinerator, 'the incinerator received the rest (at least: others burn there too)', `+${incineratorAfter - incineratorBefore}`);
  const mining = await aiwa.mining();
  check(mining && Math.abs(mining.capital - quote.capital / 1e9) < 1e-9 && mining.T === T, 'the wallet committed burned × (1 − T) as capital', `capital ${mining?.capital}`);

  step(`Mine ${epochs} epoch(s) (the deployment's own work: ${rewardParams.epochIterations} squarings each)`);
  const started = Date.now();
  await aiwa.advanceProgress({ epochs });
  say(`  ${((Date.now() - started) / 1000).toFixed(1)} s`);

  step('Verify the evidence the way the registry does: Solana itself is asked for the burn');
  const evidence = await aiwa.submissionEvidence();
  const verified = await assessSubmission({ rewardParams, evidence, domain: aiwa.identity.id, connection });
  check(verified.ok === true, 'the evidence is accepted', verified.reason);
  check(verified.mining && Math.abs(verified.mining.capital - quote.capital / 1e9) < 1e-9, 'with the position the wallet committed', `capital ${verified.mining?.capital}, epoch ${verified.mining?.epoch}`);
  check(verified.mining && Number(verified.mining.claimable) > 0, 'and something to claim', `claimable ${verified.mining?.claimable}`);
  const { creatorFee: _ignored, ...withoutFee } = rewardParams;
  const ignorant = await assessSubmission({ rewardParams: withoutFee, evidence, domain: aiwa.identity.id, connection });
  check(ignorant.mining === null, 'a verifier that does not know the creator address counts no position (fails closed)');

  step('An app through the registry\'s validation');
  const html = '<!doctype html><title>Devnet check</title><p>hello';
  const pkg = await buildAppPackage(aiwa.identity, { id: 'devnet-check', name: 'Devnet check', version: '1.0.0', description: 'made by scripts/devnet-check.mjs', html });
  const result = await validateSubmission({ submission: { format: 'aiwa-submission/1', kind: 'publish', package: pkg, evidence }, store: emptyStore(), deployment: { ...deployment, rewardParams }, connection });
  check(result.ok === true, 'the registry accepts the submission', result.reason);
  check(result.ok && result.accepted.entry.score > 0, 'and freezes a score / laps', result.ok ? `score ${result.accepted.entry.score}, laps ${result.accepted.entry.laps}` : '');
}

try {
  await main();
} catch (err) {
  failed++;
  say(`\n  ERROR  ${err.stack || err}`);
}
say(`\n${passed} passed, ${failed} failed`);
process.exit(failed === 0 ? 0 : 1);
