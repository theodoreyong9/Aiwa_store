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

1. `release.json` lists every file of the page with its SHA-256, a version and a date. It is made by the *Pages* workflow, and by the
   Android build for the copy inside the APK.
2. Each file is read and compared with its hash, so the page is one whole release and never a mix of two deployments or a file cut short.
   A name that climbs out of the folder, a missing `index.html`, more than 400 files, 5 MB for a file or 24 MB in all are refused.
3. All of it goes to `pending/` as a whole, or nothing does. It replaces the page at the **next start**, never while the page runs
   (the Store loads some of its code late, and must find the files of its own release).
4. A release older than the one held, or listing the very same files, is not an update. After the APK itself is updated, a downloaded
   copy older than the page inside it is dropped.

The app looks once an hour, in the background, and says "ferme l'appli et rouvre-la" when a release is ready.

**Whose word it is.** There is no signature: what the site publishes is what the phones run, and the site is published by the repository's
GitHub account, so that account is the key. The page travels over HTTPS and the hashes only keep it whole. If one day the Store holds
real value and the account is a risk, a signature (a key kept off GitHub, checked by the APK) can be added in front of the same download.

Not run on a phone: the checks are unit-tested on a JVM (`SiteReleaseTest`, against a release written by the Node side); the download, the
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

**Why the person pastes it.** An app can neither install another app nor write into it, and Termux refuses commands from other apps until
`allow-external-apps=true` is set in it (the line does that too). So the widget can start the backend once it is there, never install it.
When it is not there, the widget says what is missing instead of "starting":

| What the widget shows | What it means | What a tap does |
|---|---|---|
| "Termux manquant" | Termux is not on the phone (the app checks: Android itself says nothing when asked to start an app that is absent) | copies the line, opens the F-Droid page of Termux |
| "Installation manquante" | Termux was asked twice to start the backend and it never answered: it is not installed in it (or Termux refuses) | copies the line, opens Termux |
| "Dernier envoi échoué" | the backend answers but the last send went wrong; the reason was only a toast | opens the app, which shows it |

While it is missing the app stops restarting it; once the line has been pasted and the backend answers, the widget goes back to normal
by itself. (`BackendHealth.kt`, tested on a JVM; the widget and the activity are compiled in CI, never run on a phone.)

### The Store and Aiwa modes

The widget's Deploy chip offers two modes, both ending with an app sent to the phone through a public relay (ntfy.sh):

- `store`: ONE app for the Store, a self-contained `nom.app.html` (the constraints are in the instruction and in
  [`store-app-example.html`](store-app-example.html)). Its code travels in the package on GitHub when it is published.
- `aiwa`: the same kind of file, `nom.aiwa.html`, whose logic is an Aiwa **contract**: rules that anyone replays from the signed events they hold
  ([`aiwa-app-example.html`](aiwa-app-example.html)). It imports the SDK as one module, `lib/aiwa.js`, served with the Store's site. The Store publishes it through Aiwa;
  GitHub only holds a pointer to it.

Both put the same two buttons next to the Deploy chip: ▦ opens the Store on the publish sheet with the app in it, and `</>` opens the app's code
**on GitHub**: Claude is asked to put the file in the chosen repository, as `aiwa-apps/nom.app.html` (or `.aiwa.html`), with the Push instruction (its
own branch, and the base branch when the integration is direct), so that the code is somewhere besides a copy on the phone. The phone checks that the file
is there (every 30 s for an hour after the app came, on `raw.githubusercontent.com`: a public repository only) and only then does `</>` go to GitHub;
until then, and always for a private repository, it shows the copy the phone received (through the relay, or from the branch when the relay is blocked).
The globe of the Store's own repository opens the app, not a browser: the site is the page the app serves, and a browser would be a second copy of it with a
wallet of its own. Only the `aiwa` mode
adds the documents button next to the mic (the yellow paper, the plain-words explanation, the ontology of the sustainable residue and the 16-slide
carousel as PDFs in `docs/`, the plan, the business model): a contract has to follow the protocol, a plain app does not.

While a message is on its way (creating a session takes a while) the widget says how long it has lasted and what it is doing (a step of the backend's, then the last line the CLI printed, such as the progress of an upload), and the settings (repository, model,
Push, Deploy, the session name) and the mic are dimmed and say why when tapped; what only looks (the site, the app, the code, Actions, Claude)
stays alive. A creation is given up when the CLI has printed nothing for 150 seconds or after 10 minutes in all, not at a fixed 3 minutes: a repository the Claude GitHub App is not installed on is bundled and uploaded from the phone (Anthropic's documentation), which can take minutes for a repository of this size. A creation whose CLI stops to ask something (such as trusting a folder it has never seen) is reported after 30 seconds with what it
asks and what to do, instead of after the 3 minutes of the timeout; a timeout says at which second the CLI began and what it printed. The whole run
is in `~/aiwa_cloud_last.log`.

**How what Claude says reaches the phone.** An app, the "Claude waits" alert and the relay test all go to a public relay (ntfy.sh) with one
`curl`. That needs the cloud environment to allow `ntfy.sh`, a setting only claude.ai can change (a new environment is on the default list,
which does not have it; *État d'Aiwa* then says the cloud alerts are blocked). There is a second road that needs no setting, because a
cloud session always has GitHub: **when the session was started on a repository** (the widget's repository chip, chosen *before* the session
starts), the instructions add *if the command fails, write one line in `aiwa-out/SIGNAL`, commit it on your own branch and push it*:
`app <name> <time>` (with the app file next to it), `attend <time>` (only when Claude waits for an answer or a decision, not after every
reply, to keep the branch quiet) or `check <time>` (the relay test, which makes the "alerts blocked" notice go away by itself). For 45 minutes
after each message the backend reads that file from `raw.githubusercontent.com` every 20 s (a new address each time, because the CDN keeps a
file for minutes) and acts on a signal once, if it answers the last message.

Conditions: a repository chosen when the session starts (every new session starts on one: the widget asks for it before anything can be
sent, and a session begun earlier without one keeps working but has no road to GitHub; a session made elsewhere is added by the name of its
branch, `claude/…`, which gives the repository and the branch, and a link alone is refused), **public** (nothing is read from a private one, then the alert does not come and Claude pastes the app), and a push to its branch
allowed. The fallback files (`aiwa-out/`) stay on that branch, never on the main one. Tested against fakes of the three servers (`MailboxTests`); not tried with a
real cloud session.

### The Android mode

Claude builds the repository's APK with GitHub Actions and publishes it to the rolling release; the widget's download button opens that address. An APK
whose app needs Termux (a backend to start there, like this one) cannot be told apart from another one by looking at it, and the line to paste is
the app's own: so Claude is asked to write `aiwa-android.json` at the root of the repository when the app needs Termux, `{"termux": "<the line>"}` (one line,
600 characters at most), and that line does everything, the APK's download included, as this project's own does (`bootstrap.sh`, `install-apk.sh`). The phone
reads it from `raw.githubusercontent.com` (a public repository only, every 2 minutes), and the download button then copies the line, shows it in a toast and opens
Termux (or the page it is downloaded from): nothing is run, the person pastes it and validates. No declaration, no line, and the button downloads the APK as before;
a private repository never shows one. This repository declares its own (`aiwa-android.json`: the line of `bootstrap.sh`), so the button does it on `Aiwa_store` too.

The ▦ button opens the Store's publish sheet with the app in it; the user reads it, tries it and presses **Publish**: the Store signs it
with the wallet and opens the pull request on the user's GitHub account. Nothing is signed or published by the widget. What Claude pushes to
the chosen repository is the app's source, for reading; publishing in the Store is the user's act, from the sheet.

## Build

The APK is compiled in CI only (`.github/workflows/android.yml`: the web app, the backend tests, the bridge's unit tests, then
`gradle :app:assembleDebug`), signed with the fixed debug key so that it installs over the previous one, and published as one file
of a rolling release, `android-latest`. Locally: `npm run build -w aiwa-store-web`, then `gradle -p android :app:assembleDebug`
(the build copies `apps/web/dist` into the assets).

## Not verified

The WebView host, the camera for scanning codes and the Termux backend on a real device; the keystore and the GitHub login on a real device (the device flow's logic is
unit-tested against a stand-in of GitHub, not against GitHub); the automatic backup; the widget on real launchers; Google Play policy (the APK
is meant to be installed by hand). No Android SDK is available where this was written: the Kotlin that is not plain JVM is compiled in CI only.
