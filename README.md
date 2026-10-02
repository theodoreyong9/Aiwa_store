# Aiwa Store

An app store you carry in your pocket, with its own wallet, on a protocol that needs no shared ledger.

> **Status: starting point.** This repository is being built in the open, step by step; [docs/PLAN.md](docs/PLAN.md) says what exists and what does not.
> Nothing here has been run on real money, on the real Solana network, or on a real phone yet.

## The idea in one minute

- **Everyone keeps their own notebook** of signed events. There is no shared ledger: people show each other their notebooks and each one checks them by themselves.
- **A wallet from 12 words.** Burn SOL once, and your device mines AIWA by doing verifiable work, offline. Anyone can check your work in milliseconds.
- **A store of apps** that run in a sandbox with no access to your wallet. Apps are signed by their author; the listing is ranked by a score nobody can edit.
- **A creator fee**: a small fixed share of each burn goes to the creator of the protocol, in plain sight, enforced by the verifier.
- **One Android app**: the store, the wallet and, as an optional module, a widget to dictate to Claude Code.

## Where to read next

| | |
|---|---|
| [docs/PLAN.md](docs/PLAN.md) | what version 1 contains, what it leaves out, the order of work |
| [docs/EXPLAINED.md](docs/EXPLAINED.md) | how it works, in plain words, from the key to the double spend |
| [docs/YELLOWPAPER.md](docs/YELLOWPAPER.md) | the formal protocol |

## License

MIT, see [LICENSE](LICENSE).
