# Aiwa Store

**A pocket app store with its own wallet. Apps open in a sealed box, and the list is ranked by the work each author has done: no ads, no ratings, nobody editing it.**

<p align="center">
  <img src="docs/img/store.png" width="23%" alt="The Store: apps ranked">
  <img src="docs/img/app.png" width="23%" alt="An app, open in its sandbox">
  <img src="docs/img/wallet.png" width="23%" alt="The wallet: a burn, with what the creator receives">
  <img src="docs/img/publish.png" width="23%" alt="The publish sheet">
</p>
<p align="center"><sub>The Store · an app, open · the wallet's burn, showing what the creator receives before you sign · the publish sheet.<br>
The real screens at phone size. The apps and the Solana network are demo stand-ins.</sub></p>

> **Where it stands.** Pre-release. Everything is tested in CI, but it has **never run on a phone** and **never on the real Solana network**.
> [What that means exactly](#where-it-stands).

## What you do with it

- **Open apps.** Each one is signed by its author and checked before it opens. It runs in a sealed box: it cannot touch your wallet.
- **Earn AIWA.** Burn a little SOL once (send it to an address nobody controls). Then your phone computes while the app is open, and what you
  can claim grows with time.
- **Publish an app.** Dictate it to Claude Code with the widget, press ▦, read it, press **Publish**. The Store signs it with your wallet and
  opens the pull request for you. No form, no file to carry.

## How it works

```mermaid
flowchart LR
  A["1. You burn SOL<br/>on Solana, once"] --> B["2. Your phone works<br/>while the app is open"]
  B --> C["3. You publish<br/>a signed app + proof of the work"]
  C --> D["4. A GitHub workflow<br/>checks it and ranks you"]
  D --> E["5. Every Store lists it,<br/>checks it, opens it sealed"]
```

1. **Burn.** The only thing that costs anything, and the only thing that needs Solana. 0.1 % of the part of the burn you choose to set aside
   (called T) goes to the creator of the software, in the same transaction, shown before you sign. At T = 0, nothing.
2. **Work.** Each unit of work is signed, and anyone can check it in milliseconds. The more time since your last action, the more you can claim.
3. **Publish.** Your app, signed with your wallet, with proof of your work.
4. **Rank.** The registry is a GitHub workflow: it checks the signature, the proof and the burn on Solana itself, then ranks you by
   `score / laps` (what you can claim, over the epochs since your last action).
5. **Read.** Each Store checks every app against its author's signature before it opens it.

There is no server of ours and no shared ledger: GitHub keeps the list, Solana keeps the burns, and each phone keeps its own signed history.
All of it, with pictures, in [docs/EXPLAINED.md](docs/EXPLAINED.md) ([en français](docs/EXPLICATION.md)).

## Install (Android)

### The Store and the wallet

1. On the phone, open the [latest release](https://github.com/theodoreyong9/Aiwa_store/releases/tag/android-latest) and download **Aiwa_store.apk**.
2. Open it from Downloads and tap **Install**. Android asks once to allow installs from your browser or Files app. A new version installs over
   the old one.
3. Open **Aiwa Store**, tap **Wallet**, then **Create my wallet**. Write down the 12 words: they are the only way back on another phone.

That is all the Store needs. The widget below is optional.

### The widget, to dictate an app (optional)

The widget talks to Claude Code, which runs in **Termux** on the same phone. You need your own Claude account.

1. Install **Termux from F-Droid** (the Play Store version is no longer maintained).
2. In Termux, paste this one line and wait: it installs the backend, and it also downloads the latest APK into Downloads (so it can replace step 1 above).
   ```sh
   curl -fsSL https://raw.githubusercontent.com/theodoreyong9/Aiwa_store/main/android/backend/bootstrap.sh | bash
   ```
   Run the same line again later to update.
3. On the home screen: press and hold → **Widgets** → **Aiwa Store** → drag the 4×2 widget. Android opens a short setup: allow Aiwa to run
   Termux commands, and notifications.
4. Tap **Connecter Claude** on the widget and log in with your Claude account (a page opens, the code goes back into the same window).
5. Tap **Dicter un message** and say what the app should do. When Claude has written it, press **▦** on the widget: the Store opens its publish sheet.

The widget is in French for now. Not verified on a real phone: Claude Code has no Android build, so the backend installs it in a Linux layer
inside Termux, which the script itself marks as experimental. More in [docs/ANDROID.md](docs/ANDROID.md).

## Try it from source

```sh
npm install
npm test                                  # protocol, registry, web app (needs Chromium: npx -w aiwa-store-web playwright install chromium)
npm run build -w aiwa-store-web           # apps/web/dist
npx http-server apps/web/dist -p 8080     # open http://localhost:8080
node scripts/devnet-check.mjs --fake      # the whole path against a stand-in Solana
```

The Store lists nothing until the registry has accepted an app. To publish from a plain browser, the same sheet hands over the signed file to
add to a pull request yourself (`submissions/`, see [registry/README.md](registry/README.md)).

## What only the repository's owner can do

These are settings and accounts. Until they are done, the matching workflow is **red**, with the reason on its page.

| To get | Do this |
|---|---|
| The site served (workflow *Pages*) | [Settings → Pages](https://github.com/theodoreyong9/Aiwa_store/settings/pages) → Source: **GitHub Actions**. Then re-run *Pages* |
| GitHub sign-in in the publish sheet | Create a GitHub OAuth App named *Aiwa Store* with **Device Flow** enabled, put its Client ID in `deployment.json` (`github.clientId`) |
| A burn on the real network (workflow *Devnet check*) | Fund a devnet wallet once (faucet.solana.com) and add its 12 words as the secret [`DEVNET_PHRASE`](https://github.com/theodoreyong9/Aiwa_store/settings/secrets/actions) |
| A first real run | Install the APK on a phone and try it: it is the main thing nobody has done |
| Legal peace of mind | Advice on the creator's share and on running a store, before launch |

## Where it stands

| Tested in CI | Not verified |
|---|---|
| The protocol: identity, signed events, proofs of work, accrual, claims, double spend (421 tests, with a cross-check against an independent Rust implementation) | A real burn on Solana |
| The wallet, the creator's share, backup and restore (99 + 85 tests) | The Android app on a real phone: keystore, GitHub sign-in, Android's backup, the widget (it is compiled in CI, never run) |
| The registry: signatures, proofs, burns, ranking (17 tests) | A real pull request through the registry workflow |
| The web app in a real Chromium: ranking, sandbox, tampering, offline, restore, burn, publishing of both kinds (35 tests) | The legal status of the creator's share |
| The whole path against a stand-in Solana (`devnet-check --fake`) | The economic parameters in the field |

[docs/PLAN.md](docs/PLAN.md) has the full list.

## Read more

| | |
|---|---|
| [docs/EXPLAINED.md](docs/EXPLAINED.md) · [EXPLICATION.md](docs/EXPLICATION.md) | how everything works, in plain words, with pictures |
| [docs/YELLOWPAPER.md](docs/YELLOWPAPER.md) | the formal protocol, in the same order |
| [docs/ANDROID.md](docs/ANDROID.md) | the Android app and the widget |
| [docs/BUSINESS.md](docs/BUSINESS.md) | the business model, with numbers and limits |
| [docs/PLAN.md](docs/PLAN.md) | what version 1 contains and leaves out |

<details>
<summary>The GitHub workflows</summary>

| Workflow | When | What it does |
|---|---|---|
| **CI** | each push | the whole test suite, the dry run of the real-network check, and the documents (every diagram renders) |
| **Android** | a push touching the app | builds the APK; from `main`, publishes it as the `android-latest` release |
| **Registry** | a pull request adding `submissions/*.json` | validates the submission as data, writes `store/` if accepted, closes the pull request with the verdict |
| **Pages** | a push to `main`, or after the registry | publishes the site. **Red until Pages is switched on** (see above) |
| **Devnet check** | by hand | a burn with the creator's share on the real devnet. **Red until `DEVNET_PHRASE` exists** |

</details>

<details>
<summary>What is in the repository</summary>

| | |
|---|---|
| [`packages/core`](packages/core) | the protocol: identity, signed events, verifiable work, accrual, conservation, canonical fold order, creator fee |
| [`packages/platform`](packages/platform) | transport, replication, storage, bundles, the archive node |
| [`packages/lib`](packages/lib) | the wallet API (12-word recovery, backup, restore, burn, payments) and the contract SDK |
| [`registry`](registry) | what decides which apps are listed and in what order |
| [`apps/web`](apps/web) | the Store and the wallet as one web app |
| [`android`](android) | the APK: the web app in a WebView, plus the optional widget |
| [`deployment.json`](deployment.json) | the parameters the wallet and the registry share, including the creator fee and its address |
| [`docs/demo-apps`](docs/demo-apps) | the demo apps of the screenshots (`node scripts/screenshots.mjs` redraws them) |

</details>

## License

MIT, see [LICENSE](LICENSE).
