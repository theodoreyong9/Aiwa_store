# Plan

## What version 1 is

One repository, one promise: **an app store with a wallet, usable on a phone within minutes, on a protocol with no shared ledger.**

| Part | What it does |
|---|---|
| `packages/core` | The protocol, pure and without I/O: identity, signed events, the log, progression (verifiable work), accrual, conservation, canonical fold order, verification of a submission. |
| `packages/platform` | Distribution: transport, replication, storage, publication of apps, the archive node. |
| `packages/lib` | The wallet API (12-word recovery, backup, restore), the shared recovery panel, the contract SDK. |
| `apps/web` | The store and the wallet as one web app: list, rank, open apps in a sandbox. |
| `registry` | The open listing: validation and merge of submissions, ranking by the existing `score / laps`. |
| `android` | One APK: the web app in a WebView, plus the dictation widget for Claude Code as an optional module. |
| `docs` | The yellow paper, the explanation, the business model. |

## What version 1 leaves out

Peer-to-peer discovery between strangers, a graph data layer, hardware attestation as a requirement, any payment other than the creator fee, a price or purchase flow for apps.

## The creator fee

A fixed share of the burn is sent to a creator address that is a constant of the protocol, not a choice of the application or of the user. The verifier counts it; a burn that omits it counts for less. The first value is 0.1 % of the T share, changeable later through a versioned rule. See the yellow paper.

## Order of work

1. This commit: README, license, plan.
2. Import the protocol, distribution and wallet packages with their tests: one repository, one lock file, no pinned commits.
3. The creator fee in the burn: rule, verification, wallet transaction, tests.
4. The yellow paper, standalone, including the fee and the ordering of conflicting branches.
5. The registry and the web app (store + wallet).
6. The Android app: the web app in a WebView, the widget as an optional module.
7. The business model, written with its real numbers.

## Definition of done for a step

Tests pass in CI; documentation says exactly what was and was not verified; no claim without a check.

## Known risks

- Everything involving real Solana has only been played against a stand-in. The first real run is a prerequisite for any claim.
- A permissionless store attracts abuse: sandbox by design, plus listing rules and reporting.
- The creator fee and a store that lists paid-for things need legal advice before launch.
- The Android build can only be compiled in CI; the dictation module needs Termux and is optional for that reason.
