# aiwa-store-web

The store and the wallet, as one web app. It runs in a browser and, unchanged, inside the Android app (a WebView).

| Tab | What it does |
|---|---|
| **Store** | Lists the registry's apps, ranked by `score / laps`; search; **Open** runs an app in a sandbox. Works offline on the last list and on apps already opened. |
| **Wallet** | An Aiwa wallet (`aiwa-lib`) that starts by itself: balances, **burn** (T chosen at the burn; the wallet shows what goes to the creator before signing), claim, send and receive (signed codes, QR), the author's apps (refresh a ranking), history, the 12 words (shown once, and on request). Its history comes back by itself on a new phone (`src/wallet.js`). |

There is no Publish tab. The **publish sheet** (`src/publish-ui.js`) opens when the Android app hands over an app (the widget's ▦ button), and does one thing: sign
what the author is looking at and open a pull request on their GitHub account (`src/github.js`: fork, branch, file, pull request;
the login is GitHub's device flow, run by the Android app).

## How an app runs

An app is untrusted code. It is loaded into `<iframe sandbox="allow-scripts">` — never `allow-same-origin` — so the
browser gives it an opaque origin: no access to the page's storage, DOM or wallet (yellow paper §17.2). Before that, the
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
ranking, the sandbox (an app that tries `parent.document` is refused by the browser), a tampering host, offline use, the wallet
starting and coming back by itself, its history coming back from the registry, the burn with its creator fee, publishing of both
kinds through a stand-in of the Android host and of GitHub (the pull request's file is then given to the real registry code), and an
app that uses the SDK. Solana is a stand-in that decodes the transaction the wallet builds. `npx playwright install chromium` once (the version is pinned).

## Configuration

`deployment.json` at the repository root, shared with the registry (the build resolves `@deployment` to it):
the economic parameters, the RPC endpoint, the registry's URL, the creator fee (with no address there is no fee, and the wallet
says so), `github.clientId` (the OAuth App of the login) and `archiveNodes` (optional always-on holders of backups).

## Two kinds of app

`code`: the package carries the HTML. `aiwa`: the package carries a pointer, the id of a signed manifest published through Aiwa
(`aiwa-platform`'s bundle); the Store fetches the bundle's events, has Aiwa verify them against that id, and runs the result
(`src/store.js`, `src/assemble.js` puts a bundle's scripts and stylesheets inside its `index.html`). The SDK such an app imports is
built here too (`lib/aiwa.js`, from `lib-entry.js`); see `docs/aiwa-app-example.html`.

## What the page asks of the Android app

`src/host.js`: a request/response channel (`window.AiwaHost`) for the keystore (`src/keys.js`: the 12 words, the GitHub token),
GitHub's login, saving a file and the dictation screen. In a browser there is no such channel: the secrets are kept in the
browser's storage (the Recovery section says so), and publishing hands over the signed file.

## Hand-off from the Android app

The app's dictation module can have Claude write an app and open this page at `#publish=<kind>;<name>;<code>` (`<kind>`: `code`
or `aiwa`; `<code>`: the file, raw-deflated, base64url). The publish sheet opens with it; nothing is signed or sent until the author presses
**Publish**.
