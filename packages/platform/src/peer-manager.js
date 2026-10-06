// How many peers a wallet keeps, and how it gets them. Meeting a stranger needs a rendezvous (a Trystero room, through a public relay); once
// a wallet has some peers it no longer needs one: it asks them to introduce it to the peers they know (Introducer), which opens direct
// connections of its own, and when it has enough of those it leaves the room. If it falls too low it goes back.
//
//   target  direct connections that are enough: at this many the room is left (unless `stay`)
//   low     below this many direct connections the room is joined again
//   stay    a machine that is always on stays in the room, so that a newcomer always finds someone: this is what a keeper is
//
// "Direct" means a connection of the by-hand kind (a WebrtcTransport link), whether it was made with two codes or by an introduction:
// it does not depend on the room, so it outlives it. A link made through the room ends when the room is left.

const within = (promise, ms) => new Promise((resolve, reject) => {
  const timer = setTimeout(() => reject(new Error('no answer to the introduction')), ms);
  promise.then((v) => { clearTimeout(timer); resolve(v); }, (e) => { clearTimeout(timer); reject(e); });
});

export class PeerManager {
  /**
   * @param {object} options
   * @param {object} options.transport    every peer, whichever way it was met (a CompositeTransport)
   * @param {object} options.direct       the transport of direct connections (a WebrtcTransport)
   * @param {object} options.lobby        the room (a TrysteroTransport: connect, disconnect, connected, enabled)
   * @param {object} options.introducer   an Introducer over `transport`
   */
  constructor({ transport, direct, lobby, introducer, target = 6, low = 3, stay = false, retryMs = 30_000, answerMs = 20_000, now = () => Date.now() }) {
    Object.assign(this, { transport, direct, lobby, introducer, target, low, stay, retryMs, answerMs, now });
    this.enabled = true;
    this._asking = false;
    this._notBefore = 0;
    this._failed = new Set();
    this._timer = null;
    this._busy = false;
    this._again = false;
    this._unsubs = [];
    this._stopped = false;
  }

  start() {
    this._stopped = false;
    const evaluate = () => { this.evaluate().catch((err) => console.error('peer manager:', err)); };
    this._unsubs = [this.transport.onPeerJoin(evaluate), this.transport.onPeerLeave(evaluate)];
    evaluate();
  }

  stop() {
    this._stopped = true;
    clearTimeout(this._timer);
    for (const unsub of this._unsubs) unsub();
    this._unsubs = [];
  }

  /** Looks at how many peers there are and does what that calls for. Never runs twice at once: a change that comes meanwhile is looked at after. */
  async evaluate() {
    if (this._busy) { this._again = true; return; }
    this._busy = true;
    try {
      do {
        this._again = false;
        await this._step();
      } while (this._again);
    } finally {
      this._busy = false;
    }
  }

  async _step() {
    if (!this.enabled || this._stopped) return;
    const direct = this.direct.peers().length;
    const total = this.transport.peers().length;
    if (this.lobby.enabled) {
      if (!this.stay && direct >= this.target && this.lobby.connected) await this.lobby.disconnect();
      else if ((direct < this.low || total === 0) && !this.lobby.connected) await this.lobby.connect();
    }
    if (direct < this.target && total > 0 && !this._asking && this.now() >= this._notBefore) this._askForMore();
  }

  // One introduction at a time. A peer that has nobody new to offer is skipped for the rest of the round; when every peer has said so, the
  // wallet waits before it asks again (new peers have joined by then, or someone's own peers have changed).
  _askForMore() {
    const fresh = this.transport.peers().filter((peer) => !this._failed.has(peer));
    if (fresh.length === 0) {
      if (this._failed.size === 0) return;               // no peer at all
      this._failed.clear();
      this._notBefore = this.now() + this.retryMs;
      this._later(this.retryMs);
      return;
    }
    const mediator = fresh[0];
    this._asking = true;
    within(this.introducer.requestIntroduction(mediator, { exclude: this.direct.linked?.() ?? this.direct.peers() }), this.answerMs)
      .then(() => { this._asking = false; this.evaluate().catch(() => {}); })
      .catch((err) => { console.debug('introduction by', mediator, 'failed:', err.message); this._asking = false; this._failed.add(mediator); this.evaluate().catch(() => {}); });
  }

  _later(ms) {
    clearTimeout(this._timer);
    this._timer = setTimeout(() => { this.evaluate().catch(() => {}); }, ms);
    this._timer.unref?.();
  }
}
