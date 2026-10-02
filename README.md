# Aiwa Store

An app store you carry in your pocket, with its own wallet, on a protocol that needs no shared ledger.

- **A store of apps** that open and run inside, in a sandbox with no access to your wallet. Each app is signed by its author and checked before it opens. The list is ranked by `score / laps`: what the author has mined, frozen when the registry accepted it. Nobody edits it.
- **An Aiwa wallet** from 12 words. Burn SOL once, and your device mines AIWA by doing verifiable work, offline; anyone can check that work in milliseconds. Everyone keeps their own notebook of signed events: there is no shared ledger.
- **A creator fee**: 0.1 % of the T share of each burn goes to one fixed address, in the same transaction, in plain sight, enforced by every reader. The wallet shows it before you sign. At T = 0, nothing.
- **One Android app**: the store and the wallet, and, as an **optional** module, a widget to dictate to Claude Code (not specific to Aiwa) with the distributed protocol behind it.

> **Status.** Everything below is tested in CI except the Android build, which is compiled in CI but has never run on a phone. Nothing has run against the real Solana network or on real money: Solana is a stand-in that decodes the real transaction the wallet builds. The first real run is a prerequisite for any claim. [docs/PLAN.md](docs/PLAN.md) says exactly what exists and what does not.

## What is in the repository

| | |
|---|---|
| [`packages/core`](packages/core) | the protocol: identity, signed events, verifiable work, accrual, conservation, canonical fold order, creator fee |
| [`packages/platform`](packages/platform) | transport, replication, storage, bundles, the archive node |
| [`packages/lib`](packages/lib) | the wallet API (12-word recovery, backup, restore), the contract SDK |
| [`registry`](registry) | what decides which apps are listed and in what order; a workflow validates pull requests |
| [`apps/web`](apps/web) | the store and the wallet as one web app, tested in Chromium |
| [`android`](android) | the APK: the web app in a WebView, plus the optional dictation module |
| [`docs`](docs) | yellow paper, plain-words explanation, plan, business model, Android notes |
| [`deployment.json`](deployment.json) | the parameters the wallet and the registry share, including the creator fee (its address is `null` until set) |

## Try it

```sh
npm install
npm test                                  # protocol, registry and web app (a real Chromium: npx -w aiwa-store-web playwright install chromium)
npm run build -w aiwa-store-web           # apps/web/dist
node scripts/devnet-check.mjs --fake      # the real-network check, dry run; without --fake it burns devnet SOL (see docs/PLAN.md)
npx http-server apps/web/dist -p 8080     # open http://localhost:8080
```

The store lists nothing until the registry has accepted an app (`store/index.json` is empty at the start). To publish: connect
your wallet, burn a little SOL and let it mine, write an app in the Publish tab, and put the file it produces in a pull request
(`submissions/`). See [`registry/README.md`](registry/README.md).

**On Android:** the APK is built by the *Android* workflow and published as
`https://github.com/theodoreyong9/Aiwa_store/releases/download/android-latest/Aiwa_store.apk` (installed by hand, outside
Google Play). The dictation module is optional and needs Termux: see [docs/ANDROID.md](docs/ANDROID.md).

## Where to read next

| | |
|---|---|
| [docs/EXPLAINED.md](docs/EXPLAINED.md) · [EXPLICATION.md](docs/EXPLICATION.md) | how it works, in plain words, from the key to the double spend |
| [docs/YELLOWPAPER.md](docs/YELLOWPAPER.md) | the formal protocol, including the creator fee (§7.3) and the order of conflicting branches (§11.2) |
| [docs/PLAN.md](docs/PLAN.md) | what version 1 contains, what it leaves out, what was and was not verified |
| [docs/BUSINESS.md](docs/BUSINESS.md) | the business model, with its numbers and its limits |
| [docs/ANDROID.md](docs/ANDROID.md) | the Android app and the dictation module |

## License

MIT, see [LICENSE](LICENSE).
