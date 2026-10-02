# AIWA Yellow Paper

**Causal Coordination and Local Value Accrual for Partition-Tolerant Networks**
Version 1.0 — formal specification and reference implementation

*Not a specialist? Read [EXPLAINED.md](EXPLAINED.md) first ([EXPLICATION.md](EXPLICATION.md) en français): the same protocol in plain words.*

This document specifies the protocol and says, for each mechanism, what it guarantees and what it does not. Every claim
is checked against the reference implementation in this repository (`packages/`); where something is only designed or
only experimental, it says so.

---

## Abstract

AIWA is a value-accrual and causal-coordination primitive for networks
under arbitrary delay, intermittent connectivity, and unbounded
partition. It separates two properties conventionally coupled in
distributed ledgers:

**Coordination.** A deterministic causal layer over authenticated events,
rooted in a common genesis. Domains operate while disconnected;
reconciliation is deferred, not required.

**Accrual.** Local, unconditional creation of value, gated at genesis by
an external commitment (§8) and thereafter driven by real sequential
computation (§6) — never by a shared clock.

$$\text{Progression} \to \text{claimable value} \qquad \text{Conservation} \to \text{ownership} \qquad \text{Mirror} \to \text{verifiable history}$$

No component requires a globally synchronized state. The reference
implementation is split into three packages by concern — validation
(`aiwa-core`), distributed infrastructure (`aiwa-platform`) and a
developer-facing facade (`aiwa-lib`) — described in §0.

---

## 0. Architecture: three packages, one protocol

The specification below is implemented once, in `aiwa-core`, and
composed by everything above it — never reimplemented at a different
layer:

- **`aiwa-core`** — the protocol itself. Identity, the event log and its
  content-addressing, progression, the sequential proof, accrual,
  conservation, Mirror, Causal Tick, relative rate,
  content-addressed contract publishing, delegation, and bearer
  vouchers. Depends on nothing of its own — only `@noble/curves`,
  `@noble/hashes`, `@scure/bip39`, and an optional `@solana/web3.js`
  peer dependency for the genesis commitment (§8). 418 passing tests
  (including a real Rust build+run cross-check when a toolchain is available — §16.1).
- **`aiwa-platform`** — distributed infrastructure with no protocol
  logic of its own: WebRTC transport, a replicator that syncs an
  `aiwa-core` event log between peers, capability-gated data stores, a
  graph materializer, multi-file bundle publishing (§19) and the **archive node** (§12.2), the always-on holder of wallets'
  backups. 85 passing tests.
- **`aiwa-lib`** — the public, developer-facing facade. A wallet
  API (`AIWA`) composing `aiwa-core`'s validation with `aiwa-platform`'s
  transport, and a smart-contract/token authoring SDK
  (`defineContract`/`Contract`/`signedAction`), the wallet's recovery (recovery phrase, backup, restore — §12.2) and the shared
  panel every app mounts for it. 100 passing tests.

Applications — the store, its registry, the Android app — compose `aiwa-lib` and add no protocol logic of their own.

```
        ┌──────────────────────────────────────────────┐
        │ applications: store, registry, Android app   │
        │ (no protocol logic of their own)             │
        └──────────────────────────────────────────────┘
                               │  call the wallet API
                               ▼
              ┌──────────────────────────────────┐
              ▼                                  ▼
┌───────────────────────────┐      ┌───────────────────────────┐
│ aiwa-lib                  │      │ aiwa-platform             │
│ public wallet API (AIWA), │      │ transport, replication,   │
│ Channel, contract SDK     │      │ capability-gated storage, │
│                           │      │ bundle publishing,        │
│                           │      │ archive node              │
└───────────────────────────┘      └───────────────────────────┘
              │                                  │
              └────────────────┬─────────────────┘
                               ▼
       ┌──────────────────────────────────────────────┐
       │ aiwa-core                                    │
       │ the protocol itself: identity, event log,    │
       │ progression, accrual, conservation, Mirror,  │
       │ Causal Tick, contracts, delegation, vouchers │
       │                                              │
       │ depends on nothing of its own - only         │
       │ @noble/curves, @noble/hashes, @scure/bip39,  │
       │ optional @solana/web3.js                     │
       └──────────────────────────────────────────────┘
```

A consequence worth stating plainly: nothing above `aiwa-core` may alter
what counts as a valid state transition. `aiwa-lib`'s `Channel` and
bearer vouchers are real protocol extensions (§17, §18) — they live in
`aiwa-core`, not layered on top of it, for exactly this reason.

## 1. System model

A domain is an operational environment holding one or more identities
and local state. An identity is a keypair. Communication between
domains is continuous, intermittent, delayed, asymmetric, or absent, for
arbitrary duration.

**Assumed:** collision-resistant hashing, EUF-CMA-secure signatures,
deterministic serialization.
**Not assumed:** a synchronized wall clock, continuous access to any
external chain, a global consensus quorum, a globally replicated state.

## 2. Identity

$$\mathrm{domain}(i) = \mathrm{SHA\text{-}256}(\mathrm{pk}_i)$$

Full 256-bit digest, hex-encoded, untruncated. The private key
authorizes state transitions; no other binding — device, IP, location —
is part of identity. In the current implementation the same Ed25519
keypair also serves as the domain's real Solana address (`aiwa-lib`'s
own `toIdentity()`) — one key, two roles, not two separate credentials
to manage.

## 3. Event log and content addressing

State transitions are signed events referencing causal parents. A
participant need not hold every event, only what is relevant to its own
state and observed relationships.

$$\mathrm{id}(e) = \mathrm{SHA\text{-}256}\Big(\mathrm{JSON}\big(\{\mathrm{domain}, \mathrm{author}, \mathrm{authorPublicKey}, \mathrm{parents}: \mathrm{sort}(e.\mathrm{parents}), \mathrm{type}, \mathrm{payload}: \mathrm{canon}(e.\mathrm{payload}), \mathrm{createdAt}\}\big)\Big)$$

where $\mathrm{canon}(v)$ is defined recursively: for an array, applied
element-wise, order preserved; for an object, keys sorted
lexicographically and applied to each value under its sorted key;
otherwise unchanged. The event envelope (`packages/core/src/event.js`)
binds `domain`, `author`, the embedded `authorPublicKey`, and `type` into
the same id and the same signature. Binding `type` matters: an envelope
that covered only $\{\mathrm{parents}, \mathrm{payload}\}$ would leave an
event's `type` outside content-addressing, which is exactly the forgery
class `packages/lib/src/contract.js` documents (§17's delegation payloads
are deliberately signed over a message that does **not** include `type`,
for the same reason spelled out there).

**Author, present but not blindly trusted.** `author` is carried
directly in the event; a real verifier (`verifyEvent`) independently
re-derives it from the embedded `authorPublicKey` and rejects any event
where they disagree — an event cannot claim an author it cannot really
sign for. A reducer folding events into state, however, is never handed
`author` at all (`adapt-event.js`'s own `toReducerEvent` strips it
before a reducer ever sees the event) — anything a reducer needs to
attribute to a real signer must be a **separately embedded signature
inside the payload itself**, checked by the reducer, never inferred
from the outer envelope. Every payload-level signature scheme in this
document (transfer, split, delegation, voucher redemption,
`signedAction`) exists because of this exact separation.

## 4. Mirror

$M_D \in \{\texttt{empty}, \texttt{full}\}$: a domain $D$'s own signed,
per-epoch commitment to what it has observed of another domain, never a
replica of the observed state.

**Reception monotonicity.** For domain $D$ observing $X$:
$\mathrm{seen}_D(X, e_{i+1}) \geq \mathrm{seen}_D(X, e_i)$ for successive
real commitments $e_i$. A claim of having seen less than previously
committed is rejected.

Mirror does not establish that two domains are distinct real-world
actors, nor rule out a coalition fabricating consistent history together
at real cost. It is evidence about the structure of observed history —
not an identity oracle.

## 5. Progression

$$\mathrm{epoch}_D(n+1) = \mathrm{epoch}_D(n) + 1$$

valid only if causally chained to $D$'s own last accepted transition,
carrying a real sequential proof (§6), **and signed by a real Ed25519
key that derives $D$ itself**. Progression is local: $\mathrm{epoch}_A$
and $\mathrm{epoch}_B$ are never directly comparable.

**An epoch is a fixed amount of work (`epochIterations`).** A deployment that
sets `rewardParams.epochIterations` $= E$ fixes the sequential work of one
epoch, and an event may carry $k \ge 1$ epochs at once:
$\mathrm{epoch}_D \mathrel{+}= k$, with exactly $k\cdot E$ iterations and
one proof (§6.2). A deployment that does not set it accepts whatever
iteration count the signer wrote ($\ge 1$) — then an epoch could cost one
hash and $D$'s age ($q_{\text{total}}$, and so every ranking built on it)
could be inflated for nothing; that is why a real deployment sets it. Time
here is sequential work, not calendar time: a faster machine makes more
epochs per second.

**The signature requirement.** The sequential proof (§6) is a public,
deterministic function of
$(\mathrm{domain}, \mathrm{output}_{n-1})$ — both already visible to
anyone watching the log — so it was never, by itself, evidence of who
submitted the transition: anyone could compute $D$'s own next-epoch
proof and advance $D$'s progression without $D$'s consent. Not a theft
of value, but a real, verified griefing vector against $q_{\text{total}}$
(§7): since $q_{\text{total}}$ never resets and sits in $r$'s own
denominator, inflating it lowers a domain's every future reward at zero
cost to the attacker — concretely, $r(b{=}100, q{=}1, q_{\text{total}},
T{=}0)$ falls from $\approx 0.087$ at $q_{\text{total}}{=}1$ to
$\approx 0.0097$ at $q_{\text{total}}{=}20000$, a real $\sim\!9\times$
reduction imposed by a third party for free. Closed the same way
§7's accrual/claim signer-scoping is: `deriveId(signerPubkey) === D`,
checked against a signature embedded in the payload itself
(`buildSignedProgressionEvent`/`verifyProgressionAuthorization`), never
against the outer event envelope alone.

**Automatic in the reference wallet.** `aiwa-lib`'s `startProgressLoop()`
calls `advanceProgress()` on a real timer for as long as a wallet stays
connected, with no manual button. A domain that never calls
`advanceProgress()` stays at epoch 0 and never accrues anything to claim,
however much real wall-clock time passes.

## 6. Sequential proof

$$h_0 = \mathrm{SHA\text{-}256}(\mathrm{seed}), \qquad h_i = \mathrm{SHA\text{-}256}(h_{i-1})$$

seeded from $(\mathrm{domain}, \mathrm{output}_{n-1})$. Symmetric —
verification cost equals production cost — unlike an asymmetric VDF
(Wesolowski, Pietrzak); what is unconditional is that the chain imposes
$\mathrm{iterations}$ genuinely dependent, sequential steps — no amount
of hardware lets a later step be computed before an earlier one. How
much *real time* those steps take is not unconditional: real,
independent SHA-256 benchmarks show roughly a 2–4$\times$ spread
between common hardware with and without dedicated SHA acceleration.

**Locality.** Computation of $h_i$ occurs on exactly one real device at
a time — never distributed, never assisted by other domains. Identity
is the keypair (§2); the computing device may change without
discontinuity in $\mathrm{epoch}_D$.

**Deployment-chosen iteration count.** `aiwa-lib`'s own
`startProgressLoop()` defaults to 100,000 iterations every 30 real
seconds per tick — a deployment's own choice; the mechanism (§6.1 below)
is what is specified here, not any one deployment's constant.

### 6.1 Asymmetric verification (Wesolowski)

The symmetric chain (above) costs a verifier exactly what it cost the
prover. A real Wesolowski VDF, over $\mathbb{Z}_N^*$ for a 2048-bit RSA
modulus $N$ of unknown factorization (the real, published RSA-2048
challenge number), gives verification cost independent of the real
iteration count $T$:

$$y = x^{2^T} \bmod N \qquad \ell = \mathrm{HashToPrime}(x, T, y) \qquad \pi = x^{\lfloor 2^T/\ell \rfloor} \bmod N$$

$$r = 2^T \bmod \ell \qquad \text{Verify: } \pi^{\ell} \cdot x^{r} \stackrel{?}{=} y \pmod N$$

Real prover cost is on the order of $2T$ modular multiplications. Real
verifier cost is $O(\log T)$. $\ell$ is derived deterministically from
$(x, T, y)$ — never accepted as prover-supplied input.

### 6.2 Succinct progression

With `epochIterations` $= E$, a progression event carries
$(\mathrm{epoch}, \texttt{vdfIterations} = k E, y, \pi, \ell)$ and the
reducer checks the Wesolowski proof (§6.1) instead of recomputing a hash
chain. The work starts from $x = H(\mathrm{domain}\,\|\,\mathrm{output}_{n-1}) \bmod N$,
so it cannot be done ahead, borrowed from another domain or reused; $y$, $\pi$
and $\ell$ are canonical (one representation per value: $y$ and $y+N$ would
both verify otherwise). Measured: verifying takes about 3.6 ms for $10^5$ and
for $4\cdot 10^5$ squarings, against 0.5 s and 2.8 s to produce; the hash chain
of §6 took 5.6 s to verify one $10^5$-step epoch. A third party — a validator, a
registry — can therefore check a domain's age and its time since its last
action (§7.2) at a cost of milliseconds per event, not the work the domain did.
The cost it does pay is storage: one event per proof (about 1.7 KB), so the
history that proves an age grows with the number of events, not of epochs
($k$ epochs fit in one event).

**The mining events are one signed chain.** The work above started from the
previous output alone, so the same stretch of proven work could be re-signed
over any history: an action (a burn's commitment, a claim) could be left out,
or two histories kept side by side, at no cost. In a deployment with `epochIterations`
every progression, accrual and claim of $D$ therefore names, in its signed
payload, the mining event it follows ($\mathrm{prev}$: an id, or none before the
first), and the work starts from
$x = H(\mathrm{domain}\,\|\,\mathrm{output}_{n-1}\,\|\,\mathrm{prev}) \bmod N$.
Three consequences. An action cannot be left out: the epochs worked after it are
bound to it and are refused without it. Proven work cannot be re-signed over
another history: showing a history without an action means redoing, from that
action on, the work the other one holds. And an action cannot be placed earlier
than it was made (before, which of two concurrent events a reader folded first
decided its epoch). The link is a signed field, not the event's `parents` (the
log's heads — a checkpoint, a reception commitment — which pruning later removes).

What it does *not* give is a proof that no other history exists: a domain can
keep two, redoing the work, and show one. That is what a witness is for — anyone
who holds an event $D$ signed can show it, and a reader that keeps it can require
$D$'s next history to contain it (a fork then cannot be shown; this is the proof
of §13.2, here used by a registry). Without any witness the chain alone is the
protection, and its price is the work. A snapshot is also not "the current state":
an action made after the last epoch shown, and followed by none, can be left
out — that needs a clock, e.g. the head anchored on Solana.

## 7. Accrual

$$r(b, q, q_{\text{total}}, T) = \frac{b \cdot q^{\alpha}}{\left[\ln\left(q_{\text{total}}^{\,\beta(1-T)} + C\right)\right]^{\gamma}}$$

| Symbol | Meaning |
|---|---|
| $b$ | the capital that mines: what the **last burn** committed (§7.1), $b = \mathrm{burned}\cdot(1-T)$ |
| $q$ | epochs since $D$'s own last economic action (burn or claim); resets on each; floored at `minQ` |
| $q_{\text{total}}$ | $D$'s own total progression epoch count; never resets |
| $T$ | a patience rate, clamped to $[0, 0.4]$, chosen **at the burn** for what follows (§7.1) |
| $\alpha, \beta, \gamma, C, \mathrm{minQ}$ | deployment parameters |

**The $T = 0$ form.** At $T = 0$ the denominator is $\ln(q_{\text{total}}^{\,\beta} +
C)$, which equals $\beta\ln q_{\text{total}} + \ln(1 + C/q_{\text{total}}^{\beta})$
(the sum form is the numerically safe way to evaluate it). $T$ is the only
parameter beyond capital $b$, epochs since the last action $q$, and the
domain's own age $q_{\text{total}}$.

**Reproducibility.** Computed in Q128 fixed-point BigInt arithmetic
(`fixed-point-math.js`), never `Math.log`/`Math.pow` — IEEE 754 never
guarantees those agree bit-for-bit across runtimes the way
$+,-,\times,\div$ do, and `reward()`'s output funds a real, on-chain
AIWA claim. `rewardFixed()` is the reproducible core; `reward()` is a
plain-`Number` convenience wrapper over it, identical in behavior.

**Invariant.** $q$ and $q_{\text{total}}$ are
derived exclusively from $D$'s own verified progression state at query
time — never accepted from an event payload.

### 7.1 "Last action" mining

The position of a domain is what its **last action** left it:

- **A burn's commitment replaces the position.** $b$ is the capital that now
  mines; a small burn after a big one lowers $b$ — that is the rule.
- **The previous position is paid first.** Before it is replaced, what it had
  accrued is credited as a real claim (`auto:<nonce>`, owned by $D$): a new burn
  never forfeits.
- **$T$ is chosen at the burn, not inherited.** A burn without a $T$ is
  $T = 0$; a claim leaves $T$ as it was — it costs nothing, so it cannot buy a
  better one.
- **$T$ has a price, and it stays part of the burn**: $b =
  \mathrm{burned}\cdot(1-T)$, so a commitment costs
  $\lceil b/(1-T)\rceil$ lamports of confirmed burn, and each confirmed burn
  backs commitments once (`burns.consumed`). A larger $T$ makes the curve more
  generous ($q_{\text{total}}^{\beta(1-T)}$) and costs that share of the burn,
  so it is a choice, not "always $0.4$". The $T$ share is destroyed, except for
  the creator fee of §7.3, which a deployment may take out of it. There is no
  other recipient, and none chosen by the user or by the app that distributes the
  burn: a recipient the payer or the app could name would let anyone self-host a
  page and pay themselves, making $T$ free again.

### 7.2 The two states

For an app, a validator or a registry holding $D$'s events (`assessMining`):

1. **The mining state**: the capital that mines, $T$, the epoch of the last
   action, the age $q_{\text{total}}$, $q$ (epochs since the last action), and
   what is claimable now.
2. **The ranking figure**: $\{\mathrm{score} = \text{claimable},\
   \mathrm{laps} = \max(1, q)\}$, read at a moment and frozen by whoever stores
   it.

Both come from the events alone: envelopes verified, progression proofs checked
(§6.2), the burn confirmed by the validator itself (§8.2). A validator that kept
the state it derived earlier folds only the new events.

The registry of the store (`registry/`) ranks applications by this figure, as
$\mathrm{score}/\mathrm{laps}$ — claimable value per epoch since the last action.
It adds no ranking rule of its own.

### 7.3 The creator fee

A deployment may set `rewardParams.creatorFee = { address, rateOfT }`: one fixed
protocol address, and the fraction of the $T$ share that goes to it instead of
being destroyed. The reference deployment's initial value is $\mathrm{rateOfT} =
0.001$ (0.1 % of the $T$ share), paid to the address of the project's author. The
fraction and the address are protocol parameters: changing either is a change of
the versioned rule set, not a setting of the wallet, the store or the user.

$$\mathrm{fee}(\mathrm{burned}, T) = \left\lfloor \frac{\mathrm{burned}\cdot \hat T \cdot \hat\rho}{10^{12}} \right\rfloor \ \text{lamports}, \qquad \hat T = \mathrm{round}(10^6 T),\ \hat\rho = \mathrm{round}(10^6\,\mathrm{rateOfT})$$

in integer arithmetic (BigInt), so every reader computes the same number. At
$T = 0$, or in a deployment without `creatorFee`, the fee is $0$ and nothing
below applies.

- **What the burn transaction does.** It is two transfers in one transaction:
  $\mathrm{burned} - \mathrm{fee}$ to the incinerator and $\mathrm{fee}$ to the
  creator address. The fee is part of the $T$ share, which is part of the burn:
  the capital is still $b = \mathrm{burned}\cdot(1-T)$, and the user's debit is
  still $\mathrm{burned}$ (network fee aside). `burnQuote` returns the split before
  anything is signed.
- **What a reader checks.** The reader that fetches the finalized transaction
  (§8.2) also records what the creator address received in it
  (`creatorBalanceDeltaLamports`) — only if it asks for that address; a reader that
  does not know the creator address cannot confirm the fee and **fails closed**.
  A burn counts as $\mathrm{incinerated} + \mathrm{creator}$ lamports for the
  payer, who must really have spent at least that much. Each lamport paid to the
  creator backs the fee of commitments once (`feeCovered` against `feeConsumed`).
- **The rule.** A commitment at $T > 0$ is rejected unless its fee
  $\mathrm{fee}(\lceil b/(1-T)\rceil, T)$ is covered by the creator payments the
  reader confirmed for that domain and no earlier commitment used. A burn that
  sent the whole $T$ share to the incinerator, to dodge the fee, simply does not
  back a commitment at $T > 0$ in any reader that enforces the rule.
- **What it does not do.** The user does not choose who is paid: there is one
  address per rule set. A fork of the software that changes the address (the
  license allows it) is a different deployment, with its own readers and its own
  economy: it does not take anything from this one. The amounts are small by
  design: at $T = 0.4$ and
  $\mathrm{rateOfT} = 0.001$, the creator receives $0.0004$ SOL per SOL burned;
  at $T = 0$ nothing.

## 8. Genesis Commitment

$$b_D = \sum_{\text{valid burns}} \mathrm{lamports}_i$$

**What the burn is for, and what it is not for.** The burn is the entry price of *accrual* (§7) and of nothing
else: it is what lets a domain commit capital $b$, and so create value. An identity, its log, progression (§5),
Mirror (§4), receiving and transferring claims (§9), contracts and the rest need none — the reducers check no burn
there, and a domain that never burned simply has nothing to claim (`no committed capital for this domain`). Outside
accrual a burn counts in exactly one place: it is the weight of an observer in the weighted median of §13. "Activation"
below, like `identity-cost.js` in the code, means the activation of accrual.

Accrual requires an irreversible SOL burn to Solana's incinerator
address, verified from a finalized transaction record. Cumulative
across every valid burn.

**Atomic in the reference wallet.** `aiwa-lib`'s `AIWA.burn(lamports, connection)`
broadcasts the real burn, then immediately, in the same call, records the exact
burned amount as committed capital — one action, one button ("Burn & ignite"),
not two separately-clickable steps.

$R$ is linear in $b$: absent a genesis cost, splitting capital across
identities would not reduce total accrual. A per-identity activation
cost makes churn strictly costlier, not free.

### 8.1 Whether churn pays

Existence of a real cost is not the same claim as sufficiency.
`churn-analysis.js`'s own `compareChurnVsStay` compares one domain that
commits once and matures for the full span against one that restarts
every $k$ epochs, repeatedly re-entering at low $q_{\text{total}}$
where $r$'s own denominator (§7) is smallest:

$$\text{stay} = r(S, N, N, 0) - \mathrm{cost}(0) \qquad \text{churn}(k) = \left\lfloor \frac{N}{k} \right\rfloor \cdot \big[r(S, k, k, 0) - \mathrm{cost}(\text{slot at cycle start})\big]$$

With zero real cost, churn wins outright; with a real, deliberately-chosen
cost curve, churn nets negative while staying nets positive — the
identical $r$, the only real difference being $\mathrm{cost}(\cdot)$'s own
magnitude. `findMostProfitableChurnInterval` sweeps $k$ over a real
candidate range to find an attacker's own real best case, rather than
checking one interval and declaring victory. It is a real calculator, computed
per deployment's own chosen $(\alpha,\beta,\gamma,C)$ and cost curve, never a
general proof that any given tuple is safe. It models the commitment cost, not
the rest of §7's rules (a patience rate $T$, and "last action" mining: a burn
replaces the position and pays the previous one, §7.1): a result it gives must be
re-checked against those rules before it is relied on.

**External dependency.** Broadcasting a burn requires
reaching a centralized, Earth-hosted RPC endpoint over real internet —
the one exception to this document's own no-shared-infrastructure
principle. Once activated, $\mathrm{epoch}_D$ requires no further
contact with Solana or Earth.

### 8.2 The commitment is backed in the reducer — mandatory

Without this rule the burn would be a convention of the application: an `accrual` event carrying any `b`, signed by
its domain, would be accepted, and since $R$ is linear in $b$ a domain could commit $b = 10^9$ with no burn anywhere
and accrue on it.

`applyAccrualEvent` therefore rejects an `accrual` unless it is **covered by burns the reader confirmed** that no earlier
commitment used: $\text{consumed}(D) + \lceil b\cdot 10^9/(1-T) \rceil \le \text{covered}(D)$ lamports (§7.1: each
confirmed burn backs commitments once, and the $T$ share is part of the burn), and, in a deployment with a creator
fee, unless the fee of §7.3 is covered too.

- A domain points at a burn with a `burn-record` event — `{ domain, signature }`, the Solana signature and nothing
  else. The reducer does not read what the burn was worth from the event, and never reaches Solana: it reads the
  record **the reader fetched itself** (`fetchBurnRecord`: the FINALIZED transaction), and counts the burn for $D$
  only if that record is error-free, positive, sent to the incinerator, **paid by $D$'s own key** (a domain id is
  the hash of the key that is also its Solana address) and really spent by that payer. With a creator fee, what the
  creator address received in the same transaction is recorded too (§7.3). One signature counts once.
  Quoting someone else's signature earns nothing.
- Deterministic *per reader*, like the rest of validity (§11): the same log folded with different confirmed
  records gives different — each correct — results. A reader that cannot reach Solana confirms nothing, so credits
  no one's commitment until it can; folding again after confirming turns a rejected `accrual` into an accepted one.
  This is §8's "one exception to the no-shared-infrastructure principle", not a new one. It has a consequence worth
  stating, and it concerns *minting*, not transfer: a claim exists in a reader's view only if the domain that minted
  it has a position there, hence a burn the reader confirmed. Moving a claim from hand to hand looks at nothing but
  the claim, so a holder who never burned anything (a relay, a winner paid by a contract, a recipient) passes value
  on freely; what a receiver needs is the burn of the coin's *origin*, which travels with the coin's ancestors
  and is confirmed once.
- The certified witness weight of §13 is the same quantity: $w_i$ = the lamports the reader confirmed for $i$.
- Opt-out is explicit — `commitmentBacking: 'none'` in the deployment's parameters — for tests, demos and private
  economies; omitting it means mandatory.
- **Not covered.** A dishonest Solana endpoint is the reader's problem. A log whose commitments have no burns behind
  them is rejected by a reader who enforces the rule.

## 9. Conservation

A claim is a tuple $(\mathrm{id}, \mathrm{amount}, \mathrm{owner},
\mathrm{status} \in \{\mathrm{active}, \mathrm{deactivated},
\mathrm{consumed}\})$.

**Split.** $C \to (C_1, C_2)$ where $\mathrm{amount}(C_1) +
\mathrm{amount}(C_2) = \mathrm{amount}(C)$ by construction.

**Transfer, via Deactivate → Prove → Verify → Consume → Activate.**
$\mathrm{proveTransfer}$ requires a real signature verifiable against
$\mathrm{from}$'s own real public key. The proof id is deterministic —
$\mathrm{id} = \mathrm{claimId}{:}\mathrm{from}{:}\mathrm{to}{:}n{:}\mathrm{derivation}$
— and the consumed-proof set is idempotent: a second attempt at the
identical proof is rejected outright, and `deactivate()` refuses a
claim not currently `active`, which is the entire mechanism behind
§18's own "only the first redemption succeeds" — no separate
double-spend logic was written for that property; it falls out of this
one.

**A load-bearing property `owner`/`from`/`to` never enforce, exploited
constructively in §18.** Nothing in `conservation.js` requires these
fields to be real, derivable identities — they are opaque strings.
Ownership of a claim is proven entirely by the signature check in
`proveTransfer`/`verify`, never by any property of the string itself.

## 10. Denomination

$1\ \mathrm{AIWA} = 10^{18}$ base units, always integer.

## 11. Partition and reconciliation

Each domain continues independently through partition; none decrements
state for another's unreachability, none awaits permission.
Reconciliation is signature, ancestry, and Mirror-observation
verification over newly available evidence — never a question of clock
authority.

```
Earth               e0 ── e1 ── e2 ── e3 ── e4
domain              (VDF-bound progression, entirely alone —
                      Mars need not exist for any of this)

Mars                                        m0 ── m1 ── m2
domain                                      (its own, independent
                                              progression — no
                                              awareness of Earth)

                                                      │
                          a real connection opens ────┘
                          (WebRTC, a file, anything)
                                                      │
                                                      ▼
Earth               e0 ── e1 ── e2 ── e3 ── e4 ─┐
domain                                          ├── r (Mirror reception
Mars                             m0 ── m1 ── m2 ┘     commitment: "I
domain                                                 observed these")
```

`r` is not a merge and not a correction of either chain — `e4` and
`m2` both remain exactly what they were. `r` is a new, additional
event: a signed statement, by whichever domain builds it, of what it
has now observed of the other. Nothing about `e0..e4` or `m0..m2`
changes; nothing is renumbered, rewritten, or invalidated. Independent
histories stay independent, and become causally correlated the moment
they interact — nothing here forces a shared timeline, a shared
height, or a shared next event.

**Channel versus data.** A transport session (`aiwa-platform`'s
`WebrtcTransport`, or any real link) is inherently temporary. Once real
data has crossed it, that data is verified and stored durably by each
side independently; losing the session never loses what already
crossed. Re-establishing a channel after a gap is a real, ordinary
event, not a failure — the reconciliation logic it feeds (§4) is
agnostic to which transport carried the bytes.

### 11.1 Positioning

Published interplanetary-cryptocurrency proposals generally extend one
Earth-anchored consensus chain across the latency gap — DTN transport,
timelocks widened to light-time, federated or merge-mined settlement —
leaving consensus and issuance untouched. This transfers already-created
value under latency; it leaves creation itself dominated by whichever
side has more compute (mining from Mars against Earth's hashpower is
acknowledged, in that literature, as structurally unprofitable).

AIWA removes value creation from any consensus chain: $\mathrm{epoch}_D$
requires no awareness of one. Reconciliation (§4, §14) is additive and
informational only. This closes the specific asymmetry above; it does
not address real byte transport under latency (deliberately pluggable
— `aiwa-platform`'s own `Replicator`/transport separation, shared
identically by `WebrtcTransport` and any future transport, with room
for a real DTN or dedicated-hardware transport later without touching
reconciliation logic at all) nor an exchange rate between economies
that grew apart.

### 11.2 When two branches contradict each other

A log is a graph: two events that do not know of each other are two branches, and that is ordinary (Alice and Bob each acting
on their own). It matters only when they contradict — one claim spent twice, one voucher redeemed twice (§18), one claim id taken
by two domains: a reader folds one and refuses the other. A reader that folded in the order events *arrived* would let two
readers holding the same events pick different winners and keep them (the same voucher redeemed by two people, the two logs
merged in either order, would give a different winner each time): convergence would fail even once everything had been
exchanged.

Readers therefore fold in one **canonical order** (`canonicalOrder`, `aiwa-core`): a topological order (a parent before its children) in
which, among the events that can come next, the one with the smallest id goes first. The same events give the same order and so the
same winner for every reader, whatever order they arrived in, whether folded in one go or one arrival at a time (a wallet that had
already folded part of its log folds again from its last checkpoint when a concurrent branch arrives). It is not a signed format:
it adds nothing to an event.

**Agreement, not fairness.** The winner is the smaller id: arbitrary, not the first in time (nothing here has a clock). A signer
who writes two contradicting events can try variants until the one he wants has the smaller id, so the rule does not protect whoever
accepted the other; it makes every reader agree on the outcome. What stays is a proof: two valid signatures by the same key on
contradicting events show, to anyone, that it wrote both. Protection beyond that is a choice of the one who accepts: let the
histories meet before relying on a payment from someone he does not trust, or require an anchor (the head of the signer's log
inscribed on Solana: an objective clock — **not built**). An anchor needs a connection: it helps someone who can go online
before handing over what he gives, and does nothing for an exchange that stays entirely offline — there, nothing prevents a signer
who holds his key from writing two contradicting events; what remains is limiting the amount, trust, and the proof afterwards. A conflict that a checkpoint (§12.1) already absorbed stays as the
checkpoint decided it, the same trade-off every checkpoint makes.

## 12. Explicit non-claims

Not solved: the human-identity oracle, physical-location verification,
absolute global time, Byzantine agreement without assumptions,
detection of every coalition of identities under one real actor. A
coalition can produce internally consistent history at real cost. The
claim is narrower: fabricated identities cannot fabricate authenticated
history *for free*.

Also not specified: the durability of data that no domain chooses to
keep (§3, §13.2).

### 12.1 Scalability — three limits, and the tradeoff each bound makes

Cross-domain, this scales well by construction — no consensus, no
shared bottleneck. *Within* a single domain, three costs would grow
unboundedly. Each is bounded; none is "solved away" for free — each
trades something explicit and documented, never hidden.

**Local storage — bounded via checkpoints.** A continuously-running
domain still accumulates one event per real progression epoch plus one
per real economic action, forever, *unless* pruned. `aiwa-core`'s
`checkpoint.js` adds a real, self-signed event embedding a domain's own
already-materialized state as of a specific set of log heads
(signer-scoped from the start: `verifyCheckpoint` requires
`event.author === event.payload.domain`, the identical discipline
§7's own `'claim'`/`'accrual'` signer-scoping fix established).
`EventLog.pruneBeforeCheckpoint()` then physically deletes every real
event the checkpoint's own state already accounts for. **Honest
tradeoff, stated plainly**: a peer who already independently verified
everything up to a checkpoint loses nothing by trusting it afterward —
it is genuinely their own, already-verified work, summarized. A
brand-new peer who receives *only* a pruned log can no longer
independently re-derive that state from genesis; they trade full
independent verifiability for a real, signed assertion by the domain's
own key about its own past — the identical tradeoff Ethereum's own
weak-subjectivity checkpoints make, not a flaw specific to this
implementation. At the protocol level checkpointing is opt-in: a domain
that never calls it keeps unbounded growth (the reference wallet's
`startAutoCheckpoint()` runs it every five minutes by default).

**Wallet materialization — bounded via incremental folding.**
`materializeWallet` accepts an optional `baseState` to fold new events
onto instead of replaying from genesis every call; `aiwa-lib`'s
`AIWA._materializeWallet()` caches the last materialized state and folds
only what is new since. Three details make this sound together with
checkpointing: (1) a checkpoint's own embedded `progression.lastId` is
repointed to the checkpoint's own id, since pruning could delete the
event it named; (2) `progression.js`'s causal-chain check requires a
domain's last accepted progression event to be a *direct* parent, which
breaks as soon as any other event (an ordinary `recordCommitment()`)
becomes the log's head in between, so every progression-event builder
routes through `progressionParents(heads, lastId)` (in a deployment that
fixes the work of an epoch, §6.2, the chain is instead the signed
`previous` field, which also removes this dependency on `parents`);
(3) the incremental cache's exclusion boundary tracks the growing set of
already-covered ids rather than just the latest heads, since that
helper's extra parent edge can reach past a heads-only boundary. No
tradeoff here beyond the cache being in-memory-per-instance, not
persisted across a reload by itself.

**Unbounded full-sync payload — bounded via chunked, ACK-gated
replication.** `aiwa-platform`'s `Replicator` does not send every
missing event (`EventLog.since()`, §3) in one message on connect; it
sorts them topologically and releases bounded chunks (`chunkSize`,
default 100) one at a time, the next only once the previous chunk's own
real ACK arrives — real backpressure, not a fixed delay. **Honest
limit**: the existing `HELLO`/`HELLO_ACK` handshake independently
computes and sends "what's missing" twice per connection by design; in
a narrow timing race, this can cause one redundant, harmless resend of
an already-delivered chunk (absorbed by `EventLog.append()`'s own
idempotency, never a correctness issue) — eliminating it fully would
mean redesigning that handshake, left as future work.

### 12.2 Where a wallet's history lives, and how it comes back

A wallet is a key plus a journal of signed events (its burns, epochs, claims, transfers). The key is a BIP39 **recovery
phrase** (12 words, `m/44'/501'/0'/0'`: the same words give the same address in a Solana wallet): a new identity is made
from a fresh one and shown on request (`aiwa.recoveryPhrase`); a wallet imported as a raw key has no phrase, so its private
key is shown instead. The journal is not in the phrase. A blockchain recovers it for free because every node keeps all of
it; here nobody replicates everything (§12.1) — an event is kept by its owner and by whoever received it — so after a lost
device the journal must come from somewhere:

- **A backup** (`exportBackup` / `importBackup`): a *checkpoint* (§12.1) — the wallet's whole state signed by its own key —
  small however long the history, restorable after logging in with the phrase. Refused if it is of another identity, or no
  further along than the wallet is: it can never roll a wallet back.
- **A registry's baseline** (`adoptState`): an application that kept the state it derived from a wallet's submissions (§7.2)
  can hand it back; it holds what the registry saw, not value received from others. The store's registry publishes it per author
  (`store/baselines/<address>.json`), and its wallet asks for it by itself when a new phone has no history.
- **An archive node** (`aiwa-platform`): an always-on program anyone can run, that keeps per wallet the latest backup. Only the
  owner of a key can write its backup (the checkpoint must verify and be authored by the domain), the most recent wins,
  reads are public (a backup holds no secret), sizes and rates are limited. A wallet pushes its backup to the nodes it
  knows whenever it changed and asks them for it after a loss; it needs no trust in a node (a backup is signed by the
  wallet's key), which can only withhold or forget — hence several. This is the seed node of the bootstrap problem in its
  simplest form.
- **Peers** (`joinNetwork`): the replicator hands back what peers that received your events hold, as far as you have any
  connected.
- **The platform's own backup**: where an application's storage is backed up by its operating system (Android's automatic backup of
  the store's WebView), the journal follows its owner to a new device with no server of ours; only the phrase is typed.

A wallet application takes these in this order, by itself, and the user is asked for nothing but the 12 words: what the device restored, the
archive nodes its deployment lists, the registry's baseline. It never starts working epochs before it has looked: a log that began from
nothing would fork the history that was about to come back.
Same tradeoff as every checkpoint: whoever only sees a backup trusts its signature instead of re-deriving the history from
genesis. **Not claimed:** a wallet with no backup, no node and no peer that lost its device loses its journal (the burns stay
on Solana; the key stays in the phrase).

## 13. Causal Tick

A domain's own $\mathrm{epoch}_D$ (§5) is unconditional and requires
zero external observers. Causal Tick is a complementary,
externally-corroborated position.

$$\hat{\theta}_X = \mathrm{median}_w\left(\{(\mathrm{obs}_i(X), w_i)\}\right), \qquad w_i = b_i \ (\S8)$$

the crossing-point weighted median: sort estimates by value, walk
cumulative weight, return the first value at or past half the total
real weight. Robust while adversarial weight stays below
$\sum w_i / 2$. **Never a correction** — reported, never applied to any
domain's own earned value.

### 13.1 Hardware roots (optional)

The evidence interface functions on software primitives alone. A
domain may optionally strengthen independence assurance with
physically-provisioned hardware roots; hardware never computes
$\hat\theta_X$, never scales $w_i$, never becomes required input.
$\geq 2$ distinct, independently-issued roots required.

### 13.2 What always-on hardware is for — a design note, not implemented

§13.1 is about **independence**: attesting that an observer is not
backed solely by an actor who can fabricate identities in software.
That is one of three different jobs that permanently connected machines
could do. They need different things from the machine, and should stay
separate in the design even when one box does all three:

| Job | What it does | What must be trusted |
|---|---|---|
| **Keeper** (persistence) | Holds and re-serves content-addressed events and published bundles, so that data survives the domains that produced it. | Availability only. Integrity costs nothing to check: an id is the hash of the content (§3.1), so a keeper can withhold or lose data but cannot alter it undetected. Anyone can run one. |
| **Witness** (independence) | An observer whose independence is attested by §13.1's two-hop chain. | The origin's issuance process, or one physical unit (§13.1, stated limit). |
| **Rendezvous** (bootstrap) | Lets two domains that have never met find each other. | Nothing about the data; it can see who is looking for whom. |

**Why persistence needs its own answer.** A participant holds only what
is relevant to its own state and observed relationships (§3). Nothing in
the protocol obliges anyone to keep an event, and if every domain that
held it is gone, it is gone: the event DAG is tamper-evident, not
durable. Blockchains answer this by replicating everything on every
full node, paid through issuance or fees; AIWA declines the globally
replicated state (§1), so durability is left to whoever chooses to hold
the data. §13.1's last paragraph covers a different case (loss of one
device, key restored elsewhere), and says plainly that hardware does not
substitute for reachability during a partition.

**Stated as open, not solved:** who runs keepers and why (an incentive
is not specified — value accrual (§7) is local and unconditional, it
pays nobody to store); how keepers choose what to keep; and whether a
keeper that holds an event is also a useful Mirror observer of it (it
would give a stable, always-available reference, at the price of
pulling the protocol toward the infrastructure it is built to avoid).
`aiwa-platform` has no rendezvous of its own: the first connection
between two peers is a manual offer/answer exchange. A consequence
seen in practice: a contract published from one device can be loaded by
someone else only while a copy of its events is reachable — a hosted
export, or a keeper.

### 13.3 Proofs and the weighted median, combined — experimental

Observations in Mirror are **references, not opinions**: each resolves
to the progression event that fixes its epoch, an event only X's key can
sign. So an observation is also a *proof*. From proofs alone: the
highest epoch of X that any observer provably received is a *lower
bound* (one honest observer establishes it; no number of observers who
saw less can lower it; nobody but X can raise it); a report by X below
that bound contradicts X's own signed history (a *rewind*); and two
progression events of X held by observers, neither an ancestor of the
other, are a provable *fork*. The weighted median above remains the
estimate. `aiwa-core` combines the two (`src/triangulation.js`,
`src/position.js`, `assessPosition`) — **experimental, exported, and not
wired into `computeCausalTick`**:

- **position** = max(weighted median, proven lower bound): the vote is
  never reported below what is proven, and with no funded observer the
  proven bound stands alone.
- **accusation** (rewind or fork) comes from **proofs only**; the median
  never accuses, because "far from the median" cannot tell inflation
  from legitimate offline progress.
- Both rules see the same events. When the reader holds X's history
  **from epoch 1**, X's progression events are replayed through the
  reducer of §5–§6 (epoch + 1, chained to the last accepted transition,
  signed by X's key, sequential proof verified) and only the accepted
  ones count — what anyone who verifies does. Without the genesis nothing
  can be chained, so the check falls back to the signature alone and the
  result says so. A fork is the one exception, on purpose: a second
  lineage is rejected by the linear chain and is exactly the evidence of
  one, so forks are read from the signed events.

**On "X signs a fake itself".** A domain may write whatever it likes in
its own log. That is not an attack on the protocol: it is an invalid
history, refused by whoever verifies its sequential proof — at
reconnection as before — and left in the DAG as a dead branch nobody
counts. It matters here for one narrow reason: Mirror resolves a
commitment's references against the DAG as it is, so *colluders* can
sign commitments citing such an event, and a reader who does not replay
the chain would take it into the **estimate** — informational, never
applied to anyone's value. Replaying the chain closes that; a reader
without the genesis cannot, and falls back, visibly.

Compared in `experiments/triangulation-scenarios.mjs`, on synthetic
worlds that model the threats considered (the target's progression events
are a real chain — signed, chained, real sequential proofs — and the
observers' commitments are really signed; the worlds are chosen by their
author, so this is a comparison, not a proof):

| World | Weighted median (§13) | Proofs alone, log trusted | **Combined** (default) |
|---|---|---|---|
| Honest | 20 | 20 | 20 |
| Funded majority saw only an old state (4); one honest saw 20 | **4**; its check accuses the honest domain | 20 | **20** |
| Unfunded observers saw an old state | 20 | 20 | 20 |
| *Only* unfunded observers, all saw 20 | no tick | 20 | **20** |
| X rewinds to 10; funded majority saw 10; one honest saw 20 | 10; "consistent" | contradicted | **20, accused (rewind)** |
| X holds two unrelated histories at 21 | 21; no notion of a fork | fork | **21, accused (fork)** |
| Observer cites an event that does not exist | 20 | 20 | 20 |
| Only stale observers; X progressed offline to 20 | 4; accused | 4 | 4, *ahead by 16*, not accused |
| A forged event of X (signed by someone else) in the log; funded **minority** cites it | 20 | **999999** | **20** |
| The same, funded **majority** cites it | **999999**; accuses the honest domain | **999999** | **20** |
| X signs a fake far-ahead event itself, no sequential work; funded majority cites it | **999999** | **999999** | **20** (chain replayed, fake rejected) |
| **The same, but the reader holds only epochs 15–20 (no genesis)** | **999999** | **999999** | **999999**, `verification: signature` |

What this shows, and what it does not. The weighted median is a vote:
enough weight on an old view moves it, and a funded majority can move it
with an event that should never have counted. Proofs are immune to
observers who saw less, and yield rewinds and forks a vote cannot; the
chain replay is what stops either rule from being moved by a fake. The
last row is what remains: with colluders, and a reader that cannot
replay the chain, the estimate can be fooled — and the result says it
fell back to the signature. Other limits stand: no upper bound (being
ahead is reported, not accused); only as fresh as the freshest honest
observer (a keeper, §13.2, would help); and nothing about whether
observers are distinct actors — the count of observers is informational,
exactly like §13.1's hardware count. Independence is still the open
question; this removes the weight from the accusations, not that
assumption.

## 14. Relative rate, without a clock

$$w = (\mathrm{observer}, e_O, \mathrm{target}, e_X, \mathrm{sourceEventId}), \qquad \rho = \frac{e_{X,2} - e_{X,1}}{e_{O,2} - e_{O,1}}$$

a purely structural ratio from two successive witnesses. **Purely
informational** — never feeds back into what any domain may claim.

### 14.1 Composition is unsafe without a freshness bound

$\rho_{AB} \cdot \rho_{BC} = \rho_{AC}$ holds mathematically, but is
unsafe to compose across an intermediate domain whose real rate may
have drifted between measurements. `composeRelativeRates` requires the
real, verified epoch gap on the intermediate domain to stay within a
caller-supplied `maxFreshnessGap`, refusing composition outright
otherwise — a mitigation, never a closure.

---

## 15. Contract extension point

Conservation (§9) knows nothing about contracts. For a contract's own
conditional outcome to move real, spendable AIWA, some code must apply a
real state transition to the claim ledger — and it must not be a change
to the core protocol.

### 15.1 Contract identity

A contract id is a plain string. It carries no cryptographic anchor by
itself: a signature only ever proves "signed by this key, over this exact
content", never "this is really the trusted module it claims to be". The
anchor is the source hash of §16: `registerVerifiedContract` re-hashes a
contract's own currently-deployed source against a pinned expected value
before trusting its `verifyPayout`. The contract's id is embedded in the
signed event that uses it, rather than mangled into an address (§2's
address is already a direct cryptographic proof).

### 15.2 A generic payout mechanism

`applyWalletEvent` (`aiwa-core`, `src/wallet.js`) exposes one generic extension
point — a `contractVerifiers` map, $\{\mathrm{contractId} \mapsto
\mathrm{verifyPayout}\}$, supplied by the application, never by the
protocol's own source — and a `contract-payout` transfer that is accepted
only if the verifier registered for its contract id accepts it. A new
contract therefore needs no change to the core protocol, only a growing
application-level registry.

**Limit.** The extension point is real and tested; no concrete contract is
registered into it by the reference applications (the store ships none).

## 16. Publishing a single contract's source, content-addressed

A plain string contract id (§15.1) carries no cryptographic anchor by
itself. `contract-registry.js` (in `aiwa-core`) closes this: a contract's
own complete source is embedded — never only its hash — in a
`contract-spec` event, recoverable by anyone who receives it.

$$\mathrm{sourceHash} = \mathrm{SHA\text{-}256}(\mathrm{sourceCode})$$

**No canonical registry, deliberately.** Multiple, competing contracts
can coexist under different ids; wallets and users choose which to trust.
This mechanism is for **verifying one file's own source** against a
pinned hash before registering its `verifyPayout` into `contractVerifiers`
(§15.2) — a narrower, different job from §19 below, which publishes and
serves a whole, independently-runnable application.

**Limit.** `publishContractSpec`, `scanContractSpecs` and
`registerVerifiedContract` are real and tested; the reference applications
do not use them (they publish applications through §19).

### 16.1 Cross-runtime interoperability

`aiwa-core/interop/rust-vdf/` is an independent Rust implementation of
the protocol's most fundamental, custom computations: `vdf.js`,
`weighted-median.js`, `conservation.js`'s split invariant, `mirror.js`'s
monotonicity check, `relative-rate.js`'s central ratio,
`causal-tick.js`'s consistency check, `wesolowski-vdf.js`,
`bigint-math.js`, and §7's reward formula
(`reward.js`/`fixed-point-math.js`), whose algorithms are the same in
both.

`event.js`'s canonical id format (§3) — `domain`, `author`,
`authorPublicKey`, `parents`, `type`, `payload`, `createdAt` — is
checked against a real, fully signed `aiwa-core` event. Alongside
recomputing the canonical id, `ed25519-dalek` (a different library from
the `@noble/curves` the JavaScript uses) independently *re-signs* the
identical message with the identical raw secret-key bytes and checks the
result byte-for-byte against the real signature `@noble/curves`
produced. Since Ed25519 signing is deterministic (RFC 8032), this is a
stronger claim than mere verification: two independent, conforming
implementations must produce the *identical* signature, not merely one
that happens to pass the other's own check.

`test/rust-interop.test.mjs` builds the real Rust binary, runs it, and
compares its output against the live JS modules' own output for the
identical test vectors, byte for byte — including §7's reward formula
for two test vectors (a basic case and a full year of continuous
progression, ~112M epochs). It skips (never fails) if no Rust toolchain
is available in a given environment — see
`aiwa-core/interop/rust-vdf/README.md` for exactly what this does and
does not claim, and what remains undone (a multi-runtime implementation
of the whole protocol, as opposed to this cross-check of its most
fundamental, custom computations).

---

## 17. Delegation — "sign once, then click as many times as you want"

A real claim owner signs ONE
delegation — $\{\mathrm{delegate}, \mathrm{from}\}$, no amount cap, no
expiry by explicit design — after which a delegate key may move
**and split** the owner's already-owned claims repeatedly, each a
fresh, independent, delegate-signed event, without the owner's own root
key signing again.

$$\mathrm{delegation} = \big(\mathrm{delegate}, \mathrm{from}, \mathrm{ownerPubkey}, \mathrm{Sign}_{\mathrm{owner}}(\{\mathrm{delegate}, \mathrm{from}\})\big)$$

Each subsequent click — `'delegated-transfer'` or `'delegated-split'` —
carries the identical delegation record plus a fresh delegate signature
over the specific action's own fields. A verifier checks **two**
signatures, never one: the embedded delegation signature against
$\mathrm{ownerPubkey}$, and the action's own signature against the
delegate's key that actually signed it — closing the exact forgery §3's
own author/reducer separation warns about (a delegate that signed
something real, but not *this*, must never be accepted for *this*).

**No pre-funding, no escrow, by explicit design.** Issuing a delegation
moves zero funds. The delegate only ever authorizes moving what the
owner already, genuinely owns at click time — a compromised session key
threatens only funds the owner actually holds, for that one
counterparty, same as the root key already could.

**A channel is genuinely self-sufficient once opened, for any amount —
including splitting.** `aiwa-lib`'s `Channel` derives a deterministic
session key (`HMAC-SHA256`, keyed by the owner's own root secret,
seeded by the peer id) — recoverable after a crash, never a randomly
generated, losable throwaway. Because the SAME delegation authorizes
both `'delegated-transfer'` and `'delegated-split'`, a channel never
needs the owner's root key again for any send amount, not only ones
that happen to match an existing claim exactly. Verified directly:
`aiwa.disconnect()` (clearing the root key from memory) does not stop
an already-open channel from sending, splitting, or reporting its
balance.

**Honest limit, by explicit design.** No amount cap, no expiry, no
revocation. A deployment wanting either layers it into its own
`contractVerifiers` (§15.2) instead of forcing it on every caller here.

## 18. Bearer vouchers — a real withdrawal QR

A real, classic hash-lock — the
same idea a Lightning HTLC or a Bitcoin pay-to-hash-of-a-preimage script
uses — for the one case delegation and ordinary transfer both structurally
cannot cover: **the recipient is unknown until redemption time.**

$$\mathrm{voucherAddress} = \mathrm{SHA\text{-}256}(\mathrm{secret})$$

Issuing needs no new protocol at all: §9's own observation that
`owner`/`from`/`to` are opaque, unvalidated strings means an ordinary,
already-existing signed transfer to $\mathrm{voucherAddress}$ works
today — the issuer's root key signs exactly once, moving already-owned
value to an address no real key controls.

**Redemption.** A `'voucher-redeem'` event reveals $\mathrm{secret}$ and
is signed by the real identity the redeemer wants the value to land in:

$$\mathrm{Sign}_{\mathrm{redeemer}}(\{\mathrm{claimId}, \mathrm{secret}, \mathrm{to}, \mathrm{nonce}, \mathrm{timestamp}\})$$

verified by checking (a) the redeemer's signature really derives $\mathrm{to}$
— closing the same forgery class §17 closes: a real secret, revealed by
someone who does *not* control the claimed destination identity, must
never move value there — and (b) delegating entirely to §9's own
`transfer()` for the actual state change, with $\mathrm{from}$ recomputed
as $\mathrm{SHA\text{-}256}(\mathrm{secret})$.

**"The QR can be copied, but only the first redemption succeeds" —
needed no new double-spend logic.** This is §9's own single-writer
conservation invariant, unmodified: a claim's `deactivate()` throws once
its status is no longer `active`; a second redemption of an
already-consumed voucher is rejected exactly like a replayed ordinary
transfer. Verified directly, at both the protocol layer (two real
redeemers racing for the identical secret — only the first applied
wins) and the wallet-API layer (two genuinely independent, unsynced
wallets, each honestly redeeming the same offline voucher, converge to
exactly one winner once their event logs are synced — never both,
never neither).

**Honest limit, stated plainly, same as any offline transfer (§11).**
This is real double-spend *detection* via reconciliation, not real-time
*prevention*. Two people can each, honestly, offline, redeem the
identical voucher; both believe they succeeded until their logs sync
with each other or the issuer. Once they have, every reader agrees on
the winner (§11.2) — the smaller event id, which is arbitrary, not the
first in time.

---

## 19. Multi-file bundle publishing and sandboxed execution

A different mechanism from §16, which publishes one file's own source
for *verification* against a pinned hash. This publishes and serves a
whole, independently-runnable application — what the store lists.

**Two kinds of entry in the store, both submitted on GitHub.** In a `code` entry the package carries the app's one HTML file. In an
`aiwa` entry it carries only a *pointer*, the id of the signed manifest below, and the author signs the hash of that pointer
(`sha256` of `[id, name, version, description, kind, manifestId]`). The submission also carries the bundle's events, which the registry
has an event log verify (id, signature, parents), then checks against the pin: the manifest is signed by the author's domain, names
exactly this app and version, lists files all signed by that domain, and nothing else is in the bundle. The store does the same check
again before it runs anything, so the code is immutable and is what its author published whoever served the events; the bundle is
published in a log of its own, so that a manifest cites only its files and never the author's other history. What the bundle's code loads
from the network (a CDN, the SDK) is *not* pinned by the manifest.

$$e_{\mathrm{file}} = \{\mathrm{type}: \texttt{bundle.file}, \mathrm{domain}, \mathrm{payload}: \{\mathrm{path}, \mathrm{content}\}, \mathrm{parents}: [\,], \mathrm{createdAt}: 0\}$$

$$e_{\mathrm{manifest}} = \{\mathrm{type}: \texttt{bundle.manifest}, \mathrm{domain}, \mathrm{payload}: \{\mathrm{name}, \mathrm{version}, \mathrm{files}: \{\mathrm{path} \mapsto \mathrm{id}(e_{\mathrm{file}})\}\}, \mathrm{parents}\}$$

A file event is deliberately parentless with `createdAt` fixed at 0 —
its own bytes don't causally depend on when or by whom they were
published, only their content does, which content-addressing (§3)
already captures. Publishing the identical file content again (an
unchanged file across two app versions) yields the identical event id —
`EventLog.append`'s own dedup makes this a real no-op, never
retransmitted or duplicated. A manifest's parents are every file event
it references plus the domain's own prior heads, so `EventLog.head()`
naturally resolves to the latest manifest, and a genuine fork (two
competing manifest heads) is surfaced to the caller, never silently
resolved.

**Attribution needs no new protocol either.** `listBundlesByAuthor(log,
authorId)` scans for `bundle.manifest` events whose `author` field —
already cryptographically verified on every append (§3) — matches. "Find
published code by its creator's real address" was already answerable
before this function existed; it only makes the scan convenient.

**Execution: genuine origin isolation, not a convention.** A published
bundle may be arbitrary, untrusted code. A host that runs one must load
its `index.html` into a sandboxed `<iframe sandbox="allow-scripts">` —
deliberately never `allow-same-origin`; the store's app viewer
(`apps/web`) does so. This is a real, well-established browser mechanism,
not custom security code: the iframe receives a genuinely opaque
origin, with zero access to the parent page's storage, DOM, or
in-memory identity, enforced by the browser itself. A malicious
contract's own JavaScript can run, but it cannot reach the wallet that
opened it — the isolation does not depend on the contract's own author
behaving.

**Honest limit.** Storage inside a fully opaque origin is unreliable —
a published contract cannot depend on `IndexedDB` persisting across
sessions the way the hosting wallet's own storage does; an in-memory
event log is the safe default for a contract's own internal state.

---

## Reference implementation

| Concept | Package | File |
|---|---|---|
| Identity | `aiwa-core` | `src/identity.js` |
| Event log, content addressing (§3) | `aiwa-core` | `src/event.js`, `src/event-log.js`, `src/adapt-event.js` |
| Sequential proof (§6) | `aiwa-core` | `src/vdf.js`, `src/wesolowski-vdf.js`, `src/bigint-math.js` |
| Progression (§5) | `aiwa-core` | `src/progression.js` |
| Accrual formula (§7) | `aiwa-core` | `src/reward.js`, `src/fixed-point-math.js` |
| Accrual position | `aiwa-core` | `src/accrual.js` |
| Genesis Commitment (§8) | `aiwa-core` | `src/identity-cost.js`, `src/solana-wallet.js`, `src/burn-record.js` |
| Creator fee (§7.3) | `aiwa-core` + `aiwa-lib` | `aiwa-core/src/accrual.js` (`creatorFeeLamports`, `burnQuote`), `src/burn-record.js`, `src/solana-wallet.js`; `aiwa-lib/src/burns.js` (`burn`, `burnQuote`) |
| Churn profitability check (§8) | `aiwa-core` | `src/churn-analysis.js` — parameter-specific, not a general guarantee |
| Conservation (§9) | `aiwa-core` | `src/conservation.js` |
| Denomination (§10) | `aiwa-core` | `src/units.js` |
| Mirror (§4) | `aiwa-core` | `src/mirror.js` |
| Checkpoints, storage bound (§12.1) | `aiwa-core` | `src/checkpoint.js` |
| Recovery phrase (§12.2) | `aiwa-core` + `aiwa-lib` | `src/solana-wallet.js` (`generateBip39Mnemonic`), `aiwa-lib/src/wallet.js` (`recoveryPhrase`) |
| Backup, restore, adopt a state (§12.2) | `aiwa-lib` | `src/backup.js` (`exportBackup`, `importBackup`, `adoptState`) |
| Archive node (§12.2) | `aiwa-platform` + `aiwa-lib` | `src/archive.js`, `src/archive-server.js`, `node/aiwa-node.js`; `aiwa-lib/src/backup.js` (`archiveNow`, `restoreFromArchive`, `startAutoArchive`) |
| Wallet start and restore, by itself (§12.2) | applications | `apps/web/src/wallet.js`, `keys.js`; `android/.../SecretStore.kt` |
| Mining state, evidence an app takes (§7.2, §6.2) | `aiwa-core` | `src/mining-state.js`, `src/submission.js` |
| Succinct progression (§6.2) | `aiwa-core` | `src/succinct-vdf.js`, `src/progression.js` |
| Causal Tick (§13) | `aiwa-core` | `src/causal-tick.js`, `src/weighted-median.js` |
| Hardware roots (§13.1) | `aiwa-core` | `src/hardware-attestation.js` |
| Relative rate (§14) | `aiwa-core` | `src/relative-rate.js` |
| Fold order of concurrent branches (§11.2) | `aiwa-core` + `aiwa-lib` | `aiwa-core/src/canonical-order.js`; `aiwa-lib/src/ancestors.js`, `src/ledger.js` (`state`) |
| Contract extension point (§15) | `aiwa-core` | `src/wallet.js` (`contractVerifiers`, `contract-payout`) |
| Single-file contract publishing (§16) | `aiwa-core` | `src/contract-registry.js` |
| Signed actions, delegation (§17) | `aiwa-core` | `src/signing.js` (one place for every signed action), `src/wallet.js` |
| Channel (§17) | `aiwa-lib` | `src/channel.js` |
| Bearer vouchers (§18) | `aiwa-core` + `aiwa-lib` | `aiwa-core/src/wallet.js`, `aiwa-lib/src/payments.js` (`issueVoucher`/`redeemVoucher`) |
| Coherent composition (wallet state) | `aiwa-core` | `src/wallet.js`, `src/materializer.js` |
| Transport, replication | `aiwa-platform` | `src/webrtc-transport.js`, `src/replicator.js`, `src/introducer.js` |
| Capability-gated storage | `aiwa-platform` | `src/capability.js`, `src/guarded-data-store.js`, `src/graph-store.js` |
| Multi-file bundle publishing (§19) | `aiwa-platform` | `src/bundle.js`, `src/serve-worker.js` |
| Public wallet API | `aiwa-lib` | `src/wallet.js` (`AIWA`, a thin facade over `ledger`, `mining`, `burns`, `payments`, `observer`, `backup`, `evidence`) |
| Smart-contract/token SDK | `aiwa-lib` | `src/contract.js` |
| Cross-runtime interoperability (§16.1) | `aiwa-core` | `interop/rust-vdf/` (Rust), `test/rust-interop.test.mjs` |
| The store: listing, ranking, the two kinds of entry, sandboxed execution (§7.2, §19) | applications | `registry/` (`app-package.js`, `bundle.js`), `apps/web/` |
| The Android shell | applications | `android/` |

## Status

421 passing tests (`aiwa-core`, including a real Rust build+run
cross-check when a Rust toolchain is available), 85 (`aiwa-platform`),
99 (`aiwa-lib`). Every package is independently testable; none depends
on a shared, centrally-hosted server to run its own suite.

**Not demonstrated.** No run against the real Solana devnet or mainnet
(the burn path is tested against a stand-in that decodes real
transactions); no run on real phones; economic parameters not validated
in the field; no prior-art search; the creator fee (§7.3) and the store
have not been reviewed for their legal status.
