# Android

One APK, `com.aiwa.store`. Its front door is the **Store**: the web app (`apps/web`) in a WebView, served by the app itself at
`https://appassets.androidplatform.net/` — a real origin, so the wallet's journal (IndexedDB) and module scripts work. The wallet, the
ranked list and the sandbox apps run in are the web app's; the activity only hosts it (`StoreActivity.kt`).

## Where the page comes from

The page is the site's, not the APK's: the Store updates itself, and a new APK is only needed to change the app around it (the widget,
the permissions, the Keystore, this code). Two copies, served under the same origin so the wallet's storage is the same:

| Copy | Served at | When |
|---|---|---|
| inside the APK | `/assets/web/` | the first run, offline, and whenever it is newer than the other |
| downloaded from the site (`siteUrl` in `deployment.json`) | `/site/` | from the start after the download, if it is newer than the copy inside the APK |

The download is checked entirely before it is kept (`SiteRelease.kt`, the same checks as `apps/web/release.mjs`):

1. `release.json` lists every file with its SHA-256; `release.sig` is an Ed25519 signature of its exact bytes. The **site key** that
   checks it (`siteKey` in `deployment.json`) is in the APK, so a hijacked site, or a network that rewrites the page, cannot make the
   phone run anything: without the signature nothing is kept.
2. Each file is read and compared with its hash; a name that climbs out of the folder, a missing `index.html`, more than 400 files,
   5 MB for a file or 24 MB in all are refused.
3. All of it goes to `pending/` as a whole, or nothing does. It replaces the page at the **next start**, never while the page runs
   (the Store loads some of its code late, and must find the files of its own release).
4. A release older than the one held (a replay of an old signed page), or listing the very same files, is not an update. After the
   APK itself is updated, a downloaded copy older than the page inside it is dropped.

The app looks once an hour, in the background, and says "ferme l'appli et rouvre-la" when a release is ready. The release is signed by the
**Pages** workflow with the repository secret `SITE_SIGNING_KEY` (the seed that goes with `siteKey`); without the secret the site is
deployed, a browser opens it, the workflow ends red, and the phones keep the page they have. To change the key: `node scripts/site-key.mjs`,
put the public key in `deployment.json`, the seed in the secret, and ship a new APK (the old APKs only trust the old key).

Not run on a phone: the checks are unit-tested on a JVM (`SiteReleaseTest`, against a release signed by the Node side), the download, the
swap and the serving from app storage have never run on a device.

**Install, step by step** (the Store and wallet, then the optional widget): [README → Install](../README.md#install-android).

## What the page may ask of the phone

One channel, `window.AiwaHost`, added by the app to the page's own origin only. The frame an app runs in has another (opaque)
origin: it does not get the channel (an object added with `addJavascriptInterface` would be injected in every frame, apps
included). The page sends requests with an id and the app answers `{ id, result }`, `{ id, error }` or `{ id, progress }`:

| Command | What it does |
|---|---|
| `secret-get` / `secret-set` / `secret-delete` | the wallet's 12 words and the GitHub token, encrypted with an AES key that lives in the Android Keystore (`SecretStore.kt`) |
| `github-login` | GitHub's device flow, run natively because GitHub's login endpoints send no CORS headers (`GitHubDeviceFlow.kt`): the code to type is sent as progress (it is also copied, and github.com opened), the token as the result. Scope `public_repo` only; the OAuth App is `deployment.json`'s `github.clientId`, with Device Flow enabled |
| `save` | a text file into Downloads |
| `dictation` | open the dictation module's screen |

The camera is not a command: the page reads QR codes with `getUserMedia` (a payment received, two phones pairing for an app), which the WebView asks of `StoreActivity` as a permission request. It is granted to the Store's own origin only, for video only, once Android's camera permission is given.

The back button closes what is open on top (the sheet, an app) before it leaves. Links out of the Store open in the browser.

**Backup.** Android's automatic backup is on: the WebView's storage, where the wallet's journal lives, follows its owner to a new phone
(Google Drive or a phone-to-phone transfer), without any server of ours. The secrets are excluded (`res/xml/data_extraction_rules.xml`): a
Keystore key does not move, so the 12 words are typed once on the new phone, and the journal is there. If the backup did not carry it,
the wallet asks the registry for the last state it validated of this author. Neither path has been tried on a real device.

## The dictation module (optional)

A widget for the home screen to dictate to Claude Code (Claude's cloud sessions, `claude --cloud`), not specific to Aiwa. It needs
**Termux** (the local backend, `android/backend/aiwa_server.py`, runs there) and the user's own Claude login; nothing about it is
needed for the Store or the wallet. It runs only once it has been turned on — by adding the widget, or by opening its screen from
the Store's **Dictate** tab. Until then there is no service, no permission request and no notification.

Install the backend in Termux (one command, also for updates):

```sh
curl -fsSL https://raw.githubusercontent.com/theodoreyong9/Aiwa_store/main/android/backend/bootstrap.sh | bash
```

### The Store and Aiwa modes

The widget's Deploy chip offers two modes, both ending with an app sent to the phone through a public relay (ntfy.sh):

- `store`: ONE app for the Store, a self-contained `nom.app.html` (the constraints are in the instruction and in
  [`store-app-example.html`](store-app-example.html)). Its code travels in the package on GitHub.
- `aiwa`: the same kind of file, `nom.aiwa.html`, whose logic is an Aiwa **contract**: rules that anyone replays from the signed events they hold
  ([`aiwa-app-example.html`](aiwa-app-example.html)). It imports the SDK as one module, `lib/aiwa.js`, served with the Store's site. The Store publishes it through Aiwa;
  GitHub only holds a pointer to it.

The ▦ button opens the Store's publish sheet with the app in it; the user reads it, tries it and presses **Publish**: the Store signs it
with the wallet and opens the pull request on the user's GitHub account. Nothing is signed or published by the widget. Not tied to a
GitHub repository, nothing is pushed by Claude.

## Build

The APK is compiled in CI only (`.github/workflows/android.yml`: the web app, the backend tests, the bridge's unit tests, then
`gradle :app:assembleDebug`), signed with the fixed debug key so that it installs over the previous one, and published as one file
of a rolling release, `android-latest`. Locally: `npm run build -w aiwa-store-web`, then `gradle -p android :app:assembleDebug`
(the build copies `apps/web/dist` into the assets).

## Not verified

The WebView host, the camera for scanning codes and the Termux backend on a real device; the keystore and the GitHub login on a real device (the device flow's logic is
unit-tested against a stand-in of GitHub, not against GitHub); the automatic backup; the widget on real launchers; Google Play policy (the APK
is meant to be installed by hand). No Android SDK is available where this was written: the Kotlin that is not plain JVM is compiled in CI only.
