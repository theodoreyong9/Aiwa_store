# Plan

## What version 1 is

One repository, one promise: **an app store with a wallet, usable on a phone within minutes, on a protocol with no shared ledger.**

| Part | What it does |
|---|---|
| `packages/core` | The protocol, pure and without I/O: identity, signed events, the log, progression (verifiable work), accrual, conservation, canonical fold order, verification of a submission. |
| `packages/platform` | Distribution: transport, replication, storage, publication of apps, the archive node. |
| `packages/lib` | The wallet API (12-word recovery, backup, restore, burn, payments), the contract SDK. |
| `apps/web` | The store and the wallet as one web app: list, rank, open apps in a sandbox; the wallet starts and restores by itself; one sheet publishes. |
| `registry` | The open listing: validation and merge of submissions of both kinds of app, ranking by the existing `score / laps`. |
| `android` | One APK: the web app in a WebView, plus the dictation widget for Claude Code as an optional module. |
| `docs` | The yellow paper, the explanation, the business model. |

## What version 1 leaves out

Peer-to-peer discovery between strangers, a graph data layer, hardware attestation as a requirement, any payment other than the creator fee, a price or purchase flow for apps.

## The creator fee

A fixed share of the burn is sent to a creator address that is a constant of the protocol, not a choice of the application or of the user. The verifier counts it; a burn that omits it counts for less. The first value is 0.1 % of the T share, changeable later through a versioned rule. See the yellow paper.

## Where each step stands

| Step | State |
|---|---|
| Protocol, distribution and wallet packages in one workspace, one lock file | done — core 421 tests, platform 85, lib 100. The protocol was rewritten for clarity without changing a byte it signs (one signing module; the wallet class split into focused modules); the signed messages' bytes are pinned by a test |
| The creator fee in the burn: rule, verification, wallet transaction | done, with tests (core 8 + lib 5); the creator address is set in `deployment.json` |
| The yellow paper, standalone, with the fee (§11) and the order of conflicting branches (§13.4) | done |
| The documents: the README, the plain-words explanation (EN and FR) and the yellow paper (seven parts and four appendices, in the order the system works: foundations, the three pillars, creating value, agreement without a clock, applications, observation, assessment), each with diagrams of how it **functions**; `scripts/check-diagrams.mjs` renders every diagram and checks every `§` reference in CI | done |
| The registry: signed packages of two kinds (code, or a pointer to a bundle published through Aiwa), validation with the protocol's own checks, ranking `score / laps`, public baselines, workflow | done, 17 tests against a stand-in Solana; the workflow itself has not run on GitHub |
| The web app: store, wallet that starts and restores by itself, one publish sheet with the author's GitHub login, an SDK module for contract apps | done, unit tests and 20 in Chromium (ranking, sandbox, tampering host, offline, the wallet starting by itself and its history coming back, the burn with the fee, publishing of both kinds through stand-ins of the Android host and of GitHub whose pull request is given to the real registry code, an app that uses the SDK) |
| The Android app: the web app in a WebView, keystore and GitHub device login for the page, the dictation module as an option | written; compiled in CI; the device flow's logic and the hand-off link unit-tested; **never run on a phone**; backend 73 Python tests |
| Apps that use the wallet: a declared `<meta name="aiwa-wallet">`, a banner, a door (who I am, pay, receive, show or scan a code); the **click duel** between two phones (WebRTC linked by two codes, a price per click, 20 s, the one who clicked less pays, no signature per click) | done and tested between two pages of one Chromium, against a stand-in Solana. **Not** between real phones. Left for later, on purpose: nobody is stopped from lying about their count, no escrow, no cap, no search for players nearby |
| The business model, with its real numbers | [docs/BUSINESS.md](docs/BUSINESS.md) |

## Not verified

- A real burn on Solana (devnet or mainnet), end to end. `scripts/devnet-check.mjs` does it (burn at T = 0.4 with a creator address made for the run, the creator account read on chain, the registry's own verification, the fail-closed check) and passes against a stand-in; run for real from GitHub (the *Devnet check* workflow) the RPC answered (solana-core 4.3.0) but the faucet refused the shared runner twice (429, daily limit per IP). It needs a devnet wallet funded once — faucet.solana.com — whose phrase goes in the repository secret `DEVNET_PHRASE`, or `node scripts/devnet-check.mjs --phrase "…"` from your own machine or phone.
- The Android app on a real device: the WebView host, the keystore, **GitHub's device login against the real GitHub** (it needs an OAuth App with Device Flow enabled, whose Client ID goes in `deployment.json`), Android's automatic backup carrying the wallet's history to a new phone, the Termux backend, the widget. Google Play policy.
- A pull request opened by the Store's sheet on GitHub, end to end (the sheet's calls are tested against a stand-in of GitHub's API).
- The registry workflow on GitHub, with a real pull request. GitHub Pages must be enabled for the site to be served (Settings → Pages → Source: GitHub Actions).
- The legal status of the creator fee and of the store, and the economic parameters in the field.

## Definition of done for a step

Tests pass in CI; documentation says exactly what was and was not verified; no claim without a check.

## Known risks

- **The camera in the Android app has never run.** The page reads QR codes with `getUserMedia` and decodes them in JavaScript (jsQR, tested in Chromium against a fake camera that films a code); `StoreActivity` grants the camera to the page's own origin, for video only, after Android's permission (written, compiled in CI). Whether a phone's WebView then delivers frames is not known. Pasting a code always works.
- **An app that declares the wallet can spend it.** The door has no cap, no expiry and asks nothing; the Store only shows a banner. Kept apart from the sandbox on purpose, to be decided later.

- Everything involving real Solana has only been played against a stand-in. The first real run is a prerequisite for any claim.
- A permissionless store attracts abuse: sandbox by design, plus listing rules and reporting.
- The creator fee and a store that lists paid-for things need legal advice before launch.
- The Android build can only be compiled in CI; the dictation module needs Termux and is optional for that reason.
