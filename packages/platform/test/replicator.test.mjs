import { test } from 'node:test';
import assert from 'node:assert/strict';
import { generateIdentity, createEvent, EventLog } from 'aiwa-core';
import { LoopbackTransport } from '../src/transport.js';
import { Replicator } from '../src/replicator.js';

async function chainOfEvents(identity, domain, count) {
  const events = [];
  let parents = [];
  for (let i = 0; i < count; i++) {
    const event = await createEvent(identity, { domain, parents, type: 'note', payload: { i } });
    events.push(event);
    parents = [event.id];
  }
  return events;
}

function sleep(ms) { return new Promise((resolve) => setTimeout(resolve, ms)); }

async function waitUntil(predicate, { timeoutMs = 2000, intervalMs = 5 } = {}) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (await predicate()) return;
    await sleep(intervalMs);
  }
  throw new Error('waitUntil: real condition never became true within the timeout');
}

// A linear chain's own head length stays exactly 1 at EVERY intermediate
// step of appendMany()'s own incremental, one-at-a-time application —
// comparing head().length alone can match prematurely, mid-append, long
// before every real event has actually landed. Waiting for every real,
// specific event id to genuinely be present is the only real signal.
async function waitUntilAllPresent(log, events) {
  await waitUntil(async () => {
    for (const event of events) if (!(await log.has(event.id))) return false;
    return true;
  });
}

// LoopbackTransport keeps a real, process-wide static registry (see its
// own header) — a peer id that never disconnect()s stays registered
// forever, so every real pair in this file uses a fresh, unique real
// identity id (never a hardcoded 'a'/'b') and stops both replicators
// before returning, exactly the same real discipline aiwa-lib's own
// LoopbackTransport tests already established.
async function connectedPair({ chunkSizeA, chunkSizeB, logA, logB } = {}) {
  const idA = await generateIdentity();
  const idB = await generateIdentity();
  const transportA = new LoopbackTransport(idA.id);
  const transportB = new LoopbackTransport(idB.id);
  const replicatorA = new Replicator({ transport: transportA, log: logA ?? new EventLog(), domain: 'aiwa', chunkSize: chunkSizeA });
  const replicatorB = new Replicator({ transport: transportB, log: logB ?? new EventLog(), domain: 'aiwa', chunkSize: chunkSizeB });
  return { transportA, transportB, replicatorA, replicatorB };
}

function spyOnEventsMessages(transport) {
  const sizes = [];
  const originalSend = transport.send.bind(transport);
  transport.send = async (peer, bytes) => {
    const msg = JSON.parse(new TextDecoder().decode(bytes));
    if (msg.type === 'EVENTS') sizes.push(msg.events.length);
    return originalSend(peer, bytes);
  };
  return sizes;
}

test('a small backlog still syncs in a single EVENTS message — chunking never fires unnecessarily', async () => {
  const identity = await generateIdentity();
  const events = await chainOfEvents(identity, 'aiwa', 5);
  const logA = new EventLog();
  await logA.appendMany(events);
  const logB = new EventLog();

  const { transportA, replicatorA, replicatorB } = await connectedPair({ logA, logB });
  const sizesToB = spyOnEventsMessages(transportA);

  await replicatorB.start();
  await replicatorA.start();
  try {
    await waitUntilAllPresent(logB, events);
    assert.deepEqual(sizesToB, [5], 'a backlog under the chunk size must still be one real message, not needlessly split');
    for (const event of events) assert.equal(await logB.has(event.id), true);
  } finally {
    await replicatorA.stop();
    await replicatorB.stop();
  }
});

test('THE REAL FIX: a backlog larger than the chunk size is split into several bounded EVENTS messages, never one unbounded message', async () => {
  const identity = await generateIdentity();
  const events = await chainOfEvents(identity, 'aiwa', 25);
  const logA = new EventLog();
  await logA.appendMany(events);
  const logB = new EventLog();

  const { transportA, replicatorA, replicatorB } = await connectedPair({ chunkSizeA: 10, chunkSizeB: 10, logA, logB });
  const sizesToB = spyOnEventsMessages(transportA);

  await replicatorB.start();
  await replicatorA.start();
  try {
    await waitUntilAllPresent(logB, events);

    // The real property this closes: never one message carrying the
    // entire 25-event backlog.
    for (const size of sizesToB) assert.ok(size <= 10, `every real chunk must respect chunkSize, got ${size}`);
    assert.ok(sizesToB.length >= 3, `a 25-event backlog at chunkSize 10 must genuinely take at least 3 real messages, got ${sizesToB.length}`);
    assert.equal(sizesToB.reduce((sum, n) => sum + n, 0), 25, 'every real event accounted for exactly once across all chunks sent to B');

    // Correctness, not just chunking: every real event genuinely arrives, and B's own head converges on the same real tip.
    for (const event of events) assert.equal(await logB.has(event.id), true);
    assert.deepEqual(await logB.head(), [events[events.length - 1].id]);
  } finally {
    await replicatorA.stop();
    await replicatorB.stop();
  }
});

test('a chunked sync survives an unrelated publish() ACK arriving mid-sync — the chunk queue only ever advances on its own real ACK', async () => {
  const identity = await generateIdentity();
  const events = await chainOfEvents(identity, 'aiwa', 22);
  const logA = new EventLog();
  await logA.appendMany(events);
  const logB = new EventLog();

  const { replicatorA, replicatorB } = await connectedPair({ chunkSizeA: 10, chunkSizeB: 10, logA, logB });

  await replicatorB.start();
  await replicatorA.start();
  try {
    // A real, unrelated, standalone event, published mid-sync — its own
    // real ACK from B must never be mistaken for the pending chunk's ACK.
    const strayIdentity = await generateIdentity();
    const stray = await createEvent(strayIdentity, { domain: 'aiwa', parents: [], type: 'note', payload: { stray: true } });
    await replicatorA.publish([stray]);

    await waitUntilAllPresent(logB, [...events, stray]);
    for (const event of events) assert.equal(await logB.has(event.id), true);
    assert.equal(await logB.has(stray.id), true, 'the real, unrelated published event must still land correctly');
  } finally {
    await replicatorA.stop();
    await replicatorB.stop();
  }
});

test('a real, connecting peer whose own log already has a real prefix only receives the real, genuinely missing suffix, still chunked and bounded', async () => {
  const identity = await generateIdentity();
  const events = await chainOfEvents(identity, 'aiwa', 15);
  const logA = new EventLog();
  await logA.appendMany(events);
  const logB = new EventLog();
  await logB.appendMany(events.slice(0, 8)); // B already has the real first 8

  const { transportA, replicatorA, replicatorB } = await connectedPair({ chunkSizeA: 5, chunkSizeB: 5, logA, logB });
  const sizesToB = spyOnEventsMessages(transportA);

  await replicatorB.start();
  await replicatorA.start();
  try {
    await waitUntilAllPresent(logB, events);
    for (const size of sizesToB) assert.ok(size <= 5, `every real chunk to B must respect chunkSize, got ${size}`);
    assert.equal(sizesToB.reduce((sum, n) => sum + n, 0), 7, 'only the real 7 missing events reach B in total — never the already-known first 8 again');
    for (const event of events) assert.equal(await logB.has(event.id), true);
  } finally {
    await replicatorA.stop();
    await replicatorB.stop();
  }
});

test('a real, empty-to-empty connection sends no EVENTS message at all', async () => {
  const { transportA, replicatorA, replicatorB } = await connectedPair({});
  const sizesToB = spyOnEventsMessages(transportA);

  await replicatorB.start();
  await replicatorA.start();
  try {
    await sleep(50);
    assert.deepEqual(sizesToB, [], 'nothing real to send — no EVENTS message should ever fire');
  } finally {
    await replicatorA.stop();
    await replicatorB.stop();
  }
});
