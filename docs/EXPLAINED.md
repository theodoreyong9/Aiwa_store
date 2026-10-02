# Aiwa, explained

*For anyone: no cryptography needed. The formal protocol is in the [yellow paper](YELLOWPAPER.md); this page says the same things in plain words, and says what is not solved. Français : [EXPLICATION.md](EXPLICATION.md).*

## The idea in one minute

Everyone keeps **their own notebook** of signed events (technically an event log). There is no shared ledger: people show each other their notebooks, and each one checks them by themselves.

There are two layers, independent of each other:

- **The foundation**: identity, notebook, transfers, contracts. It is free: no burn, and no network required.
- **Accrual**: the creation of new AIWA. It is the only thing that requires burning SOL.

| | Needs a burn? | Needs the internet? |
|---|---|---|
| Identity (your key), your notebook | no | no |
| Mining epochs (Aiwa's clock) | no | no |
| Receiving and transferring AIWA | no | only to move the events from one person to the other, by any means (file, QR, text…); the receiver checks the origin burn of a coin once, with Solana |
| Contracts, publishing an app | no | no |
| **Creating new AIWA (accrual)** | **yes** | for the burn itself (Solana); after that, mining is offline |

## 1. The foundation

**Your key.**
- You get 12 words that make a secret key. Your identity is the fingerprint (SHA-256) of the matching public key, and it is also your Solana address: the same words give the same account in a Solana wallet.
- There is no account and no sign-up: nothing is sent anywhere.

**Your notebook.**
- Everything you do is an event signed with your key. Its number is the fingerprint of its content, and it names its "parents", the events that come before it.
- Everyone keeps only what concerns them.

**Time, without a clock.**
- Your device does a computation that cannot be sped up by spreading it over many machines. One computation is one "epoch", with a proof that anyone checks in milliseconds (about 3.6 ms, against about 0.5 s to produce it, as measured in the paper).
- Advancing this counter needs no burn.

**Owning and transferring AIWA.**
- A claim belongs to a key. To transfer it, its owner signs, and each proof of transfer can be used only once.
- Receiving and passing on AIWA needs no burn.

**Contracts.**
- There is no machine that runs them for everyone. A contract is a set of rules ("state + event → new state") that each person replays from the events they hold. An action is proved by a signature placed inside the action itself; otherwise anyone could impersonate anyone.
- You can also publish an application: signed, addressed by its content, immutable, with its versions, and opened in an isolated frame that has no access to your wallet. Publishing needs no burn.
- A contract does not create AIWA. Only accrual does.
- Your contract state is what the events you hold give: it can differ from someone who holds other events.

## 2. How notebooks meet

Aiwa asks for one thing: that the events of someone else reach you, **by any means**. A file, pasted text, a QR code, a GitHub Pull Request (the store's registry), an archive node, or a direct connection between devices. The direct connection is only a convenience; this app does not even have one.

Once you have received someone's events, you can sign a **Mirror**: "this is what I saw of them". It is not a merge: both notebooks stay intact, simply linked. You can also estimate where someone else stands, by proofs and by a weighted vote. That is informational; in the vote, an observer's weight is their confirmed burn.

## 3. Branches, conflicts and double spending

**A branch is not a conflict.** Two events that do not cite each other are two branches, and that is ordinary. They meet again as soon as someone writes a new event, which cites every head they know.

**What signatures do, and do not.** Each event contains the fingerprint of its parents: changing the past breaks everything after it. But nothing prevents signing two different continuations of the same parent. It is a local computation with the key.

**Double spending.** It is exactly that case: two transfers of the same claim that cite the same parent.
- As soon as both are in the same notebook, the claim has one owner only. The other transfer is refused, and the refusal is recorded. Value is never duplicated.
- Every reader now picks the **same** winner: when it is a tie, the smaller event id wins. Before, it was the one received first, which differed from one reader to another. (A conflict that a checkpoint has already absorbed stays as the checkpoint decided it.)
- It is agreement, not fairness: the winner is not the first in time, and a cheater can try variants of his event until the one that suits him has the smaller id.

**What cannot be prevented offline.** Someone who holds their key can sign two spends without anyone seeing it. The person whose payment loses may have believed they were paid until they saw the other branch. Preventing that needs a common authority or a clock, which the protocol does not have. What remains: limiting the amount, trust, and the proof afterwards (two valid signatures of the same key on two spends of the same claim, checkable by anyone). An anchor on Solana only helps someone who can go online before handing over what they give; it does nothing for an exchange that stays entirely offline, and it is not built.

**Mining.** Forking your own mining history costs work: each event names the previous one in its signed part, so showing another history means redoing the computation. A "witness" (anyone who keeps an event they received) lets a registry require that it appears in the history shown next.

**Published applications.** If two versions fork, reading is refused with an error and you choose which one to read.

## 4. Accrual: creating value

**Why a cost.** Without a central authority, one more identity must cost something, otherwise anyone could make a thousand of them to create money for free. The cost is burning SOL, irreversibly and verifiably.

**The steps.**
1. **Burn** SOL by sending it to Solana's incinerator address.
2. **Choose T** (between 0 and 40 %) at the moment of the burn. T is a share of the burn that is not counted as capital: it is destroyed, except a tiny fixed part that goes to the creator of the software (see below). Your mining capital = burned × (1 − T).
3. **Mine**: your epochs raise what you can claim.
4. **Claim**: what has accumulated becomes a claim that is yours.

**The creator's part.** The burn is two transfers in one transaction: almost everything to the incinerator, and a small part to one fixed address, the creator's. Initially that part is 0.1 % of the T share: at T = 40 %, 0.0004 SOL per SOL burned; at T = 0 (the default), nothing. You do not choose who is paid, the wallet shows the split before you sign, and readers reject a commitment at T > 0 whose creator part was not paid. The address and the rate are protocol parameters: they change only with a new version of the rules, never from a setting.

**Verification.** Every reader reads the finalized transaction on Solana itself (successful, to the incinerator, paid by your key). Without a confirmed burn, a claim is rejected.

**The formula, in words.** The gain rises with capital and with the time since your last action. It falls as the identity gets older. A new burn replaces your position, and the old one is paid first. Epochs mined before burning earn nothing and even slow your rate afterwards.

## 5. Getting your history back

The 12 words give back the key, not the notebook. It comes back through a backup signed by you, an archive node, the state kept by the store's registry, or peers who received your events. On restore, the most advanced backup is taken, never a step backwards.

## 6. What is new, and what is not

The building blocks are known: a signed, hash-linked log (like git or Secure Scuttlebutt), a sequential-work proof (Wesolowski), burning as a cost, hash-locked vouchers, session keys. No prior-art search has been done. What is unusual is the combination and its economic design:

- **Creating value without consensus.** Each identity creates its AIWA locally, from verifiable sequential work, with an external burn as the only gate. In most systems issuance is tied to consensus; here it is not, which makes it compatible with partitions and offline use.
- **Work as each identity's own clock.** Actions are bound to the work by the signature, so hiding or reordering an action costs computation. The rule "an action replaces the position and the old one is paid first" and the destroyed share T come with it.
- **A proof any third party checks in milliseconds.** A registry reads someone's mining state without trusting them.

Not demonstrated: no real deployment yet, no run on the real Solana devnet, economic parameters not validated in the field.

## 7. What is not solved

- Nothing proves that two identities are two people.
- Nobody is paid to keep other people's data.
- There is no automatic rendezvous between strangers.
- Solana is the only external dependency, for the burn.

**Not verified:** the complete path has only been played against a stand-in Solana (25 checks out of 25), not on the real devnet nor on real phones. That Claude writes a working contract has not been verified either.

## Where to read more

[Yellow paper](YELLOWPAPER.md) (formal protocol) · [`packages/core`](../packages/core) (the protocol) · [`packages/platform`](../packages/platform) (transport, storage, archive node) · [`packages/lib`](../packages/lib) (wallet API and contract SDK) · [plan](PLAN.md).
