import { test } from 'node:test';
import assert from 'node:assert/strict';
import { PeerManager } from '../src/peer-manager.js';
import { CompositeTransport } from '../src/composite-transport.js';

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
async function waitUntil(predicate, { timeoutMs = 2000 } = {}) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) { if (await predicate()) return; await sleep(5); }
  throw new Error('waitUntil: the condition never became true');
}

// A transport whose peers the test decides, and which says when they change.
function fakeTransport(peers = []) {
  const joins = new Set();
  const leaves = new Set();
  return {
    list: [...peers],
    peers() { return [...this.list]; },
    onPeerJoin(h) { joins.add(h); return () => joins.delete(h); },
    onPeerLeave(h) { leaves.add(h); return () => leaves.delete(h); },
    add(id) { this.list.push(id); for (const h of joins) h(id); },
    remove(id) { this.list = this.list.filter((p) => p !== id); for (const h of leaves) h(id); },
  };
}

function fakeLobby({ enabled = true, connected = true } = {}) {
  return { enabled, connected, calls: [], async connect() { this.connected = true; this.calls.push('connect'); }, async disconnect() { this.connected = false; this.calls.push('disconnect'); } };
}

function setup({ lobbyPeers = [], directPeers = [], introduce = async () => { throw new Error('nobody to offer'); }, ...options } = {}) {
  const lobbyT = fakeTransport(lobbyPeers);
  const direct = fakeTransport(directPeers);
  const lobby = Object.assign(fakeLobby(options.lobby), { peers: () => lobbyT.peers() });
  const all = { peers: () => [...new Set([...direct.peers(), ...lobbyT.peers()])], onPeerJoin: (h) => { const a = direct.onPeerJoin(h); const b = lobbyT.onPeerJoin(h); return () => { a(); b(); }; }, onPeerLeave: (h) => { const a = direct.onPeerLeave(h); const b = lobbyT.onPeerLeave(h); return () => { a(); b(); }; } };
  const asked = [];
  const introducer = { async requestIntroduction(mediator, opts) { asked.push({ mediator, exclude: opts.exclude }); return introduce(mediator, opts); } };
  const manager = new PeerManager({ transport: all, direct, lobby, introducer, target: 3, low: 2, retryMs: 50, answerMs: 200, ...options.manager });
  return { manager, lobby, lobbyT, direct, asked };
}

test('with enough direct peers the wallet leaves the room, and goes back when it falls too low', async () => {
  const { manager, lobby, direct } = setup({ lobbyPeers: ['l1'], directPeers: ['d1', 'd2'] });
  manager.start();
  await sleep(20);
  assert.deepEqual(lobby.calls, [], 'two direct peers are not enough (target 3)');
  direct.add('d3');
  await waitUntil(() => lobby.calls.includes('disconnect'));
  assert.equal(lobby.connected, false);
  direct.remove('d3'); direct.remove('d2');                  // one direct peer left: below 2
  await waitUntil(() => lobby.calls.includes('connect'));
  assert.equal(lobby.connected, true);
  manager.stop();
});

test('a machine that is always on stays in the room', async () => {
  const { manager, lobby } = setup({ directPeers: ['d1', 'd2', 'd3', 'd4'], manager: { stay: true } });
  manager.start();
  await sleep(30);
  assert.deepEqual(lobby.calls, []);
  manager.stop();
});

test('a person who turned the network off is not pulled back into the room, and nobody is asked for introductions', async () => {
  const { manager, lobby, asked } = setup({ lobby: { enabled: false, connected: false }, lobbyPeers: [], directPeers: ['d1'] });
  manager.enabled = false;
  manager.start();
  await sleep(30);
  assert.deepEqual(lobby.calls, []);
  assert.equal(asked.length, 0);
  manager.stop();
});

test('below the target it asks a peer to introduce it to its own, skips the one that has nobody, and keeps what it already has out of the offer', async () => {
  const { manager, direct, asked } = setup({
    lobbyPeers: ['l1', 'l2'], directPeers: [],
    introduce: async (mediator) => { if (mediator === 'l1') throw new Error('nobody to offer'); await sleep(10); },
  });
  manager.start();
  await waitUntil(() => asked.length >= 2);
  assert.deepEqual(asked.slice(0, 2).map((a) => a.mediator), ['l1', 'l2'], 'the first had nobody: the next is asked at once');
  assert.deepEqual(asked[0].exclude, [], 'nothing to skip yet');
  direct.add('l2');                                            // an introduction made a direct link
  await waitUntil(() => asked.some((a) => a.exclude.includes('l2')));
  manager.stop();
});

test('nobody to offer: it waits before asking again, by itself, and asks nobody when it has no peer', async () => {
  const none = setup({ lobbyPeers: [], directPeers: [] });
  none.manager.start();
  await sleep(30);
  assert.equal(none.asked.length, 0, 'no peer, nobody to ask');
  none.manager.stop();

  const { manager, asked } = setup({ lobbyPeers: ['l1'], directPeers: [] });   // the default introduce() refuses
  manager.start();
  await waitUntil(() => asked.length === 1);
  await sleep(20);
  assert.equal(asked.length, 1, 'it does not hammer the peer');
  await waitUntil(() => asked.length === 2, { timeoutMs: 1000 });             // retryMs 50: it comes back by itself
  manager.stop();
});

test('an introduction that never answers is given up on', async () => {
  const { manager, asked } = setup({ lobbyPeers: ['l1'], introduce: () => new Promise(() => {}) });
  manager.start();
  await waitUntil(() => asked.length === 1);
  await waitUntil(() => asked.length === 2, { timeoutMs: 1500 });             // answerMs 200, then retryMs 50
  manager.stop();
});

test('the composite hands the signaling of a direct connection to the transport that makes them', async () => {
  const calls = [];
  const direct = { ...fakeTransport(), async createOfferFor(p) { calls.push(['offer', p]); return 'o'; }, async acceptOffer(b, o) { calls.push(['accept', b, o.peerId]); return 'a'; }, async completeConnection(p, b) { calls.push(['complete', p, b]); }, closePeer(p) { calls.push(['close', p]); } };
  const lobby = fakeTransport();
  const composite = new CompositeTransport([lobby, direct]);
  assert.equal(await composite.createOfferFor('x'), 'o');
  assert.equal(await composite.acceptOffer('blob', { peerId: 'y' }), 'a');
  await composite.completeConnection('x', 'ans');
  composite.closePeer('x');
  assert.deepEqual(calls, [['offer', 'x'], ['accept', 'blob', 'y'], ['complete', 'x', 'ans'], ['close', 'x']]);
  assert.throws(() => new CompositeTransport([lobby]).createOfferFor('x'), /direct connections/);
});
