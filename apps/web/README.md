# aiwa-store-web

The store and the wallet, as one web app. It runs in a browser and, unchanged, inside the Android app (a WebView).

| Tab | What it does |
|---|---|
| **Store** | Lists the registry's apps, ranked by `score / laps`; search; **Open** runs an app in a sandbox. Works offline on the last list and on apps already opened. |
| **Wallet** | An Aiwa wallet (`aiwa-lib`): connect with a recovery phrase or make an identity, balances, **burn** (T chosen at the burn; the wallet shows what goes to the creator before signing), claim, send and receive (signed codes, QR), history, recovery (phrase, backup, archive nodes). |
| **Publish** | Turns an HTML file into a signed package with your mining evidence — one `submission.json` for a pull request (see `registry/`). Nothing is sent from the app. |

## How an app runs

An app is untrusted code. It is loaded into `<iframe sandbox="allow-scripts">` — never `allow-same-origin` — so the
browser gives it an opaque origin: no access to the page's storage, DOM or wallet (yellow paper §19). Before that, the
store checks the package: its hash is that of its content, the author's signature covers it, and it is what the registry
lists (`src/store.js`). A host that serves another file than the one signed is refused. An app can use the network, and
has no storage that survives; it is not reviewed.

## Build and test

```
npm run build -w aiwa-store-web      # -> apps/web/dist (about 600 KB; @solana/web3.js and the QR code load on demand)
npm test -w aiwa-store-web           # unit tests + the app in Chromium
```

The end-to-end test builds the app with a test deployment, produces a registry the way the real one is produced (wallets
that burned and mined, packages they signed, submissions validated by `aiwa-registry`), serves both, and drives Chromium:
ranking, the sandbox (an app that tries `parent.document` is refused by the browser), a tampering host, offline use, the
burn with its creator fee, and a submission made by the browser's wallet that the registry accepts. Solana is a stand-in
that decodes the transaction the wallet builds. `npx playwright install chromium` once (the version is pinned).

## Configuration

`deployment.json` at the repository root, shared with the registry (the build resolves `@deployment` to it):
the economic parameters, the RPC endpoint, the registry's URL, and the creator fee (`creatorFee.address` is `null` until
set: with no address there is no fee, and the wallet says so).

## Hand-off from the Android app

The app's dictation module can have Claude write an app and open this page at `#publish=1;<name>;<code>` (`<code>`: the
file, raw-deflated, base64url). The Publish form is filled in; nothing is signed or sent until the author presses
**Prepare submission**.
