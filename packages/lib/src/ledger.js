// The wallet's log and the state folded from it.
//
// Folding the whole log on every call would cost more the longer a wallet lives, so the last folded state is kept
// and only what was appended since is folded onto it. The cache is in memory, per instance.

import {
  EventLog, createMemoryBackend, createIndexedDbBackend, createEvent, initialWalletState, materializeWalletFromWireEvents,
  withConfirmedBurns, findLatestCheckpoint, checkpointWalletState, buildCheckpointEvent,
} from 'aiwa-core';
import { collectAncestors } from './ancestors.js';

const MINING_TYPES = new Set(['burn-record', 'progression', 'accrual', 'claim']);
/** A burn record, progression, accrual or claim written by `domain`: what a validator needs to re-derive its mining. */
export const isMiningEvent = (event, domain) => MINING_TYPES.has(event.type) && event.author === domain;

/** Order-independent comparison of two head sets. */
export function sameHeadSet(a, b) {
  if (a.length !== b.length) return false;
  const setA = new Set(a);
  return b.every((id) => setA.has(id));
}

/**
 * Every event still in the log that is reachable from `ids`. Unlike collectAncestors() it does not stop at an event
 * that was pruned (a checkpoint's own parents are gone by design).
 */
export async function knownAncestors(log, ids) {
  const seen = new Set();
  const out = [];
  const stack = [...ids];
  while (stack.length > 0) {
    const id = stack.pop();
    if (seen.has(id)) continue;
    seen.add(id);
    const event = await log.get(id);
    if (!event) continue;
    out.push(event);
    stack.push(...event.parents);
  }
  return out;
}

// Whether every event of `newEvents` descends from every one of `oldHeads` (and so, in canonical order, comes after
// all of what those heads stand for): each event of the batch that has no parent inside the batch must cite all of
// the old heads.
function descendsFromAll(newEvents, oldHeads) {
  if (newEvents.length === 0 || !oldHeads || oldHeads.length === 0) return true;
  const inBatch = new Set(newEvents.map((e) => e.id));
  for (const event of newEvents) {
    if (event.parents.some((p) => inBatch.has(p))) continue;
    if (!oldHeads.every((head) => event.parents.includes(head))) return false;
  }
  return true;
}

export class Ledger {
  constructor({ rewardParams, logDomain, dbName, backend, miningArchive, keepMiningHistory }) {
    this.rewardParams = rewardParams;
    this.logDomain = logDomain;
    this.log = new EventLog(backend ?? (dbName ? createIndexedDbBackend(dbName) : createMemoryBackend()));
    // Pruning to a checkpoint deletes old events. A validator that never held them (a registry checking this domain's
    // age) needs the ones that prove it, so the domain's own mining events are set aside here, whole, before pruning.
    // `keepMiningHistory: false` opts out: the history then starts at the last checkpoint.
    this.miningArchive = keepMiningHistory ? (miningArchive ?? (dbName && !backend ? createIndexedDbBackend(`${dbName}-mining`) : createMemoryBackend())) : null;
    // Burns this wallet has confirmed against Solana (signature -> the record fetchBurnRecord returned). A commitment
    // counts only as far as confirmed burns cover it, so this decides what the wallet credits, to itself and to others.
    this.burnRecords = {};
    // Survives disconnect(): a Channel can still fold the log after the root identity clears, and needs to know whose
    // checkpoint to look for.
    this.domainId = null;
    // Optional (current, total) => void, called while a potentially large backlog is folded.
    this.onProgress = null;
    this.reset();
  }

  /** Forget the folded state: the next state() folds again from the last checkpoint. */
  reset() {
    this._folded = null;
    this._foldedHeads = null;
    // Everything already folded, by id, not just the last heads: a progression event may cite its domain's last
    // progression as an extra parent, and that edge reaches past a heads-only boundary back into folded territory.
    this._covered = new Set();
  }

  /** A newly confirmed burn changes what earlier commitments were worth: fold again from the base. */
  noteBurnRecords(records) {
    Object.assign(this.burnRecords, records);
    this.reset();
  }

  /** The wallet state of everything in the log: the cache, plus what was appended since. */
  async state() {
    const heads = await this.log.head();
    if (this._foldedHeads && sameHeadSet(this._foldedHeads, heads)) return this._folded;

    let base = this._folded;
    if (!base) {
      const domain = this.domainId;
      const checkpoint = domain ? await findLatestCheckpoint(this.log, domain) : null;
      if (checkpoint) {
        base = checkpointWalletState(checkpoint);
        this._covered = new Set(checkpoint.payload.coveredHeads);
      }
    }

    const newEvents = await collectAncestors(this.log, heads, { excludeIds: this._covered });
    // The fold order is canonical (the same for every reader holding the same events), so that a conflict between two
    // branches has the same winner everywhere. Folding new events on top of the folded state equals folding everything
    // in that order only when they all come after it, i.e. descend from everything folded. One that does not (a
    // concurrent branch that just arrived) may belong BEFORE some of what is folded: start again from the checkpoint.
    // Rare, and bounded by the checkpoint; a wallet's own events, each citing every head, stay incremental.
    if (this._folded && !descendsFromAll(newEvents, this._foldedHeads)) {
      this.reset();
      return this.state();
    }
    // The wire events, not adapted ones: a checkpoint's signature needs the event's `author`.
    const state = await materializeWalletFromWireEvents(
      this.rewardParams, newEvents, this.onProgress, undefined, {}, withConfirmedBurns(base ?? initialWalletState(), this.burnRecords),
    );
    for (const event of newEvents) this._covered.add(event.id);
    this._folded = state;
    this._foldedHeads = heads;
    return state;
  }

  /** Signs an event of `type` with `identity` and appends it, by default on top of the log's current heads. */
  async append(identity, type, payload, { parents } = {}) {
    const event = await createEvent(identity, { domain: this.logDomain, parents: parents ?? await this.log.head(), type, payload });
    await this.log.append(event);
    return event;
  }

  /** A checkpoint of the current state, signed by `identity` and appended: what pruning can safely discard against. */
  async checkpoint(identity) {
    const state = await this.state();
    const heads = await this.log.head();
    const event = await buildCheckpointEvent(identity, { logDomain: this.logDomain, parents: heads, coveredHeads: heads, walletState: state });
    await this.log.append(event);
    return { eventId: event.id, coveredHeads: heads };
  }

  /** Physically discards everything the latest checkpoint accounts for. Returns how many events went (0 if there is no checkpoint). */
  async pruneToCheckpoint() {
    const domain = this.domainId;
    if (!domain) throw new Error('AIWA: pruneToLastCheckpoint needs to know your own domain — connect() at least once first.');
    const checkpoint = await findLatestCheckpoint(this.log, domain);
    if (!checkpoint) return 0;
    if (this.miningArchive) {
      for (const event of await knownAncestors(this.log, checkpoint.payload.coveredHeads)) {
        if (isMiningEvent(event, domain)) await this.miningArchive.putEvent(event);
      }
    }
    return this.log.pruneBeforeCheckpoint(checkpoint.id);
  }
}
